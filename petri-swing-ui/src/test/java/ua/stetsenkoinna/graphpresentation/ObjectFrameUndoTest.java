package ua.stetsenkoinna.graphpresentation;

import org.junit.Test;
import ua.stetsenkoinna.graphnet.GraphElement;
import ua.stetsenkoinna.graphnet.GraphObjectFrame;
import ua.stetsenkoinna.graphnet.GraphPetriPlace;
import ua.stetsenkoinna.graphnet.GraphPetriTransition;
import ua.stetsenkoinna.graphpresentation.undoable_edits.DeleteGraphElementsEdit;
import ua.stetsenkoinna.petriobj.PetriP;
import ua.stetsenkoinna.petriobj.PetriT;

import javax.swing.undo.UndoableEdit;
import javax.swing.event.UndoableEditListener;
import java.awt.Rectangle;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/**
 * Ctrl+Z reaching Petri-objects.
 *
 * <p>Creating and removing an object posted no undoable edit at all, which was survivable while an
 * object's net lived behind a modal window with its own Cancel. There is no Cancel anywhere now:
 * an edit made on an object's canvas is an edit to the model at the moment it is made, and undo is
 * the only way back. So object creation and removal are undo steps, and they have to restore
 * everything removal destroys - the object's position in the flat frame list, what enclosed it,
 * what it claimed, and what was nested inside it.
 */
public class ObjectFrameUndoTest {

    private static int idCounter = 6000;

    private static GraphPetriPlace placeAt(String name, int x, int y) {
        GraphPetriPlace place = new GraphPetriPlace(new PetriP(name, 0), idCounter++);
        place.setNewCoordinates(new Point2D.Double(x, y));
        return place;
    }

    private static GraphPetriTransition transitionAt(String name, int x, int y) {
        GraphPetriTransition transition = new GraphPetriTransition(new PetriT(name, 1.0), idCounter++);
        transition.setNewCoordinates(new Point2D.Double(x, y));
        return transition;
    }

    private static PetriNetsPanel panelWithTwoFreeElements() {
        PetriP.initNext();
        PetriT.initNext();
        PetriNetsPanel panel = new PetriNetsPanel(null, true);
        panel.getGraphNet().getGraphPetriPlaceList().add(placeAt("P", 200, 200));
        panel.getGraphNet().getGraphPetriTransitionList().add(transitionAt("T", 300, 200));
        return panel;
    }

    private static List<GraphElement> everything(PetriNetsPanel panel) {
        List<GraphElement> all = new ArrayList<>();
        all.addAll(panel.getGraphNet().getGraphPetriPlaceList());
        all.addAll(panel.getGraphNet().getGraphPetriTransitionList());
        return all;
    }

    /** Runs an action and returns the undoable edits it posted, in order. */
    private static List<UndoableEdit> editsPostedBy(Runnable action) {
        List<UndoableEdit> posted = new ArrayList<>();
        UndoableEditListener listener = event -> posted.add(event.getEdit());
        PetriNetsFrame.getUndoSupport().addUndoableEditListener(listener);
        try {
            action.run();
        } finally {
            PetriNetsFrame.getUndoSupport().removeUndoableEditListener(listener);
        }
        return posted;
    }

    @Test
    public void creatingAnObjectIsUndoableAndRedoable() {
        PetriNetsPanel panel = panelWithTwoFreeElements();
        List<GraphElement> chunk = everything(panel);

        List<UndoableEdit> posted = editsPostedBy(() -> panel.groupIntoObject(chunk, "Object"));
        assertEquals("grouping is exactly one undo step", 1, posted.size());
        GraphObjectFrame frame = panel.getCanvasModel().getFrames().getFirst();
        assertEquals(2, panel.countElementsIn(frame));

        posted.getFirst().undo();

        assertTrue("the object is gone", panel.getCanvasModel().getFrames().isEmpty());
        assertNull("and what it held is free again", panel.getCanvasModel().ownerOf(chunk.getFirst()));
        assertTrue("the net itself is untouched",
                panel.getGraphNet().getGraphPetriPlaceList().contains(chunk.getFirst()));

        posted.getFirst().redo();

        assertEquals(1, panel.getCanvasModel().getFrames().size());
        assertSame("and it claims what it claimed before", frame,
                panel.getCanvasModel().ownerOf(chunk.getFirst()));
        assertEquals(2, panel.countElementsIn(frame));
    }

    @Test
    public void removingAnObjectIsUndoableAndRestoresItsMembership() {
        PetriNetsPanel panel = panelWithTwoFreeElements();
        List<GraphElement> chunk = everything(panel);
        panel.groupIntoObject(chunk, "Object");
        GraphObjectFrame frame = panel.getCanvasModel().getFrames().getFirst();

        List<UndoableEdit> posted = editsPostedBy(() -> panel.removeObjectFrame(frame));
        assertEquals(1, posted.size());
        assertNull("removal freed what it held", panel.getCanvasModel().ownerOf(chunk.getFirst()));

        posted.getFirst().undo();

        assertEquals(1, panel.getCanvasModel().getFrames().size());
        assertSame(frame, panel.getCanvasModel().ownerOf(chunk.getFirst()));
        assertEquals(2, panel.countElementsIn(frame));

        posted.getFirst().redo();
        assertTrue(panel.getCanvasModel().getFrames().isEmpty());
        assertNull(panel.getCanvasModel().ownerOf(chunk.getFirst()));
    }

    @Test
    public void undoingARemovalRestoresItsChildrensNestingAndItsPlaceInTheFrameList() {
        // The frame's position in the flat list is load-bearing: it indexes the object in the
        // exported model, in the PNML document and in the statistics formulas, so putting the frame
        // back somewhere else would silently re-address every one of them.
        PetriNetsPanel panel = panelWithTwoFreeElements();
        GraphObjectFrame first = new GraphObjectFrame("First", new Rectangle(60, 60, 200, 160));
        panel.addObjectFrame(first);
        GraphObjectFrame parent = new GraphObjectFrame("Parent", new Rectangle(300, 60, 400, 320));
        panel.addObjectFrame(parent);
        GraphObjectFrame child = new GraphObjectFrame("Child", new Rectangle(340, 100, 200, 160));
        panel.getCanvasModel().nest(child, parent);
        panel.addObjectFrame(child);
        GraphObjectFrame last = new GraphObjectFrame("Last", new Rectangle(800, 60, 200, 160));
        panel.addObjectFrame(last);
        panel.getCanvasModel().claim(parent, everything(panel).getFirst());
        assertEquals(1, panel.getCanvasModel().getFrames().indexOf(parent));

        List<UndoableEdit> posted = editsPostedBy(() -> panel.removeObjectFrame(parent));
        assertNull("removing the parent lifted the child to the top level",
                panel.getCanvasModel().enclosingOf(child));

        posted.getFirst().undo();

        assertEquals("back at the same index", 1, panel.getCanvasModel().getFrames().indexOf(parent));
        assertSame("and the child is nested in it again", parent,
                panel.getCanvasModel().enclosingOf(child));
        assertSame(parent, panel.getCanvasModel().ownerOf(everything(panel).getFirst()));
    }

    @Test
    public void deletingAnElementInsideAnObjectAndUndoingReturnsItToThatObject() {
        PetriNetsPanel panel = panelWithTwoFreeElements();
        List<GraphElement> chunk = everything(panel);
        panel.groupIntoObject(chunk, "Object");
        GraphObjectFrame frame = panel.getCanvasModel().getFrames().getFirst();
        GraphElement place = chunk.getFirst();

        panel.openObjectCanvas(frame);
        panel.selectAll();
        assertTrue(panel.getChoosenElements().contains(place));
        List<UndoableEdit> posted = editsPostedBy(panel::deleteSelectedElements);

        assertEquals(0, panel.countElementsIn(frame));
        assertFalse("the frame does not go on claiming what the canvas no longer draws",
                frame.hasMember(place));
        assertEquals(1, posted.size());
        assertTrue(posted.getFirst() instanceof DeleteGraphElementsEdit);

        posted.getFirst().undo();

        assertSame("undo is symmetric with the release", frame,
                panel.getCanvasModel().ownerOf(place));
        assertEquals(2, panel.countElementsIn(frame));
    }

    /** Builds the net a collection entry would hand over: two elements and the arc between them. */
    private static ua.stetsenkoinna.graphnet.GraphPetriNet templateNet() {
        GraphPetriPlace place = placeAt("TP", 500, 500);
        GraphPetriTransition transition = transitionAt("TT", 600, 500);
        ua.stetsenkoinna.graphnet.GraphPetriNet net = new ua.stetsenkoinna.graphnet.GraphPetriNet();
        net.getGraphPetriPlaceList().add(place);
        net.getGraphPetriTransitionList().add(transition);
        net.getGraphArcInList().add(
                ua.stetsenkoinna.graphnet.GraphArcFactory.inArc(place, transition, 1, false));
        return net;
    }

    private static void placeAsObject(PetriNetsPanel panel,
            ua.stetsenkoinna.graphnet.GraphPetriNet net, String name) {
        try {
            java.lang.reflect.Method method = PetriNetsPanel.class.getDeclaredMethod(
                    "placeGraphNet", ua.stetsenkoinna.graphnet.GraphPetriNet.class, String.class,
                    ua.stetsenkoinna.graphnet.NetTemplateRef.class);
            method.setAccessible(true);
            method.invoke(panel, net, name, null);
        } catch (java.lang.reflect.InvocationTargetException failure) {
            throw new AssertionError(failure.getCause());
        } catch (ReflectiveOperationException broken) {
            throw new AssertionError(broken);
        }
    }

    @Test
    public void undoingAnObjectFromTheCollectionTakesItsContentsWithIt() {
        PetriNetsPanel panel = panelWithTwoFreeElements();
        int before = everything(panel).size();

        List<UndoableEdit> posted =
                editsPostedBy(() -> placeAsObject(panel, templateNet(), "FromCollection"));

        assertEquals("putting one object on the canvas is one Ctrl+Z", 1, posted.size());
        assertEquals("fixture: the object brought its net with it", before + 2, everything(panel).size());
        assertEquals(1, panel.getCanvasModel().getFrames().size());

        posted.getFirst().undo();

        assertTrue("the frame is gone", panel.getCanvasModel().getFrames().isEmpty());
        // The defect this pins: the frame came off and its net stayed behind as loose elements
        // nobody had drawn, so the object survived its own undo in pieces.
        assertEquals("and so is everything it brought", before, everything(panel).size());
    }

    @Test
    public void redoingItPutsBackBothTheFrameAndTheNet() {
        PetriNetsPanel panel = panelWithTwoFreeElements();
        int before = everything(panel).size();

        List<UndoableEdit> posted =
                editsPostedBy(() -> placeAsObject(panel, templateNet(), "FromCollection"));
        posted.getFirst().undo();
        posted.getFirst().redo();

        assertEquals("the net is back", before + 2, everything(panel).size());
        assertEquals("and so is the object", 1, panel.getCanvasModel().getFrames().size());
    }

    @Test
    public void deletingAnObjectDeletesTheNetInsideIt() {
        PetriNetsPanel panel = panelWithTwoFreeElements();
        List<GraphElement> chunk = everything(panel);
        GraphObjectFrame frame = panel.groupIntoObject(chunk, "Object");
        panel.getSelection().clear();
        panel.getSelection().add(frame);

        List<UndoableEdit> posted = editsPostedBy(panel::deleteSelectedObjects);

        assertEquals("deleting one object is one Ctrl+Z", 1, posted.size());
        assertTrue("the object is gone", panel.getCanvasModel().getFrames().isEmpty());
        // The defect this pins: Delete used to lift the net out and drop only the frame, which
        // is ungrouping. The user pressed Delete on one thing and got its contents back.
        assertEquals("and so is the net it held", 0, everything(panel).size());
    }

    @Test
    public void undoingThatDeleteBringsBackTheObjectAndItsNet() {
        PetriNetsPanel panel = panelWithTwoFreeElements();
        List<GraphElement> chunk = everything(panel);
        GraphObjectFrame frame = panel.groupIntoObject(chunk, "Object");
        panel.getSelection().clear();
        panel.getSelection().add(frame);

        List<UndoableEdit> posted = editsPostedBy(panel::deleteSelectedObjects);
        posted.getFirst().undo();

        assertEquals("the net is back", 2, everything(panel).size());
        assertEquals("and so is the object", 1, panel.getCanvasModel().getFrames().size());
        GraphObjectFrame restored = panel.getCanvasModel().getFrames().getFirst();
        assertEquals("with its members still claimed by it", 2, panel.countElementsIn(restored));
    }

    @Test
    public void deletingAnObjectTakesWhatIsNestedInsideItToo() {
        PetriNetsPanel panel = panelWithTwoFreeElements();
        GraphObjectFrame outer = panel.groupIntoObject(everything(panel), "Outer");
        GraphElement innerMember = placeAt("PInner", 900, 900);
        panel.getGraphNet().getGraphPetriPlaceList().add((GraphPetriPlace) innerMember);
        GraphObjectFrame inner = panel.groupIntoObject(List.of(innerMember), "Inner");
        panel.getCanvasModel().nest(inner, outer);
        assertSame("fixture: the inner object really is nested",
                outer, panel.getCanvasModel().enclosingOf(inner));
        assertEquals("fixture: and it holds its own place", 1, panel.countElementsIn(inner));

        panel.getSelection().clear();
        panel.getSelection().add(outer);
        panel.deleteSelectedObjects();

        assertTrue("a nested object cannot outlive the object it was nested in",
                panel.getCanvasModel().getFrames().isEmpty());
        assertEquals("nor can its net", 0, everything(panel).size());
    }

    @Test
    public void removingOnlyTheFrameStillKeepsTheNet() {
        PetriNetsPanel panel = panelWithTwoFreeElements();
        List<GraphElement> chunk = everything(panel);
        GraphObjectFrame frame = panel.groupIntoObject(chunk, "Object");

        // Ungrouping is the other operation, and it is unchanged: it is what the frame's own
        // Remove Petri-object frame does, and the only way to keep a net without its object.
        panel.removeObjectFrame(frame);

        assertTrue(panel.getCanvasModel().getFrames().isEmpty());
        assertEquals("the net stays on the canvas", 2, everything(panel).size());
        assertNull("free again", panel.getCanvasModel().ownerOf(chunk.getFirst()));
    }

    @Test
    public void duplicatingAnObjectIsOneUndoStepThatTakesTheCopiedNetWithIt() {
        PetriNetsPanel panel = panelWithTwoFreeElements();
        GraphObjectFrame frame = panel.groupIntoObject(everything(panel), "Object");
        panel.getSelection().clear();
        panel.getSelection().add(frame);

        List<UndoableEdit> posted = editsPostedBy(panel::duplicateSelection);

        assertEquals("duplicating is one Ctrl+Z", 1, posted.size());
        assertEquals(2, panel.getCanvasModel().getFrames().size());
        assertEquals("the copy brought a net of its own", 4, everything(panel).size());

        posted.getFirst().undo();

        assertEquals("the copy is gone whole", 1, panel.getCanvasModel().getFrames().size());
        assertEquals("net included", 2, everything(panel).size());
    }

    @Test
    public void pastingAnObjectIsOneUndoStepThatTakesItsNetWithIt() {
        PetriNetsPanel panel = panelWithTwoFreeElements();
        GraphObjectFrame frame = panel.groupIntoObject(everything(panel), "Object");
        panel.getSelection().clear();
        panel.getSelection().add(frame);
        panel.copySelection();

        List<UndoableEdit> posted = editsPostedBy(panel::pasteClipboard);

        assertEquals("pasting is one Ctrl+Z", 1, posted.size());
        assertEquals(2, panel.getCanvasModel().getFrames().size());
        assertEquals(4, everything(panel).size());

        posted.getFirst().undo();

        assertEquals("the pasted object is gone whole", 1, panel.getCanvasModel().getFrames().size());
        assertEquals("net included", 2, everything(panel).size());
    }

    @Test
    public void groupingASelectionThatHoldsAnObjectNestsThatObjectInTheNewOne() {
        PetriNetsPanel panel = panelWithTwoFreeElements();
        GraphObjectFrame existing = panel.groupIntoObject(everything(panel), "Existing");

        GraphPetriPlace loose = placeAt("PLoose", 800, 200);
        panel.getGraphNet().getGraphPetriPlaceList().add(loose);

        // What the user selects with a marquee across a chunk of net that includes an object.
        panel.getSelection().clear();
        panel.getSelection().add(loose);
        panel.getSelection().add(existing);

        GraphObjectFrame grouped = panel.groupIntoObject(
                List.of(loose), List.of(existing), "Grouped");

        assertSame("the loose element joined the new object", grouped,
                panel.getCanvasModel().ownerOf(loose));
        // The defect this pins: an object is not a GraphElement, so it was never in the list
        // being grouped and was quietly left where it was.
        assertSame("and the object that was selected with it is nested inside it", grouped,
                panel.getCanvasModel().enclosingOf(existing));
        assertTrue("the nested object keeps its own net",
                panel.countElementsIn(existing) == 2);
        assertTrue("whose elements the new object did not steal",
                panel.getCanvasModel().ownerOf(everything(panel).getFirst()) == existing);
    }

    @Test
    public void groupingDoesNotReparentAnObjectAlreadyInsideAnotherOneBeingGrouped() {
        PetriNetsPanel panel = panelWithTwoFreeElements();
        GraphObjectFrame parent = panel.groupIntoObject(everything(panel), "Parent");
        GraphPetriPlace innerPlace = placeAt("PInner", 900, 900);
        panel.getGraphNet().getGraphPetriPlaceList().add(innerPlace);
        GraphObjectFrame child = panel.groupIntoObject(List.of(innerPlace), "Child");
        panel.getCanvasModel().nest(child, parent);

        GraphObjectFrame grouped = panel.groupIntoObject(
                List.of(), List.of(parent, child), "Grouped");

        assertSame("the outer one moved into the new object", grouped,
                panel.getCanvasModel().enclosingOf(parent));
        assertSame("the one already inside it stayed where it was", parent,
                panel.getCanvasModel().enclosingOf(child));
    }
}

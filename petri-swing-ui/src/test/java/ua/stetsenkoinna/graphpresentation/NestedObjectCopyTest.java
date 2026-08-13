package ua.stetsenkoinna.graphpresentation;

import org.junit.Test;
import ua.stetsenkoinna.graphnet.GraphElement;
import ua.stetsenkoinna.graphnet.GraphObjectFrame;
import ua.stetsenkoinna.graphnet.GraphPetriPlace;
import ua.stetsenkoinna.graphnet.GraphPetriTransition;
import ua.stetsenkoinna.petriobj.PetriP;
import ua.stetsenkoinna.petriobj.PetriT;

import javax.swing.event.UndoableEditEvent;
import javax.swing.event.UndoableEditListener;
import javax.swing.undo.UndoManager;

import java.awt.Rectangle;
import java.awt.geom.Point2D;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/**
 * Pins that copying an object copies the whole nest, and that the undo and redo of a paste
 * put the model back exactly.
 *
 * <p>Every operation here used to flatten or corrupt a nest in its own way: duplicate read
 * only the direct members, so the copy of an object holding a nested object silently lost the
 * nested object and its net; copy/paste recreated one frame and claimed the whole subtree's
 * copies for it directly; undoing a paste deleted the clones while the pasted frames still
 * claimed them, leaving ghost membership; and every undo/redo cycle re-applied the paste
 * offset, sliding the pasted net out of its own reinstated frame.
 */
public class NestedObjectCopyTest {

    private static int idCounter = 1;

    private static PetriNetsPanel freshPanel() {
        PetriP.initNext();
        PetriT.initNext();
        idCounter = 1;
        return new PetriNetsPanel(null, true);
    }

    private static GraphPetriPlace placeAt(PetriNetsPanel panel, String name, int x, int y) {
        GraphPetriPlace place = new GraphPetriPlace(new PetriP(name, 0), idCounter++);
        place.setNewCoordinates(new Point2D.Double(x, y));
        panel.getGraphNet().getGraphPetriPlaceList().add(place);
        return place;
    }

    private static GraphPetriTransition transitionAt(PetriNetsPanel panel, String name, int x, int y) {
        GraphPetriTransition transition = new GraphPetriTransition(new PetriT(name, 1.0), idCounter++);
        transition.setNewCoordinates(new Point2D.Double(x, y));
        panel.getGraphNet().getGraphPetriTransitionList().add(transition);
        return transition;
    }

    /** Outer object holding one direct place and a nested object with a place of its own. */
    private static GraphObjectFrame nestedFixture(PetriNetsPanel panel) {
        GraphPetriPlace outerPlace = placeAt(panel, "PO", 200, 320);
        GraphObjectFrame outer = new GraphObjectFrame("Outer", new Rectangle(120, 100, 360, 300));
        panel.addObjectFrame(outer);
        panel.getCanvasModel().claim(outer, outerPlace);

        GraphPetriPlace innerPlace = placeAt(panel, "PI", 260, 200);
        GraphObjectFrame inner = new GraphObjectFrame("Inner", new Rectangle(200, 150, 160, 120));
        panel.addObjectFrame(inner);
        panel.getCanvasModel().claim(inner, innerPlace);
        panel.getCanvasModel().nest(inner, outer);
        return outer;
    }

    private static GraphObjectFrame frameNamed(PetriNetsPanel panel, String name) {
        for (GraphObjectFrame frame : panel.getCanvasModel().getFrames()) {
            if (name.equals(frame.getName())) {
                return frame;
            }
        }
        return null;
    }

    // ------------------------------------------------------------------ duplicate

    @Test
    public void duplicatingAnObjectCopiesItsNestedObjectsToo() {
        PetriNetsPanel panel = freshPanel();
        GraphObjectFrame outer = nestedFixture(panel);

        GraphObjectFrame copy = panel.duplicateObject(outer, "Outer copy");

        assertNotNull("an object whose net lives in a nested object still duplicates", copy);
        assertEquals("four frames: the original pair and the copied pair",
                4, panel.getCanvasModel().getFrames().size());
        GraphObjectFrame innerCopy = frameNamed(panel, "Inner 2");
        assertNotNull("the nested object exists in the copy under its uniquified name", innerCopy);
        assertSame("and it is nested inside the copied outer object",
                copy, panel.getCanvasModel().enclosingOf(innerCopy));
        assertEquals("the copy holds the whole net, counted from outside",
                2, panel.countElementsIn(copy));
        assertEquals("one element belongs to the copied nested object itself",
                1, innerCopy.getMembers().size());
    }

    @Test
    public void duplicatingAnObjectWhoseNetIsAllInTheNestedObjectStillWorks() {
        PetriNetsPanel panel = freshPanel();
        GraphPetriPlace innerPlace = placeAt(panel, "PI", 260, 200);
        GraphObjectFrame outer = new GraphObjectFrame("Outer", new Rectangle(120, 100, 360, 300));
        panel.addObjectFrame(outer);
        GraphObjectFrame inner = new GraphObjectFrame("Inner", new Rectangle(200, 150, 160, 120));
        panel.addObjectFrame(inner);
        panel.getCanvasModel().claim(inner, innerPlace);
        panel.getCanvasModel().nest(inner, outer);

        GraphObjectFrame copy = panel.duplicateObject(outer, "Outer copy");

        assertNotNull("it used to refuse, claiming the object had no net to copy", copy);
        assertEquals("the nested object's net is the whole net here",
                1, panel.countElementsIn(copy));
    }

    // ------------------------------------------------------------------ copy/paste

    @Test
    public void pastingACopiedObjectRecreatesItsNestedObjects() {
        PetriNetsPanel panel = freshPanel();
        GraphObjectFrame outer = nestedFixture(panel);
        panel.getSelection().add(outer);
        panel.copySelection();

        panel.pasteClipboard();

        assertEquals("four frames after the paste", 4, panel.getCanvasModel().getFrames().size());
        GraphObjectFrame outerCopy = frameNamed(panel, "Outer copy");
        GraphObjectFrame innerCopy = frameNamed(panel, "Inner 2");
        assertNotNull(outerCopy);
        assertNotNull("the nested object is recreated, not flattened into the copy", innerCopy);
        assertSame(outerCopy, panel.getCanvasModel().enclosingOf(innerCopy));
        assertEquals("the pasted outer object holds one direct element",
                1, outerCopy.getMembers().size());
        assertEquals("seen from outside it still holds the whole net",
                2, panel.countElementsIn(outerCopy));
    }

    @Test
    public void copyingAParentAndItsNestedChildTogetherPastesTheNestOnce() {
        PetriNetsPanel panel = freshPanel();
        GraphObjectFrame outer = nestedFixture(panel);
        GraphObjectFrame inner = frameNamed(panel, "Inner");
        panel.getSelection().add(outer);
        panel.getSelection().add(inner);
        panel.copySelection();

        panel.pasteClipboard();

        assertEquals("the nest pastes once, not once per selected frame",
                4, panel.getCanvasModel().getFrames().size());
    }

    // ------------------------------------------------------------------ paste undo/redo

    private static UndoManager watchUndo() {
        UndoManager manager = new UndoManager();
        UndoableEditListener listener = (UndoableEditEvent event) ->
                manager.addEdit(event.getEdit());
        PetriNetsFrame.getUndoSupport().addUndoableEditListener(listener);
        return manager;
    }

    @Test
    public void undoingAPasteLeavesNoGhostMembership() {
        PetriNetsPanel panel = freshPanel();
        GraphObjectFrame outer = nestedFixture(panel);
        UndoManager undo = watchUndo();
        panel.getSelection().add(outer);
        panel.copySelection();
        panel.pasteClipboard();

        undo.undo();

        assertEquals("the original pair of frames is back alone",
                2, panel.getCanvasModel().getFrames().size());
        assertEquals("no element of the net is claimed by a frame that is gone",
                2, panel.getGraphNet().getGraphPetriPlaceList().size());
        for (GraphObjectFrame frame : panel.getCanvasModel().getFrames()) {
            for (GraphElement member : List.copyOf(frame.getMembers())) {
                assertTrue("a frame only holds elements that still exist in the net: "
                                + member.getName(),
                        panel.getGraphNet().getGraphPetriPlaceList().contains(member)
                                || panel.getGraphNet().getGraphPetriTransitionList().contains(member));
            }
        }
    }

    @Test
    public void undoRedoCyclesDoNotSlideThePastedNetOutOfItsFrame() {
        PetriNetsPanel panel = freshPanel();
        GraphObjectFrame outer = nestedFixture(panel);
        UndoManager undo = watchUndo();
        panel.getSelection().add(outer);
        panel.copySelection();
        panel.pasteClipboard();

        GraphObjectFrame outerCopy = frameNamed(panel, "Outer copy");
        List<GraphElement> members = panel.getCanvasModel().membersOfSubtree(outerCopy);
        Point2D pastedAt = members.get(0).getGraphElementCenter();
        double x = pastedAt.getX();
        double y = pastedAt.getY();

        undo.undo();
        undo.redo();
        undo.undo();
        undo.redo();

        GraphObjectFrame reinstated = frameNamed(panel, "Outer copy");
        assertNotNull(reinstated);
        List<GraphElement> after = panel.getCanvasModel().membersOfSubtree(reinstated);
        assertFalse("the pasted object still holds its net after undo and redo", after.isEmpty());
        Point2D cycled = after.get(0).getGraphElementCenter();
        assertEquals("the pasted net sits exactly where the paste put it", x, cycled.getX(), 0.001);
        assertEquals("the pasted net sits exactly where the paste put it", y, cycled.getY(), 0.001);
    }

    @Test
    public void redoingAPasteLeavesNoLockedMemberSelected() {
        PetriNetsPanel panel = freshPanel();
        GraphObjectFrame outer = nestedFixture(panel);
        UndoManager undo = watchUndo();
        panel.getSelection().add(outer);
        panel.copySelection();
        panel.pasteClipboard();

        undo.undo();
        undo.redo();

        GraphObjectFrame outerCopy = frameNamed(panel, "Outer copy");
        for (GraphElement member : panel.getCanvasModel().membersOfSubtree(outerCopy)) {
            assertFalse("a member locked inside the pasted object is not selected: "
                    + member.getName(), panel.getSelection().contains(member));
        }
    }

    // ------------------------------------------------------------------ undo navigation

    /**
     * Undoing a nested object's creation while standing on its canvas used to teleport the
     * user to the root Net canvas: the removal released the frame's enclosing pointer before
     * the canvas stack could walk it to find the nearest surviving ancestor.
     */
    @Test
    public void undoingANestedCreationLandsOnTheParentCanvasNotTheRoot() {
        PetriNetsPanel panel = freshPanel();
        GraphPetriPlace outerPlace = placeAt(panel, "PO", 200, 320);
        GraphObjectFrame outer = new GraphObjectFrame("Outer", new Rectangle(120, 100, 360, 300));
        panel.addObjectFrame(outer);
        panel.getCanvasModel().claim(outer, outerPlace);
        panel.openObjectCanvas(outer);
        UndoManager undo = watchUndo();

        GraphPetriPlace innerPlace = placeAt(panel, "PI", 260, 200);
        panel.getSelection().add(innerPlace);
        GraphObjectFrame inner = panel.groupIntoObject(List.of(innerPlace), "Inner");
        panel.openObjectCanvas(inner);
        assertSame(inner, panel.getCanvasStack().getActive());

        undo.undo();

        assertSame("undo of the nested creation lands on the parent's canvas",
                outer, panel.getCanvasStack().getActive());
        assertNull("the nested object is gone", frameNamed(panel, "Inner"));
    }

    // ------------------------------------------------------------------ collapsed drag

    /**
     * The remembered expanded rectangle did not travel with a drag, so dragging a collapsed
     * object and expanding it snapped the frame back to where it was collapsed while its
     * net, carried along by the drag, stayed at the drop point.
     */
    @Test
    public void draggingACollapsedObjectAndExpandingItKeepsItsNetInside() {
        PetriNetsPanel panel = freshPanel();
        GraphPetriPlace place = placeAt(panel, "P1", 200, 200);
        GraphObjectFrame frame = new GraphObjectFrame("Obj", new Rectangle(120, 120, 220, 180));
        panel.addObjectFrame(frame);
        panel.getCanvasModel().claim(frame, place);
        frame.setCollapsed(true);

        // The real drag path: a selected frame moves through moveFrame, which carries the
        // frame's whole subtree along by the same delta.
        panel.getSelection().setSelectedFrame(frame);
        panel.moveSelectionBy(380, 280);
        frame.setCollapsed(false);

        assertTrue("expanded again, the frame is where the collapsed box was dropped, "
                        + "with its net inside: " + frame.getBounds(),
                frame.getBounds().contains(place.getGraphElementCenter().getX(),
                        place.getGraphElementCenter().getY()));
    }
}

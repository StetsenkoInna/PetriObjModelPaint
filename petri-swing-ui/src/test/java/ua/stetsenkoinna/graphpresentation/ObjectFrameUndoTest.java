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
}

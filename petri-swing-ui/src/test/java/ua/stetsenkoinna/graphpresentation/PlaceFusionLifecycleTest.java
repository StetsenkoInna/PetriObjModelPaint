package ua.stetsenkoinna.graphpresentation;

import org.junit.Test;
import ua.stetsenkoinna.graphnet.GraphObjectFrame;
import ua.stetsenkoinna.graphnet.GraphPetriPlace;
import ua.stetsenkoinna.graphnet.GraphPlaceFusion;
import ua.stetsenkoinna.petriobj.PetriP;
import ua.stetsenkoinna.petriobj.PetriT;

import javax.swing.event.UndoableEditEvent;
import javax.swing.event.UndoableEditListener;
import javax.swing.undo.UndoManager;

import java.awt.Rectangle;
import java.awt.geom.Point2D;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/**
 * Pins the shared place's life beyond its creation: undo, the owners going stale, deletion
 * of a fused half, and serialization.
 *
 * <p>Creating a fusion posted no undoable edit, so Ctrl+Z right after making one left the
 * fusion alone and undid whatever older action was next on the stack, up to deleting a whole
 * Petri-object. The owners were frozen at join time in final fields, so ungrouping the
 * owning object left the fusion anchored to a frame no longer on the canvas. Deleting a
 * fused place dropped the fusion, and undoing that delete restored the place but silently
 * forgot it had been shared.
 */
public class PlaceFusionLifecycleTest {

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

    private static GraphObjectFrame frameWith(PetriNetsPanel panel, String name,
            Rectangle bounds, GraphPetriPlace member) {
        GraphObjectFrame frame = new GraphObjectFrame(name, bounds);
        panel.getCanvasModel().getFrames().add(frame);
        panel.getCanvasModel().claim(frame, member);
        return frame;
    }

    private static UndoManager watchUndo() {
        UndoManager manager = new UndoManager();
        UndoableEditListener listener = (UndoableEditEvent event) ->
                manager.addEdit(event.getEdit());
        PetriNetsFrame.getUndoSupport().addUndoableEditListener(listener);
        return manager;
    }

    /** Two framed places joined through the panel's own port-link path. */
    private static GraphPlaceFusion fuseThroughPanel(PetriNetsPanel panel,
            GraphPetriPlace masterPlace, GraphPetriPlace joinedPlace) throws Exception {
        java.lang.reflect.Method link = PetriNetsPanel.class.getDeclaredMethod(
                "linkPortToElement",
                ua.stetsenkoinna.graphnet.GraphElement.class,
                ua.stetsenkoinna.graphnet.GraphElement.class);
        link.setAccessible(true);
        link.invoke(panel, masterPlace, joinedPlace);
        return panel.getCanvasModel().fusionOf(masterPlace);
    }

    // ------------------------------------------------------------------ undo

    @Test
    public void creatingASharedPlaceIsOneUndoStep() throws Exception {
        PetriNetsPanel panel = freshPanel();
        GraphPetriPlace pa = placeAt(panel, "PA", 200, 150);
        GraphObjectFrame a = frameWith(panel, "A", new Rectangle(140, 90, 160, 140), pa);
        GraphPetriPlace pb = placeAt(panel, "PB", 700, 150);
        GraphObjectFrame b = frameWith(panel, "B", new Rectangle(640, 90, 160, 140), pb);
        UndoManager undo = watchUndo();

        fuseThroughPanel(panel, pa, pb);
        assertEquals(1, panel.getCanvasModel().getFusions().size());

        undo.undo();

        assertTrue("undo removes the fusion, not something older",
                panel.getCanvasModel().getFusions().isEmpty());
        assertTrue("both objects are untouched",
                panel.getCanvasModel().getFrames().contains(a)
                        && panel.getCanvasModel().getFrames().contains(b));

        undo.redo();
        assertEquals("redo brings the shared place back",
                1, panel.getCanvasModel().getFusions().size());
    }

    @Test
    public void splittingASharedPlaceIsOneUndoStep() throws Exception {
        PetriNetsPanel panel = freshPanel();
        GraphPetriPlace pa = placeAt(panel, "PA", 200, 150);
        frameWith(panel, "A", new Rectangle(140, 90, 160, 140), pa);
        GraphPetriPlace pb = placeAt(panel, "PB", 700, 150);
        frameWith(panel, "B", new Rectangle(640, 90, 160, 140), pb);
        GraphPlaceFusion fusion = fuseThroughPanel(panel, pa, pb);
        UndoManager undo = watchUndo();
        Point2D joinedBefore = pb.getGraphElementCenter();
        double x = joinedBefore.getX();
        double y = joinedBefore.getY();

        panel.splitSharedPlace(fusion);
        assertTrue(panel.getCanvasModel().getFusions().isEmpty());

        undo.undo();

        assertEquals("undo restores the shared place",
                1, panel.getCanvasModel().getFusions().size());
        Point2D joinedAfter = pb.getGraphElementCenter();
        assertEquals("and moves the joined half back where it was", x, joinedAfter.getX(), 0.001);
        assertEquals("and moves the joined half back where it was", y, joinedAfter.getY(), 0.001);
    }

    // ------------------------------------------------------------------ stale owners

    @Test
    public void ungroupingAFusedHalfsObjectRefreshesTheFusionsOwners() throws Exception {
        PetriNetsPanel panel = freshPanel();
        GraphPetriPlace pa = placeAt(panel, "PA", 200, 150);
        GraphObjectFrame a = frameWith(panel, "A", new Rectangle(140, 90, 160, 140), pa);
        GraphPetriPlace pb = placeAt(panel, "PB", 700, 150);
        frameWith(panel, "B", new Rectangle(640, 90, 160, 140), pb);
        GraphPlaceFusion fusion = fuseThroughPanel(panel, pa, pb);
        assertSame(a, fusion.getMasterOwner());

        panel.removeObjectFrame(a);

        assertNull("the fusion no longer claims a frame that left the canvas",
                fusion.getMasterOwner());
        assertTrue("the other half still anchors it", fusion.isAnchoredToAFrame());
    }

    @Test
    public void draggingAFusedHalfIntoAnotherObjectRefreshesTheFusionsOwners() throws Exception {
        PetriNetsPanel panel = freshPanel();
        GraphPetriPlace pa = placeAt(panel, "PA", 200, 150);
        GraphObjectFrame a = frameWith(panel, "A", new Rectangle(140, 90, 160, 140), pa);
        GraphPetriPlace pb = placeAt(panel, "PB", 700, 150);
        GraphObjectFrame b = frameWith(panel, "B", new Rectangle(640, 90, 160, 140), pb);
        GraphPlaceFusion fusion = fuseThroughPanel(panel, pa, pb);

        // The claim moves, however it happens in the UI; the fusion must follow.
        panel.getCanvasModel().claim(b, pa);

        assertSame("the fusion follows the claim", b, fusion.getMasterOwner());
    }

    // ------------------------------------------------------------------ delete undo

    @Test
    public void undoingTheDeletionOfAFusedPlaceRestoresTheSharedPlace() throws Exception {
        PetriNetsPanel panel = freshPanel();
        GraphPetriPlace pa = placeAt(panel, "PA", 200, 150);
        frameWith(panel, "A", new Rectangle(140, 90, 160, 140), pa);
        GraphPetriPlace pb = placeAt(panel, "PB", 700, 150);
        GraphObjectFrame b = frameWith(panel, "B", new Rectangle(640, 90, 160, 140), pb);
        fuseThroughPanel(panel, pa, pb);
        UndoManager undo = watchUndo();

        // Delete the joined half on its own canvas, the same way the Delete tool does.
        panel.openObjectCanvas(b);
        java.lang.reflect.Method delete = PetriNetsPanel.class.getDeclaredMethod(
                "deleteElement", ua.stetsenkoinna.graphnet.GraphElement.class);
        delete.setAccessible(true);
        delete.invoke(panel, pb);
        assertTrue("deleting a fused half drops the fusion",
                panel.getCanvasModel().getFusions().isEmpty());

        undo.undo();

        assertEquals("the place comes back shared, not silently ordinary",
                1, panel.getCanvasModel().getFusions().size());

        undo.redo();
        assertTrue("and redo drops the fusion again",
                panel.getCanvasModel().getFusions().isEmpty());
    }

    // ------------------------------------------------------------------ serialization

    @Test
    public void aCanvasWithASharedPlaceSurvivesSerialization() throws Exception {
        PetriNetsPanel panel = freshPanel();
        GraphPetriPlace pa = placeAt(panel, "PA", 200, 150);
        frameWith(panel, "A", new Rectangle(140, 90, 160, 140), pa);
        GraphPetriPlace pb = placeAt(panel, "PB", 700, 150);
        frameWith(panel, "B", new Rectangle(640, 90, 160, 140), pb);
        fuseThroughPanel(panel, pa, pb);

        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream out = new ObjectOutputStream(bytes)) {
            out.writeObject(panel.getCanvasModel());
        }
        ua.stetsenkoinna.graphnet.GraphCanvasModel restored;
        try (ObjectInputStream in = new ObjectInputStream(
                new ByteArrayInputStream(bytes.toByteArray()))) {
            restored = (ua.stetsenkoinna.graphnet.GraphCanvasModel) in.readObject();
        }

        assertEquals("the shared place survives a save/load round trip",
                1, restored.getFusions().size());
        GraphPlaceFusion restoredFusion = restored.getFusions().get(0);
        List<GraphPetriPlace> restoredPlaces = restored.getNet().getGraphPetriPlaceList();
        assertTrue("its halves are the restored places, not copies",
                restoredPlaces.contains(restoredFusion.getMaster())
                        && restoredPlaces.contains(restoredFusion.getJoined()));
        assertFalse("and it still knows it is anchored to frames",
                restoredFusion.getMasterOwner() == null && restoredFusion.getJoinedOwner() == null);
    }
}

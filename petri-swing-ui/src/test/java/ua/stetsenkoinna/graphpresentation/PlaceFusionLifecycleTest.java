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

    // ------------------------------------------------------------------ one shared marking

    /**
     * A shared place is one place with one marking: a PNML reference place has no marking of
     * its own, and the built simulation replaces the joined half's instance with the
     * master's. The editor used to keep showing each half's own count, so the drawing
     * displayed two different numbers for what the model runs as one.
     */
    @Test
    public void joiningAdoptsTheMastersMarkingForBothHalves() throws Exception {
        PetriNetsPanel panel = freshPanel();
        GraphPetriPlace pa = new GraphPetriPlace(new PetriP("PA", 3), idCounter++);
        pa.setNewCoordinates(new Point2D.Double(200, 150));
        panel.getGraphNet().getGraphPetriPlaceList().add(pa);
        frameWith(panel, "A", new Rectangle(140, 90, 160, 140), pa);
        GraphPetriPlace pb = new GraphPetriPlace(new PetriP("PB", 0), idCounter++);
        pb.setNewCoordinates(new Point2D.Double(700, 150));
        panel.getGraphNet().getGraphPetriPlaceList().add(pb);
        frameWith(panel, "B", new Rectangle(640, 90, 160, 140), pb);

        fuseThroughPanel(panel, pa, pb);

        assertEquals("the joined half shows the master's count",
                3, pb.getPetriPlace().getMark());
    }

    @Test
    public void editingEitherHalfChangesTheOneSharedMarking() throws Exception {
        PetriNetsPanel panel = freshPanel();
        GraphPetriPlace pa = placeAt(panel, "PA", 200, 150);
        frameWith(panel, "A", new Rectangle(140, 90, 160, 140), pa);
        GraphPetriPlace pb = placeAt(panel, "PB", 700, 150);
        frameWith(panel, "B", new Rectangle(640, 90, 160, 140), pb);
        fuseThroughPanel(panel, pa, pb);

        // The user sets tokens through the JOINED half's properties dialog.
        pb.getPetriPlace().setMark(5);
        panel.placeMarkingEdited(pb);

        assertEquals("the edit wrote through to the master",
                5, pa.getPetriPlace().getMark());
        assertEquals(5, pb.getPetriPlace().getMark());

        // And through the master's dialog.
        pa.getPetriPlace().setMark(7);
        panel.placeMarkingEdited(pa);
        assertEquals("the mirror also runs master to joined",
                7, pb.getPetriPlace().getMark());
    }

    // ------------------------------------------------------------------ animation

    /**
     * The built model replaces the joined half's place instance with the master's, so a
     * firing in the joined half's own object reports the master's number - which the
     * joined object's own list never contained: the token arriving in the shared place
     * animated nothing there and the joined half kept its stale initial count forever.
     */
    @Test
    public void aTokenArrivingInASharedPlaceUpdatesAndAnimatesBothHalves() throws Exception {
        PetriNetsPanel panel = freshPanel();
        GraphPetriPlace pa = placeAt(panel, "PA", 200, 150);
        frameWith(panel, "A", new Rectangle(140, 90, 160, 140), pa);
        GraphPetriPlace pb = placeAt(panel, "PB", 700, 150);
        frameWith(panel, "B", new Rectangle(640, 90, 160, 140), pb);
        GraphPlaceFusion fusion = fuseThroughPanel(panel, pa, pb);

        // The simulation moves tokens on the master's live instance only.
        pa.getPetriPlace().setMark(4);
        java.util.ArrayList<Integer> fired = new java.util.ArrayList<>(
                java.util.List.of(pa.getPetriPlace().getNumber()));

        panel.animateP(fired, null);

        assertEquals("the joined half shows the new count, not its stale initial one",
                4, pb.getPetriPlace().getMark());
        assertFalse("the pulse is over, the line is dark again", fusion.isAnimationLit());
    }

    // ------------------------------------------------------------------ clicking the line

    /**
     * The port-to-port form had no hit test at all: the drawn line could not be clicked,
     * so a framed shared place could not be removed by any means.
     */
    @Test
    public void theSharedPlacesDrawnLineIsClickable() throws Exception {
        PetriNetsPanel panel = freshPanel();
        GraphPetriPlace pa = placeAt(panel, "PA", 200, 150);
        frameWith(panel, "A", new Rectangle(140, 90, 160, 140), pa);
        GraphPetriPlace pb = placeAt(panel, "PB", 700, 150);
        frameWith(panel, "B", new Rectangle(640, 90, 160, 140), pb);
        GraphPlaceFusion fusion = fuseThroughPanel(panel, pa, pb);

        assertSame("a point on the drawn line finds the shared place",
                fusion, panel.findSharedPlace(new Point2D.Double(470, 150)));
        assertNull("a point away from the line finds nothing",
                panel.findSharedPlace(new Point2D.Double(470, 300)));
    }

    @Test
    public void theDeleteToolSplitsASharedPlaceWithoutMovingItsHalves() throws Exception {
        PetriNetsPanel panel = freshPanel();
        GraphPetriPlace pa = placeAt(panel, "PA", 200, 150);
        frameWith(panel, "A", new Rectangle(140, 90, 160, 140), pa);
        GraphPetriPlace pb = placeAt(panel, "PB", 700, 150);
        frameWith(panel, "B", new Rectangle(640, 90, 160, 140), pb);
        fuseThroughPanel(panel, pa, pb);
        UndoManager undo = watchUndo();

        panel.setTool(CanvasTool.DELETE);
        for (java.awt.event.MouseListener listener : panel.getMouseListeners()) {
            if (listener instanceof PetriNetsPanel.MouseHandler handler) {
                // Press and release both: the eraser decides on the release, since the press
                // it starts from may still turn out to be the corner of a sweep.
                handler.mousePressed(new java.awt.event.MouseEvent(panel,
                        java.awt.event.MouseEvent.MOUSE_PRESSED, System.currentTimeMillis(),
                        0, 470, 150, 1, false, java.awt.event.MouseEvent.BUTTON1));
                handler.mouseReleased(new java.awt.event.MouseEvent(panel,
                        java.awt.event.MouseEvent.MOUSE_RELEASED, System.currentTimeMillis(),
                        0, 470, 150, 1, false, java.awt.event.MouseEvent.BUTTON1));
            }
        }

        assertTrue("the link is gone", panel.getCanvasModel().getFusions().isEmpty());
        assertEquals("the framed halves stay exactly where they were",
                700.0, pb.getGraphElementCenter().getX(), 0.001);

        undo.undo();
        assertEquals("and the split is one undo step",
                1, panel.getCanvasModel().getFusions().size());
    }

    /**
     * The user's own flow: click the drawn line to select the link, press Delete to remove
     * it. The line was hit-testable for the tools but never selectable, so the Delete key
     * had nothing to act on.
     */
    @Test
    public void clickingTheLineSelectsTheSharedPlaceAndDeleteRemovesIt() throws Exception {
        PetriNetsPanel panel = freshPanel();
        GraphPetriPlace pa = placeAt(panel, "PA", 200, 150);
        frameWith(panel, "A", new Rectangle(140, 90, 160, 140), pa);
        GraphPetriPlace pb = placeAt(panel, "PB", 700, 150);
        frameWith(panel, "B", new Rectangle(640, 90, 160, 140), pb);
        fuseThroughPanel(panel, pa, pb);

        // A full click on the drawn line: press, release, click.
        for (java.awt.event.MouseListener listener : panel.getMouseListeners()) {
            if (listener instanceof PetriNetsPanel.MouseHandler handler) {
                handler.mousePressed(new java.awt.event.MouseEvent(panel,
                        java.awt.event.MouseEvent.MOUSE_PRESSED, System.currentTimeMillis(),
                        0, 470, 150, 1, false, java.awt.event.MouseEvent.BUTTON1));
                handler.mouseReleased(new java.awt.event.MouseEvent(panel,
                        java.awt.event.MouseEvent.MOUSE_RELEASED, System.currentTimeMillis(),
                        0, 470, 150, 1, false, java.awt.event.MouseEvent.BUTTON1));
                handler.mouseClicked(new java.awt.event.MouseEvent(panel,
                        java.awt.event.MouseEvent.MOUSE_CLICKED, System.currentTimeMillis(),
                        0, 470, 150, 1, false, java.awt.event.MouseEvent.BUTTON1));
            }
        }

        panel.deleteSelection();

        assertTrue("Delete removed the clicked link", panel.getCanvasModel().getFusions().isEmpty());
    }

    /**
     * Every fusion used to be painted on every canvas: entering an unrelated object's own
     * canvas showed ghost reference lines between the raw positions of places that canvas
     * does not draw.
     */
    @Test
    public void anUnrelatedObjectsCanvasNeitherDrawsNorHitsTheSharedPlace() throws Exception {
        PetriNetsPanel panel = freshPanel();
        GraphPetriPlace pa = placeAt(panel, "PA", 200, 150);
        frameWith(panel, "A", new Rectangle(140, 90, 160, 140), pa);
        GraphPetriPlace pb = placeAt(panel, "PB", 700, 150);
        frameWith(panel, "B", new Rectangle(640, 90, 160, 140), pb);
        GraphPetriPlace pc = placeAt(panel, "PC", 400, 500);
        GraphObjectFrame c = frameWith(panel, "C", new Rectangle(340, 440, 160, 140), pc);
        GraphPlaceFusion fusion = fuseThroughPanel(panel, pa, pb);

        assertSame("on the root canvas the line is there",
                fusion, panel.findSharedPlace(new Point2D.Double(470, 150)));

        panel.openObjectCanvas(c);

        assertNull("on an unrelated object's canvas it neither draws nor answers clicks",
                panel.findSharedPlace(new Point2D.Double(470, 150)));
    }

    // ------------------------------------------------------------------ copied nests

    /**
     * Duplicating a nest whose insides shared a place used to drop the fusion from the copy:
     * it looked identical but its places were no longer shared, so the copy simulated
     * differently from the original.
     */
    @Test
    public void duplicatingANestCopiesItsInternalFusions() throws Exception {
        PetriNetsPanel panel = freshPanel();
        GraphPetriPlace pa = placeAt(panel, "PA", 200, 200);
        GraphObjectFrame a = frameWith(panel, "A", new Rectangle(140, 140, 160, 140), pa);
        GraphPetriPlace pb = placeAt(panel, "PB", 500, 200);
        GraphObjectFrame b = frameWith(panel, "B", new Rectangle(440, 140, 160, 140), pb);
        fuseThroughPanel(panel, pa, pb);
        GraphObjectFrame pipeline = panel.groupIntoObject(
                List.of(), List.of(a, b), "Pipeline");

        GraphObjectFrame copy = panel.duplicateObject(pipeline, "Pipeline copy");

        assertEquals("the copy carries its own fusion",
                2, panel.getCanvasModel().getFusions().size());
        GraphPlaceFusion copied = panel.getCanvasModel().getFusions().get(1);
        assertFalse("joining the clones, not the originals",
                copied.involves(pa) || copied.involves(pb));
        assertTrue("the copied halves live inside the copied nest",
                panel.getCanvasModel().membersOfSubtree(copy).contains(copied.getMaster())
                        && panel.getCanvasModel().membersOfSubtree(copy).contains(copied.getJoined()));

        // The whole duplicate, fusions included, is one Ctrl+Z.
        UndoManager undo = watchUndo();
        panel.getSelection().setSelectedFrame(copy);
        // no edit was recorded by the selection; undo the duplicate posted before watchUndo
        // is not visible to this manager, so re-duplicate under observation instead
        GraphObjectFrame observed = panel.duplicateObject(pipeline, "Pipeline copy 2");
        assertEquals(3, panel.getCanvasModel().getFusions().size());
        undo.undo();
        assertEquals("undoing the duplicate takes its fusion with it",
                2, panel.getCanvasModel().getFusions().size());
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

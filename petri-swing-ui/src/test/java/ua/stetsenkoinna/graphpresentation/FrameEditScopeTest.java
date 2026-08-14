package ua.stetsenkoinna.graphpresentation;

import org.junit.Test;

import ua.stetsenkoinna.graphnet.GraphObjectFrame;
import ua.stetsenkoinna.graphnet.GraphPetriPlace;
import ua.stetsenkoinna.petriobj.PetriP;
import ua.stetsenkoinna.petriobj.PetriT;

import java.awt.Rectangle;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionListener;
import java.awt.geom.Point2D;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/**
 * A Petri-object nested two or more levels deep can still be drawn directly on an ancestor's
 * canvas, whenever every object between it and that canvas is expanded - the same way a doubly
 * framed element's own body can be. An element in that position has always been locked: a click
 * on it resolves to its owning frame instead, and every drag/delete/duplicate path checks
 * {@code isOnThisCanvas} before touching it. A frame in that same position had no such check at
 * all - it could be dragged, resized, renamed, deleted or duplicated directly from an ancestor's
 * canvas, as if it were a direct child of it. These tests pin the frame-shaped version of the
 * same boundary, {@code isFrameOnThisCanvas}.
 */
public class FrameEditScopeTest {

    private static PetriNetsPanel freshPanel() {
        PetriP.initNext();
        PetriT.initNext();
        return new PetriNetsPanel(null, true);
    }

    private static PetriNetsPanel.MouseHandler mouseHandlerOf(PetriNetsPanel panel) {
        for (java.awt.event.MouseListener listener : panel.getMouseListeners()) {
            if (listener instanceof PetriNetsPanel.MouseHandler handler) {
                return handler;
            }
        }
        throw new AssertionError("the panel registered no MouseHandler");
    }

    private static MouseMotionListener motionHandlerOf(PetriNetsPanel panel) {
        MouseMotionListener[] listeners = panel.getMouseMotionListeners();
        assertTrue("the panel registered no mouse motion listener", listeners.length > 0);
        return listeners[0];
    }

    private static MouseEvent event(PetriNetsPanel panel, int id, int x, int y) {
        return new MouseEvent(panel, id, System.currentTimeMillis(), 0, x, y, 1, false, MouseEvent.BUTTON1);
    }

    private static void dragFromTo(PetriNetsPanel panel, int fromX, int fromY, int toX, int toY) {
        PetriNetsPanel.MouseHandler handler = mouseHandlerOf(panel);
        MouseMotionListener motion = motionHandlerOf(panel);
        handler.mousePressed(event(panel, MouseEvent.MOUSE_PRESSED, fromX, fromY));
        motion.mouseDragged(event(panel, MouseEvent.MOUSE_DRAGGED, (fromX + toX) / 2, (fromY + toY) / 2));
        motion.mouseDragged(event(panel, MouseEvent.MOUSE_DRAGGED, toX, toY));
        handler.mouseReleased(event(panel, MouseEvent.MOUSE_RELEASED, toX, toY));
    }

    /** Marquees from one corner to the other, left button, press-drag-release. */
    private static void marquee(PetriNetsPanel panel, int fromX, int fromY, int toX, int toY) {
        dragFromTo(panel, fromX, fromY, toX, toY);
    }

    private static int headerY(GraphObjectFrame frame) {
        return frame.getBounds().y + GraphObjectFrame.HEADER_HEIGHT / 2;
    }

    /**
     * {@code PetriNetsPanel.addObjectFrame} leaves what it added selected ({@code
     * selection.setSelectedFrame}), which is right for a real user creating one object at a time
     * but leaves this fixture's second {@code addObjectFrame} call with Inner still selected
     * afterward - contaminating the very first simulated drag/marquee a test does next, since
     * {@code mouseDragged}'s "move the whole selection" branch fires the moment
     * {@code !selection.isEmpty()}, before the test's own gesture ever gets to establish what it
     * meant to select. Clearing here is what a real user gets for free by the time they start
     * their own next gesture, several UI events after either object was actually created.
     */
    private static void clearSelection(PetriNetsPanel panel) {
        try {
            java.lang.reflect.Field field = PetriNetsPanel.class.getDeclaredField("selection");
            field.setAccessible(true);
            Object selection = field.get(panel);
            java.lang.reflect.Method clear = selection.getClass().getDeclaredMethod("clear");
            clear.setAccessible(true);
            clear.invoke(selection);
        } catch (ReflectiveOperationException broken) {
            throw new AssertionError(broken);
        }
    }

    /** Outer (150..550,150..550) holding Inner (200..350,200..300), both expanded. */
    private static GraphObjectFrame[] outerAndInner(PetriNetsPanel panel) {
        GraphObjectFrame outer = new GraphObjectFrame("Outer", new Rectangle(150, 150, 400, 400));
        panel.addObjectFrame(outer);
        GraphObjectFrame inner = new GraphObjectFrame("Inner", new Rectangle(200, 200, 150, 100));
        panel.addObjectFrame(inner);
        panel.getCanvasModel().nest(inner, outer);
        clearSelection(panel);
        return new GraphObjectFrame[]{outer, inner};
    }

    @Test
    public void draggingADoublyNestedFrameFromTheRootCanvasDoesNotMoveIt() {
        PetriNetsPanel panel = freshPanel();
        GraphObjectFrame[] frames = outerAndInner(panel);
        GraphObjectFrame inner = frames[1];
        Rectangle before = new Rectangle(inner.getBounds());

        dragFromTo(panel, inner.getBounds().x + 20, headerY(inner), 500, 500);

        assertEquals("Inner is not a direct child of the root canvas, so its header must not drag",
                before, inner.getBounds());
    }

    @Test
    public void draggingTheSameFrameFromItsActualParentsCanvasDoesMoveIt() {
        // The positive control: once Outer is the focused (open) object, Inner is a direct
        // child of it, and the exact same gesture that just refused to move Inner must now work.
        PetriNetsPanel panel = freshPanel();
        GraphObjectFrame[] frames = outerAndInner(panel);
        GraphObjectFrame outer = frames[0];
        GraphObjectFrame inner = frames[1];
        panel.openObjectCanvas(outer);
        Rectangle before = new Rectangle(inner.getBounds());

        dragFromTo(panel, inner.getBounds().x + 20, headerY(inner), 400, 400);

        assertTrue("from Inner's own parent's canvas the drag must actually move it",
                !before.equals(inner.getBounds()));
    }

    @Test
    public void resizingADoublyNestedFrameFromTheRootCanvasDoesNothing() {
        PetriNetsPanel panel = freshPanel();
        GraphObjectFrame[] frames = outerAndInner(panel);
        GraphObjectFrame inner = frames[1];
        Rectangle before = new Rectangle(inner.getBounds());
        int handleX = before.x + before.width - 3;
        int handleY = before.y + before.height - 3;

        dragFromTo(panel, handleX, handleY, handleX + 200, handleY + 200);

        assertEquals("the resize handle must not work on a frame that is not a direct child here",
                before, inner.getBounds());
    }

    @Test
    public void deleteToolCannotRemoveADoublyNestedFrameFromTheRootCanvas() {
        PetriNetsPanel panel = freshPanel();
        GraphObjectFrame[] frames = outerAndInner(panel);
        GraphObjectFrame inner = frames[1];
        panel.setTool(CanvasTool.DELETE);

        PetriNetsPanel.MouseHandler handler = mouseHandlerOf(panel);
        handler.mousePressed(event(panel, MouseEvent.MOUSE_PRESSED,
                inner.getBounds().x + 20, headerY(inner)));

        assertTrue("Inner must still be on the canvas",
                panel.getCanvasModel().getFrames().contains(inner));
        assertSame("and still nested exactly where it was",
                frames[0], panel.getCanvasModel().enclosingOf(inner));
    }

    @Test
    public void marqueeSelectingADoublyNestedFrameAndPressingDeleteDoesNotDeleteIt() {
        // The gap moveSelectionBy/deleteSelectedObjects had on their own: a marquee (selectIn)
        // catches a frame by whether it is merely drawn here, same as a click does, so a
        // rubber-band that swept over Inner put it in the selection same as a direct click would.
        PetriNetsPanel panel = freshPanel();
        GraphObjectFrame[] frames = outerAndInner(panel);
        GraphObjectFrame inner = frames[1];
        // Catches Inner's centre (275,250) but not Outer's (350,350) - if Outer were caught too,
        // it would legitimately be eligible on its own, and acting on it would cascade to Inner
        // as its rightful subtree, which is correct and would defeat the point of this test.
        marquee(panel, 50, 50, 300, 300);

        panel.deleteSelectedObjects();

        assertTrue("a frame this canvas cannot edit must not be deleted by a bulk delete either",
                panel.getCanvasModel().getFrames().contains(inner));
    }

    @Test
    public void marqueeSelectingADoublyNestedFrameAndDraggingDoesNotMoveIt() {
        PetriNetsPanel panel = freshPanel();
        GraphObjectFrame[] frames = outerAndInner(panel);
        GraphObjectFrame inner = frames[1];
        Rectangle before = new Rectangle(inner.getBounds());
        // Catches Inner's centre (275,250) but not Outer's (350,350) - if Outer were caught too,
        // it would legitimately be eligible on its own, and acting on it would cascade to Inner
        // as its rightful subtree, which is correct and would defeat the point of this test.
        marquee(panel, 50, 50, 300, 300);

        panel.moveSelectionBy(300, 300);

        assertEquals("a frame caught by a marquee but not owned by this canvas must not move",
                before, inner.getBounds());
    }

    @Test
    public void ctrlDDoesNotDuplicateAMarqueeCaughtDoublyNestedFrame() {
        PetriNetsPanel panel = freshPanel();
        GraphObjectFrame[] frames = outerAndInner(panel);
        GraphObjectFrame inner = frames[1];
        // Gives Inner an actual net, so duplicateObject reaches the code this test means to
        // exercise instead of returning early on "the Petri-object has no net to copy yet" -
        // a short-circuit that would make the assertion below pass for the wrong reason.
        GraphPetriPlace innerPlace = new GraphPetriPlace(new PetriP("Pin", 1), 900);
        innerPlace.setNewCoordinates(new Point2D.Double(250, 240));
        panel.getGraphNet().getGraphPetriPlaceList().add(innerPlace);
        panel.getCanvasModel().claim(inner, innerPlace);
        int before = panel.getCanvasModel().getFrames().size();
        // Catches Inner's centre (275,250) but not Outer's (350,350) - if Outer were caught too,
        // it would legitimately be eligible on its own, and acting on it would cascade to Inner
        // as its rightful subtree, which is correct and would defeat the point of this test.
        marquee(panel, 50, 50, 300, 300);

        panel.duplicateSelection();

        assertEquals("nothing eligible was selected, so nothing should have been duplicated",
                before, panel.getCanvasModel().getFrames().size());
    }
}

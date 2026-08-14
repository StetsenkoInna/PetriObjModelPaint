package ua.stetsenkoinna.graphpresentation;

import org.junit.Test;

import ua.stetsenkoinna.graphnet.GraphObjectFrame;
import ua.stetsenkoinna.petriobj.PetriP;
import ua.stetsenkoinna.petriobj.PetriT;

import java.awt.Rectangle;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionListener;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/**
 * Dragging a Petri-object's frame by its header used to move only its rectangle - nothing
 * checked afterward whether that rectangle still landed inside the frame it was nested in, the
 * way an ordinary element's drop already gets checked by {@code confirmMoveBetweenObjects}. A
 * nested object could end up drawn entirely outside its parent while the model still said it
 * was nested there, which is what the reported "the inner object can be dragged past the outer
 * one's border" actually was: not a missing margin, a missing check.
 */
public class FrameReparentingTest {

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

    /** Drags whatever is under (fromX, fromY) - a frame's header, in every test here - to (toX, toY). */
    private static void dragFromTo(PetriNetsPanel panel, int fromX, int fromY, int toX, int toY) {
        PetriNetsPanel.MouseHandler handler = mouseHandlerOf(panel);
        MouseMotionListener motion = motionHandlerOf(panel);
        handler.mousePressed(event(panel, MouseEvent.MOUSE_PRESSED, fromX, fromY));
        motion.mouseDragged(event(panel, MouseEvent.MOUSE_DRAGGED, (fromX + toX) / 2, (fromY + toY) / 2));
        motion.mouseDragged(event(panel, MouseEvent.MOUSE_DRAGGED, toX, toY));
        handler.mouseReleased(event(panel, MouseEvent.MOUSE_RELEASED, toX, toY));
    }

    private static int headerY(GraphObjectFrame frame) {
        return frame.getBounds().y + GraphObjectFrame.HEADER_HEIGHT / 2;
    }

    @Test
    public void draggingANestedFrameOutsideItsParentLiftsItToTheTopLevel() {
        PetriNetsPanel panel = freshPanel();
        GraphObjectFrame outer = new GraphObjectFrame("Outer", new Rectangle(50, 50, 400, 400));
        panel.addObjectFrame(outer);
        GraphObjectFrame inner = new GraphObjectFrame("Inner", new Rectangle(100, 100, 150, 100));
        panel.addObjectFrame(inner);
        panel.getCanvasModel().nest(inner, outer);
        assertSame(outer, panel.getCanvasModel().enclosingOf(inner));
        // Only Inner's own direct parent's canvas can move it at all (isFrameOnThisCanvas) - see
        // FrameEditScopeTest for the boundary itself. This test is about what a valid drag does
        // once it is allowed to happen, so it opens Outer's canvas first, the same as a user
        // would have to before dragging Inner for real.
        panel.openObjectCanvas(outer);

        // Drags the inner frame's header far to the right, well clear of the outer frame.
        int fromX = inner.getBounds().x + 20;
        int fromY = headerY(inner);
        dragFromTo(panel, fromX, fromY, 900, 900);

        assertNull("dragged clear of every frame, it must be lifted to the top level",
                panel.getCanvasModel().enclosingOf(inner));
    }

    @Test
    public void draggingAFrameIntoASiblingNestsItThere() {
        PetriNetsPanel panel = freshPanel();
        GraphObjectFrame source = new GraphObjectFrame("Source", new Rectangle(50, 50, 200, 200));
        panel.addObjectFrame(source);
        GraphObjectFrame sink = new GraphObjectFrame("Sink", new Rectangle(500, 500, 300, 300));
        panel.addObjectFrame(sink);
        assertNull(panel.getCanvasModel().enclosingOf(source));

        int fromX = source.getBounds().x + 20;
        int fromY = headerY(source);
        // Lands somewhere safely inside Sink's rectangle, clear of its own header.
        dragFromTo(panel, fromX, fromY, 600, 650);

        assertSame("dropped inside another frame's rectangle, it must nest into it",
                sink, panel.getCanvasModel().enclosingOf(source));
    }

    @Test
    public void draggingAFrameAndStayingInsideItsParentLeavesNestingAlone() {
        // The failure mode a naive fix would introduce: a nested frame's own centre trivially
        // sits inside its own rectangle, so a search that does not exclude the dragged frame's
        // own subtree could "find" the frame itself and read that as having left its parent.
        PetriNetsPanel panel = freshPanel();
        GraphObjectFrame outer = new GraphObjectFrame("Outer", new Rectangle(50, 50, 400, 400));
        panel.addObjectFrame(outer);
        GraphObjectFrame inner = new GraphObjectFrame("Inner", new Rectangle(100, 100, 150, 100));
        panel.addObjectFrame(inner);
        panel.getCanvasModel().nest(inner, outer);

        int fromX = inner.getBounds().x + 20;
        int fromY = headerY(inner);
        // A small nudge that keeps the whole rectangle inside Outer's.
        dragFromTo(panel, fromX, fromY, fromX + 15, fromY + 15);

        assertSame("a small move that never left the parent must not change the nesting",
                outer, panel.getCanvasModel().enclosingOf(inner));
    }

    @Test
    public void draggingAParentSoItsCentreCoversItsOwnChildDoesNotNestItThere() {
        // The other half of the same exclusion: a parent dragged far enough that a child's
        // rectangle happens to end up under the parent's own centre must not be read as the
        // parent wanting to nest inside its own child - nest() would reject that as a cycle,
        // but the geometry search must not even try.
        PetriNetsPanel panel = freshPanel();
        GraphObjectFrame outer = new GraphObjectFrame("Outer", new Rectangle(50, 50, 100, 100));
        panel.addObjectFrame(outer);
        GraphObjectFrame inner = new GraphObjectFrame("Inner", new Rectangle(70, 70, 60, 40));
        panel.addObjectFrame(inner);
        panel.getCanvasModel().nest(inner, outer);

        int fromX = outer.getBounds().x + 20;
        int fromY = headerY(outer);
        // Outer moves elsewhere; its own centre still lands on Inner's rectangle wherever Inner
        // now visually sits, since moveFrame carries members and nested frames along with it.
        dragFromTo(panel, fromX, fromY, 700, 700);

        assertNull("the parent must not end up nested inside the child it just carried along",
                panel.getCanvasModel().enclosingOf(outer));
    }

    @Test
    public void theNewParentGrowsToKeepTheFrameMarginAroundTheDroppedFrame() throws Exception {
        PetriNetsPanel panel = freshPanel();
        GraphObjectFrame source = new GraphObjectFrame("Source", new Rectangle(50, 50, 100, 100));
        panel.addObjectFrame(source);
        GraphObjectFrame sink = new GraphObjectFrame("Sink", new Rectangle(500, 500, 120, 120));
        panel.addObjectFrame(sink);

        int fromX = source.getBounds().x + 20;
        int fromY = headerY(source);
        // Dropped so it lands mostly, but not entirely, inside Sink - forcing Sink to grow.
        dragFromTo(panel, fromX, fromY, 560, 560);

        assertSame(sink, panel.getCanvasModel().enclosingOf(source));
        java.lang.reflect.Field field = PetriNetsPanel.class.getDeclaredField("FRAME_MARGIN");
        field.setAccessible(true);
        int margin = field.getInt(panel);
        Rectangle sinkBounds = sink.getBounds();
        Rectangle sourceBounds = source.getBounds();
        assertTrue("Sink must have grown to keep the margin on every side that escaped it",
                sinkBounds.x <= sourceBounds.x - margin
                        && sinkBounds.y <= sourceBounds.y - margin
                        && sinkBounds.x + sinkBounds.width >= sourceBounds.x + sourceBounds.width + margin
                        && sinkBounds.y + sinkBounds.height >= sourceBounds.y + sourceBounds.height + margin);
    }
}

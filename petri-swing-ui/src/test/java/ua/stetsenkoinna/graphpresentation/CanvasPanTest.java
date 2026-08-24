package ua.stetsenkoinna.graphpresentation;

import org.junit.Test;
import ua.stetsenkoinna.petriobj.PetriP;
import ua.stetsenkoinna.petriobj.PetriT;

import javax.swing.JViewport;
import java.awt.Point;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionListener;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * That dragging with the Pan tool moves the view by exactly as much as the pointer moved, for
 * as long as the drag lasts.
 *
 * <p>This is the test the pan defect needed and did not have. Panning was measured with
 * {@code MouseEvent.getPoint()}, which is relative to the canvas panel - the very component the
 * viewport scrolls. Scrolling it slides it under a motionless pointer, so the panel-relative
 * coordinate of a fixed physical position changes by precisely the amount just scrolled, and the
 * delta feeds back into itself. The canvas then trailed the cursor and stuttered, on Windows and
 * macOS alike.
 *
 * <p>The simulation below is faithful to that: each event's panel coordinate is derived from the
 * viewport's <em>current</em> position, the way a real event's would be. That is what makes the
 * second drag step meaningful - see {@link #panTracksThePointerAcrossTheWholeDrag}.
 */
public class CanvasPanTest {

    private static final int VIEWPORT_WIDTH = 400;
    private static final int VIEWPORT_HEIGHT = 300;

    private PetriNetsPanel panel;
    private JViewport viewport;

    private void canvasInAViewportAt(int viewX, int viewY) {
        PetriP.initNext();
        PetriT.initNext();
        panel = new PetriNetsPanel(null, true);
        viewport = new JViewport();
        viewport.setView(panel);
        viewport.setSize(VIEWPORT_WIDTH, VIEWPORT_HEIGHT);
        viewport.doLayout();
        viewport.setViewPosition(new Point(viewX, viewY));
        panel.setTool(CanvasTool.PAN);
    }

    private PetriNetsPanel.MouseHandler mouseHandler() {
        for (java.awt.event.MouseListener listener : panel.getMouseListeners()) {
            if (listener instanceof PetriNetsPanel.MouseHandler handler) {
                return handler;
            }
        }
        throw new AssertionError("the panel registered no MouseHandler");
    }

    private MouseMotionListener motionHandler() {
        MouseMotionListener[] listeners = panel.getMouseMotionListeners();
        assertTrue("the panel registered no mouse motion listener", listeners.length > 0);
        return listeners[0];
    }

    /**
     * Builds the event a pointer sitting at {@code (viewportX, viewportY)} within the viewport
     * would really produce right now - its panel coordinate depends on where the view has been
     * scrolled to, which is exactly the coupling that broke panning.
     */
    private MouseEvent atViewportPoint(int id, int viewportX, int viewportY) {
        Point view = viewport.getViewPosition();
        return new MouseEvent(panel, id, System.currentTimeMillis(), 0,
                viewportX + view.x, viewportY + view.y, 1, false, MouseEvent.BUTTON1);
    }

    /**
     * The regression proper. The pointer travels 50 right and 30 down, then another 50 and 30;
     * the view has to end up 100 left and 60 up from where it started.
     *
     * <p>The second step is the one that mattered: by then the view has already moved by the
     * first delta, and the pointer's panel coordinate happens to land back on the value it had
     * at the previous event. Reading the delta from panel coordinates therefore saw no movement
     * at all and left the view where it was.
     */
    @Test
    public void panTracksThePointerAcrossTheWholeDrag() {
        canvasInAViewportAt(500, 400);

        mouseHandler().mousePressed(atViewportPoint(MouseEvent.MOUSE_PRESSED, 100, 100));

        motionHandler().mouseDragged(atViewportPoint(MouseEvent.MOUSE_DRAGGED, 150, 130));
        assertEquals("first step of the drag", new Point(450, 370), viewport.getViewPosition());

        motionHandler().mouseDragged(atViewportPoint(MouseEvent.MOUSE_DRAGGED, 200, 160));
        assertEquals("second step, where the feedback loop used to swallow the movement",
                new Point(400, 340), viewport.getViewPosition());

        mouseHandler().mouseReleased(atViewportPoint(MouseEvent.MOUSE_RELEASED, 200, 160));
    }

    /** Dragging back the other way returns the view to where it started, with nothing left over. */
    @Test
    public void panIsReversible() {
        canvasInAViewportAt(500, 400);

        mouseHandler().mousePressed(atViewportPoint(MouseEvent.MOUSE_PRESSED, 200, 200));
        motionHandler().mouseDragged(atViewportPoint(MouseEvent.MOUSE_DRAGGED, 260, 240));
        motionHandler().mouseDragged(atViewportPoint(MouseEvent.MOUSE_DRAGGED, 200, 200));

        assertEquals(new Point(500, 400), viewport.getViewPosition());
    }

    /** Panning towards the top-left corner stops there rather than scrolling into nothing. */
    @Test
    public void panStopsAtTheCanvasEdge() {
        canvasInAViewportAt(20, 20);

        mouseHandler().mousePressed(atViewportPoint(MouseEvent.MOUSE_PRESSED, 100, 100));
        motionHandler().mouseDragged(atViewportPoint(MouseEvent.MOUSE_DRAGGED, 900, 900));

        assertEquals(new Point(0, 0), viewport.getViewPosition());
    }

    /**
     * A fresh press starts a fresh pan. Leaving the previous drag's origin armed made the next
     * one jump by the difference between the two.
     */
    @Test
    public void aSecondDragDoesNotInheritTheFirstsOrigin() {
        canvasInAViewportAt(500, 400);

        mouseHandler().mousePressed(atViewportPoint(MouseEvent.MOUSE_PRESSED, 100, 100));
        motionHandler().mouseDragged(atViewportPoint(MouseEvent.MOUSE_DRAGGED, 150, 130));
        mouseHandler().mouseReleased(atViewportPoint(MouseEvent.MOUSE_RELEASED, 150, 130));
        Point afterFirstDrag = viewport.getViewPosition();

        mouseHandler().mousePressed(atViewportPoint(MouseEvent.MOUSE_PRESSED, 300, 200));
        motionHandler().mouseDragged(atViewportPoint(MouseEvent.MOUSE_DRAGGED, 310, 205));

        assertEquals(new Point(afterFirstDrag.x - 10, afterFirstDrag.y - 5),
                viewport.getViewPosition());
    }
}

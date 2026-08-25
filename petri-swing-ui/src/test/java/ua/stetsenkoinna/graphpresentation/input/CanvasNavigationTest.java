package ua.stetsenkoinna.graphpresentation.input;

import org.junit.Test;

import java.awt.Point;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * The navigation arithmetic, pinned where looking at it does not help.
 */
public class CanvasNavigationTest {

    private static final int NO_LIMIT = 100_000;

    /**
     * How far the anchor may legitimately drift, in canvas units.
     *
     * <p>A view position is a whole number of pixels, so rounding it costs up to half a pixel -
     * and half a pixel is half a canvas unit at scale 1, but two canvas units at scale 0.25. The
     * tolerance therefore has to be stated in pixels and converted, not written as a flat number
     * that silently means something different at every zoom level.
     */
    private static double roundingSlack(double scale) {
        return 1.0 / scale;
    }

    /**
     * The defect this whole class exists for. Panning must be measured from where the drag
     * began, so a long drag is just as accurate as a short one; deriving each step from the
     * previous view position instead is what let the error compound and made the canvas trail
     * the cursor.
     */
    @Test
    public void panIsMeasuredFromTheStartOfTheDragEveryTime() {
        Point viewAtStart = new Point(500, 400);
        Point grabbed = new Point(1000, 800);

        assertEquals(new Point(480, 390),
                CanvasNavigation.panTo(viewAtStart, grabbed, new Point(1020, 810), NO_LIMIT, NO_LIMIT));
        assertEquals(new Point(460, 380),
                CanvasNavigation.panTo(viewAtStart, grabbed, new Point(1040, 820), NO_LIMIT, NO_LIMIT));
        assertEquals(new Point(440, 370),
                CanvasNavigation.panTo(viewAtStart, grabbed, new Point(1060, 830), NO_LIMIT, NO_LIMIT));
    }

    /** Dragging the canvas right moves the view left - the content follows the hand. */
    @Test
    public void theViewMovesOppositeToThePointer() {
        Point moved = CanvasNavigation.panTo(
                new Point(300, 300), new Point(100, 100), new Point(160, 40), NO_LIMIT, NO_LIMIT);
        assertEquals(new Point(240, 360), moved);
    }

    @Test
    public void panStopsAtTheEdgesInsteadOfRunningOffThem() {
        // Dragged far past the top-left: clamped to the origin, not to a negative position that
        // would leave the viewport showing nothing.
        assertEquals(new Point(0, 0),
                CanvasNavigation.panTo(new Point(10, 10), new Point(0, 0), new Point(9000, 9000), 700, 700));
        // And equally far past the bottom-right.
        assertEquals(new Point(700, 700),
                CanvasNavigation.panTo(new Point(10, 10), new Point(9000, 9000), new Point(0, 0), 700, 700));
    }

    /**
     * Zooming has to hold the pointer still. Anchoring on the viewport's corner instead throws
     * the drawing further sideways the further the pointer is from that corner, so the user
     * zooms towards one element and lands somewhere else entirely.
     */
    @Test
    public void zoomKeepsWhateverIsUnderThePointerUnderThePointer() {
        Point view = new Point(200, 100);
        Point pointer = new Point(300, 150);
        double before = 1.0;
        double after = 2.0;

        double canvasX = (view.x + pointer.x) / before;
        double canvasY = (view.y + pointer.y) / before;

        Point zoomed = CanvasNavigation.zoomAbout(view, pointer, before, after, NO_LIMIT, NO_LIMIT);

        assertEquals(canvasX, (zoomed.x + pointer.x) / after, roundingSlack(after));
        assertEquals(canvasY, (zoomed.y + pointer.y) / after, roundingSlack(after));
    }

    @Test
    public void zoomingOutHoldsThePointerToo() {
        Point view = new Point(1200, 900);
        Point pointer = new Point(400, 250);

        double canvasX = (view.x + pointer.x) / 2.0;
        double canvasY = (view.y + pointer.y) / 2.0;

        Point zoomed = CanvasNavigation.zoomAbout(view, pointer, 2.0, 0.5, NO_LIMIT, NO_LIMIT);

        assertEquals(canvasX, (zoomed.x + pointer.x) / 0.5, roundingSlack(0.5));
        assertEquals(canvasY, (zoomed.y + pointer.y) / 0.5, roundingSlack(0.5));
    }

    /**
     * A fast spin or a trackpad flick arrives as one event carrying many notches. Refusing to
     * step below the floor one notch at a time would let that single event drive the scale
     * through zero, at which point the drawing disappears and every hit test divides by a
     * non-positive number.
     */
    @Test
    public void aSingleViolentSpinCannotDriveTheScaleOutOfRange() {
        assertEquals(CanvasNavigation.MIN_SCALE, CanvasNavigation.zoomStep(0.2, 400), 1e-9);
        assertEquals(CanvasNavigation.MAX_SCALE, CanvasNavigation.zoomStep(4.0, -400), 1e-9);
    }

    @Test
    public void scrollingUpZoomsInAndDownZoomsOut() {
        assertTrue(CanvasNavigation.zoomStep(1.0, -1) > 1.0);
        assertTrue(CanvasNavigation.zoomStep(1.0, 1) < 1.0);
    }

    /**
     * One notch is the same felt size wherever you are in the range. An additive step is a
     * doubling at 0.1 and imperceptible at 5.0, which is why the step is multiplicative.
     */
    @Test
    public void oneNotchIsTheSameProportionAtEveryZoomLevel() {
        double lowRatio = CanvasNavigation.zoomStep(0.5, -1) / 0.5;
        double highRatio = CanvasNavigation.zoomStep(2.0, -1) / 2.0;
        assertEquals(lowRatio, highRatio, 1e-9);
    }
}

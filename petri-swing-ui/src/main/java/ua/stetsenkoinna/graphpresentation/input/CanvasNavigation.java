package ua.stetsenkoinna.graphpresentation.input;

import java.awt.Point;

/**
 * The arithmetic behind moving the canvas under the viewport - panning by drag, scrolling by
 * wheel, and zooming without the drawing sliding out from under the pointer.
 *
 * <p>Pure geometry, deliberately separated from {@code PetriNetsPanel}: these are the two
 * calculations in the editor's navigation that are easy to get subtly wrong and impossible to
 * check by looking at them, and keeping them free of Swing is what lets a test pin them down.
 */
public final class CanvasNavigation {

    private CanvasNavigation() {
    }

    /**
     * Where the viewport should sit part-way through a pan drag.
     *
     * <p>Both mouse points must be in the <em>viewport's</em> coordinates. This is the whole
     * trick, and getting it wrong is what made panning misbehave on every platform: the obvious
     * source for a drag delta is {@code MouseEvent.getPoint()}, but that is measured against the
     * panel - which is the very thing being scrolled. Move the viewport and the panel slides
     * underneath a motionless pointer, so the next event reports a different point for the same
     * physical position, and the delta is fed back into itself. The view then trails the cursor
     * at roughly half speed and stutters. The viewport itself does not move, so measuring
     * against it is what breaks the loop.
     *
     * <p>Note that the result is derived only from where the drag <em>began</em>, never from
     * where the view currently sits. That is deliberate: it makes each step independent, so no
     * error can accumulate across a long drag.
     *
     * @param viewOriginAtDragStart the viewport's view position when the drag began
     * @param dragStart             pointer position, in viewport coordinates, when the drag began
     * @param current               pointer position, in viewport coordinates, now
     * @param maxX                  largest legal view x, i.e. view width minus extent width
     * @param maxY                  largest legal view y
     * @return the clamped view position to apply
     */
    public static Point panTo(Point viewOriginAtDragStart, Point dragStart,
                              Point current, int maxX, int maxY) {
        int x = viewOriginAtDragStart.x - (current.x - dragStart.x);
        int y = viewOriginAtDragStart.y - (current.y - dragStart.y);
        return new Point(clamp(x, maxX), clamp(y, maxY));
    }

    /**
     * Where the viewport should sit after a zoom, so that whatever canvas point was under the
     * pointer stays under the pointer.
     *
     * <p>Zooming around the viewport's origin instead - which is what happens if the view
     * position is simply left alone - throws the drawing sideways by more the further the
     * pointer is from the top-left corner, so the user zooms in on one thing and arrives
     * somewhere else. Anchoring on the pointer is what makes wheel zoom feel like a magnifying
     * glass rather than a scrollbar.
     *
     * @param viewOrigin    the viewport's current view position
     * @param pointerInView pointer position relative to the viewport, not the panel
     * @param oldScale      scale before the zoom
     * @param newScale      scale after the zoom
     * @param maxX          largest legal view x at the new scale
     * @param maxY          largest legal view y at the new scale
     * @return the clamped view position to apply
     */
    public static Point zoomAbout(Point viewOrigin, Point pointerInView,
                                  double oldScale, double newScale, int maxX, int maxY) {
        // The canvas coordinate currently under the pointer. Panel pixels are canvas units
        // times the scale, and the panel pixel under the pointer is viewOrigin + pointerInView.
        double canvasX = (viewOrigin.x + pointerInView.x) / oldScale;
        double canvasY = (viewOrigin.y + pointerInView.y) / oldScale;
        // Put that same canvas coordinate back under the same pointer at the new scale.
        int x = (int) Math.round(canvasX * newScale - pointerInView.x);
        int y = (int) Math.round(canvasY * newScale - pointerInView.y);
        return new Point(clamp(x, maxX), clamp(y, maxY));
    }

    /**
     * Applies one wheel step to the zoom level, keeping the result inside the range the canvas
     * can actually draw at.
     *
     * <p>Clamping rather than guarding a single step: a fast spin, or a trackpad flick, arrives
     * as one event carrying several notches, and a check that only refuses to go below the floor
     * one notch at a time lets that single event drive the scale straight through zero. At which
     * point the drawing vanishes and every hit test divides by a non-positive number.
     *
     * @param currentScale the scale now
     * @param notches      wheel movement, positive when scrolling down/away from the user
     * @return the new scale, within [{@value #MIN_SCALE}, {@value #MAX_SCALE}]
     */
    public static double zoomStep(double currentScale, double notches) {
        // Multiplicative, not additive: a fixed increment is a huge relative jump when zoomed
        // out to 0.1 and an imperceptible one at 5.0. Scaling by a factor per notch makes every
        // step feel the same size, which is what "one notch" is supposed to mean.
        double factor = Math.pow(ZOOM_PER_NOTCH, -notches);
        return clampScale(currentScale * factor);
    }

    /** Smallest scale the canvas may be drawn at. */
    public static final double MIN_SCALE = 0.1;
    /** Largest scale the canvas may be drawn at. */
    public static final double MAX_SCALE = 5.0;
    /** How much one wheel notch multiplies the scale by. */
    private static final double ZOOM_PER_NOTCH = 1.1;

    /**
     * @param scale any candidate scale
     * @return that scale held inside the drawable range
     */
    public static double clampScale(double scale) {
        return Math.min(MAX_SCALE, Math.max(MIN_SCALE, scale));
    }

    private static int clamp(int value, int max) {
        return Math.max(0, Math.min(value, max));
    }
}

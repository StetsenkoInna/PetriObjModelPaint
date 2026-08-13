package ua.stetsenkoinna.graphnet;

import java.awt.Rectangle;
import java.awt.geom.Point2D;
import java.util.Objects;

/**
 * A fixed point that {@link GraphArc#changeBorder()} can trim a line to exactly the way it
 * trims one to a real place or transition — used where a connection's end is not a place or a
 * transition at all, but a port on a locked object's border, or the pointer's current position
 * while a link is still being dragged.
 *
 * <p>This is what lets an arc reaching a hidden object's port, or the in-progress preview of one
 * being dragged, reuse {@code GraphArc}'s own trimming and drawing instead of a second,
 * hand-rolled copy of it: both only ever need a centre and a border to work from, and a
 * {@code PortAnchor} is exactly that, nothing else.
 */
public final class PortAnchor extends GraphElement {

    /** Pinned for the same reason as {@link GraphPlace#serialVersionUID}. */
    private static final long serialVersionUID = 6000598388561772887L;

    private final Point2D center;
    private final int border;

    /**
     * @param center where this anchor sits, in canvas coordinates
     * @param border radius to trim an arc's line by — a port's own {@code FramePort.RADIUS},
     *        or {@code 0} for a bare point, like the pointer mid-drag, that nothing should be
     *        trimmed against
     */
    public PortAnchor(Point2D center, int border) {
        this.center = Objects.requireNonNull(center, "center");
        this.border = border;
    }

    @Override
    public Point2D getGraphElementCenter() {
        return center;
    }

    @Override
    public int getBorder() {
        return border;
    }

    @Override
    public boolean isCircular() {
        return true;
    }

    /**
     * @return a square around the centre, {@code border} on every side - an anchor has no shape
     *         of its own to draw, but this is the honest bounding box of what {@link #getBorder()}
     *         already claims it occupies, for a caller that only knows it as a {@link CanvasItem}
     */
    @Override
    public Rectangle getBounds() {
        return new Rectangle(
                (int) Math.round(center.getX() - border), (int) Math.round(center.getY() - border),
                border * 2, border * 2);
    }
}

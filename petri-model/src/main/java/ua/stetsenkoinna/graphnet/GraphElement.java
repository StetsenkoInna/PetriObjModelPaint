package ua.stetsenkoinna.graphnet;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.geom.Point2D;
import java.io.Serializable;

/**
 * Base class for all drawable graph elements (places, transitions, arcs).
 * Moved from graphpresentation to graphnet so the graph model layer
 * has no dependency on the UI presentation layer.
 */
public class GraphElement implements Serializable, CanvasItem {

    /**
     * Pinned to the value the compiler already computed for this class's previous shape, before
     * it gained {@code implements CanvasItem}. Every place and transition in every saved
     * {@code .pns} is a {@code GraphElement} subclass instance, and none of them declare their
     * own id, so left to the compiler this would have recomputed the moment the interface was
     * added and every file on disk would have stopped loading.
     */
    private static final long serialVersionUID = 7232492741244001431L;

    private int lineWidth;
    private Color color;

    public void drawGraphElement(Graphics2D g2) {
    }

    public void setNewCoordinates(Point2D p) {
    }

    public boolean isGraphElement(Point2D p) {
        return false;
    }

    public Point2D getGraphElementCenter() {
        return null;
    }

    public String getType() {
        return null;
    }

    public int getBorder() {
        return 0;
    }

    /**
     * @return true if an arc reaching this element should be trimmed to it by a simple radius
     *         around its centre — a place's circle, or anything that stands in for one, like a
     *         port; false to trim against a rectangle instead, the way a transition is. See
     *         {@link GraphArc#changeBorder()}, the one place this actually matters.
     */
    public boolean isCircular() {
        return false;
    }

    public int getId() {
        return 0;
    }

    public String getName() {
        return null;
    }

    public int getNumber() {
        return 0;
    }

    public int getLineWidth() {
        return lineWidth;
    }

    public void setLineWidth(int lineWidth) {
        this.lineWidth = lineWidth;
    }

    public Color getColor() {
        return color;
    }

    public void setColor(Color color) {
        this.color = color;
    }

    /**
     * @return the rectangle this element occupies, or {@code null} for a subclass that draws
     *         nothing of its own to bound - overridden by every concrete shape (see
     *         {@link GraphPlace#getBounds()}, {@link GraphTransition#getBounds()}).
     */
    @Override
    public Rectangle getBounds() {
        return null;
    }

    @Override
    public boolean containsPoint(Point2D point) {
        return isGraphElement(point);
    }

    /**
     * Moves this element by a delta, keeping whatever anchors its shape - every concrete element
     * positions itself from its centre, so this reads the centre back, shifts it, and hands the
     * result to {@link #setNewCoordinates}, which is what {@code moveSelectionBy} and the drag
     * handlers already did by hand at every call site before this existed.
     */
    @Override
    public void moveBy(int dx, int dy) {
        Point2D centre = getGraphElementCenter();
        if (centre == null) {
            return;
        }
        setNewCoordinates(new Point2D.Double(centre.getX() + dx, centre.getY() + dy));
    }
}

package ua.stetsenkoinna.graphnet;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.geom.Point2D;
import java.io.Serializable;

/**
 * Base class for all drawable graph elements (places, transitions, arcs).
 * Moved from graphpresentation to graphnet so the graph model layer
 * has no dependency on the UI presentation layer.
 */
public class GraphElement implements Serializable {

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
}

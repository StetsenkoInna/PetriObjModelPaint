package ua.stetsenkoinna.graphpresentation;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.geom.Point2D;
import java.io.Serializable;

/**
 *
 * @author Ольга
 */
public class GraphElement implements Serializable {

    private int lineWidth; // replaced from subclasses
    private Color color;  // replaced from subclasses

    public void drawGraphElement(Graphics2D g2) {
        // todo
    }

    public void setNewCoordinates(Point2D p) {
        //todo
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

    public int getId() {
        return 0;
    }

    public String getName() {
        return null;
    }

    public int getNumber() {
        return 0;
    }

    /**
     * @return the lineWidth
     */
    public int getLineWidth() {
        return lineWidth;
    }

    /**
     * @param lineWidth the lineWidth to set
     */
    public void setLineWidth(int lineWidth) {
        this.lineWidth = lineWidth;
    }

    /**
     * @return the color
     */
    public Color getColor() {
        return color;
    }

    /**
     * @param color the color to set
     */
    public void setColor(Color color) {
        this.color = color;
    }
}

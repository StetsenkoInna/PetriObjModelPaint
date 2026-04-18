package ua.stetsenkoinna.graphnet;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.geom.Line2D;
import java.awt.geom.Point2D;
import java.awt.geom.RoundRectangle2D;

public class GraphTransition extends GraphElement {

    private static int height = 50;
    private static final int defaultWidth = 19;
    private int width = 19;

    private RoundRectangle2D graphElement = new RoundRectangle2D.Double(0, 0, getWidth(), getHeight(), 6, 6);

    public static int getHeight() { return height; }
    public static void setHeight(int aHeight) { height = aHeight; }
    public int getWidth() { return width; }
    public void setWidth(int aWidth) { width = aWidth; }
    public static int getDefaultWidth() { return defaultWidth; }

    public GraphTransition() {
        super.setLineWidth(3);
        super.setColor(Color.BLACK);
    }

    @Override
    public void drawGraphElement(Graphics2D g2) {
        graphElement.setRoundRect(graphElement.getX(), graphElement.getY(), getWidth(), getHeight(), 6, 6);
        g2.setStroke(new BasicStroke(getLineWidth()));
        g2.setColor(getColor());
        g2.draw(graphElement);
        g2.setColor(Color.WHITE);
        g2.fill(graphElement);
        g2.setColor(getColor());
    }

    @Override
    public void setNewCoordinates(Point2D p) {
        graphElement.setFrame(p.getX() - (double) getWidth() / 2, p.getY() - (double) getHeight() / 2, getWidth(), getHeight());
    }

    @Override
    public boolean isGraphElement(Point2D p) {
        return graphElement.contains(p)
                || new Line2D.Double(
                        new Point2D.Double(graphElement.getMaxX(), graphElement.getMinY()),
                        new Point2D.Double(graphElement.getMinX(), graphElement.getMaxY())
                   ).ptSegDist(p) < getWidth() * 2;
    }

    @Override
    public Point2D getGraphElementCenter() {
        return new Point2D.Double(graphElement.getX() + (double) getWidth() / 2, graphElement.getY() + (double) getHeight() / 2);
    }

    @Override
    public String getType() { return graphElement.getClass().toString(); }

    @Override
    public int getBorder() { return getDefaultWidth() / 2; }

    public RoundRectangle2D getGraphElement() { return graphElement; }
    public void setGraphElement(RoundRectangle2D graphElement) { this.graphElement = graphElement; }
}

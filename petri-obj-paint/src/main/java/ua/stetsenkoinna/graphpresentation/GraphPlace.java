package ua.stetsenkoinna.graphpresentation;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Point2D;

/**
 *
 * @author Ольга
 */
public class GraphPlace extends GraphElement {
    
    private static  int diameter = 40; // діаметр кола

    /**
     * @return the diameter
     */
    public static int getDiameter() {
        return diameter;
    }

    /**
     * @param aDiameter the diameter to set
     */
    public static void setDiameter(int aDiameter) {
        diameter = aDiameter;
    }
 
    private Ellipse2D graphElement=new Ellipse2D.Double(0,0, getDiameter(), getDiameter());  // координати розташування кола

    public GraphPlace() {
        super.setLineWidth(3);
        super.setColor(Color.BLACK);
    }

    @Override
    public void drawGraphElement(Graphics2D g2) {
        g2.setStroke(new BasicStroke(getLineWidth()));
        g2.setColor(getColor());
        g2.draw(graphElement);
        g2.setColor(Color.WHITE);
        g2.fill(graphElement);
        g2.setColor(getColor());
    }

    @Override
    public void setNewCoordinates(Point2D p) {
            graphElement.setFrame(
                    p.getX() - (double) getDiameter() / 2,
                    p.getY() - (double) getDiameter() / 2, getDiameter(), getDiameter()
            );
    }

    @Override
    public boolean isGraphElement(Point2D p) {
        return graphElement.contains(p);
    }

    @Override
    public Point2D getGraphElementCenter() {
        return new Point2D.Double(
                graphElement.getX() + (double) getDiameter() / 2,
                graphElement.getY() + (double) getDiameter() / 2
        );
    }

    @Override
    public String getType() {
        return graphElement.getClass().toString();
    }

    @Override
    public  int getBorder() {return getDiameter() / 2;}

    public Ellipse2D getGraphElement() {
        return graphElement;
    }

    public void setGraphElement(Ellipse2D graphElement) {
        this.graphElement = graphElement;
    }

}

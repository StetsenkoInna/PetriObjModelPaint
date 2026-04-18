package ua.stetsenkoinna.graphnet;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.geom.AffineTransform;
import java.awt.geom.Line2D;
import java.awt.geom.Point2D;
import java.io.Serializable;

public class GraphArc implements Serializable {

    private Line2D graphElement;
    private GraphElement beginElement;
    private GraphElement endElement;

    private Point2D avgLine = new Point2D.Double(0, 0);
    private boolean firstArc;
    private boolean secondArc;

    private int lineWidth;
    private Color color;

    public GraphArc() {
        color = Color.BLACK;
        lineWidth = 1;
        firstArc = false;
        secondArc = false;
    }

    public void drawGraphElement(Graphics2D g) {
    }

    public void settingNewArc(GraphElement e) {
        if (e != null) {
            setBeginElement(e);
            graphElement = new Line2D.Double(0, 0, 0, 0);
        }
    }

    public boolean finishSettingNewArc(GraphElement e) {
        if (beginElement != null) {
            if (beginElement.getClass().equals(e.getClass())) {
                return false;
            } else {
                setEndElement(e);
                return true;
            }
        } else {
            return false;
        }
    }

    public void setBeginElement(GraphElement e) {
        if (e != null) { beginElement = e; }
    }

    public void setEndElement(GraphElement e) {
        if (e != null) { endElement = e; }
    }

    public boolean isGraphElement(Point2D p) { return graphElement.contains(p); }

    public void movingBeginElement(Point2D p) {
        if (endElement != null) { graphElement.setLine(p, endElement.getGraphElementCenter()); }
    }

    public void movingEndElement(Point2D p) {
        if (beginElement != null) { graphElement.setLine(beginElement.getGraphElementCenter(), p); }
    }

    public void setNewCoordinates(Point2D p) {
        graphElement.setLine(beginElement.getGraphElementCenter(), p);
    }

    public void drawArrowHead(Graphics2D g) {
        AffineTransform tx = new AffineTransform();
        Polygon arrowHead = new Polygon();
        arrowHead.addPoint(0, 0);
        arrowHead.addPoint(-5, -10);
        arrowHead.addPoint(5, -10);
        tx.setToIdentity();
        double angle = Math.atan2(graphElement.getY2() - graphElement.getY1(), graphElement.getX2() - graphElement.getX1());
        tx.translate(graphElement.getX2(), graphElement.getY2());
        tx.rotate((angle - Math.PI / 2d));
        Graphics2D g2 = (Graphics2D) g.create();
        g2.transform(tx);
        g2.fill(arrowHead);
        g2.dispose();
    }

    public void changeBorder() {
        double x, y, r, rr, yy, xx, dy, dx, k, b;
        r = endElement.getBorder();
        rr = beginElement.getBorder();
        Point2D beginCenter = beginElement.getGraphElementCenter();
        Point2D endCenter = endElement.getGraphElementCenter();

        y = endCenter.getY();
        x = endCenter.getX();
        yy = beginCenter.getY();
        xx = beginCenter.getX();
        dy = y - yy;
        dx = x - xx;
        double arcLength = Math.sqrt(dy * dy + dx * dx);
        double halfH = (double) GraphTransition.getHeight() / 2;

        if (dx == 0) {
            if (beginElement.getClass().equals(GraphPetriPlace.class)) {
                rr = rr / arcLength;
                xx = xx + rr * dx;
                yy = yy + rr * dy;
            } else {
                double halfW = (double) ((GraphTransition) beginElement).getWidth() / 2;
                xx = (xx < x) ? xx + halfW : xx - halfW;
            }
            if (endElement.getClass().equals(GraphPetriPlace.class)) {
                r = r / arcLength;
                x = x + r * dx;
                y = y + r * dy;
            } else {
                double halfW = (double) ((GraphTransition) endElement).getWidth() / 2;
                x = (xx < x) ? x - halfW : x + halfW;
            }
            double slide = 10;
            if (firstArc) {
                graphElement.setLine(new Point2D.Double(xx, yy - slide), new Point2D.Double(x, y - slide));
            } else if (secondArc) {
                graphElement.setLine(new Point2D.Double(xx, yy + slide), new Point2D.Double(x, y + slide));
            } else {
                graphElement.setLine(new Point2D.Double(xx, yy), new Point2D.Double(x, y));
            }
        } else {
            k = dy / dx;
            b = y - k * x;

            if (beginElement.getClass().equals(GraphPetriPlace.class)) {
                rr = rr / arcLength;
                xx = xx + rr * dx;
                yy = yy + rr * dy;
            } else {
                double halfW = (double) ((GraphTransition) beginElement).getWidth() / 2;
                Point2D.Double p1, p2;
                if (Math.abs(k) <= halfH / halfW) {
                    p1 = new Point2D.Double(xx - halfW, b + k * (xx - halfW));
                    p2 = new Point2D.Double(xx + halfW, b + k * (xx + halfW));
                } else {
                    p1 = new Point2D.Double((yy - halfH - b) / k, yy - halfH);
                    p2 = new Point2D.Double((yy + halfH - b) / k, yy + halfH);
                }
                if (p1.distance(endCenter) < p2.distance(endCenter)) { xx = p1.x; yy = p1.y; }
                else { xx = p2.x; yy = p2.y; }
            }

            if (endElement.getClass().equals(GraphPetriPlace.class)) {
                r = r / arcLength;
                x = x - r * dx;
                y = y - r * dy;
            } else {
                double halfW = (double) ((GraphTransition) endElement).getWidth() / 2;
                Point2D.Double p1, p2;
                if (Math.abs(k) <= halfH / halfW) {
                    p1 = new Point2D.Double(x - halfW, b + k * (x - halfW));
                    p2 = new Point2D.Double(x + halfW, b + k * (x + halfW));
                } else {
                    p1 = new Point2D.Double((y - halfH - b) / k, y - halfH);
                    p2 = new Point2D.Double((y + halfH - b) / k, y + halfH);
                }
                if (p1.distance(beginCenter) < p2.distance(beginCenter)) { x = p1.x; y = p1.y; }
                else { x = p2.x; y = p2.y; }
            }

            double slide = 8;
            if (firstArc) {
                if (Math.abs(k) <= 2) {
                    graphElement.setLine(new Point2D.Double(xx, yy - slide), new Point2D.Double(x, y - slide));
                } else {
                    graphElement.setLine(new Point2D.Double(xx - slide, yy), new Point2D.Double(x - slide, y));
                }
            } else if (secondArc) {
                if (Math.abs(k) <= 2) {
                    graphElement.setLine(new Point2D.Double(xx, yy + slide), new Point2D.Double(x, y + slide));
                } else {
                    graphElement.setLine(new Point2D.Double(xx + slide, yy), new Point2D.Double(x + slide, y));
                }
            } else {
                graphElement.setLine(new Point2D.Double(xx, yy), new Point2D.Double(x, y));
            }
        }
    }

    public void twoArcs(GraphArc t) {
        t.firstArc = true;
        this.secondArc = true;
        t.changeBorder();
        this.changeBorder();
    }

    public void updateCoordinates() { changeBorder(); }

    public boolean isEnoughDistance(Point2D p) { return graphElement.ptSegDist(p) < 3; }

    public void printTwoState() {
        System.out.println(this.beginElement.getName() + "-> " + this.firstArc + ", " + this.secondArc);
    }

    public Line2D getGraphElement() { return graphElement; }
    public void setGraphElement(Line2D graphElement) { this.graphElement = graphElement; }
    public Point2D getAvgLine() { return avgLine; }
    public void setAvgLine(Point2D avgLine) { this.avgLine = avgLine; }
    public GraphElement getBeginElement() { return beginElement; }
    public GraphElement getEndElement() { return endElement; }
    public void addElementToArrayList() { }
    public void setPetriElements() { }
    public int getId() { return 0; }
    public int getQuantity() { return 1; }
    public void setQuantity(int i) { }
    public boolean getIsInf() { return false; }
    public void setInf(boolean i) { }
    public Color getColor() { return color; }
    public void setColor(Color color) { this.color = color; }
    public int getLineWidth() { return lineWidth; }
    public void setLineWidth(int lineWidth) { this.lineWidth = lineWidth; }
    public boolean isFirstArc() { return firstArc; }
    public void setFirstArc(boolean first) { this.firstArc = first; }
    public boolean isSecondArc() { return secondArc; }
    public void setSecondArc(boolean second) { this.secondArc = second; }
}

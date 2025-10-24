package ua.stetsenkoinna.graphnet;

import ua.stetsenkoinna.PetriObj.ArcOut;
import ua.stetsenkoinna.graphpresentation.GraphArc;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 *
 * @author Inna
 */
public class GraphArcOut extends GraphArc implements Serializable {

    private static ArrayList<GraphArcOut> graphArcOutList = new ArrayList<>();

    private final ArcOut arc;

    public GraphArcOut() { // для створення тимчасової дуги (тільки для промальовки)
        super();
        arc = new ArcOut();
        super.setLineWidth(1);
        super.setColor(Color.BLACK);
    }

    public GraphArcOut(ArcOut arcout) {
        arc = arcout;
        super.setLineWidth(1);
        super.setColor(Color.BLACK);
    }

    public ArcOut getArcOut() {
        return arc;
    }

    @Override
    public void setPetriElements() {
        arc.setQuantity(arc.getQuantity());
        arc.setNumT(super.getBeginElement().getNumber());
        arc.setNameT(super.getBeginElement().getName());
        arc.setNumP(super.getEndElement().getNumber());
        arc.setNameP(super.getEndElement().getName());
        addElementToArrayList();
    }

    @Override
    public void addElementToArrayList() {
        if (graphArcOutList == null) {
            graphArcOutList = new ArrayList<>();
        }
        graphArcOutList.add(this);
    }

    @Override
    public void drawGraphElement(Graphics2D g) {
        Graphics2D g2 = (Graphics2D) g;
        g2.setStroke(new BasicStroke(getLineWidth()));
        g2.setColor(getColor());
        g2.draw(this.getGraphElement());
        drawArrowHead(g2);
        if (arc.getQuantity() != 1 || arc.kIsParam()) {
            String quantityString = arc.kIsParam()
                    ? arc.getKParamName()
                    : Integer.toString(arc.getQuantity());
            this.getAvgLine().setLocation((this.getGraphElement().getX1() + this.getGraphElement().getX2()) / 2, (this.getGraphElement().getY1() + this.getGraphElement().getY2()) / 2);
            g2.drawLine((int) this.getAvgLine().getX() + 5, (int) this.getAvgLine().getY() - 5, (int) this.getAvgLine().getX() - 5, (int) this.getAvgLine().getY() + 5);

            // shift two arcs
            float textX = (float) this.getAvgLine().getX();
            float textY = (float) this.getAvgLine().getY() - 7;
            if (this.isFirstArc()) {
                textX -= 10; // left shift for the first arc
                textY -= 5;  // upper shift
            } else if (this.isSecondArc()) {
                textX += 10; // right shift for the second arc
                textY += 5;  // down shift
            }
            g2.drawString(quantityString, textX, textY);
        }
    }

    public static ArrayList<GraphArcOut> getGraphArcOutList() {
        return graphArcOutList;
    }

    public static ArrayList<ArcOut> getArcOutList() {
        ArrayList<ArcOut> arrayTieOut = new ArrayList<>();
        for (GraphArcOut e : graphArcOutList) {
            arrayTieOut.add(e.getArcOut());
        }
        return arrayTieOut;
    }

    public static void setNullTieOutList() {
        graphArcOutList.clear();
    }

    public static void addGraphTieOutList(List<GraphArcOut> tieOut) { // added by Olha 14/11/2012
        graphArcOutList.addAll(tieOut);
    }

    @Override
    public int getQuantity() {  //потрібно для правильної роботи методу getQuantity() батьківського класу
        return arc.getQuantity();
    }

    @Override
    public void setQuantity(int i) {
        arc.setQuantity(i);
    }
}

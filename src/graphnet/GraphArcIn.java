/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package graphnet;

import PetriObj.ArcIn;
import graphpresentation.GraphArc;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Stroke;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 *
 * @author Инна
 */
public class GraphArcIn extends GraphArc implements Serializable {

    private static ArrayList<GraphArcIn> graphArcInList = new ArrayList<>(); // added by Olha 24.09.12, cjrrect by Inna 28.11.2012
    private ArcIn arc;
   
   
  
    public GraphArcIn() { // додано Олею 28.09.12 для створення тимчасової дуги (тільки для промальовки)  
        super();
        arc = new ArcIn();
        //System.out.println("GraphArcIn  "+ arc.getNameP()+"  "+arc.getNumP()+"  "+arc.getNameT()+"  "+arc.getNumT());
        super.setLineWidth(1);
        super.setColor(Color.BLACK);
    }
        
    public GraphArcIn(ArcIn arcin){
        arc = arcin;
        super.setLineWidth(1);
        super.setColor(Color.BLACK);
   
    }
    public ArcIn getArcIn()
    {
    return arc;
    }

    @Override
    public void setPetriElements() {
        arc.setQuantity(arc.getQuantity()); // ?? зайва операція?
        arc.setInf(arc.getIsInf());
        arc.setNumP(super.getBeginElement().getNumber());
        arc.setNumT(super.getEndElement().getNumber());
        arc.setNameP(super.getBeginElement().getName());
        arc.setNameT(super.getEndElement().getName());
      
     
        addElementToArrayList(); //// added by Olha 24.09.12
    }

    @Override
    public void addElementToArrayList() {  // added by Olha 24.09.12
        if (graphArcInList == null) {
            graphArcInList = new ArrayList<>();
        }
        graphArcInList.add(this);
    }

    @Override
    public void drawGraphElement(Graphics2D g) {
        Graphics2D g2 = (Graphics2D) g;
        if (arc.getIsInf()) {
            Stroke drawingStroke = new BasicStroke(1, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL, 0, new float[]{4, 4}, 0);
            g2.setStroke(drawingStroke);
            g2.draw(this.getGraphElement());
            drawArrowHead(g2);
        } else {
            g2.setStroke(new BasicStroke(getLineWidth()));
            g2.setColor(getColor());
            g2.draw(this.getGraphElement());
            drawArrowHead(g2);
        }
        if (arc.getQuantity() != 1 || arc.kIsParam()) {
            String quantityString = arc.kIsParam() // added by Katya 08.12.2016
                ? arc.getKParamName()
                : Integer.toString(arc.getQuantity());
            this.getAvgLine().setLocation((this.getGraphElement().getX1() + this.getGraphElement().getX2()) / 2, (this.getGraphElement().getY1() + this.getGraphElement().getY2()) / 2);
            g2.drawLine((int) this.getAvgLine().getX() + 5, (int) this.getAvgLine().getY() - 5, (int) this.getAvgLine().getX() - 5, (int) this.getAvgLine().getY() + 5);
            g2.drawString(quantityString, (float) this.getAvgLine().getX(), (float) this.getAvgLine().getY() - 7);
        }
        if(this.isFirstArc()||this.isSecondArc()){ // важливо для правильної відмальовки після запуску мережі
            this.updateCoordinates();
        }
    }
    
    public static ArrayList<GraphArcIn> getGraphArcInList() {
        return graphArcInList;
    }
    public static ArrayList<ArcIn> getArcInList() {  // added by Inna 1.11.2012
        
        ArrayList<ArcIn> arrayArcIn = new ArrayList <>();
        for (GraphArcIn e: graphArcInList)
            arrayArcIn.add(e.getArcIn());
        return arrayArcIn;
    }
    

    public static void setNullArcInList() {
        graphArcInList.clear();
    }
  
    @Override
    public int getQuantity(){
            return arc.getQuantity();
        }
    @Override
   public void setQuantity(int i){
            arc.setQuantity(i);
        }
    @Override
   public boolean getIsInf(){
            return arc.getIsInf();
        }
    @Override
   public void setInf(boolean i){
            arc.setInf(i);
        } 
    public static void addGraphArcInList(List<GraphArcIn> tieIn){ // added by Olha 14/11/2012
        for (GraphArcIn ti:tieIn){
            graphArcInList.add(ti);
        }
    }

    

    
    
    
    
}

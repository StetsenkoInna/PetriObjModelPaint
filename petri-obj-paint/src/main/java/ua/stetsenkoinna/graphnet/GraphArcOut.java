/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
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
 * @author Инна
 */
public class GraphArcOut extends GraphArc implements Serializable {

    private static ArrayList<GraphArcOut> graphTieOutList = new ArrayList<>();  // added by Olha 24.09.12, cjrrect by Inna 28.11.2012
    private ArcOut tie;
    
    
    
   public GraphArcOut() { // додано Олею 28.09.12 для створення тимчасової дуги (тільки для промальовки) 
       super();
       tie = new ArcOut();
       //System.out.println("GraphTieOut  "+ tie.getNameT()+"  "+tie.getNumT()+"  "+tie.getNameP()+"  "+tie.getNumP());
       super.setLineWidth(1);
       super.setColor(Color.BLACK);
    }
    
     public GraphArcOut(ArcOut tieout){
        tie = tieout;
        super.setLineWidth(1);
        super.setColor(Color.BLACK);
   
    }
     public ArcOut getArcOut()
    {
        return tie;
    }
    @Override
    public void setPetriElements() {
        tie.setQuantity(tie.getQuantity());
        tie.setNumT(super.getBeginElement().getNumber());
        tie.setNameT(super.getBeginElement().getName());
        tie.setNumP(super.getEndElement().getNumber());
        tie.setNameP(super.getEndElement().getName());
    /*  System.out.println("GraphTIE OUT : setPetriElements "+super.getBeginElement().getName()+  "  "+ super.getBeginElement().getNumber()+
                    super.getEndElement().getName()+"  "+super.getEndElement().getNumber()     
                );*/
        addElementToArrayList(); //// added by Olha 24.09.12
    }
    

    @Override
    public void addElementToArrayList() {   // added by Olha 24.09.12
        if (graphTieOutList == null) {
            graphTieOutList = new ArrayList<>();
        }
        graphTieOutList.add(this);
    }

    @Override
    public void drawGraphElement(Graphics2D g) {
        Graphics2D g2 = (Graphics2D) g;
        g2.setStroke(new BasicStroke(getLineWidth()));
        g2.setColor(getColor());
        g2.draw(this.getGraphElement());
        drawArrowHead(g2);
        if (tie.getQuantity() != 1 || tie.kIsParam()) {
            String quantityString = tie.kIsParam() // added by Katya 08.12.2016
                ? tie.getKParamName()
                : Integer.toString(tie.getQuantity());
            this.getAvgLine().setLocation((this.getGraphElement().getX1() + this.getGraphElement().getX2()) / 2, (this.getGraphElement().getY1() + this.getGraphElement().getY2()) / 2);
            g2.drawLine((int) this.getAvgLine().getX() + 5, (int) this.getAvgLine().getY() - 5, (int) this.getAvgLine().getX() - 5, (int) this.getAvgLine().getY() + 5);
            g2.drawString(quantityString, (float) this.getAvgLine().getX(), (float) this.getAvgLine().getY() - 7);
        }
    }

    public static ArrayList<GraphArcOut> getGraphTieOutList() {
        return graphTieOutList;
    }
    
    public static ArrayList<ArcOut> getArcOutList() {  // added by Inna 1.11.2012
        
        ArrayList<ArcOut> arrayTieOut = new ArrayList<>();
        for (GraphArcOut e: graphTieOutList)
            arrayTieOut.add(e.getArcOut());
        return arrayTieOut;
    }
//    public static void setTieOutList(ArrayList<TieOut> TieOutList) {
//        TieOut.tieOutList = TieOutList;
//    }
    public static void setNullTieOutList() {
        graphTieOutList.clear();
    }
    public static void addGraphTieOutList(List<GraphArcOut> tieOut){ // added by Olha 14/11/2012
      for (GraphArcOut to:tieOut) {
          graphTieOutList.add(to);
      } 
    }
     
    
    
     @Override
    public int getQuantity(){  //потрібно для правильної роботи методу getQuantity() батьківського класу
            return tie.getQuantity();
        }
    @Override
   public void setQuantity(int i){
            tie.setQuantity(i);
        }

    
    
}

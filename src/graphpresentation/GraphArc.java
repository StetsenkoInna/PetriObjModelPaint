/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package graphpresentation;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.geom.AffineTransform;
import java.awt.geom.Line2D;
import java.awt.geom.Point2D;
import java.io.Serializable;

/**
 *
 * @author Ольга
 */
public class GraphArc implements Serializable{
    
 
    private Line2D graphElement;
    private GraphElement beginElement;
    private GraphElement endElement;
 
    private Point2D avgLine = new Point2D.Double(0, 0);  //static
    private boolean firstArc;
    private boolean secondArc;
    
    private int lineWidth; //replaced from subclasses
    
    private Color color; //replaced from subclasses

    public GraphArc()
    {
        color = Color.BLACK;
        lineWidth = 1;
    }
    
    public void drawGraphElement(Graphics2D g) {

    }

    // create new Arc   set Begin Element
    public void settingNewArc(GraphElement e){
      if(e!=null){ //add by Inna 26.01.2013
         setBeginElement(e);
         graphElement = new Line2D.Double(0, 0, 0, 0);
      } else
         ;
    }

    //setting end element
    public boolean finishSettingNewArc(GraphElement e){
        if(beginElement!=null) { //added by Inna 26.01.2013
            if (beginElement.getClass().equals(e.getClass())) {
                return false;
             } else {
                 setEndElement(e);
                 return true;

               }
        }
        else {
            return false;
        }
    }
    

    public void setBeginElement(GraphElement e){
        if(e!=null) {   //add by Inna 26.01.2013
            beginElement=e;
        }
        else
            ;
    }
    
    public void setEndElement(GraphElement e){
      if(e!=null) {  //add by Inna 26.01.2013
           {
              endElement=e;
          }
      }
      else ;
       
    }
    

    public boolean isGraphElement(Point2D p){
        return graphElement.contains(p);
    }

    public void movingBeginElement(Point2D p){
        if(endElement!=null) {//added by Inna 26.01.2013
            graphElement.setLine(p, endElement.getGraphElementCenter());
        }
    }
    
    public void movingEndElement(Point2D p){
        if(beginElement!=null) {//added by Inna 26.01.2013
            graphElement.setLine(beginElement.getGraphElementCenter(), p);
        }
    }
    
    public void setNewCoordinates(Point2D p){
     
        graphElement.setLine(beginElement.getGraphElementCenter(), p);
    }

    public void drawArrowHead(Graphics2D g) {
        AffineTransform tx = new AffineTransform();
        Polygon arrowHead = new Polygon();
        arrowHead.addPoint(0, -2);
        arrowHead.addPoint(-5, -12);
        arrowHead.addPoint(5, -12);
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
        double x, y, k, b, r, yy, rr, xx, dd, ii1, ii2, jj1, jj2;
        if (firstArc) {
            y = graphElement.getY2() - 10;
            yy = graphElement.getY1() - 10;
        } else if (secondArc) {
            y = graphElement.getY2() + 10;
            yy = graphElement.getY1() + 10;
        } else {
            y = graphElement.getY2();
            yy = graphElement.getY1();
        }
      
        x = graphElement.getX2();
        xx = graphElement.getX1();
        k = (yy - y) / (xx - x);
        b = yy - k * xx;
      
        r = endElement.getBorder();  
        rr = beginElement.getBorder();

        double d = (Math.pow((2 * k * b - 2 * x - 2 * y * k), 2) - (4 + 4 * k * k) * (b * b - r * r + x * x + y * y - 2 * y * b));
        double i1 = ((-(2 * k * b - 2 * x - 2 * y * k) - Math.sqrt(d)) / (2 + 2 * k * k));
        double i2 = ((-(2 * k * b - 2 * x - 2 * y * k) + Math.sqrt(d)) / (2 + 2 * k * k));
        double j1 = k * i1 + b;
        double j2 = k * i2 + b;

        dd = (Math.pow((2 * k * b - 2 * xx - 2 * yy * k), 2) - (4 + 4 * k * k) * (b * b - rr * rr + xx * xx + yy * yy - 2 * yy * b));
        ii1 = ((-(2 * k * b - 2 * xx - 2 * yy * k) - Math.sqrt(dd)) / (2 + 2 * k * k));
        ii2 = ((-(2 * k * b - 2 * xx - 2 * yy * k) + Math.sqrt(dd)) / (2 + 2 * k * k));
        jj1 = k * ii1 + b;
        jj2 = k * ii2 + b;


        if (x < xx){      
            graphElement.setLine(new Point2D.Double(ii1, jj1), new Point2D.Double(i2, j2));
        } else if (x > xx) {                       
           graphElement.setLine(new Point2D.Double(ii2, jj2), new Point2D.Double(i1, j1));
        } else if (yy > y) {                     
            graphElement.setLine(beginElement.getGraphElementCenter(), new Point2D.Double(endElement.getGraphElementCenter().getX(), endElement.getGraphElementCenter().getY() + 10));
        } else {
            graphElement.setLine(beginElement.getGraphElementCenter(), new Point2D.Double(endElement.getGraphElementCenter().getX(), endElement.getGraphElementCenter().getY() - 10));
        }
    }

    public void twoArcs(GraphArc t) {
        t.secondArc = true;
        this.firstArc = true;
    }

   
     public boolean isEnoughDistance(Point2D p) {
        return graphElement.ptSegDist(p) < 3;
    }

public void updateCoordinates() { // метод для оновлення інформації щодо координат елементу (для перемалювання) 09.01.13
        graphElement.setLine(beginElement.getGraphElementCenter(), endElement.getGraphElementCenter());
        changeBorder();
    }
     
    public Line2D getGraphElement() {
        return graphElement;
    }

    public void setGraphElement(Line2D graphElement) {
        this.graphElement = graphElement;
    }

    public Point2D getAvgLine() {
        return avgLine;
    }

    public void setAvgLine(Point2D avgLine) {
        this.avgLine = avgLine;
    }

    public GraphElement getBeginElement() {
        return beginElement;
    }

    public GraphElement getEndElement() {
        return endElement;
    }
     
   public  void addElementToArrayList(){  // added by Olha 24.09.12
           
       }
   
   
   public void setPetriElements(){
            
        }
    public int getId(){
         return 0;
     }
    
    public int getQuantity(){
            return 1; //якщо зв"язок існує, то за замовчуванням кількість = 1
        }
   public void setQuantity(int i){
            
        }
   public boolean getIsInf(){
            return false;
        }
   public void setInf(boolean i){
            
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
   
}

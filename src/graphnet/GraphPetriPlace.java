/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package graphnet;

import PetriObj.PetriP;
import graphpresentation.GraphPlace;

import java.awt.Color;
import java.awt.Graphics2D;
import java.io.Serializable;

import javax.swing.JTextField;

/**
 *
 * @author Инна
 */
public class GraphPetriPlace extends GraphPlace implements Serializable {     //додано Олею 17.25.2012
 
    private PetriP place;
    private static int simpleInd=0; // added by Inna 18.01.2013
  
    
    
    private int id; //додано Олею
  
    public GraphPetriPlace(PetriP P, int i){ //додано Олею
        place = P;
        id=i;
    }

    public PetriP getPetriPlace(){
         return place;
    }
   
    public GraphPetriPlace(PetriP P) {
       place = P;
       id=place.getNumber();
   }

    @Override                 //додано Олею 17.09.2012
    public void drawGraphElement(Graphics2D g2) {
        super.drawGraphElement(g2);
        int font = 10;
        g2.drawString(place.getName(), (float) this.getGraphElement().getX() + GraphPetriPlace.getDiameter() / 2 - (place.getName().length() * font) / 2, (float) this.getGraphElement().getY()- GraphPetriPlace.getDiameter() / 2 + GraphPetriPlace.getDiameter()/3 );
        String markString = place.markIsParam() // added by Katya 08.12.2016
            ? place.getMarkParamName()
            : Integer.toString(place.getMark());
        g2.drawString(markString, (float) this.getGraphElement().getX() + GraphPetriPlace.getDiameter() / 2 - Integer.toString(place.getMark()).length() * font / 2, (float) this.getGraphElement().getY() + GraphPetriPlace.getDiameter() / 2 + font / 2);
    }
    
    @Override
    public String getType() { // added by Katya 23.10.2016
        return "GraphPetriPlace";
    }

    @Override
    public int getId(){
        return id;
    } 
    @Override
     public String getName(){
           return this.getPetriPlace().getName();
       }
    @Override
       public int getNumber(){
           return this.getPetriPlace().getNumber();
       }
    
    public static String setSimpleName(){ // added by Inna 18.01.2013
          simpleInd++; 
          return "P"+simpleInd;
       }
    
     public static void setNullSimpleName(){ // added by Inna 18.01.2013
          simpleInd = 0; 
          
       }
     
}

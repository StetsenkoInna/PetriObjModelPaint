package ua.stetsenkoinna.graphnet;

import ua.stetsenkoinna.PetriObj.PetriP;
import ua.stetsenkoinna.graphpresentation.GraphPlace;

import java.awt.Graphics2D;
import java.io.Serializable;

/**
 *
 * @author Инна
 */
public class GraphPetriPlace extends GraphPlace implements Serializable {

    private static int simpleInd=0;

    private final PetriP place;
    private final int id;
  
    public GraphPetriPlace(PetriP P, int i){
        place = P;
        id=i;
    }

    public PetriP getPetriPlace(){
         return place;
    }
   
    @Override
    public void drawGraphElement(Graphics2D g2) {
        super.drawGraphElement(g2);
        int font = 10;
        g2.drawString(place.getName(),
                    (float) this.getGraphElement().getX() + (float) GraphPetriPlace.getDiameter() / 2 - (float) (place.getName().length() * font) / 2,
                    (float) this.getGraphElement().getY()- (float) GraphPetriPlace.getDiameter() / 2 + (float) GraphPetriPlace.getDiameter() /3
        );
        String markString = place.markIsParam()
            ? place.getMarkParamName()
            : Integer.toString(place.getMark());
        g2.drawString(markString,
                (float) this.getGraphElement().getX() + (float) GraphPetriPlace.getDiameter() / 2 - (float) (Integer.toString(place.getMark()).length() * font) / 2,
                (float) this.getGraphElement().getY() + (float) GraphPetriPlace.getDiameter() / 2 + (float) font / 2
        );
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
    
    public static String setSimpleName(){
          simpleInd++; 
          return "P"+simpleInd;
       }

    public static void setNullSimpleName(){
        simpleInd = 0;
    }
}

/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package graphnet;

import PetriObj.ExceptionInvalidNetStructure;
import PetriObj.PetriNet;
import PetriObj.PetriP;
import PetriObj.PetriT;
import PetriObj.ArcIn;
import PetriObj.ArcOut;
import PetriObj.ExceptionInvalidTimeDelay;
import graphpresentation.GraphElement;
import graphpresentation.PetriNetsPanel;

import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.swing.JTextArea;
//import java.util.List;  замінено на ArrayList 19.11.2012

/**
 * This class owns the information on the graphic elements and provides creation
 * of a Petri net in accordance
 *
 * @author Инна
 */
public class GraphPetriNet implements Cloneable, Serializable {

    private ArrayList<GraphPetriPlace> graphPetriPlaceList;
    private ArrayList<GraphPetriTransition> graphPetriTransitionList;
    private ArrayList<GraphArcIn> graphArcInList;
    private ArrayList<GraphArcOut> graphArcOutList;
    
    private static final int bigNumber = 10000; // для правильного коригування нумерації позицій та переходів; added by Katya 08.12.2016
    
    private PetriNet pNet;

    public GraphPetriNet() {
        // pNet = null;
        graphPetriPlaceList = new ArrayList<>();
        graphPetriTransitionList = new ArrayList<>();
        graphArcInList = new ArrayList<>();
        graphArcOutList = new ArrayList<>();
    }

    //цей конструктор використовується під час копіювання added by Inna 19.11.2012
    public GraphPetriNet(PetriNet net, ArrayList<GraphPetriPlace> grPlaces,
            ArrayList<GraphPetriTransition> grTransitions,
            ArrayList<GraphArcIn> grArcIns,
            ArrayList<GraphArcOut> grArcOuts) {
        graphPetriPlaceList = grPlaces;
        graphPetriTransitionList = grTransitions;
        graphArcInList = grArcIns;
        graphArcOutList = grArcOuts;

        pNet = net; // Немає гарантії, що списки елементів мережі Петрі відповідають спискам графічних елементів
        //використовувати ТІЛЬКИ при копіюванні
    }

    /**
     * Coping constructor
     * @param graphPetriNet: GraphPetriNet to copy
     */
    public GraphPetriNet(GraphPetriNet graphPetriNet) {
        graphPetriPlaceList = new ArrayList<>();
        graphPetriTransitionList = new ArrayList<>();
        graphArcInList = new ArrayList<>();
        graphArcOutList = new ArrayList<>();

        List<GraphElement> elementsToCopy = new ArrayList<>();
        elementsToCopy.addAll(graphPetriNet.graphPetriPlaceList);
        elementsToCopy.addAll(graphPetriNet.graphPetriTransitionList);
        bulkCopyElements(elementsToCopy, graphPetriNet.graphArcInList, graphPetriNet.graphArcOutList);
    }

    @Override
    public GraphPetriNet clone() throws CloneNotSupportedException { // added by Inna 19.11.2012, corrected by Inna 6.12.2015 

        super.clone();
        ArrayList<GraphPetriPlace> copyGraphPlaceList = new ArrayList<>();
        ArrayList<GraphPetriTransition> copyGraphTransitionList = new ArrayList<>();
        ArrayList<GraphArcIn> copyGraphArcIn = new ArrayList<>();
        ArrayList<GraphArcOut> copyGraphArcOut = new ArrayList<>();
        copyGraphPlaceList.addAll(graphPetriPlaceList);
        copyGraphTransitionList.addAll(graphPetriTransitionList);
        copyGraphArcIn.addAll(graphArcInList);
        copyGraphArcOut.addAll(graphArcOutList);

        PetriNet copyNet = pNet.clone();

        GraphPetriNet net = new GraphPetriNet(copyNet,
                copyGraphPlaceList,
                copyGraphTransitionList,
                copyGraphArcIn,
                copyGraphArcOut);

        return net;
    }
    
    public boolean hasParameters() { // added by Katya 08.12.2016
        return pNet.hasParameters();
    }

    public void print() { //цей фрагмент виділений в окремий метод
        // added by Olha 14.11.2012, corrected by Inna 28.11.2012 
        System.out.println("Information about GraphPetriNet");
        for (PetriP pp : this.getPetriPList()) {
            pp.printParameters();
        }
        for (PetriT pt : this.getPetriTList()) {
            pt.printParameters();
           //  System.out.println("inP size ="+pt.getInP().size());

        }
        for (ArcIn ti : this.getArcInList()) {
            ti.printParameters();

        }
        for (ArcOut to : this.getArcOutList()) {
            to.printParameters();
        }
    }
    
    public PetriNet getPetriNet() {
        return pNet;
    }

    public void setPetriNet(PetriNet net) {
        pNet = net;
    }

    public ArrayList<GraphPetriPlace> getGraphPetriPlaceList() {
        return graphPetriPlaceList;
    }

    public ArrayList<GraphPetriTransition> getGraphPetriTransitionList() {
        return graphPetriTransitionList;
    }

    public ArrayList<GraphArcIn> getGraphArcInList() {
        return graphArcInList;
    }

    public ArrayList<GraphArcOut> getGraphArcOutList() {
        return graphArcOutList;
    }

    public void setGraphPetriPlaceList(ArrayList<GraphPetriPlace> graphPetriPlaceList) {
        this.graphPetriPlaceList = graphPetriPlaceList;
    }

    public void setGraphPetriTransitionList(ArrayList<GraphPetriTransition> graphPetriTransitionList) {
        this.graphPetriTransitionList = graphPetriTransitionList;
    }

    public void setGraphArcInList(ArrayList<GraphArcIn> graphArcInList) {
        this.graphArcInList = graphArcInList;
    }

    public void setGraphArcOutList(ArrayList<GraphArcOut> graphArcOutList) {
        this.graphArcOutList = graphArcOutList;
    }

    public ArrayList<PetriP> getPetriPList() {  // added by Inna 3.12.2012

        ArrayList<PetriP> array = new ArrayList<>();
        for (GraphPetriPlace e : graphPetriPlaceList) {
            array.add(e.getPetriPlace());
        }
        return array;
    }

    public ArrayList<PetriT> getPetriTList() {  // added by Inna 3.12.2012

        ArrayList<PetriT> array = new ArrayList<>();
        for (GraphPetriTransition e : graphPetriTransitionList) {
            array.add(e.getPetriTransition());
        }
        return array;
    }

    public ArrayList<ArcIn> getArcInList() {  // added by Inna 3.12.2012

        ArrayList<ArcIn> array = new ArrayList<>();
        for (GraphArcIn e : graphArcInList) {
            array.add(e.getArcIn());
        }
        return array;
    }

    public ArrayList<ArcOut> getArcOutList() {  // added by Inna 3.12.2012

        ArrayList<ArcOut> array = new ArrayList<>();
        for (GraphArcOut e : graphArcOutList) {
            array.add(e.getArcOut());
        }
        return array;
    }

    public boolean isCorrectInArcs() {
        boolean b = false;
        for (GraphPetriTransition grT : graphPetriTransitionList) {
            for (GraphArcIn in : graphArcInList) {
                if (in.getArcIn().getNumT() == grT.getNumber()) {
                    b = true;
                    break;
                }
            }
            if (b == false) {
                break;
            }
        }
        return b;
    }

    public boolean isCorrectOutArcs() {
        boolean b = false;
        for (GraphPetriTransition grT : graphPetriTransitionList) {
            for (GraphArcOut in : graphArcOutList) {
                if (in.getArcOut().getNumT() == grT.getNumber()) {
                    b = true;
                    break;
                }
            }
            if (b == false) {
                break;
            }
        }
        return b;
    }

    public void createPetriNet(String s) throws ExceptionInvalidNetStructure, ExceptionInvalidTimeDelay {//added by Inna 4.12.2012 //створюється мережа Петрі у відповідності до графічних елементів 
    // added by Inna 4.12.2012
        correctingNumP();
        correctingNumT();
        pNet = new PetriNet(s, this.getPetriPList(), this.getPetriTList(), this.getArcInList(), this.getArcOutList());
    }

    public boolean isCorrectNumberP() { //added by Inna 5.12.2012
        ArrayList<PetriP> list = this.getPetriPList();
        boolean b = true;
        for (int j = 0; j < list.size(); j++) {
            if (list.get(j).getNumber() != j) {
                b = false;
                break;
            }
        }
        return b;
    }

    public boolean isCorrectNumberT() { //added by Inna 5.12.2012
        ArrayList<PetriT> list = this.getPetriTList();
        boolean b = true;
        for (int j = 0; j < list.size(); j++) {
            if (list.get(j).getNumber() != j) {
                b = false;
                break;
            }
        }
        return b;
    }

    public void correctingNumP() { //added by Inna 5.12.2012
        if (isCorrectNumberP() == true) {
            return;
        } else {
            for (int j = 0; j < this.getPetriPList().size(); j++) {
                if (this.getPetriPList().get(j).getNumber() != j) {
                    int actualNumber = getPetriPList().get(j).getNumber();
                    
                    for (ArcIn in : this.getArcInList()) {
                        if (in.getNumP() == actualNumber) {
                            in.setNumP(j + bigNumber);
                        }
                    }
                    for (ArcOut out : this.getArcOutList()) {
                        if (out.getNumP() == actualNumber) {
                            out.setNumP(j + bigNumber);
                        }
                    }
                    this.getPetriPList().get(j).setNumber(j + bigNumber); //встановлення номера позиції по порядку слідування в списку
                }
            }
            
            for (int j = 0; j < this.getPetriPList().size(); j++) { // added by Katya 08.12.2016
                if (this.getPetriPList().get(j).getNumber() >= bigNumber) {
                    this.getPetriPList().get(j).setNumber(this.getPetriPList().get(j).getNumber() - bigNumber);
                }
            }
            for (ArcIn in : this.getArcInList()) {
                if (in.getNumP() >= bigNumber) {
                    in.setNumP(in.getNumP() - bigNumber);
                }
            }
            for (ArcOut out : this.getArcOutList()) {
                if (out.getNumP() >= bigNumber) {
                    out.setNumP(out.getNumP() - bigNumber);
                }
            }
        }
    }

    public void correctingNumT() { //added by Inna 5.12.2012
        if (isCorrectNumberT() == true) {
            return;
        } else {

            for (int j = 0; j < this.getPetriTList().size(); j++) {
                if (this.getPetriTList().get(j).getNumber() != j) {
                    int actualNumber = getPetriTList().get(j).getNumber();
                    
                    for (ArcIn in : this.getArcInList()) {
                        if (in.getNumT() == actualNumber) {
                            in.setNumT(j + bigNumber);
                        }
                    }
                    for (ArcOut out : this.getArcOutList()) {
                        if (out.getNumT() == actualNumber) {
                            out.setNumT(j + bigNumber);
                        }
                    }
                    this.getPetriTList().get(j).setNumber(j + bigNumber); //встановлення номера переходу по порядку слідування в списку
                }
            }
            
            for (int j = 0; j < this.getPetriTList().size(); j++) { // added by Katya 08.12.2016
                if (this.getPetriTList().get(j).getNumber() >= bigNumber) {
                    this.getPetriTList().get(j).setNumber(this.getPetriTList().get(j).getNumber() - bigNumber);
                }
            }
            for (ArcIn in : this.getArcInList()) {
                if (in.getNumT() >= bigNumber) {
                    in.setNumT(in.getNumT() - bigNumber);
                }
            }
            for (ArcOut out : this.getArcOutList()) {
                if (out.getNumT() >= bigNumber) {
                    out.setNumT(out.getNumT() - bigNumber);
                }
            }
        }
    }

    public void delGraphElement(GraphElement s) throws ExceptionInvalidNetStructure { //added by Inna 4.12.2012
        String name;
        if (pNet != null) {
            name = pNet.getName();
        } else {
            name = "Untitled";
        }
        pNet = null; //тому що порушується структура мережі Петрі!!!
        for (GraphPetriPlace pp : graphPetriPlaceList) {
              if (pp.getId() == s.getId()) {
                boolean b = true;
                while (b) {
                    b = false;
                    for (GraphArcIn arc : graphArcInList) {
                        if (arc.getBeginElement().getId() == s.getId()) {
                            graphArcInList.remove(arc);
                            b = true;
                            break;
                        }
                    }
                }
                b = true;
                while (b) {
                    b = false;
                    for (GraphArcOut arc : graphArcOutList) {
                        if (arc.getEndElement().getId() == s.getId()) {
                            graphArcOutList.remove(arc);
                            b = true;
                            break;
                        }
                    }
                }
                graphPetriPlaceList.remove(pp);
                break;
            }
        }
        for (GraphPetriTransition tt : graphPetriTransitionList) {
            if (tt.getId() == s.getId()) {
                boolean b = true;
                while (b) {
                    b = false;
                    for (GraphArcIn arc : graphArcInList) {
                        if (arc.getEndElement().getId() == s.getId()) {
                            graphArcInList.remove(arc);
                            b = true;
                            break;
                        }
                    }
                }
                b = true;
                while (b) {
                    b = false;
                    for (GraphArcOut arc : graphArcOutList) {
                        if (arc.getBeginElement().getId() == s.getId()) {
                            graphArcOutList.remove(arc);
                            b = true;
                            break;
                        }
                    }
                }
                graphPetriTransitionList.remove(tt);
                break;

            }
        }
     }
    
    public GraphElement copyElement(GraphElement element) {
        if (element instanceof GraphPetriPlace) {
            GraphPetriPlace newPlace = new GraphPetriPlace(
                    new PetriP(((GraphPetriPlace) element).getPetriPlace()),
                    PetriNetsPanel.getIdElement()
            );

            newPlace.setNewCoordinates(element.getGraphElementCenter());
            graphPetriPlaceList.add(newPlace);
            return newPlace;
        }

        if (element instanceof GraphPetriTransition) {
            GraphPetriTransition newTransition = new GraphPetriTransition(
                    new PetriT(((GraphPetriTransition) element).getPetriTransition()),
                    PetriNetsPanel.getIdElement()
            );

            newTransition.setNewCoordinates(element.getGraphElementCenter());
            graphPetriTransitionList.add(newTransition);
            return newTransition;
        }

        return null;
    }

    public List<GraphElement> bulkCopyElements(List<GraphElement> elements) {
        return bulkCopyElements(elements, graphArcInList, graphArcOutList);
    }

    private List<GraphElement> bulkCopyElements(List<GraphElement> elements, List<GraphArcIn> arcInSource, List<GraphArcOut> arcOutSource) {
        Map<GraphPetriTransition, GraphPetriTransition> transitionsCopies = new HashMap<>();
        Map<GraphPetriPlace, GraphPetriPlace> positionCopies = new HashMap<>();

        for (GraphElement element : elements) {
            GraphElement copiedElement = copyElement(element);

            if (copiedElement instanceof GraphPetriPlace) {
                positionCopies.put((GraphPetriPlace) element, (GraphPetriPlace) copiedElement);
            } else if (copiedElement instanceof GraphPetriTransition) {
                transitionsCopies.put((GraphPetriTransition) element, (GraphPetriTransition) copiedElement);
            }
        }

        List<GraphArcIn> arcInsToAdd = new ArrayList<>();
        List<GraphArcOut> arcOutsToAdd = new ArrayList<>();

        for (GraphPetriTransition transition : transitionsCopies.keySet()) {
            for (GraphArcIn arc : arcInSource) {
                if (arc.getEndElement().getId() == transition.getId()) {
                    GraphPetriPlace position = positionCopies.get(arc.getBeginElement());

                    if (position != null) {
                        GraphArcIn arcIn = new GraphArcIn(new ArcIn(arc.getArcIn()));
                        arcIn.setEndElement(transitionsCopies.get(transition));
                        arcIn.settingNewArc(position);
                        arcIn.setPetriElements();
                        arcIn.changeBorder();
                        arcIn.updateCoordinates();
                        arcInsToAdd.add(arcIn);
                    }
                }
            }

            for (GraphArcOut arc : arcOutSource) {
                if (arc.getBeginElement().getId() == transition.getId()) {
                    GraphPetriPlace position = positionCopies.get(arc.getEndElement());

                    if (position != null) {
                        GraphArcOut arcOut = new GraphArcOut(new ArcOut(arc.getArcOut()));
                        arcOut.settingNewArc(transitionsCopies.get(transition));
                        arcOut.setEndElement(position);
                        arcOut.setPetriElements();
                        arcOut.changeBorder();
                        arcOut.updateCoordinates();
                        arcOutsToAdd.add(arcOut);
                    }
                }
            }
        }
         // added by Inna 13.01.2020
        for (GraphArcOut arcOut : arcOutSource) {
            for (GraphArcIn arcIn : arcInSource) {
                int inBeginId = ((GraphPetriPlace)arcIn.getBeginElement()).getId();
                int inEndId = ((GraphPetriTransition)arcIn.getEndElement()).getId();
                int outBeginId = ((GraphPetriTransition)arcOut.getBeginElement()).getId();
                int outEndId = ((GraphPetriPlace)arcOut.getEndElement()).getId();
                if (inBeginId == outEndId && inEndId == outBeginId) {
                    arcIn.twoArcs(arcOut); // two arcs
                    arcIn.updateCoordinates();
                    arcOut.updateCoordinates();

                }
            }
        }
        //

        graphArcInList.addAll(arcInsToAdd);
        graphArcOutList.addAll(arcOutsToAdd);

        List<GraphElement> copiedElements = new ArrayList<>(transitionsCopies.values());
        copiedElements.addAll(positionCopies.values());

        return copiedElements;
    }

    public void printStatistics(JTextArea area) {

        area.append("\n Statistics of Petri net places:\n");
        for (GraphPetriPlace grP : graphPetriPlaceList) {
            PetriP P = grP.getPetriPlace();
            area.append("Place " + P.getName() + ": mean value = " + Double.toString(P.getMean()) + "\n"
                    + "         max value = " + Double.toString(P.getObservedMax()) + "\n"
                    + "         min value = " + Double.toString(P.getObservedMin()) + "\n");

        }
        area.append("\n Statistics of Petri net transitions:\n");
        for (GraphPetriTransition grT : graphPetriTransitionList) {
            PetriT T = grT.getPetriTransition();
            area.append("Transition " + T.getName() + " has mean value " + Double.toString(T.getMean()) + "\n"
                    + "         max value = " + Double.toString(T.getObservedMax()) + "\n"
                    + "         min value = " + Double.toString(T.getObservedMin()) + "\n");
        }

    }

    //added 05.12.17
    public Point getCurrentLocation(){
    	Double x = 0.0;
        Double y = 0.0;
        Double placeX = 0.0;
        Double placeY = 0.0;
        Double transitionX = 0.0;
        Double transitionY = 0.0;
        for (GraphPetriPlace place : graphPetriPlaceList) {
            placeX = placeX + place.getGraphElementCenter().getX();
            placeY = placeY + place.getGraphElementCenter().getY();
        }
        for (GraphPetriTransition transition : graphPetriTransitionList) {
            transitionX = transitionX + transition.getGraphElementCenter().getX();
            transitionY = transitionY + transition.getGraphElementCenter().getY();
        }
    	x = (placeX + transitionX) / (graphPetriPlaceList.size() + graphPetriTransitionList.size());
        y = (placeY + transitionY) / (graphPetriPlaceList.size() + graphPetriTransitionList.size());
    	return new Point(x.intValue(), y.intValue());
    }
    
    // added by Olha 09.01.13
    public void changeLocation(Point newCenter) {   // змінити центр розсташування мережі відповідно до заданої точки 09.01.13
        
        Point currentCenter = getCurrentLocation();
        Double diferenceX = 0.0;
        Double diferenceY = 0.0;
        diferenceX = currentCenter.getX() - newCenter.getX();
        diferenceY = currentCenter.getY() - newCenter.getY();
        for (GraphPetriPlace place : graphPetriPlaceList) {
            Double newX = place.getGraphElementCenter().getX() - diferenceX;
            Double newY = place.getGraphElementCenter().getY() - diferenceY;
            place.setNewCoordinates(new Point(newX.intValue(), newY.intValue()));
        }
        for (GraphPetriTransition transition : graphPetriTransitionList) {
            Double newX = transition.getGraphElementCenter().getX() - diferenceX;
            Double newY = transition.getGraphElementCenter().getY() - diferenceY;
            transition.setNewCoordinates(new Point(newX.intValue(), newY.intValue()));
        }
        for (GraphArcIn arcIn : graphArcInList) {
            arcIn.updateCoordinates();
        }
        for (GraphArcOut arcOut : graphArcOutList) {
            arcOut.updateCoordinates();
        }
    }
    
    public void setDefaultColorGraphElements(){
        if (!graphPetriPlaceList.isEmpty()) {
            for (GraphPetriPlace e : graphPetriPlaceList) {
                e.setColor(Color.BLACK); // edited 26.07.2018
                
            }
        }
        if (!graphPetriTransitionList.isEmpty()) {
            for (GraphPetriTransition e : graphPetriTransitionList) {
               e.setColor(Color.BLACK); // edited 26.07.2018
                
            }
        }
    }
 
    // 11.01.13 промальовка мережі винесена в окремий метод by Inna
    public void paintGraphPetriNet(Graphics2D g2, Graphics g) { // edited 26.07.2018 by Inna
        if (!graphArcOutList.isEmpty()) {
            for (GraphArcOut a : graphArcOutList) {
              a.drawGraphElement(g2);
            }
        }
        if (!graphArcInList.isEmpty()) {
            for (GraphArcIn a : graphArcInList) {
               a.drawGraphElement(g2);
            }
        }
        if (!graphPetriPlaceList.isEmpty()) {
            for (GraphPetriPlace e : graphPetriPlaceList) {
               e.drawGraphElement(g2);
            }
        }
        if (!graphPetriTransitionList.isEmpty()) {
            for (GraphPetriTransition e : graphPetriTransitionList) {
                e.drawGraphElement(g2);
            }
        }
    }

}

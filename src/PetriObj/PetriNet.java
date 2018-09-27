package PetriObj;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;

/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
/**
 * This class provides constructing Petri net
 *
 * @author Inna V. Stetsenko
 */
public class PetriNet implements Cloneable, Serializable {

    private String name;
    private int numP;
    private int numT;
    private int numIn;
    private int numOut;
    private PetriP[] ListP = new PetriP[numP];
    private PetriT[] ListT = new PetriT[numT];
    private ArcIn[] ListIn = new ArcIn[numIn];
    private ArcOut[] ListOut = new ArcOut[numOut];

    /**
     * Construct Petri net for given set of places, set of transitions, set of
     * arcs and the name of Petri net
     *
     * @param s name of Petri net
     * @param pp set of places
     * @param TT set of transitions
     * @param In set of arcs directed from place to transition
     * @param Out set of arcs directed from transition to place
     */
    public PetriNet(String s, PetriP[] pp, PetriT TT[], ArcIn[] In, ArcOut[] Out) {
        name = s;
        numP = pp.length;
        numT = TT.length;
        numIn = In.length;
        numOut = Out.length;
        ListP = pp;
        ListT = TT;
        ListIn = In;
        ListOut = Out;

        for (PetriT transition : ListT) {
            try {
                transition.createInP(ListP, ListIn);
                transition.createOutP(ListP, ListOut);
                if (transition.getInP().isEmpty()) {
                    throw new ExceptionInvalidTimeDelay("Error: Transition " + transition.getName() + " has empty list of input places "); //генерувати виключення???
                }
                if (transition.getOutP().isEmpty()) {
                    throw new ExceptionInvalidTimeDelay("Error: Transition " + transition.getName() + " has empty list of input places"); //генерувати виключення???
                }
            } catch (ExceptionInvalidTimeDelay ex) {
                Logger.getLogger(PetriNet.class.getName()).log(Level.SEVERE, null, ex);
            }
        }

    }

    /**
     *
     * @param s name of Petri net
     * @param pp set of places
     * @param TT set of transitions
     * @param In set of arcs directed from place to transition
     * @param Out set of arcs directed from transition to place
     * @throws PetriObj.ExceptionInvalidTimeDelay if Petri net has invalid structure
     */
    public PetriNet(String s, ArrayList<PetriP> pp, ArrayList<PetriT> TT, ArrayList<ArcIn> In, ArrayList<ArcOut> Out) throws ExceptionInvalidTimeDelay //додано 16 серпня 2011
    {//Працює прекрасно, якщо номера у списку співпадають із номерами, що присвоюються, і з номерами, які використовувались при створенні зв"язків!!!
        name = s;
        numP = pp.size();
        numT = TT.size();
        numIn = In.size();
        numOut = Out.size();
        ListP = new PetriP[numP];
        ListT = new PetriT[numT];
        ListIn = new ArcIn[numIn];
        ListOut = new ArcOut[numOut];

        for (int j = 0; j < numP; j++) {
            ListP[j] = pp.get(j);
        }

        for (int j = 0; j < numT; j++) {
            ListT[j] = TT.get(j);
        }

        for (int j = 0; j < numIn; j++) {
            ListIn[j] = In.get(j);
        }
        for (int j = 0; j < numOut; j++) {
            ListOut[j] = Out.get(j);
        }

        for (PetriT transition : ListT) {
            transition.createInP(ListP, ListIn);
            transition.createOutP(ListP, ListOut);
        }

    }

    /**
     *
     * @return name of Petri net
     */
    public String getName() {
        return name;
    }

    /**
     * Sets name of Petri net
     *
     * @param s the name of Petri net
     */
    public void setName(String s) {
        name = s;
    }

    /**
     *
     * @return array of Petri net places
     */
    public PetriP[] getListP() {
        return ListP;
    }

    /**
     *
     * @return array of Petri net transitions
     */
    public PetriT[] getListT() {
        return ListT;
    }

    /**
     *
     * @return array of Petri net input arcs
     */
    public ArcIn[] getArcIn() {
        return ListIn;
    }

    /**
     *
     * @return array of Petri net output arcs
     */
    public ArcOut[] getArcOut() {
        return ListOut;
    }

    /**
     * Finds the place of Petri net with given name
     *
     * @param s name of place
     * @return number of place with given name
     */
    public int strToNumP(String s) {
        
        int a = -1;
        for (PetriP e : ListP) {
            if (s.equalsIgnoreCase(e.getName())) {
                a = e.getNumber();
                
            }
        }
        return a;
    }

    /**
     * Determines the quantity of markers in place with given name
     *
     * @param s name of place
     * @return quantity of markers in place with given name
     */
    public int getCurrentMark(String s) {
        int a = ListP[PetriNet.this.strToNumP(s)].getMark();
        return a;
    }

    /**
     * Determines the mean value of markers in place with given name
     *
     * @param s name of place
     * @return the mean value of quantity of markers in place with given name
     */
    public double getMeanMark(String s) {
        double a = ListP[PetriNet.this.strToNumP(s)].getMean();
        return a;
    }

    /**
     * Determines quantity of active channels of transition with given name
     *
     * @param s name of transition
     * @return quantity of active channels of transition
     */
    public int getCurrentBuffer(String s) {
        int a = ListT[strToNumT(s)].getBuffer();
        return a;
    }

    /**
     * Determines mean value of active channels of transition with given name
     *
     * @param s name of transition
     * @return the mean value of quantity of active channels of transition
     */
    public double getMeanBuffer(String s) {
        double a = ListT[strToNumT(s)].getMean();
        return a;
    }

    /**
     * Finds the place of Petri net with given name and given set of places
     *
     * @param s name of place
     * @param pp array of places
     * @return the number of place
     */
    public int strToNumP(String s, PetriP[] pp) {
   
        int a = -1;
        for (PetriP e : pp) {
            if (s.equalsIgnoreCase(e.getName())) {
                a = e.getNumber();
                
            }
        }
        return a;
    }

    /**
     * Finds the transition of Petri net with given name
     *
     * @param s name of transition
     * @return the number of transition of Petri net with given name
     */
    public int strToNumT(String s) {
        
        int a = -1;
        for (PetriT e : ListT) {
            if (s.equalsIgnoreCase(e.getName())) {
                a = e.getNumber();
                
            }
        }
        return a;
    }

    /**
     *
     */
    public void printArcs() //додано 1.10.2012
    {
        System.out.println("Petri net " + name + " arcs: " + ListIn.length + " input arcs snd " + ListOut.length + " output arcs");

        for (ArcIn arcs : ListIn) {
            arcs.print();
        }
        for (ArcOut arcs : ListOut) {
            arcs.print();
        }
    }

    /**
     *
     */
    public void printMark() {
        System.out.print("Mark in Net  " + this.getName() + "   ");
        for (PetriP position: ListP) {
            System.out.print(position.getMark() + "  ");
        }
        System.out.println();
    }
    public void printBuffer() {
        System.out.print("Buffer in Net  " + this.getName() + "   ");
        for (PetriT transition: ListT) {
            System.out.print(transition.getBuffer() + "  ");
        }
        System.out.println();
    }
    
    @Override
    public PetriNet clone() throws CloneNotSupportedException //14.11.2012
    {
        super.clone();
        PetriP[] copyListP = new PetriP[numP];
        PetriT[] copyListT = new PetriT[numT];
        ArcIn[] copyListIn = new ArcIn[numIn];
        ArcOut[] copyListOut = new ArcOut[numOut];
        for (int j = 0; j < numP; j++) {
            copyListP[j] = ListP[j].clone();
        }
        for (int j = 0; j < numT; j++) {
            copyListT[j] = ListT[j].clone();
        }
        for (int j = 0; j < numIn; j++) {
            copyListIn[j] = ListIn[j].clone();
            copyListIn[j].setNameP(ListIn[j].getNameP());
            copyListIn[j].setNameT(ListIn[j].getNameT());
        }

        for (int j = 0; j < numOut; j++) {
            copyListOut[j] = ListOut[j].clone();
            copyListOut[j].setNameP(ListOut[j].getNameP());
            copyListOut[j].setNameT(ListOut[j].getNameT());
        }

        PetriNet net = new PetriNet(name, copyListP, copyListT, copyListIn, copyListOut);

        return net;
    }
    
    public boolean hasParameters() { // added by Katya 08.12.2016
        for (PetriP petriPlace : ListP) {
            if (petriPlace.markIsParam()) {
                return true;
            }
        }
        for (PetriT petriTran : ListT) {
            if (petriTran.distributionIsParam() || petriTran.parametrIsParam() || petriTran.priorityIsParam() || petriTran.probabilityIsParam()) {
                return true;
            }
        }
        for (ArcIn arcIn : ListIn) {
            if (arcIn.infIsParam() || arcIn.kIsParam()) {
                return true;
            }
        }
        for (ArcOut arcOut : ListOut) {
            if (arcOut.kIsParam()) {
                return true;
            }
        }
        return false;
    }

}

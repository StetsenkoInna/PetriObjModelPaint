package PetriObj;

import java.io.Serializable;

/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
/**
 * This class for creating the arc between place and transition of Petri net
 * (and directed from place to transion) numP - number of place numT - number of
 * transition k - arc multiplicity inf - flag of information arc
 *
 *  @author Inna V. Stetsenko
 */
public class ArcIn implements Cloneable, Serializable {

    private int numP;
    private int numT;
    private int k;
    boolean inf;
    private String nameP;
    private String nameT;
    private static int next = 0;
    private int number;

    // whether k and inf are parameters; added by Katya 08.12.2016
    private boolean kIsParam = false;
    private boolean infIsParam = false;
    // param names
    private String kParamName = null;
    private String infParamName = null;

    
    /**
     *
     */
    public ArcIn() {
        k = 1;
        number = next;
        next++;
    }

    /**
     * @param P number of place
     * @param T number of transition
     * @param K arc multiplicity
     *
     */
    public ArcIn(int P, int T, int K) {
        numP = P;
        numT = T;
        k = K;
        inf = false;
        number = next;
        next++;
    }

    /**
     *
     * @param P number of place
     * @param T number of transition
     */
    public ArcIn(PetriP P, PetriT T) {
        numP = P.getNumber();
        numT = T.getNumber();
        k = 1;
        inf = false;
        nameP = P.getName();
        nameT = T.getName();
        number = next;
        next++;
    }

    /**
     *
     * @param P number of place
     * @param T number of transition
     * @param K arc multiplicity
     */
    public ArcIn(PetriP P, PetriT T, int K) {
        numP = P.getNumber();
        numT = T.getNumber();
        k = K;
        inf = false;
        nameP = P.getName();
        nameT = T.getName();
        number = next;
        next++;
    }

    /**
     *
     * @param P number of place
     * @param T number of transition
     * @param K arc multiplicity
     * @param isInf arc is informational
     */
    public ArcIn(PetriP P, PetriT T, int K, boolean isInf) {
        numP = P.getNumber();
        numT = T.getNumber();
        k = K;
        inf = isInf;
        nameP = P.getName();
        nameT = T.getName();
        number = next;
        next++;
    }

    public ArcIn(ArcIn arcIn) {
        this(arcIn.getNumP(), arcIn.getNumT(), arcIn.getQuantity());
        inf = arcIn.getIsInf();
    }

    public boolean kIsParam() {
        return kIsParam;
    }
    
    public boolean infIsParam() {
        return infIsParam;
    }
    
    public String getKParamName() {
        return kParamName;
    }
    
    public String getInfParamName() {
        return infParamName;
    }
    
    public void setKParam(String paramName) {
        if (paramName == null) {
            kIsParam = false;
            kParamName = null;
        } else {
            kIsParam = true;
            kParamName = paramName;
            k = 1;
        }
    }
    
    public void setInfParam(String paramName) {
        if (paramName == null) {
            infIsParam = false;
            infParamName = null;
        } else {
            infIsParam = true;
            infParamName = paramName;
            inf = false;
        }
    }
    /**
     * Set the counter of input arcs to zero.
     */
    public static void initNext(){ //ініціалізація лічильника нульовим значенням
            next = 0;
    }

    /**
     *
     * @return arc multiplicity
     */
    public int getQuantity() {
        return k;
    }

    /**
     *
     * @param K value of arc multiplicity
     */
    public void setQuantity(int K) {
        k = K;
    }

    /**
     *
     * @return the number of place that is the beginning of the arc
     */
    public int getNumP() {
        return numP;
    }

    /**
     *
     * @param n number of place that is the beginning of the arc
     */
    public void setNumP(int n) {
        numP = n;
    }

    /**
     *
     * @return number of transition that is the end of the arc
     */
    public int getNumT() {
        return numT;
    }

    /**
     *
     * @param n number of transition that is the end of the arc
     */
    public void setNumT(int n) {
        numT = n;
    }

    /**
     *
     * @return transition name
     */
    public String getNameT() {
        return nameT;
    }

    /**
     *
     * @param s transition name
     */
    public void setNameT(String s) {
        nameT = s;
    }

    /**
     *
     * @return name of place that is the beginning of the arc
     */
    public String getNameP() {
        return nameP;
    }

    /**
     *
     * @param s name of place that is the beginning of the arc
     */
    public void setNameP(String s) {
        nameP = s;
    }

    /**
     *
     * @return true if arc is informational
     */
    public boolean getIsInf() {
        return inf;
    }

    /**
     *
     * @param i equals true if arc must be informational
     */
    public void setInf(boolean i) {
        inf = i;
    }

    /**
     *
     */
    public void print() {
        if (nameP != null && nameT != null) {
            System.out.println(" P=  " + nameP + ", T=  " + nameT + ", inf= " + getIsInf() + ", k= " + getQuantity());
        } else {
            System.out.println(" P= P" + numP + ", T= T" + numT + ", inf= " + getIsInf() + ", k= " + getQuantity());
        }
    }

    public void printParameters() {
        System.out.println("This arc has direction from  place with number " + numP + " to transition with number " + numT
                + " and has " + k + " value of multiplicity, ");
        if (inf == true) {
            System.out.println(" and is informational.");
        }
    }

    /**
     *
     * @return ArcIn object with parameters which copy current parameters of
 this arc
     * @throws java.lang.CloneNotSupportedException if Petri net has invalid structure
     */
    @Override
    public ArcIn clone() throws CloneNotSupportedException {
        super.clone();
        ArcIn arc = new ArcIn(numP, numT, k);  // коректніть номерів дуже важлива!!!
        return arc;

    }

}

/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package PetriObj;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Random;
import javax.swing.JTextArea;

/**
 * This class provides constructing Petri object model.<br>
 * List of Petri-objects contains Petri-objects with links between them.<br>
 * For creating Petri-object use class PetriSim. For linking Petri-objects use
 * combining places and passing tokens.<br>
 * Method DoT() of class PetriSim provides programming the passing tokens from
 * the transition of one Petri-object to the place of other.
 *
 * @author Inna V. Stetsenko
 */
public class PetriObjModel implements Serializable, Cloneable  {

    private ArrayList<PetriSim> listObj = new ArrayList<>();
    private boolean protocolPrint = true;
    private boolean statistics = true;
    private ArrayList<LinkByPlaces> links; //added 29.11.2017 by Inna
    private StateTime timeState;
    
    private String id; // unique number for server
    
    
    public PetriObjModel(ArrayList<PetriSim> listObj) {
        this(listObj, new StateTime());
    }
    public PetriObjModel(String id, ArrayList<PetriSim> listObj) {
        this(listObj, new StateTime());
        this.id = id;
    }
    
    
    public PetriObjModel(ArrayList<PetriSim> listObj, StateTime timeState) {
        this.listObj = listObj;
        this.timeState = timeState;
        links = new ArrayList<>(); //added 29.11.2017 by Inna
        
        this.listObj.forEach(sim -> sim.setTimeState(timeState));
    }
    
    
    
    
    @Override
    public PetriObjModel clone() throws CloneNotSupportedException {  //added 29.11.2017 by Inna
        super.clone();
        ArrayList<PetriSim> copyList = new ArrayList<>();

        for (PetriSim sim : this.listObj) {
            copyList.add(sim.clone());  //  error: we must reproduce shared places
        }
        PetriObjModel clone = new PetriObjModel(copyList);
        //  reproduce combine places
 
        for (LinkByPlaces li : links) {
            int one = this.getNumInList(li.getOne());
            int other = this.getNumInList(li.getOther());
 
            if (one >= 0 && other >= 0) {
                PetriSim oneClone = clone.getListObj().get(one);
                PetriSim otherClone = clone.getListObj().get(other);
                clone.linkObjectsCombiningPlaces(oneClone, li.getNumPlaceOne(),
                        otherClone, li.getNumPlaceOther());
            }
        }
        return clone;
    }
    
    public int getNumInList(PetriSim sim){
       int num=-1;
        for(int j=0;j<listObj.size();j++){
            if(sim==listObj.get(j)){
                num=j;
                break;
            }
       }
        if(num <0 ) System.out.println("No such PetriSim "+sim.getName()+ " in model's list of objects.");
       
        return num;
    }
    
  

    /**
     * Set need in protocol
     *
     * @param b is true if protocol is needed
     */
    public void setIsProtokol(boolean b) {
        setProtocolPrint(b);
    }

    /**
     * Set need in statistics
     *
     * @param b is true if statistics is 
     */
    public void setIsStatistics(boolean b) {
        setStatistics(b);
    }

    /**
     *
     * @return the list of Petri objects of model
     */
    public ArrayList<PetriSim> getListObj() {
        return listObj;
    }

    /**
     * Set list of Petri objects
     *
     * @param List list of Petri objects
     */
    public void setListObj(ArrayList<PetriSim> List) {
        listObj = List;
        this.listObj.forEach(sim -> sim.setTimeState(timeState));
    }

    /**
     * Simulating from zero time until the time equal time modeling.<br>
     * Simulation protocol is printed on console.
     *
     * @param timeModeling time modeling
     * 
     */
    public void go(double timeModeling) {
        double min;
        this.setSimulationTime(timeModeling);   
        this.setCurrentTime(0.0); 
      
        getListObj().sort(PetriSim.getComparatorByPriority()); //edited 9.11.2015, 12.10.2017
        for (PetriSim e : getListObj()) { //edited 9.11.2015, 18.07.2018
            e.input();
        }
        if (isProtocolPrint() == true) {
            for (PetriSim e : getListObj()) {
                e.printMark();
            }
        }
        ArrayList<PetriSim> conflictObj = new ArrayList<>();
        Random r = new Random();

        while (this.getCurrentTime() < this.getSimulationTime()) { // edited 18.07.2018

            conflictObj.clear();

            min = getListObj().get(0).getTimeMin();  //пошук найближчої події

            for (PetriSim e : getListObj()) {
                if (e.getTimeMin() < min) {
                    min = e.getTimeMin();
                }
            }
            /*  if(min_t<t){ // added 24.06.2013   !!!!Подумать...при отрицательных задержках висит!!!!
             JOptionPane.showMessageDialog(null, "Negative time delay was generated! Check parameters, please/");
             return;
            
             }*/
            if (isStatistics() == true) {
                for (PetriSim e : getListObj()) {
                   if (min > 0) {
                        if(min<this.getSimulationTime())
                            e.doStatistics((min - this.getCurrentTime()) / min); //статистика за час "дельта т", для спільних позицій потрібно статистику збирати тільки один раз!!!
                        else
                            e.doStatistics((this.getSimulationTime() - this.getCurrentTime()) / this.getSimulationTime()); 
                    }

                }
            }

           this.setCurrentTime(min); // просування часу //3.12.2015
            
            if (isProtocolPrint() == true) {
                System.out.println(" Time progress: time = " + this.getCurrentTime() + "\n");
            }
            if (this.getCurrentTime() <= this.getSimulationTime()) {

                for (PetriSim sim : getListObj()) {
                    if (this.getCurrentTime() == sim.getTimeMin()) // розв'язання конфлікту об'єктів рівноймовірнісним способом
                    {
                        conflictObj.add(sim);                           //список конфліктних обєктів
                    }
                }
                int num;
                int max;
                if (isProtocolPrint() == true) {
                    System.out.println(" List of conflicting objects  " + "\n");
                    for (int ii = 0; ii < conflictObj.size(); ii++) {
                        System.out.println(" K [ " + ii + "  ] = " + conflictObj.get(ii).getName() + "\n");
                    }
                }

                if (conflictObj.size() > 1) { //вибір об'єкта, що запускається
                    max = conflictObj.size();
                    conflictObj.sort(PetriSim.getComparatorByPriority());
                    for (int i = 1; i < conflictObj.size(); i++) { //System.out.println("  "+conflictObj.get(i).getPriority()+"  "+conflictObj.get(i-1).getPriority());
                        if (conflictObj.get(i).getPriority() < conflictObj.get(i - 1).getPriority()) {
                            max = i - 1;
                            //System.out.println("max=  "+max);
                            break;
                        }

                    }
                    if (max == 0) {
                        num = 0;
                    } else {
                        num = r.nextInt(max);
                    }
                } else {
                    num = 0;
                }

                if (isProtocolPrint() == true) {
                    System.out.println(" Selected object  " + conflictObj.get(num).getName() + "\n" + " NextEvent " + "\n");
                }

                for (PetriSim sim: getListObj()) {
                    if (sim.getNumObj() == conflictObj.get(num).getNumObj()) {
                        if (isProtocolPrint() == true) {
                            System.out.println(" time =   " + this.getCurrentTime() + "   Event '" + sim.getEventMin().getName() + "'\n"
                                    + "                       is occuring for the object   " + sim.getName() + "\n");
                        }
                        sim.doT();
                        sim.output(); // added by Inna 11.07.2018
                    }
                }
                if (isProtocolPrint() == true) {
                    System.out.println("Markers output:");
                    for (PetriSim sim : getListObj()) //ДРУК поточного маркірування
                    {
                        sim.printMark();
                    }
                }
                
                Collections.shuffle(getListObj()); // added by Inna 11.07.2018, need for correct functioning of Petri object's shared resource 
                
                getListObj().sort(PetriSim.getComparatorByPriority());
                
                for (PetriSim e : getListObj()) {
                    //можливо змінились умови для інших обєктів
                    e.input(); //вхід маркерів в переходи Петрі-об'єкта

                }
                if (isProtocolPrint() == true) {
                    System.out.println("Markers input:");
                    for (PetriSim e : getListObj()){ //ДРУК поточного маркірування
                          e.printMark();
                    }
                }
            }
        }
        getListObj().sort(PetriSim.getComparatorByNum()); // return the initial order in the list for a correct output of the results (in SMO test)
    }
    
    
     
    /**
     * Prints the string in given JTextArea object
     *
     * @param info string for printing
     * @param area specifies where simulation protocol is printed
     */
    public void printInfo(String info, JTextArea area){
        if(isProtocolPrint() == true)
            area.append(info);
    }
    /**
     * Prints the quantity for each position of Petri net
     *
     * @param area specifies where simulation protocol is printed
     */
    public void printMark(JTextArea area){
        if (isProtocolPrint() == true) {
            for (PetriSim e : listObj) {
                e.printMark(area);
            }
        }
    }
    
    public void setCurrentTime(double t){
        getTimeState().setCurrentTime(t);
        for(PetriSim sim: this.listObj) {
            sim.setTimeCurr(t);   //3.12.2015
       }
    }
    
    public double getCurrentTime(){
        return getTimeState().getCurrentTime();
    }
    
    public void printStatistics(){
       System.out.println("State of places and transitions:");
        for (PetriSim e : listObj) {
                e.printMark();
                e.printBuffer();
        } 
       
        if (this.isStatistics() == true) {
            for (PetriSim e : listObj) {
               System.out.println("\nMean value of markers in places and mean value of buffers in transitions for "+e.getName()+" object");
                for(PetriP p: e.getNet().getListP()) {
                   System.out.println(p.getName()+"  "+p.getMean());
               }
                for(PetriT tr: e.getNet().getListT()) {
                   System.out.println(tr.getName()+"  "+tr.getMean());
               }
               
            }
        }
    }
    /**
     * @param t the simulation time to set
     */
    public void setSimulationTime(double t){
        getTimeState().setSimulationTime(t);
        for(PetriSim sim: getListObj()) {
            sim.setSimulationTime(t);   //3.12.2015
       }
    }
    
    public double getSimulationTime(){
        return getTimeState().getSimulationTime();
    }

    /**
     * @return the id
     */
    public String getId() {
        return id;
    }

    /**
     * @param id the id to set
     */
    public void setId(String id) {
        this.id = id;
    }
    
    public void linkObjectsCombiningPlaces(PetriSim one, int numberOne, PetriSim other, int numberOther) { //added 29.11.2017 by Inna
        
         if (listObj.contains(one) && listObj.contains(other)) {
             one.getNet().getListP()[numberOne] = other.getNet().getListP()[numberOther];   // combine places
             links.add(new LinkByPlaces(one, numberOne, other, numberOther));
         } else {
             System.out.println("ERROR: no such PetriSim objects in model's list of objects");
         }
     }
   
     public void clearLinks(){ //added 29.11.2017 by Inna
         links.clear();
     }

    /**
     * @return the protocolPrint
     */
    public boolean isProtocolPrint() {
        return protocolPrint;
    }

    /**
     * @param protocolPrint the protocolPrint to set
     */
    public void setProtocolPrint(boolean protocolPrint) {
        this.protocolPrint = protocolPrint;
    }

    /**
     * @return the statistics
     */
    public boolean isStatistics() {
        return statistics;
    }

    /**
     * @param statistics the statistics to set
     */
    public void setStatistics(boolean statistics) {
        this.statistics = statistics;
    }

    /**
     * @return the timeState
     */
    public StateTime getTimeState() {
        return timeState;
    }

    /**
     * @param timeState the timeState to set
     */
    public void setTimeState(StateTime timeState) {
        this.timeState = timeState;
        this.listObj.forEach(sim -> sim.setTimeState(timeState));
       
    }

    
   private class LinkByPlaces{ //added 29.11.2017 by Inna
        PetriSim one, other;
        int numOne, numOther;
        LinkByPlaces(PetriSim simOne, int nOne, PetriSim simOther, int nOther){
            one = simOne;
            other = simOther;
            numOne = nOne;
            numOther = nOther;
            
        }
        private PetriSim getOne(){
            return one;
        }
         private PetriSim getOther(){
            return other;
        }
         private int getNumPlaceOne(){
             return numOne;
         }
         private int getNumPlaceOther(){
             return numOther;
         }
     
    }
    
    public void printLinks(){ //added 29.11.2017 by Inna
        System.out.println(" number of links "+links.size());
        for(LinkByPlaces li:links ){
            System.out.println(li.getOne().getName()+".p["+ li.getNumPlaceOne()+"] -> "+
                                li.getOther().getName()+".p["+ li.getNumPlaceOther()+"] ");
        }
    }
    
}

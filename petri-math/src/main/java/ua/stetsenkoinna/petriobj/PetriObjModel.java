package ua.stetsenkoinna.petriobj;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

    private static final Logger log = LoggerFactory.getLogger(PetriObjModel.class);

    private final ArrayList<PetriObjLink> links;

    private ArrayList<PetriSim> listObj;
    private boolean protocolPrint = true;
    private boolean statistics = true;
    private StateTime timeState;
    
    private String id; // unique number for server

    private SimulationStatisticCollector statisticCollector;
    
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
        links = new ArrayList<>();
        this.listObj.forEach(sim -> sim.setTimeState(timeState));
        indexObjects();
    }

    @Override
    public PetriObjModel clone() throws CloneNotSupportedException {
        super.clone();
        ArrayList<PetriSim> copyList = new ArrayList<>();

        for (PetriSim sim : this.listObj) {
            copyList.add(sim.clone());
        }
        PetriObjModel clone = new PetriObjModel(copyList);
        // The copies are unlinked nets, so replaying the declarations rebuilds the very
        // same shared places and inter-object arcs the original has.
        clone.links.addAll(this.links);
        clone.applyLinks();
        return clone;
    }

    /**
     * Numbers the Petri-objects by their position in the list, which is what link
     * declarations and statistic formulas address them by.
     */
    private void indexObjects() {
        for (int i = 0; i < listObj.size(); i++) {
            listObj.get(i).setObjIndex(i);
        }
    }
    
    public int getNumInList(PetriSim sim){
       int num=-1;
        for(int j=0;j<listObj.size();j++){
            if(sim==listObj.get(j)){
                num=j;
                break;
            }
       }
        if(num <0 ) log.warn("No such PetriSim {} in model's list of objects.", sim.getName());
       
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
        indexObjects();
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
      
        getListObj().sort(PetriSim.getComparatorByPriority());
        for (PetriSim e : getListObj()) {
            e.input();
        }
        if (isProtocolPrint()) {
            for (PetriSim e : getListObj()) {
                e.printMark();
            }
        }
        ArrayList<PetriSim> conflictObj = new ArrayList<>();
        Random r = new Random();

        while (this.getCurrentTime() < this.getSimulationTime()) {

            conflictObj.clear();

            min = getListObj().getFirst().getTimeMin();  //пошук найближчої події

            for (PetriSim e : getListObj()) {
                if (e.getTimeMin() < min) {
                    min = e.getTimeMin();
                }
            }

            if (isStatistics()) {
                for (PetriSim e : getListObj()) {
                   if (min > 0) {
                        if(min<this.getSimulationTime())
                            e.doStatistics((min - this.getCurrentTime()) / min);
                        else
                            e.doStatistics((this.getSimulationTime() - this.getCurrentTime()) / this.getSimulationTime());
                    }
                    if (statisticCollector != null && statisticCollector.shouldCollect(getCurrentTime())) {
                        statisticCollector.onTimeStep(getCurrentTime(), e.getNet(), e.getStatisticId());
                    }
                }
                if (statisticCollector != null && statisticCollector.shouldCollect(getCurrentTime())) {
                    statisticCollector.flush(getCurrentTime());
                }
            }
           this.setCurrentTime(min);
            
            if (isProtocolPrint()) {
                log.info(" Time progress: time = " + this.getCurrentTime() + "\n");
            }
            if (this.getCurrentTime() <= this.getSimulationTime()) {

                for (PetriSim sim : getListObj()) {
                    if (this.getCurrentTime() == sim.getTimeMin())
                    {
                        // розв'язання конфлікту об'єктів рівноймовірнісним способом
                        conflictObj.add(sim); //список конфліктних обєктів
                    }
                }
                int num;
                int max;
                if (isProtocolPrint()) {
                    log.info(" List of conflicting objects  " + "\n");
                    for (int ii = 0; ii < conflictObj.size(); ii++) {
                        log.info(" K [ " + ii + "  ] = " + conflictObj.get(ii).getName() + "\n");
                    }
                }

                if (conflictObj.size() > 1) { //вибір об'єкта, що запускається
                    max = conflictObj.size();
                    conflictObj.sort(PetriSim.getComparatorByPriority());
                    for (int i = 1; i < conflictObj.size(); i++) {
                        if (conflictObj.get(i).getPriority() < conflictObj.get(i - 1).getPriority()) {
                            max = i - 1;
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

                if (isProtocolPrint()) {
                    log.info(" Selected object  " + conflictObj.get(num).getName() + "\n" + " NextEvent " + "\n");
                }

                for (PetriSim sim: getListObj()) {
                    if (sim.getNumObj() == conflictObj.get(num).getNumObj()) {
                        if (isProtocolPrint()) {
                            log.info(
                                    " time =   " + this.getCurrentTime() + "   Event '" + sim.getEventMin().getName() + "'\n"
                                    + "                       is occuring for the object   " + sim.getName() + "\n"
                            );
                        }
                        sim.doT();
                        sim.output();
                    }
                }
                if (isProtocolPrint()) {
                    log.info("Markers output:");
                    for (PetriSim sim : getListObj()) //ДРУК поточного маркірування
                    {
                        sim.printMark();
                    }
                }
                
                Collections.shuffle(getListObj()); // need for correct functioning of Petri object's shared resource
                getListObj().sort(PetriSim.getComparatorByPriority());
                
                for (PetriSim e : getListObj()) {
                    //можливо змінились умови для інших обєктів
                    e.input(); //вхід маркерів в переходи Петрі-об'єкта

                }
                if (isProtocolPrint()) {
                    log.info("Markers input:");
                    for (PetriSim e : getListObj()){ //ДРУК поточного маркірування
                          e.printMark();
                    }
                }
            }
        }
        if (statisticCollector != null) {
            double time = getCurrentTime() - getSimulationTime() <= getSimulationTime() ? getCurrentTime() : getSimulationTime();
            statisticCollector.onSimulationEnd(time, getListObj());
            statisticCollector.shutdown();
        }
        getListObj().sort(PetriSim.getComparatorByIndex()); // return the initial order in the list for a correct output of the results (in SMO test)
    }

    /**
     * Prints the string in given JTextArea object
     *
     * @param info string for printing
     * @param area specifies where simulation protocol is printed
     */
    public void printInfo(String info, Consumer<String> output){
        if(isProtocolPrint())
            output.accept(info);
    }
    /**
     * Prints the quantity for each position of Petri net
     */
    public void printMark(Consumer<String> output){
        if (isProtocolPrint()) {
            for (PetriSim e : listObj) {
                e.printMark(output);
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
       log.info("State of places and transitions:");
        for (PetriSim e : listObj) {
                e.printMark();
                e.printBuffer();
        }

        if (this.isStatistics()) {
            for (PetriSim e : listObj) {
               log.info("\nMean value of markers in places and mean value of buffers in transitions for "+e.getName()+" object");
                for(PetriP p: e.getNet().getListP()) {
                   log.info(p.getName()+"  "+p.getMean());
               }
                for(PetriT tr: e.getNet().getListT()) {
                   log.info(tr.getName()+"  "+tr.getMean());
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
    
    public void linkObjectsCombiningPlaces(PetriSim one, int numberOne, PetriSim other, int numberOther) {
        int oneIndex = getNumInList(one);
        int otherIndex = getNumInList(other);
        if (oneIndex < 0 || otherIndex < 0) {
            log.error("no such PetriSim objects in model's list of objects");
            return;
        }
        linkObjectsCombiningPlaces(oneIndex, numberOne, otherIndex, numberOther);
    }

    /**
     * Merges a place of one Petri-object with a place of another: from now on both objects
     * see the same marking through the same {@link PetriP} instance.
     *
     * @param oneObject index of the object whose place slot is redirected
     * @param onePlace index of that place inside the object's net
     * @param otherObject index of the object that owns the resulting shared place
     * @param otherPlace index of that place inside the other object's net
     */
    public void linkObjectsCombiningPlaces(int oneObject, int onePlace, int otherObject, int otherPlace) {
        addLink(PetriObjLink.placeFusion(oneObject, onePlace, otherObject, otherPlace));
    }

    /**
     * Makes a transition of one Petri-object deliver tokens straight into a place of
     * another one, without the source object owning a matching output place.
     *
     * @param sourceObject index of the object that owns the firing transition
     * @param sourceTransition index of that transition inside the source object's net
     * @param targetObject index of the object that owns the receiving place
     * @param targetPlace index of that place inside the target object's net
     * @param quantity how many tokens each firing delivers
     */
    public void linkTransitionToPlace(int sourceObject, int sourceTransition,
                                      int targetObject, int targetPlace, int quantity) {
        addLink(PetriObjLink.transitionToPlace(sourceObject, sourceTransition,
                targetObject, targetPlace, quantity));
    }

    /**
     * Adds a place of one Petri-object to the firing condition of a transition of another
     * one — consuming its tokens, or only testing them when the arc is informational.
     *
     * @param sourceObject index of the object that owns the place
     * @param sourcePlace index of that place inside the source object's net
     * @param targetObject index of the object that owns the transition
     * @param targetTransition index of that transition inside the target object's net
     * @param quantity arc multiplicity
     * @param informational {@code true} to test the marking without consuming it
     */
    public void linkPlaceToTransition(int sourceObject, int sourcePlace,
                                      int targetObject, int targetTransition,
                                      int quantity, boolean informational) {
        addLink(PetriObjLink.placeToTransition(sourceObject, sourcePlace,
                targetObject, targetTransition, quantity, informational));
    }

    /**
     * Records a link declaration and wires it into the object graph.
     *
     * @param link the link to add
     * @throws IllegalArgumentException if the link addresses an object or an element that
     *         this model does not have
     */
    public void addLink(PetriObjLink link) {
        wire(link);
        links.add(link);
    }

    /**
     * Checks every object's net now that every declared link has been wired in — the point at
     * which a transition fed only by another Petri-object's place can finally be told apart
     * from one with no input at all. See {@link PetriNet#validateStructure()}.
     *
     * @throws ExceptionInvalidTimeDelay naming the first transition with no consuming input
     */
    public void validateStructure() throws ExceptionInvalidTimeDelay {
        for (PetriSim sim : listObj) {
            sim.getNet().validateStructure();
        }
    }

    /**
     * @return the link declarations of this model, in the order they were added
     */
    public List<PetriObjLink> getLinks() {
        return Collections.unmodifiableList(links);
    }

    /**
     * Wires every declared link into the object graph.
     *
     * <p>Meant for a model whose Petri-objects are freshly built — that is, whose places are
     * not shared and whose transitions carry no {@link ExternalArc} yet. Inter-object arcs
     * are dropped first, but a place fusion cannot be undone, so replaying the declarations
     * over an already linked model would chain fusions further instead of reproducing them.
     */
    public void applyLinks() {
        for (PetriSim sim : listObj) {
            sim.clearExternalArcs();
        }
        for (PetriObjLink link : links) {
            wire(link);
        }
    }

    /**
     * Turns a single declaration into the corresponding shared place or external arc.
     */
    private void wire(PetriObjLink link) {
        PetriSim source = objectAt(link.getSourceObject(), link);
        PetriSim target = objectAt(link.getTargetObject(), link);
        switch (link.getType()) {
            case PLACE_FUSION -> {
                PetriP[] sourcePlaces = source.getNet().getListP();
                PetriP shared = placeAt(target, link.getTargetElement(), link);
                checkElementIndex(link.getSourceElement(), sourcePlaces.length, "place", link);
                sourcePlaces[link.getSourceElement()] = shared;
            }
            case TRANSITION_TO_PLACE -> transitionAt(source, link.getSourceElement(), link)
                    .addExternalOutput(placeAt(target, link.getTargetElement(), link), link.getQuantity());
            case PLACE_TO_TRANSITION -> transitionAt(target, link.getTargetElement(), link)
                    .addExternalInput(placeAt(source, link.getSourceElement(), link),
                            link.getQuantity(), link.isInformational());
        }
    }

    private PetriSim objectAt(int index, PetriObjLink link) {
        if (index >= listObj.size()) {
            throw new IllegalArgumentException(
                    "Link " + link + " refers to Petri-object " + index
                            + " but the model has only " + listObj.size());
        }
        return listObj.get(index);
    }

    private PetriP placeAt(PetriSim sim, int index, PetriObjLink link) {
        PetriP[] places = sim.getNet().getListP();
        checkElementIndex(index, places.length, "place", link);
        return places[index];
    }

    private PetriT transitionAt(PetriSim sim, int index, PetriObjLink link) {
        PetriT[] transitions = sim.getNet().getListT();
        checkElementIndex(index, transitions.length, "transition", link);
        return transitions[index];
    }

    private static void checkElementIndex(int index, int size, String element, PetriObjLink link) {
        if (index >= size) {
            throw new IllegalArgumentException(
                    "Link " + link + " refers to " + element + " " + index
                            + " but the net has only " + size);
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

    
    public void printLinks(){
        log.info(" number of links {}", links.size());
        for (PetriObjLink link : links) {
            log.info(link.toString());
        }
    }

    public void setStatisticCollector(SimulationStatisticCollector statisticCollector) {
        this.statisticCollector = statisticCollector;
    }

    /** @deprecated Use {@link #setStatisticCollector(SimulationStatisticCollector)} */
    @Deprecated
    public void setStatisticMonitor(SimulationStatisticCollector statisticCollector) {
        this.statisticCollector = statisticCollector;
    }

}

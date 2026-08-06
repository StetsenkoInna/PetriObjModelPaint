package ua.stetsenkoinna.petriobj;

import java.io.Serializable;
import java.util.*;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class is Petri simulator. <br>
 * The object of this class simulates dynamics of functioning according to Petri
 * net, given in his data field. Such object is named Petri-object.
 *
 *  @author Inna V. Stetsenko
 */
public class PetriSim implements Cloneable, Serializable {

    private static final Logger log = LoggerFactory.getLogger(PetriSim.class);

    /** Value of {@link #getObjIndex()} for a Petri-object that does not belong to a model yet. */
    public static final int UNASSIGNED_INDEX = -1;

    private static int next = 1; //лічильник створених об"єктів

    private final PetriNet net;
    private final ArrayList<PetriP> listPositionsForStatistica = new ArrayList<>();
    private final PetriP[] listP;
    private final PetriT[] listT;

    private StateTime timeState;
    private String name;
    private int numObj; //поточний номер створюваного об"єкта
    private int objIndex = UNASSIGNED_INDEX;
    private int priority;
    private int numP;
    private int numT;
    private int numIn;
    private int numOut;
    private ArcIn[] listIn;
    private ArcOut[] listOut;
    private String id; //unique number of object for server

    protected PetriT eventMin; // modifier is edited on protected for the subclass access
    protected double timeMin; // modifier is edited on protected for the subclass access
    
    /**
     * Constructs the Petri simulator with given Petri net and time modeling
     *
     * @param net Petri net that describes the dynamics of object
     */
   public PetriSim(PetriNet net) {
        this(net, new StateTime());
    }
   
   public PetriSim(String id, PetriNet net, StateTime timeState) {
        this(net, new StateTime());
        this.id = id; // server set id
   }
    
    public PetriSim(PetriNet net, StateTime timeState) {
        this.net = net;
        this.timeState = timeState;
        name = net.getName();
        numObj = next; 
        next++;        
        timeMin = Double.MAX_VALUE;
            
        listP = net.getListP();
        listT = net.getListT();
        listIn = net.getArcIn();
        listOut = net.getArcOut();
        numP = listP.length;
        numT = listT.length;
        numIn = listIn.length;
        numOut = listOut.length;
        eventMin = this.getEventMin();
        priority = 0;
        listPositionsForStatistica.addAll(Arrays.asList(listP));
        
        id = null; // server set id
    }

    @Override
    public PetriSim clone() throws CloneNotSupportedException{
        super.clone();
        return new PetriSim(this.getNet().clone());
    }
    
    /**
     *
     * @return PetriNet
     */
    public PetriNet getNet() {
        return net;
    }

    /**
     *
     * @return name of Petri-object
     */
    public String getName() {
        return name;
    }

    /**
     *
     * @return list of places for statistics which use for statistics
     */
    public ArrayList<PetriP> getListPositionsForStatistica() {
        return listPositionsForStatistica;
    }

    /**
     * Get priority of Petri-object
     *
     * @return value of priority
     */
    public int getPriority() {
        return priority;
    }

    /**
     *
     * @return the number of object
     */
    public int getNumObj() {
        return numObj;
    }

    /**
     * Position of this Petri-object in the object list of the {@link PetriObjModel} that
     * owns it, assigned when the model is built.
     *
     * <p>Unlike {@link #getNumObj()}, which counts every Petri-object ever created in the
     * JVM, this index is stable, zero-based and independent of how many simulations ran
     * before. It is the index used to address a Petri-object in statistic formulas
     * ({@code O0.P1}, {@code O1.T2}, …) and in {@link PetriObjLink} declarations.
     *
     * @return the zero-based index in the model, or {@link #UNASSIGNED_INDEX} when this
     *         Petri-object is not part of a model
     */
    public int getObjIndex() {
        return objIndex;
    }

    /**
     * Assigns the position of this Petri-object in its model. Called by
     * {@link PetriObjModel} — application code has no reason to invoke it.
     *
     * @param objIndex the zero-based index in the model's object list
     */
    public void setObjIndex(int objIndex) {
        this.objIndex = objIndex;
    }

    /**
     * @return {@link #getObjIndex()} when this Petri-object belongs to a model, and the
     *         legacy creation counter otherwise, so statistic consumers always get a value
     */
    public int getStatisticId() {
        return objIndex == UNASSIGNED_INDEX ? numObj : objIndex;
    }

    /**
     * Drops the inter-object arcs of every transition of this Petri-object.
     *
     * @see PetriT#clearExternalArcs()
     */
    public void clearExternalArcs() {
        for (PetriT transition : listT) {
            transition.clearExternalArcs();
        }
    }

    /**
     * Set priority of Petri-object
     *
     * @param a value of priority
     */
    public void setPriority(int a) {
        priority = a;
    }

    /**
     * This method uses for describing other actions associated with transition
     * markers output.<br>
     * Such as the output markers into the other Petri-object.<br>
     * The method is overridden in subclass.
     */
    public void doT() {

    }

    /**
     * Hook called just before a transition fires its input arc (tokens enter).
     * Override in subclass to add pre-firing behaviour (e.g. animation).
     */
    protected void beforeActIn(PetriT tr) {}

    /**
     * Hook called just after a transition fires its input arc (tokens enter).
     * Override in subclass to add post-firing behaviour (e.g. animation, delay).
     */
    protected void afterActIn(PetriT tr) {}

    /**
     * Hook called just before a transition fires its output arc (tokens leave).
     */
    protected void beforeActOut(PetriT tr) {}

    /**
     * Hook called just after a transition fires its output arc (tokens leave).
     */
    protected void afterActOut(PetriT tr) {}

    /**
     * Returns true if the current simulation step should be interrupted.
     * Override to implement pause/halt support (e.g. in animated subclass).
     */
    protected boolean shouldInterrupt() {
        return false;
    }

    /**
     * Determines the next event and its moment.
     */
    public void eventMin() {
        PetriT event = null; //пошук часу найближчої події
        // якщо усі переходи порожні, то це означає зупинку імітації, 
        // отже за null значенням eventMin можна відслідковувати зупинку імітації
        double min = Double.MAX_VALUE;
        for (PetriT transition : listT) {
            if (transition.getMinTime() < min) {
                event = transition;
                min = transition.getMinTime();
            }
        }
        timeMin = min;
        eventMin = event;
    }

    /**
     *
     * @return moment of next event
     */
    public double getTimeMin() {
        return timeMin;
    }

    /**
     * Finds the set of transitions for which the firing condition is true and
     * sorts it on priority value
     *
     * @return the sorted list of transitions with the true firing condition
     */
    public ArrayList<PetriT> findActiveT() {
        ArrayList<PetriT> aT = new ArrayList<>();

        for (PetriT transition : listT) {
            if ((transition.condition(listP)) && (transition.getProbability() != 0)) {
                aT.add(transition);

            }
        }

        if (aT.size() > 1) {
            // сортування переходів за спаданням пріоритету
            aT.sort((o1, o2) -> Integer.compare(o2.getPriority(), o1.getPriority()));
        }
        return aT;
    }
       /**
     * @return the timeCurr
     */
    public double getCurrentTime() {
        return getTimeState().getCurrentTime();
    }

    /**
     * @param aTimeCurr the timeCurr to set
     */
    public void setTimeCurr(double aTimeCurr) {
        getTimeState().setCurrentTime(aTimeCurr);
    }
    /**
     * @return the timeMod
     */
    public double getSimulationTime() {
        return getTimeState().getSimulationTime();
    }

    /**
     * @param aTimeMod the timeMod to set
     */
    public void setSimulationTime(double aTimeMod) {
        getTimeState().setSimulationTime(aTimeMod);
       
    }
    
    public static Comparator<PetriSim> getComparatorByPriority() {
        return (o1, o2) -> Integer.compare(o2.getPriority(), o1.getPriority());
    }
    
    public static Comparator<PetriSim> getComparatorByNum() {
        return Comparator.comparingInt(PetriSim::getNumObj);
    }

    /**
     * Orders Petri-objects by their position in the model, falling back to the creation
     * counter for objects that do not belong to a model.
     *
     * @return comparator restoring the declared order of a model's object list
     */
    public static Comparator<PetriSim> getComparatorByIndex() {
        return Comparator.comparingInt(PetriSim::getStatisticId);
    }

    /**
     * Do one event
     */
    public void step() {
        // використовується для одного об'єкту мережа Петрі
        log.info("Next Step  time={}", this.getCurrentTime());

        this.printMark(); //друкувати поточне маркування
        ArrayList<PetriT> activeT = this.findActiveT(); //формування списку активних переходів

        if ((activeT.isEmpty() && isBufferEmpty()) || this.getCurrentTime() >= getSimulationTime()) {
            //зупинка імітації за умови, що немає переходів, які запускаються, і немає маркерів у переходах, або вичерпаний час моделювання
            log.info("STOP in Net  {}", this.getName());
            timeMin = getSimulationTime();
            for (PetriP p : listP) {
                p.changeMean((timeMin - this.getCurrentTime()) / getSimulationTime());
            }

            for (PetriT transition : listT) {
                transition.changeMean((timeMin - this.getCurrentTime()) / getSimulationTime());
            }
            setTimeCurr(timeMin);//просування часу
        } else {
            while (!activeT.isEmpty()) { //вхід маркерів в переходи доки можливо
                this.doConflikt(activeT).actIn(listP, this.getCurrentTime()); //розв'язання конфліктів
                activeT = this.findActiveT(); //оновлення списку активних переходів
            }

            this.eventMin();//знайти найближчу подію та ії час
            
            for (PetriP position : listP) {
                position.changeMean((timeMin - this.getCurrentTime()) / getSimulationTime());
            }

            for (PetriT transition : listT) {
                transition.changeMean((timeMin - this.getCurrentTime()) / getSimulationTime());
            }

            setTimeCurr(timeMin);         //просування часу

            if (this.getCurrentTime() <= getSimulationTime()) {

                //Вихід маркерів
                eventMin.actOut(listP, this.getCurrentTime() );//Вихід маркерів з переходу, що відповідає найближчому моменту часу

                if (eventMin.getBuffer() > 0) {
                    boolean u = true;
                    while (u) {
                        eventMin.minEvent();
                        if (eventMin.getMinTime() == this.getCurrentTime()) {
                            eventMin.actOut(listP, this.getCurrentTime());
                        } else {
                            u = false;
                        }
                    }
                }

                for (PetriT transition : listT) {
                    //ВАЖЛИВО!!Вихід з усіх переходів, що час виходу маркерів == поточний момент час.
                    if (transition.getBuffer() > 0 && transition.getMinTime() == this.getCurrentTime()) {
                        transition.actOut(listP,this.getCurrentTime());
                        //Вихід маркерів з переходу, що відповідає найближчому моменту часу
                        if (transition.getBuffer() > 0) {
                            boolean u = true;
                            while (u) {
                                transition.minEvent();
                                if (transition.getMinTime() == this.getCurrentTime()) {
                                    transition.actOut(listP, this.getCurrentTime());
                                } else {
                                    u = false;
                                }
                            }
                        }
                    }
                }
            }
        }
    }
   
    /**
     * It does the transitions input markers
     */
    public void input() {
        ArrayList<PetriT> activeT = this.findActiveT();
        if (activeT.isEmpty() && isBufferEmpty()) {
            timeMin = Double.MAX_VALUE;
        } else {
            while (!activeT.isEmpty()) {
                PetriT tr = this.doConflikt(activeT);
                beforeActIn(tr);
                tr.actIn(listP, this.getCurrentTime());
                afterActIn(tr);
                if (shouldInterrupt()) return;
                activeT = this.findActiveT();
            }
            this.eventMin();
        }
    }
    
      /**
     * It does the transitions output markers
     */
    public void output() {
        if (this.getCurrentTime() <= this.getSimulationTime()) {
            beforeActOut(eventMin);
            eventMin.actOut(listP, this.getCurrentTime());
            afterActOut(eventMin);
            if (shouldInterrupt()) return;
            if (eventMin.getBuffer() > 0) {
                boolean u = true;
                while (u) {
                    eventMin.minEvent();
                    if (eventMin.getMinTime() == this.getCurrentTime()) {
                        beforeActOut(eventMin);
                        eventMin.actOut(listP, this.getCurrentTime());
                        afterActOut(eventMin);
                        if (shouldInterrupt()) return;
                    } else {
                        u = false;
                    }
                }
            }
            for (PetriT transition : listT) {
                if (transition.getBuffer() > 0 && transition.getMinTime() == this.getCurrentTime()) {
                    beforeActOut(transition);
                    transition.actOut(listP, this.getCurrentTime());
                    afterActOut(transition);
                    if (shouldInterrupt()) return;
                    if (transition.getBuffer() > 0) {
                        boolean u = true;
                        while (u) {
                            transition.minEvent();
                            if (transition.getMinTime() == this.getCurrentTime()) {
                                beforeActOut(transition);
                                transition.actOut(listP, this.getCurrentTime());
                                afterActOut(transition);
                                if (shouldInterrupt()) return;
                            } else {
                                u = false;
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * It does the transitions input and output markers in the current moment
     */
    public void stepEvent() {
        //один крок,вихід та вхід маркерів в переходи Петрі-об"єкта, використовується для множини Петрі-об'єктів
        if(isStop()){
            timeMin = Double.MAX_VALUE;
             return; //зупинка імітації
        }
        output();
        input();  
    }

    /**
     * Calculates mean value of quantity of markers in places and quantity of
     * active channels of transitions
     */
    public void doStatistics() {
        for (PetriP position : listP) {
            position.changeMean((timeMin - this.getCurrentTime()) / getSimulationTime());
        }
        for (PetriT transition : listT) {
            transition.changeMean((timeMin - this.getCurrentTime()) / getSimulationTime());
        }
    }

    /**
     *
     * @param dt - the time interval
     */
    public void doStatistics(double dt) {
        if (dt > 0) {
            for (PetriP position : listPositionsForStatistica) {
                position.changeMean(dt);
            }
        }
        if (dt > 0) {
            for (PetriT transition : listT) {
                transition.changeMean(dt);
            }
        }
    }

    /**
     * This method use for simulating Petri-object
     */
    public void go() {
        setTimeCurr(0);

        while (this.getCurrentTime() <= getSimulationTime() && !isStop()) {
            this.step();
            if (isStop()) {
                log.info("STOP in net  {}", this.getName());
            }
        }
    }

    /**
     * This method use for simulating Petri-object until current time less then
     * the momemt in parametr time
     *
     * @param time - the simulation time
     */
    public void go(double time) {
        while (this.getCurrentTime() < time && !isStop()) {
            this.step();
            if (isStop()) {
                log.info("STOP in net  {}", this.getName());
            }
        }
    }

    /**
     * Determines is all of transitions has empty buffer
     *
     * @return true if buffer is empty for all transitions of Petri net
     */
    public boolean isBufferEmpty() {
        boolean c = true;
        for (PetriT e : listT) {
            if (e.getBuffer() > 0) {
                c = false;
                break;
            }
        }
        return c;
    }

    /**
     * Do printing the current marking of Petri net
     */
    public void printMark() {
        StringBuilder sb = new StringBuilder("Mark in Net  " + this.getName() + "   ");
        for (PetriP position : listP) {
            sb.append(position.getMark()).append("  ");
        }
        log.info(sb.toString());
    }
    public void printBuffer(){
        StringBuilder sb = new StringBuilder("Buffer in Net  " + this.getName() + "   ");
        for (PetriT transition : listT) {
            sb.append(transition.getBuffer()).append("  ");
        }
        log.info(sb.toString());
    }
    
        
    public void printMark(Consumer<String> output) {
        output.accept("\n Mark in Net  " + this.getName() + "   \n");
        for (PetriP position : listP) {
            output.accept(position.getMark() + "  ");
        }
        output.accept("\n");
    }
   
    /**
     *
     * @return the nearest event
     */
    public final PetriT getEventMin() {
        this.eventMin();
        return eventMin;
    }

    /**
     * This method solves conflict between transitions given in parametr transitions
     *
     * @param transitions the list of transitions
     * @return the transition - winner of conflict
     */
    public PetriT doConflikt(ArrayList<PetriT> transitions) {//
        PetriT aT = transitions.getFirst();
        if (transitions.size() > 1) {
            aT = transitions.getFirst();
            int i = 0;
            while (i < transitions.size() && transitions.get(i).getPriority() == aT.getPriority()) {
                i++;
            }
            if (i != 1){
                double r = Math.random();
                int j = 0;
                double sum = 0;
                double prob;
                while (j < transitions.size() && transitions.get(j).getPriority() == aT.getPriority()) {
                    if (transitions.get(j).getProbability() == 1.0) {
                        prob = 1.0 / i;
                    } else {
                        prob = transitions.get(j).getProbability();
                    }
                    sum = sum + prob;
                    if (r < sum) {
                        aT = transitions.get(j);
                        break;
                    } //вибір переходу за значенням ймовірності
                    else {
                        j++;
                    }
                }
            }
        }
        return aT;
    }

    /**
     * @return the stop
     */
    public boolean isStop() {
        this.eventMin();
        return (eventMin == null);
    }

    /**
     * @param name the name to set
     */
    public void setName(String name) {
        this.name = name;
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
    }
}

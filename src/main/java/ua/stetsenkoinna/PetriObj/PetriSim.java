package ua.stetsenkoinna.PetriObj;

import java.io.Serializable;
import java.util.*;
import javax.swing.JSlider;
import javax.swing.JTextArea;

/**
 * This class is Petri simulator. <br>
 * The object of this class simulates dynamics of functioning according to Petri
 * net, given in his data field. Such object is named Petri-object.
 *
 *  @author Inna V. Stetsenko
 */
public class PetriSim implements Cloneable, Serializable {

    private static int next = 1; //лічильник створених об"єктів

    private final PetriNet net;
    private final ArrayList<PetriP> listPositionsForStatistica = new ArrayList<>();
    private final PetriP[] listP;
    private final PetriT[] listT;

    private StateTime timeState;
    private String name;
    private int numObj; //поточний номер створюваного об"єкта
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

    /**
     * Constructs the Petri simulator with given Petri net and time modeling
     *
     * @param net Petri net that describes the dynamics of object
     * @param delaySlider use for control of the speed of the animation 
     */    
    public PetriSim(PetriNet net, JSlider delaySlider) {
        this(net); // for animation
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
     * Do one event
     */
    public void step() {
        // використовується для одного об'єкту мережа Петрі
        System.out.println("Next Step  " + "time=" + this.getCurrentTime());

        this.printMark(); //друкувати поточне маркування
        ArrayList<PetriT> activeT = this.findActiveT(); //формування списку активних переходів

        if ((activeT.isEmpty() && isBufferEmpty()) || this.getCurrentTime() >= getSimulationTime()) {
            //зупинка імітації за умови, що немає переходів, які запускаються, і немає маркерів у переходах, або вичерпаний час моделювання
            System.out.println("STOP in Net  " + this.getName());
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
    public void input() {//вхід маркерів в переходи Петрі-об'єкта

       ArrayList<PetriT> activeT = this.findActiveT();     //формування списку активних переходів

        if (activeT.isEmpty() && isBufferEmpty()) { //зупинка імітації за умови, що
            //не має переходів, які запускаються,і не має маркерів у переходах
            timeMin = Double.MAX_VALUE;
            //eventMin = null;  // 19.07.2018 by Sasha animation
        } else {
            while (!activeT.isEmpty()) {//запуск переходів доки можливо
                this.doConflikt(activeT).actIn(listP, this.getCurrentTime()); //розв'язання конфліктів
                activeT = this.findActiveT(); //оновлення списку активних переходів
            }
            this.eventMin();//знайти найближчу подію та ії час
        }
    }
    
      /**
     * It does the transitions output markers
     */
   
    public void output(){
            if (this.getCurrentTime() <= this.getSimulationTime()) {
            eventMin.actOut(listP, this.getCurrentTime());//здійснення події
            if (eventMin.getBuffer() > 0) {
                boolean u = true;
                while (u) {
                    eventMin.minEvent();
                    if (eventMin.getMinTime() == this.getCurrentTime()) {
                        eventMin.actOut(listP,this.getCurrentTime());
                    } else {
                        u = false;
                    }
                }
            }
            for (PetriT transition : listT) {
                //ВАЖЛИВО!!Вихід з усіх переходів, що час виходу маркерів == поточний момент час.
                if (transition.getBuffer() > 0 && transition.getMinTime() == this.getCurrentTime()) {
                    transition.actOut(listP, this.getCurrentTime());//Вихід маркерів з переходу, що відповідає найближчому моменту часу
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
                System.out.println("STOP in net  " + this.getName());
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
                System.out.println("STOP in net  " + this.getName());
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
        System.out.print("Mark in Net  " + this.getName() + "   ");
        for (PetriP position : listP) {
            System.out.print(position.getMark() + "  ");
        }
        System.out.println();
    }
    public void printBuffer(){
    System.out.print("Buffer in Net  " + this.getName() + "   ");
        for (PetriT transition : listT) {
            System.out.print(transition.getBuffer() + "  ");
        }
        System.out.println();
    }
    
        
    public void printMark(JTextArea area) {
        area.append("\n Mark in Net  " + this.getName() + "   \n");
        for (PetriP position : listP) {
            area.append(position.getMark() + "  ");
        }
        area.append("\n");
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

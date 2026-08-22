package ua.stetsenkoinna.graphpresentation;

import ua.stetsenkoinna.graphnet.GraphPetriNet;
import ua.stetsenkoinna.petriobj.PetriNet;
import ua.stetsenkoinna.petriobj.PetriP;
import ua.stetsenkoinna.petriobj.PetriSim;
import ua.stetsenkoinna.petriobj.PetriT;
import ua.stetsenkoinna.petriobj.StateTime;

import java.util.ArrayList;
import javax.swing.JTextArea;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Саша
 */
public class AnimRunPetriSim extends PetriSim {

    private static final Logger log = LoggerFactory.getLogger(AnimRunPetriSim.class);

    private final JTextArea area; // specifies where simulation protokol is printed
    private final PetriNetsPanel panel;
    /**
     * How long each fired transition is held on screen, and what that length is measured in -
     * a fixed slice of real time per event, or a ratio of simulated time to real time. Asked
     * fresh after every step rather than read once at the start, so changing speed part-way
     * through a run takes effect on the very next event.
     */
    private final AnimationSpeedControl pace;

    /**
     * The model's clock as it stood after the previous step, so this one knows how much
     * simulated time it advanced - which is what a run paced by simulated time is paced by.
     */
    private double simTimeAtPreviousStep;
    private AnimRunPetriObjModel parentModel;

    /**
     * This object's own drawing — the same places and transitions the canvas holds for it,
     * filtered to just this one — so animation looks them up within it instead of across the
     * whole canvas. Every object's own net gets renumbered from zero independently right before
     * a run, so a bare {@code PetriT}/{@code PetriP} number is only unique inside this scope,
     * never across every object sharing the one canvas. {@code null} when there is no
     * Petri-object split to speak of (a plain net played back as a single simulator), in which
     * case the whole canvas already is the only scope there is.
     */
    private final GraphPetriNet scope;

    /**
     * Whether the simulation is paused (by pressing pause button)
     */
    private volatile boolean paused = false;

    /**
     * Whether the simulation should completely stop immediately
     */
    private volatile boolean halted = false;

    public AnimRunPetriSim(PetriNet net, StateTime timeState, JTextArea area, PetriNetsPanel panel,
                           AnimationSpeedControl pace, AnimRunPetriObjModel parentModel, GraphPetriNet scope) {
        super(net, timeState);
        this.panel = panel;
        this.area = area;
        this.pace = pace;
        this.simTimeAtPreviousStep = getCurrentTime();
        this.parentModel = parentModel;
        this.scope = scope;
    }

    /**
     * Constructs the Petri simulator with given Petri net and time modeling
     * Be carefull with this constructor. Time should be the same for all PetriSim objects in the list of PetriObjModel
     * @param net Petri net that describes the dynamics of object
     * @param area
     * @param panel
     * @param pace how fast the animation plays, and what it is paced by
     * @param parentModel AnimRunPetriObjModel that includes this object
     * @param scope this object's own graphical net, for correctly-scoped animation lookups
     */
   public AnimRunPetriSim(PetriNet net, JTextArea area, PetriNetsPanel panel, AnimationSpeedControl pace,
                          AnimRunPetriObjModel parentModel, GraphPetriNet scope) {
        this(net, new StateTime(), area, panel, pace, parentModel, scope);
   }

   public AnimRunPetriSim(String id, PetriNet net, JTextArea area, PetriNetsPanel panel,
                          AnimationSpeedControl pace, AnimRunPetriObjModel parentModel, GraphPetriNet scope) {
       this(net, new StateTime(), area, panel, pace, parentModel, scope);
       super.setId(id); // server set id
   }

    @Override
    protected void beforeActIn(PetriT tr) {
        panel.animateP(tr.getInP(), scope);
        panel.animateIn(tr, scope);
    }

    @Override
    protected void afterActIn(PetriT tr) {
        panel.animateT(tr, scope);
        doAfterStep();
    }

    @Override
    protected void beforeActOut(PetriT tr) {
        panel.animateT(tr, scope);
        panel.animateOut(tr, scope);
    }

    @Override
    protected void afterActOut(PetriT tr) {
        panel.animateP(tr.getOutP(), scope);
        doAfterStep();
    }

    @Override
    protected boolean shouldInterrupt() {
        return halted;
    }

    private void doAfterStep() {
        try {
            if (pace != null) {
                double now = getCurrentTime();
                // How far the clock moved over this step. Asked of the parent model when there
                // is one, because every object of a composed model shares that clock and the
                // advance is the model's, not any one object's - see advanceSince. A lone
                // object measures against its own last step, which is the same thing when it
                // is the only one stepping.
                double advanced;
                if (parentModel != null) {
                    advanced = parentModel.advanceSince(now);
                } else {
                    advanced = Math.max(0, now - simTimeAtPreviousStep);
                    simTimeAtPreviousStep = now;
                }
                long sleep = pace.sleepMillisAfterStep(advanced);
                if (sleep > 0) {
                    Thread.sleep(sleep);
                }
            }
            
            /* pausing/unpausing support */   
            if (parentModel != null) {
                if (parentModel.isPaused()) {
                    synchronized(parentModel) {
                        while (parentModel.isPaused()) {
                            try {
                                parentModel.wait();
                            } catch (InterruptedException e) {
                                /* the simulation should stop asap */
                                parentModel.halt();
                            }
                        }
                    }
                }
            } else {
                // there's no parent model
                if (paused){
                    synchronized(this) {
                        while (paused) {
                            try {
                                this.wait();
                            } catch (InterruptedException e) {
                                /* the simulation should stop asap */
                                halt();
                            }
                        }
                    }
                }
            }
        } catch (InterruptedException e) {
            log.error("Animation simulation interrupted", e);
        }
    }
   
    @Override
    public void step() //один крок,використовується для одного об'єкту мережа Петрі(наприклад, покрокова імітація мережі Петрі в графічному редакторі)
    {
        area.append("\n Next event, current time = " + getCurrentTime());

        this.printMark();//друкувати поточне маркування
        ArrayList<PetriT> activeT =  this.findActiveT();     //формування списку активних переходів
        for (PetriT T : activeT) {
            area.append("\nList of transitions with a fulfilled activation condition " + T.getName());
        }
        if ((activeT.isEmpty() && isBufferEmpty()) || getCurrentTime() >= getSimulationTime()) { //зупинка імітації за умови, що
            //не має переходів, які запускаються,
            // і не має фішок в переходах або вичерпаний час моделювання
            area.append("\n STOP, there are no active transitions / transitions with a fulfilled activation condition " + this.getName());
            timeMin = getSimulationTime();
            for (PetriP position : super.getNet().getListP()) {
                position.changeMean((timeMin - getCurrentTime()) / getSimulationTime());
            }

            for (PetriT transition : super.getNet().getListT()) {
                transition.changeMean((timeMin - getCurrentTime()) / getSimulationTime());
            }
            setTimeCurr(timeMin); //просування часу
        } else {
            while (!activeT.isEmpty()) { //вхід маркерів в переходи доки можливо
                area.append("\n Choosing a transition to activate " + this.doConflikt(activeT).getName());
                this.doConflikt(activeT).actIn(super.getNet().getListP(), getCurrentTime()); //розв'язання конфліктів
                doAfterStep();
                /* support for early termination of the simulation */
                if (halted) {
                    return;
                }
                activeT = this.findActiveT(); //оновлення списку активних переходів
            }
            area.append("\n Markers enter transitions:");
            this.printMark(area::append);//друкувати поточне маркування

            this.eventMin();//знайти найближчу подію та ії час

            for (PetriP position : super.getNet().getListP()) {
                position.changeMean((timeMin - getCurrentTime()) / getSimulationTime());
            }

            for (PetriT transition : super.getNet().getListT()) {
                transition.changeMean((timeMin - getCurrentTime()) / getSimulationTime());
            }
            setTimeCurr(timeMin);         //просування часу

            if (getCurrentTime() <= getSimulationTime()) {

                area.append("\n current time =" + getCurrentTime() + "   " + eventMin.getName());
                //Вихід маркерів
                eventMin.actOut(super.getNet().getListP(),super.getCurrentTime());//Вихід маркерів з переходу, що відповідає найближчому моменту часу
                doAfterStep();
                /* support for early termination of the simulation */
                if (halted) {
                    return;
                }
                area.append("\n Markers leave a transition " + eventMin.getName());
                this.printMark(area::append);//друкувати поточне маркування

                if (eventMin.getBuffer() > 0) {
                    boolean u = true;
                    while (u) {
                        eventMin.minEvent();
                        if (eventMin.getMinTime() == getCurrentTime()) {
                            eventMin.actOut(super.getNet().getListP(),super.getCurrentTime());
                            doAfterStep();
                            /* support for early termination of the simulation */
                            if (halted) {
                                return;
                            }
                        } else {
                            u = false;
                        }
                    }
                    area.append("\n Markers leave a transition buffer " + eventMin.getName());
                    this.printMark(area::append);//друкувати поточне маркування
                }

                for (PetriT transition : super.getNet().getListT()) {
                    //Вихід з усіх переходів, що час виходу маркерів == поточний момент час.
                    if (transition.getBuffer() > 0 && transition.getMinTime() == getCurrentTime()) {
                    	transition.actOut(super.getNet().getListP(),super.getCurrentTime());
                        //Вихід маркерів з переходу, що відповідає найближчому моменту часу
                    	doAfterStep();
                        /* support for early termination of the simulation */
                        if (halted) {
                            return;
                        }
                    	area.append("\n Markers leave a transition " + transition.getName());
                        this.printMark(area::append);//друкувати поточне маркування
                        if (transition.getBuffer() > 0) {
                            boolean u = true;
                            while (u) {
                                transition.minEvent();
                                if (transition.getMinTime() == getCurrentTime()) {
                                	transition.actOut(super.getNet().getListP(),super.getCurrentTime());
                                	doAfterStep();
                                        /* support for early termination of the simulation */
                                        if (halted) {
                                            return;
                                        }
                                    // this.printMark();//друкувати поточне маркування
                                } else {
                                    u = false;
                                }
                            }
                            area.append("\n Markers leave a transition buffer " + transition.getName());
                            this.printMark(area::append);//друкувати поточне маркування
                        }
                    }
                }
            }
        }
    }
 
    /**
     * Attaches this Petri-object to the model that drives it.
     *
     * <p>Every object of a composed model is drawn on a panel of its own, so the objects
     * have to exist before the model can be built; this closes the loop afterwards, which
     * is what makes pause and stop reach them.
     *
     * @param parentModel the model this object belongs to
     */
    public void setParentModel(AnimRunPetriObjModel parentModel) {
        this.parentModel = parentModel;
    }

    public void setPaused(boolean paused) {
        this.paused = paused;
    }
    
    public void halt() {
        this.halted = true;
        setPaused(false); // otherwise it doesn't halt and remains paused      
        synchronized(this) {
            this.notifyAll();
        }
    }
    
    public boolean isHalted() {
        return halted;
    }
}

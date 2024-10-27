/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package graphpresentation;

import PetriObj.PetriNet;
import PetriObj.PetriP;
import PetriObj.PetriSim;
import PetriObj.PetriT;
import PetriObj.StateTime;
import graphpresentation.statistic.dto.PetriElementStatisticDto;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import javax.swing.JSlider;
import javax.swing.JTextArea;

/**
 *
 * @author Саша
 */
public class AnimRunPetriSim extends PetriSim {
    private JTextArea area; // specifies where simulation protokol is printed
    private final PetriNetsPanel panel;
    private JSlider delaySlider = null;
    
    private final AnimRunPetriObjModel parentModel;
    
    /**
     * Whether the simulation is paused (by pressing pause button)
     */
    private volatile boolean paused = false;
    
    /**
     * Whether the simulation should completely stop immediately
     */
    private volatile boolean halted = false;
    
    public AnimRunPetriSim(PetriNet net, StateTime timeState, JTextArea area,PetriNetsPanel panel,  JSlider delaySlider, AnimRunPetriObjModel parentModel) {
        super(net, timeState);
        this.panel = panel;
        this.area = area;
        this.delaySlider = delaySlider;
        this.parentModel = parentModel;
    }
       
    /**
     * Constructs the Petri simulator with given Petri net and time modeling
     *
     * @param net Petri net that describes the dynamics of object
     * @param area 
     * @param panel
     * @param delaySlider
     * @param parentModel AnimRunPetriObjModel that includes this object
     */
   public AnimRunPetriSim(PetriNet net,  JTextArea area, PetriNetsPanel panel, JSlider delaySlider, AnimRunPetriObjModel parentModel) {
        this(net, new StateTime(), area, panel,delaySlider, parentModel);  // Be carefull with this constructor. Time should be the same for all PetriSim objects in the list of PetriObjModel
    }
   
    public AnimRunPetriSim(String id, PetriNet net, JTextArea area,PetriNetsPanel panel, JSlider delaySlider, AnimRunPetriObjModel parentModel) {
        this(net, new StateTime(),  area, panel,delaySlider, parentModel);
        
        super.setId(id); // server set id

    }
    
    @Override
    public void input() {//вхід маркерів в переходи Петрі-об'єкта

        ArrayList<PetriT> activeT = this.findActiveT();     //формування списку активних переходів

        if (activeT.isEmpty() && isBufferEmpty() == true) { //зупинка імітації за умови, що
            //не має переходів, які запускаються,
            timeMin = Double.MAX_VALUE;
            // eventMin = null;
        } else {
            while (activeT.size() > 0) { //запуск переходів доки можливо
                PetriT tr = this.doConflikt(activeT);
                panel.animateP(tr.getInP());
                panel.animateIn(tr);
                tr.actIn(super.getNet().getListP(), super.getCurrentTime()); //розв'язання конфліктів
                panel.animateT(tr);
                doAfterStep();
                /* support for early termination of the simulation */
                if (halted) {
                    return;
                }
                activeT = this.findActiveT(); //оновлення списку активних переходів
            }

            this.eventMin();//знайти найближчу подію та ії час
        }
    }
    
     
    private void doAfterStep() {
        try {
            if (delaySlider != null) {
                Thread.sleep(delaySlider.getValue());
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
            e.printStackTrace();
        }
    }
   
    @Override
    public void output() {
        if (super.getCurrentTime() <= super.getSimulationTime()) {
            panel.animateT(eventMin);
            panel.animateOut(eventMin);
            eventMin.actOut(super.getNet().getListP(),super.getCurrentTime());//здійснення події
            panel.animateP(eventMin.getOutP());
            doAfterStep();
            /* support for early termination of the simulation */
            if (halted) {
                return;
            }
            if (eventMin.getBuffer() > 0) {
                boolean u = true;
                while (u == true) {
                    eventMin.minEvent();
                    if (eventMin.getMinTime() == super.getCurrentTime()) {
                        panel.animateT(eventMin);
                        panel.animateOut(eventMin);
                        eventMin.actOut(super.getNet().getListP(),super.getCurrentTime());
                        panel.animateP(eventMin.getOutP());
                        doAfterStep();
                        /* support for early termination of the simulation */
                        if (halted) {
                            return;
                        }
                    } else {
                        u = false;
                    }
                }
            }
            for (PetriT transition : super.getNet().getListT()) { //ВАЖЛИВО!!Вихід з усіх переходів, що час виходу маркерів == поточний момент час.
                
                if (transition.getBuffer() > 0 && transition.getMinTime() == super.getCurrentTime()) {
                    panel.animateT(transition); // 24.07.2018
                    panel.animateOut(transition); // 24.07.2018
                    transition.actOut(super.getNet().getListP(),super.getCurrentTime());//Вихід маркерів з переходу, що відповідає найближчому моменту часу
                    panel.animateP(transition.getOutP()); // 24.07.2018
                    if (transition.getBuffer() > 0) {
                        boolean u = true;
                        while (u == true) {
                            transition.minEvent();
                            if (transition.getMinTime() == super.getCurrentTime()) {
                                panel.animateT(transition); // 24.07.2018
                                panel.animateOut(transition); // 24.07.2018
                                transition.actOut(super.getNet().getListP(),super.getCurrentTime());
                                panel.animateP(transition.getOutP()); // 24.07.2018
                            } else {
                                u = false;
                            }
                        }
                    }
                }
            }
        }
    }
    
    @Override // ttodo: check halted support
    public void step() //один крок,використовується для одного об'єкту мережа Петрі(наприклад, покрокова імітація мережі Петрі в графічному редакторі)
    {
        area.append("\n Next event, current time = " + getCurrentTime());

        this.printMark();//друкувати поточне маркування
        ArrayList<PetriT> activeT =  this.findActiveT();     //формування списку активних переходів
        for (PetriT T : activeT) {
            area.append("\nList of transitions with a fulfilled activation condition " + T.getName());
        }
        if ((activeT.isEmpty() && isBufferEmpty() == true) || getCurrentTime() >= getSimulationTime()) { //зупинка імітації за умови, що
            //не має переходів, які запускаються,
          //  stop = true;                              // і не має фішок в переходах або вичерпаний час моделювання
            area.append("\n STOP, there are no active transitions / transitions with a fulfilled activation condition " + this.getName());
            timeMin = getSimulationTime();
            for (PetriP position : super.getNet().getListP()) {
                position.changeMean((timeMin - getCurrentTime()) / getSimulationTime());
            }

            for (PetriT transition : super.getNet().getListT()) {
                transition.changeMean((timeMin - getCurrentTime()) / getSimulationTime());
            }

            setTimeCurr(timeMin);         //просування часу
        } else {

            while (activeT.size() > 0) {      //вхід маркерів в переходи доки можливо

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
            this.printMark(area);//друкувати поточне маркування

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
                this.printMark(area);//друкувати поточне маркування

                if (eventMin.getBuffer() > 0) {

                    boolean u = true;
                    while (u == true) {
                        eventMin.minEvent();
                        if (eventMin.getMinTime() == getCurrentTime()) {
                            // System.out.println("MinTime="+TEvent.getMinTime());
                           
                            eventMin.actOut(super.getNet().getListP(),super.getCurrentTime());
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
                    area.append("\n Markers leave a transition buffer " + eventMin.getName());
                    this.printMark(area);//друкувати поточне маркування
                }
                //Додано 6.08.2011!!!
                for (PetriT transition : super.getNet().getListT()) { //ВАЖЛИВО!!Вихід з усіх переходів, що час виходу маркерів == поточний момент час.
                    if (transition.getBuffer() > 0 && transition.getMinTime() == getCurrentTime()) {
                    	transition.actOut(super.getNet().getListP(),super.getCurrentTime());//Вихід маркерів з переходу, що відповідає найближчому моменту часу
                    	doAfterStep();
                        /* support for early termination of the simulation */
                        if (halted) {
                            return;
                        }
                    	area.append("\n Markers leave a transition " + transition.getName());
                        this.printMark(area);//друкувати поточне маркування
                        if (transition.getBuffer() > 0) {
                            boolean u = true;
                            while (u == true) {
                                transition.minEvent();
                                if (transition.getMinTime() == getCurrentTime()) {
                                    // System.out.println("MinTime="+TEvent.getMinTime());
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
                            this.printMark(area);//друкувати поточне маркування
                        }
                    }
                }
            }
        }
     
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


    public List<PetriElementStatisticDto> collectElementStatistic(List<String> statistisWatchList) {
        List<PetriElementStatisticDto> petriStat = new ArrayList<>();
        petriStat.addAll(Arrays.stream(getNet().getListP())
               .filter(petriP -> statistisWatchList.contains(petriP.getName()))
               .map(petriP -> new PetriElementStatisticDto(getCurrentTime(), petriP.getName(), petriP.getObservedMin(), petriP.getObservedMax(), petriP.getMean()))
               .collect(Collectors.toList()));
        petriStat.addAll(Arrays.stream(getNet().getListT())
                .filter(petriT -> statistisWatchList.contains(petriT.getName()))
                .map(petriT -> new PetriElementStatisticDto(getCurrentTime(), petriT.getName(), petriT.getObservedMin(), petriT.getObservedMax(), petriT.getMean()))
                .collect(Collectors.toList()));
        return petriStat;
    }
}

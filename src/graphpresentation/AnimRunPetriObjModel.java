/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package graphpresentation;

import PetriObj.PetriObjModel;
import PetriObj.PetriP;
import PetriObj.PetriSim;
import PetriObj.PetriT;
import PetriObj.StateTime;
import graphpresentation.statistic.StatisticMonitorDialog;
import graphpresentation.statistic.dto.PetriElementStatisticDto;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import javax.swing.JSlider;
import javax.swing.JTextArea;

/**
 * 
 * @author Inna
 */
public class AnimRunPetriObjModel extends PetriObjModel{  // added 07.2018
    
    private JTextArea area; // specifies where simulation protokol is printed
    private PetriNetsPanel panel;
    private JSlider delaySlider;
    private List<String> statWatchList;
    private StatisticMonitorDialog statMonitor;
    private ArrayList<AnimRunPetriSim> runlist = new ArrayList<>();
    
    /**
     * Whether the simulation is paused (by pressing pause button)
     */
    private volatile boolean paused = false;
    
    /**
     * Whether the simulation should completely stop immediately
     */
    private volatile boolean halted = false;
       
    public AnimRunPetriObjModel(ArrayList<PetriSim> list,
                                JTextArea area,
                                PetriNetsPanel panel,
                                JSlider delaySlider){
        super(list);
        this.area = area;
        this.panel = panel;
        this.delaySlider = delaySlider;
        StateTime s = new StateTime();
        for(PetriSim sim: list){
            runlist.add(new AnimRunPetriSim(sim.getNet(),s, area, panel,delaySlider, this)); // edit 25.07.2018
        }
        super.setTimeState(s); // edit 25.07.2018  It's very important for correct statistics but building of project get error...very strange
        super.setListObj(list); // edit 25.07.2018 May be it's not important 
        
    }
    
    
    
    
    
   
    @Override
    public void go(double timeModeling) { //виведення протоколу подій та результатів моделювання у об"єкт класу JTextArea
        area.setText(" Events protocol ");
        super.setSimulationTime(timeModeling);

        super.setCurrentTime(0.0);
        double min;
        super.getListObj().sort(PetriSim.getComparatorByPriority()); //виправлено 9.11.2015, 12.10.2017
        for (AnimRunPetriSim e : getRunlist()) {
            e.input();
            /* support for early termination of the simulation */
            if (isHalted()) {
                return;
            }
        }
        super.printMark(area);
        ArrayList<AnimRunPetriSim> conflictObj = new ArrayList<>();
        Random r = new Random();

        while ((super.getCurrentTime() < super.getSimulationTime())) {
            conflictObj.clear();

            min = Double.MAX_VALUE;  //пошук найближчої події

            for (AnimRunPetriSim e : getRunlist()) {
                if (e.getTimeMin() < min) {
                    min = e.getTimeMin();
                }
            }

            List<PetriElementStatisticDto> elementStatistics = new ArrayList<>();
            if (super.isStatistics() == true) {
                for (AnimRunPetriSim e : getRunlist()) {
                    if (min > 0) {
                        if (min < super.getSimulationTime()) {
                            e.doStatistics((min - super.getCurrentTime()) / min); //статистика за час "дельта т", для спільних позицій потрібно статистику збирати тільки один раз!!!
                        } else {
                            e.doStatistics((timeModeling - super.getCurrentTime()) / super.getSimulationTime());
                        }
                    }
                    elementStatistics.addAll(e.collectElementStatistic(statWatchList));
                }
            }
            if (!elementStatistics.isEmpty()) {
                System.out.println("SEND CURRENT TIME:"+getCurrentTime());
                statMonitor.sendStatistic(getCurrentTime(), elementStatistics);
            }

            super.setCurrentTime(min); // просування часу

            super.printInfo(" \n Time progress: time = " + super.getCurrentTime() + "\n", area);

            if (super.getCurrentTime() <= timeModeling) {

                for (AnimRunPetriSim e : getRunlist()) {
                    if (super.getCurrentTime() == e.getTimeMin()) { // розв'язання конфлікту об'єктів рівноймовірнісним способом

                        conflictObj.add(e);                           //список конфліктних обєктів
                    }
                }
                int num;
                int max;
                if (super.isProtocolPrint() == true) {
                    area.append("  List of conflicting objects  " + "\n");
                    for (int ii = 0; ii < conflictObj.size(); ii++) {
                        area.append("  K [ " + ii + "  ] = " + conflictObj.get(ii).getName() + "\n");
                    }
                }

                if (conflictObj.size() > 1) { //вибір обєкта, що запускається
                    max = conflictObj.size();
                    super.getListObj().sort(PetriSim.getComparatorByPriority());
                    for (int i = 1; i < conflictObj.size(); i++) { //System.out.println("  "+conflictObj.get(i).getPriority()+"  "+conflictObj.get(i-1).getPriority());
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

                super.printInfo(" Selected object  " + conflictObj.get(num).getName() + "\n" + " NextEvent " + "\n", area);

                for (AnimRunPetriSim list : getRunlist()) {
                    if (list.getNumObj() == conflictObj.get(num).getNumObj()) {
                        super.printInfo(" time =   " + super.getCurrentTime() + "   Event '" + list.getEventMin().getName() + "'\n" + "                       is occuring for the object   " + list.getName() + "\n", area);
                        list.doT();
                        list.output(); /* вихід маркерів з переходів */
                        /* support for early termination of the simulation */
                        if (isHalted()) {
                            return;
                        }
                    }
                }
                super.printInfo("Markers leave transitions:", area);
                super.printMark(area);
                
                /* pausing/unpausing support between input and output */
                /*if (paused) {
                    synchronized(this) {
                        while (paused) {
                            try {
                                wait();
                            } catch (InterruptedException e) {}
                        }
                    }
                } */
                /* end of pausing/unpausing support */
                
                super.getListObj().sort(PetriSim.getComparatorByPriority());
                for (AnimRunPetriSim e : getRunlist()) {
                        e.input(); //вхід маркерів в переходи Петрі-об'єкта 
                        /* support for early termination of the simulation */
                        if (isHalted()) {
                            return;
                        }
                }

                super.printInfo("Markers enter transitions:", area);
                super.printMark(area);
            }
        }
        
        
        displayModellingResults();
    }
    
    private void displayModellingResults() {
        area.append("\n Modeling results: \n");
        for (AnimRunPetriSim e : getRunlist()) {
            area.append("\n Petri-object " + e.getName());
            area.append("\n Mean values of the quantity of markers in places : ");
            for (PetriP P : e.getListPositionsForStatistica()) {
                area.append("\n  Place '" + P.getName() + "'  " + Double.toString(P.getMean()));
            }
            area.append("\n Mean values of the quantity of active transition channels : ");
            for (PetriT T : e.getNet().getListT()) {
                area.append("\n Transition '" + T.getName() + "'  " + Double.toString(T.getMean()));
            }
        }
    }

    /**
     * @return the runlist
     */
    public ArrayList<AnimRunPetriSim> getRunlist() {
        return runlist;
    }

    /**
     * @param runlist the runlist to set
     */
    public void setRunlist(ArrayList<AnimRunPetriSim> runlist) {
        this.runlist = runlist;
    }
    
     /**
     * Prints the string in given JTextArea object
     *
     * @param info string for printing
     * 
     */
    public void printInfo(String info){
        if(isProtocolPrint() == true)
            area.append(info);
    }
    /**
     * Prints the quantity for each position of Petri net
     ** 
     */
    public void printMark(){
        if (isProtocolPrint() == true) {
            for (AnimRunPetriSim e : getRunlist()) {
                e.printMark(area);
            }
        }
    }
    
    /**
     * Pause or unpause the simulation
     */
    public void setPaused(boolean isPaused) {
        this.paused = isPaused;
        for (AnimRunPetriSim petriObject: runlist) {
            petriObject.setPaused(isPaused);
        }
    }
    
    /**
     * @return Whether the animation is paused or not
     */
    public boolean isPaused() {
        return paused;
    }
    
    /**
     * Send a signal to the simulation that it should stop ASAP.
     * It wouldn't be possible to continue this simulation after that.
     */
    public void halt() {
        halted = true;
        setPaused(false); // otherwise it remains paused and doesn't terminate
        synchronized(this) {
            this.notifyAll();
        }
        for (AnimRunPetriSim petriObject: runlist) {
            petriObject.halt();
        }
    }
    
    /**
     * @return whether the simulation received the signal to halt 
     */
    public boolean isHalted() {
        return halted;
    }

    public void setStatMonitor(StatisticMonitorDialog statMonitor) {
        this.statMonitor = statMonitor;
    }

    public void setStatWatchList(List<String> statWatchList) {
        this.statWatchList = statWatchList;
    }
}

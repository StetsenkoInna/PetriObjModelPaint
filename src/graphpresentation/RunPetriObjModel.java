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
import graphpresentation.PetriNetsPanel;
import graphpresentation.statistic.StatisticMonitorDialog;
import graphpresentation.statistic.dto.PetriElementStatisticDto;

import java.util.*;
import java.util.stream.Collectors;
import javax.swing.JSlider;
import javax.swing.JTextArea;

/**
 *
 * @author Inna
 */
public class RunPetriObjModel extends PetriObjModel{
    
    private JTextArea area; // specifies where simulation protokol is printed
    private List<String> statWatchList;
    private StatisticMonitorDialog statMonitor;

    public RunPetriObjModel(ArrayList<PetriSim> list, JTextArea area){
        super(list);
        this.area = area;
     
    }
    
    
  /*  @Override
     public void go(double timeModeling) { //виведення протоколу подій та результатів моделювання у об"єкт класу JTextArea
        if(area==null){
            super.go(timeModeling);
            return;
        }
        area.setText(" Events protocol ");
        super.setSimulationTime(timeModeling);
       
        super.setCurrentTime(0.0);
        double min;
        super.getListObj().sort(PetriSim.getComparatorByPriority()); //виправлено 9.11.2015, 12.10.2017
        for (PetriSim e : getListObj()) { 
            e.input();
        }
        this.printMark();
        ArrayList<PetriSim> conflictObj = new ArrayList<>();
        Random r = new Random();

        while (super.getCurrentTime() < super.getSimulationTime()) {

            conflictObj.clear();

            min = Double.MAX_VALUE;  //пошук найближчої події

            for (PetriSim e : getListObj()) {
                if (e.getTimeMin() < min) {
                    min = e.getTimeMin();
                }
            }
            if (super.isStatistics() == true) {
                for (PetriSim e : getListObj()) {
                    if (min > 0) {
                        if(min<super.getSimulationTime())
                            e.doStatistics((min - super.getCurrentTime()) / min); //статистика за час "дельта т", для спільних позицій потрібно статистику збирати тільки один раз!!!
                        else
                            e.doStatistics((super.getSimulationTime() - super.getCurrentTime()) / super.getSimulationTime()); 
                    }
                }
            }

           super.setCurrentTime(min); // просування часу

          //PetriSim.setTimeCurr(t); // просування часу //3.12.2015
            
          
            this.printInfo(" \n Time progress: time = " + super.getCurrentTime() + "\n");
            
            if (super.getCurrentTime() <= super.getSimulationTime()) {

                for (PetriSim e : getListObj()) {
                    if (super.getCurrentTime() == e.getTimeMin()){ // розв'язання конфлікту об'єктів рівноймовірнісним способом
                    
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

                if (conflictObj.size() > 1) //вибір обєкта, що запускається
                {
                    max = conflictObj.size();
                   getListObj().sort(PetriSim.getComparatorByPriority());
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

               
                super.printInfo(" Selected object  " + conflictObj.get(num).getName() + "\n" + " NextEvent " + "\n",area);
                

                for (PetriSim list : getListObj()) {
                    if (list.getNumObj() == conflictObj.get(num).getNumObj()) {
                        super.printInfo(" time =   " + super.getCurrentTime() + "   Event '" + list.getEventMin().getName() + "'\n" + "                       is occuring for the object   " + list.getName() + "\n", area);
                        list.doT();
                        list.output();
                    }
                }
                printInfo("Markers leave transitions:");
                printMark();
                
                Collections.shuffle(getListObj()); // added by Inna 11.07.2018, need for correct functioning of Petri object's shared resource 
                
                getListObj().sort(PetriSim.getComparatorByPriority());
                
                for (PetriSim e : getListObj()) {
                   
                    e.input(); //вхід маркерів в переходи Петрі-об'єкта

                }
                
                printInfo("Markers enter transitions:");
                printMark();
            }
        }
        area.append("\n Modeling results: \n");

        for (PetriSim e : getListObj()) {
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
    }*/
    
    
    @Override
        public void go(double timeModeling) { //виведення протоколу подій та результатів моделювання у об"єкт класу JTextArea
        area.setText(" Events protocol ");
        super.setSimulationTime(timeModeling);

        super.setCurrentTime(0.0);
        double min;
        super.getListObj().sort(PetriSim.getComparatorByPriority()); //виправлено 9.11.2015, 12.10.2017
        for (PetriSim e : super.getListObj()) {
            e.input();
        }
        this.printMark();
        ArrayList<PetriSim> conflictObj = new ArrayList<>();
        Random r = new Random();

        while (super.getCurrentTime() < super.getSimulationTime()) {

            conflictObj.clear();

            min = Double.MAX_VALUE;  //пошук найближчої події

            for (PetriSim e : super.getListObj()) {
                if (e.getTimeMin() < min) {
                    min = e.getTimeMin();
                }
            }

            List<PetriElementStatisticDto> elementStatistics = new ArrayList<>();
            if (super.isStatistics() == true) {
                for (PetriSim e : super.getListObj()) {
                    if (min > 0) {
                        if (min < super.getSimulationTime()) {
                            e.doStatistics((min - super.getCurrentTime()) / min); //статистика за час "дельта т", для спільних позицій потрібно статистику збирати тільки один раз!!!
                        } else {
                            e.doStatistics((timeModeling - super.getCurrentTime()) / super.getSimulationTime());
                        }
                    }
                    elementStatistics.addAll(collectElementStatistic(e, statWatchList));
                }
            }
            if (!elementStatistics.isEmpty()) {
                System.out.println("SEND CURRENT TIME:"+getCurrentTime());
                statMonitor.sendStatistic(getCurrentTime(), elementStatistics);
            }

            super.setCurrentTime(min); // просування часу

            printInfo(" \n Time progress: time = " + super.getCurrentTime() + "\n");

            if (super.getCurrentTime() <= timeModeling) {

                for (PetriSim e : super.getListObj()) {
                    if (super.getCurrentTime() == e.getTimeMin()) { // розв'язання конфлікту об'єктів рівноймовірнісним способом

                        conflictObj.add(e);                           //список конфліктних обєктів
                    }
                }
                int num;
                int max;
                if (super.isProtocolPrint() == true) {
                    printInfo("  List of conflicting objects  " + "\n");
                    for (int ii = 0; ii < conflictObj.size(); ii++) {
                        printInfo("  K [ " + ii + "  ] = " + conflictObj.get(ii).getName() + "\n");
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

                printInfo(" Selected object  " + conflictObj.get(num).getName() + "\n" + " NextEvent " + "\n");

                for (PetriSim list : super.getListObj()) {
                    if (list.getNumObj() == conflictObj.get(num).getNumObj()) {
                        printInfo(" time =   " + super.getCurrentTime() + "   Event '" + list.getEventMin().getName() + "'\n" + "                       is occuring for the object   " + list.getName() + "\n");
                        list.doT();
                        list.output();
                    }
                }
                printInfo("Markers leave transitions:");
                this.printMark(area);
                Collections.shuffle(getListObj()); // added by Inna 11.07.2018, need for correct functioning of Petri object's shared resource 
                
                getListObj().sort(PetriSim.getComparatorByPriority());
               
                for (PetriSim e : super.getListObj()) {
                        e.input(); //вхід маркерів в переходи Петрі-об'єкта
                }

                printInfo("Markers enter transitions:");
                this.printMark();
            }
        }
        area.append("\n Modeling results: \n");

        for (PetriSim e : super.getListObj()) {
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
            for (PetriSim e : super.getListObj()) {
                e.printMark(area);
            }
        }
    }

    public void setStatMonitor(StatisticMonitorDialog statMonitor) {
        this.statMonitor = statMonitor;
    }

    public void setStatWatchList(List<String> statWatchList) {
        this.statWatchList = statWatchList;
    }

    public List<PetriElementStatisticDto> collectElementStatistic(PetriSim petriSim, List<String> statistisWatchList) {
        List<PetriElementStatisticDto> petriStat = new ArrayList<>();
        petriStat.addAll(Arrays.stream(petriSim.getNet().getListP())
                .filter(petriP -> statistisWatchList.contains(petriP.getName()))
                .map(petriP -> new PetriElementStatisticDto(getCurrentTime(), petriP.getName(), petriP.getObservedMin(), petriP.getObservedMax(), petriP.getMean()))
                .collect(Collectors.toList()));
        petriStat.addAll(Arrays.stream(petriSim.getNet().getListT())
                .filter(petriT -> statistisWatchList.contains(petriT.getName()))
                .map(petriT -> new PetriElementStatisticDto(getCurrentTime(), petriT.getName(), petriT.getObservedMin(), petriT.getObservedMax(), petriT.getMean()))
                .collect(Collectors.toList()));
        return petriStat;
    }
}

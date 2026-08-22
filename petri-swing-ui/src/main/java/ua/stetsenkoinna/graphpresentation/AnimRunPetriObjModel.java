package ua.stetsenkoinna.graphpresentation;

import ua.stetsenkoinna.petriobj.PetriObjModel;
import ua.stetsenkoinna.petriobj.PetriP;
import ua.stetsenkoinna.petriobj.PetriSim;
import ua.stetsenkoinna.petriobj.PetriT;
import ua.stetsenkoinna.petriobj.StateTime;
import ua.stetsenkoinna.api.dto.PetriElementStatisticDto;
import ua.stetsenkoinna.graphpresentation.statistic.dto.data.StatisticGraphMonitor;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import javax.swing.*;

/**
 *
 * @author Inna
 */
public class AnimRunPetriObjModel extends PetriObjModel{

    private final JTextArea area; // specifies where simulation protokol is printed
    private StatisticGraphMonitor statisticGraphMonitor;
    private ArrayList<AnimRunPetriSim> runlist = new ArrayList<>();

    /**
     * Whether the simulation is paused (by pressing pause button)
     */
    private volatile boolean paused = false;

    /**
     * True from {@link #stepOnce} until the in-flight event finishes and {@link #go}'s loop
     * re-arms {@link #paused} at the next event boundary. While true, the per-checkpoint waits
     * in {@link AnimRunPetriSim#doAfterStep} let the thread through without blocking — same as
     * a normal run — so the one event already in progress (or about to start) plays out fully
     * before the run holds again, rather than freezing wherever a checkpoint happens to be.
     */
    private volatile boolean stepping = false;

    /**
     * Whether the simulation should completely stop immediately
     */
    private volatile boolean halted = false;

    /**
     * The shared clock as it stood when any object of this model last finished a step.
     *
     * <p>Kept here rather than per object because the objects share one {@link StateTime}: were
     * each to remember the clock at its own last step, two objects acting at the same moment
     * would each measure the whole advance since they themselves last acted, and a run paced by
     * simulated time would sleep for it twice over - once per object that happened to be
     * involved. The advance belongs to the model, so it is measured once here.
     */
    private double simTimeAtPreviousStep;

    /**
     * @param now the shared clock as it stands after a step
     * @return how far it moved since the last step of any object of this model, never negative
     */
    synchronized double advanceSince(double now) {
        double advanced = now - simTimeAtPreviousStep;
        simTimeAtPreviousStep = now;
        return Math.max(0, advanced);
    }

    public AnimRunPetriObjModel(ArrayList<PetriSim> list,
                                JTextArea area,
                                PetriNetsPanel panel,
                                AnimationSpeedControl pace
    ){
        super(list);
        this.area = area;
        StateTime s = new StateTime();
        for(PetriSim sim: list){
            // No GraphPetriObject is available from a bare PetriSim, so there is no per-object
            // graphical net to scope animation lookups to; null falls back to the whole canvas.
            runlist.add(new AnimRunPetriSim(sim.getNet(), s, area, panel, pace, this, null));
        }
        super.setTimeState(s); // It's very important for correct statistics but building of project get error
        super.setListObj(list);
    }

    /**
     * Builds an animated model out of Petri-objects that are already bound to their views.
     *
     * <p>Every object of a composed model is drawn on a panel of its own, so the animated
     * simulators cannot be derived from the nets here — the caller creates them, each with
     * its own panel, and they become the model's object list directly.
     *
     * @param objects the animated Petri-objects, sharing one {@link StateTime}
     * @param area where the events protocol is printed
     */
    public AnimRunPetriObjModel(ArrayList<AnimRunPetriSim> objects, JTextArea area) {
        super(new ArrayList<>(objects));
        this.area = area;
        this.runlist = objects;
    }

    @Override
    public void go(double timeModeling) {
        // виведення протоколу подій та результатів моделювання у об"єкт класу JTextArea
        area.setText(" Events protocol ");
        super.setSimulationTime(timeModeling);

        super.setCurrentTime(0.0);
        double min;
        super.getListObj().sort(PetriSim.getComparatorByPriority());
        for (AnimRunPetriSim e : getRunlist()) {
            e.input();
            /* support for early termination of the simulation */
            if (isHalted()) {
                return;
            }
        }
        super.printMark(area::append);
        ArrayList<AnimRunPetriSim> conflictObj = new ArrayList<>();
        Random r = new Random();

        while ((super.getCurrentTime() < super.getSimulationTime())) {
            // Blocks here, before this iteration's event has changed anything, rather than
            // relying only on doAfterStep()'s scattered mid-event checkpoints. Without this,
            // re-arming `paused` at the bottom of the loop (below) was not enough on its own:
            // the next iteration's conflict resolution and clock advance already ran by the
            // time doAfterStep() got a chance to notice `paused`, so a step bled partway into
            // the following event instead of stopping cleanly before it. A regular Pause still
            // typically lands mid-event via doAfterStep() first, since that is checked far
            // more often than once per event; this is the backstop for the gap between them.
            synchronized (this) {
                while (paused) {
                    try {
                        wait();
                    } catch (InterruptedException e) {
                        halt();
                    }
                }
            }
            if (isHalted()) {
                return;
            }

            conflictObj.clear();

            min = Double.MAX_VALUE;  //пошук найближчої події

            for (AnimRunPetriSim e : getRunlist()) {
                if (e.getTimeMin() < min) {
                    min = e.getTimeMin();
                }
            }

            List<PetriElementStatisticDto> currentStatistic = new ArrayList<>();
            if (super.isStatistics()) {
                for (AnimRunPetriSim e : getRunlist()) {
                    if (min > 0) {
                        if (min < super.getSimulationTime()) {
                            // статистика за час "дельта т"
                            // для спільних позицій потрібно статистику збирати тільки один раз
                            e.doStatistics((min - super.getCurrentTime()) / min);
                        } else {
                            e.doStatistics((timeModeling - super.getCurrentTime()) / super.getSimulationTime());
                        }
                    }
                    if (isStatisticMonitorEnabled() && isStatisticCollectionTime()) {
                        currentStatistic.addAll(statisticGraphMonitor.getNetWatchListStatistic(e.getStatisticId(), e.getNet()));
                    }
                }
            }
            if (!currentStatistic.isEmpty()) {
                statisticGraphMonitor.setLastStatisticCollectionTime(getCurrentTime());
                statisticGraphMonitor.asyncStatisticSend(getCurrentTime(), currentStatistic);
            }

            super.setCurrentTime(min); // просування часу

            printInfo(" \n Time progress: time = " + super.getCurrentTime() + "\n");

            if (super.getCurrentTime() <= timeModeling) {

                for (AnimRunPetriSim e : getRunlist()) {
                    if (super.getCurrentTime() == e.getTimeMin()) {
                        // розв'язання конфлікту об'єктів рівноймовірнісним способом
                        conflictObj.add(e); //список конфліктних обєктів
                    }
                }
                int num;
                int max;
                if (super.isProtocolPrint()) {
                    area.append("  List of conflicting objects  " + "\n");
                    for (int ii = 0; ii < conflictObj.size(); ii++) {
                        area.append("  K [ " + ii + "  ] = " + conflictObj.get(ii).getName() + "\n");
                    }
                }

                if (conflictObj.size() > 1) { //вибір обєкта, що запускається
                    max = conflictObj.size();
                    super.getListObj().sort(PetriSim.getComparatorByPriority());
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

                printInfo(" Selected object  " + conflictObj.get(num).getName() + "\n" + " NextEvent " + "\n");

                for (AnimRunPetriSim list : getRunlist()) {
                    if (list.getNumObj() == conflictObj.get(num).getNumObj()) {
                        printInfo(" time =   " + super.getCurrentTime()
                                + "   Event '" + list.getEventMin().getName()
                                + "'\n" + "                       is occuring for the object   "
                                + list.getName() + "\n");
                        list.doT();
                        list.output(); /* вихід маркерів з переходів */
                        /* support for early termination of the simulation */
                        if (isHalted()) {
                            return;
                        }
                    }
                }
                printInfo("Markers leave transitions:");
                super.printMark(area::append);

                super.getListObj().sort(PetriSim.getComparatorByPriority());
                for (AnimRunPetriSim e : getRunlist()) {
                        e.input(); //вхід маркерів в переходи Петрі-об'єкта
                        /* support for early termination of the simulation */
                        if (isHalted()) {
                            return;
                        }
                }

                printInfo("Markers enter transitions:");
                super.printMark(area::append);
            }

            // One whole event has now finished. Re-arming the pause here rather than at the
            // top of the loop is what makes a step from a standing start work: stepping can
            // be set before go() is even entered, and the first event still plays out in full
            // before anything blocks, instead of freezing at its first checkpoint.
            if (stepping) {
                stepping = false;
                paused = true;
            }
        }

        if (isLastStatisticSegment()) {
            List<PetriElementStatisticDto> statistic = new ArrayList<>();
            for (PetriSim e : getListObj()) {
                statistic.addAll(statisticGraphMonitor.getNetWatchListStatistic(e.getStatisticId(), e.getNet()));
            }
            statisticGraphMonitor.asyncStatisticSend(getCurrentTime(), statistic);
        }
        if (isStatisticMonitorEnabled()) {
            statisticGraphMonitor.shutdownStatisticUpdate();
        }
        displayModellingResults();
    }

    private void displayModellingResults() {
        area.append("\n Modeling results: \n");
        for (AnimRunPetriSim e : getRunlist()) {
            area.append("\n Petri-object " + e.getName());
            area.append("\n Mean values of the quantity of markers in places : ");
            for (PetriP P : e.getListPositionsForStatistica()) {
                area.append("\n  Place '" + P.getName() + "'  " + P.getMean());
            }
            area.append("\n Mean values of the quantity of active transition channels : ");
            for (PetriT T : e.getNet().getListT()) {
                area.append("\n Transition '" + T.getName() + "'  " + T.getMean());
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
        if(isProtocolPrint())
            area.append(info);
    }
    /**
     * Prints the quantity for each position of Petri net
     **
     */
    public void printMark(){
        if (isProtocolPrint()) {
            for (AnimRunPetriSim e : getRunlist()) {
                e.printMark(area::append);
            }
        }
    }

    /**
     * Pause or unpause the simulation
     */
    public void setPaused(boolean isPaused) {
        if (!isPaused) {
            // An explicit resume outranks a step still playing out: without this, pressing
            // Start during that one event would be undone the moment the step re-paused.
            stepping = false;
        }
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
     * Lets the run advance by exactly one more full event, then re-pauses at the next event
     * boundary — for a "step forward" control. Works the same whether the run is currently
     * paused (already blocked mid-event in some {@code doAfterStep()} checkpoint) or actively
     * playing (running freely, no checkpoint blocking yet): either way {@code stepping} is
     * what actually re-arms the pause, in {@link #go}'s own loop, at the top of whichever event
     * comes next — so it always lands on a clean, whole event rather than an arbitrary
     * mid-event checkpoint.
     */
    public void stepOnce() {
        stepping = true;
        paused = false;
        synchronized (this) {
            notifyAll();
        }
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

    public void setStatisticMonitor(StatisticGraphMonitor statisticGraphMonitor) {
        this.statisticGraphMonitor = statisticGraphMonitor;
    }

    private boolean isStatisticMonitorEnabled() {
        return statisticGraphMonitor != null && statisticGraphMonitor.isValidMonitor();
    }

    private boolean isStatisticCollectionTime() {
        return isStatisticMonitorEnabled() && (getCurrentTime() >= statisticGraphMonitor.getDataCollectionStartTime());
    }

    private boolean isLastStatisticSegment() {
        return isStatisticMonitorEnabled() && (getCurrentTime() - getSimulationTime() <= getSimulationTime()) &&
                statisticGraphMonitor.getDataCollectionStartTime() <= getSimulationTime();
    }
}

package ua.stetsenkoinna.graphpresentation;

import javax.swing.*;
import ua.stetsenkoinna.graphnet.*;

/** Common simulation host for editable documents and detached read-only review windows. */
public abstract class SimulationFrame extends JFrame {
    public volatile AnimRunPetriObjModel animationModel;
    public volatile RunPetriObjModel runModel;
    public volatile boolean startAnimationStepping;
    public Thread animationThread;
    public Timer timer;
    private final GraphPetriNetBackupHolder simulationBackup = new GraphPetriNetBackupHolder();
    protected SimulationFrame() { super(); }
    protected SimulationFrame(String title) { super(title); }
    public final GraphPetriNetBackupHolder getSimulationBackup() { return simulationBackup; }
    public abstract PetriNetsPanel getPetriNetsPanel();
    public abstract void animateNet();
    public abstract void runNet();
    public abstract void disableInput();
    public abstract void enableInput();
    public abstract Integer getNumberOfRuns();
    public abstract void showRunProgress();
    public abstract void hideRunProgress();
    public GraphPetriObjModel getObjectModel() { return getPetriNetsPanel().getCanvasModel().toObjModel(); }
}

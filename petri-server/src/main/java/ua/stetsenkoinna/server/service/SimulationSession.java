package ua.stetsenkoinna.server.service;

import ua.stetsenkoinna.api.simulation.SimulationStatus;
import ua.stetsenkoinna.server.dto.ObjectModelResultDto;
import ua.stetsenkoinna.server.dto.SimulationResultDto;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public class SimulationSession {

    private final String id;
    private final AtomicReference<SimulationStatus> status =
            new AtomicReference<>(SimulationStatus.PENDING);
    private final AtomicBoolean stopRequested = new AtomicBoolean(false);
    private final AtomicBoolean pauseRequested = new AtomicBoolean(false);

    private volatile Thread thread;
    private volatile SimulationResultDto result;
    private volatile ObjectModelResultDto objectModelResult;

    public SimulationSession(String id) {
        this.id = id;
    }

    public String getId() { return id; }

    public SimulationStatus getStatus() { return status.get(); }

    public void setStatus(SimulationStatus s) { status.set(s); }

    public boolean isStopRequested() { return stopRequested.get(); }

    public boolean isPauseRequested() { return pauseRequested.get(); }

    public void requestStop() { stopRequested.set(true); pauseRequested.set(false); }

    public void requestPause() { pauseRequested.set(true); }

    public void requestResume() { pauseRequested.set(false); }

    public Thread getThread() { return thread; }

    public void setThread(Thread thread) { this.thread = thread; }

    public SimulationResultDto getResult() { return result; }

    public void setResult(SimulationResultDto result) { this.result = result; }

    /** Per-object statistics of a Petri-object model run; null until the run finishes. */
    public ObjectModelResultDto getObjectModelResult() { return objectModelResult; }

    public void setObjectModelResult(ObjectModelResultDto objectModelResult) {
        this.objectModelResult = objectModelResult;
    }
}

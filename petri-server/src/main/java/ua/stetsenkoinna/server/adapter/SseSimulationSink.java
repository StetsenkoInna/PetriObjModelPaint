package ua.stetsenkoinna.server.adapter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ua.stetsenkoinna.PetriObj.PetriNet;
import ua.stetsenkoinna.PetriObj.PetriP;
import ua.stetsenkoinna.PetriObj.PetriSim;
import ua.stetsenkoinna.PetriObj.PetriT;
import ua.stetsenkoinna.PetriObj.SimulationStatisticCollector;
import ua.stetsenkoinna.api.simulation.SimulationStatus;
import ua.stetsenkoinna.server.service.SimulationSession;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.BlockingQueue;

/**
 * SimulationStatisticCollector that emits snapshots to a BlockingQueue for SSE streaming.
 *
 * Supports two snapshot modes:
 * - Time-based (snapshotInterval == null): one snapshot every timeStep simulation units.
 * - Step-based (snapshotInterval != null): one snapshot every snapshotInterval transition firings.
 *
 * The queue consumer (SseSimulationService writer thread) reads frames and sends them
 * as SSE events. An empty Optional signals end-of-stream.
 */
public class SseSimulationSink implements SimulationStatisticCollector {

    private static final Logger log = LoggerFactory.getLogger(SseSimulationSink.class);

    private final BlockingQueue<Optional<SimulationFrame>> queue;
    private final SimulationSession session;
    private final double timeStep;
    private final Integer snapshotInterval;
    private final double simulationTime;

    private final Map<String, Integer> currentMarkings = new LinkedHashMap<>();
    private final Map<String, Integer> currentBuffers = new LinkedHashMap<>();

    private double nextSnapshotAt;
    private int stepCount = 0;

    public SseSimulationSink(BlockingQueue<Optional<SimulationFrame>> queue,
                              SimulationSession session,
                              double timeStep,
                              Integer snapshotInterval,
                              double simulationTime) {
        this.queue = queue;
        this.session = session;
        this.timeStep = timeStep;
        this.snapshotInterval = snapshotInterval;
        this.simulationTime = simulationTime;
        this.nextSnapshotAt = timeStep;
    }

    @Override
    public boolean shouldCollect(double currentTime) {
        while (session.isPauseRequested() && !session.isStopRequested()) {
            try {
                Thread.sleep(20);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new SimulationInterruptedException();
            }
        }
        if (session.isStopRequested()) {
            throw new SimulationInterruptedException();
        }
        return true;
    }

    @Override
    public void onTimeStep(double currentTime, PetriNet net, int petriObjId) {
        for (PetriP p : net.getListP()) {
            currentMarkings.put(p.getId(), p.getMark());
        }
        for (PetriT t : net.getListT()) {
            currentBuffers.put(t.getId(), t.getBuffer());
        }
    }

    @Override
    public void flush(double currentTime) {
        stepCount++;

        boolean shouldEmit;
        if (snapshotInterval != null) {
            shouldEmit = stepCount % snapshotInterval == 0;
        } else {
            shouldEmit = currentTime >= nextSnapshotAt;
            if (shouldEmit) {
                nextSnapshotAt = (Math.floor(currentTime / timeStep) + 1) * timeStep;
            }
        }

        if (shouldEmit && !currentMarkings.isEmpty()) {
            enqueue(new SimulationFrame(
                    currentTime,
                    stepCount,
                    Map.copyOf(currentMarkings),
                    Map.copyOf(currentBuffers),
                    Math.min(currentTime / simulationTime, 1.0)
            ));
        }

        currentMarkings.clear();
        currentBuffers.clear();
    }

    @Override
    public void onSimulationEnd(double simulationEndTime, Iterable<PetriSim> objects) {
        Map<String, Integer> finalMarkings = new LinkedHashMap<>();
        Map<String, Integer> finalBuffers = new LinkedHashMap<>();
        for (PetriSim sim : objects) {
            for (PetriP p : sim.getNet().getListP()) {
                finalMarkings.put(p.getId(), p.getMark());
            }
            for (PetriT t : sim.getNet().getListT()) {
                finalBuffers.put(t.getId(), t.getBuffer());
            }
        }
        enqueue(new SimulationFrame(simulationEndTime, stepCount, finalMarkings, finalBuffers, 1.0));
    }

    @Override
    public void shutdown() {
        session.setStatus(SimulationStatus.FINISHED);
        queue.offer(Optional.empty());
    }

    private void enqueue(SimulationFrame frame) {
        if (!queue.offer(Optional.of(frame))) {
            log.warn("SSE queue full — dropping frame at t={}", frame.currentTime());
        }
    }
}

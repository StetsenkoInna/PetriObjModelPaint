package ua.stetsenkoinna.server.adapter;

import ua.stetsenkoinna.petriobj.PetriSim;
import ua.stetsenkoinna.server.service.SimulationSession;

import java.util.Optional;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Streaming sink of the Petri-object model API.
 *
 * <p>The snapshots are the same as for a plain net — markings and buffers keyed by element
 * id, which stays unique across the objects of a model — so a client that already renders
 * v1 frames keeps working. What this adds is the per-object breakdown of the final
 * statistics, which the {@code /result} endpoint of the v2 API returns.
 */
public class SseObjectModelSink extends SseSimulationSink {

    public SseObjectModelSink(BlockingQueue<Optional<SimulationFrame>> queue,
                              SimulationSession session,
                              double timeStep,
                              Integer snapshotInterval,
                              double simulationTime,
                              AtomicInteger inFlightPhases) {
        super(queue, session, timeStep, snapshotInterval, simulationTime, inFlightPhases);
    }

    @Override
    public void onSimulationEnd(double simulationEndTime, Iterable<PetriSim> objects) {
        super.onSimulationEnd(simulationEndTime, objects);
        session.setObjectModelResult(
                ObjectModelResults.of(simulationTime, simulationEndTime, stepCount, objects));
    }
}

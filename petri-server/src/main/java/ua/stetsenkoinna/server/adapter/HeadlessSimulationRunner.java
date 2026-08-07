package ua.stetsenkoinna.server.adapter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ua.stetsenkoinna.petriobj.PetriObjModel;
import ua.stetsenkoinna.petriobj.SimulationStatisticCollector;
import ua.stetsenkoinna.api.simulation.SimulationRequest;
import ua.stetsenkoinna.api.simulation.SimulationStatus;
import ua.stetsenkoinna.server.service.SimulationSession;
import ua.stetsenkoinna.server.service.WebSocketStatisticSink;

/**
 * Runs one simulation session to completion on its own thread, reporting through the given
 * collector. How the document becomes a model is left to a {@link ModelBuilder}, so the
 * same runner serves both the plain-net and the Petri-object model API.
 */
public class HeadlessSimulationRunner implements Runnable {

    private static final Logger log = LoggerFactory.getLogger(HeadlessSimulationRunner.class);

    private final ModelBuilder modelBuilder;
    private final double simulationTime;
    private final SimulationSession session;
    private final SimulationStatisticCollector collector;

    /**
     * Runs a plain single-net simulation.
     */
    public HeadlessSimulationRunner(SimulationRequest request,
                                    SimulationSession session,
                                    WebSocketStatisticSink sink) {
        this((sessionId, statistics) -> SimulationModelFactory.build(sessionId, request.getNetXml(), statistics),
                request.getSimulationTime(), session, sink);
    }

    /**
     * @param modelBuilder turns the request document into a model
     * @param simulationTime how long to simulate, in model time units
     * @param session the session this run belongs to
     * @param collector where statistics and control checks go
     */
    public HeadlessSimulationRunner(ModelBuilder modelBuilder,
                                    double simulationTime,
                                    SimulationSession session,
                                    SimulationStatisticCollector collector) {
        this.modelBuilder = modelBuilder;
        this.simulationTime = simulationTime;
        this.session = session;
        this.collector = collector;
    }

    @Override
    public void run() {
        session.setStatus(SimulationStatus.RUNNING);
        try {
            PetriObjModel model = modelBuilder.build(session.getId(), collector);
            model.go(simulationTime);

        } catch (SimulationInterruptedException e) {
            log.info("Simulation {} stopped by request", session.getId());
            session.setStatus(SimulationStatus.HALTED);
        } catch (Exception e) {
            log.error("Simulation {} failed", session.getId(), e);
            session.setStatus(SimulationStatus.HALTED);
        }
    }
}

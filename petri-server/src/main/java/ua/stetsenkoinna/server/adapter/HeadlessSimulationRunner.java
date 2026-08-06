package ua.stetsenkoinna.server.adapter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ua.stetsenkoinna.petriobj.PetriObjModel;
import ua.stetsenkoinna.api.simulation.SimulationRequest;
import ua.stetsenkoinna.api.simulation.SimulationStatus;
import ua.stetsenkoinna.server.service.SimulationSession;
import ua.stetsenkoinna.server.service.WebSocketStatisticSink;

public class HeadlessSimulationRunner implements Runnable {

    private static final Logger log = LoggerFactory.getLogger(HeadlessSimulationRunner.class);

    private final SimulationRequest request;
    private final SimulationSession session;
    private final WebSocketStatisticSink sink;

    public HeadlessSimulationRunner(SimulationRequest request,
                                    SimulationSession session,
                                    WebSocketStatisticSink sink) {
        this.request = request;
        this.session = session;
        this.sink = sink;
    }

    @Override
    public void run() {
        session.setStatus(SimulationStatus.RUNNING);
        try {
            PetriObjModel model = SimulationModelFactory.build(session.getId(), request.getNetXml(), sink);
            model.go(request.getSimulationTime());

        } catch (SimulationInterruptedException e) {
            log.info("Simulation {} stopped by request", session.getId());
            session.setStatus(SimulationStatus.HALTED);
        } catch (Exception e) {
            log.error("Simulation {} failed", session.getId(), e);
            session.setStatus(SimulationStatus.HALTED);
        }
    }
}

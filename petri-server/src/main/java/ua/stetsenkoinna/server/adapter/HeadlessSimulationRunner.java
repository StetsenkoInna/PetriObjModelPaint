package ua.stetsenkoinna.server.adapter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ua.stetsenkoinna.PetriObj.PetriNet;
import ua.stetsenkoinna.PetriObj.PetriObjModel;
import ua.stetsenkoinna.PetriObj.PetriSim;
import ua.stetsenkoinna.api.simulation.SimulationRequest;
import ua.stetsenkoinna.api.simulation.SimulationStatus;
import ua.stetsenkoinna.pnml.PnmlParser;
import ua.stetsenkoinna.server.service.SimulationSession;
import ua.stetsenkoinna.server.service.WebSocketStatisticSink;

import java.util.ArrayList;

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
            PetriNet net = new PnmlParser().parseXml(request.getNetXml());
            PetriSim sim = new PetriSim(net);

            ArrayList<PetriSim> objects = new ArrayList<>();
            objects.add(sim);

            PetriObjModel model = new PetriObjModel(session.getId(), objects);
            model.setIsProtokol(false);
            model.setIsStatistics(true);
            model.setStatisticCollector(sink);

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

package ua.stetsenkoinna.server.service;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import ua.stetsenkoinna.petriobj.PetriSim;
import ua.stetsenkoinna.server.adapter.ObjectModelResults;
import ua.stetsenkoinna.server.controller.ApiVersions;

/**
 * WebSocket sink of the Petri-object model API.
 *
 * <p>Publishes to the v2 topics and, on top of what the plain-net sink records, stores the
 * final statistics grouped per Petri-object.
 */
public class WebSocketObjectModelSink extends WebSocketStatisticSink {

    private final double simulationTime;

    /**
     * @param simulationTime the run's configured time span, reported back with the result
     */
    public WebSocketObjectModelSink(SimulationSession session, SimpMessagingTemplate messaging,
                                    double simulationTime) {
        super(session, messaging, ApiVersions.WS_V2);
        this.simulationTime = simulationTime;
    }

    @Override
    public void onSimulationEnd(double simulationEndTime, Iterable<PetriSim> objects) {
        super.onSimulationEnd(simulationEndTime, objects);
        session.setObjectModelResult(
                ObjectModelResults.of(simulationTime, simulationEndTime, 0, objects));
    }
}

package ua.stetsenkoinna.server.service;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import ua.stetsenkoinna.api.simulation.SimulationStatus;
import ua.stetsenkoinna.server.adapter.HeadlessSimulationRunner;
import ua.stetsenkoinna.server.adapter.PetriObjModelFactory;
import ua.stetsenkoinna.server.adapter.SimulationStatusMessage;
import ua.stetsenkoinna.server.controller.ApiVersions;
import ua.stetsenkoinna.server.controller.WsDestinations;

/**
 * Runs Petri-object model simulations over WebSocket.
 *
 * <p>Sessions, threading and control flow are the ones the plain-net service uses; what
 * differs is that the document is read as a composed model and that steps and status go to
 * the v2 topics, so v1 and v2 clients never see each other's traffic.
 */
@Service
public class ObjectModelSimulationService {

    private final SimulationSessionRegistry registry;
    private final SimpMessagingTemplate messaging;

    public ObjectModelSimulationService(SimulationSessionRegistry registry, SimpMessagingTemplate messaging) {
        this.registry = registry;
        this.messaging = messaging;
    }

    /**
     * Starts a run on a virtual thread.
     *
     * @param modelXml PNML document describing one or several Petri-objects
     * @param simulationTime how long to simulate, in model time units
     * @return the id of the created session
     */
    public String start(String modelXml, double simulationTime) {
        SimulationSession session = registry.create();
        WebSocketObjectModelSink sink = new WebSocketObjectModelSink(session, messaging, simulationTime);
        HeadlessSimulationRunner runner = new HeadlessSimulationRunner(
                (sessionId, collector) -> PetriObjModelFactory.build(sessionId, modelXml, collector),
                simulationTime, session, sink);

        Thread thread = Thread.ofVirtual()
                .name("sim-obj-" + session.getId())
                .start(runner);
        session.setThread(thread);

        return session.getId();
    }

    public void pause(String sessionId) {
        SimulationSession session = registry.get(sessionId);
        if (session == null || session.getStatus() != SimulationStatus.RUNNING) {
            return;
        }
        session.requestPause();
        session.setStatus(SimulationStatus.PAUSED);
        publishStatus(sessionId, SimulationStatus.PAUSED);
    }

    public void resume(String sessionId) {
        SimulationSession session = registry.get(sessionId);
        if (session == null || session.getStatus() != SimulationStatus.PAUSED) {
            return;
        }
        session.requestResume();
        session.setStatus(SimulationStatus.RUNNING);
        publishStatus(sessionId, SimulationStatus.RUNNING);
    }

    public void stop(String sessionId) {
        SimulationSession session = registry.get(sessionId);
        if (session == null) {
            return;
        }
        session.requestStop();
    }

    public SimulationStatus getStatus(String sessionId) {
        SimulationSession session = registry.get(sessionId);
        return session != null ? session.getStatus() : SimulationStatus.NOT_FOUND;
    }

    private void publishStatus(String sessionId, SimulationStatus status) {
        messaging.convertAndSend(
                WsDestinations.status(ApiVersions.WS_V2, sessionId),
                new SimulationStatusMessage(sessionId, status)
        );
    }
}

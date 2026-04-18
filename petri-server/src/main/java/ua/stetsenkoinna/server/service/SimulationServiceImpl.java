package ua.stetsenkoinna.server.service;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import ua.stetsenkoinna.api.simulation.SimulationRequest;
import ua.stetsenkoinna.api.simulation.SimulationService;
import ua.stetsenkoinna.api.simulation.SimulationStatus;
import ua.stetsenkoinna.server.adapter.HeadlessSimulationRunner;
import ua.stetsenkoinna.server.adapter.SimulationStatusMessage;
import ua.stetsenkoinna.server.controller.ApiVersions;

@Service
public class SimulationServiceImpl implements SimulationService {

    private final SimulationSessionRegistry registry;
    private final SimpMessagingTemplate messaging;

    public SimulationServiceImpl(SimulationSessionRegistry registry, SimpMessagingTemplate messaging) {
        this.registry = registry;
        this.messaging = messaging;
    }

    @Override
    public String startSimulation(SimulationRequest request) {
        SimulationSession session = registry.create();
        WebSocketStatisticSink sink = new WebSocketStatisticSink(session, messaging);
        HeadlessSimulationRunner runner = new HeadlessSimulationRunner(request, session, sink);

        Thread thread = Thread.ofVirtual()
                .name("sim-" + session.getId())
                .start(runner);
        session.setThread(thread);

        return session.getId();
    }

    @Override
    public void pause(String sessionId) {
        SimulationSession session = registry.get(sessionId);
        if (session == null || session.getStatus() != SimulationStatus.RUNNING) return;
        session.requestPause();
        session.setStatus(SimulationStatus.PAUSED);
        messaging.convertAndSend(
                "/topic" + ApiVersions.WS_V1 + "/sim/" + sessionId + "/status",
                new SimulationStatusMessage(sessionId, SimulationStatus.PAUSED)
        );
    }

    @Override
    public void resume(String sessionId) {
        SimulationSession session = registry.get(sessionId);
        if (session == null || session.getStatus() != SimulationStatus.PAUSED) return;
        session.requestResume();
        session.setStatus(SimulationStatus.RUNNING);
        messaging.convertAndSend(
                "/topic" + ApiVersions.WS_V1 + "/sim/" + sessionId + "/status",
                new SimulationStatusMessage(sessionId, SimulationStatus.RUNNING)
        );
    }

    @Override
    public void stop(String sessionId) {
        SimulationSession session = registry.get(sessionId);
        if (session == null) return;
        session.requestStop();
    }

    @Override
    public SimulationStatus getStatus(String sessionId) {
        SimulationSession session = registry.get(sessionId);
        return session != null ? session.getStatus() : SimulationStatus.NOT_FOUND;
    }
}

package ua.stetsenkoinna.server.service;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import ua.stetsenkoinna.PetriObj.PetriNet;
import ua.stetsenkoinna.PetriObj.PetriP;
import ua.stetsenkoinna.PetriObj.PetriSim;
import ua.stetsenkoinna.PetriObj.PetriT;
import ua.stetsenkoinna.PetriObj.SimulationStatisticCollector;
import ua.stetsenkoinna.api.dto.PetriElementStatisticDto;
import ua.stetsenkoinna.api.simulation.SimulationStatus;
import ua.stetsenkoinna.server.adapter.SimulationInterruptedException;
import ua.stetsenkoinna.server.adapter.SimulationStepMessage;
import ua.stetsenkoinna.server.adapter.SimulationStatusMessage;
import ua.stetsenkoinna.server.controller.ApiVersions;

import java.util.ArrayList;
import java.util.List;

public class WebSocketStatisticSink implements SimulationStatisticCollector {

    private final SimulationSession session;
    private final SimpMessagingTemplate messaging;
    private final List<PetriElementStatisticDto> buffer = new ArrayList<>();

    private final String stepsTopic;
    private final String statusTopic;

    public WebSocketStatisticSink(SimulationSession session, SimpMessagingTemplate messaging) {
        this.session = session;
        this.messaging = messaging;
        this.stepsTopic  = "/topic" + ApiVersions.WS_V1 + "/sim/" + session.getId() + "/steps";
        this.statusTopic = "/topic" + ApiVersions.WS_V1 + "/sim/" + session.getId() + "/status";
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
        for (PetriP place : net.getListP()) {
            buffer.add(new PetriElementStatisticDto(
                    petriObjId,
                    place.getName(),
                    place.getObservedMin(),
                    place.getObservedMax(),
                    place.getMean()
            ));
        }
        for (PetriT transition : net.getListT()) {
            buffer.add(new PetriElementStatisticDto(
                    petriObjId,
                    transition.getName(),
                    transition.getObservedMin(),
                    transition.getObservedMax(),
                    transition.getMean()
            ));
        }
    }

    @Override
    public void flush(double currentTime) {
        if (buffer.isEmpty()) return;
        messaging.convertAndSend(
                stepsTopic,
                new SimulationStepMessage(session.getId(), currentTime, List.copyOf(buffer))
        );
        buffer.clear();
    }

    @Override
    public void onSimulationEnd(double simulationEndTime, Iterable<PetriSim> objects) {
        List<PetriElementStatisticDto> finalStats = new ArrayList<>();
        for (PetriSim sim : objects) {
            for (PetriP place : sim.getNet().getListP()) {
                finalStats.add(new PetriElementStatisticDto(
                        sim.getNumObj(),
                        place.getName(),
                        place.getObservedMin(),
                        place.getObservedMax(),
                        place.getMean()
                ));
            }
        }
        messaging.convertAndSend(
                stepsTopic,
                new SimulationStepMessage(session.getId(), simulationEndTime, finalStats)
        );
    }

    @Override
    public void shutdown() {
        session.setStatus(SimulationStatus.FINISHED);
        messaging.convertAndSend(
                statusTopic,
                new SimulationStatusMessage(session.getId(), SimulationStatus.FINISHED)
        );
    }
}

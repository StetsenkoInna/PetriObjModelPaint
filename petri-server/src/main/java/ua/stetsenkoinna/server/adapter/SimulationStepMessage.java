package ua.stetsenkoinna.server.adapter;

import ua.stetsenkoinna.api.dto.PetriElementStatisticDto;

import java.util.List;

public class SimulationStepMessage {

    private final String sessionId;
    private final double currentTime;
    private final List<PetriElementStatisticDto> statistics;

    public SimulationStepMessage(String sessionId, double currentTime, List<PetriElementStatisticDto> statistics) {
        this.sessionId = sessionId;
        this.currentTime = currentTime;
        this.statistics = statistics;
    }

    public String getSessionId() { return sessionId; }
    public double getCurrentTime() { return currentTime; }
    public List<PetriElementStatisticDto> getStatistics() { return statistics; }
}

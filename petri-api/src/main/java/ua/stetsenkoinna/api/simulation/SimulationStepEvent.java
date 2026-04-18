package ua.stetsenkoinna.api.simulation;

import ua.stetsenkoinna.api.dto.PetriElementStatisticDto;

import java.util.List;

public class SimulationStepEvent {
    private final String sessionId;
    private final double currentTime;
    private final List<PetriElementStatisticDto> statistics;

    public SimulationStepEvent(String sessionId, double currentTime, List<PetriElementStatisticDto> statistics) {
        this.sessionId = sessionId;
        this.currentTime = currentTime;
        this.statistics = statistics;
    }

    public String getSessionId() { return sessionId; }
    public double getCurrentTime() { return currentTime; }
    public List<PetriElementStatisticDto> getStatistics() { return statistics; }
}

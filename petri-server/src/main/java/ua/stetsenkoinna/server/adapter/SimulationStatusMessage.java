package ua.stetsenkoinna.server.adapter;

import ua.stetsenkoinna.api.simulation.SimulationStatus;

public class SimulationStatusMessage {

    private final String sessionId;
    private final SimulationStatus status;

    public SimulationStatusMessage(String sessionId, SimulationStatus status) {
        this.sessionId = sessionId;
        this.status = status;
    }

    public String getSessionId() { return sessionId; }
    public SimulationStatus getStatus() { return status; }
}

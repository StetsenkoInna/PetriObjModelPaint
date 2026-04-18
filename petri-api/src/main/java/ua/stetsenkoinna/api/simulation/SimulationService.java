package ua.stetsenkoinna.api.simulation;

public interface SimulationService {
    String startSimulation(SimulationRequest request);
    void pause(String sessionId);
    void resume(String sessionId);
    void stop(String sessionId);
    SimulationStatus getStatus(String sessionId);
}

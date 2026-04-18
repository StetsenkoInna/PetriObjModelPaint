package ua.stetsenkoinna.api.simulation;

public class SimulationRequest {
    private final String netXml;
    private final double simulationTime;
    private final int numberOfRuns;

    public SimulationRequest(String netXml, double simulationTime, int numberOfRuns) {
        this.netXml = netXml;
        this.simulationTime = simulationTime;
        this.numberOfRuns = numberOfRuns;
    }

    public String getNetXml() { return netXml; }
    public double getSimulationTime() { return simulationTime; }
    public int getNumberOfRuns() { return numberOfRuns; }
}

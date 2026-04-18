package ua.stetsenkoinna.server.adapter;

public class SimulationInterruptedException extends RuntimeException {
    public SimulationInterruptedException() {
        super("Simulation was stopped");
    }
}

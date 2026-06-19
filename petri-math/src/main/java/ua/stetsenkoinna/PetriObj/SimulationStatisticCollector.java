package ua.stetsenkoinna.PetriObj;

/**
 * Abstraction for statistics collection during simulation.
 * Implemented by UI monitors (console/chart) and server-side sinks.
 * PetriObjModel depends only on this interface — no DTO imports needed.
 */
public interface SimulationStatisticCollector {

    /**
     * Returns true if statistics should be collected at the given simulation time.
     * Implementations check monitoring flags, collection intervals, etc.
     */
    boolean shouldCollect(double currentTime);

    /**
     * Called for each Petri-object once per time step (when shouldCollect() is true).
     * The collector maps net element values to its internal DTO format.
     */
    void onTimeStep(double currentTime, PetriNet net, int petriObjId);

    /**
     * Flush accumulated time-step data — called once after all objects are processed
     * in the same time step.
     */
    void flush(double currentTime);

    /**
     * Called at the end of simulation for final segment statistics.
     */
    void onSimulationEnd(double simulationEndTime, Iterable<PetriSim> objects);

    /**
     * Shutdown any background workers (thread pools, etc.).
     */
    void shutdown();
}

package ua.stetsenkoinna.petriobj;

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
     * Called once per atomic firing phase, on the simulation thread, in the order the phases
     * occur — between the {@link #onTimeStep} calls of two time steps rather than instead of
     * them. Collectors that only report statistics ignore it; a streaming one turns the phases
     * into the animation steps a client replays between two snapshots.
     *
     * <p>Default-implemented on purpose: a firing phase is finer-grained than anything this
     * interface promised before, and a collector that has no use for one should not have to
     * say so.
     *
     * @param time model current time at the instant the phase was recorded
     * @param phase which instant of the firing this is
     * @param transition the transition being fired
     * @param objects the whole model's object list — read markings and buffers
     *        <em>synchronously</em>, the arrays are mutated by the next firing
     */
    default void onFiringPhase(double time, FiringPhase phase, PetriT transition,
                               Iterable<PetriSim> objects) {}

    /**
     * Called at the end of simulation for final segment statistics.
     */
    void onSimulationEnd(double simulationEndTime, Iterable<PetriSim> objects);

    /**
     * Shutdown any background workers (thread pools, etc.).
     */
    void shutdown();
}

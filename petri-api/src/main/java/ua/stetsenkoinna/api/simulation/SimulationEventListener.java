package ua.stetsenkoinna.api.simulation;

public interface SimulationEventListener {
    void onStep(SimulationStepEvent event);
    void onFinished();
    void onHalted();
}

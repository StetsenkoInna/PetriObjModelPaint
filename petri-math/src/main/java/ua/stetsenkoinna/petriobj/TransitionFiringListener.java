package ua.stetsenkoinna.petriobj;

/**
 * Reports the atomic phases of a firing as they happen.
 *
 * <p>This is the whole of what a {@link PetriSim} learns about anyone observing it: no model,
 * no element maps, no statistic collector. A Petri-object knows only its own net, so anything
 * that needs a model-wide picture of the firing — which is what an animating client needs —
 * has to be assembled by whoever installed the listener, and {@link PetriObjModel} is the only
 * place that has the object list to assemble it from.
 */
@FunctionalInterface
public interface TransitionFiringListener {

    /**
     * Called on the simulation thread, in order, at each of the four instants of a firing.
     *
     * <p>The net's arrays are read straight through — nothing is copied for the listener — so
     * a listener that wants the state at this instant must read it before returning.
     *
     * @param phase which instant of the firing this is
     * @param transition the transition being fired
     * @param time the model's current time at that instant
     */
    void onFiringPhase(FiringPhase phase, PetriT transition, double time);
}

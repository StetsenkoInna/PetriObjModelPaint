package ua.stetsenkoinna.server.adapter;

import ua.stetsenkoinna.petriobj.PetriObjModel;
import ua.stetsenkoinna.petriobj.SimulationStatisticCollector;

/**
 * Builds the model a simulation session is going to run.
 *
 * <p>What differs between the plain-net API and the Petri-object model API is only how the
 * document turns into a {@link PetriObjModel}; the session, threading and streaming
 * machinery around it is the same. Passing this in keeps that machinery version-agnostic.
 */
@FunctionalInterface
public interface ModelBuilder {

    /**
     * @param sessionId id the model is tagged with, so statistics can be traced back
     * @param collector the collector the model reports to
     * @return the model to simulate
     * @throws Exception if the document cannot be turned into a model
     */
    PetriObjModel build(String sessionId, SimulationStatisticCollector collector) throws Exception;
}

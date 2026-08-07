package ua.stetsenkoinna.server.adapter;

import ua.stetsenkoinna.graphnet.GraphPetriObjModel;
import ua.stetsenkoinna.petriobj.PetriObjModel;
import ua.stetsenkoinna.petriobj.PetriP;
import ua.stetsenkoinna.petriobj.PetriT;
import ua.stetsenkoinna.petriobj.SimulationStatisticCollector;
import ua.stetsenkoinna.pnml.PnmlModelParser;

/**
 * Builds a headless {@link PetriObjModel} from a Petri-object model document.
 *
 * <p>Accepts both shapes the format allows: a composed model of several Petri-objects with
 * the links between them, and a plain single-net document, which reads as a model of one
 * object. That makes the v2 endpoints a superset of v1 — the same net still runs.
 *
 * <p>{@link PetriP}/{@link PetriT} hand out element ids from static counters, so parsing is
 * serialized on {@link NetBuildLock#LOCK} to keep concurrent simulations from interleaving
 * id assignment.
 */
public final class PetriObjModelFactory {

    private PetriObjModelFactory() {}

    /**
     * Parses {@code modelXml} and wires the resulting model to {@code collector}.
     *
     * @param sessionId id to tag the model with
     * @param modelXml PNML document describing one or several Petri-objects
     * @param collector the collector statistics are reported to
     * @return a model ready to run
     * @throws Exception if the document cannot be parsed or does not describe valid nets
     */
    public static PetriObjModel build(String sessionId, String modelXml,
                                      SimulationStatisticCollector collector) throws Exception {
        PetriObjModel model;
        synchronized (NetBuildLock.LOCK) {
            GraphPetriObjModel graphModel = new PnmlModelParser().parseXml(modelXml);
            model = graphModel.createPetriObjModel(sessionId);
        }
        model.setIsProtokol(false);
        model.setIsStatistics(true);
        model.setStatisticCollector(collector);
        return model;
    }

    /**
     * Parses a document into its graph-level model, without building a simulation from it.
     *
     * @param modelXml PNML document describing one or several Petri-objects
     * @return the parsed model
     * @throws Exception if the document cannot be parsed
     */
    public static GraphPetriObjModel parse(String modelXml) throws Exception {
        synchronized (NetBuildLock.LOCK) {
            return new PnmlModelParser().parseXml(modelXml);
        }
    }
}

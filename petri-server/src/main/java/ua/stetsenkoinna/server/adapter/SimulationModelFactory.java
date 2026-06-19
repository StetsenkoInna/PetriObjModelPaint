package ua.stetsenkoinna.server.adapter;

import ua.stetsenkoinna.petriobj.PetriNet;
import ua.stetsenkoinna.petriobj.PetriObjModel;
import ua.stetsenkoinna.petriobj.PetriP;
import ua.stetsenkoinna.petriobj.PetriSim;
import ua.stetsenkoinna.petriobj.PetriT;
import ua.stetsenkoinna.petriobj.SimulationStatisticCollector;
import ua.stetsenkoinna.pnml.PnmlParser;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds a headless {@link PetriObjModel} from PNML for server-side simulation runs.
 *
 * <p>Shared by the WebSocket and SSE flows so the net-construction sequence lives in
 * one place.
 */
public final class SimulationModelFactory {

    private SimulationModelFactory() {}

    /**
     * Parses {@code netXml} and assembles a single-object model wired to {@code collector}.
     *
     * <p>{@link PetriP}/{@link PetriT} hand out element ids from static counters, so the
     * counter reset and net construction are serialized on {@link NetBuildLock#LOCK} to
     * keep concurrent simulations from interleaving id assignment.
     *
     * @throws Exception if the PNML cannot be parsed (propagated from {@link PnmlParser})
     */
    public static PetriObjModel build(String sessionId, String netXml,
                                      SimulationStatisticCollector collector) throws Exception {
        PetriNet net;
        synchronized (NetBuildLock.LOCK) {
            PetriP.initNext();
            PetriT.initNext();
            net = new PnmlParser().parseXml(netXml);
        }

        PetriSim sim = new PetriSim(net);
        PetriObjModel model = new PetriObjModel(sessionId, new ArrayList<>(List.of(sim)));
        model.setIsProtokol(false);
        model.setIsStatistics(true);
        model.setStatisticCollector(collector);
        return model;
    }
}

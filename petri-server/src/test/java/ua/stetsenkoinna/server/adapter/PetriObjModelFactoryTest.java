package ua.stetsenkoinna.server.adapter;

import org.junit.jupiter.api.Test;
import ua.stetsenkoinna.graphnet.GraphNetBuilder;
import ua.stetsenkoinna.graphnet.GraphPetriNet;
import ua.stetsenkoinna.graphnet.GraphPetriObjModel;
import ua.stetsenkoinna.graphnet.GraphPetriObject;
import ua.stetsenkoinna.petriobj.ArcIn;
import ua.stetsenkoinna.petriobj.ArcOut;
import ua.stetsenkoinna.petriobj.PetriNet;
import ua.stetsenkoinna.petriobj.PetriObjLink;
import ua.stetsenkoinna.petriobj.PetriObjModel;
import ua.stetsenkoinna.petriobj.PetriP;
import ua.stetsenkoinna.petriobj.PetriT;
import ua.stetsenkoinna.petriobj.SimulationStatisticCollector;
import ua.stetsenkoinna.pnml.PnmlModelGenerator;
import ua.stetsenkoinna.server.dto.ObjectModelParseResultDto;
import ua.stetsenkoinna.server.dto.ObjectModelResultDto;

import java.util.ArrayList;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What the v2 endpoints do with a Petri-object model document, without booting the
 * application: read it, describe it, and run it.
 */
class PetriObjModelFactoryTest {

    /** Builds {@code P0 -> T0 -> P1} with a constant delay, so a run is deterministic. */
    private static GraphPetriNet chainNet(String name, int startTokens) throws Exception {
        PetriP.initNext();
        PetriT.initNext();
        ArcIn.initNext();
        ArcOut.initNext();
        ArrayList<PetriP> places = new ArrayList<>();
        places.add(new PetriP("P0", startTokens));
        places.add(new PetriP("P1", 0));
        ArrayList<PetriT> transitions = new ArrayList<>();
        transitions.add(new PetriT("T0", 1.0));
        ArrayList<ArcIn> arcsIn = new ArrayList<>();
        arcsIn.add(new ArcIn(places.get(0), transitions.get(0), 1));
        ArrayList<ArcOut> arcsOut = new ArrayList<>();
        arcsOut.add(new ArcOut(transitions.get(0), places.get(1), 1));
        PetriNet net = new PetriNet(name, places, transitions, arcsIn, arcsOut);
        return GraphNetBuilder.build(net, Collections.emptyMap(), Collections.emptyMap(), null);
    }

    private static String twoObjectModelXml() throws Exception {
        GraphPetriObjModel model = new GraphPetriObjModel("Pipeline");
        model.addObject(new GraphPetriObject("Source", chainNet("Source", 2)));
        GraphPetriObject sink = new GraphPetriObject("Sink", chainNet("Sink", 0));
        sink.setPriority(1);
        model.addObject(sink);
        model.addLink(PetriObjLink.placeFusion(0, 1, 1, 0));
        return new PnmlModelGenerator().generateXml(model);
    }

    @Test
    void parseDescribesObjectsAndLinks() throws Exception {
        ObjectModelParseResultDto parsed = ObjectModelDtos.of(
                PetriObjModelFactory.parse(twoObjectModelXml()));

        assertEquals("Pipeline", parsed.name());
        assertEquals(2, parsed.objects().size());

        ObjectModelParseResultDto.ObjectDto source = parsed.objects().getFirst();
        assertEquals(0, source.index());
        assertEquals("Source", source.name());
        assertEquals(2, source.places().size());
        assertEquals(1, source.transitions().size());
        assertEquals(2, source.arcs().size());
        assertEquals(2, source.places().getFirst().initialMarking());

        assertEquals(1, parsed.objects().get(1).priority());

        assertEquals(1, parsed.links().size());
        ObjectModelParseResultDto.LinkDto link = parsed.links().getFirst();
        assertEquals("placeFusion", link.type());
        assertEquals(0, link.sourceObject());
        assertEquals(1, link.sourceElement());
        assertEquals(1, link.targetObject());
        assertEquals(0, link.targetElement());
    }

    @Test
    void buildProducesALinkedRunnableModel() throws Exception {
        PetriObjModel model = PetriObjModelFactory.build("session", twoObjectModelXml(), noStatistics());

        assertEquals(2, model.getListObj().size());
        assertSame(model.getListObj().get(1).getNet().getListP()[0],
                model.getListObj().get(0).getNet().getListP()[1],
                "the fused place must be a single instance");

        model.go(10.0);

        ObjectModelResultDto result = ObjectModelResults.of(10.0, 10.0, 0, model.getListObj());
        assertEquals(2, result.objects().size());
        assertEquals(0, result.objects().getFirst().index());
        assertEquals("Sink", result.objects().get(1).name());
        assertEquals(1, result.objects().get(1).priority());
        assertEquals(2, result.objects().get(1).places().get(1).finalMarking(),
                "both tokens must have travelled through the second object");
    }

    @Test
    void plainSingleNetDocumentRunsAsAModelOfOneObject() throws Exception {
        GraphPetriObjModel single = GraphPetriObjModel.singleObject(chainNet("Simple", 1), "Simple");
        String xml = new PnmlModelGenerator().generateXml(single);

        PetriObjModel model = PetriObjModelFactory.build("session", xml, noStatistics());
        assertEquals(1, model.getListObj().size());
        assertEquals(0, model.getListObj().getFirst().getStatisticId(),
                "the only object of a model is addressed as O0");

        ObjectModelParseResultDto parsed = ObjectModelDtos.of(PetriObjModelFactory.parse(xml));
        assertNotNull(parsed.objects().getFirst());
        assertTrue(parsed.links().isEmpty());
    }

    /** A collector that records nothing — these tests assert on the model, not the stream. */
    private static SimulationStatisticCollector noStatistics() {
        return new SimulationStatisticCollector() {
            @Override
            public boolean shouldCollect(double currentTime) {
                return false;
            }

            @Override
            public void onTimeStep(double currentTime, PetriNet net, int petriObjId) {
            }

            @Override
            public void flush(double currentTime) {
            }

            @Override
            public void onSimulationEnd(double simulationEndTime, Iterable<ua.stetsenkoinna.petriobj.PetriSim> objects) {
            }

            @Override
            public void shutdown() {
            }
        };
    }
}

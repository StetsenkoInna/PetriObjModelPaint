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

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

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

    /**
     * The document the three writers of this format agree on, read by the code the v2
     * endpoints actually run.
     *
     * <p>It is petri-model's copy rather than one of this module's own: the fixture already
     * exists in three repositories and is diffed between them, and a fourth copy here would
     * be one more thing to drift without anything watching it.
     */
    private static String conformantFixture() throws Exception {
        Path fixture = Paths.get("..", "petri-model", "src", "test", "resources", "pnml",
                "composed_conformant_v21.pnml");
        assertTrue(Files.exists(fixture),
                "expected the shared fixture at " + fixture.toAbsolutePath());
        return Files.readString(fixture, StandardCharsets.UTF_8);
    }

    /**
     * The off-by-one detector. The fused place sits in the <em>middle</em> of object 0's
     * places, so anything that treated the reference node as an extra element rather than as
     * the slot it replaces would shift every index after it, and the link that addresses
     * place 1 would land somewhere else.
     */
    @Test
    void aConformantDocumentDescribesItsObjectsAndLinksInOrder() throws Exception {
        ObjectModelParseResultDto parsed =
                ObjectModelDtos.of(PetriObjModelFactory.parse(conformantFixture()));

        assertEquals("PipelineDemo", parsed.name());
        assertEquals(2, parsed.objects().size());

        ObjectModelParseResultDto.ObjectDto generator = parsed.objects().getFirst();
        assertEquals(List.of("p_pool", "p_ready", "p_log"), generator.places().stream()
                .map(place -> place.id()).toList());
        assertEquals(List.of("t_gen"), generator.transitions().stream()
                .map(transition -> transition.id()).toList());
        assertEquals(1, generator.priority());
        assertEquals(3, generator.arcs().size());

        ObjectModelParseResultDto.ObjectDto server = parsed.objects().get(1);
        // "p_watch" is the object's own half of the place it shares with the generator, and the
        // arc from it into "t_end" is an ordinary arc of this object - which is what feeding a
        // transition across an object boundary looks like when it is drawn canonically.
        assertEquals(List.of("p_in", "p_busy", "p_done", "p_watch"), server.places().stream()
                .map(place -> place.id()).toList());
        assertEquals(List.of("t_start", "t_end"), server.transitions().stream()
                .map(transition -> transition.id()).toList());
        assertEquals(5, server.arcs().size(), "the arc realising a link is not an arc of the object");
        String sharedHalf = server.places().get(3).name();
        String fedTransition = server.transitions().get(1).name();
        assertTrue(server.arcs().stream().anyMatch(arc ->
                        arc.source().equals(sharedHalf) && arc.target().equals(fedTransition)),
                "the transition takes its input from a place of its own object");

        assertEquals(List.of("placeFusion", "transitionToPlace"),
                parsed.links().stream().map(ObjectModelParseResultDto.LinkDto::type)
                        .distinct().sorted().toList(),
                "a shared place and a transition feeding a place are the whole link set");
        assertEquals(3, parsed.links().size());
        assertLink(parsed, "placeFusion", 0, 1, 1, 0, 1);
        assertLink(parsed, "transitionToPlace", 1, 0, 0, 2, 2);
        assertLink(parsed, "placeFusion", 1, 3, 0, 2, 1);
    }

    /**
     * The canonical way one object feeds another object's transition: the two objects share a
     * place, and the arc into the transition is an ordinary arc of the object that owns the
     * transition. A transition never carries inputs from outside its own object, so no link
     * describes a place of one object reaching into a transition of another.
     */
    @Test
    void aTransitionIsFedFromAnotherObjectThroughASharedPlace() throws Exception {
        ObjectModelParseResultDto parsed = ObjectModelDtos.of(
                PetriObjModelFactory.parse(twoObjectModelXml()));

        assertEquals(List.of("placeFusion"),
                parsed.links().stream().map(ObjectModelParseResultDto.LinkDto::type).toList(),
                "sharing the place is the only link the two objects need");

        ObjectModelParseResultDto.ObjectDto sink = parsed.objects().get(1);
        String sharedPlace = sink.places().getFirst().name();
        String fedTransition = sink.transitions().getFirst().name();
        assertTrue(sink.arcs().stream().anyMatch(arc ->
                        arc.source().equals(sharedPlace) && arc.target().equals(fedTransition)),
                "the arc into the transition belongs to the object that owns the transition");

        PetriObjModel model = PetriObjModelFactory.build("session", twoObjectModelXml(), noStatistics());
        assertSame(model.getListObj().get(1).getNet().getListP()[0],
                model.getListObj().getFirst().getNet().getListP()[1],
                "the shared place is one instance, which is what carries the tokens across");

        model.go(10.0);
        assertEquals(2, model.getListObj().get(1).getNet().getListP()[1].getMark(),
                "the fed transition really fired on tokens produced by the other object");
    }

    @Test
    void aConformantDocumentBuildsALinkedModelThatRuns() throws Exception {
        PetriObjModel model = PetriObjModelFactory.build("session", conformantFixture(), noStatistics());

        PetriP[] generator = model.getListObj().getFirst().getNet().getListP();
        PetriP[] server = model.getListObj().get(1).getNet().getListP();
        assertSame(server[0], generator[1], "the fused place must be a single instance");

        model.go(200.0);

        assertEquals(0, generator[0].getMark(), "every token of the pool is generated");
        assertTrue(generator[2].getMark() >= 5,
                "the link into the log place delivered, so the two objects really are wired");
        assertTrue(server[2].getMark() >= 1,
                "and tokens reached the far end through the fused place");
    }

    private static void assertLink(ObjectModelParseResultDto parsed, String type,
                                   int sourceObject, int sourceElement,
                                   int targetObject, int targetElement,
                                   int quantity) {
        ObjectModelParseResultDto.LinkDto expected = new ObjectModelParseResultDto.LinkDto(
                type, sourceObject, sourceElement, targetObject, targetElement, quantity);
        assertTrue(parsed.links().contains(expected),
                "expected " + expected + " among " + parsed.links());
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

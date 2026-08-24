package pnml;

import org.junit.Test;
import org.w3c.dom.Document;
import ua.stetsenkoinna.graphnet.GraphNetBuilder;
import ua.stetsenkoinna.graphnet.GraphPetriNet;
import ua.stetsenkoinna.graphnet.GraphPetriObjModel;
import ua.stetsenkoinna.graphnet.GraphPetriObject;
import ua.stetsenkoinna.petriobj.ArcIn;
import ua.stetsenkoinna.petriobj.ArcOut;
import ua.stetsenkoinna.petriobj.PetriNet;
import ua.stetsenkoinna.petriobj.PetriObjLink;
import ua.stetsenkoinna.petriobj.PetriP;
import ua.stetsenkoinna.petriobj.PetriT;
import ua.stetsenkoinna.pnml.PnmlGenerator;
import ua.stetsenkoinna.pnml.PnmlModelGenerator;

import java.awt.geom.Point2D;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static pnml.PnmlConformanceAssertions.assertConformant;
import static pnml.PnmlConformanceAssertions.assertSchemaValid;
import static pnml.PnmlConformanceAssertions.parse;

/**
 * Validates what this project's writers actually produce against the PNML grammar itself,
 * ISO/IEC 15909-2's own RELAX NG schema for P/T nets, using jing as the validator.
 *
 * <p>This is a check no test elsewhere in this module runs: everywhere else, a document this
 * tool wrote is checked by re-reading it with this tool's own reader, which only proves the
 * two agree with each other. Here the referee is the standard's own grammar, run against real
 * output of {@link PnmlGenerator} and {@link PnmlModelGenerator}, plus the composed conformant
 * fixture other tests already rely on.
 *
 * <p>A RELAX NG schema cannot state everything a conformant document has to satisfy: an
 * arc's endpoints sharing its page, a reference chain terminating rather than cycling, the net
 * staying bipartite once references are resolved to what they stand for. So this also runs
 * those checks itself, in Java, on the same documents.
 *
 * <p>Both checks live in {@link PnmlConformanceAssertions}, so any other test with a document
 * to check, {@link WebInteropTest} included, can run them without duplicating this class.
 */
public class PnmlRngConformanceTest {

    // ---------------------------------------------------------------- (a) plain dialect

    /**
     * A single rich net covering what the plain dialect can carry: fractional canvas
     * coordinates (which the writer rounds, see {@link PnmlGenerator}), a marking parameter, a
     * transition of each supported distribution, a priority, a probability, an informational
     * (test/inhibitor-style) arc, and a display name containing spaces, the exact shape that
     * used to become an invalid {@code <net id>} before the writer sanitized it.
     */
    @Test
    public void aRichFlatNetWrittenByPnmlGeneratorIsSchemaValid() throws Exception {
        PetriP.initNext();
        PetriT.initNext();
        ArcIn.initNext();
        ArcOut.initNext();

        PetriP source = new PetriP("p0", "Source", 3);
        PetriP buffered = new PetriP("p1", "Buffered", 0);
        buffered.setMarkParam("bufferSize");
        PetriP watcher = new PetriP("p2", "Watcher", 1);
        PetriP sink = new PetriP("p3", "Sink", 0);
        List<PetriP> places = List.of(source, buffered, watcher, sink);

        PetriT det = new PetriT("t0", "Det", 1.5);
        det.setDistribution("det", 1.5);
        det.setPriority(2);
        PetriT exp = new PetriT("t1", "Exp", 2.5);
        exp.setDistribution("exp", 2.5);
        exp.setProbability(0.5);
        PetriT norm = new PetriT("t2", "Norm", 3.5);
        norm.setDistribution("norm", 3.5);
        norm.setParamDeviation(0.75);
        List<PetriT> transitions = List.of(det, exp, norm);

        ArrayList<ArcIn> arcIns = new ArrayList<>();
        arcIns.add(new ArcIn(source, det, 2));
        arcIns.add(new ArcIn(buffered, exp, 1));
        arcIns.add(new ArcIn(watcher, norm, 1, true)); // informational: tests norm without consuming
        ArrayList<ArcOut> arcOuts = new ArrayList<>();
        arcOuts.add(new ArcOut(det, buffered, 1));
        arcOuts.add(new ArcOut(exp, sink, 1));
        arcOuts.add(new ArcOut(norm, sink, 1));

        PetriNet net = new PetriNet("Rich Flat Net With Spaces", new ArrayList<>(places),
                new ArrayList<>(transitions), arcIns, arcOuts);

        // Fractional canvas coordinates: the point of writing them is that the writer rounds
        // them (Math.round) into the same integer for both <graphics><position> and the
        // tool-specific <coordinates>, never leaving the two to disagree.
        Map<Integer, Point2D.Double> placeCoordinates = new HashMap<>();
        placeCoordinates.put(source.getNumber(), new Point2D.Double(60.4, 80.6));
        placeCoordinates.put(buffered.getNumber(), new Point2D.Double(200.5, 80.5));
        placeCoordinates.put(watcher.getNumber(), new Point2D.Double(130.2, 200.8));
        placeCoordinates.put(sink.getNumber(), new Point2D.Double(340.9, 80.1));
        Map<Integer, Point2D.Double> transitionCoordinates = new HashMap<>();
        transitionCoordinates.put(det.getNumber(), new Point2D.Double(130.5, 80.5));
        transitionCoordinates.put(exp.getNumber(), new Point2D.Double(270.3, 80.7));
        transitionCoordinates.put(norm.getNumber(), new Point2D.Double(270.6, 200.4));
        GraphPetriNet graphNet = GraphNetBuilder.build(net, placeCoordinates, transitionCoordinates, null, false);

        File file = new File("target/rng_rich_flat_net.pnml");
        new PnmlGenerator().generate(net, file, graphNet);
        String xml = Files.readString(file.toPath(), StandardCharsets.UTF_8);

        assertSchemaValid(xml);
        Document document = parse(xml);
        assertConformant(document);
        assertTrue("the net name keeps its spaces", xml.contains("Rich Flat Net With Spaces"));
        assertFalse("but the id it was sanitized into has none", xml.contains("id=\"Rich Flat Net With Spaces\""));
    }

    // ---------------------------------------------------------------- (b) composed dialect

    /**
     * One place repeated by several others.
     *
     * <p>This is the conformance question behind one-to-many reference links, and the schema
     * answers it plainly. A {@code <referencePlace>} carries a single {@code ref}, so there is
     * no node that means "shared with many"; what the standard does not forbid is several
     * reference places naming the same target. Its three validating instructions for a
     * reference place are that {@code ref} names a place or another reference place, that it
     * does not name its own element, and that it does not close a cycle - and N references to
     * one place breaks none of them.
     *
     * <p>So the check is not that the writer learned a new construct. It is that emitting the
     * links independently, which is all it does, produces a document the standard's own grammar
     * accepts.
     */
    @Test
    public void onePlaceRepeatedBySeveralOthersIsSchemaValid() throws Exception {
        GraphPetriObjModel model = new GraphPetriObjModel("Fan Out Model");

        GraphPetriObject source = new GraphPetriObject("Source", chainNet("Source", 4));
        model.addObject(source);
        GraphPetriObject firstCopy = new GraphPetriObject("FirstCopy", chainNet("FirstCopy", 0));
        model.addObject(firstCopy);
        GraphPetriObject secondCopy = new GraphPetriObject("SecondCopy", chainNet("SecondCopy", 0));
        model.addObject(secondCopy);

        // Both copies point at object 0's first place. In PNML terms each of them is the one
        // replaced by a reference node, and object 0's place is the instance that survives.
        model.addLink(PetriObjLink.placeFusion(1, 0, 0, 0));
        model.addLink(PetriObjLink.placeFusion(2, 0, 0, 0));

        String xml = new PnmlModelGenerator().generateXml(model);

        assertSchemaValid(xml);
        Document document = parse(xml);
        assertConformant(document);

        List<String> refs = referenceTargets(document);
        assertEquals("one reference place per link", 2, refs.size());
        assertEquals("both name the same place, which is what one-to-many is",
                refs.get(0), refs.get(1));
    }

    /** Every {@code ref} attribute of every {@code <referencePlace>} in the document. */
    private static List<String> referenceTargets(Document document) {
        List<String> refs = new ArrayList<>();
        org.w3c.dom.NodeList nodes = document.getElementsByTagName("referencePlace");
        for (int i = 0; i < nodes.getLength(); i++) {
            refs.add(((org.w3c.dom.Element) nodes.item(i)).getAttribute("ref"));
        }
        return refs;
    }

    /**
     * A rich composed model: an object nested inside another, a place fusion, a
     * transition-to-place link with a quantity above one, and a fused-away place's marking
     * preserved in {@code <fusedInitialMarking>}.
     */
    @Test
    public void aRichComposedModelWrittenByPnmlModelGeneratorIsSchemaValid() throws Exception {
        GraphPetriObjModel model = new GraphPetriObjModel("Rich Composed Model");

        GraphPetriObject outer = new GraphPetriObject("Outer", chainNet("Outer", 4));
        model.addObject(outer);
        GraphPetriObject inner = new GraphPetriObject("Inner", chainNet("Inner", 0));
        inner.setParentIndex(0);
        model.addObject(inner);
        GraphPetriObject server = new GraphPetriObject("Server", chainNet("Server", 0));
        server.setPriority(3);
        model.addObject(server);

        // Outer's first place slot is fused with Server's first: the fused-away place still
        // carries 4 tokens, which the writer has to preserve in <fusedInitialMarking>.
        model.addLink(PetriObjLink.placeFusion(0, 0, 2, 0));
        // Server's transition feeds Inner's first place, with a quantity above one.
        model.addLink(PetriObjLink.transitionToPlace(2, 0, 1, 0, 3));

        String xml = new PnmlModelGenerator().generateXml(model);

        assertSchemaValid(xml);
        Document document = parse(xml);
        assertConformant(document);
        assertTrue("a fused place keeps its marking for the drawing",
                xml.contains("fusedInitialMarking"));
        assertTrue("the nested object's page sits inside its parent's",
                xml.contains("<page id=\"object1\""));
    }

    /** Builds {@code P0 -> T0 -> P1}, the smallest net that can take part in every link type. */
    private static GraphPetriNet chainNet(String namePrefix, int startTokens) throws Exception {
        PetriP.initNext();
        PetriT.initNext();
        ArcIn.initNext();
        ArcOut.initNext();
        PetriP p0 = new PetriP(namePrefix + "_p0", "P0", startTokens);
        PetriP p1 = new PetriP(namePrefix + "_p1", "P1", 0);
        PetriT t0 = new PetriT(namePrefix + "_t0", "T0", 1.0);
        ArrayList<PetriP> places = new ArrayList<>(List.of(p0, p1));
        ArrayList<PetriT> transitions = new ArrayList<>(List.of(t0));
        ArrayList<ArcIn> arcIns = new ArrayList<>(List.of(new ArcIn(p0, t0, 1)));
        ArrayList<ArcOut> arcOuts = new ArrayList<>(List.of(new ArcOut(t0, p1, 1)));
        PetriNet net = new PetriNet(namePrefix, places, transitions, arcIns, arcOuts);
        return GraphNetBuilder.build(net, Collections.emptyMap(), Collections.emptyMap(), null);
    }

    // ---------------------------------------------------------------- (c) the shared fixture

    /**
     * The composed conformant fixture {@link pnml.ComposedPnmlConformanceTest} reads by name:
     * proof that a document already covered by this project's own reader tests is also valid
     * PNML by the standard's own grammar.
     */
    @Test
    public void theComposedConformantFixtureIsSchemaValid() throws Exception {
        String xml = Files.readString(
                Paths.get(getClass().getResource("/pnml/composed_conformant_v21.pnml").toURI()),
                StandardCharsets.UTF_8);

        assertSchemaValid(xml);
        assertConformant(parse(xml));
    }
}

package pnml;

import org.junit.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import ua.stetsenkoinna.graphnet.GraphNetBuilder;
import ua.stetsenkoinna.graphnet.GraphPetriNet;
import ua.stetsenkoinna.graphnet.GraphPetriObjModel;
import ua.stetsenkoinna.graphnet.GraphPetriObject;
import ua.stetsenkoinna.petriobj.ArcIn;
import ua.stetsenkoinna.petriobj.ArcOut;
import ua.stetsenkoinna.petriobj.PetriNet;
import ua.stetsenkoinna.petriobj.PetriObjLink;
import ua.stetsenkoinna.petriobj.PetriObjLinkType;
import ua.stetsenkoinna.petriobj.PetriObjModel;
import ua.stetsenkoinna.petriobj.PetriP;
import ua.stetsenkoinna.petriobj.PetriT;
import ua.stetsenkoinna.pnml.PnmlModelGenerator;
import ua.stetsenkoinna.pnml.PnmlModelParser;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * The inter-object structure of a composed document, expressed the way ISO/IEC 15909-2 says
 * it must be: with {@code <referencePlace>} and {@code <referenceTransition>} nodes rather
 * than only inside a tool-specific block.
 *
 * <p>The point of the exercise is that a reader which is not this tool sees the same model
 * this tool sees. The point of <em>these</em> tests is that the change costs nothing: a
 * document in the old format still parses into exactly the model it always did, which
 * {@link #aLegacyDocumentParsesIntoExactlyTheModelTheConformantOneDescribes} pins against a
 * literal old-format file.
 */
public class ComposedPnmlConformanceTest {

    // ---------------------------------------------------------------- fixtures

    private static final String CONFORMANT = "/pnml/composed_conformant_v21.pnml";
    private static final String LEGACY = "/pnml/composed_legacy_v20.pnml";
    private static final String FOREIGN = "/pnml/composed_foreign_references.pnml";

    private static String fixture(String resource) throws Exception {
        return Files.readString(
                Paths.get(ComposedPnmlConformanceTest.class.getResource(resource).toURI()),
                StandardCharsets.UTF_8);
    }

    private static GraphPetriObjModel parseFixture(String resource) throws Exception {
        return new PnmlModelParser().parseXml(fixture(resource));
    }

    /** Builds {@code P0 -> T0 -> P1}, the smallest net that can take part in every link type. */
    private static GraphPetriNet chainNet(String name, int startTokens, int endTokens) throws Exception {
        return chainNet(name, startTokens, endTokens, null);
    }

    /**
     * @param idPrefix fixed element ids, to force the id collision two objects loaded from
     *        different files can have; {@code null} for generated, unique ones
     */
    private static GraphPetriNet chainNet(String name, int startTokens, int endTokens, String idPrefix)
            throws Exception {
        PetriP.initNext();
        PetriT.initNext();
        ArcIn.initNext();
        ArcOut.initNext();
        ArrayList<PetriP> places = new ArrayList<>();
        ArrayList<PetriT> transitions = new ArrayList<>();
        if (idPrefix == null) {
            places.add(new PetriP("P0", startTokens));
            places.add(new PetriP("P1", endTokens));
            transitions.add(new PetriT("T0", 1.0));
        } else {
            places.add(new PetriP(idPrefix + "p0", "P0", startTokens));
            places.add(new PetriP(idPrefix + "p1", "P1", endTokens));
            transitions.add(new PetriT(idPrefix + "t0", "T0", 1.0));
        }
        ArrayList<ArcIn> arcsIn = new ArrayList<>();
        arcsIn.add(new ArcIn(places.get(0), transitions.get(0), 1));
        ArrayList<ArcOut> arcsOut = new ArrayList<>();
        arcsOut.add(new ArcOut(transitions.get(0), places.get(1), 1));
        PetriNet net = new PetriNet(name, places, transitions, arcsIn, arcsOut);
        return GraphNetBuilder.build(net, Collections.emptyMap(), Collections.emptyMap(), null);
    }

    /** A model carrying one link of each of the three kinds. */
    private static GraphPetriObjModel threeLinkModel() throws Exception {
        GraphPetriObjModel model = new GraphPetriObjModel("QueueingSystem");
        model.addObject(new GraphPetriObject("Generator", chainNet("Generator", 1, 4)));
        GraphPetriObject server = new GraphPetriObject("Server", chainNet("Server", 0, 0));
        server.setPriority(3);
        model.addObject(server);
        model.addLink(PetriObjLink.placeFusion(0, 1, 1, 0));
        model.addLink(PetriObjLink.transitionToPlace(1, 0, 0, 0, 2));
        model.addLink(PetriObjLink.placeToTransition(1, 1, 0, 0, 1, true));
        return model;
    }

    // ---------------------------------------------------------------- reading

    @Test
    public void theConformantFixtureParsesIntoTheModelItDescribes() throws Exception {
        GraphPetriObjModel model = parseFixture(CONFORMANT);

        assertEquals("PipelineDemo", model.getName());
        assertEquals(2, model.getObjectCount());

        GraphPetriObject generator = model.getObject(0);
        assertEquals("Generator", generator.getName());
        assertEquals(1, generator.getPriority());
        assertEquals("the fused place is still a slot of the object that gave it up",
                List.of("Pool", "Ready", "Log"), placeNames(generator));
        assertEquals(List.of("Generate"), transitionNames(generator));
        assertEquals("the arc realising a link is not an arc of the object",
                3, arcCount(generator));

        GraphPetriObject server = model.getObject(1);
        assertEquals("Server", server.getName());
        assertEquals(List.of("In", "Busy", "Done"), placeNames(server));
        assertEquals(List.of("Start", "End"), transitionNames(server));
        assertEquals(4, arcCount(server));

        assertEquals(3, model.getLinks().size());
        PetriObjLink fusion = only(model, PetriObjLinkType.PLACE_FUSION);
        assertEquals("the fusion is on the middle slot, where an off-by-one would show",
                1, fusion.getSourceElement());
        assertEquals(0, fusion.getSourceObject());
        assertEquals(1, fusion.getTargetObject());
        assertEquals(0, fusion.getTargetElement());

        PetriObjLink delivery = only(model, PetriObjLinkType.TRANSITION_TO_PLACE);
        assertEquals(1, delivery.getSourceObject());
        assertEquals(0, delivery.getSourceElement());
        assertEquals(0, delivery.getTargetObject());
        assertEquals(2, delivery.getTargetElement());
        assertEquals(2, delivery.getQuantity());

        PetriObjLink test = only(model, PetriObjLinkType.PLACE_TO_TRANSITION);
        assertEquals(0, test.getSourceObject());
        assertEquals(2, test.getSourceElement());
        assertEquals(1, test.getTargetObject());
        assertEquals(1, test.getTargetElement());
        assertEquals(1, test.getQuantity());
        assertTrue(test.isInformational());
    }

    /**
     * The backward-compatibility contract, pinned against a literal document in the format
     * this tool wrote before reference nodes existed. Both files describe the same model, and
     * both must produce it, element for element, link for link.
     */
    @Test
    public void aLegacyDocumentParsesIntoExactlyTheModelTheConformantOneDescribes() throws Exception {
        GraphPetriObjModel legacy = parseFixture(LEGACY);
        GraphPetriObjModel conformant = parseFixture(CONFORMANT);

        assertEquals(conformant.getName(), legacy.getName());
        assertEquals(conformant.getObjectCount(), legacy.getObjectCount());
        for (int index = 0; index < conformant.getObjectCount(); index++) {
            GraphPetriObject expected = conformant.getObject(index);
            GraphPetriObject actual = legacy.getObject(index);
            assertEquals(expected.getName(), actual.getName());
            assertEquals(expected.getPriority(), actual.getPriority());
            assertEquals(expected.getPosition(), actual.getPosition());
            assertEquals(placeNames(expected), placeNames(actual));
            assertEquals(placeMarkings(expected), placeMarkings(actual));
            assertEquals(placeIds(expected), placeIds(actual));
            assertEquals(transitionNames(expected), transitionNames(actual));
            assertEquals(arcCount(expected), arcCount(actual));
        }
        assertEquals(sortedLinks(conformant), sortedLinks(legacy));
    }

    /** The legacy document must also still run, and produce the same wiring. */
    @Test
    public void aLegacyDocumentStillBuildsTheSameRunnableModel() throws Exception {
        PetriObjModel legacy = parseFixture(LEGACY).createPetriObjModel("legacy");
        PetriObjModel conformant = parseFixture(CONFORMANT).createPetriObjModel("conformant");

        for (PetriObjModel model : List.of(legacy, conformant)) {
            assertSame("the fused place must be one instance in both objects",
                    model.getListObj().get(1).getNet().getListP()[0],
                    model.getListObj().get(0).getNet().getListP()[1]);
        }
        legacy.setIsProtokol(false);
        legacy.go(10.0);
    }

    /**
     * A document from a tool that knows the standard but not this tool: reference nodes and
     * nothing else. The objects and both links still come out, with each reference node's
     * role inferred from whether the page draws an arc to it.
     */
    @Test
    public void aForeignDocumentWithoutToolSpecificDataStillYieldsObjectsAndLinks() throws Exception {
        GraphPetriObjModel model = parseFixture(FOREIGN);

        assertEquals(2, model.getObjectCount());
        assertEquals("the untouched reference node is a shared place, so it keeps its slot",
                List.of("Ready", "alpha_share"), placeNames(model.getObject(0)));
        assertEquals(1, model.getObject(0).getTransitionCount());
        assertEquals("the arc into the stand-in is a link, not an arc of the object",
                1, arcCount(model.getObject(0)));
        assertEquals(List.of("Inbox", "Done"), placeNames(model.getObject(1)));

        assertEquals(2, model.getLinks().size());
        PetriObjLink fusion = only(model, PetriObjLinkType.PLACE_FUSION);
        assertEquals(0, fusion.getSourceObject());
        assertEquals(1, fusion.getSourceElement());
        assertEquals(1, fusion.getTargetObject());
        assertEquals(1, fusion.getTargetElement());

        PetriObjLink delivery = only(model, PetriObjLinkType.TRANSITION_TO_PLACE);
        assertEquals(0, delivery.getSourceObject());
        assertEquals(0, delivery.getSourceElement());
        assertEquals(1, delivery.getTargetObject());
        assertEquals(0, delivery.getTargetElement());
        assertEquals(3, delivery.getQuantity());
    }

    /**
     * Fusion is destructive and not idempotent, so it gets exactly one source of truth: the
     * structure. A declaration the reference nodes do not back is ignored rather than applied
     * on top, which would fuse a second pair of places nobody asked for.
     */
    @Test
    public void aDeclaredFusionTheStructureDoesNotHaveIsIgnored() throws Exception {
        String contradictory = fixture(CONFORMANT).replace("</petriObjectLinks>",
                "  <link type=\"placeFusion\" sourceObject=\"0\" sourceElement=\"0\""
                        + " targetObject=\"1\" targetElement=\"2\"/>\n      </petriObjectLinks>");

        GraphPetriObjModel model = new PnmlModelParser().parseXml(contradictory);
        assertEquals("only the fusion the structure states survives",
                1, countOfType(model, PetriObjLinkType.PLACE_FUSION));

        PetriObjModel runnable = model.createPetriObjModel("contradictory");
        PetriP[] generator = runnable.getListObj().get(0).getNet().getListP();
        PetriP[] server = runnable.getListObj().get(1).getNet().getListP();
        assertSame(server[0], generator[1]);
        assertNotSame("the declared fusion must not have been applied", server[2], generator[0]);
        assertEquals("and the markings of the two places must not have been merged",
                5, generator[0].getMark());
        assertEquals(0, server[2].getMark());
    }

    @Test
    public void aReferenceThatStandsForItselfIsRejected() throws Exception {
        try {
            new PnmlModelParser().parseXml(twoPageDocument(
                    "<referencePlace id=\"loop\" ref=\"loop\"/>", ""));
            fail("a self-reference has no node to resolve to");
        } catch (Exception expected) {
            assertTrue("the message should name the offending id, was: " + expected.getMessage(),
                    expected.getMessage().contains("loop"));
        }
    }

    @Test
    public void aCycleOfReferencesIsRejected() throws Exception {
        try {
            new PnmlModelParser().parseXml(twoPageDocument(
                    "<referencePlace id=\"here\" ref=\"there\"/>",
                    "<referencePlace id=\"there\" ref=\"here\"/>"));
            fail("a reference cycle never reaches a real node");
        } catch (Exception expected) {
            assertTrue("the message should name the offending id, was: " + expected.getMessage(),
                    expected.getMessage().contains("here") || expected.getMessage().contains("there"));
        }
    }

    /**
     * An id used twice makes every {@code ref=} pointing at it ambiguous, so a conformant
     * document is refused. A legacy document has no {@code ref=} to be ambiguous and is read
     * exactly as before, page by page, each with its own id table.
     */
    @Test
    public void aDuplicateIdIsFatalOnlyWhereItCouldBeAmbiguous() throws Exception {
        try {
            new PnmlModelParser().parseXml(twoPageDocument(
                    "<place id=\"shared\"/><referencePlace id=\"stand_in\" ref=\"beta_p\"/>",
                    "<place id=\"shared\"/><place id=\"beta_p\"/>"));
            fail("two elements with one id make a reference ambiguous");
        } catch (Exception expected) {
            assertTrue("the message should name the duplicated id, was: " + expected.getMessage(),
                    expected.getMessage().contains("shared"));
        }

        GraphPetriObjModel legacy = new PnmlModelParser().parseXml(twoPageDocument(
                "<place id=\"shared\"/>", "<place id=\"shared\"/>"));
        assertEquals("a legacy document with the same duplicate still opens",
                2, legacy.getObjectCount());
    }

    // ---------------------------------------------------------------- writing

    @Test
    public void theWriterDrawsEveryLinkWithReferenceNodes() throws Exception {
        Document document = parseXml(new PnmlModelGenerator().generateXml(threeLinkModel()));
        Element generator = page(document, 0);
        Element server = page(document, 1);

        assertEquals("the fused slot is a reference node instead of a place, not as well as one",
                1, children(generator, "place").size());
        assertEquals(1, children(generator, "referencePlace").size());
        assertEquals("a fusion is drawn on the page that gives its place up",
                "fusion", roleOf(children(generator, "referencePlace").getFirst()));

        List<Element> stands = new ArrayList<>(children(server, "referencePlace"));
        stands.addAll(children(server, "referenceTransition"));
        assertEquals("both arc-like links are drawn on the source object's page", 2, stands.size());
        for (Element stand : stands) {
            assertEquals("representative", roleOf(stand));
        }

        for (Element reference : allReferenceNodes(document)) {
            assertEquals("a reference node may not carry a marking of its own: after "
                            + "flattening it is the node it stands for, and the tokens would double",
                    0, reference.getElementsByTagName("initialMarking").getLength());
        }
        assertIdsUniqueAndArcsIntraPage(document);
    }

    /**
     * The one thing the standard projection cannot keep: a fused-away place stops existing,
     * so its marking has nowhere to go. It has no effect after wiring either, but a user who
     * typed it should still find it in the file.
     */
    @Test
    public void theMarkingOfAFusedAwayPlaceIsKeptForTheDrawing() throws Exception {
        Document document = parseXml(new PnmlModelGenerator().generateXml(threeLinkModel()));
        Element reference = children(page(document, 0), "referencePlace").getFirst();

        assertEquals("4", textOf(reference, "fusedInitialMarking"));

        GraphPetriObjModel restored =
                new PnmlModelParser().parseXml(new PnmlModelGenerator().generateXml(threeLinkModel()));
        assertEquals("and it comes back on the slot, so a re-export says the same thing",
                List.of(1, 4), placeMarkings(restored.getObject(0)));
    }

    @Test
    public void aWrittenModelSurvivesTheRoundTripAndRuns() throws Exception {
        String xml = new PnmlModelGenerator().generateXml(threeLinkModel());
        GraphPetriObjModel restored = new PnmlModelParser().parseXml(xml);

        assertEquals(2, restored.getObjectCount());
        assertEquals(2, restored.getObject(0).getPlaceCount());
        assertEquals(1, restored.getObject(0).getTransitionCount());
        assertEquals(3, restored.getObject(1).getPriority());
        assertEquals(3, restored.getLinks().size());

        PetriObjLink delivery = only(restored, PetriObjLinkType.TRANSITION_TO_PLACE);
        assertEquals(2, delivery.getQuantity());
        PetriObjLink test = only(restored, PetriObjLinkType.PLACE_TO_TRANSITION);
        assertTrue("an informational arc has no P/T form, so the flag has to survive in "
                + "the arc's own tool-specific block", test.isInformational());
        assertEquals(1, test.getQuantity());

        PetriObjModel runnable = restored.createPetriObjModel("round-trip");
        assertSame("the fused place must be one instance in both objects",
                runnable.getListObj().get(1).getNet().getListP()[0],
                runnable.getListObj().get(0).getNet().getListP()[1]);
        runnable.setIsProtokol(false);
        runnable.go(10.0);
    }

    /**
     * Re-exporting what was just read has to name the same things: an id that drifted would
     * break every simulation result keyed by it, and a reference node whose target moved
     * would silently relink the model.
     */
    @Test
    public void writingWhatWasReadNamesExactlyTheSameElements() throws Exception {
        String once = new PnmlModelGenerator().generateXml(threeLinkModel());
        String twice = new PnmlModelGenerator().generateXml(new PnmlModelParser().parseXml(once));
        assertEquals(identifiedStructure(parseXml(once)), identifiedStructure(parseXml(twice)));
    }

    /**
     * Ids are written verbatim, which keeps documents byte-stable and keeps results keyed by
     * the ids the canvas uses. Two objects loaded from different files can nevertheless carry
     * the same id, and then the whole document is namespaced by object, all of it, so that
     * one rule explains every id in the file.
     */
    @Test
    public void idsSharedBetweenTwoObjectsAreNamespacedByObject() throws Exception {
        GraphPetriObjModel model = new GraphPetriObjModel("Twins");
        model.addObject(new GraphPetriObject("Left", chainNet("Left", 1, 0, "dup_")));
        model.addObject(new GraphPetriObject("Right", chainNet("Right", 0, 0, "dup_")));
        model.addLink(PetriObjLink.placeFusion(0, 1, 1, 0));

        String xml = new PnmlModelGenerator().generateXml(model);
        Document document = parseXml(xml);
        assertIdsUniqueAndArcsIntraPage(document);
        assertTrue("every id is namespaced, not only the colliding ones",
                xml.contains("\"o0_dup_p0\"") && xml.contains("\"o1_dup_p0\""));

        GraphPetriObjModel restored = new PnmlModelParser().parseXml(xml);
        assertEquals(2, restored.getObjectCount());
        assertEquals(1, restored.getLinks().size());
        assertEquals("o0_dup_p1", placeIds(restored.getObject(0)).get(1));

        assertEquals("re-exporting must not stack a second namespace on top",
                identifiedStructure(document),
                identifiedStructure(parseXml(new PnmlModelGenerator().generateXml(restored))));
    }

    /**
     * Wiring a fusion overwrites the source object's slot, so when two fusions claim the same
     * slot only the last one is left standing. The document has to say that and nothing else:
     * a page cannot show one slot twice, and drawing the earlier fusion instead would hand
     * every reader a model the engine never builds.
     */
    @Test
    public void onlyTheLastFusionOfASlotIsDrawn() throws Exception {
        GraphPetriObjModel model = new GraphPetriObjModel("Contested");
        model.addObject(new GraphPetriObject("Source", chainNet("Source", 1, 0)));
        model.addObject(new GraphPetriObject("Loser", chainNet("Loser", 0, 0)));
        model.addObject(new GraphPetriObject("Winner", chainNet("Winner", 0, 0)));
        model.addLink(PetriObjLink.placeFusion(0, 1, 1, 0));
        model.addLink(PetriObjLink.placeFusion(0, 1, 2, 0));

        GraphPetriObjModel restored =
                new PnmlModelParser().parseXml(new PnmlModelGenerator().generateXml(model));
        assertEquals("the overwritten declaration is not a link of the document any more",
                1, restored.getLinks().size());
        assertEquals(2, only(restored, PetriObjLinkType.PLACE_FUSION).getTargetObject());

        PetriObjModel runnable = restored.createPetriObjModel("contested");
        PetriP contested = runnable.getListObj().getFirst().getNet().getListP()[1];
        assertSame(runnable.getListObj().get(2).getNet().getListP()[0], contested);
        assertNotSame(runnable.getListObj().get(1).getNet().getListP()[0], contested);
    }

    /**
     * A link inside a single Petri-object has no structural form, there is no second page to
     * put a reference node on, but a user's export must not fail over it.
     */
    @Test
    public void aLinkInsideOneObjectIsSkippedRatherThanFatal() throws Exception {
        GraphPetriObjModel model = new GraphPetriObjModel("SelfLink");
        model.addObject(new GraphPetriObject("Only", chainNet("Only", 1, 0)));
        model.addObject(new GraphPetriObject("Other", chainNet("Other", 0, 0)));
        model.addLink(PetriObjLink.transitionToPlace(0, 0, 0, 0, 1));

        String xml = new PnmlModelGenerator().generateXml(model);
        assertFalse("no reference node can express it", xml.contains("referencePlace"));
        assertTrue("but the declaration is still recorded", xml.contains("transitionToPlace"));
        assertIdsUniqueAndArcsIntraPage(parseXml(xml));
    }

    // ---------------------------------------------------------------- helpers

    /** A minimal conformant-or-legacy document, depending on what the pages are given. */
    private static String twoPageDocument(String alpha, String beta) {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<pnml xmlns=\"http://www.pnml.org/version-2009/grammar/pnml\">\n"
                + "  <net id=\"n\" type=\"http://www.pnml.org/version-2009/grammar/ptnet\">\n"
                + "    <name><text>Minimal</text></name>\n"
                + "    <page id=\"object0\">" + alpha + "</page>\n"
                + "    <page id=\"object1\">" + beta + "</page>\n"
                + "  </net>\n"
                + "</pnml>\n";
    }

    private static Document parseXml(String xml) throws Exception {
        return DocumentBuilderFactory.newInstance().newDocumentBuilder()
                .parse(new InputSource(new StringReader(xml)));
    }

    private static Element page(Document document, int index) {
        return (Element) document.getElementsByTagName("page").item(index);
    }

    /** @param tagName tag to keep, or {@code null} for every element child */
    private static List<Element> children(Element parent, String tagName) {
        List<Element> found = new ArrayList<>();
        NodeList nodes = parent.getChildNodes();
        for (int i = 0; i < nodes.getLength(); i++) {
            Node node = nodes.item(i);
            if (node.getNodeType() == Node.ELEMENT_NODE
                    && (tagName == null || tagName.equals(node.getNodeName()))) {
                found.add((Element) node);
            }
        }
        return found;
    }

    private static List<Element> allReferenceNodes(Document document) {
        List<Element> references = new ArrayList<>();
        for (String tagName : List.of("referencePlace", "referenceTransition")) {
            NodeList nodes = document.getElementsByTagName(tagName);
            for (int i = 0; i < nodes.getLength(); i++) {
                references.add((Element) nodes.item(i));
            }
        }
        return references;
    }

    private static String roleOf(Element reference) {
        return textOf(reference, "referenceRole");
    }

    /**
     * Everything the document names: which page each element sits on, what its id is and,
     * for a reference node, which element it stands for. Coordinates are deliberately left
     * out, an import re-anchors a drawing, which is not what this is about.
     */
    private static List<String> identifiedStructure(Document document) {
        List<String> described = new ArrayList<>();
        NodeList pages = document.getElementsByTagName("page");
        for (int i = 0; i < pages.getLength(); i++) {
            Element page = (Element) pages.item(i);
            described.add("page " + page.getAttribute("id"));
            for (Element child : children(page, null)) {
                String id = child.getAttribute("id");
                if (id.isEmpty()) {
                    continue;
                }
                described.add("  " + child.getNodeName() + " " + id
                        + " ref=" + child.getAttribute("ref")
                        + " source=" + child.getAttribute("source")
                        + " target=" + child.getAttribute("target"));
            }
        }
        NodeList links = document.getElementsByTagName("link");
        for (int i = 0; i < links.getLength(); i++) {
            Element link = (Element) links.item(i);
            described.add("link " + link.getAttribute("type")
                    + " " + link.getAttribute("sourceElementId")
                    + " -> " + link.getAttribute("targetElementId")
                    + " x" + link.getAttribute("quantity")
                    + " informational=" + link.getAttribute("informational"));
        }
        return described;
    }

    private static String textOf(Element parent, String tagName) {
        NodeList nodes = parent.getElementsByTagName(tagName);
        return nodes.getLength() == 0 ? null : nodes.item(0).getTextContent();
    }

    /**
     * The two invariants a schema cannot state: ids are unique across the document, and no
     * arc leaves its page, RELAX NG types an arc endpoint as a plain IDREF and therefore
     * cannot see a cross-page arc at all.
     */
    private static void assertIdsUniqueAndArcsIntraPage(Document document) {
        List<String> allIds = new ArrayList<>();
        NodeList pages = document.getElementsByTagName("page");
        for (int i = 0; i < pages.getLength(); i++) {
            Element page = (Element) pages.item(i);
            List<String> nodesOnPage = new ArrayList<>();
            for (String tagName : List.of("place", "transition", "referencePlace", "referenceTransition")) {
                for (Element element : children(page, tagName)) {
                    nodesOnPage.add(element.getAttribute("id"));
                }
            }
            allIds.addAll(nodesOnPage);
            for (Element arc : children(page, "arc")) {
                allIds.add(arc.getAttribute("id"));
                assertTrue("arc " + arc.getAttribute("id") + " leaves its page",
                        nodesOnPage.contains(arc.getAttribute("source"))
                                && nodesOnPage.contains(arc.getAttribute("target")));
            }
        }
        assertEquals("every id in the document is unique",
                allIds.size(), Set.copyOf(allIds).size());
    }

    private static List<String> placeNames(GraphPetriObject object) {
        List<String> names = new ArrayList<>();
        for (int i = 0; i < object.getPlaceCount(); i++) {
            names.add(object.getPlaceName(i));
        }
        return names;
    }

    private static List<String> placeIds(GraphPetriObject object) {
        List<String> ids = new ArrayList<>();
        object.getGraphNet().getGraphPetriPlaceList()
                .forEach(place -> ids.add(place.getPetriPlace().getId()));
        return ids;
    }

    private static List<Integer> placeMarkings(GraphPetriObject object) {
        List<Integer> markings = new ArrayList<>();
        object.getGraphNet().getGraphPetriPlaceList()
                .forEach(place -> markings.add(place.getPetriPlace().getMark()));
        return markings;
    }

    private static List<String> transitionNames(GraphPetriObject object) {
        List<String> names = new ArrayList<>();
        for (int i = 0; i < object.getTransitionCount(); i++) {
            names.add(object.getTransitionName(i));
        }
        return names;
    }

    private static int arcCount(GraphPetriObject object) {
        return object.getGraphNet().getGraphArcInList().size()
                + object.getGraphNet().getGraphArcOutList().size();
    }

    /** Links are compared as a set: the two dialects state them in a different order. */
    private static List<String> sortedLinks(GraphPetriObjModel model) {
        List<String> described = new ArrayList<>();
        for (PetriObjLink link : model.getLinks()) {
            described.add(link.getType() + " " + link + " informational=" + link.isInformational());
        }
        Collections.sort(described);
        return described;
    }

    private static PetriObjLink only(GraphPetriObjModel model, PetriObjLinkType type) {
        PetriObjLink found = null;
        for (PetriObjLink link : model.getLinks()) {
            if (link.getType() == type) {
                if (found != null) {
                    fail("expected exactly one link of type " + type + ", found " + found + " and " + link);
                }
                found = link;
            }
        }
        assertNotNull("expected a link of type " + type, found);
        return found;
    }

    private static int countOfType(GraphPetriObjModel model, PetriObjLinkType type) {
        int count = 0;
        for (PetriObjLink link : model.getLinks()) {
            if (link.getType() == type) {
                count++;
            }
        }
        return count;
    }
}

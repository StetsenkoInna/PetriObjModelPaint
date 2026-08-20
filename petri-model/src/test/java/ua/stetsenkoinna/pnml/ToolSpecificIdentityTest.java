package ua.stetsenkoinna.pnml;

import org.junit.After;
import org.junit.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
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
import ua.stetsenkoinna.petriobj.PetriP;
import ua.stetsenkoinna.petriobj.PetriT;

import javax.xml.parsers.DocumentBuilderFactory;
import java.awt.Point;
import java.awt.geom.Point2D;
import java.io.File;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * The two tool identities a document can carry, and what each of them costs a reader.
 *
 * <p>This project and the web application that shares this PNML dialect each write only their
 * own identity now: every block this project writes is {@link PnmlConstants#TOOL_PETRI_OBJ_MODEL},
 * stating this project's own release. These tests pin that half of the bargain: nothing written
 * here states the web application's name or version any more.
 *
 * <p>The other half is the reader's: {@link XmlHelper#toolSpecificBlocks} still prefers this
 * project's own blocks but falls back to {@link PnmlConstants#TOOL_PETRI_NET_SIM} ones, which
 * is what lets a document the web application wrote open here at all. That fallback is
 * exercised against a real, hand-authored fixture carrying only the other tool's identity, see
 * {@link #aDocumentCarryingOnlyTheOtherToolsIdentityImportsFully}.
 */
public class ToolSpecificIdentityTest {

    private final File tempFile = new File("target/test-classes/tool_specific_identity_junit.pnml");

    @After
    public void tearDown() {
        if (tempFile.exists()) {
            tempFile.delete();
        }
    }

    // ---------------------------------------------------------------- writing

    /** Every tool-specific block a writer produces carries this project's own identity alone. */
    @Test
    public void everyToolSpecificBlockStatesThisProjectsOwnIdentityAlone() throws Exception {
        assertOnlyOwnIdentity(parse(new PnmlModelGenerator().generateXml(twoObjectModel())));

        new PnmlGenerator().generate(plainNet(), tempFile, null);
        assertOnlyOwnIdentity(parse(Files.readString(tempFile.toPath(), StandardCharsets.UTF_8)));
    }

    /**
     * The version states a release, so the constant is only right while it equals the version
     * this project's own pom states. It is read from the file, so the two cannot drift.
     */
    @Test
    public void theOwnReleaseIsTheOneStatedByThePom() throws Exception {
        assertEquals("PnmlConstants.TOOL_VERSION_PETRI_OBJ_MODEL has drifted from the release "
                        + "stated in pom.xml",
                pomVersion(), PnmlConstants.TOOL_VERSION_PETRI_OBJ_MODEL);
    }

    // ---------------------------------------------------------------- the reader's choice

    /**
     * Which block a reader takes when an element carries both, asked where the answer is
     * visible. Nothing written here carries both any more, so the disagreement is injected by
     * hand: exactly the shape a document from a third tool sharing this dialect could still put
     * in front of this reader.
     */
    @Test
    public void ownIdentityWinsWhenTheTwoBlocksDisagree() throws Exception {
        Document document = parse(new PnmlModelGenerator().generateXml(twoObjectModel()));
        Element page = (Element) document.getElementsByTagName(PnmlConstants.ELEMENT_PAGE).item(0);
        Element own = ownIdentityBlock(page);
        String written = markerName(own);

        Element other = (Element) own.cloneNode(true);
        other.setAttribute(PnmlConstants.ATTR_TOOL, PnmlConstants.TOOL_PETRI_NET_SIM);
        markerOf(other).setAttribute(PnmlConstants.ATTR_NAME, "written by the other tool");
        page.insertBefore(other, own.getNextSibling());

        List<Element> chosen = XmlHelper.toolSpecificBlocks(page);

        assertEquals("one identity's blocks, never a mix", 1, chosen.size());
        assertEquals(PnmlConstants.TOOL_PETRI_OBJ_MODEL,
                chosen.get(0).getAttribute(PnmlConstants.ATTR_TOOL));
        assertEquals("the object's name comes from this tool's own block",
                written, markerName(chosen.get(0)));
    }

    /** With no block of its own, the reader takes the other tool's rather than nothing. */
    @Test
    public void theOtherIdentityIsTheFallback() throws Exception {
        Document document = parse(new PnmlModelGenerator().generateXml(twoObjectModel()));
        Element page = (Element) document.getElementsByTagName(PnmlConstants.ELEMENT_PAGE).item(0);
        ownIdentityBlock(page).setAttribute(PnmlConstants.ATTR_TOOL, PnmlConstants.TOOL_PETRI_NET_SIM);

        List<Element> chosen = XmlHelper.toolSpecificBlocks(page);

        assertEquals(1, chosen.size());
        assertEquals(PnmlConstants.TOOL_PETRI_NET_SIM,
                chosen.get(0).getAttribute(PnmlConstants.ATTR_TOOL));
    }

    /** A block belonging to neither tool is not an answer at all. */
    @Test
    public void aThirdToolsBlockIsIgnored() throws Exception {
        Document document = parse(new PnmlModelGenerator().generateXml(twoObjectModel()));
        Element page = (Element) document.getElementsByTagName(PnmlConstants.ELEMENT_PAGE).item(0);
        Element stranger = document.createElement(PnmlConstants.ELEMENT_TOOLSPECIFIC);
        stranger.setAttribute(PnmlConstants.ATTR_TOOL, "SomeOtherEditor");
        stranger.setAttribute(PnmlConstants.ATTR_VERSION, "9.9");
        page.insertBefore(stranger, page.getFirstChild());

        for (Element block : XmlHelper.toolSpecificBlocks(page)) {
            assertEquals(PnmlConstants.TOOL_PETRI_OBJ_MODEL,
                    block.getAttribute(PnmlConstants.ATTR_TOOL));
        }
    }

    private static Element markerOf(Element toolspecific) {
        return XmlHelper.firstDirectChild(toolspecific, PnmlConstants.ELEMENT_PETRI_OBJECT);
    }

    private static String markerName(Element toolspecific) {
        return markerOf(toolspecific).getAttribute(PnmlConstants.ATTR_NAME);
    }

    private static Element ownIdentityBlock(Element scope) {
        for (Element block : XmlHelper.directChildren(scope, PnmlConstants.ELEMENT_TOOLSPECIFIC)) {
            if (PnmlConstants.TOOL_PETRI_OBJ_MODEL.equals(block.getAttribute(PnmlConstants.ATTR_TOOL))) {
                return block;
            }
        }
        throw new AssertionError("no " + PnmlConstants.TOOL_PETRI_OBJ_MODEL
                + " block on <" + scope.getNodeName() + ">");
    }

    // ---------------------------------------------------------------- the reader's fallback

    private static final String OTHER_TOOL_ONLY = "/pnml/composed_other_tool_only.pnml";

    /**
     * A document carrying only the web application's identity, {@code tool="PetriNetSim"},
     * imports fully: everything {@link XmlHelper#toolSpecificBlocks}'s fallback has to carry
     * across on its own, with no {@link PnmlConstants#TOOL_PETRI_OBJ_MODEL} block anywhere to
     * fall back from. See the fixture's own header for exactly what it exercises: the
     * {@code <petriObject>} marker, a reference node's {@code <referenceRole>}, a transition's
     * timed parameters, an arc's {@code <arcType>}, and the tool-specific {@code <coordinates>}
     * fallback for a place with no standard position at all.
     */
    @Test
    public void aDocumentCarryingOnlyTheOtherToolsIdentityImportsFully() throws Exception {
        String xml = Files.readString(
                Paths.get(getClass().getResource(OTHER_TOOL_ONLY).toURI()), StandardCharsets.UTF_8);
        assertFalse("the fixture is the point: it names one tool only",
                xml.contains("\"" + PnmlConstants.TOOL_PETRI_OBJ_MODEL + "\""));

        PnmlModelParser parser = new PnmlModelParser();
        GraphPetriObjModel model = parser.parseXml(xml);
        assertEquals(List.of(), parser.getWarnings());
        assertEquals(2, model.getObjectCount());

        // The petriObject marker: name, priority and canvas position of each object.
        GraphPetriObject alpha = model.getObject(0);
        assertEquals("Alpha", alpha.getName());
        assertEquals(2, alpha.getPriority());
        assertEquals(new Point(10, 20), alpha.getPosition());
        assertEquals("Beta", model.getObject(1).getName());

        // referenceRole: "Shared" is a declared fusion, so it stays a place slot of Alpha, and
        // it is what the resulting link resolves to Beta's "Target".
        assertEquals(List.of("Pool", "Shared"), placeNames(alpha));
        assertEquals(1, model.getLinks().size());
        PetriObjLink fusion = model.getLinks().get(0);
        assertEquals(PetriObjLinkType.PLACE_FUSION, fusion.getType());
        assertEquals(0, fusion.getSourceObject());
        assertEquals(1, fusion.getSourceElement());
        assertEquals(1, fusion.getTargetObject());
        assertEquals(0, fusion.getTargetElement());

        // Timed transition parameters.
        PetriT run = Arrays.stream(alpha.getGraphNet().getPetriNet().getListT())
                .filter(t -> "Run".equals(t.getName()))
                .findFirst().orElseThrow(() -> new AssertionError("transition Run not found"));
        assertEquals(1.5, run.getParameter(), 0.0);
        assertEquals("det", run.getDistribution());
        assertEquals(3, run.getPriority());

        // arcType: the flat dialect's inhibitor marker reads as an informational arc.
        assertTrue("arcType maps to the informational arc flag",
                alpha.getGraphNet().getGraphArcInList().get(0).getArcIn().getIsInf());

        // The tool-specific <coordinates> fallback: "Pool" carries no standard <graphics> at all.
        Point2D poolCenter = alpha.getGraphNet().getGraphPetriPlaceList().stream()
                .filter(place -> "alpha_pool".equals(place.getPetriPlace().getId()))
                .findFirst().orElseThrow(() -> new AssertionError("place alpha_pool not found"))
                .getGraphElementCenter();
        assertEquals(15.0, poolCenter.getX(), 0.0);
        assertEquals(25.0, poolCenter.getY(), 0.0);
    }

    // ---------------------------------------------------------------- round trip

    /** Generate, parse, and the model that comes back writes the same document again. */
    @Test
    public void aComposedModelSurvivesTheRoundTrip() throws Exception {
        String xml = new PnmlModelGenerator().generateXml(twoObjectModel());
        GraphPetriObjModel restored = new PnmlModelParser().parseXml(xml);

        assertEquals("QueueingSystem", restored.getName());
        assertEquals(2, restored.getObjectCount());
        assertEquals(2, restored.getLinks().size());
        assertEquals(xml, new PnmlModelGenerator().generateXml(restored));
    }

    /** The same for a plain net, down to the properties its blocks carry. */
    @Test
    public void aPlainNetSurvivesTheRoundTrip() throws Exception {
        PetriNet original = plainNet();
        new PnmlGenerator().generate(original, tempFile, null);
        PetriNet restored = new PnmlParser().parse(tempFile);

        assertEquals(summary(original), summary(restored));
    }

    // ---------------------------------------------------------------- fixtures

    /**
     * A net that exercises every kind of block a plain document carries: a place with a
     * marking parameter, a transition with a priority, a probability and a distribution, and
     * an informational arc.
     */
    private static PetriNet plainNet() throws Exception {
        resetCounters();
        ArrayList<PetriP> places = new ArrayList<>();
        places.add(new PetriP("p0", "Pool", 5));
        places.add(new PetriP("p1", "Done", 0));
        places.add(new PetriP("p2", "Gate", 1));
        places.get(2).setMarkParam("gateSize");
        ArrayList<PetriT> transitions = new ArrayList<>();
        transitions.add(new PetriT("t0", "Serve", 2.5));
        transitions.getFirst().setPriority(3);
        transitions.getFirst().setProbability(0.75);
        transitions.getFirst().setDistribution("exp", 2.5);
        ArrayList<ArcIn> arcsIn = new ArrayList<>();
        arcsIn.add(new ArcIn(places.getFirst(), transitions.getFirst(), 2));
        arcsIn.add(new ArcIn(places.get(2), transitions.getFirst(), 1, true));
        ArrayList<ArcOut> arcsOut = new ArrayList<>();
        arcsOut.add(new ArcOut(transitions.getFirst(), places.get(1), 1));
        return new PetriNet("Server", places, transitions, arcsIn, arcsOut);
    }

    /** Builds {@code P0 -> T0 -> P1} as a drawing, the smallest net an object can hold. */
    private static GraphPetriNet chainNet(String name, int startTokens) throws Exception {
        resetCounters();
        ArrayList<PetriP> places = new ArrayList<>();
        places.add(new PetriP("P0", startTokens));
        places.add(new PetriP("P1", 0));
        ArrayList<PetriT> transitions = new ArrayList<>();
        transitions.add(new PetriT("T0", 1.0));
        ArrayList<ArcIn> arcsIn = new ArrayList<>();
        arcsIn.add(new ArcIn(places.getFirst(), transitions.getFirst(), 1));
        ArrayList<ArcOut> arcsOut = new ArrayList<>();
        arcsOut.add(new ArcOut(transitions.getFirst(), places.get(1), 1));
        PetriNet net = new PetriNet(name, places, transitions, arcsIn, arcsOut);
        return GraphNetBuilder.build(net, Collections.emptyMap(), Collections.emptyMap(), null);
    }

    /** Two objects and two links: blocks land on pages, on elements and on the net. */
    private static GraphPetriObjModel twoObjectModel() throws Exception {
        GraphPetriObjModel model = new GraphPetriObjModel("QueueingSystem");
        GraphPetriObject generator = new GraphPetriObject("Generator", chainNet("Generator", 4));
        generator.setPosition(new Point(40, 60));
        model.addObject(generator);

        GraphPetriObject server = new GraphPetriObject("Server", chainNet("Server", 0));
        server.setPriority(3);
        server.setPosition(new Point(260, 60));
        model.addObject(server);

        model.addLink(PetriObjLink.placeFusion(0, 1, 1, 0));
        model.addLink(PetriObjLink.transitionToPlace(1, 0, 0, 0, 1));
        return model;
    }

    private static void resetCounters() {
        PetriP.initNext();
        PetriT.initNext();
        ArcIn.initNext();
        ArcOut.initNext();
    }

    // ---------------------------------------------------------------- helpers

    private static Document parse(String xml) throws Exception {
        return DocumentBuilderFactory.newInstance().newDocumentBuilder()
                .parse(new InputSource(new StringReader(xml)));
    }

    /** @return every tool-specific block of the document, in document order */
    private static List<Element> allBlocks(Document document) {
        List<Element> blocks = new ArrayList<>();
        NodeList nodes = document.getElementsByTagName(PnmlConstants.ELEMENT_TOOLSPECIFIC);
        for (int i = 0; i < nodes.getLength(); i++) {
            blocks.add((Element) nodes.item(i));
        }
        return blocks;
    }

    /** Asserts every tool-specific block of the document states this project's own identity. */
    private static void assertOnlyOwnIdentity(Document document) {
        List<Element> blocks = allBlocks(document);
        assertTrue("the document should carry blocks at all", !blocks.isEmpty());
        for (Element block : blocks) {
            assertEquals(PnmlConstants.TOOL_PETRI_OBJ_MODEL, block.getAttribute(PnmlConstants.ATTR_TOOL));
            assertEquals(PnmlConstants.TOOL_VERSION_PETRI_OBJ_MODEL,
                    block.getAttribute(PnmlConstants.ATTR_VERSION));
        }
    }

    private static List<String> placeNames(GraphPetriObject object) {
        List<String> names = new ArrayList<>();
        for (int i = 0; i < object.getPlaceCount(); i++) {
            names.add(object.getPlaceName(i));
        }
        return names;
    }

    /**
     * @return everything about a plain net that a tool-specific block can carry, addressed by
     *         name so that it does not depend on the shared element counters
     */
    private static String summary(PetriNet net) {
        StringBuilder text = new StringBuilder(net.getName());
        for (PetriP place : net.getListP()) {
            text.append("\nplace ").append(place.getName())
                    .append(" mark=").append(place.getMark())
                    .append(" param=").append(place.getMarkParamName());
        }
        for (PetriT transition : net.getListT()) {
            text.append("\ntransition ").append(transition.getName())
                    .append(" delay=").append(transition.getParameter())
                    .append(" deviation=").append(transition.getParamDeviation())
                    .append(" priority=").append(transition.getPriority())
                    .append(" probability=").append(transition.getProbability())
                    .append(" distribution=").append(transition.getDistribution());
        }
        for (ArcIn arc : net.getArcIn()) {
            text.append("\narcIn ").append(placeName(net, arc.getNumP()))
                    .append(" -> ").append(transitionName(net, arc.getNumT()))
                    .append(" quantity=").append(arc.getQuantity())
                    .append(" informational=").append(arc.getIsInf());
        }
        for (ArcOut arc : net.getArcOut()) {
            text.append("\narcOut ").append(transitionName(net, arc.getNumT()))
                    .append(" -> ").append(placeName(net, arc.getNumP()))
                    .append(" quantity=").append(arc.getQuantity());
        }
        return text.toString();
    }

    private static String placeName(PetriNet net, int number) {
        for (PetriP place : net.getListP()) {
            if (place.getNumber() == number) {
                return place.getName();
            }
        }
        return "?";
    }

    private static String transitionName(PetriNet net, int number) {
        for (PetriT transition : net.getListT()) {
            if (transition.getNumber() == number) {
                return transition.getName();
            }
        }
        return "?";
    }

    /**
     * @return the release this project states, taken from the pom the tests run against: the
     *         module pom names it as its parent's version, the root pom as its own
     */
    private static String pomVersion() throws Exception {
        Path pom = Paths.get("pom.xml").toAbsolutePath().normalize();
        assertTrue("expected to find " + pom, Files.exists(pom));

        Element project = DocumentBuilderFactory.newInstance().newDocumentBuilder()
                .parse(pom.toFile()).getDocumentElement();
        Element version = XmlHelper.firstDirectChild(project, "version");
        if (version == null) {
            Element parent = XmlHelper.firstDirectChild(project, "parent");
            assertNotNull("the pom states no version of its own and has no parent", parent);
            version = XmlHelper.firstDirectChild(parent, "version");
        }
        assertNotNull("the pom states no version", version);
        return version.getTextContent().trim();
    }
}

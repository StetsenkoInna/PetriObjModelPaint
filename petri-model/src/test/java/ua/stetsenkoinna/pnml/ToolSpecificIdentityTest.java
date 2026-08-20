package ua.stetsenkoinna.pnml;

import org.junit.After;
import org.junit.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
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
import ua.stetsenkoinna.petriobj.PetriP;
import ua.stetsenkoinna.petriobj.PetriT;

import javax.xml.parsers.DocumentBuilderFactory;
import java.awt.Point;
import java.io.File;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * The two tool identities a document carries, and what each of them costs a reader.
 *
 * <p>This project and the web application that shares this PNML dialect both read and write
 * the same tool-specific vocabulary. Every document written here now states it twice, once
 * under each tool name, so that a file written by either tool opens in both. These tests pin
 * the two halves of that bargain: the writer emits the pair, in that order, with the same
 * children; and the reader accepts a document that carries either one alone, which is what
 * every file saved before the second identity existed looks like.
 */
public class ToolSpecificIdentityTest {

    /** A legacy document: only this project's blocks, and the version stamps of the day. */
    private static final String LEGACY = "/pnml/composed_legacy_v20.pnml";

    private final File tempFile = new File("target/test-classes/tool_specific_identity_junit.pnml");

    @After
    public void tearDown() {
        if (tempFile.exists()) {
            tempFile.delete();
        }
    }

    // ---------------------------------------------------------------- writing

    /**
     * Every block of a written document is a pair: this project's block, then the web
     * application's, holding exactly the same children.
     */
    @Test
    public void everyToolSpecificBlockIsWrittenTwice() throws Exception {
        assertPairedEverywhere(parse(new PnmlModelGenerator().generateXml(twoObjectModel())));

        new PnmlGenerator().generate(plainNet(), tempFile, null);
        assertPairedEverywhere(parse(Files.readString(tempFile.toPath(), StandardCharsets.UTF_8)));
    }

    /** Both halves of every pair state the release their vocabulary belongs to. */
    @Test
    public void eachBlockStatesTheReleaseOfItsOwnTool() throws Exception {
        Document document = parse(new PnmlModelGenerator().generateXml(twoObjectModel()));

        int blocks = 0;
        for (Element block : allBlocks(document)) {
            blocks++;
            String tool = block.getAttribute(PnmlConstants.ATTR_TOOL);
            String version = block.getAttribute(PnmlConstants.ATTR_VERSION);
            if (PnmlConstants.TOOL_PETRI_OBJ_MODEL.equals(tool)) {
                // Not the literal: theOwnReleaseIsTheOneStatedByThePom pins the constant to
                // the pom, so repeating the string here would only break the next bump.
                assertEquals(PnmlConstants.TOOL_VERSION_PETRI_OBJ_MODEL, version);
            } else {
                assertEquals(PnmlConstants.TOOL_PETRI_NET_SIM, tool);
                assertEquals(PnmlConstants.TOOL_VERSION_PETRI_NET_SIM, version);
            }
        }
        assertTrue("the document should carry blocks at all", blocks > 0);
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

    /** Writing the pair a second time onto the same document must not make it a triple. */
    @Test
    public void mirroringAnAlreadyMirroredDocumentChangesNothing() throws Exception {
        Document document = parse(new PnmlModelGenerator().generateXml(twoObjectModel()));
        int blocks = allBlocks(document).size();

        XmlHelper.mirrorToolSpecificBlocks(document);

        assertEquals(blocks, allBlocks(document).size());
        assertPairedEverywhere(document);
    }

    // ---------------------------------------------------------------- reading

    /**
     * A document that states only this project's identity, which is every file saved before
     * the second one existed, describes exactly the model the paired document describes.
     */
    @Test
    public void aDocumentCarryingOnlyThisToolParsesUnchanged() throws Exception {
        String paired = new PnmlModelGenerator().generateXml(twoObjectModel());
        String alone = without(paired, PnmlConstants.TOOL_PETRI_NET_SIM);

        assertFalse(alone.contains(PnmlConstants.TOOL_PETRI_NET_SIM));
        assertEquals(reExport(paired), reExport(alone));
    }

    /** And so does one that states only the other tool's identity. */
    @Test
    public void aDocumentCarryingOnlyTheOtherToolParses() throws Exception {
        String paired = new PnmlModelGenerator().generateXml(twoObjectModel());
        String alone = without(paired, PnmlConstants.TOOL_PETRI_OBJ_MODEL);

        assertFalse(alone.contains("\"" + PnmlConstants.TOOL_PETRI_OBJ_MODEL + "\""));
        assertEquals(reExport(paired), reExport(alone));
    }

    /**
     * The same for a plain net, whose blocks carry the coordinates, the marking parameter and
     * the informational flag rather than the object metadata.
     */
    @Test
    public void aPlainNetIsReadTheSameFromEitherIdentityAlone() throws Exception {
        new PnmlGenerator().generate(plainNet(), tempFile, null);
        String paired = Files.readString(tempFile.toPath(), StandardCharsets.UTF_8);

        String expected = summary(new PnmlParser().parseXml(paired));
        assertEquals(expected,
                summary(new PnmlParser().parseXml(without(paired, PnmlConstants.TOOL_PETRI_NET_SIM))));
        assertEquals(expected,
                summary(new PnmlParser().parseXml(without(paired, PnmlConstants.TOOL_PETRI_OBJ_MODEL))));
    }

    /**
     * A file written before this change, pinned verbatim in the test resources, still parses
     * into what it always described: giving its blocks their twins changes not one thing
     * about the model it yields.
     */
    @Test
    public void aFileSavedBeforeTheSecondIdentityStillParses() throws Exception {
        String legacy = Files.readString(
                Paths.get(getClass().getResource(LEGACY).toURI()), StandardCharsets.UTF_8);
        assertFalse("the fixture is the point: it names one tool only",
                legacy.contains(PnmlConstants.TOOL_PETRI_NET_SIM));

        Document mirrored = parse(legacy);
        XmlHelper.mirrorToolSpecificBlocks(mirrored);

        assertEquals(reExport(legacy), reExport(PnmlGenerator.toXml(mirrored)));
    }

    // ---------------------------------------------------------------- the reader's choice

    /**
     * Which block a reader takes, asked where the answer is visible.
     *
     * <p>Nothing writes blocks that disagree: the twin is a clone. That is exactly why the
     * preference needs a document built by hand, since with identical children every reading
     * looks right and the rule underneath is never exercised.
     */
    @Test
    public void ownIdentityWinsWhenTheTwoBlocksDisagree() throws Exception {
        Document document = parse(new PnmlModelGenerator().generateXml(twoObjectModel()));
        Element page = (Element) document.getElementsByTagName(PnmlConstants.ELEMENT_PAGE).item(0);
        String written = markerName(ownIdentityBlock(page));
        markerOf(otherIdentityBlock(page))
                .setAttribute(PnmlConstants.ATTR_NAME, "written by the other tool");

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
        Element own = ownIdentityBlock(page);
        own.getParentNode().removeChild(own);

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
        return identityBlock(scope, PnmlConstants.TOOL_PETRI_OBJ_MODEL);
    }

    private static Element otherIdentityBlock(Element scope) {
        return identityBlock(scope, PnmlConstants.TOOL_PETRI_NET_SIM);
    }

    private static Element identityBlock(Element scope, String tool) {
        for (Element block : XmlHelper.directChildren(scope, PnmlConstants.ELEMENT_TOOLSPECIFIC)) {
            if (tool.equals(block.getAttribute(PnmlConstants.ATTR_TOOL))) {
                return block;
            }
        }
        throw new AssertionError("no " + tool + " block on <" + scope.getNodeName() + ">");
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

    /**
     * Asserts that wherever the document carries tool-specific blocks they come two by two:
     * this project's first, the web application's second, with the same children.
     */
    private static void assertPairedEverywhere(Document document) {
        int pairs = 0;
        for (Element block : allBlocks(document)) {
            if (!PnmlConstants.TOOL_PETRI_OBJ_MODEL.equals(block.getAttribute(PnmlConstants.ATTR_TOOL))) {
                continue;
            }
            Element twin = nextElement(block);
            assertNotNull("the block of " + ownerOf(block) + " has no twin after it", twin);
            assertEquals("the twin has to be the very next element",
                    PnmlConstants.ELEMENT_TOOLSPECIFIC, twin.getNodeName());
            assertEquals(PnmlConstants.TOOL_PETRI_NET_SIM, twin.getAttribute(PnmlConstants.ATTR_TOOL));
            assertEquals("the two blocks of " + ownerOf(block) + " must say the same thing",
                    children(block), children(twin));
            pairs++;
        }
        assertTrue("the document should carry blocks at all", pairs > 0);
        assertEquals("no block may be left unpaired", 2 * pairs, allBlocks(document).size());
    }

    private static Element nextElement(Node node) {
        for (Node next = node.getNextSibling(); next != null; next = next.getNextSibling()) {
            if (next.getNodeType() == Node.ELEMENT_NODE) {
                return (Element) next;
            }
        }
        return null;
    }

    /** @return what the block belongs to, for a message that says where the failure is */
    private static String ownerOf(Element block) {
        Element parent = (Element) block.getParentNode();
        return parent.getNodeName() + " " + parent.getAttribute(PnmlConstants.ATTR_ID);
    }

    /**
     * @return the children of a block as text, so that two blocks can be compared without the
     *         indentation the serialiser inserted between them
     */
    private static String children(Element block) {
        StringBuilder text = new StringBuilder();
        for (Element child : XmlHelper.directChildren(block)) {
            text.append(render(child));
        }
        return text.toString();
    }

    private static String render(Element element) {
        StringBuilder text = new StringBuilder(element.getNodeName()).append('{');
        NamedNodeMap attributes = element.getAttributes();
        for (int i = 0; i < attributes.getLength(); i++) {
            Node attribute = attributes.item(i);
            text.append(attribute.getNodeName()).append('=').append(attribute.getNodeValue()).append(';');
        }
        List<Element> children = XmlHelper.directChildren(element);
        if (children.isEmpty()) {
            text.append(element.getTextContent().trim());
        } else {
            for (Element child : children) {
                text.append(render(child));
            }
        }
        return text.append('}').toString();
    }

    /** @return the document with every block of that tool removed */
    private static String without(String xml, String tool) throws Exception {
        Document document = parse(xml);
        for (Element block : allBlocks(document)) {
            if (tool.equals(block.getAttribute(PnmlConstants.ATTR_TOOL))) {
                block.getParentNode().removeChild(block);
            }
        }
        return PnmlGenerator.toXml(document);
    }

    /**
     * @return the document a composed model writes after being read from the given one, the
     *         shortest statement of "these two documents describe the same model"
     */
    private static String reExport(String xml) throws Exception {
        return new PnmlModelGenerator().generateXml(new PnmlModelParser().parseXml(xml));
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

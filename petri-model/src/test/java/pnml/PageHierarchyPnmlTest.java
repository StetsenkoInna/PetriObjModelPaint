package pnml;

import org.junit.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import ua.stetsenkoinna.graphnet.GraphArcFactory;
import ua.stetsenkoinna.graphnet.GraphCanvasModel;
import ua.stetsenkoinna.graphnet.GraphNetBuilder;
import ua.stetsenkoinna.graphnet.GraphObjectFrame;
import ua.stetsenkoinna.graphnet.GraphPetriNet;
import ua.stetsenkoinna.graphnet.GraphPetriObjModel;
import ua.stetsenkoinna.graphnet.GraphPetriObject;
import ua.stetsenkoinna.graphnet.GraphPetriPlace;
import ua.stetsenkoinna.graphnet.GraphPetriTransition;
import ua.stetsenkoinna.petriobj.ArcIn;
import ua.stetsenkoinna.petriobj.ArcOut;
import ua.stetsenkoinna.petriobj.PetriNet;
import ua.stetsenkoinna.petriobj.PetriObjLink;
import ua.stetsenkoinna.petriobj.PetriObjLinkType;
import ua.stetsenkoinna.petriobj.PetriP;
import ua.stetsenkoinna.petriobj.PetriT;
import ua.stetsenkoinna.pnml.PnmlModelGenerator;
import ua.stetsenkoinna.pnml.PnmlModelParser;

import javax.xml.parsers.DocumentBuilderFactory;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.geom.Point2D;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * The hierarchy between Petri-objects, expressed the way ISO/IEC 15909-2 expresses a
 * hierarchy of pages: a child object's {@code <page>} written inside its parent's.
 *
 * <p>Before this, every page was a flat sibling under {@code <net>} and the nesting lived in
 * a tool-specific {@code parentObject} attribute, which meant a reader that is neither of
 * the two tools sharing this dialect saw a pile of unrelated pages and lost the hierarchy
 * outright. The writer now states it in the standard's own terms and states it nowhere else,
 * and the reader takes it from there and nowhere else: the attribute is neither written nor
 * read, so a document that carries it opens with its objects flattened to the top level.
 */
public class PageHierarchyPnmlTest {

    private static final String NESTED = "/pnml/composed_nested_pages.pnml";
    private static final String FLAT = "/pnml/composed_legacy_parent_object.pnml";

    // ---------------------------------------------------------------- writing

    /**
     * The shape itself: three objects, each inside the one before it, are three pages each
     * inside the one before it.
     */
    @Test
    public void aChainOfNestedObjectsIsWrittenAsAPageInsideAPageInsideAPage() throws Exception {
        Document document = parseXml(new PnmlModelGenerator().generateXml(nestedModel()));

        assertEquals("only what has no parent is a page of the net itself",
                List.of("object0", "object4", "object5"), pageIds(netElement(document)));
        assertEquals("the child of the first object is written inside it",
                List.of("object1", "object3"), pageIds(pageById(document, "object0")));
        assertEquals("and its own child inside that",
                List.of("object2"), pageIds(pageById(document, "object1")));
        assertTrue("three deep is where this chain ends",
                pageIds(pageById(document, "object2")).isEmpty());
    }

    /**
     * The product owner's requirement in one line: an exported file says nothing
     * non-standard about where a page belongs.
     */
    @Test
    public void anExportedDocumentCarriesNoParentObjectAttribute() throws Exception {
        assertFalse(new PnmlModelGenerator().generateXml(nestedModel()).contains("parentObject"));
    }

    /**
     * A page still holds everything it always held, and holds it first: a reader walking a
     * page in document order meets its name, its metadata and its whole net before it meets
     * anything belonging to another object.
     */
    @Test
    public void aChildPageComesAfterEverythingItsParentPageHoldsOfItsOwn() throws Exception {
        Document document = parseXml(new PnmlModelGenerator().generateXml(nestedModel()));
        List<String> outer = tagNames(children(pageById(document, "object0"), null));

        assertEquals("name, tool-specific blocks, nodes, arcs, then the child pages",
                List.of("name", "toolspecific", "toolspecific",
                        "place", "place", "transition", "arc", "arc",
                        "page", "page"),
                outer);
    }

    /**
     * An object that belongs to nobody stays where it always was, and so does the page that
     * carries the elements the user drew outside every object.
     */
    @Test
    public void aSiblingObjectAndThePageOfTheFreeElementsStayDirectlyUnderTheNet() throws Exception {
        resetCounters();
        GraphCanvasModel canvas = new GraphCanvasModel("Workshop", new GraphPetriNet());
        GraphObjectFrame parent = new GraphObjectFrame("Parent", new Rectangle(0, 0, 400, 400));
        GraphObjectFrame child = new GraphObjectFrame("Child", new Rectangle(40, 60, 200, 200));
        GraphObjectFrame sibling = new GraphObjectFrame("Sibling", new Rectangle(500, 0, 300, 300));
        canvas.getFrames().add(parent);
        canvas.getFrames().add(child);
        canvas.getFrames().add(sibling);
        canvas.nest(child, parent);
        claimChain(canvas, parent, "Parent", 300, 320);
        claimChain(canvas, child, "Child", 60, 100);
        claimChain(canvas, sibling, "Sibling", 520, 60);
        // Drawn outside every frame, so it becomes the object of the free elements, which is
        // the last one and belongs to nobody.
        claimChain(canvas, null, "Loose", 900, 60);

        GraphPetriObjModel model = canvas.toObjModel();
        assertEquals(GraphCanvasModel.FREE_OBJECT_NAME, model.getObject(3).getName());

        Document document = parseXml(new PnmlModelGenerator().generateXml(model));
        assertEquals("the sibling and the free page are pages of the net",
                List.of("object0", "object2", "object3"), pageIds(netElement(document)));
        assertEquals("the nested object is a page of the object that encloses it",
                List.of("object1"), pageIds(pageById(document, "object0")));
    }

    /** What the writer wrote is what the reader reads: the same objects, nesting and links. */
    @Test
    public void aNestedModelSurvivesTheRoundTrip() throws Exception {
        GraphPetriObjModel written = nestedModel();
        GraphPetriObjModel read =
                new PnmlModelParser().parseXml(new PnmlModelGenerator().generateXml(written));

        assertEquals(written.getObjectCount(), read.getObjectCount());
        for (int index = 0; index < written.getObjectCount(); index++) {
            assertEquals("object " + index,
                    written.getObject(index).getName(), read.getObject(index).getName());
            assertEquals("the parent of object " + index,
                    written.getObject(index).getParentIndex(),
                    read.getObject(index).getParentIndex());
        }
        assertEquals(describeLinks(written), describeLinks(read));
    }

    /**
     * Nesting parts document order from object index: a child of the first object is written
     * before the second top-level one, whatever index it carries. Objects are addressed by
     * index everywhere, links included, so the reader orders the pages by the index each one
     * states rather than by where it happens to sit.
     */
    @Test
    public void anObjectKeepsItsIndexWhenNestingReordersThePages() throws Exception {
        GraphPetriObjModel model = new GraphPetriObjModel("Reordered");
        addObject(model, "First", -1, true);
        addObject(model, "Second", -1, true);
        addObject(model, "Nested", 0, true);
        model.addLink(PetriObjLink.transitionToPlace(1, 0, 2, 0, 3));

        String xml = new PnmlModelGenerator().generateXml(model);
        Document document = parseXml(xml);
        assertEquals(List.of("object0", "object1"), pageIds(netElement(document)));
        assertEquals("the third object is written second, inside the first",
                List.of("object2"), pageIds(pageById(document, "object0")));

        GraphPetriObjModel read = new PnmlModelParser().parseXml(xml);
        assertEquals(List.of("First", "Second", "Nested"), objectNames(read));
        assertEquals(List.of(-1, -1, 0), parentIndices(read));
        assertEquals("and the link still addresses the objects it was declared against",
                List.of("TRANSITION_TO_PLACE 1:0 -> 2:0 x3"), describeLinks(read));
    }

    // ---------------------------------------------------------------- reading

    /**
     * The reader takes the hierarchy from the document's own structure, and a page nested
     * inside another is a Petri-object of its own: its places and transitions belong to it,
     * never to the page enclosing it.
     */
    @Test
    public void theNestedFixtureStatesItsHierarchyByNestingAlone() throws Exception {
        GraphPetriObjModel model = parseFixture(NESTED);

        assertEquals(3, model.getObjectCount());
        assertEquals(List.of("Outer", "Middle", "Inner"), objectNames(model));
        assertEquals("every object's parent comes from where its page sits",
                List.of(-1, 0, 1), parentIndices(model));
        assertEquals("the enclosing object's net is what its own page holds, no more",
                List.of("OuterIn", "OuterOut"), placeNames(model.getObject(0)));
        assertEquals(1, model.getObject(0).getTransitionCount());
        assertEquals(List.of("MiddleIn", "MiddleOut"), placeNames(model.getObject(1)));
        assertEquals(List.of("InnerIn", "InnerOut"), placeNames(model.getObject(2)));
        assertEquals(2, model.getLinks().size());
    }

    /**
     * The decision, pinned against a literal document in the shape this tool wrote while the
     * pages were flat siblings. The page structure is the only statement of a hierarchy this
     * reader knows, so a document whose pages are flat siblings is a set of top-level objects
     * however its tool-specific block reads. Everything else the document carries survives:
     * only the nest is lost, and losing it is what was decided.
     */
    @Test
    public void aFlatDocumentReadsAsTopLevelObjectsWhateverParentObjectClaims()
            throws Exception {
        assertTrue("the fixture has to carry the attribute for this to prove anything",
                fixture(FLAT).contains("parentObject"));

        GraphPetriObjModel flat = parseFixture(FLAT);
        GraphPetriObjModel nested = parseFixture(NESTED);

        assertEquals("what the attribute claims gives no object a parent",
                List.of(-1, -1, -1), parentIndices(flat));
        assertEquals("and the nested document is the one that still has the hierarchy",
                List.of(-1, 0, 1), parentIndices(nested));

        assertEquals(nested.getName(), flat.getName());
        assertEquals(nested.getObjectCount(), flat.getObjectCount());
        for (int index = 0; index < nested.getObjectCount(); index++) {
            GraphPetriObject expected = nested.getObject(index);
            GraphPetriObject actual = flat.getObject(index);
            assertEquals(expected.getName(), actual.getName());
            assertEquals(expected.getPriority(), actual.getPriority());
            assertEquals(expected.getPosition(), actual.getPosition());
            assertEquals(placeNames(expected), placeNames(actual));
            assertEquals(placeMarkings(expected), placeMarkings(actual));
            assertEquals(expected.getTransitionCount(), actual.getTransitionCount());
        }
        assertEquals("the links still address the objects the document names",
                describeLinks(nested), describeLinks(flat));
    }

    /**
     * The attribute is not a source of anything, not even where a document still carries one
     * and contradicts its own nesting. The pages say where every object belongs.
     */
    @Test
    public void aParentObjectAttributeIsIgnoredWhereADocumentStillCarriesOne() throws Exception {
        String contradictory = fixture(NESTED).replace(
                "<petriObject index=\"2\"", "<petriObject parentObject=\"0\" index=\"2\"");
        assertTrue("the fixture has to carry the attribute for this to prove anything",
                contradictory.contains("parentObject"));

        GraphPetriObjModel model = new PnmlModelParser().parseXml(contradictory);
        assertEquals("the innermost page is inside Middle, whatever the attribute claims",
                List.of(-1, 0, 1), parentIndices(model));
    }

    /**
     * A page nested inside another is a page of the document like any other, so a plain net
     * reader has to refuse it for the same reason it refuses flat pages: flattening the two
     * would merge nets that only a composed model can run.
     */
    @Test
    public void aPlainNetReaderRefusesANestedCompositionToo() throws Exception {
        try {
            new ua.stetsenkoinna.pnml.PnmlParser().parseXml(fixture(NESTED));
            org.junit.Assert.fail("three pages are three Petri-objects, however they are arranged");
        } catch (Exception expected) {
            assertTrue("the message should say how many objects it found, was: "
                            + expected.getMessage(),
                    expected.getMessage().contains("3 objects"));
        }
    }

    /**
     * What the writer's containment invariant is really protecting, checked on the shape that
     * made it worth restating: a node on a nested page is not a node of the page enclosing it,
     * so an arc drawn on one page must find both its ends among that page's OWN children.
     */
    @Test
    public void everyArcEndsOnThePageItIsDrawnOn() throws Exception {
        Document document = parseXml(new PnmlModelGenerator().generateXml(nestedModel()));

        int arcsChecked = 0;
        for (Element page : allPages(netElement(document))) {
            Set<String> ownNodes = new HashSet<>();
            for (String tag : List.of("place", "transition", "referencePlace", "referenceTransition")) {
                for (Element node : directChildren(page, tag)) {
                    ownNodes.add(node.getAttribute("id"));
                }
            }
            for (Element arc : directChildren(page, "arc")) {
                arcsChecked++;
                assertTrue("arc " + arc.getAttribute("id") + " leaves page "
                                + page.getAttribute("id"),
                        ownNodes.contains(arc.getAttribute("source"))
                                && ownNodes.contains(arc.getAttribute("target")));
            }
        }
        assertTrue("the nested model should draw arcs at all", arcsChecked > 0);
    }

    private static List<Element> allPages(Element scope) {
        List<Element> found = new ArrayList<>();
        for (Element page : directChildren(scope, "page")) {
            found.add(page);
            found.addAll(allPages(page));
        }
        return found;
    }

    private static List<Element> directChildren(Element parent, String tagName) {
        List<Element> found = new ArrayList<>();
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            // The factory here is not namespace aware, so the tag name is the whole name.
            if (child instanceof Element element && tagName.equals(element.getTagName())) {
                found.add(element);
            }
        }
        return found;
    }

    // ---------------------------------------------------------------- fixtures

    /**
     * Six objects: a chain three deep, a second child of the outermost one, a sibling that
     * belongs to nobody, and an object with no geometry standing for the free elements.
     */
    private static GraphPetriObjModel nestedModel() throws Exception {
        GraphPetriObjModel model = new GraphPetriObjModel("Nested");
        addObject(model, "Outer", -1, true);
        addObject(model, "Middle", 0, true);
        addObject(model, "Inner", 1, true);
        addObject(model, "Cousin", 0, true);
        addObject(model, "Sibling", -1, true);
        addObject(model, "Free elements", -1, false);
        model.addLink(PetriObjLink.transitionToPlace(2, 0, 0, 0, 2));
        model.addLink(PetriObjLink.placeFusion(1, 1, 4, 0));
        return model;
    }

    private static void addObject(GraphPetriObjModel model, String name, int parent,
                                  boolean framed) throws Exception {
        GraphPetriObject object = new GraphPetriObject(name, chainNet(name));
        object.setParentIndex(parent);
        if (framed) {
            object.setPosition(new Point(20, 40));
            object.setSize(300, 200);
        }
        model.addObject(object);
    }

    /** Builds {@code P0 -> T0 -> P1}, the smallest net an object can be built around. */
    private static GraphPetriNet chainNet(String name) throws Exception {
        resetCounters();
        ArrayList<PetriP> places = new ArrayList<>();
        places.add(new PetriP("P0", 1));
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

    /**
     * Draws {@code P -> T -> P} on the canvas and, when there is a frame, gives it to that
     * frame.
     *
     * @param frame the object that owns the three elements, or {@code null} to leave them
     *        outside every object
     */
    private static void claimChain(GraphCanvasModel canvas, GraphObjectFrame frame,
                                   String prefix, int x, int y) {
        GraphPetriPlace in = place(canvas, prefix + "In", 1, x, y);
        GraphPetriTransition step = transition(canvas, prefix + "Step", x + 60, y);
        GraphPetriPlace out = place(canvas, prefix + "Out", 0, x + 120, y);
        canvas.getNet().getGraphArcInList().add(GraphArcFactory.inArc(in, step, 1, false));
        canvas.getNet().getGraphArcOutList().add(GraphArcFactory.outArc(step, out, 1));
        if (frame != null) {
            canvas.claim(frame, in);
            canvas.claim(frame, step);
            canvas.claim(frame, out);
        }
    }

    private static GraphPetriPlace place(GraphCanvasModel canvas, String name, int tokens,
                                         int x, int y) {
        GraphPetriPlace place = new GraphPetriPlace(new PetriP(name, tokens), nextId());
        place.setNewCoordinates(new Point2D.Double(x, y));
        canvas.getNet().getGraphPetriPlaceList().add(place);
        return place;
    }

    private static GraphPetriTransition transition(GraphCanvasModel canvas, String name,
                                                   int x, int y) {
        GraphPetriTransition transition = new GraphPetriTransition(new PetriT(name, 1.0), nextId());
        transition.setNewCoordinates(new Point2D.Double(x, y));
        canvas.getNet().getGraphPetriTransitionList().add(transition);
        return transition;
    }

    private static int idCounter = 1;

    private static int nextId() {
        return idCounter++;
    }

    private static void resetCounters() {
        PetriP.initNext();
        PetriT.initNext();
        ArcIn.initNext();
        ArcOut.initNext();
    }

    private static String fixture(String resource) throws Exception {
        return Files.readString(
                Paths.get(PageHierarchyPnmlTest.class.getResource(resource).toURI()),
                StandardCharsets.UTF_8);
    }

    private static GraphPetriObjModel parseFixture(String resource) throws Exception {
        return new PnmlModelParser().parseXml(fixture(resource));
    }

    // ---------------------------------------------------------------- helpers

    private static Document parseXml(String xml) throws Exception {
        return DocumentBuilderFactory.newInstance().newDocumentBuilder()
                .parse(new InputSource(new StringReader(xml)));
    }

    private static Element netElement(Document document) {
        Element net = (Element) document.getElementsByTagName("net").item(0);
        assertNotNull("the document has a net", net);
        return net;
    }

    private static Element pageById(Document document, String id) {
        NodeList pages = document.getElementsByTagName("page");
        for (int i = 0; i < pages.getLength(); i++) {
            Element page = (Element) pages.item(i);
            if (id.equals(page.getAttribute("id"))) {
                return page;
            }
        }
        throw new AssertionError("the document has no page " + id);
    }

    /** @return the ids of the pages written directly inside the given element */
    private static List<String> pageIds(Element parent) {
        List<String> ids = new ArrayList<>();
        for (Element page : children(parent, "page")) {
            ids.add(page.getAttribute("id"));
        }
        return ids;
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

    private static List<String> tagNames(List<Element> elements) {
        List<String> names = new ArrayList<>();
        elements.forEach(element -> names.add(element.getNodeName()));
        return names;
    }

    private static List<String> objectNames(GraphPetriObjModel model) {
        List<String> names = new ArrayList<>();
        for (int index = 0; index < model.getObjectCount(); index++) {
            names.add(model.getObject(index).getName());
        }
        return names;
    }

    private static List<Integer> parentIndices(GraphPetriObjModel model) {
        List<Integer> parents = new ArrayList<>();
        for (int index = 0; index < model.getObjectCount(); index++) {
            parents.add(model.getObject(index).getParentIndex());
        }
        return parents;
    }

    private static List<String> placeNames(GraphPetriObject object) {
        List<String> names = new ArrayList<>();
        for (int i = 0; i < object.getPlaceCount(); i++) {
            names.add(object.getPlaceName(i));
        }
        return names;
    }

    private static List<Integer> placeMarkings(GraphPetriObject object) {
        List<Integer> markings = new ArrayList<>();
        object.getGraphNet().getGraphPetriPlaceList()
                .forEach(place -> markings.add(place.getPetriPlace().getMark()));
        return markings;
    }

    /** @return the links as text, sorted, so that two models can be compared outright */
    private static List<String> describeLinks(GraphPetriObjModel model) {
        List<String> described = new ArrayList<>();
        for (PetriObjLink link : model.getLinks()) {
            described.add(link.getType() + " " + link.getSourceObject() + ":"
                    + link.getSourceElement() + " -> " + link.getTargetObject() + ":"
                    + link.getTargetElement()
                    + (link.getType() == PetriObjLinkType.PLACE_FUSION
                            ? "" : " x" + link.getQuantity()));
        }
        Collections.sort(described);
        return described;
    }
}

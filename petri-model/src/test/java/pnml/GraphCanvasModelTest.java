package pnml;

import org.junit.Test;
import ua.stetsenkoinna.graphnet.FramePort;
import ua.stetsenkoinna.graphnet.GraphArcFactory;
import ua.stetsenkoinna.graphnet.GraphCanvasModel;
import ua.stetsenkoinna.graphnet.GraphElement;
import ua.stetsenkoinna.graphnet.GraphObjectFrame;
import ua.stetsenkoinna.graphnet.GraphPetriNet;
import ua.stetsenkoinna.graphnet.GraphPetriObjModel;
import ua.stetsenkoinna.graphnet.GraphPetriPlace;
import ua.stetsenkoinna.graphnet.GraphPetriTransition;
import ua.stetsenkoinna.graphnet.GraphPlaceFusion;
import ua.stetsenkoinna.petriobj.ArcIn;
import ua.stetsenkoinna.petriobj.ArcOut;
import ua.stetsenkoinna.petriobj.PetriObjLinkType;
import ua.stetsenkoinna.petriobj.PetriObjModel;
import ua.stetsenkoinna.petriobj.PetriP;
import ua.stetsenkoinna.petriobj.PetriT;

import java.awt.Point;
import java.awt.Rectangle;
import java.awt.geom.Point2D;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

/**
 * Reading the canvas as a Petri-object model: which frame owns what, which arcs turn into
 * links, and what a canvas without frames means.
 */
public class GraphCanvasModelTest {

    /** Adds a place at the given point and returns it. */
    private static GraphPetriPlace place(GraphCanvasModel canvas, String name, int tokens, int x, int y) {
        GraphPetriPlace place = new GraphPetriPlace(new PetriP(name, tokens), nextId());
        place.setNewCoordinates(new Point2D.Double(x, y));
        canvas.getNet().getGraphPetriPlaceList().add(place);
        return place;
    }

    private static GraphPetriTransition transition(GraphCanvasModel canvas, String name, int x, int y) {
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

    /**
     * Two framed objects, each {@code P -> T -> P}, wired so that the first object's output
     * feeds the second one across the frame border.
     */
    private static GraphCanvasModel twoFramedObjects() {
        resetCounters();
        GraphCanvasModel canvas = new GraphCanvasModel("Pipeline", new GraphPetriNet());

        GraphObjectFrame source = new GraphObjectFrame("Source", new Rectangle(0, 0, 300, 300));
        source.setPriority(1);
        canvas.getFrames().add(source);
        GraphObjectFrame sink = new GraphObjectFrame("Sink", new Rectangle(400, 0, 300, 300));
        canvas.getFrames().add(sink);

        GraphPetriPlace p0 = place(canvas, "P0", 2, 60, 120);
        GraphPetriTransition t0 = transition(canvas, "T0", 160, 120);
        GraphPetriPlace p1 = place(canvas, "P1", 0, 250, 120);
        canvas.claim(source, p0);
        canvas.claim(source, t0);
        canvas.claim(source, p1);

        GraphPetriPlace p2 = place(canvas, "P2", 0, 460, 120);
        GraphPetriTransition t1 = transition(canvas, "T1", 560, 120);
        GraphPetriPlace p3 = place(canvas, "P3", 0, 660, 120);
        canvas.claim(sink, p2);
        canvas.claim(sink, t1);
        canvas.claim(sink, p3);

        canvas.getNet().getGraphArcInList().add(GraphArcFactory.inArc(p0, t0, 1, false));
        canvas.getNet().getGraphArcOutList().add(GraphArcFactory.outArc(t0, p1, 1));
        canvas.getNet().getGraphArcInList().add(GraphArcFactory.inArc(p2, t1, 1, false));
        canvas.getNet().getGraphArcOutList().add(GraphArcFactory.outArc(t1, p3, 1));

        // The link: the first object's transition feeds a place of the second one.
        canvas.getNet().getGraphArcOutList().add(GraphArcFactory.outArc(t0, p2, 2));
        return canvas;
    }

    @Test
    public void aFrameDecidesWhichObjectAnElementBelongsTo() {
        GraphCanvasModel canvas = twoFramedObjects();

        GraphPetriObjModel model = canvas.toObjModel();

        assertEquals(2, model.getObjectCount());
        assertEquals("Source", model.getObject(0).getName());
        assertEquals(1, model.getObject(0).getPriority());
        assertEquals(2, model.getObject(0).getPlaceCount());
        assertEquals(1, model.getObject(0).getTransitionCount());
        assertEquals("Sink", model.getObject(1).getName());
        assertEquals(2, model.getObject(1).getPlaceCount());
    }

    @Test
    public void anArcCrossingAFrameBorderBecomesALink() {
        GraphCanvasModel canvas = twoFramedObjects();

        GraphPetriObjModel model = canvas.toObjModel();

        assertEquals(1, model.getLinks().size());
        assertEquals(PetriObjLinkType.TRANSITION_TO_PLACE, model.getLinks().getFirst().getType());
        assertEquals(0, model.getLinks().getFirst().getSourceObject());
        assertEquals(1, model.getLinks().getFirst().getTargetObject());
        assertEquals(2, model.getLinks().getFirst().getQuantity());
    }

    /**
     * The other direction is not a link at all. A transition takes its input places from its
     * own Petri-object only, so a place of another object reaches it by being shared, and the
     * refusal has to say so rather than quietly build something else.
     */
    @Test
    public void anInputArcCrossingAFrameBorderIsRefused() {
        GraphCanvasModel canvas = twoFramedObjects();
        GraphPetriPlace outputOfSource = canvas.getNet().getGraphPetriPlaceList().get(1);
        GraphPetriTransition transitionOfSink = canvas.getNet().getGraphPetriTransitionList().get(1);
        canvas.getNet().getGraphArcInList().add(
                GraphArcFactory.inArc(outputOfSource, transitionOfSink, 1, true));

        IllegalArgumentException refused =
                assertThrows(IllegalArgumentException.class, canvas::toObjModel);

        String message = refused.getMessage();
        assertTrue("the message should name both elements, was: " + message,
                message.contains("'P1'") && message.contains("'T1'"));
        assertTrue("and both objects, was: " + message,
                message.contains("'Source'") && message.contains("'Sink'"));
        assertTrue("and the canonical alternative, was: " + message,
                message.contains("Share place") && message.contains("ordinary arc"));
    }

    /** And once the place is shared instead, the same model exports without complaint. */
    @Test
    public void theSharedPlaceFormOfThatArcExportsFine() {
        GraphCanvasModel canvas = twoFramedObjects();
        GraphPetriPlace outputOfSource = canvas.getNet().getGraphPetriPlaceList().get(1);
        GraphPetriPlace inputOfSink = canvas.getNet().getGraphPetriPlaceList().get(2);
        GraphPetriTransition transitionOfSink = canvas.getNet().getGraphPetriTransitionList().get(1);
        canvas.joinPlaces(inputOfSink, outputOfSource);
        canvas.getNet().getGraphArcInList().add(
                GraphArcFactory.inArc(inputOfSink, transitionOfSink, 1, true));

        GraphPetriObjModel model = canvas.toObjModel();

        assertTrue(model.getLinks().stream()
                .anyMatch(link -> link.getType() == PetriObjLinkType.PLACE_FUSION));
        assertTrue("the arc stays an ordinary arc of the object that owns the transition",
                model.getObject(1).getGraphNet().getGraphArcInList().stream()
                        .anyMatch(arc -> arc.getArcIn().getIsInf()));
    }

    @Test
    public void aSharedPlaceBecomesAFusionLink() {
        GraphCanvasModel canvas = twoFramedObjects();
        GraphPetriPlace outputOfSource = canvas.getNet().getGraphPetriPlaceList().get(1);
        GraphPetriPlace inputOfSink = canvas.getNet().getGraphPetriPlaceList().get(2);

        canvas.joinPlaces(inputOfSink, outputOfSource);

        GraphPetriObjModel model = canvas.toObjModel();
        assertTrue(model.getLinks().stream()
                .anyMatch(link -> link.getType() == PetriObjLinkType.PLACE_FUSION));
    }

    @Test
    public void joiningTwoFramedPlacesLeavesBothWhereTheyWereInsideTheirOwnObject() {
        GraphCanvasModel canvas = twoFramedObjects();
        GraphPetriPlace outputOfSource = canvas.getNet().getGraphPetriPlaceList().get(1);
        GraphPetriPlace inputOfSink = canvas.getNet().getGraphPetriPlaceList().get(2);
        Point2D beforeSource = outputOfSource.getGraphElementCenter();
        Point2D beforeSink = inputOfSink.getGraphElementCenter();

        canvas.joinPlaces(inputOfSink, outputOfSource);

        assertEquals("moving a place to coincide with one deep inside another frame "
                        + "would corrupt that frame's own layout",
                beforeSource, outputOfSource.getGraphElementCenter());
        assertEquals(beforeSink, inputOfSink.getGraphElementCenter());
    }

    /**
     * Two places that belong to no object may be linked.
     *
     * <p>They used to be refused, on the reasoning that both belong to the same implicit "free
     * elements" object and so linking them is as meaningless as linking two places of one frame.
     * That reasoning was retired deliberately: a reference link is now a way of saying one place
     * repeats another wherever they are drawn, not solely a way of composing two objects. Two
     * places of the same real object are still refused - see the test below.
     */
    @Test
    public void twoFreePlacesCanBeLinked() {
        resetCounters();
        GraphCanvasModel canvas = new GraphCanvasModel("Simple", new GraphPetriNet());
        GraphPetriPlace first = place(canvas, "A", 1, 40, 40);
        GraphPetriPlace second = place(canvas, "B", 0, 400, 400);

        GraphPlaceFusion link = canvas.joinPlaces(first, second);

        assertSame(first, link.getMaster());
        assertSame(second, link.getJoined());
    }

    @Test
    public void twoPlacesOfOneObjectCannotBeShared() {
        GraphCanvasModel canvas = twoFramedObjects();
        GraphPetriPlace first = canvas.getNet().getGraphPetriPlaceList().getFirst();
        GraphPetriPlace second = canvas.getNet().getGraphPetriPlaceList().get(1);

        assertThrows(IllegalArgumentException.class, () -> canvas.joinPlaces(first, second));
    }

    @Test
    public void aCanvasWithoutFramesIsAModelOfOneObject() {
        resetCounters();
        GraphCanvasModel canvas = new GraphCanvasModel("Simple", new GraphPetriNet());
        GraphPetriPlace p0 = place(canvas, "P0", 1, 60, 120);
        GraphPetriTransition t0 = transition(canvas, "T0", 160, 120);
        GraphPetriPlace p1 = place(canvas, "P1", 0, 260, 120);
        canvas.getNet().getGraphArcInList().add(GraphArcFactory.inArc(p0, t0, 1, false));
        canvas.getNet().getGraphArcOutList().add(GraphArcFactory.outArc(t0, p1, 1));

        GraphPetriObjModel model = canvas.toObjModel();

        assertTrue(canvas.isPlainNet());
        assertTrue(model.isSingleObject());
        assertEquals(GraphCanvasModel.FREE_OBJECT_NAME, model.getObject(0).getName());
    }

    @Test
    public void whatIsDrawnOutsideEveryFrameStillSimulates() {
        GraphCanvasModel canvas = twoFramedObjects();
        GraphPetriPlace loose = place(canvas, "Loose", 1, 900, 400);
        GraphPetriTransition looseTransition = transition(canvas, "LooseT", 1000, 400);
        GraphPetriPlace looseOut = place(canvas, "LooseOut", 0, 1100, 400);
        canvas.getNet().getGraphArcInList().add(GraphArcFactory.inArc(loose, looseTransition, 1, false));
        canvas.getNet().getGraphArcOutList().add(GraphArcFactory.outArc(looseTransition, looseOut, 1));

        GraphPetriObjModel model = canvas.toObjModel();

        assertEquals("the free elements make one more object", 3, model.getObjectCount());
        assertEquals(GraphCanvasModel.FREE_OBJECT_NAME, model.getObject(2).getName());
        assertEquals(2, model.getObject(2).getPlaceCount());
    }

    @Test
    public void theSplitModelRunsWithItsLinksWired() throws Exception {
        GraphCanvasModel canvas = twoFramedObjects();

        PetriObjModel simulation = canvas.toObjModel().createPetriObjModel("canvas");
        simulation.setIsProtokol(false);
        simulation.go(10.0);

        assertEquals("two firings deliver two tokens each across the frame border",
                4, simulation.getListObj().get(1).getNet().getListP()[1].getMark());
    }

    @Test
    public void aModelLaidOutOnTheCanvasKeepsItsObjectsAndLinks() {
        GraphPetriObjModel model = twoFramedObjects().toObjModel();

        GraphCanvasModel canvas = GraphCanvasModel.fromObjModel(model);

        assertEquals(2, canvas.getFrames().size());
        assertEquals("Source", canvas.getFrames().getFirst().getName());
        assertEquals(6, canvas.getNet().getGraphPetriPlaceList().size()
                + canvas.getNet().getGraphPetriTransitionList().size());

        GraphPetriObjModel again = canvas.toObjModel();
        assertEquals(2, again.getObjectCount());
        assertEquals(1, again.getLinks().size());
        assertEquals(PetriObjLinkType.TRANSITION_TO_PLACE, again.getLinks().getFirst().getType());
        assertSame(canvas.getNet(), canvas.getNet());
    }

    @Test
    public void everyOwnedElementGetsExactlyOnePort() {
        GraphCanvasModel canvas = twoFramedObjects();
        GraphObjectFrame source = canvas.getFrames().getFirst();

        List<FramePort> ports = canvas.portsOf(source);

        assertEquals("two places and one transition", 3, ports.size());
        List<GraphElement> portedElements = ports.stream().map(FramePort::getElement).toList();
        for (GraphPetriPlace place : canvas.getNet().getGraphPetriPlaceList()) {
            if (canvas.ownerOf(place) == source) {
                assertTrue(place.getName() + " must have a port", portedElements.contains(place));
            }
        }
        for (GraphPetriTransition transition : canvas.getNet().getGraphPetriTransitionList()) {
            if (canvas.ownerOf(transition) == source) {
                assertTrue(transition.getName() + " must have a port", portedElements.contains(transition));
            }
        }
    }

    @Test
    public void portsNeverSitOnTheHeaderSide() {
        GraphCanvasModel canvas = twoFramedObjects();
        GraphObjectFrame source = canvas.getFrames().getFirst();
        Rectangle bounds = source.getBounds();

        for (FramePort port : canvas.portsOf(source)) {
            Point p = port.getPosition();
            assertTrue("a port must never sit on the header/top side",
                    port.getEdge() != FramePort.Edge.TOP);
            switch (port.getEdge()) {
                case LEFT -> assertEquals("a left-edge port's x is the frame's left border", bounds.x, p.x);
                case RIGHT ->
                        assertEquals("a right-edge port's x is the frame's right border",
                                bounds.x + bounds.width, p.x);
                case BOTTOM ->
                        assertEquals("a bottom-edge port's y is the frame's bottom border",
                                bounds.y + bounds.height, p.y);
                default -> throw new AssertionError("unexpected edge " + port.getEdge());
            }
            assertTrue("port must not be above the header", p.y >= bounds.y + GraphObjectFrame.HEADER_HEIGHT);
            assertTrue(p.x >= bounds.x && p.x <= bounds.x + bounds.width);
            assertTrue(p.y <= bounds.y + bounds.height);
        }
    }

    @Test
    public void aPortSitsOnTheSideNearestItsOwnElement() {
        resetCounters();
        GraphCanvasModel canvas = new GraphCanvasModel("Widget", new GraphPetriNet());
        GraphObjectFrame frame = new GraphObjectFrame("W", new Rectangle(0, 0, 400, 400));
        canvas.getFrames().add(frame);
        GraphPetriPlace nearLeft = place(canvas, "Left", 0, 20, 140); // hugs the left border
        GraphPetriTransition nearBottom = transition(canvas, "Bottom", 200, 380); // hugs the bottom
        canvas.claim(frame, nearLeft);
        canvas.claim(frame, nearBottom);

        List<FramePort> ports = canvas.portsOf(frame);

        assertEquals(FramePort.Edge.LEFT, portFor(ports, nearLeft).getEdge());
        assertEquals(FramePort.Edge.BOTTOM, portFor(ports, nearBottom).getEdge());
    }

    private static FramePort portFor(List<FramePort> ports, GraphElement element) {
        for (FramePort port : ports) {
            if (port.getElement() == element) {
                return port;
            }
        }
        throw new AssertionError("no port for " + element);
    }

    @Test
    public void portAtFindsTheNearestPortAndNothingFarAway() {
        GraphCanvasModel canvas = twoFramedObjects();
        GraphObjectFrame source = canvas.getFrames().getFirst();
        FramePort target = canvas.portsOf(source).getFirst();

        assertSame(target.getElement(), canvas.portAt(target.getPosition()).getElement());
        // Genuinely empty space: none of Source's own elements sit anywhere near here, so
        // unlike the frame's bare centre this cannot land on an element's own body either.
        assertNull(canvas.portAt(new Point2D.Double(source.getBounds().x + 20, source.getBounds().getMaxY() - 10)));
    }

    @Test
    public void portAtAlsoFindsTheOwningElementsOwnBodyWhileItIsShown() {
        // A locked object's content is visible by default, so a point on the real element —
        // not on its (undrawn, in that case) port circle — resolves to that element's port
        // too: this is what lets it be dragged from directly while shown.
        GraphCanvasModel canvas = twoFramedObjects();
        GraphObjectFrame source = canvas.getFrames().getFirst();
        GraphPetriPlace p0 = canvas.getNet().getGraphPetriPlaceList().getFirst();
        assertTrue("fixture sanity check", source.isContentShown());

        FramePort hit = canvas.portAt(p0.getGraphElementCenter());

        assertNotNull(hit);
        assertSame(p0, hit.getElement());
    }

    @Test
    public void portAtIgnoresAnElementsBodyWhileItsObjectIsHidden() {
        GraphCanvasModel canvas = twoFramedObjects();
        GraphObjectFrame source = canvas.getFrames().getFirst();
        GraphPetriPlace p0 = canvas.getNet().getGraphPetriPlaceList().getFirst();
        source.setContentVisible(false);

        assertNull("only the port circle itself should be reachable once content is hidden",
                canvas.portAt(p0.getGraphElementCenter()));
    }

    @Test
    public void resizingAFrameMovesAPortOnTheAffectedEdge() {
        GraphCanvasModel canvas = twoFramedObjects();
        GraphObjectFrame source = canvas.getFrames().getFirst();
        // P1 sits near the right edge (x=250 inside a 300-wide frame), so widening the frame
        // must move its port; a port on an untouched edge is not expected to move.
        GraphPetriPlace p1 = canvas.getNet().getGraphPetriPlaceList().get(1);
        Point before = portFor(canvas.portsOf(source), p1).getPosition();

        source.setBounds(new Rectangle(source.getBounds().x, source.getBounds().y, 600, 600));
        Point after = portFor(canvas.portsOf(source), p1).getPosition();

        assertTrue("a port on the widened edge must move", !before.equals(after));
    }

    @Test
    public void ownershipIsWhatWasExplicitlyClaimedNotWhatTheFrameHappensToCover() {
        resetCounters();
        GraphCanvasModel canvas = new GraphCanvasModel("Simple", new GraphPetriNet());
        GraphObjectFrame frame = new GraphObjectFrame("F", new Rectangle(0, 0, 300, 300));
        canvas.getFrames().add(frame);
        GraphPetriPlace stray = place(canvas, "Stray", 0, 100, 100); // inside the frame's rectangle

        assertNull("a frame's rectangle covering a point does not by itself claim what is "
                + "drawn there — this is exactly what let moving a frame silently absorb "
                + "whatever it passed over", canvas.ownerOf(stray));

        // The other half of the same guarantee: a claim is single-valued, so handing the element
        // to a second frame takes it off the first rather than leaving both claiming it.
        GraphObjectFrame second = new GraphObjectFrame("G", new Rectangle(0, 0, 300, 300));
        canvas.getFrames().add(second);
        canvas.claim(frame, stray);
        canvas.claim(second, stray);

        assertSame(second, canvas.ownerOf(stray));
        assertFalse("the first frame let go of it", frame.hasMember(stray));
        assertEquals(1, canvas.membersOfSubtree(second).size());
    }

    @Test
    public void removingAFrameLiftsItsMembersToWhateverEnclosedIt() {
        // Geometry is no longer consulted here at all. What a removed object held moves one
        // level out: to the object that enclosed it, or to the free elements when nothing did.
        // The frame merely drawn inside its rectangle gets nothing, which is the whole point -
        // the previous "falls to whatever covers it now" rule handed an outer object's entire
        // net to whatever happened to be nested in it.
        resetCounters();
        GraphCanvasModel canvas = new GraphCanvasModel("Simple", new GraphPetriNet());
        GraphObjectFrame outer = new GraphObjectFrame("Outer", new Rectangle(0, 0, 300, 300));
        GraphObjectFrame inner = new GraphObjectFrame("Inner", new Rectangle(50, 50, 100, 100));
        canvas.getFrames().add(outer);
        canvas.getFrames().add(inner);
        GraphPetriPlace insideBoth = place(canvas, "Both", 0, 80, 80);
        GraphPetriPlace insideOuterOnly = place(canvas, "OuterOnly", 0, 200, 200);
        canvas.claim(outer, insideBoth);
        canvas.claim(outer, insideOuterOnly);

        canvas.getFrames().remove(outer);
        canvas.releaseMembers(outer);

        assertNull("the frame drawn inside it does not inherit its net", canvas.ownerOf(insideBoth));
        assertNull(canvas.ownerOf(insideOuterOnly));
        assertFalse(inner.hasMember(insideBoth));
    }

    @Test
    public void removingANestedFrameHandsItsNetToItsParent() {
        resetCounters();
        GraphCanvasModel canvas = new GraphCanvasModel("Simple", new GraphPetriNet());
        GraphObjectFrame parent = new GraphObjectFrame("Parent", new Rectangle(0, 0, 400, 400));
        GraphObjectFrame child = new GraphObjectFrame("Child", new Rectangle(50, 50, 150, 150));
        GraphObjectFrame grandchild = new GraphObjectFrame("Grandchild", new Rectangle(60, 60, 90, 90));
        canvas.getFrames().add(parent);
        canvas.getFrames().add(child);
        canvas.getFrames().add(grandchild);
        canvas.nest(child, parent);
        canvas.nest(grandchild, child);
        GraphPetriPlace held = place(canvas, "Held", 0, 80, 80);
        canvas.claim(child, held);

        canvas.getFrames().remove(child);
        canvas.releaseMembers(child);

        assertSame("the child's net joins the object that enclosed it", parent, canvas.ownerOf(held));
        assertSame("and its own children are re-nested one level out",
                parent, canvas.enclosingOf(grandchild));
    }

    @Test
    public void aNestedFrameNamesItsParentAndKnowsItsLevel() {
        resetCounters();
        GraphCanvasModel canvas = new GraphCanvasModel("Simple", new GraphPetriNet());
        GraphObjectFrame parent = new GraphObjectFrame("Parent", new Rectangle(0, 0, 400, 400));
        GraphObjectFrame child = new GraphObjectFrame("Child", new Rectangle(50, 50, 150, 150));
        canvas.getFrames().add(parent);
        canvas.getFrames().add(child);
        GraphPetriPlace ofParent = place(canvas, "OfParent", 0, 350, 350);
        GraphPetriPlace ofChild = place(canvas, "OfChild", 0, 80, 80);
        canvas.claim(parent, ofParent);
        canvas.claim(child, ofChild);

        canvas.nest(child, parent);

        assertSame(parent, canvas.enclosingOf(child));
        assertNull(canvas.enclosingOf(parent));
        assertEquals("a top-level object is level 1", 1, canvas.levelOf(parent));
        assertEquals("nested in it, level 2", 2, canvas.levelOf(child));
        assertEquals(List.of(child), canvas.childrenOf(parent));
        assertEquals(List.of(parent, child), canvas.subtreeOf(parent));
        assertEquals("the parent, seen from outside, holds both",
                2, canvas.membersOfSubtree(parent).size());
        assertSame("while ownerOf still answers with the direct owner",
                child, canvas.ownerOf(ofChild));
        assertEquals("and a port per element of the whole subtree", 2, canvas.portsOf(parent).size());
    }

    @Test
    public void nestingRefusesACycleAndFramesComeOutParentFirst() {
        resetCounters();
        GraphCanvasModel canvas = new GraphCanvasModel("Simple", new GraphPetriNet());
        GraphObjectFrame child = new GraphObjectFrame("Child", new Rectangle(50, 50, 150, 150));
        GraphObjectFrame parent = new GraphObjectFrame("Parent", new Rectangle(0, 0, 400, 400));
        // Deliberately added child-first, so parent-first order is not merely list order.
        canvas.getFrames().add(child);
        canvas.getFrames().add(parent);
        canvas.nest(child, parent);

        assertThrows(IllegalArgumentException.class, () -> canvas.nest(parent, child));
        assertThrows(IllegalArgumentException.class, () -> canvas.nest(parent, parent));
        assertEquals(List.of(parent, child), canvas.framesParentFirst());
    }

    @Test
    public void frameAtPicksTheInnermostFrameAndOwnerOfTheOneThatClaims() {
        // The tie that used to be broken in opposite directions: ownerOf took the first frame in
        // canvas order that claimed the element, frameAt the last one whose rectangle contained
        // the point. Both now agree that deeper wins, which is what makes a nest reason about.
        resetCounters();
        GraphCanvasModel canvas = new GraphCanvasModel("Simple", new GraphPetriNet());
        GraphObjectFrame outer = new GraphObjectFrame("Outer", new Rectangle(100, 100, 400, 300));
        GraphObjectFrame inner = new GraphObjectFrame("Inner", new Rectangle(200, 200, 150, 120));
        canvas.getFrames().add(outer);
        canvas.getFrames().add(inner);
        canvas.nest(inner, outer);
        GraphPetriPlace inside = place(canvas, "Inside", 0, 250, 250);
        canvas.claim(inner, inside);

        assertSame("frameAt: the innermost frame containing the point",
                inner, canvas.frameAt(inside.getGraphElementCenter()));
        assertSame("ownerOf: the frame that claims it, which is the same one",
                inner, canvas.ownerOf(inside));
    }

    @Test
    public void aDeepCopyKeepsItsNestingPointingAtItsOwnFrames() {
        resetCounters();
        GraphCanvasModel canvas = new GraphCanvasModel("Simple", new GraphPetriNet());
        GraphObjectFrame parent = new GraphObjectFrame("Parent", new Rectangle(0, 0, 400, 400));
        GraphObjectFrame child = new GraphObjectFrame("Child", new Rectangle(50, 50, 150, 150));
        canvas.getFrames().add(parent);
        canvas.getFrames().add(child);
        canvas.nest(child, parent);
        canvas.claim(child, place(canvas, "Held", 0, 80, 80));

        GraphCanvasModel copy = new GraphCanvasModel(canvas);

        GraphObjectFrame copiedParent = copy.getFrames().get(0);
        GraphObjectFrame copiedChild = copy.getFrames().get(1);
        assertSame("the copy's child names the copy's own parent, not the original's",
                copiedParent, copy.enclosingOf(copiedChild));
        assertNull(canvas.enclosingOf(parent));
        assertEquals(2, copy.levelOf(copiedChild));
    }

    @Test
    public void aNestedCanvasExportsAsSiblingObjectsAndTheNestComesBack() {
        // A model is a flat, indexed list of objects however deeply the canvas nests them, so
        // a nested object is a sibling object here and records which sibling encloses it. A
        // document says the same thing by writing the child's page inside its parent's; see
        // PageHierarchyPnmlTest. The nest is restored on import: it used to be dropped, so
        // the inner object came back sitting inside the outer frame's rectangle while
        // structurally belonging to nobody, and dragging the outer object left it behind.
        resetCounters();
        GraphCanvasModel canvas = new GraphCanvasModel("Nested", new GraphPetriNet());
        GraphObjectFrame parent = new GraphObjectFrame("Parent", new Rectangle(0, 0, 400, 400));
        GraphObjectFrame child = new GraphObjectFrame("Child", new Rectangle(50, 50, 150, 150));
        canvas.getFrames().add(parent);
        canvas.getFrames().add(child);
        canvas.nest(child, parent);
        GraphPetriTransition ofParent = transition(canvas, "OfParent", 350, 350);
        GraphPetriPlace ofChild = place(canvas, "OfChild", 0, 80, 80);
        canvas.claim(parent, ofParent);
        canvas.claim(child, ofChild);
        canvas.getNet().getGraphArcOutList().add(GraphArcFactory.outArc(ofParent, ofChild, 1));

        GraphPetriObjModel exported = canvas.toObjModel();

        assertEquals("two siblings, indexed by the flat frame list", 2, exported.getObjectCount());
        assertEquals("Parent", exported.getObject(0).getName());
        assertEquals("Child", exported.getObject(1).getName());
        assertEquals("the child records which sibling encloses it",
                0, exported.getObject(1).getParentIndex());
        assertEquals("the arc across the nesting boundary is a link, as between any two objects",
                1, exported.getLinks().size());

        GraphCanvasModel reimported = GraphCanvasModel.fromObjModel(exported);

        assertEquals(2, reimported.getFrames().size());
        assertSame("and the nest is restored from the parent index",
                reimported.getFrames().get(0),
                reimported.enclosingOf(reimported.getFrames().get(1)));
    }

    @Test
    public void contentVisibleDefaultsTrueAndTogglingItNeverTouchesBounds() {
        GraphObjectFrame frame = new GraphObjectFrame("F", new Rectangle(10, 10, 300, 200));
        Rectangle before = new Rectangle(frame.getBounds());

        assertTrue("an object's content starts shown", frame.isContentVisible());

        frame.setContentVisible(false);
        assertFalse(frame.isContentVisible());
        assertEquals("hiding content is a drawing choice, not a resize", before, frame.getBounds());

        frame.setContentVisible(true);
        assertTrue(frame.isContentVisible());
        assertEquals(before, frame.getBounds());
    }

    @Test
    public void theEyeIconSitsInTheHeaderAndNowhereElseIsOnIt() {
        GraphObjectFrame frame = new GraphObjectFrame("F", new Rectangle(0, 0, 300, 200));
        Rectangle icon = frame.eyeIconBounds();

        assertTrue("the icon must fit inside the header strip",
                icon.y >= frame.getBounds().y && icon.y + icon.height <= frame.getBounds().y + GraphObjectFrame.HEADER_HEIGHT);
        assertTrue(frame.isOnEyeIcon(new Point2D.Double(icon.getCenterX(), icon.getCenterY())));
        assertFalse("the body is not the icon", frame.isOnEyeIcon(new Point2D.Double(
                frame.getBounds().getCenterX(), frame.getBounds().getCenterY())));
        assertFalse("the rest of the header is not the icon either",
                frame.isOnEyeIcon(new Point2D.Double(frame.getBounds().x + frame.getBounds().width - 10, icon.getCenterY())));
    }

    @Test
    public void aFramedPlaceCanBeSharedWithAFreePlace() {
        GraphCanvasModel canvas = twoFramedObjects();
        GraphPetriPlace framed = canvas.getNet().getGraphPetriPlaceList().get(1); // P1, owned by Source
        GraphPetriPlace free = place(canvas, "Free", 0, 900, 900);

        GraphPlaceFusion fusion = canvas.joinPlaces(framed, free);

        assertTrue("a fusion with a framed half is drawn as a line, not a coincident ring",
                fusion.isAnchoredToAFrame());
        assertEquals(canvas.getFrames().getFirst(), fusion.getMasterOwner());
        assertNull(fusion.getJoinedOwner());
    }

    @Test
    public void movingAFramedHalfOfAFusionLeavesTheFreeHalfWhereItWas() {
        // syncFusions() runs after every frame drag to keep a free-free fusion's ring coincident
        // - GraphPlaceFusion#syncPosition() used to do that unconditionally, so dragging the
        // object that owns one half pulled the other half on top of wherever it ended up, the
        // instant the two places were joined this way rather than becoming two circles kept
        // apart by nothing but a line.
        GraphCanvasModel canvas = twoFramedObjects();
        GraphPetriPlace framed = canvas.getNet().getGraphPetriPlaceList().get(1); // P1, owned by Source
        GraphPetriPlace free = place(canvas, "Free", 0, 900, 900);
        canvas.joinPlaces(framed, free);

        Point2D freeBefore = free.getGraphElementCenter();

        // What moving the "Source" object does to its own member's position before
        // PetriNetsPanel#moveFrame calls syncFusions() at the end of the same drag.
        Point2D framedCentre = framed.getGraphElementCenter();
        framed.setNewCoordinates(new Point2D.Double(framedCentre.getX() + 50, framedCentre.getY() + 50));
        canvas.syncFusions();

        assertEquals("the free half must stay exactly where it was, not jump onto the framed one",
                freeBefore, free.getGraphElementCenter());
    }

    /**
     * The full PNML fidelity round trip through real XML: exact coordinates for every
     * element (framed and free alike), frame rectangles, nesting, claims and the shared
     * place all come back as saved. Reimporting used to normalize every page to the
     * (50,50) corner and re-centre each net inside its frame, so elements drifted and
     * piled up, free elements lost their positions entirely, and the nest between objects
     * vanished - the inner object sat inside the outer frame's rectangle while dragging
     * that frame left it behind.
     */
    @Test
    public void aComplexCanvasRoundTripsThroughPnmlExactly() throws Exception {
        resetCounters();
        GraphCanvasModel canvas = new GraphCanvasModel("Doc", new GraphPetriNet());

        GraphObjectFrame outer = new GraphObjectFrame("Outer", new Rectangle(120, 100, 500, 400));
        canvas.getFrames().add(outer);
        GraphPetriPlace outerPlace = place(canvas, "PO", 2, 200, 320);
        canvas.claim(outer, outerPlace);

        GraphObjectFrame inner = new GraphObjectFrame("Inner", new Rectangle(300, 180, 220, 160));
        canvas.getFrames().add(inner);
        canvas.nest(inner, outer);
        GraphPetriPlace innerPlace = place(canvas, "PI", 0, 380, 260);
        GraphPetriTransition innerTransition = transition(canvas, "TI", 460, 260);
        canvas.claim(inner, innerPlace);
        canvas.claim(inner, innerTransition);
        canvas.getNet().getGraphArcInList().add(
                GraphArcFactory.inArc(innerPlace, innerTransition, 1, false));

        GraphPetriPlace freePlace = place(canvas, "PF", 5, 900, 700);

        GraphObjectFrame other = new GraphObjectFrame("Other", new Rectangle(700, 100, 200, 160));
        canvas.getFrames().add(other);
        GraphPetriPlace otherPlace = place(canvas, "PX", 0, 780, 200);
        canvas.claim(other, otherPlace);
        canvas.joinPlaces(outerPlace, otherPlace);

        String xml = new ua.stetsenkoinna.pnml.PnmlModelGenerator().generateXml(canvas.toObjModel());
        GraphCanvasModel restored = GraphCanvasModel.fromObjModel(
                new ua.stetsenkoinna.pnml.PnmlModelParser().parseXml(xml));

        assertEquals("three frames come back", 3, restored.getFrames().size());
        GraphObjectFrame restoredOuter = restored.getFrames().get(0);
        GraphObjectFrame restoredInner = restored.getFrames().get(1);
        assertEquals(new Rectangle(120, 100, 500, 400), restoredOuter.getBounds());
        assertEquals(new Rectangle(300, 180, 220, 160), restoredInner.getBounds());
        assertSame("the nest survives the flat pages",
                restoredOuter, restored.enclosingOf(restoredInner));

        for (GraphPetriPlace restoredPlace : restored.getNet().getGraphPetriPlaceList()) {
            java.awt.geom.Point2D centre = restoredPlace.getGraphElementCenter();
            switch (restoredPlace.getName()) {
                case "PO" -> assertEquals("PO stays put", new Point(200, 320),
                        new Point((int) centre.getX(), (int) centre.getY()));
                case "PI" -> assertEquals("PI stays put", new Point(380, 260),
                        new Point((int) centre.getX(), (int) centre.getY()));
                case "PF" -> assertEquals("the free element stays put", new Point(900, 700),
                        new Point((int) centre.getX(), (int) centre.getY()));
                case "PX" -> assertEquals("PX stays put", new Point(780, 200),
                        new Point((int) centre.getX(), (int) centre.getY()));
                default -> { }
            }
        }
        assertEquals("the inner object's members are claimed by the restored inner frame",
                2, restoredInner.getMembers().size());
        assertEquals("the shared place survives", 1, restored.getFusions().size());
    }

    /**
     * A collapsed object used to export its 170x56 summary box as its size: reloading piled
     * the whole net inside that box, and expanding the object restored the box's size
     * instead of the frame the user had drawn.
     */
    @Test
    public void aCollapsedObjectRoundTripsWithItsExpandedSize() {
        resetCounters();
        GraphCanvasModel canvas = new GraphCanvasModel("Doc", new GraphPetriNet());
        GraphObjectFrame frame = new GraphObjectFrame("Obj", new Rectangle(100, 80, 400, 300));
        canvas.getFrames().add(frame);
        GraphPetriPlace inside = place(canvas, "P1", 0, 200, 200);
        canvas.claim(frame, inside);
        frame.setCollapsed(true);

        GraphCanvasModel restored = GraphCanvasModel.fromObjModel(canvas.toObjModel());

        GraphObjectFrame restoredFrame = restored.getFrames().get(0);
        assertTrue("the object comes back collapsed", restoredFrame.isCollapsed());
        restoredFrame.setCollapsed(false);
        assertEquals("expanded, it has the size the user gave it",
                new Rectangle(100, 80, 400, 300), restoredFrame.getBounds());
        GraphPetriPlace restoredPlace = restored.getNet().getGraphPetriPlaceList().get(0);
        assertTrue("and its net sits inside it",
                restoredFrame.getBounds().contains(restoredPlace.getGraphElementCenter()));
    }
}

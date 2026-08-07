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
        source.addMember(p0);
        source.addMember(t0);
        source.addMember(p1);

        GraphPetriPlace p2 = place(canvas, "P2", 0, 460, 120);
        GraphPetriTransition t1 = transition(canvas, "T1", 560, 120);
        GraphPetriPlace p3 = place(canvas, "P3", 0, 660, 120);
        sink.addMember(p2);
        sink.addMember(t1);
        sink.addMember(p3);

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

    @Test
    public void twoFreePlacesCannotBeSharedEither() {
        // Both belong to the same implicit "free elements" object once the canvas is split,
        // exactly like two places drawn inside the same frame — joining them would be just as
        // meaningless.
        resetCounters();
        GraphCanvasModel canvas = new GraphCanvasModel("Simple", new GraphPetriNet());
        GraphPetriPlace first = place(canvas, "A", 1, 40, 40);
        GraphPetriPlace second = place(canvas, "B", 0, 400, 400);

        assertThrows(IllegalArgumentException.class, () -> canvas.joinPlaces(first, second));
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
        frame.addMember(nearLeft);
        frame.addMember(nearBottom);

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
        assertNull(canvas.portAt(new Point2D.Double(
                source.getBounds().getCenterX(), source.getBounds().getCenterY())));
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
    }

    @Test
    public void removingAFrameFreesItsMembersUnlessAnotherFrameStillCoversThem() {
        resetCounters();
        GraphCanvasModel canvas = new GraphCanvasModel("Simple", new GraphPetriNet());
        GraphObjectFrame outer = new GraphObjectFrame("Outer", new Rectangle(0, 0, 300, 300));
        GraphObjectFrame inner = new GraphObjectFrame("Inner", new Rectangle(50, 50, 100, 100));
        canvas.getFrames().add(outer);
        canvas.getFrames().add(inner);
        GraphPetriPlace coveredByBoth = place(canvas, "Both", 0, 80, 80);
        GraphPetriPlace coveredByOuterOnly = place(canvas, "OuterOnly", 0, 200, 200);
        outer.addMember(coveredByBoth);
        outer.addMember(coveredByOuterOnly);

        canvas.getFrames().remove(outer);
        canvas.releaseMembers(outer);

        assertEquals("falls to the frame that still geometrically covers it",
                inner, canvas.ownerOf(coveredByBoth));
        assertNull("nothing covers it anymore, so it becomes free", canvas.ownerOf(coveredByOuterOnly));
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
}

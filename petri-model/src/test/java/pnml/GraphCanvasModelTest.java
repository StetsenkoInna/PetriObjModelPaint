package pnml;

import org.junit.Test;
import ua.stetsenkoinna.graphnet.GraphArcFactory;
import ua.stetsenkoinna.graphnet.GraphCanvasModel;
import ua.stetsenkoinna.graphnet.GraphObjectFrame;
import ua.stetsenkoinna.graphnet.GraphPetriNet;
import ua.stetsenkoinna.graphnet.GraphPetriObjModel;
import ua.stetsenkoinna.graphnet.GraphPetriPlace;
import ua.stetsenkoinna.graphnet.GraphPetriTransition;
import ua.stetsenkoinna.petriobj.ArcIn;
import ua.stetsenkoinna.petriobj.ArcOut;
import ua.stetsenkoinna.petriobj.PetriObjLinkType;
import ua.stetsenkoinna.petriobj.PetriObjModel;
import ua.stetsenkoinna.petriobj.PetriP;
import ua.stetsenkoinna.petriobj.PetriT;

import java.awt.Rectangle;
import java.awt.geom.Point2D;

import static org.junit.Assert.assertEquals;
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
        canvas.getFrames().add(new GraphObjectFrame("Sink", new Rectangle(400, 0, 300, 300)));

        GraphPetriPlace p0 = place(canvas, "P0", 2, 60, 120);
        GraphPetriTransition t0 = transition(canvas, "T0", 160, 120);
        GraphPetriPlace p1 = place(canvas, "P1", 0, 250, 120);

        GraphPetriPlace p2 = place(canvas, "P2", 0, 460, 120);
        GraphPetriTransition t1 = transition(canvas, "T1", 560, 120);
        GraphPetriPlace p3 = place(canvas, "P3", 0, 660, 120);

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

        // Joining moves the shared place onto the master, which is inside the second frame.
        GraphPetriObjModel model = canvas.toObjModel();
        assertTrue(model.getLinks().stream()
                .anyMatch(link -> link.getType() == PetriObjLinkType.PLACE_FUSION));
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
}

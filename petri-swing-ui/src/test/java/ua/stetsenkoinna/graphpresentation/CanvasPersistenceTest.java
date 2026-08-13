package ua.stetsenkoinna.graphpresentation;

import org.junit.Test;
import ua.stetsenkoinna.graphnet.GraphCanvasModel;
import ua.stetsenkoinna.graphnet.GraphObjectFrame;
import ua.stetsenkoinna.graphnet.GraphPetriNet;
import ua.stetsenkoinna.graphnet.GraphPetriPlace;
import ua.stetsenkoinna.graphnet.GraphPetriTransition;
import ua.stetsenkoinna.petriobj.PetriP;
import ua.stetsenkoinna.petriobj.PetriT;

import java.awt.Rectangle;
import java.awt.geom.Point2D;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/**
 * What survives saving a canvas and opening it again.
 *
 * <p>A .pns file used to hold the net alone, so every Petri-object on the canvas was dropped on
 * reopen without a word: the frames, the places they shared and, once objects could nest, the
 * whole hierarchy. These tests pin that a canvas with objects goes to file as the canvas document
 * and comes back whole, and that a file written before any of this still opens.
 */
public class CanvasPersistenceTest {

    private static int idCounter = 7000;

    /** {@code P -> T} inside one object, with a second object nested in it. */
    private static GraphCanvasModel nestedCanvas() {
        PetriP.initNext();
        PetriT.initNext();

        GraphPetriPlace place = new GraphPetriPlace(new PetriP("P0", 3), idCounter++);
        place.setNewCoordinates(new Point2D.Double(120, 140));
        GraphPetriTransition transition =
                new GraphPetriTransition(new PetriT("T0", 1.0), idCounter++);
        transition.setNewCoordinates(new Point2D.Double(240, 140));

        GraphPetriNet net = new GraphPetriNet();
        net.getGraphPetriPlaceList().add(place);
        net.getGraphPetriTransitionList().add(transition);

        GraphCanvasModel model = new GraphCanvasModel();
        model.setNet(net);
        model.setName("Saved canvas");

        GraphObjectFrame outer = new GraphObjectFrame("Outer", new Rectangle(80, 100, 260, 120));
        outer.setPriority(7);
        GraphObjectFrame inner = new GraphObjectFrame("Inner", new Rectangle(100, 110, 100, 60));
        model.getFrames().add(outer);
        model.getFrames().add(inner);
        // claim, not addMember: the model is the only writer of ownership, which is what keeps
        // it single-valued rather than leaving every caller to check.
        model.claim(outer, place);
        model.claim(outer, transition);
        model.nest(inner, outer);

        return model;
    }

    private static Object roundTrip(Object graph) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream out = new ObjectOutputStream(bytes)) {
            out.writeObject(graph);
        }
        try (ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            return in.readObject();
        }
    }

    @Test
    public void aCanvasWithObjectsSurvivesTheRoundTrip() throws Exception {
        GraphCanvasModel restored = (GraphCanvasModel) roundTrip(nestedCanvas());

        assertEquals("Saved canvas", restored.getName());
        assertEquals("both objects came back", 2, restored.getFrames().size());

        GraphObjectFrame outer = restored.getFrames().get(0);
        GraphObjectFrame inner = restored.getFrames().get(1);
        assertEquals("Outer", outer.getName());
        assertEquals("the priority is what makes one object act before another", 7, outer.getPriority());
        assertEquals(new Rectangle(80, 100, 260, 120), outer.getBounds());
        assertSame("the nesting came back, not just the two frames", outer, restored.enclosingOf(inner));
    }

    @Test
    public void membershipComesBackPointingAtTheRestoredElements() throws Exception {
        GraphCanvasModel restored = (GraphCanvasModel) roundTrip(nestedCanvas());

        GraphPetriPlace place = restored.getNet().getGraphPetriPlaceList().get(0);
        GraphPetriTransition transition = restored.getNet().getGraphPetriTransitionList().get(0);
        GraphObjectFrame outer = restored.getFrames().get(0);

        // Membership is held in an identity set, so this only holds if the net and the frames
        // were written in ONE object graph. Serialising them separately would give the frame
        // copies of the elements and leave the object apparently empty, which is the very bug
        // the whole change is about.
        assertSame("the object owns the place the net holds", outer, restored.ownerOf(place));
        assertSame("and the transition too", outer, restored.ownerOf(transition));
        assertEquals("its marking came with it", 3, place.getPetriPlace().getMark());
    }

    @Test
    public void theRestoredCanvasIsAnIndependentCopy() throws Exception {
        GraphCanvasModel original = nestedCanvas();
        GraphCanvasModel restored = (GraphCanvasModel) roundTrip(original);

        assertNotSame(original, restored);
        assertNotSame(original.getFrames().get(0), restored.getFrames().get(0));
        assertNotSame(original.getNet().getGraphPetriPlaceList().get(0),
                restored.getNet().getGraphPetriPlaceList().get(0));
    }

    @Test
    public void aFileHoldingOnlyANetStillOpens() throws Exception {
        // What every .pns written before objects were persisted contains, and what a canvas with
        // no objects on it is still written as, so such a file stays readable by builds that know
        // nothing about objects.
        PetriP.initNext();
        PetriT.initNext();
        GraphPetriNet net = new GraphPetriNet();
        GraphPetriPlace place = new GraphPetriPlace(new PetriP("P0", 2), idCounter++);
        place.setNewCoordinates(new Point2D.Double(60, 60));
        net.getGraphPetriPlaceList().add(place);

        Object restored = roundTrip(net);

        assertTrue("a bare net must still arrive as a bare net", restored instanceof GraphPetriNet);
        assertEquals(1, ((GraphPetriNet) restored).getGraphPetriPlaceList().size());
    }

    @Test
    public void everyPersistedClassPinsItsSerialVersionUid() throws Exception {
        // Left to the compiler these ids are derived from the class shape, so the next field
        // added to any of them would make every file already written unreadable. They are pinned
        // now, before the first file containing them exists.
        for (Class<?> persisted : new Class<?>[]{
                GraphCanvasModel.class,
                GraphObjectFrame.class,
                ua.stetsenkoinna.graphnet.FramePort.class,
                ua.stetsenkoinna.graphnet.GraphPlaceFusion.class}) {
            java.lang.reflect.Field field = persisted.getDeclaredField("serialVersionUID");
            assertNotNull(persisted.getSimpleName() + " must pin its serialVersionUID", field);
        }
    }

    @Test
    public void aCanvasWithNoObjectsNeedsNoCanvasDocument() {
        // The rule the save path branches on: nothing to say about objects means the file stays
        // a bare net. Kept as a test so the branch is not "simplified" into always writing the
        // document, which would make new files unreadable by older builds for no gain.
        GraphCanvasModel model = new GraphCanvasModel();
        model.setNet(new GraphPetriNet());
        assertTrue("a canvas with no frames has nothing the net cannot carry",
                model.getFrames().isEmpty());
        assertEquals(new ArrayList<>(), model.getFusions());
    }
}

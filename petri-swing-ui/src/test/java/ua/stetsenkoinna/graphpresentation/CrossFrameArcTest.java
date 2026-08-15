package ua.stetsenkoinna.graphpresentation;

import org.junit.Test;

import ua.stetsenkoinna.graphnet.GraphArcFactory;
import ua.stetsenkoinna.graphnet.GraphObjectFrame;
import ua.stetsenkoinna.graphnet.GraphPetriObjModel;
import ua.stetsenkoinna.graphnet.GraphPetriPlace;
import ua.stetsenkoinna.graphnet.GraphPetriTransition;
import ua.stetsenkoinna.petriobj.ExceptionInvalidTimeDelay;
import ua.stetsenkoinna.petriobj.PetriObjModel;
import ua.stetsenkoinna.petriobj.PetriP;
import ua.stetsenkoinna.petriobj.PetriT;

import java.awt.Point;
import java.awt.Rectangle;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * What a transition may be fed from. A transition takes its input places from its own
 * Petri-object and from nowhere else, which is what the definition of a transition says, so an
 * arc running from a place of one object into a transition of another is not a link between the
 * two objects and cannot be exported as one.
 *
 * <p>Nothing is lost by that. Sharing the place between the two objects and drawing an ordinary
 * arc from it inside the object that owns the transition says exactly the same thing, and it is
 * the form the canvas and the document both accept.
 */
public class CrossFrameArcTest {

    @Test
    public void anArcFromAnotherObjectsPlaceIntoATransitionIsRejected() throws Exception {
        PetriNetsPanel panel = new PetriNetsPanel(null, false);

        GraphPetriPlace framedPlace = new GraphPetriPlace(new PetriP("Pin", 1), 1);
        framedPlace.setNewCoordinates(new Point(100, 100));
        panel.getGraphNet().getGraphPetriPlaceList().add(framedPlace);
        GraphObjectFrame frame = new GraphObjectFrame("Source", new Rectangle(40, 40, 160, 160));
        panel.getCanvasModel().claim(frame, framedPlace);
        panel.addObjectFrame(frame);

        GraphPetriTransition freeTransition = new GraphPetriTransition(new PetriT("Sink", 1.0), 2);
        freeTransition.setNewCoordinates(new Point(400, 100));
        panel.getGraphNet().getGraphPetriTransitionList().add(freeTransition);

        // The arc crosses the frame boundary: its place is a member of "Source", its
        // transition is not. There is no link type this can become.
        panel.getGraphNet().getGraphArcInList()
                .add(GraphArcFactory.inArc(framedPlace, freeTransition, 1, false));

        try {
            panel.getCanvasModel().toObjModel();
            fail("an input arc leaving its Petri-object must be refused");
        } catch (IllegalArgumentException expected) {
            assertTrue("the message must name the place: " + expected.getMessage(),
                    expected.getMessage().contains("Pin"));
            assertTrue("the message must name the transition: " + expected.getMessage(),
                    expected.getMessage().contains("Sink"));
        }
    }

    /**
     * The canonical form of the same intent, which must keep working: one object's transition
     * is fed by a place that object owns, and that place is shared with the object producing
     * into it. The arc itself never leaves the object that owns the transition.
     */
    @Test
    public void aTransitionFedThroughASharedPlaceIsAcceptedAndRuns() throws Exception {
        PetriNetsPanel panel = new PetriNetsPanel(null, false);

        // "Source": Pool -> Emit -> Handover, all three inside the frame.
        GraphPetriPlace pool = new GraphPetriPlace(new PetriP("Pool", 2), 1);
        pool.setNewCoordinates(new Point(70, 100));
        GraphPetriTransition emit = new GraphPetriTransition(new PetriT("Emit", 1.0), 1);
        emit.setNewCoordinates(new Point(120, 100));
        GraphPetriPlace handover = new GraphPetriPlace(new PetriP("Handover", 0), 2);
        handover.setNewCoordinates(new Point(170, 100));
        panel.getGraphNet().getGraphPetriPlaceList().add(pool);
        panel.getGraphNet().getGraphPetriPlaceList().add(handover);
        panel.getGraphNet().getGraphPetriTransitionList().add(emit);
        panel.getGraphNet().getGraphArcInList().add(GraphArcFactory.inArc(pool, emit, 1, false));
        panel.getGraphNet().getGraphArcOutList().add(GraphArcFactory.outArc(emit, handover, 1));

        GraphObjectFrame frame = new GraphObjectFrame("Source", new Rectangle(40, 40, 160, 160));
        panel.getCanvasModel().claim(frame, pool);
        panel.getCanvasModel().claim(frame, handover);
        panel.getCanvasModel().claim(frame, emit);
        panel.addObjectFrame(frame);

        // The free elements are the second object: its own place feeds its own transition.
        GraphPetriPlace intake = new GraphPetriPlace(new PetriP("Intake", 0), 3);
        intake.setNewCoordinates(new Point(400, 100));
        GraphPetriTransition consume = new GraphPetriTransition(new PetriT("Consume", 1.0), 2);
        consume.setNewCoordinates(new Point(460, 100));
        panel.getGraphNet().getGraphPetriPlaceList().add(intake);
        panel.getGraphNet().getGraphPetriTransitionList().add(consume);
        panel.getGraphNet().getGraphArcInList()
                .add(GraphArcFactory.inArc(intake, consume, 1, false));

        // Sharing "Handover" with "Intake" is what carries the tokens across the boundary.
        panel.getCanvasModel().joinPlaces(handover, intake);

        GraphPetriObjModel objModel = panel.getCanvasModel().toObjModel();
        assertEquals("the shared place is the only link between the two objects",
                1, objModel.getLinks().size());

        PetriObjModel model = objModel.createPetriObjModel("test");

        boolean consumeHasItsOwnInput = model.getListObj().stream()
                .flatMap(sim -> java.util.Arrays.stream(sim.getNet().getListT()))
                .anyMatch(t -> "Consume".equals(t.getName()) && t.hasConsumingInput());
        assertTrue("the fed transition's input is a place of its own object",
                consumeHasItsOwnInput);

        // Objects come out in frame order, with the free elements last.
        PetriP[] sourcePlaces = model.getListObj().get(0).getNet().getListP();
        PetriP[] freePlaces = model.getListObj().get(1).getNet().getListP();
        boolean sharesAnInstance = java.util.Arrays.stream(sourcePlaces).anyMatch(place ->
                java.util.Arrays.stream(freePlaces).anyMatch(other -> other == place));
        assertTrue("the shared place must be one instance held by both objects, which is what"
                + " carries the tokens across", sharesAnInstance);

        model.go(20.0);
    }

    /**
     * A transition with no input at all must still be rejected, and now that a transition can
     * only ever be fed locally there is nothing else left for it to be fed by: it would
     * otherwise fire unconditionally forever without advancing time.
     */
    @Test
    public void aTransitionWithNoInputAtAllIsStillRejected() throws Exception {
        PetriNetsPanel panel = new PetriNetsPanel(null, false);

        GraphPetriTransition orphan = new GraphPetriTransition(new PetriT("Orphan", 1.0), 1);
        orphan.setNewCoordinates(new Point(200, 200));
        panel.getGraphNet().getGraphPetriTransitionList().add(orphan);

        GraphPetriObjModel objModel = panel.getCanvasModel().toObjModel();
        try {
            objModel.createPetriObjModel("test");
            fail("a transition with no input at all must be rejected");
        } catch (ExceptionInvalidTimeDelay expected) {
            assertEquals("Transition Orphan hasn't input positions!", expected.getMessage());
        }
        assertFalse("sanity check: the orphan transition really has no consuming input",
                orphan.getPetriTransition().hasConsumingInput());
    }
}

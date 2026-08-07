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
import static org.junit.Assert.fail;

/**
 * A transition whose only input place lives inside a Petri-object, drawn outside the frame and
 * connected to it, used to be rejected before the model was ever built: {@code toObjModel()}
 * partitions an arc crossing a frame boundary into a {@code PetriObjLink} rather than either
 * object's own arc list (since neither object may see into the other's net), but the old {@code
 * PetriT.createInP} rejected an empty local input list immediately on construction — before
 * {@code PetriObjModel.addLink} ever had a chance to wire that link in as an external input.
 *
 * <p>The fix moves the "has no input at all" check to {@link PetriObjModel#validateStructure()},
 * called once every link exists, and widens it to accept a consuming external input in place of
 * a local one. A transition with no consuming input whatsoever — local or external — must still
 * be rejected: {@link ua.stetsenkoinna.petriobj.PetriT#condition} would otherwise return {@code
 * true} unconditionally and the simulation would fire it forever without ever advancing time.
 */
public class CrossFrameArcTest {

    @Test
    public void aTransitionFedOnlyThroughAFrameBoundaryIsAccepted() throws Exception {
        PetriNetsPanel panel = new PetriNetsPanel(null, false);

        GraphPetriPlace framedPlace = new GraphPetriPlace(new PetriP("Pin", 1), 1);
        framedPlace.setNewCoordinates(new Point(100, 100));
        panel.getGraphNet().getGraphPetriPlaceList().add(framedPlace);
        GraphObjectFrame frame = new GraphObjectFrame("Source", new Rectangle(40, 40, 160, 160));
        frame.addMember(framedPlace);
        panel.addObjectFrame(frame);

        GraphPetriTransition freeTransition = new GraphPetriTransition(new PetriT("Sink", 1.0), 2);
        freeTransition.setNewCoordinates(new Point(400, 100));
        panel.getGraphNet().getGraphPetriTransitionList().add(freeTransition);

        // The arc crosses the frame boundary: its place is a member of "Source", its
        // transition is free. GraphCanvasModel.toObjModel() must turn this into a link.
        panel.getGraphNet().getGraphArcInList()
                .add(GraphArcFactory.inArc(framedPlace, freeTransition, 1, false));

        GraphPetriObjModel objModel = panel.getCanvasModel().toObjModel();
        assertEquals("the crossing arc must become a link, not sit inside either object",
                1, objModel.getLinks().size());

        // This used to throw ExceptionInvalidTimeDelay before the link was ever wired in.
        PetriObjModel model = objModel.createPetriObjModel("test");

        boolean sinkHasConsumingInput = model.getListObj().stream()
                .flatMap(sim -> java.util.Arrays.stream(sim.getNet().getListT()))
                .anyMatch(t -> "Sink".equals(t.getName()) && t.hasConsumingInput());
        assertEquals("the free transition's only input must be the linked external place",
                true, sinkHasConsumingInput);
    }

    /**
     * The other half of the same change: a transition with no input at all, local or external,
     * must still be rejected — otherwise removing the old constructor-time check would trade a
     * clear startup error for a transition that fires unconditionally forever.
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
            fail("a transition with no input at all, local or external, must be rejected");
        } catch (ExceptionInvalidTimeDelay expected) {
            assertEquals("Transition Orphan hasn't input positions!", expected.getMessage());
        }
        assertFalse("sanity check: the orphan transition really has no consuming input",
                orphan.getPetriTransition().hasConsumingInput());
    }
}

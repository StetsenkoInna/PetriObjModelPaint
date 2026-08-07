package ua.stetsenkoinna.graphpresentation;

import org.junit.Test;

import ua.stetsenkoinna.graphnet.GraphArcFactory;
import ua.stetsenkoinna.graphnet.GraphObjectFrame;
import ua.stetsenkoinna.graphnet.GraphPetriNet;
import ua.stetsenkoinna.graphnet.GraphPetriObject;
import ua.stetsenkoinna.graphnet.GraphPetriPlace;
import ua.stetsenkoinna.graphnet.GraphPetriTransition;
import ua.stetsenkoinna.libnet.NetLibrary;
import ua.stetsenkoinna.petriobj.PetriNet;
import ua.stetsenkoinna.petriobj.PetriP;
import ua.stetsenkoinna.petriobj.PetriT;

import java.awt.Point;
import java.awt.Rectangle;
import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.assertEquals;

/**
 * The bug: stamp a Petri-object template, then place a free transition and place next to it and
 * connect them. {@code NetLibrary} factory methods reset PetriP/PetriT's shared numbering
 * counter back to zero right after building, so the free elements added afterward reused
 * numbers the template's own elements already held. {@code PetriNet}'s constructor resolves
 * every arc by comparing those numbers as plain ints with no identity fallback, so the collision
 * didn't just mislabel something — it silently misrouted the free transition's own input arc,
 * and the free transition came up with no input positions at all
 * ({@code ExceptionInvalidTimeDelay: "Transition T1 hasn't input positions!"}).
 */
public class PetriNumberingCollisionTest {

    @Test
    public void aFreeTransitionNextToAStampedTemplateKeepsItsOwnInputArc() throws Exception {
        PetriNetsPanel panel = new PetriNetsPanel(null, false);

        // Stamp a Generator template — same as the toolbar's "add Petri-object" tool: build via
        // NetLibrary (which ends by resetting PetriP/PetriT's counter to zero) and absorb it.
        GraphPetriNet generator = SimpleNetGraphBuilder.build(
                NetLibrary.CreateNetGenerator(2.0), new Point(100, 100));
        panel.addNet(generator);

        // Add a free place and transition next to it — same raw construction the Place/
        // Transition toolbar tools use — and connect them, exactly as reported.
        GraphPetriPlace freePlace = new GraphPetriPlace(new PetriP("P1", 1), 9001);
        freePlace.setNewCoordinates(new Point(400, 100));
        GraphPetriTransition freeTransition = new GraphPetriTransition(new PetriT("T1", 1.0), 9002);
        freeTransition.setNewCoordinates(new Point(500, 100));
        panel.getGraphNet().getGraphPetriPlaceList().add(freePlace);
        panel.getGraphNet().getGraphPetriTransitionList().add(freeTransition);
        panel.getGraphNet().getGraphArcInList()
                .add(GraphArcFactory.inArc(freePlace, freeTransition, 1, false));

        panel.getGraphNet().createPetriNet("test");

        PetriNet builtNet = panel.getGraphNet().getPetriNet();
        assertEquals("Generator's own input arc plus the free transition's",
                2, builtNet.getListIn().length);
        assertEquals("the free transition must resolve to its own place, not the template's",
                freePlace.getPetriPlace().getNumber(), builtNet.getListIn()[1].getNumP());
    }

    /**
     * The same bug report, but reaching it the way the running app does rather than by hand.
     *
     * <p>Pressing Start builds the canvas twice over: once as a whole
     * ({@code PetriNetsFrame.isCorrectNet}) and then once per Petri-object
     * ({@code getAnimRunPetriObjModel}). The per-object pass is the destructive one — each object's
     * sub-net holds the canvas's own elements, so numbering each object from zero renumbers the
     * live drawing, and the framed Generator and the free elements end up sharing numbers again.
     * The next whole-canvas build then cannot tell whose arc is whose, which is why the user saw
     * the failure on the *second* press of Start rather than the first.
     */
    @Test
    public void buildingPerObjectThenWholeCanvasAgainStillResolvesEveryArc() throws Exception {
        PetriNetsPanel panel = new PetriNetsPanel(null, false);

        // A stamped Petri-object: the Generator's net, wrapped in a frame the way the toolbar's
        // Petri-object tool wraps it.
        GraphPetriNet generator = SimpleNetGraphBuilder.build(
                NetLibrary.CreateNetGenerator(2.0), new Point(100, 100));
        panel.addNet(generator);
        GraphObjectFrame frame = new GraphObjectFrame("Generator", new Rectangle(40, 40, 260, 200));
        generator.getGraphPetriPlaceList().forEach(frame::addMember);
        generator.getGraphPetriTransitionList().forEach(frame::addMember);
        panel.addObjectFrame(frame);

        // Free elements outside the frame, connected to each other.
        GraphPetriPlace freePlace = new GraphPetriPlace(new PetriP("P1", 1), 9001);
        freePlace.setNewCoordinates(new Point(400, 100));
        GraphPetriTransition freeTransition = new GraphPetriTransition(new PetriT("T1", 1.0), 9002);
        freeTransition.setNewCoordinates(new Point(500, 100));
        panel.getGraphNet().getGraphPetriPlaceList().add(freePlace);
        panel.getGraphNet().getGraphPetriTransitionList().add(freeTransition);
        panel.getGraphNet().getGraphArcInList()
                .add(GraphArcFactory.inArc(freePlace, freeTransition, 1, false));
        panel.getGraphNet().getGraphArcOutList()
                .add(GraphArcFactory.outArc(freeTransition, freePlace, 1));

        // Press Start: whole canvas first, then once per Petri-object.
        panel.getGraphNet().createPetriNet("canvas");
        for (GraphPetriObject object : panel.getCanvasModel().toObjModel().getObjects()) {
            object.getGraphNet().createPetriNet(object.getName());
        }

        // Press Start again — this is the build that used to throw
        // "Transition T1 hasn't input positions!".
        panel.getGraphNet().createPetriNet("canvas");

        PetriNet rebuilt = panel.getGraphNet().getPetriNet();
        assertEquals("both input arcs must survive the rebuild", 2, rebuilt.getListIn().length);

        // Every transition must still own an input arc. The old code moved both transitions' arcs
        // onto whichever shared a number last, starving the other one.
        Set<Integer> transitionsWithInput = new HashSet<>();
        for (var arc : rebuilt.getListIn()) {
            transitionsWithInput.add(arc.getNumT());
        }
        assertEquals("no transition may be left without an input arc",
                rebuilt.getListT().length, transitionsWithInput.size());
    }
}

package ua.stetsenkoinna.petriobj;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

/**
 * Behaviour of the links that compose several Petri-objects into one model.
 *
 * <p>Every net used here fires with a constant delay (no distribution is set), so the
 * simulation is deterministic and the resulting markings can be asserted exactly.
 */
public class PetriObjLinkTest {

    private static final double DELAY = 1.0;
    private static final double SIMULATION_TIME = 10.0;

    /**
     * Builds {@code P0 -> T0 -> P1}, the smallest net that can both consume and produce.
     *
     * @param name net name
     * @param startTokens initial marking of {@code P0}
     */
    private static PetriNet chainNet(String name, int startTokens) throws ExceptionInvalidTimeDelay {
        resetElementCounters();
        ArrayList<PetriP> places = new ArrayList<>();
        places.add(new PetriP("P0", startTokens));
        places.add(new PetriP("P1", 0));
        ArrayList<PetriT> transitions = new ArrayList<>();
        transitions.add(new PetriT("T0", DELAY));
        ArrayList<ArcIn> arcsIn = new ArrayList<>();
        arcsIn.add(new ArcIn(places.get(0), transitions.get(0), 1));
        ArrayList<ArcOut> arcsOut = new ArrayList<>();
        arcsOut.add(new ArcOut(transitions.get(0), places.get(1), 1));
        PetriNet net = new PetriNet(name, places, transitions, arcsIn, arcsOut);
        resetElementCounters();
        return net;
    }

    /**
     * Builds a net whose own transition can never fire (its input place is empty) and that
     * owns an isolated place {@code P2} holding {@code guardTokens}. Used as the far end of
     * a place-to-transition link, where the marking must stay under the test's control.
     */
    private static PetriNet guardNet(String name, int guardTokens) throws ExceptionInvalidTimeDelay {
        resetElementCounters();
        ArrayList<PetriP> places = new ArrayList<>();
        places.add(new PetriP("P0", 0));
        places.add(new PetriP("P1", 0));
        places.add(new PetriP("P2", guardTokens));
        ArrayList<PetriT> transitions = new ArrayList<>();
        transitions.add(new PetriT("T0", DELAY));
        ArrayList<ArcIn> arcsIn = new ArrayList<>();
        arcsIn.add(new ArcIn(places.get(0), transitions.get(0), 1));
        ArrayList<ArcOut> arcsOut = new ArrayList<>();
        arcsOut.add(new ArcOut(transitions.get(0), places.get(1), 1));
        PetriNet net = new PetriNet(name, places, transitions, arcsIn, arcsOut);
        resetElementCounters();
        return net;
    }

    /**
     * Element numbers are handed out by static counters and are used as indices into the
     * net's own arrays, so each net has to start numbering from zero.
     */
    private static void resetElementCounters() {
        PetriP.initNext();
        PetriT.initNext();
        ArcIn.initNext();
        ArcOut.initNext();
    }

    private static PetriObjModel model(PetriNet... nets) {
        ArrayList<PetriSim> objects = new ArrayList<>();
        for (PetriNet net : nets) {
            objects.add(new PetriSim(net));
        }
        PetriObjModel model = new PetriObjModel(objects);
        model.setIsProtokol(false);
        return model;
    }

    private static int mark(PetriObjModel model, int object, int place) {
        return model.getListObj().get(object).getNet().getListP()[place].getMark();
    }

    @Test
    public void objectsAreIndexedByTheirPositionInTheModel() throws Exception {
        PetriObjModel model = model(chainNet("first", 0), chainNet("second", 0));

        assertEquals(0, model.getListObj().get(0).getObjIndex());
        assertEquals(1, model.getListObj().get(1).getObjIndex());
        assertEquals(1, model.getListObj().get(1).getStatisticId());
    }

    @Test
    public void placeFusionMakesTwoObjectsShareOnePlace() throws Exception {
        PetriObjModel model = model(chainNet("producer", 1), chainNet("consumer", 0));
        model.linkObjectsCombiningPlaces(0, 1, 1, 0);

        assertSame("the output place of the producer must be the input place of the consumer",
                model.getListObj().get(1).getNet().getListP()[0],
                model.getListObj().get(0).getNet().getListP()[1]);

        model.go(SIMULATION_TIME);

        assertEquals("token produced by the first object must reach the second one",
                1, mark(model, 1, 1));
    }

    @Test
    public void transitionOfOneObjectFeedsPlaceOfAnother() throws Exception {
        PetriObjModel model = model(chainNet("producer", 1), chainNet("consumer", 0));
        model.linkTransitionToPlace(0, 0, 1, 0, 2);

        model.go(SIMULATION_TIME);

        assertEquals("the producer keeps filling its own output place", 1, mark(model, 0, 1));
        assertEquals("both delivered tokens must pass through the consumer", 2, mark(model, 1, 1));
    }

    @Test
    public void informationalLinkBlocksFiringWhileForeignPlaceIsEmpty() throws Exception {
        PetriObjModel model = model(guardNet("guard", 0), chainNet("worker", 1));
        model.linkPlaceToTransition(0, 2, 1, 0, 1, true);

        model.go(SIMULATION_TIME);

        assertEquals("the worker must stay blocked", 1, mark(model, 1, 0));
        assertEquals(0, mark(model, 1, 1));
    }

    @Test
    public void informationalLinkPermitsFiringWithoutConsumingTheForeignPlace() throws Exception {
        PetriObjModel model = model(guardNet("guard", 1), chainNet("worker", 2));
        model.linkPlaceToTransition(0, 2, 1, 0, 1, true);

        model.go(SIMULATION_TIME);

        assertEquals("a test arc must leave the foreign marking untouched", 1, mark(model, 0, 2));
        assertEquals(2, mark(model, 1, 1));
    }

    @Test
    public void consumingLinkTakesTokensFromTheForeignPlace() throws Exception {
        PetriObjModel model = model(guardNet("resource", 2), chainNet("worker", 2));
        model.linkPlaceToTransition(0, 2, 1, 0, 1, false);

        model.go(SIMULATION_TIME);

        assertEquals("each firing must consume one foreign token", 0, mark(model, 0, 2));
        assertEquals(2, mark(model, 1, 1));
    }

    @Test
    public void consumingLinkStopsTheObjectWhenTheForeignPlaceRunsOut() throws Exception {
        PetriObjModel model = model(guardNet("resource", 1), chainNet("worker", 3));
        model.linkPlaceToTransition(0, 2, 1, 0, 1, false);

        model.go(SIMULATION_TIME);

        assertEquals(0, mark(model, 0, 2));
        assertEquals("only one token could be served", 1, mark(model, 1, 1));
        assertEquals("the rest must stay in the queue", 2, mark(model, 1, 0));
    }

    @Test
    public void cloneRebuildsEveryLinkOnItsOwnCopies() throws Exception {
        PetriObjModel original = model(chainNet("producer", 1), chainNet("consumer", 0));
        original.linkObjectsCombiningPlaces(0, 1, 1, 0);
        original.linkTransitionToPlace(0, 0, 1, 1, 1);

        PetriObjModel copy = original.clone();
        copy.setIsProtokol(false);

        assertNotSame(original.getListObj().get(0), copy.getListObj().get(0));
        assertSame("the copy must share its own place, not the original's",
                copy.getListObj().get(1).getNet().getListP()[0],
                copy.getListObj().get(0).getNet().getListP()[1]);

        List<ExternalArc> copied = copy.getListObj().get(0).getNet().getListT()[0].getExternalOutputs();
        assertEquals(1, copied.size());
        assertSame(copy.getListObj().get(1).getNet().getListP()[1], copied.getFirst().getPlace());

        copy.go(SIMULATION_TIME);

        assertEquals("running the copy must not disturb the original",
                1, mark(original, 0, 0));
        assertTrue(mark(copy, 1, 1) > 0);
    }

    @Test
    public void modelKeepsTheDeclarationsItWasGiven() throws Exception {
        PetriObjModel model = model(chainNet("producer", 1), chainNet("consumer", 0));
        model.linkObjectsCombiningPlaces(0, 1, 1, 0);
        model.linkPlaceToTransition(1, 1, 0, 0, 2, true);

        assertEquals(2, model.getLinks().size());
        assertEquals(PetriObjLinkType.PLACE_FUSION, model.getLinks().get(0).getType());
        assertEquals(PetriObjLinkType.PLACE_TO_TRANSITION, model.getLinks().get(1).getType());
        assertEquals(2, model.getLinks().get(1).getQuantity());
        assertTrue(model.getLinks().get(1).isInformational());
    }

    @Test
    public void linkToAMissingElementIsRejected() throws Exception {
        PetriObjModel model = model(chainNet("producer", 1), chainNet("consumer", 0));

        assertThrows(IllegalArgumentException.class,
                () -> model.linkObjectsCombiningPlaces(0, 1, 1, 7));
        assertThrows(IllegalArgumentException.class,
                () -> model.linkTransitionToPlace(5, 0, 1, 0, 1));
    }
}

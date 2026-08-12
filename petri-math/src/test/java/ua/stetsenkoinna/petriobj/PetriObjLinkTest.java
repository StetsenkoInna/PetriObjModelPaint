package ua.stetsenkoinna.petriobj;

import org.junit.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

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

    @Test
    public void firingPhasesArriveInMatchedPairs() throws Exception {
        PhaseRecorder recorder = new PhaseRecorder();
        runLinkedPair(recorder);

        assertTrue("a linked pair must fire at least once", recorder.phases.size() >= 4);
        assertEquals("a firing is reported as complete pairs", 0, recorder.phases.size() % 2);

        for (int i = 0; i < recorder.phases.size(); i += 2) {
            RecordedPhase before = recorder.phases.get(i);
            RecordedPhase after = recorder.phases.get(i + 1);
            FiringPhase expectedAfter = before.phase() == FiringPhase.BEFORE_ACT_IN
                    ? FiringPhase.AFTER_ACT_IN
                    : FiringPhase.AFTER_ACT_OUT;

            assertTrue("a pair must open with a BEFORE phase, got " + before.phase(),
                    before.phase() == FiringPhase.BEFORE_ACT_IN
                            || before.phase() == FiringPhase.BEFORE_ACT_OUT);
            assertEquals("a BEFORE phase must be followed by its own AFTER phase",
                    expectedAfter, after.phase());
            assertEquals("both halves of a pair belong to the same transition",
                    before.transitionId(), after.transitionId());
        }
    }

    @Test
    public void actInIsReportedBeforeAndAfterTheTokenIsConsumed() throws Exception {
        PhaseRecorder recorder = new PhaseRecorder();
        PetriObjModel model = runLinkedPair(recorder);

        String startPlace = model.getListObj().getFirst().getNet().getListP()[0].getId();
        String transition = model.getListObj().getFirst().getNet().getListT()[0].getId();

        RecordedPhase before = recorder.first(FiringPhase.BEFORE_ACT_IN, transition);
        RecordedPhase after = recorder.next(before, FiringPhase.AFTER_ACT_IN);

        assertEquals("the token is still in the input place when the input arc is highlighted",
                Integer.valueOf(1), before.markings().get(startPlace));
        assertEquals("the token is gone once the transition is highlighted",
                Integer.valueOf(0), after.markings().get(startPlace));
        assertEquals("the transition holds one more token than before",
                before.buffers().get(transition) + 1, (int) after.buffers().get(transition));
    }

    @Test
    public void actOutOpensOnExactlyTheStateActInLeftBehind() throws Exception {
        PhaseRecorder recorder = new PhaseRecorder();
        PetriObjModel model = runLinkedPair(recorder);

        String transition = model.getListObj().getFirst().getNet().getListT()[0].getId();
        RecordedPhase afterIn = recorder.next(
                recorder.first(FiringPhase.BEFORE_ACT_IN, transition), FiringPhase.AFTER_ACT_IN);
        RecordedPhase beforeOut = recorder.first(FiringPhase.BEFORE_ACT_OUT, transition);

        assertEquals("holding a token in a transition must not move any marking",
                afterIn.markings(), beforeOut.markings());
        assertEquals("nor any buffer", afterIn.buffers(), beforeOut.buffers());
    }

    @Test
    public void everyPhaseSpansTheWholeModelNotOnlyTheFiringObject() throws Exception {
        PhaseRecorder recorder = new PhaseRecorder();
        PetriObjModel model = runLinkedPair(recorder);

        Set<String> everyPlace = new LinkedHashSet<>();
        Set<String> everyTransition = new LinkedHashSet<>();
        for (PetriSim sim : model.getListObj()) {
            for (PetriP p : sim.getNet().getListP()) {
                everyPlace.add(p.getId());
            }
            for (PetriT t : sim.getNet().getListT()) {
                everyTransition.add(t.getId());
            }
        }

        for (RecordedPhase phase : recorder.phases) {
            assertEquals("a phase must report every place of the model",
                    everyPlace, phase.markings().keySet());
            assertEquals("a phase must report every transition of the model",
                    everyTransition, phase.buffers().keySet());
        }

        // The assertion above only bites because the two objects have places of their own: a
        // callback narrowed to the firing object would keep reporting its half and drop these.
        String consumerPlace = model.getListObj().get(1).getNet().getListP()[1].getId();
        String producerTransition = model.getListObj().getFirst().getNet().getListT()[0].getId();
        RecordedPhase producerPhase = recorder.first(FiringPhase.BEFORE_ACT_IN, producerTransition);
        assertTrue("the producer's own firing must still report the consumer's places",
                producerPhase.markings().containsKey(consumerPlace));
    }

    @Test
    public void noPairIsReportedForAFiringThatChangedNothing() throws Exception {
        PhaseRecorder recorder = new PhaseRecorder();
        runLinkedPair(recorder);

        for (int i = 0; i < recorder.phases.size(); i += 2) {
            RecordedPhase before = recorder.phases.get(i);
            RecordedPhase after = recorder.phases.get(i + 1);
            assertTrue("a pair that moved neither a marking nor a buffer is a firing that never"
                            + " happened — " + before.phase() + " on " + before.transitionId(),
                    !before.markings().equals(after.markings())
                            || !before.buffers().equals(after.buffers()));
        }
    }

    /**
     * Runs a producer whose transition feeds a consumer of its own, so the phases of one
     * object's firing have another object's elements to report alongside their own.
     */
    private PetriObjModel runLinkedPair(PhaseRecorder recorder) throws Exception {
        PetriObjModel model = model(chainNet("producer", 1), chainNet("consumer", 0));
        model.linkTransitionToPlace(0, 0, 1, 0, 1);
        model.setStatisticCollector(recorder);

        model.go(SIMULATION_TIME);
        return model;
    }

    /** One reported phase, with the model-wide state as it stood at that instant. */
    private record RecordedPhase(double time, FiringPhase phase, String transitionId,
                                 Map<String, Integer> markings, Map<String, Integer> buffers) {}

    /**
     * Keeps every phase of a run. The collector's own arrays keep moving, so each phase is
     * copied out on the spot — which is exactly the contract the callback documents.
     */
    private static final class PhaseRecorder implements SimulationStatisticCollector {

        private final List<RecordedPhase> phases = new ArrayList<>();

        @Override
        public void onFiringPhase(double time, FiringPhase phase, PetriT transition,
                                  Iterable<PetriSim> objects) {
            Map<String, Integer> markings = new LinkedHashMap<>();
            Map<String, Integer> buffers = new LinkedHashMap<>();
            for (PetriSim sim : objects) {
                for (PetriP p : sim.getNet().getListP()) {
                    markings.put(p.getId(), p.getMark());
                }
                for (PetriT t : sim.getNet().getListT()) {
                    buffers.put(t.getId(), t.getBuffer());
                }
            }
            phases.add(new RecordedPhase(time, phase, transition.getId(), markings, buffers));
        }

        RecordedPhase first(FiringPhase phase, String transitionId) {
            for (RecordedPhase recorded : phases) {
                if (recorded.phase() == phase && recorded.transitionId().equals(transitionId)) {
                    return recorded;
                }
            }
            throw new AssertionError("no " + phase + " was reported for " + transitionId);
        }

        RecordedPhase next(RecordedPhase after, FiringPhase phase) {
            int index = phases.indexOf(after) + 1;
            assertTrue("nothing follows " + after.phase(), index < phases.size());
            assertEquals(phase, phases.get(index).phase());
            return phases.get(index);
        }

        @Override
        public boolean shouldCollect(double currentTime) {
            return false;
        }

        @Override
        public void onTimeStep(double currentTime, PetriNet net, int petriObjId) {
        }

        @Override
        public void flush(double currentTime) {
        }

        @Override
        public void onSimulationEnd(double simulationEndTime, Iterable<PetriSim> objects) {
        }

        @Override
        public void shutdown() {
        }
    }
}

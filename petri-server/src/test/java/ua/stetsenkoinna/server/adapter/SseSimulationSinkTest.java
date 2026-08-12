package ua.stetsenkoinna.server.adapter;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import ua.stetsenkoinna.petriobj.ArcIn;
import ua.stetsenkoinna.petriobj.ArcOut;
import ua.stetsenkoinna.petriobj.FiringPhase;
import ua.stetsenkoinna.petriobj.PetriNet;
import ua.stetsenkoinna.petriobj.PetriObjModel;
import ua.stetsenkoinna.petriobj.PetriP;
import ua.stetsenkoinna.petriobj.PetriSim;
import ua.stetsenkoinna.petriobj.PetriT;
import ua.stetsenkoinna.server.service.SimulationSession;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What a streaming client actually gets out of a run: the snapshot it always got, plus the
 * atomic steps that explain how the model reached it.
 */
class SseSimulationSinkTest {

    /** {@code P0 -> T0 -> P1} with a constant delay, so a run is deterministic. */
    private static PetriNet chainNet(String name, int startTokens) throws Exception {
        PetriP.initNext();
        PetriT.initNext();
        ArcIn.initNext();
        ArcOut.initNext();
        ArrayList<PetriP> places = new ArrayList<>();
        places.add(new PetriP("P0", startTokens));
        places.add(new PetriP("P1", 0));
        ArrayList<PetriT> transitions = new ArrayList<>();
        transitions.add(new PetriT("T0", 1.0));
        ArrayList<ArcIn> arcsIn = new ArrayList<>();
        arcsIn.add(new ArcIn(places.get(0), transitions.get(0), 1));
        ArrayList<ArcOut> arcsOut = new ArrayList<>();
        arcsOut.add(new ArcOut(transitions.get(0), places.get(1), 1));
        return new PetriNet(name, places, transitions, arcsIn, arcsOut);
    }

    private static PetriObjModel twoObjectModel() throws Exception {
        ArrayList<PetriSim> objects = new ArrayList<>();
        objects.add(new PetriSim(chainNet("Source", 3)));
        objects.add(new PetriSim(chainNet("Sink", 0)));
        PetriObjModel model = new PetriObjModel(objects);
        model.setIsProtokol(false);
        model.linkObjectsCombiningPlaces(0, 1, 1, 0);
        return model;
    }

    @Test
    void framesCarryTheFiringStepsOfTheirOwnInterval() throws Exception {
        LinkedBlockingQueue<Optional<SimulationFrame>> queue = new LinkedBlockingQueue<>(2000);
        PetriObjModel model = twoObjectModel();
        model.setStatisticCollector(new SseSimulationSink(queue, new SimulationSession("test"),
                1.0, null, 10.0, new AtomicInteger()));

        model.go(10.0);

        List<SimulationFrame> frames = drain(queue);
        assertFalse(frames.isEmpty(), "a run must produce frames");

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

        Set<String> seenPhases = new LinkedHashSet<>();
        boolean anyAnimated = false;
        for (SimulationFrame frame : frames) {
            assertNotNull(frame.firedTransitions(), "an empty interval is [], never null");
            assertNotNull(frame.firingSequence(), "an empty interval is [], never null");

            Set<String> firedInSequence = new LinkedHashSet<>();
            for (FiringStepDto step : frame.firingSequence()) {
                seenPhases.add(step.phase());
                firedInSequence.add(step.transitionId());
                assertEquals(everyPlace, step.markings().keySet(),
                        "a step reports the whole model's markings, not the firing object's");
                assertEquals(everyTransition, step.buffers().keySet(),
                        "a step reports the whole model's buffers, not the firing object's");
            }
            assertEquals(firedInSequence, new LinkedHashSet<>(frame.firedTransitions()),
                    "fired_transitions is derived from the steps, so it cannot drift from them");

            if (!frame.firingSequence().isEmpty()) {
                anyAnimated = true;
                // The invariant a replaying client depends on: it drops the frame's own state
                // when there are steps to play, so the last step has to land on that state.
                FiringStepDto last = frame.firingSequence().getLast();
                assertEquals(frame.markings(), last.markings(),
                        "replaying a frame's steps must end on the frame's own markings");
                assertEquals(frame.buffers(), last.buffers(),
                        "replaying a frame's steps must end on the frame's own buffers");
            }
        }

        assertTrue(anyAnimated, "a model that fires must put steps on the wire");
        Set<String> expectedPhases = new LinkedHashSet<>();
        for (FiringPhase phase : FiringPhase.values()) {
            expectedPhases.add(phase.wireName());
        }
        assertEquals(expectedPhases, seenPhases,
                "a full produce-and-consume run visits all four phases");
    }

    @Test
    void phasesOfARejectedFrameRideTheNextOneInstead() throws Exception {
        LinkedBlockingQueue<Optional<SimulationFrame>> queue = new LinkedBlockingQueue<>(1);
        PetriObjModel model = twoObjectModel();
        SseSimulationSink sink = new SseSimulationSink(queue, new SimulationSession("test"),
                1.0, 1, 10.0, new AtomicInteger());
        PetriSim source = model.getListObj().getFirst();
        PetriT transition = source.getNet().getListT()[0];

        sink.onFiringPhase(1.0, FiringPhase.BEFORE_ACT_IN, transition, model.getListObj());
        sink.onFiringPhase(1.0, FiringPhase.AFTER_ACT_IN, transition, model.getListObj());
        sink.onTimeStep(1.0, source.getNet(), 0);

        queue.offer(Optional.empty());   // the writer is behind: nothing more fits
        sink.flush(1.0);
        assertEquals(1, queue.size(), "the frame had nowhere to go");

        queue.clear();
        sink.onTimeStep(2.0, source.getNet(), 0);
        sink.flush(2.0);

        SimulationFrame recovered = queue.poll().orElseThrow();
        assertEquals(2, recovered.firingSequence().size(),
                "steps of a dropped frame belong to the next one, not to nobody");
        assertEquals(List.of(transition.getId()), recovered.firedTransitions());
    }

    @Test
    void anExhaustedBudgetTrimsToTheNewestStepsRatherThanDroppingThemAll() throws Exception {
        LinkedBlockingQueue<Optional<SimulationFrame>> queue = new LinkedBlockingQueue<>(10);
        PetriObjModel model = twoObjectModel();
        AtomicInteger inFlight = new AtomicInteger();
        SseSimulationSink sink = new SseSimulationSink(queue, new SimulationSession("test"),
                1.0, 1, 10.0, inFlight);
        PetriSim source = model.getListObj().getFirst();
        PetriT transition = source.getNet().getListT()[0];

        // The budget is worked out from the model on the first phase, so the writer can only be
        // wound up to exactly one free slot once that has happened.
        sink.onFiringPhase(1.0, FiringPhase.BEFORE_ACT_IN, transition, model.getListObj());
        inFlight.set(sink.maxInFlightPhases() - 1);
        sink.onFiringPhase(1.0, FiringPhase.AFTER_ACT_IN, transition, model.getListObj());
        sink.onFiringPhase(1.0, FiringPhase.BEFORE_ACT_OUT, transition, model.getListObj());
        sink.onTimeStep(1.0, source.getNet(), 0);
        sink.flush(1.0);

        SimulationFrame frame = queue.poll().orElseThrow();
        assertEquals(1, frame.firingSequence().size(),
                "one free slot buys one step; dropping the interval whole would leave a big "
                        + "model with no animation at all rather than a coarser one");
        assertEquals(FiringPhase.BEFORE_ACT_OUT.wireName(),
                frame.firingSequence().getFirst().phase(),
                "the tail is kept, because that is what ends on the frame's own state");
    }

    @Test
    void aFrameSerializesUnderTheNamesTheContractPromises() throws Exception {
        SimulationFrame frame = new SimulationFrame(1.5, 7, Map.of("p1", 2), Map.of("t1", 0), 0.15,
                List.of("t1"),
                List.of(new FiringStepDto("t1", FiringPhase.BEFORE_ACT_IN.wireName(),
                        Map.of("p1", 2), Map.of("t1", 0), 1.5)));

        String json = new ObjectMapper().writeValueAsString(frame);

        for (String key : List.of("current_time", "step_number", "markings", "buffers", "progress",
                "fired_transitions", "firing_sequence", "transition_id", "phase", "time")) {
            assertTrue(json.contains("\"" + key + "\""), json + " is missing " + key);
        }
        assertTrue(json.contains("\"before_act_in\""), "the phase travels as its wire name");
    }

    private static List<SimulationFrame> drain(LinkedBlockingQueue<Optional<SimulationFrame>> queue) {
        List<SimulationFrame> frames = new ArrayList<>();
        for (Optional<SimulationFrame> frame : queue) {
            frame.ifPresent(frames::add);
        }
        return frames;
    }
}

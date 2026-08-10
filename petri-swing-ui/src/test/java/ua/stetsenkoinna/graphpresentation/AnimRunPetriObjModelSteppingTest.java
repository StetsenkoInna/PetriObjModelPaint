package ua.stetsenkoinna.graphpresentation;

import org.junit.Test;

import ua.stetsenkoinna.graphnet.GraphPetriNet;
import ua.stetsenkoinna.petriobj.ArcIn;
import ua.stetsenkoinna.petriobj.ArcOut;
import ua.stetsenkoinna.petriobj.PetriNet;
import ua.stetsenkoinna.petriobj.PetriP;
import ua.stetsenkoinna.petriobj.PetriT;
import ua.stetsenkoinna.petriobj.StateTime;

import javax.swing.JTextArea;
import java.awt.Point;
import java.util.ArrayList;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Pins the "step forward" contract: exactly one event advances the model, then it holds —
 * never bleeding into the next event, and never running on unattended.
 *
 * <p>The net loops on itself indefinitely (T0 hands its own token straight back to P0), so if
 * a step's re-pause boundary is not clean, the run does not just nudge forward a little
 * further than expected; it runs away until {@link AnimRunPetriObjModel#halt()} is called at
 * the end of the test — a much harder failure to miss than an off-by-one in the advanced time.
 *
 * <p>Animation itself is skipped via a {@link PetriNetsPanel} subclass that no-ops the four
 * {@code animate*} hooks: they carry their own fixed per-frame {@code Thread.sleep} calls
 * (several seconds per real event) that are orthogonal to what is under test here — the
 * pause/step/resume state machine, not the visual animation.
 */
public class AnimRunPetriObjModelSteppingTest {

    /** {@code P0 -> T0 -> P0}: one token cycling through the same transition forever. */
    private static PetriNet loopingNet() throws Exception {
        PetriP.initNext();
        PetriT.initNext();
        ArcIn.initNext();
        ArcOut.initNext();
        ArrayList<PetriP> places = new ArrayList<>();
        places.add(new PetriP("P0", 1));
        ArrayList<PetriT> transitions = new ArrayList<>();
        transitions.add(new PetriT("T0", 1.0));
        ArrayList<ArcIn> arcsIn = new ArrayList<>();
        arcsIn.add(new ArcIn(places.get(0), transitions.get(0), 1));
        ArrayList<ArcOut> arcsOut = new ArrayList<>();
        arcsOut.add(new ArcOut(transitions.get(0), places.get(0), 1));
        return new PetriNet("Loop", places, transitions, arcsIn, arcsOut);
    }

    private static final class SilentPanel extends PetriNetsPanel {
        SilentPanel() {
            super(null, false);
        }

        @Override
        public void animateIn(PetriT tr, GraphPetriNet scope) {
        }

        @Override
        public void animateT(PetriT tr, GraphPetriNet scope) {
        }

        @Override
        public void animateP(ArrayList<Integer> inP, GraphPetriNet scope) {
        }

        @Override
        public void animateOut(PetriT eventMin, GraphPetriNet scope) {
        }
    }

    @Test
    public void stepOnceAdvancesExactlyOneEventThenHolds() throws Exception {
        PetriNet net = loopingNet();
        PetriNetsPanel panel = new SilentPanel();

        AnimRunPetriSim sim = new AnimRunPetriSim(net, new StateTime(), new JTextArea(), panel, null, null, null);
        ArrayList<AnimRunPetriSim> runlist = new ArrayList<>();
        runlist.add(sim);
        AnimRunPetriObjModel model = new AnimRunPetriObjModel(runlist, new JTextArea());
        sim.setParentModel(model);

        // Long enough that the net — which loops on itself and never runs out of events on its
        // own — would keep firing for the rest of the test if a step failed to hold it.
        model.setSimulationTime(1_000_000.0);

        // Armed before go() starts, exactly as PetriNetsFrame.animateNet() does for a step
        // pressed from a standing start.
        model.stepOnce();

        final Throwable[] failure = new Throwable[1];
        Thread runner = new Thread(() -> {
            try {
                model.go(1_000_000.0);
            } catch (Throwable t) {
                failure[0] = t;
            }
        });
        runner.setDaemon(true);
        runner.start();

        try {
            double afterStep1 = waitUntilPaused(model);
            assertEquals("one event of this net advances time by exactly its service time",
                    1.0, afterStep1, 0.0001);

            // The run-away failure mode: if the pause boundary lands mid-event instead of
            // between events, the net keeps going past where the step should have stopped it.
            Thread.sleep(150);
            assertEquals("holding after one step must not let more events through",
                    afterStep1, model.getCurrentTime(), 0.0);

            model.stepOnce();
            double afterStep2 = waitUntilPaused(model);
            assertEquals("a second step advances by exactly one more service time",
                    2.0, afterStep2, 0.0001);
            assertTrue(afterStep2 > afterStep1);

            Thread.sleep(150);
            assertEquals("holding after the second step must not let more events through",
                    afterStep2, model.getCurrentTime(), 0.0);
        } finally {
            model.halt();
            runner.join(2000);
            if (failure[0] != null) {
                throw new AssertionError("go() threw on the runner thread", failure[0]);
            }
        }
    }

    private static double waitUntilPaused(AnimRunPetriObjModel model) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 2000;
        while (!model.isPaused()) {
            if (System.currentTimeMillis() > deadline) {
                fail("model did not pause within the expected time; currentTime="
                        + model.getCurrentTime() + " halted=" + model.isHalted());
            }
            Thread.sleep(5);
        }
        return model.getCurrentTime();
    }
}

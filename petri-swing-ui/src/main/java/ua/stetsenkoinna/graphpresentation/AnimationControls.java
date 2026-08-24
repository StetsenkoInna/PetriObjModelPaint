package ua.stetsenkoinna.graphpresentation;

import ua.stetsenkoinna.graphnet.GraphCanvasModel;
import ua.stetsenkoinna.graphpresentation.actions.PlayPauseAction;
import ua.stetsenkoinna.graphpresentation.actions.RunNetAction;
import ua.stetsenkoinna.graphpresentation.actions.RunOneEventAction;
import ua.stetsenkoinna.graphpresentation.actions.StepBackAction;
import ua.stetsenkoinna.graphpresentation.actions.StopSimulationAction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

/**
 * Drives the net's animation/simulation lifecycle and keeps the transport buttons (play,
 * pause, stop, step, run) in sync with it. Modeled as a small finite-state machine: every
 * button press is only legal from certain {@link State}s, and every transition ends by
 * re-deriving which buttons should be enabled from the new state.
 */
public class AnimationControls {

    private static final Logger log = LoggerFactory.getLogger(AnimationControls.class);

    public static enum State {
        /**
         * Nothing to restore: either the controls haven't been touched yet, or the last
         * run ended with "Stop".
         */
        NO_SAVED_STATE,
        /**
         * A restorable snapshot exists, but no animation is currently paused. The canvas may
         * differ from that snapshot because a run finished on its own, or because a rewind
         * button (>> or >>|) was used.
         */
        SAVED_STATE_EXISTS,
        /** An animated run is playing right now. */
        ANIMATION_IN_PROGRESS,
        /** An animated run is frozen mid-flight and can be resumed, stepped, or stopped. */
        ANIMATION_PAUSED,
        /**
         * Every transport button is locked out. Reached while a non-animated run is in
         * flight.
         */
        CONTROLS_BLOCKED,
    }

    private final PetriNetsFrame hostFrame;

    private volatile State phase;

    /**
     * True from the moment Stop is pressed on an in-progress run until that run's own
     * background thread actually notices and unwinds. Distinguishes a user-requested Stop
     * from a Rewind-triggered halt() — both flip the same model's halted flag, but only Stop
     * should make the thread's own cleanup restore the pre-run snapshot; Rewind already does
     * that itself, synchronously, because it's only ever legal once nothing is still running.
     */
    private volatile boolean stopRequested;

    /**
     * Per-event undo history for the step-back button: one canvas snapshot pushed right
     * before every forward step (cold single-step or live-run stepOnce() alike). Popped by
     * {@link #stepBackButtonPressed}; cleared at the start of every new run in {@link
     * #saveCurrentNetState}. Once empty, stepping back falls all the way to the single
     * pre-run snapshot in {@link GraphPetriNetBackupHolder} instead.
     *
     * <p>Snapshots the whole {@link GraphCanvasModel} — net, Petri-object frames and shared
     * places alike — not just the bare net: a snapshot of the net alone has no notion of
     * frames, so restoring one used to bring every place and transition back as loose elements
     * with the Petri-object that held them gone.
     */
    private final Deque<GraphCanvasModel> stepHistory = new ArrayDeque<>();

    // The transport actions, in toolbar order: |<< step back, >/|| play-pause,
    // [] stop, >| one event forward, >>| run to completion.
    public final StepBackAction stepBackAction;
    public final PlayPauseAction playPauseAction;
    public final StopSimulationAction stopSimulationAction;
    public final RunOneEventAction runOneEventAction;
    public final RunNetAction runNetAction;

    private static final String ILLEGAL_ACTION_MESSAGE = "Illegal action on AnimationControls. Current state: %s, attempted action: %s";

    public AnimationControls(PetriNetsFrame hostFrame) {
        this.hostFrame = hostFrame;

        stepBackAction = new StepBackAction(this);
        playPauseAction = new PlayPauseAction(this);
        stopSimulationAction = new StopSimulationAction(this);
        runOneEventAction = new RunOneEventAction(this);
        runNetAction = new RunNetAction(this);

        setState(State.NO_SAVED_STATE);
    }

    /**
     * Puts the controls back where they start for a brand-new document. Everything they hold
     * — the pre-run snapshot, the per-event step history — describes a net that is about to be
     * discarded, and restoring any of it onto the new one would resurrect the old drawing.
     *
     * <p>Only safe with nothing running, which is the case wherever this is called from: the
     * menus that lead here are disabled for the duration of a run.
     */
    public void resetForNewDocument() {
        stopRequested = false;
        stepHistory.clear();
        clearSavedState();
        setState(State.NO_SAVED_STATE);
    }

    /**
     * A handler for "step forward" (>|) button — always animated, always against the one real
     * animated run, so a step looks exactly like Start-then-Pause: same element highlighting,
     * same timing, same statistics. If a run is already going (playing or paused) this nudges
     * that same run forward one event; with nothing running yet it starts one that advances a
     * single event and pauses itself, rather than a disconnected one-off simulation.
     */
    public void runOneEventButtonPressed() {
        requireState(
                new State[] { State.NO_SAVED_STATE, State.SAVED_STATE_EXISTS,
                        State.ANIMATION_IN_PROGRESS, State.ANIMATION_PAUSED },
                "runOneEvent");

        if (hostFrame.animationModel != null) {
            // A live run is already in flight — nudge it forward one event in place.
            // stepOnce() re-pauses it on its own once that one event finishes.
            pushStepHistory();
            hostFrame.animationModel.stepOnce();
            setState(State.ANIMATION_PAUSED);
            return;
        }

        // Nothing running: start a real animation, pre-armed to stop itself after one event.
        // Deliberately does NOT re-save from SAVED_STATE_EXISTS — that would drop both the
        // original pre-run snapshot and the step history built up so far.
        if (phase == State.NO_SAVED_STATE) {
            saveCurrentNetState();
        }
        pushStepHistory();
        hostFrame.startAnimationStepping = true;
        initializeAnimation(State.ANIMATION_PAUSED);
    }

    /**
     * A handler for "step back" button — undoes the most recent forward step by popping the
     * per-event history stack, or, once that stack is empty, falls all the way back to the
     * single state saved just before the run started. Always ends up at a plain restored net
     * with no live run attached: true reverse-simulation isn't possible (which transition
     * fires on a tie is sometimes a random choice, not invertible), so stepping backward can
     * only ever mean "go look at an earlier snapshot," never "rewind the live run's own
     * internal state" — resuming from there (via Start) always begins a fresh run.
     */
    public void stepBackButtonPressed() {
        requireState(
                new State[] { State.ANIMATION_PAUSED, State.SAVED_STATE_EXISTS },
                "stepBack");

        // A paused run is still technically alive, so it has to be torn down before we can
        // reach into its history.
        if (phase == State.ANIMATION_PAUSED) {
            haltAnimation();
        }

        if (!stepHistory.isEmpty()) {
            restoreCanvas(stepHistory.pop());
            if (stepHistory.isEmpty()) {
                // the pre-run snapshot is now redundant with what was just restored
                clearSavedState();
                setState(State.NO_SAVED_STATE);
            } else {
                setState(State.SAVED_STATE_EXISTS);
            }
        } else {
            restoreSavedState();
            setState(State.NO_SAVED_STATE);
        }
    }

    /**
     * Toggles between starting, pausing and resuming — whichever one applies to the current
     * state.
     */
    public void playPauseButtonPressed() {
        requireState(
                new State[] { State.NO_SAVED_STATE, State.SAVED_STATE_EXISTS,
                        State.ANIMATION_PAUSED, State.ANIMATION_IN_PROGRESS },
                "playPause");

        if (phase == State.NO_SAVED_STATE || phase == State.SAVED_STATE_EXISTS) {
            // Either a fresh canvas or one sitting at a completed/rewound/stepped-back state —
            // either way there's no live run left to resume, so this always means starting a
            // new one from whatever is currently on screen. Re-saving is deliberately skipped
            // when a snapshot already exists: Stop should still rewind all the way to where
            // the very first run began, not to wherever stepping happened to leave off.
            if (phase == State.NO_SAVED_STATE) {
                saveCurrentNetState();
            }
            initializeAnimation(State.ANIMATION_IN_PROGRESS);
            return;
        }

        if (phase == State.ANIMATION_PAUSED) {
            resumeAnimation();
        } else if (phase == State.ANIMATION_IN_PROGRESS) {
            pauseAnimation();
        }
    }

    /**
     * Initialize and run net animation.
     *
     * @param initialState the state to enter as the run starts — {@code ANIMATION_IN_PROGRESS}
     *        for a normal Start, or {@code ANIMATION_PAUSED} when the model has been pre-armed
     *        to pause itself after one event (a step from a standing start). Set before the
     *        thread starts rather than after, so it cannot land on top of the state the
     *        thread's own cleanup sets when a net turns out to be invalid.
     */
    private void initializeAnimation(State initialState) {
        hostFrame.animationThread = new Thread(() -> {
            try {
                hostFrame.disableInput();
                hostFrame.timer.start();
                hostFrame.animateNet();
            } catch (Exception e) {
                log.error("Animation control error", e);
            } finally {
                hostFrame.enableInput();
                hostFrame.timer.stop();

                if (hostFrame.animationModel == null) {
                    // the net was rejected before the run ever got going
                    setState(State.NO_SAVED_STATE);
                } else if (stopRequested) {
                    // The loop has now actually exited — only safe to touch the net's state
                    // once we know this thread is done mutating it, which is exactly now.
                    restoreSavedState();
                    stopRequested = false;
                    setState(State.NO_SAVED_STATE);
                } else if (!hostFrame.animationModel.isHalted()
                        && (phase == State.ANIMATION_IN_PROGRESS
                                || phase == State.ANIMATION_PAUSED)) {
                    // Ran to completion on its own. ANIMATION_PAUSED counts here too: a run
                    // armed to pause after one event still reaches this point if the net had
                    // no events left to begin with, and leaving it looking paused with no
                    // model behind it would strand every control.
                    setState(State.SAVED_STATE_EXISTS);
                }
                // else: halted some other way (a step back while paused) — that caller already
                // restored the state and transitioned it itself.

                hostFrame.animationModel = null;
            }

        });
        setState(initialState);
        hostFrame.animationThread.start();
    }

    private void resumeAnimation() {
        setState(State.ANIMATION_IN_PROGRESS);

        if (hostFrame.animationModel != null) {
            hostFrame.animationModel.setPaused(false);

            synchronized (hostFrame.animationModel) { // TODO: replace with something better
                hostFrame.animationModel.notifyAll();
            }
        }
    }

    private void pauseAnimation() {
        if (hostFrame.animationModel != null) {
            hostFrame.animationModel.setPaused(true);
        }

        setState(State.ANIMATION_PAUSED);
    }

    private void haltAnimation() {
        if (hostFrame.animationModel != null) {
            hostFrame.animationModel.halt();
        }
    }

    /**
     * A handler for the "stop" action — interrupts whatever is running (animation, paused or
     * not, or a non-animated Run Net) and rolls the net back to the snapshot taken just
     * before it started. When nothing is actually running, it just drops that snapshot.
     */
    public void stopSimulationButtonPressed() {
        requireState(
                new State[] { State.SAVED_STATE_EXISTS, State.ANIMATION_PAUSED,
                        State.ANIMATION_IN_PROGRESS, State.CONTROLS_BLOCKED },
                "stopSimulation");

        switch (phase) {
            case ANIMATION_PAUSED:
            case ANIMATION_IN_PROGRESS:
                // Either way the run's own background thread is still "in flight" (a paused
                // one is merely parked in wait(), not gone) — haltAnimation() wakes it and
                // flips its halted flag, and its own finally block (initializeAnimation() /
                // animateEventButtonPressed()) does the actual restore once it has genuinely
                // stopped touching the net. Restoring from here instead, on the EDT, would
                // race a still-running thread.
                //
                // CONTROLS_BLOCKED locks every other action out in the meantime — without
                // that, e.g. Play/Pause would stay enabled (ANIMATION_PAUSED leaves it so)
                // and clicking it before the halt actually lands would resume a model that's
                // already being torn down.
                stopRequested = true;
                setState(State.CONTROLS_BLOCKED);
                haltAnimation();
                break;
            case CONTROLS_BLOCKED:
                // Run Net — same reasoning, its own thread (runNetButtonPressed()) restores
                // once its loop actually exits. Already fully locked out, nothing more to do
                // here beyond signalling the halt (harmless if this fires twice).
                stopRequested = true;
                if (hostFrame.runModel != null) {
                    hostFrame.runModel.halt();
                }
                break;
            default:
                // SAVED_STATE_EXISTS — the run is over, so there is nothing to wait for and
                // the button is wearing its reset icon. It used to drop the snapshot and leave
                // the net wherever the run had left it, which is the one thing the icon must
                // not do: a button that says "put it back" has to put it back. Straight to the
                // pre-run snapshot rather than one step at a time, which is what step-back is
                // for.
                if (GraphPetriNetBackupHolder.getInstance().isEmpty()) {
                    // Should not happen - both ways into this state leave the pre-run snapshot
                    // in place - but a missing snapshot is not worth an exception on the event
                    // thread when there is an obvious answer: there is nothing to go back to,
                    // so the button has simply finished its job.
                    setState(State.NO_SAVED_STATE);
                    break;
                }
                restoreSavedState();
                stepHistory.clear();
                setState(State.NO_SAVED_STATE);
                break;
        }
    }

    /**
     * A handler for the "run net" (skip forward) action — the non-animated engine, which may
     * repeat itself {@code numberOfRuns} times (rewinding and re-saving the snapshot between
     * each). A Stop mid-run breaks out before the next repetition and restores the snapshot
     * from before the very first one, same as it does for animation.
     */
    public void runNetButtonPressed() {
        requireState(
                new State[] { State.NO_SAVED_STATE },
                "runNet");

        saveCurrentNetState();
        int numberOfRuns = hostFrame.getNumberOfRuns();
        Thread t = new Thread(() -> {
            for (int i = 0; i < numberOfRuns && !stopRequested; i++) {
                try {
                    hostFrame.disableInput();
                    hostFrame.timer.start();
                    hostFrame.runNet();
                } catch (Exception e) {
                    log.error("Animation control error", e);
                } finally {
                    hostFrame.enableInput();
                    hostFrame.timer.stop();
                }
                if (!stopRequested && i + 1 != numberOfRuns) {
                    // Briefly legal states stepBackButtonPressed() itself requires, and back
                    // to CONTROLS_BLOCKED before the next repetition starts — closing what
                    // used to be a gap where every control looked enabled mid-run.
                    setState(AnimationControls.State.SAVED_STATE_EXISTS);
                    stepBackButtonPressed();
                    saveCurrentNetState();
                    setState(AnimationControls.State.CONTROLS_BLOCKED);
                }
            }

            hostFrame.hideRunProgress();
            if (stopRequested) {
                restoreSavedState();
                stopRequested = false;
                setState(AnimationControls.State.NO_SAVED_STATE);
            } else {
                setState(AnimationControls.State.SAVED_STATE_EXISTS);
            }
        });
        hostFrame.showRunProgress();
        setState(State.CONTROLS_BLOCKED);
        t.start();
    }

    private synchronized void setState(State newPhase) {
        this.phase = newPhase;

        // Every state owns its own answer to "which buttons make sense right now" —
        // recomputed in full on each transition rather than diffed against the last one.
        switch (newPhase) {
            case NO_SAVED_STATE -> {
                stepBackAction.setEnabled(false);
                playPauseAction.setEnabled(true);
                playPauseAction.switchToPlayButton();
                stopSimulationAction.setEnabled(false);
                stopSimulationAction.switchToStopButton();
                runOneEventAction.setEnabled(true);
                runNetAction.setEnabled(true);
            }
            case SAVED_STATE_EXISTS -> {
                stepBackAction.setEnabled(true);
                // Widened: Start now also begins a fresh run from here — the natural way to
                // resume/continue after a step-back left a plain restored net with no live
                // run attached (see stepBackButtonPressed()).
                playPauseAction.setEnabled(true);
                playPauseAction.switchToPlayButton();
                // Nothing is running any more, so all this button still does is put the net
                // back, and it says so.
                stopSimulationAction.setEnabled(true);
                stopSimulationAction.switchToResetButton();
                runOneEventAction.setEnabled(true);
                runNetAction.setEnabled(false);
            }
            case ANIMATION_IN_PROGRESS -> {
                // Stepping backward here would race the live thread's own net mutations —
                // only legal once something has actually paused it (see stepBackButtonPressed()).
                stepBackAction.setEnabled(false);
                playPauseAction.setEnabled(true);
                playPauseAction.switchToPauseButton();
                stopSimulationAction.setEnabled(true);
                stopSimulationAction.switchToStopButton();
                // Widened: steps the live run forward in place instead of starting a new one.
                runOneEventAction.setEnabled(true);
                runNetAction.setEnabled(false);
            }
            case ANIMATION_PAUSED -> {
                stepBackAction.setEnabled(true);
                playPauseAction.setEnabled(true);
                playPauseAction.switchToPlayButton();
                stopSimulationAction.setEnabled(true);
                stopSimulationAction.switchToStopButton();
                // Widened: steps the live run forward in place instead of starting a new one.
                runOneEventAction.setEnabled(true);
                runNetAction.setEnabled(false);
            }
            case CONTROLS_BLOCKED -> {
                stepBackAction.setEnabled(false);
                playPauseAction.setEnabled(false);
                // The one control Run Net doesn't lock out — it's the only way to interrupt
                // a run once it's started, since there's no pause step to fall back on.
                stopSimulationAction.setEnabled(true);
                stopSimulationAction.switchToStopButton();
                runOneEventAction.setEnabled(false);
                runNetAction.setEnabled(false);
            }
        }
    }

    /**
     * Guards a button handler against firing while the controls are in a state where it
     * doesn't make sense, surfacing the mistake as an exception instead of letting it corrupt
     * whatever the state machine assumes is true.
     *
     * @param permittedStates the states {@code attemptedAction} is allowed to run from
     * @param attemptedAction name of the attempted action, used only to make the exception
     *        message readable
     */
    private void requireState(State[] permittedStates, String attemptedAction) {
        if (!List.of(permittedStates).contains(phase)) {
            throw new RuntimeException(String.format(ILLEGAL_ACTION_MESSAGE,
                    phase.name(), attemptedAction));
        }
    }

    /**
     * Backup the current state of the canvas for possible future restoration. Also clears the
     * step-back history: a new run starting means whatever was steppable before no longer is.
     */
    private void saveCurrentNetState() {
        GraphPetriNetBackupHolder holder = GraphPetriNetBackupHolder.getInstance();
        holder.save(
            new GraphCanvasModel(hostFrame.getPetriNetsPanel().getCanvasModel())
        );
        stepHistory.clear();
    }

    /**
     * Snapshots the canvas exactly as it looks right now onto the step-back history stack,
     * before a forward step advances it — so stepping backward can restore this exact moment.
     */
    private void pushStepHistory() {
        stepHistory.push(new GraphCanvasModel(hostFrame.getPetriNetsPanel().getCanvasModel()));
    }

    /**
     * Restores the canvas to the state that was previously saved and clears the saved state.
     * Throws RuntimeException if no state was saved.
     */
    private void restoreSavedState() {
        GraphPetriNetBackupHolder holder = GraphPetriNetBackupHolder.getInstance();

        if (holder.isEmpty()) {
            throw new RuntimeException("Tried to restore saved state, but there was no state saved");
        }

        restoreCanvas(holder.get());
        holder.clear();
    }

    /**
     * Swaps the canvas for the given snapshot — the common step underneath both a full restore
     * ({@link #restoreSavedState}) and a step-back ({@link #stepBackButtonPressed}). Goes
     * through {@code setCanvasModel} rather than {@code deletePetriNet}+{@code addGraphNet}:
     * the latter pair only ever touched the bare net, and {@code deletePetriNet} on its own
     * explicitly clears every Petri-object frame — restoring through it could bring the net's
     * elements back but never the frame that had held them.
     */
    private void restoreCanvas(GraphCanvasModel model) {
        hostFrame.getPetriNetsPanel().setCanvasModel(model);
    }

    /**
     * Drops the previously saved net-state backup without restoring it.
     */
    private void clearSavedState() {
        GraphPetriNetBackupHolder.getInstance().clear();
    }

}

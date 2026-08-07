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

/**
 * This class is responsible for contolling the state of the net
 * (during animation and non-animated simulations). It is sort of
 * a finite-state machine with 4 states.
 * @author Leonid
 */
public class AnimationControls {

    private static final Logger log = LoggerFactory.getLogger(AnimationControls.class);
    
    public static enum State {
        /**
         * There is no saved state of the net which can be restored.
         * Happens before any animation controls are used or after pressing "Stop" button.
         */
        NO_SAVED_STATE,
        /**
         * There is a saved state of the net which can be restored, but there is no paused animation.
         * Any changes compared to the saved state were either a result of an animation that has ended,
         * or a result of pressing rewind buttons (i.e. >> and >>|)
         */
        SAVED_STATE_EXISTS,
        ANIMATION_IN_PROGRESS,
        ANIMATION_PAUSED,
        /**
         * All buttons blocked. Happens when a non-animated simulation is running
         */
        CONTROLS_BLOCKED,
    }
    
    private final PetriNetsFrame frame;

    private volatile State currentState;

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

    public final StepBackAction stepBackAction; // A (|<<)
    public final PlayPauseAction playPauseAction; // B (> or ||)
    public final StopSimulationAction stopSimulationAction; // C (square)
    public final RunOneEventAction runOneEventAction; // D (>|)
    public final RunNetAction runNetAction; // E (>>|)

    private static final String ILLEGAL_ACTION_MESSAGE = "Illegal action on AnimationControls. Current state: %s, attempted action: %s";

    public AnimationControls(PetriNetsFrame frame) {
        this.frame = frame;

        runNetAction = new RunNetAction(this);
        stepBackAction = new StepBackAction(this);
        stopSimulationAction = new StopSimulationAction(this);
        playPauseAction = new PlayPauseAction(this);
        runOneEventAction = new RunOneEventAction(this);

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
        throwIfActionIsIllegal(
                new State[] { State.NO_SAVED_STATE, State.SAVED_STATE_EXISTS,
                        State.ANIMATION_IN_PROGRESS, State.ANIMATION_PAUSED },
                "runOneEvent");

        if (frame.animationModel != null) {
            // A live run is already in flight — nudge it forward one event in place.
            // stepOnce() re-pauses it on its own once that one event finishes.
            pushStepHistory();
            frame.animationModel.stepOnce();
            setState(State.ANIMATION_PAUSED);
            return;
        }

        // Nothing running: start a real animation, pre-armed to stop itself after one event.
        // Deliberately does NOT re-save from SAVED_STATE_EXISTS — that would drop both the
        // original pre-run snapshot and the step history built up so far.
        if (currentState == State.NO_SAVED_STATE) {
            saveCurrentNetState();
        }
        pushStepHistory();
        frame.startAnimationStepping = true;
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
        throwIfActionIsIllegal(
                new State[] { State.ANIMATION_PAUSED, State.SAVED_STATE_EXISTS },
                "stepBack");

        // if animation is paused, stop it altogether
        if (currentState == State.ANIMATION_PAUSED) {
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
     * A handler for the "play" / "pause" action
     */
    public void playPauseButtonPressed() {
        throwIfActionIsIllegal(
                new State[] { State.NO_SAVED_STATE, State.SAVED_STATE_EXISTS,
                        State.ANIMATION_PAUSED, State.ANIMATION_IN_PROGRESS },
                "playPause");

        if (currentState == State.NO_SAVED_STATE || currentState == State.SAVED_STATE_EXISTS) {
            // Either a fresh canvas or one sitting at a completed/rewound/stepped-back state —
            // either way there's no live run left to resume, so this always means starting a
            // new one from whatever is currently on screen. Re-saving is deliberately skipped
            // when a snapshot already exists: Stop should still rewind all the way to where
            // the very first run began, not to wherever stepping happened to leave off.
            if (currentState == State.NO_SAVED_STATE) {
                saveCurrentNetState();
            }
            initializeAnimation(State.ANIMATION_IN_PROGRESS);
            return;
        }

        if (currentState == State.ANIMATION_PAUSED) {
            resumeAnimation();
        } else if (currentState == State.ANIMATION_IN_PROGRESS) {
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
        frame.animationThread = new Thread(() -> {
            try {
                frame.disableInput();
                frame.timer.start();
                frame.animateNet();
            } catch (Exception e) {
                log.error("Animation control error", e);
            } finally {
                frame.enableInput();
                frame.timer.stop();

                if (frame.animationModel == null) {
                    // the net was incorrect and animation didn't even start
                    setState(State.NO_SAVED_STATE);
                } else if (stopRequested) {
                    // The loop has now actually exited — only safe to touch the net's state
                    // once we know this thread is done mutating it, which is exactly now.
                    restoreSavedState();
                    stopRequested = false;
                    setState(State.NO_SAVED_STATE);
                } else if (!frame.animationModel.isHalted()
                        && (currentState == State.ANIMATION_IN_PROGRESS
                                || currentState == State.ANIMATION_PAUSED)) {
                    // Ran to completion on its own. ANIMATION_PAUSED counts here too: a run
                    // armed to pause after one event still reaches this point if the net had
                    // no events left to begin with, and leaving it looking paused with no
                    // model behind it would strand every control.
                    setState(State.SAVED_STATE_EXISTS);
                }
                // else: halted some other way (a step back while paused) — that caller already
                // restored the state and transitioned it itself.

                frame.animationModel = null;
            }

        });
        setState(initialState);
        frame.animationThread.start();
    }

    private void resumeAnimation() {
        setState(State.ANIMATION_IN_PROGRESS);
        
        if (frame.animationModel != null) {
            frame.animationModel.setPaused(false);
            
            synchronized(frame.animationModel) { // TODO: replace with somthing better
                frame.animationModel.notifyAll();
            }
        }
        
    }
    
    private void pauseAnimation() {
        if (frame.animationModel != null) {
            frame.animationModel.setPaused(true);
        }
        
        setState(State.ANIMATION_PAUSED);
    }
    
    private void haltAnimation() {
        if (frame.animationModel != null) {
            frame.animationModel.halt();
        }
        
    }
    
    /**
     * A handler for the "stop" action — interrupts whatever is running (animation, paused or
     * not, or a non-animated Run Net) and rolls the net back to the snapshot taken just
     * before it started. When nothing is actually running, it just drops that snapshot.
     */
    public void stopSimulationButtonPressed() {
        throwIfActionIsIllegal(
                new State[] { State.SAVED_STATE_EXISTS, State.ANIMATION_PAUSED,
                        State.ANIMATION_IN_PROGRESS, State.CONTROLS_BLOCKED },
                "stopSimulation");

        switch (currentState) {
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
                if (frame.runModel != null) {
                    frame.runModel.halt();
                }
                break;
            default:
                // SAVED_STATE_EXISTS — nothing is running, so nothing to wait for.
                clearSavedState();
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
        throwIfActionIsIllegal(
                new State[] { State.NO_SAVED_STATE },
                "runNet");

        saveCurrentNetState();
        int numberOfRuns = frame.getNumberOfRuns();
        Thread t = new Thread(() -> {
            for (int i = 0; i < numberOfRuns && !stopRequested; i++) {
                try {
                    frame.disableInput();
                    frame.timer.start();
                    frame.runNet();
                } catch (Exception e) {
                    log.error("Animation control error", e);
                } finally {
                    frame.enableInput();
                    frame.timer.stop();
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

            frame.hideRunProgress();
            if (stopRequested) {
                restoreSavedState();
                stopRequested = false;
                setState(AnimationControls.State.NO_SAVED_STATE);
            } else {
                setState(AnimationControls.State.SAVED_STATE_EXISTS);
            }
        });
        frame.showRunProgress();
        setState(State.CONTROLS_BLOCKED);
        t.start();
    }
    
    private synchronized void setState(State state) {
        this.currentState = state;
        
        // turn the buttons on/off appropriately
        switch(state) {
            case NO_SAVED_STATE:
                stepBackAction.setEnabled(false);
                playPauseAction.setEnabled(true);
                playPauseAction.switchToPlayButton();
                stopSimulationAction.setEnabled(false);
                runOneEventAction.setEnabled(true);
                runNetAction.setEnabled(true);
                break;
            case SAVED_STATE_EXISTS:
                stepBackAction.setEnabled(true);
                // Widened: Start now also begins a fresh run from here — the natural way to
                // resume/continue after a step-back left a plain restored net with no live
                // run attached (see stepBackButtonPressed()).
                playPauseAction.setEnabled(true);
                playPauseAction.switchToPlayButton();
                stopSimulationAction.setEnabled(true);
                runOneEventAction.setEnabled(true);
                runNetAction.setEnabled(false);
                break;
            case ANIMATION_IN_PROGRESS:
                // Stepping backward here would race the live thread's own net mutations —
                // only legal once something has actually paused it (see stepBackButtonPressed()).
                stepBackAction.setEnabled(false);
                playPauseAction.setEnabled(true);
                playPauseAction.switchToPauseButton();
                stopSimulationAction.setEnabled(true);
                // Widened: steps the live run forward in place instead of starting a new one.
                runOneEventAction.setEnabled(true);
                runNetAction.setEnabled(false);
                break;
            case ANIMATION_PAUSED:
                stepBackAction.setEnabled(true);
                playPauseAction.setEnabled(true);
                playPauseAction.switchToPlayButton();
                stopSimulationAction.setEnabled(true);
                // Widened: steps the live run forward in place instead of starting a new one.
                runOneEventAction.setEnabled(true);
                runNetAction.setEnabled(false);
                break;
            case CONTROLS_BLOCKED:
                stepBackAction.setEnabled(false);
                playPauseAction.setEnabled(false);
                // The one control Run Net doesn't lock out — it's the only way to interrupt
                // a run once it's started, since there's no pause step to fall back on.
                stopSimulationAction.setEnabled(true);
                runOneEventAction.setEnabled(false);
                runNetAction.setEnabled(false);
                break;
        }
    }
    
    /**
     * Checks whether the current controls state is in the supplied list of legal
     * states and throws a RuntimeException if it's not
     * @param legalStates a list of acceptable states
     * @param actionName name of the attempted action to be included in exception's message
     */
    private void throwIfActionIsIllegal(State[] legalStates, String actionName) {
        if (!isActionLegal(legalStates)) {
             throw new RuntimeException(String.format(ILLEGAL_ACTION_MESSAGE,
                    currentState.name(), actionName));
        }
    }
    
    private boolean isActionLegal(State[] legalStates) {
        for (State legalState: legalStates) {
            if (legalState.equals(currentState)) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * Backup the current state of the canvas for possible future restoration. Also clears the
     * step-back history: a new run starting means whatever was steppable before no longer is.
     */
    private void saveCurrentNetState() {
        GraphPetriNetBackupHolder holder = GraphPetriNetBackupHolder.getInstance();
        holder.save(
            new GraphCanvasModel(frame.getPetriNetsPanel().getCanvasModel())
        );
        stepHistory.clear();
    }

    /**
     * Snapshots the canvas exactly as it looks right now onto the step-back history stack,
     * before a forward step advances it — so stepping backward can restore this exact moment.
     */
    private void pushStepHistory() {
        stepHistory.push(new GraphCanvasModel(frame.getPetriNetsPanel().getCanvasModel()));
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
        frame.getPetriNetsPanel().setCanvasModel(model);
    }
    
    /**
     * Delete previously saved net state backup
     */
    private void clearSavedState() {
        GraphPetriNetBackupHolder.getInstance().clear();
    }
    
}

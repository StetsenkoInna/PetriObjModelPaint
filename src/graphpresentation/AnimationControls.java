package graphpresentation;

import graphnet.GraphPetriNet;
import graphpresentation.actions.AnimateEventAction;
import graphpresentation.actions.PlayPauseAction;
import graphpresentation.actions.RewindAction;
import graphpresentation.actions.RunNetAction;
import graphpresentation.actions.RunOneEventAction;
import graphpresentation.actions.StopSimulationAction;

/**
 * This class is responsible for contolling the state of the net
 * (during animation and non-animated simulations). It is sort of
 * a finite-state machine with 4 states.
 * @author Leonid
 */
public class AnimationControls {
    
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
    
    public final RewindAction rewindAction; // A (|<<)
    public final PlayPauseAction playPauseAction; // B (> or ||)
    public final StopSimulationAction stopSimulationAction; // C (square)
    public final RunOneEventAction runOneEventAction; // D (>|)
    public final RunNetAction runNetAction; // E (>>|)
    public final AnimateEventAction animateEventAction; // only in menu
    
    private static final String ILLEGAL_ACTION_MESSAGE = "Illegal action on AnimationControls. Current state: %s, attempted action: %s";  
    
    public AnimationControls(PetriNetsFrame frame) {
        this.frame = frame;
        
        runNetAction = new RunNetAction(this);
        rewindAction = new RewindAction(this);
        stopSimulationAction = new StopSimulationAction(this);
        playPauseAction = new PlayPauseAction(this);
        runOneEventAction = new RunOneEventAction(this);
        animateEventAction = new AnimateEventAction(this);
        
        setState(State.NO_SAVED_STATE);
    }
    
    /**
     * A handler for "run one event" (>>) button
     */
    public void runOneEventButtonPressed() {
        throwIfActionIsIllegal(
                new State[] { State.NO_SAVED_STATE, State.SAVED_STATE_EXISTS },
                "runOneEvent");
 
        if (currentState == State.NO_SAVED_STATE) {
            saveCurrentNetState();
        }
        
        // run one event
        Thread t = new Thread(() -> {
            try {
                frame.disableInput();
                frame.timer.start();
                frame.runEvent();
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                frame.enableInput();
                frame.timer.stop();

                setState(AnimationControls.State.SAVED_STATE_EXISTS);
            }
        });
        setState(State.CONTROLS_BLOCKED);
        t.start();
    }
    
    /**
     * A handler for "rewind (restore state)" button
     */
    public void rewindButtonPressed() {
        throwIfActionIsIllegal(
                new State[] { State.ANIMATION_PAUSED, State.SAVED_STATE_EXISTS }, 
                "rewind");
        
        // if animation is paused, stop it altogether
        if (currentState == State.ANIMATION_PAUSED) {
            haltAnimation();
        }
        
        // restore state
        restoreSavedState();
        
        setState(State.NO_SAVED_STATE);
    }
    
    /**
     * A handler for the "play" / "pause" action
     */
    public void playPauseButtonPressed() {
        throwIfActionIsIllegal(
                new State[] { State.NO_SAVED_STATE, State.ANIMATION_PAUSED, State.ANIMATION_IN_PROGRESS }, 
                "playPause");
        
        if (currentState == State.NO_SAVED_STATE) {
            // save the state and initialize animation 
            saveCurrentNetState();
            initializeAnimation();
            return;
        }
        
        if (currentState == State.ANIMATION_PAUSED) {
            resumeAnimation();
        } else if (currentState == State.ANIMATION_IN_PROGRESS) {
            pauseAnimation();
        }
    }
    
    /**
     * Initialize and run net animation
     */
    private void initializeAnimation() {
        frame.animationThread = new Thread(() -> {
            try {
                frame.disableInput();
                frame.timer.start();
                frame.animateNet();
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                frame.enableInput();
                frame.timer.stop();
                
                if (frame.animationModel == null) {
                    // the net was incorrect and animation didn't even start
                    setState(State.NO_SAVED_STATE);
                } else {
                    // if anim ended on its own, set state SAVED_STATE_EXISTS
                    // otherwise (if anim was haled by stop button) the state
                    // change will be handled by stopSimulationButtonPressed()
                    if (!frame.animationModel.isHalted() 
                            && currentState == State.ANIMATION_IN_PROGRESS) {
                        setState(State.SAVED_STATE_EXISTS);
                    }
                }
                
                frame.animationModel = null;
            }

        });
        setState(State.ANIMATION_IN_PROGRESS);
        frame.animationThread.start();
    }
    
    /**
     * A handler for the "animate event" action
     */
    public void animateEventButtonPressed() {
        throwIfActionIsIllegal(
                new State[] { State.NO_SAVED_STATE, State.SAVED_STATE_EXISTS },
                "animateEvent");
        
        if (currentState == State.NO_SAVED_STATE) {
            saveCurrentNetState();
        }
        
        frame.animationThread = new Thread(() -> {
            try {
                frame.disableInput();
                frame.timer.start();
                frame.animateEvent();
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                frame.enableInput();
                frame.timer.stop();
                
                // if anim ended on its own, set state SAVED_STATE_EXISTS
                // otherwise (if anim was haled by stop button) the state
                // change will be handled by stopSimulationButtonPressed()
                if (!frame.animationPetriObject.isHalted() 
                            && currentState == State.ANIMATION_IN_PROGRESS) {
                    setState(State.SAVED_STATE_EXISTS);
                }
                
                frame.animationPetriObject = null;
            }

        });
        setState(State.ANIMATION_IN_PROGRESS);
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
        
        if (frame.animationPetriObject != null) {
            frame.animationPetriObject.setPaused(false);
            synchronized(frame.animationPetriObject) {
                frame.animationPetriObject.notifyAll();
            }
        } 
    }
    
    private void pauseAnimation() {
        if (frame.animationModel != null) {
            frame.animationModel.setPaused(true);
        }
        
        if (frame.animationPetriObject != null) {
            frame.animationPetriObject.setPaused(true);
        }
        
        setState(State.ANIMATION_PAUSED);
    }
    
    private void haltAnimation() {
        if (frame.animationModel != null) {
            frame.animationModel.halt();
        }
        
        if (frame.animationPetriObject != null) {
            frame.animationPetriObject.halt();
        }
    }
    
    /**
     * A handler for the "stop" action
     */
    public void stopSimulationButtonPressed() {
        throwIfActionIsIllegal(
                new State[] { State.SAVED_STATE_EXISTS, State.ANIMATION_PAUSED },
                "stopSimulation");
        
        // if animation exists and paused, stop it altogether
        if (currentState == State.ANIMATION_PAUSED) {
            haltAnimation();
        }
        
        clearSavedState();
        setState(State.NO_SAVED_STATE);
    }
    
    /**
     * A handler for the "run net" (skip forward) action
     */
    public void runNetButtonPressed() {
        throwIfActionIsIllegal(
                new State[] { State.NO_SAVED_STATE }, 
                "runNet");
        
        saveCurrentNetState();
        
        Thread t = new Thread(() -> {
            try {
                frame.disableInput();
                frame.timer.start();
                frame.runNet();
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                frame.enableInput();
                frame.timer.stop();
                setState(AnimationControls.State.SAVED_STATE_EXISTS);
            }

        });  
        setState(State.CONTROLS_BLOCKED);
        t.start();
    }
    
    private synchronized void setState(State state) {
        this.currentState = state;
        
        // turn the buttons on/off appropriately
        switch(state) {
            case NO_SAVED_STATE:
                rewindAction.setEnabled(false);
                playPauseAction.setEnabled(true);
                playPauseAction.switchToPlayButton();
                stopSimulationAction.setEnabled(false);
                runOneEventAction.setEnabled(true);
                runNetAction.setEnabled(true);
                animateEventAction.setEnabled(true);
                break;
            case SAVED_STATE_EXISTS:
                rewindAction.setEnabled(true);
                playPauseAction.setEnabled(false);
                playPauseAction.switchToPlayButton();
                stopSimulationAction.setEnabled(true);
                runOneEventAction.setEnabled(true);
                runNetAction.setEnabled(false);
                animateEventAction.setEnabled(true);
                break;
            case ANIMATION_IN_PROGRESS:
                rewindAction.setEnabled(false);
                playPauseAction.setEnabled(true);
                playPauseAction.switchToPauseButton();
                stopSimulationAction.setEnabled(false);
                runOneEventAction.setEnabled(false);
                runNetAction.setEnabled(false);
                animateEventAction.setEnabled(false);
                break;
            case ANIMATION_PAUSED:
                rewindAction.setEnabled(true);
                playPauseAction.setEnabled(true);
                playPauseAction.switchToPlayButton();
                stopSimulationAction.setEnabled(true);
                runOneEventAction.setEnabled(false);
                runNetAction.setEnabled(false);
                animateEventAction.setEnabled(false);
                break;
            case CONTROLS_BLOCKED:
                rewindAction.setEnabled(false);
                playPauseAction.setEnabled(false);
                stopSimulationAction.setEnabled(false);
                runOneEventAction.setEnabled(false);
                runNetAction.setEnabled(false);
                animateEventAction.setEnabled(false);
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
     * Backup the current state of the net for possible future restoration
     */
    private void saveCurrentNetState() {
        GraphPetriNetBackupHolder holder = GraphPetriNetBackupHolder.getInstance();
        holder.save(
            new GraphPetriNet(frame.getPetriNetsPanel().getGraphNet())
        );
    }
    
    /**
     * Restores the net to the state that was previously saved and clears the saved state.
     * Throws RuntimeException if no state was saved.
     */
    private void restoreSavedState() {
        GraphPetriNetBackupHolder holder = GraphPetriNetBackupHolder.getInstance();
        
        if (holder.isEmpty()) {
            throw new RuntimeException("Tried to restore saved state, but there was no state saved");
        }  
        
        frame.getPetriNetsPanel().deletePetriNet();
        frame.getPetriNetsPanel().addGraphNet(holder.get());
        holder.clear();
    }
    
    /**
     * Delete previously saved net state backup
     */
    private void clearSavedState() {
        GraphPetriNetBackupHolder.getInstance().clear();
    }
    
}

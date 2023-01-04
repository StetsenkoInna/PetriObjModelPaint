package graphpresentation;

import graphnet.GraphPetriNet;
import graphpresentation.actions.PlayPauseAction;
import graphpresentation.actions.RewindAction;
import graphpresentation.actions.RunNetAction;
import graphpresentation.actions.RunOneEventAction;
import graphpresentation.actions.StopSimulationAction;
import java.util.List;

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
    }
    
    private final PetriNetsFrame frame;
    
    private State currentState;
    
    public final RewindAction rewindAction; // A (|<<)
    public final PlayPauseAction playPauseAction; // B (> or ||)
    public final StopSimulationAction stopSimulationAction; // C (square)
    public final RunOneEventAction runOneEventAction; // D (>|)
    public final RunNetAction runNetAction; // E (>>|)
    
    private static String ILLEGAL_ACTION_MESSAGE = "Illegal action on AnimationControls. Current state: %s, attempted action: %s";  
    
    public AnimationControls(PetriNetsFrame frame) {
        this.frame = frame;
        currentState = State.NO_SAVED_STATE;
        
        runNetAction = new RunNetAction(frame); // TODO: replace with 'this'
        rewindAction = new RewindAction(frame);
        stopSimulationAction = new StopSimulationAction(frame);
        playPauseAction = new PlayPauseAction(frame);
        runOneEventAction = new RunOneEventAction(this);
    }
    
    /**
     * A handler for "run one event" (>>) button
     */
    public void runOneEventButtonPressed() {
        throwIfActionIsIllegal(
                List.of(State.NO_SAVED_STATE, State.SAVED_STATE_EXISTS), 
                "runOneEvent");
 
        if (currentState == State.NO_SAVED_STATE) {
            saveCurrentNetState();
        }
        
        // run one event
        new Thread(() -> {
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
        }).start();
    }
    
    private void setState(State state) {
        this.currentState = state;
        
        // turn the buttons on/off appropriately
        switch(state) {
            case NO_SAVED_STATE:
                rewindAction.setEnabled(false);
                playPauseAction.setEnabled(true);
                stopSimulationAction.setEnabled(false);
                runOneEventAction.setEnabled(true);
                runNetAction.setEnabled(true);
                break;
            case SAVED_STATE_EXISTS:
                rewindAction.setEnabled(true);
                playPauseAction.setEnabled(false);
                stopSimulationAction.setEnabled(true);
                runOneEventAction.setEnabled(true);
                runNetAction.setEnabled(false);
                break;
            case ANIMATION_IN_PROGRESS:
                rewindAction.setEnabled(false);
                playPauseAction.setEnabled(true);
                stopSimulationAction.setEnabled(false);
                runOneEventAction.setEnabled(false);
                runNetAction.setEnabled(false);
                break;
            case ANIMATION_PAUSED:
                rewindAction.setEnabled(true);
                playPauseAction.setEnabled(true);
                stopSimulationAction.setEnabled(false);
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
    private void throwIfActionIsIllegal(List<State> legalStates, String actionName) {
        if (!isActionLegal(legalStates)) {
             throw new RuntimeException(String.format(ILLEGAL_ACTION_MESSAGE,
                    currentState.name(), actionName));
        }
    }
    
    private boolean isActionLegal(List<State> legalStates) {
        return legalStates.contains(currentState);
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
}

package graphpresentation.actions;

import graphnet.GraphPetriNet;
import graphpresentation.GraphPetriNetBackupHolder;
import graphpresentation.PetriNetsFrame;
import java.awt.event.ActionEvent;
import javax.swing.AbstractAction;
import static javax.swing.Action.SHORT_DESCRIPTION;

/**
 * Start/Pause/Unpause net animation
 * @author Leonid
 */
public class PlayPauseAction extends AbstractAction {
    
    private final PetriNetsFrame frame;
    
    private static String PLAY_DESCRIPTION = "Start/continue animating the net";
    private static String PAUSE_DESCRIPTION = "Pause the simulation";
    
    public PlayPauseAction(PetriNetsFrame frame) {
        super("⏵");
        this.frame = frame;
        putValue(SHORT_DESCRIPTION, PLAY_DESCRIPTION);
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        if (!frame.isAnimationInitiated) {
            frame.isAnimationInitiated = true;
            startAnimation(); // this creates a new model and runs it
            switchToPauseButton();
            frame.isAnimationPaused = false;
            
            frame.rewindAction.setEnabled(false);
            frame.stopSimulationAction.setEnabled(false);
            
            // skipBackwardAnimationButton.setEnabled(false);
        } else {
            if (frame.isAnimationPaused) {
                /* unpause */
                frame.animationModel.setPaused(false);
                synchronized(frame.animationModel) { // TODO: replace with somthing better
                    frame.animationModel.notifyAll();
                }
                
                switchToPauseButton();
                frame.isAnimationPaused = false;
                // skipBackwardAnimationButton.setEnabled(false);
                frame.rewindAction.setEnabled(false);
                frame.stopSimulationAction.setEnabled(false);
            } else {
                /* pause */
                frame.animationModel.setPaused(true);
                switchToPlayButton();
               // stopAnimationButton.setEnabled(true);
                frame.rewindAction.setEnabled(true);
                frame.stopSimulationAction.setEnabled(true);
               
                frame.isAnimationPaused = true;
                if (!GraphPetriNetBackupHolder.getInstance().isEmpty()) {
                    frame.rewindAction.setEnabled(true);
                }
            }
        }
    }
    
    /**
     * Starts animating the simulation of the net in a new thread
     */
    private void startAnimation() {
        /* save the current state of the net for possible future rewinding
           but only if it wasn't previously saved by running without animation (fast-forwarding)
        */
        GraphPetriNetBackupHolder holder = GraphPetriNetBackupHolder.getInstance();
        if (holder.isEmpty()) {
            GraphPetriNetBackupHolder.getInstance().save(
                new GraphPetriNet(frame.getPetriNetsPanel().getGraphNet())
            );
        }
        
        
        frame.animationThread = new Thread() {
            @Override
            public void run() {

                try {
                    frame.disableInput();
                    frame.timer.start();
                    frame.animateNet();
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    System.out.println("Animation thread halted");
                    frame.enableInput();
                    frame.timer.stop();
                    
                    frame.isAnimationPaused = true;
                    frame.isAnimationInitiated = false;
                    switchToPlayButton();
                    frame.rewindAction.setEnabled(true);
                }

            }
        };
        frame.animationThread.start();
    }
    
    /**
     * Changes the appearance and function of the button associated with this action to
     * be a pause button
     */
    private void switchToPauseButton() {
        putValue(NAME, "||"); // TODO: char
        putValue(SHORT_DESCRIPTION, PAUSE_DESCRIPTION);
    }
    
    /**
     * Changes the appearance and function of the button associated with this action to
     * be a play button
     */
    public void switchToPlayButton() {
        putValue(NAME, "⏵");
        putValue(SHORT_DESCRIPTION, PLAY_DESCRIPTION);
    }
    
}

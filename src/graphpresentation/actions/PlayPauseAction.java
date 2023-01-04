package graphpresentation.actions;

import graphpresentation.AnimationControls;
import java.awt.event.ActionEvent;
import javax.swing.AbstractAction;
import static javax.swing.Action.SHORT_DESCRIPTION;

/**
 * Start/Pause/Unpause net animation
 * @author Leonid
 */
public class PlayPauseAction extends AbstractAction {
    
    private final AnimationControls controls;
    
    private static String PLAY_DESCRIPTION = "Start net animation";
    private static String PAUSE_DESCRIPTION = "Pause animation";
    
    public PlayPauseAction(AnimationControls controls) {
        super("⏵");
        this.controls = controls;
        putValue(SHORT_DESCRIPTION, PLAY_DESCRIPTION);
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        controls.playPauseButtonPressed();
    }
    
    /**
     * Changes the appearance and function of the button associated with this action to
     * be a pause button
     */
    public void switchToPauseButton() {
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

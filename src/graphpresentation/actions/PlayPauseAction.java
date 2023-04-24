package graphpresentation.actions;

import graphpresentation.AnimationControls;
import java.awt.event.ActionEvent;
import javax.swing.AbstractAction;
import static javax.swing.Action.SHORT_DESCRIPTION;
import javax.swing.ImageIcon;

/**
 * Start/Pause/Unpause net animation
 * @author Leonid
 */
public class PlayPauseAction extends AbstractAction {
    
    private final AnimationControls controls;
    
    private static String PLAY_DESCRIPTION = "Start net animation";
    private static String PAUSE_DESCRIPTION = "Pause animation";
    
    private final ImageIcon playIcon = new ImageIcon(getClass().getResource("icons/play.png"));
    private final ImageIcon pauseIcon = new ImageIcon(getClass().getResource("icons/pause.png"));
    
    public PlayPauseAction(AnimationControls controls) {
        this.controls = controls;
        switchToPlayButton();
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
        putValue(LARGE_ICON_KEY, pauseIcon);
        putValue(SHORT_DESCRIPTION, PAUSE_DESCRIPTION);
    }
    
    /**
     * Changes the appearance and function of the button associated with this action to
     * be a play button
     */
    public void switchToPlayButton() {
        putValue(LARGE_ICON_KEY, playIcon);
        putValue(SHORT_DESCRIPTION, PLAY_DESCRIPTION);
    }
    
}

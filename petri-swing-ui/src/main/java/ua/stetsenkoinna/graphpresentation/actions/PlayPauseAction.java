package ua.stetsenkoinna.graphpresentation.actions;

import ua.stetsenkoinna.graphpresentation.AnimationControls;
import ua.stetsenkoinna.graphpresentation.CanvasToolIcons;
import java.awt.event.ActionEvent;
import javax.swing.AbstractAction;

/**
 * Start/Pause/Unpause net animation
 * @author Leonid
 */
public class PlayPauseAction extends AbstractAction {

    /**
     * Matches the size the frame gives the transport buttons around this one, so the row of
     * them stays even.
     */
    private static final int ICON_SIZE = 20;

    private final AnimationControls controls;

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
        putValue(LARGE_ICON_KEY, CanvasToolIcons.pause(ICON_SIZE));
        String PAUSE_DESCRIPTION = "Pause";
        putValue(SHORT_DESCRIPTION, PAUSE_DESCRIPTION);
    }

    /**
     * Changes the appearance and function of the button associated with this action to
     * be a play button
     *
     * <p>The icon is drawn rather than loaded from {@code play.png}: an image carries its
     * colours with it, so on a dark toolbar the black triangle was a black triangle on a dark
     * button. Every other transport button already drew itself from the component's foreground;
     * this one was the exception, and the only control in the window that disappeared when the
     * theme changed.
     */
    public void switchToPlayButton() {
        putValue(LARGE_ICON_KEY, CanvasToolIcons.play(ICON_SIZE));
        String PLAY_DESCRIPTION = "Start";
        putValue(SHORT_DESCRIPTION, PLAY_DESCRIPTION);
    }

}

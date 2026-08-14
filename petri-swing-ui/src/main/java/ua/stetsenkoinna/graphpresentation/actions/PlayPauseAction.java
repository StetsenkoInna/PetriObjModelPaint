package ua.stetsenkoinna.graphpresentation.actions;

import ua.stetsenkoinna.graphpresentation.AnimationControls;
import ua.stetsenkoinna.graphpresentation.CanvasToolIcons;
import java.awt.event.ActionEvent;
import javax.swing.AbstractAction;

/**
 * The transport button that starts a run, and thereafter toggles it between paused and
 * playing.
 */
public class PlayPauseAction extends AbstractAction {

    /**
     * Matches the size the frame gives the transport buttons around this one, so the row of
     * them stays even.
     */
    private static final int ICON_SIZE = 20;

    private final AnimationControls animationControls;

    public PlayPauseAction(AnimationControls animationControls) {
        this.animationControls = animationControls;
        switchToPlayButton();
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        animationControls.playPauseButtonPressed();
    }

    /**
     * Restyles this action's button into a pause control.
     */
    public void switchToPauseButton() {
        putValue(LARGE_ICON_KEY, CanvasToolIcons.pause(ICON_SIZE));
        putValue(SHORT_DESCRIPTION, "Pause");
    }

    /**
     * Restyles this action's button back into a play/start control.
     *
     * <p>The icon is drawn rather than loaded from {@code play.png}: an image carries its
     * colours with it, so on a dark toolbar the black triangle was a black triangle on a dark
     * button. Every other transport button already drew itself from the component's foreground;
     * this one was the exception, and the only control in the window that disappeared when the
     * theme changed.
     */
    public void switchToPlayButton() {
        putValue(LARGE_ICON_KEY, CanvasToolIcons.play(ICON_SIZE));
        putValue(SHORT_DESCRIPTION, "Start");
    }

}

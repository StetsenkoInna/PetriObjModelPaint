package ua.stetsenkoinna.graphpresentation.actions;

import ua.stetsenkoinna.graphpresentation.AnimationControls;
import ua.stetsenkoinna.graphpresentation.CanvasToolIcons;
import java.awt.event.ActionEvent;
import javax.swing.AbstractAction;

/**
 * Ends a run and puts the net back the way it was before it started.
 *
 * <p>Two things depending on when it is pressed, and it says which by the icon it wears. During
 * a run it is a stop button: it halts what is playing and restores the net. Once a run has
 * finished on its own there is nothing left to halt, and the only thing it still does is that
 * restore - so it becomes a reset button, which is the whole of what is left to want at that
 * point and was previously impossible to guess from a square.
 */
public class StopSimulationAction extends AbstractAction {

    /**
     * Matches the size the frame gives the transport buttons around this one, so the row of
     * them stays even.
     */
    private static final int ICON_SIZE = 20;

    private final AnimationControls animationControls;

    public StopSimulationAction(AnimationControls animationControls) {
        this.animationControls = animationControls;
        switchToStopButton();
    }

    /** Restyles this action's button into a stop control: there is a run to halt. */
    public void switchToStopButton() {
        putValue(LARGE_ICON_KEY, CanvasToolIcons.stop(ICON_SIZE));
    }

    /** Restyles it into a reset control: the run is over, and only the restore is left. */
    public void switchToResetButton() {
        putValue(LARGE_ICON_KEY, CanvasToolIcons.reset(ICON_SIZE));
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        animationControls.stopSimulationButtonPressed();
    }

}

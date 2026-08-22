package ua.stetsenkoinna.graphpresentation.actions;

import ua.stetsenkoinna.graphpresentation.AnimationControls;
import java.awt.event.ActionEvent;
import javax.swing.AbstractAction;

/**
 * Runs the net straight through to completion, with no animation and no per-event pausing.
 */
public class RunNetAction extends AbstractAction {

    private final AnimationControls animationControls;

    public RunNetAction(AnimationControls animationControls) {
        this.animationControls = animationControls;
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        animationControls.runNetButtonPressed();
    }

}

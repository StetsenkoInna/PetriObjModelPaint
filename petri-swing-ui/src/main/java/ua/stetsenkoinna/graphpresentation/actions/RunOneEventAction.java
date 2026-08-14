package ua.stetsenkoinna.graphpresentation.actions;

import ua.stetsenkoinna.graphpresentation.AnimationControls;
import java.awt.event.ActionEvent;
import javax.swing.AbstractAction;

/**
 * Advances the simulation by exactly one event.
 */
public class RunOneEventAction extends AbstractAction {

    private final AnimationControls animationControls;

    public RunOneEventAction(AnimationControls animationControls) {
        this.animationControls = animationControls;
        putValue(SHORT_DESCRIPTION, "Step forward");
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        animationControls.runOneEventButtonPressed();
    }

}

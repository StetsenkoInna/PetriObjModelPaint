package ua.stetsenkoinna.graphpresentation.actions;

import ua.stetsenkoinna.graphpresentation.AnimationControls;
import java.awt.event.ActionEvent;
import javax.swing.AbstractAction;

/**
 * Completely halts the simulation, animated or not. Once this fires, there is no way back to
 * the state the net was in before it started: every future rewind will land on the state the
 * net is in right now. Think of it as a commit that can't be undone.
 */
public class StopSimulationAction extends AbstractAction {

    private final AnimationControls animationControls;

    public StopSimulationAction(AnimationControls animationControls) {
        this.animationControls = animationControls;
        putValue(SHORT_DESCRIPTION, "Stop");
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        animationControls.stopSimulationButtonPressed();
    }

}

package ua.stetsenkoinna.graphpresentation.actions;

import ua.stetsenkoinna.graphpresentation.AnimationControls;
import java.awt.event.ActionEvent;
import javax.swing.AbstractAction;

/**
 * Steps the net backward by one event, popping the most recent entry off the per-event
 * history stack (or, once that stack is empty, falling all the way back to the state saved
 * just before the run started).
 */
public class StepBackAction extends AbstractAction {

    private final AnimationControls controls;

    public StepBackAction(AnimationControls controls) {
        this.controls = controls;
        putValue(SHORT_DESCRIPTION, "Step back");
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        controls.stepBackButtonPressed();
    }

}

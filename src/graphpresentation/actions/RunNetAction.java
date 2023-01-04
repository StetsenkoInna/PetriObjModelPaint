package graphpresentation.actions;

import graphpresentation.AnimationControls;
import java.awt.event.ActionEvent;
import javax.swing.AbstractAction;

/**
 * Represents an action of running the net (without animation)
 */
public class RunNetAction extends AbstractAction {
    
    private final AnimationControls controls;
    
    public RunNetAction(AnimationControls controls) {
        this.controls = controls;
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        controls.runNetButtonPressed();
    }
    
}

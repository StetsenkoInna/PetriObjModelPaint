package graphpresentation.actions;

import graphpresentation.AnimationControls;
import java.awt.event.ActionEvent;
import javax.swing.AbstractAction;

/**
 * The action of restoring the initial state of a net after running the simulation on it 
 * (with or without animation)
 * @author Leonid
 */
public class RewindAction extends AbstractAction {
    
    private final AnimationControls controls;
    
    public RewindAction(AnimationControls controls) {
        this.controls = controls;
        putValue(SHORT_DESCRIPTION, "Restore net state");
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        controls.rewindButtonPressed();
    }
    
}

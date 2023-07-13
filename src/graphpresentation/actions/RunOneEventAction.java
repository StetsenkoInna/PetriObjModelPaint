package graphpresentation.actions;

import graphpresentation.AnimationControls;
import java.awt.event.ActionEvent;
import javax.swing.AbstractAction;

/**
 * Represents an action of simulating one event in the net
 * @author Leonid
 */
public class RunOneEventAction extends AbstractAction {
    
    private final AnimationControls controls;
    
     public RunOneEventAction(AnimationControls controls) {
        super("⏩");
        this.controls = controls;
        putValue(SHORT_DESCRIPTION, "Run one event");
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        controls.runOneEventButtonPressed();
    }
    
}

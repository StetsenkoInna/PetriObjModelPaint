package graphpresentation.actions;

import graphpresentation.AnimationControls;
import java.awt.event.ActionEvent;
import javax.swing.AbstractAction;

/**
 *
 * @author Leonid
 */
public class AnimateEventAction extends AbstractAction {
    
    private final AnimationControls controls;
    
    public AnimateEventAction(AnimationControls controls) {
        this.controls = controls;
        putValue(SHORT_DESCRIPTION, "Animate one event");
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        controls.animateEventButtonPressed();
    }
    
}

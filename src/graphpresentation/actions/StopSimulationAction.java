package graphpresentation.actions;

import graphpresentation.AnimationControls;
import java.awt.event.ActionEvent;
import javax.swing.AbstractAction;

/**
 * Completely halts the simulation (with or without animation). After that, there will
 * be no way to rewind back to the prevoius state of the net, and all future rewinds
 * will lead to the current net state. In a way, it works like "git commit", except
 * you cannot undo it.
 * @author Leonid
 */
public class StopSimulationAction extends AbstractAction {
    
    private final AnimationControls controls;
    
    public StopSimulationAction(AnimationControls controls) {
        this.controls = controls;
        putValue(SHORT_DESCRIPTION, "Stop / Commit changes");
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        controls.stopSimulationButtonPressed();
    }
    
}

package graphpresentation.actions;

import graphpresentation.GraphPetriNetBackupHolder;
import graphpresentation.PetriNetsFrame;
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
    
    private final PetriNetsFrame frame;
    
    public StopSimulationAction(PetriNetsFrame frame) {
        this.frame = frame;
        setEnabled(false);
        putValue(SHORT_DESCRIPTION, "Stop simulation & use current net state");
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        frame.haltAnimation();
        this.setEnabled(false);
        frame.rewindAction.setEnabled(false);
        GraphPetriNetBackupHolder.getInstance().clear();
    }
    
}

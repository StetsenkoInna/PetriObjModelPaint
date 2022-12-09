package graphpresentation.actions;

import graphpresentation.GraphPetriNetBackupHolder;
import graphpresentation.PetriNetsFrame;
import java.awt.event.ActionEvent;
import javax.swing.AbstractAction;

/**
 * The action of restoring the initial state of a net after running the simulation on it 
 * (with or without animation)
 * @author Leonid
 */
public class RewindAction extends AbstractAction {
    
    private final PetriNetsFrame frame;
    
    public RewindAction(PetriNetsFrame frame) {
        this.frame = frame;
        setEnabled(false);
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        frame.haltAnimation();
        GraphPetriNetBackupHolder holder = GraphPetriNetBackupHolder.getInstance();
        if (!holder.isEmpty()) {
            frame.getPetriNetsPanel().deletePetriNet();
            frame.getPetriNetsPanel().addGraphNet(holder.get());
            this.setEnabled(false);
            GraphPetriNetBackupHolder.getInstance().clear();
        }
    }
    
}

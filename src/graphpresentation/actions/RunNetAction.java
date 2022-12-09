package graphpresentation.actions;

import graphnet.GraphPetriNet;
import graphpresentation.GraphPetriNetBackupHolder;
import graphpresentation.PetriNetsFrame;
import java.awt.event.ActionEvent;
import javax.swing.AbstractAction;

/**
 * Represents an action of running the net (without animation)
 */
public class RunNetAction extends AbstractAction {
    
    private final PetriNetsFrame frame;
    
    public RunNetAction(PetriNetsFrame frame) {
        this.frame = frame;
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        // backing up the state of the net for future rewinding
        // (but not if it was previously backed up by staring an animation)
        GraphPetriNetBackupHolder holder = GraphPetriNetBackupHolder.getInstance();
        if (holder.isEmpty()) {
             GraphPetriNetBackupHolder.getInstance()
                            .save(new GraphPetriNet(frame.getPetriNetsPanel().getGraphNet()));
        }
       
        
        new Thread() {
            @Override
            public void run() {
                try {
                    frame.disableInput();

                    
                    frame.timer.start();
                    frame.runNet();
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    frame.enableInput();
                    frame.timer.stop();
                    frame.stopSimulationAction.setEnabled(true);
                    frame.rewindAction.setEnabled(true);
                    // frame.stopAnimationAction.setEnabled(true);
                }

            }
        }.start();    
    }
    
}

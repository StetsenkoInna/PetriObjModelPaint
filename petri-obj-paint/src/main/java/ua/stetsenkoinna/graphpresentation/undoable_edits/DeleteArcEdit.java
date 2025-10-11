package ua.stetsenkoinna.graphpresentation.undoable_edits;

import ua.stetsenkoinna.graphnet.GraphArcIn;
import ua.stetsenkoinna.graphnet.GraphArcOut;
import ua.stetsenkoinna.graphpresentation.GraphArc;
import ua.stetsenkoinna.graphpresentation.PetriNetsPanel;
import javax.swing.undo.AbstractUndoableEdit;

/**
 * Represents an undoable & redoable action of deleting an GraphArcOut from the graph
 * @author Leonid
 */
public class DeleteArcEdit extends AbstractUndoableEdit {
    
    private final PetriNetsPanel panel;
    private final GraphArc arc;
    
    public DeleteArcEdit(PetriNetsPanel panel, GraphArc arc) {
        this.panel = panel;
        this.arc = arc;
    }
    
    @Override
    public void undo() {
        super.undo();
        
        if (arc instanceof GraphArcOut) {
            panel.getGraphNet().getGraphArcOutList().add((GraphArcOut)arc);
        } else if (arc instanceof GraphArcIn) {
            panel.getGraphNet().getGraphArcInList().add((GraphArcIn)arc);
        }

        panel.repaint();
    }
    
    @Override
    public void redo() {
        super.redo();
        panel.removeArc(arc);
        panel.setChoosenArc(null);
    }
    
}

package ua.stetsenkoinna.graphpresentation.undoable_edits;

import ua.stetsenkoinna.graphnet.GraphArcIn;
import ua.stetsenkoinna.graphnet.GraphArcOut;
import ua.stetsenkoinna.graphpresentation.GraphArc;
import ua.stetsenkoinna.graphpresentation.PetriNetsPanel;
import javax.swing.undo.AbstractUndoableEdit;

/**
 * Represents an undoable & redoable action of adding a new GraphArcOut to the graph
 * @author Leonid
 */
public class AddArcEdit extends AbstractUndoableEdit {
    
    private final PetriNetsPanel panel;
    private final GraphArc arc;
    
    public AddArcEdit(PetriNetsPanel panel, GraphArc arc) {
        this.panel = panel;
        this.arc = arc;
    }
    
    @Override
    public void undo() {
        super.undo();
        panel.removeArc(arc);
        panel.setChoosenArc(null);
    }
    
    @Override
    public void redo() {
        super.redo();
        if (arc instanceof GraphArcOut) {
            panel.getGraphNet().getGraphArcOutList().add((GraphArcOut)arc);
        } else if (arc instanceof GraphArcIn) {
            panel.getGraphNet().getGraphArcInList().add((GraphArcIn)arc);
        }

        panel.repaint();
    }
    
}

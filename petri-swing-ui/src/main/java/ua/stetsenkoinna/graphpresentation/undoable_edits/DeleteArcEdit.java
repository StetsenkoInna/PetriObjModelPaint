package ua.stetsenkoinna.graphpresentation.undoable_edits;

import ua.stetsenkoinna.graphnet.GraphArcIn;
import ua.stetsenkoinna.graphnet.GraphArcOut;
import ua.stetsenkoinna.graphnet.GraphArc;
import ua.stetsenkoinna.graphpresentation.PetriNetsPanel;
import javax.swing.undo.AbstractUndoableEdit;

/**
 * Represents an undoable & redoable action of deleting an GraphArcOut from the graph
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

        if (arc instanceof GraphArcOut out) {
            panel.getGraphNet().getGraphArcOutList().add(out);
        } else if (arc instanceof GraphArcIn in) {
            panel.getGraphNet().getGraphArcInList().add(in);
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

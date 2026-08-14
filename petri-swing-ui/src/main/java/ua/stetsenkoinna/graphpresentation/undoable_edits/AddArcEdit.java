package ua.stetsenkoinna.graphpresentation.undoable_edits;

import ua.stetsenkoinna.graphnet.GraphArcIn;
import ua.stetsenkoinna.graphnet.GraphArcOut;
import ua.stetsenkoinna.graphnet.GraphArc;
import ua.stetsenkoinna.graphpresentation.PetriNetsPanel;
import javax.swing.undo.AbstractUndoableEdit;

/**
 * Represents an undoable & redoable action of adding a new GraphArcOut to the graph
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
        if (arc instanceof GraphArcOut out) {
            panel.getGraphNet().getGraphArcOutList().add(out);
        } else if (arc instanceof GraphArcIn in) {
            panel.getGraphNet().getGraphArcInList().add(in);
        }

        panel.repaint();
    }

}

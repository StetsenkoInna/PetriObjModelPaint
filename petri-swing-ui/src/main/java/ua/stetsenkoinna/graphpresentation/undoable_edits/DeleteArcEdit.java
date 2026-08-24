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

        // The arc goes straight back into the list, carrying whatever pairing flags it had when
        // it was removed - which is to say, the wrong ones. Without this a restored arc and the
        // one running the other way drew down the same centre line, one hidden under the other.
        panel.getGraphNet().fixOverlappingArcs();
        panel.repaint();
    }

    @Override
    public void redo() {
        super.redo();
        // removeArc re-derives the pairing for what is left behind.
        panel.removeArc(arc);
        panel.setChoosenArc(null);
    }

}

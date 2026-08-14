package ua.stetsenkoinna.graphpresentation.undoable_edits;

import javax.swing.undo.AbstractUndoableEdit;

import ua.stetsenkoinna.graphnet.GraphPlaceFusion;
import ua.stetsenkoinna.graphpresentation.PetriNetsPanel;

/**
 * Joining two places into a shared place, undone and redone.
 *
 * <p>Making a fusion used to post no edit at all, so the Ctrl+Z pressed right after it left
 * the fusion in place and instead undid whatever older action was next on the stack, up to
 * and including deleting a whole Petri-object the user had no intention of touching.
 */
public class JoinPlacesEdit extends AbstractUndoableEdit {

    private final PetriNetsPanel panel;
    private final GraphPlaceFusion fusion;

    public JoinPlacesEdit(PetriNetsPanel panel, GraphPlaceFusion fusion) {
        this.panel = panel;
        this.fusion = fusion;
    }

    @Override
    public void undo() {
        super.undo();
        panel.getCanvasModel().removeFusion(fusion);
        panel.repaint();
    }

    @Override
    public void redo() {
        super.redo();
        panel.getCanvasModel().restoreFusion(fusion);
        panel.repaint();
    }
}

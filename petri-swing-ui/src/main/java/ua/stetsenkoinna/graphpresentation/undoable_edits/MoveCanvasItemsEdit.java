package ua.stetsenkoinna.graphpresentation.undoable_edits;

import javax.swing.undo.AbstractUndoableEdit;

import ua.stetsenkoinna.graphpresentation.PetriNetsPanel;

/**
 * One drag on the canvas, undone and redone: where everything ended up put back to where it
 * started, and the other way round.
 *
 * <p>Moving things used to post no edit at all, so {@code Ctrl+Z} straight after a drag reached
 * past it to whatever had been done before - it took back the paste, or the object creation,
 * that the user had then arranged, and the arranging itself could never be taken back. That was
 * survivable while a drag moved one element; it stopped being so once a drag could carry whole
 * Petri-objects, their nets and their nesting with it.
 *
 * <p>The two snapshots are of the whole canvas rather than of the dragged items, because a drag
 * reaches past what the pointer grabbed - see {@link CanvasLayoutSnapshot} for what that covers
 * and why it is recorded that way.
 */
public class MoveCanvasItemsEdit extends AbstractUndoableEdit {

    private final PetriNetsPanel panel;
    private final CanvasLayoutSnapshot before;
    private final CanvasLayoutSnapshot after;

    public MoveCanvasItemsEdit(PetriNetsPanel panel, CanvasLayoutSnapshot before,
            CanvasLayoutSnapshot after) {
        this.panel = panel;
        this.before = before;
        this.after = after;
    }

    @Override
    public void undo() {
        super.undo();
        apply(before);
    }

    @Override
    public void redo() {
        super.redo();
        apply(after);
    }

    private void apply(CanvasLayoutSnapshot snapshot) {
        snapshot.applyTo(panel.getCanvasModel());
        panel.updateArcCoordinates();
        panel.repaint();
    }

    @Override
    public String getPresentationName() {
        return "move";
    }
}

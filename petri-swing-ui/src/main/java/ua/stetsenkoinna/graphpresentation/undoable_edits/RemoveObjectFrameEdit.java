package ua.stetsenkoinna.graphpresentation.undoable_edits;

import javax.swing.undo.AbstractUndoableEdit;

import ua.stetsenkoinna.graphpresentation.PetriNetsPanel;

/**
 * Removing a Petri-object frame, undone and redone.
 *
 * <p>Removal is the destructive half of the pair: it lifts the object's whole net and every
 * object nested inside it one level out, and afterwards nothing on the canvas distinguishes what
 * used to be the removed object's from what the enclosing object already held. The snapshot is
 * therefore taken before the removal, not after.
 */
public class RemoveObjectFrameEdit extends AbstractUndoableEdit {

    private final PetriNetsPanel panel;
    private final ObjectFrameSnapshot snapshot;

    /**
     * @param panel the canvas the frame is being taken off
     * @param snapshot the frame's state, read while it was still on the canvas
     */
    public RemoveObjectFrameEdit(PetriNetsPanel panel, ObjectFrameSnapshot snapshot) {
        this.panel = panel;
        this.snapshot = snapshot;
    }

    @Override
    public void undo() {
        super.undo();
        panel.reinstateObjectFrame(snapshot);
    }

    @Override
    public void redo() {
        super.redo();
        panel.removeObjectFrameSilently(snapshot.getFrame());
    }
}

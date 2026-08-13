package ua.stetsenkoinna.graphpresentation.undoable_edits;

import javax.swing.undo.AbstractUndoableEdit;

import ua.stetsenkoinna.graphnet.GraphObjectFrame;
import ua.stetsenkoinna.graphpresentation.PetriNetsPanel;

/**
 * Creating a Petri-object, undone and redone.
 *
 * <p>Object creation used to post no edit at all, which was survivable while an object's net was
 * edited in a modal window with its own Cancel. It is not survivable now: an object's canvas is
 * left by clicking another pill, and {@code Ctrl+Z} is the only way back from anything done on
 * it. So grouping a chunk into an object, stamping a template and duplicating an object are all
 * one undo step each.
 *
 * <p>The snapshot is taken at undo time rather than at construction because what the new frame
 * claims is decided after it is added: {@code groupIntoObject} adds the frame first and then
 * claims the selection for it.
 */
public class AddObjectFrameEdit extends AbstractUndoableEdit {

    private final PetriNetsPanel panel;
    private final GraphObjectFrame frame;
    private ObjectFrameSnapshot snapshot;

    public AddObjectFrameEdit(PetriNetsPanel panel, GraphObjectFrame frame) {
        this.panel = panel;
        this.frame = frame;
    }

    @Override
    public void undo() {
        super.undo();
        snapshot = new ObjectFrameSnapshot(panel.getCanvasModel(), frame);
        panel.removeObjectFrameSilently(frame);
    }

    @Override
    public void redo() {
        super.redo();
        if (snapshot != null) {
            panel.reinstateObjectFrame(snapshot);
        }
    }
}

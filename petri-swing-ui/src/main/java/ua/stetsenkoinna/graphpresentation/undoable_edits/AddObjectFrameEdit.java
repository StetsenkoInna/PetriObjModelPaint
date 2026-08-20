package ua.stetsenkoinna.graphpresentation.undoable_edits;

import java.util.List;

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
 *
 * <p>{@link #pushNeighborsClear()} is separate from construction for the same reason: whatever
 * the new frame pushed out of its way can only be told apart from what it is about to claim once
 * its membership is settled, so the caller invokes it only after the frame holds everything it
 * is going to hold. The push is still not a second edit - it is recorded on this same edit and
 * replayed by {@link #undo} and {@link #redo}, so one {@code Ctrl+Z} takes back the frame and
 * whatever it moved together.
 */
public class AddObjectFrameEdit extends AbstractUndoableEdit {

    private final PetriNetsPanel panel;
    private final GraphObjectFrame frame;
    private List<NeighborNudge> nudges = List.of();
    private ObjectFrameSnapshot snapshot;

    public AddObjectFrameEdit(PetriNetsPanel panel, GraphObjectFrame frame) {
        this.panel = panel;
        this.frame = frame;
    }

    /**
     * Pushes whatever now stands too close to the frame's border out of the way. Call once,
     * after the frame's own membership for this creation is settled - a member claimed later
     * would otherwise be read as a stranger standing next to the frame and pushed instead of
     * left alone.
     */
    public void pushNeighborsClear() {
        nudges = panel.nudgeNeighborsAway(frame);
    }

    @Override
    public void undo() {
        super.undo();
        snapshot = new ObjectFrameSnapshot(panel.getCanvasModel(), frame);
        panel.removeObjectFrameSilently(frame);
        for (NeighborNudge nudge : nudges) {
            nudge.undo();
        }
        if (!nudges.isEmpty()) {
            panel.updateArcCoordinates();
            panel.repaint();
        }
    }

    @Override
    public void redo() {
        super.redo();
        if (snapshot != null) {
            panel.reinstateObjectFrame(snapshot);
        }
        for (NeighborNudge nudge : nudges) {
            nudge.redo();
        }
        if (!nudges.isEmpty()) {
            panel.updateArcCoordinates();
            panel.repaint();
        }
    }
}

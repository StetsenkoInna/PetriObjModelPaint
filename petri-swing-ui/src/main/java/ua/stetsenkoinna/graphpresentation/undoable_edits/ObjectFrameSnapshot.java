package ua.stetsenkoinna.graphpresentation.undoable_edits;

import java.util.ArrayList;
import java.util.List;

import ua.stetsenkoinna.graphnet.GraphCanvasModel;
import ua.stetsenkoinna.graphnet.GraphElement;
import ua.stetsenkoinna.graphnet.GraphObjectFrame;

/**
 * Everything about one Petri-object frame that taking it off the canvas destroys: where it sat in
 * the flat frame list, what enclosed it, what it claimed, and what was nested inside it.
 *
 * <p>Removing a frame lifts all of that one level out, so an undo cannot reconstruct it from the
 * canvas afterwards - the members are indistinguishable from the enclosing object's own by then.
 * Taking the snapshot before the removal is what makes creating and removing a Petri-object
 * undoable at all, which the canvas needs now that there is no Save and no Cancel: an edit made
 * on an object's canvas is an edit to the model, and {@code Ctrl+Z} is the only way back.
 */
public final class ObjectFrameSnapshot {

    private final GraphObjectFrame frame;
    private final int index;
    private final GraphObjectFrame enclosing;
    private final List<GraphElement> members;
    private final List<GraphObjectFrame> children;

    /**
     * Reads the current state of a frame off the canvas. Call before removing it.
     *
     * @param model the canvas document the frame is still on
     * @param frame the frame to record
     */
    public ObjectFrameSnapshot(GraphCanvasModel model, GraphObjectFrame frame) {
        this.frame = frame;
        this.index = model.getFrames().indexOf(frame);
        this.enclosing = model.enclosingOf(frame);
        this.members = new ArrayList<>(frame.getMembers());
        this.children = new ArrayList<>(model.childrenOf(frame));
    }

    public GraphObjectFrame getFrame() {
        return frame;
    }

    /**
     * @return where the frame sat in the flat frame list. Load-bearing: that position is what
     *         indexes the object in the exported model, in the PNML document and in the
     *         statistics formulas, so putting the frame back somewhere else would silently
     *         re-address every one of them.
     */
    public int getIndex() {
        return index;
    }

    public GraphObjectFrame getEnclosing() {
        return enclosing;
    }

    public List<GraphElement> getMembers() {
        return members;
    }

    public List<GraphObjectFrame> getChildren() {
        return children;
    }
}

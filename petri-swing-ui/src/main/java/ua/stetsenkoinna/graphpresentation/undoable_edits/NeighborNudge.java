package ua.stetsenkoinna.graphpresentation.undoable_edits;

import ua.stetsenkoinna.graphnet.GraphElement;

/**
 * One place or transition pushed clear of a Petri-object frame at the moment the frame was
 * created, and the offset that pushed it.
 *
 * <p>The push is not an edit of its own: it happens as part of creating the frame, so
 * {@link AddObjectFrameEdit#undo} and {@link AddObjectFrameEdit#redo} are what replay it, in
 * reverse and forward, alongside taking the frame off and putting it back. Public because it is
 * built on the panel's side, where the frame's own margin and its neighbours are known, and
 * carried into this package only to be replayed.
 */
public final class NeighborNudge {

    private final GraphElement element;
    private final int dx;
    private final int dy;

    /**
     * @param element the place or transition that was pushed
     * @param dx how far it moved on the horizontal axis
     * @param dy how far it moved on the vertical axis; exactly one of {@code dx} and
     *        {@code dy} is non-zero, since a nudge always moves along a single axis
     */
    public NeighborNudge(GraphElement element, int dx, int dy) {
        this.element = element;
        this.dx = dx;
        this.dy = dy;
    }

    /** Puts the element back where it was before the push. */
    void undo() {
        element.moveBy(-dx, -dy);
    }

    /** Pushes the element again, the same way it moved the first time. */
    void redo() {
        element.moveBy(dx, dy);
    }
}

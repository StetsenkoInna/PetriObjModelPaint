package ua.stetsenkoinna.graphpresentation.objmodel;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import ua.stetsenkoinna.graphnet.GraphCanvasModel;
import ua.stetsenkoinna.graphnet.GraphObjectFrame;

/**
 * Which canvases are open along the bottom of the window, and which one is being edited.
 *
 * <p>A "canvas" here is not a second document. There is one {@link GraphCanvasModel}, one net and
 * one undo history; a canvas is a level of that one document - the pair (the document, one
 * focused Petri-object, or none for the net itself). Entering an object changes what is painted,
 * what is hit-tested and which object a newly drawn element is claimed for, and changes nothing
 * about the document. That is why this class holds frames rather than snapshots: there is
 * nothing to stash and nothing to merge back, so an edit made on an object's canvas is an edit
 * to the model at the moment it is made.
 *
 * <p>Index 0 is always {@code null}, the net's own canvas, and it can never be closed.
 */
public class CanvasStack {

    private final GraphCanvasModel model;

    /** The open canvases: index 0 is always {@code null}, the net itself. */
    private final List<GraphObjectFrame> open = new ArrayList<>();

    private int activeIndex;

    private final List<Runnable> listeners = new ArrayList<>();

    /**
     * @param model the one canvas document these canvases are levels of; consulted for the
     *        nesting relation, so opening a deeply nested object can open its whole chain
     */
    public CanvasStack(GraphCanvasModel model) {
        this.model = Objects.requireNonNull(model, "model");
        open.add(null);
    }

    /**
     * @return the open canvases in strip order, {@code null} first for the net
     */
    public List<GraphObjectFrame> getOpen() {
        return Collections.unmodifiableList(open);
    }

    /**
     * @return the Petri-object currently being edited, or {@code null} for the net's own canvas
     */
    public GraphObjectFrame getActive() {
        return open.get(activeIndex);
    }

    public int getActiveIndex() {
        return activeIndex;
    }

    /**
     * @return true when only the net's own canvas is open, so there is no chain to show
     */
    public boolean isRootOnly() {
        return open.size() == 1;
    }

    /**
     * Opens a Petri-object's canvas and makes it active, opening every object that encloses it
     * first, outermost first. A deeply nested object therefore always arrives with its whole
     * chain on the strip, so the pill order reads as the breadcrumb it is. Opening one that is
     * already open activates it rather than adding a second pill.
     *
     * @param frame the object to open, or {@code null} to activate the net's own canvas
     */
    public void open(GraphObjectFrame frame) {
        if (frame == null) {
            activate(0);
            return;
        }
        List<GraphObjectFrame> chain = new ArrayList<>();
        for (GraphObjectFrame above = frame; above != null; above = model.enclosingOf(above)) {
            if (chain.contains(above)) {
                break; // a cycle in the nesting: stop rather than loop forever
            }
            chain.add(0, above);
        }
        for (GraphObjectFrame link : chain) {
            if (!open.contains(link)) {
                open.add(link);
            }
        }
        activeIndex = open.indexOf(frame);
        fireChanged();
    }

    /**
     * Makes an already-open canvas active.
     *
     * @param index its position on the strip; out-of-range indexes are ignored rather than
     *        throwing, since a stale pill click can race a canvas being closed
     */
    public void activate(int index) {
        if (index < 0 || index >= open.size() || index == activeIndex) {
            return;
        }
        activeIndex = index;
        fireChanged();
    }

    /**
     * Makes an open canvas active by the object it shows.
     *
     * @param frame the object, or {@code null} for the net's own canvas
     */
    public void activate(GraphObjectFrame frame) {
        activate(open.indexOf(frame));
    }

    /**
     * Closes a Petri-object's canvas, and with it the canvas of everything nested inside it -
     * a canvas whose enclosing canvas is gone has no chain left to read. The nearest surviving
     * enclosing canvas becomes active, falling back to the net.
     *
     * <p>Nothing is asked and nothing is discarded: every edit made on that canvas is already in
     * the document, exactly as if it had been made on the net's own canvas.
     *
     * @param frame the object whose canvas to close; the net's own canvas cannot be closed
     */
    public void close(GraphObjectFrame frame) {
        if (frame == null || !open.contains(frame)) {
            return;
        }
        GraphObjectFrame wasActive = getActive();
        List<GraphObjectFrame> closing = new ArrayList<>();
        for (GraphObjectFrame candidate : open) {
            if (candidate != null && isSelfOrDescendant(candidate, frame)) {
                closing.add(candidate);
            }
        }
        open.removeAll(closing);

        GraphObjectFrame nextActive = closing.contains(wasActive)
                ? nearestOpenAncestor(frame)
                : wasActive;
        activeIndex = Math.max(0, open.indexOf(nextActive));
        fireChanged();
    }

    /**
     * Closes the canvases of a frame that has just been taken off the canvas altogether, along
     * with those of everything that was nested inside it.
     *
     * <p>Eager where the web editor is lazy, and for a reason the desktop cannot avoid: a pill
     * here holds a live frame reference, not a snapshot, so a canvas whose frame is gone cannot
     * be painted at all rather than merely being stale.
     *
     * @param removed the frame that was removed from the document
     */
    public void pruneRemoved(GraphObjectFrame removed) {
        close(removed);
    }

    /**
     * Drops every canvas but the net's own. Called when the whole document is replaced - a PNML
     * import, {@code File > New}, an animation snapshot restored - because the frames that come
     * back are different instances from the ones the pills were holding.
     */
    public void reset() {
        open.clear();
        open.add(null);
        activeIndex = 0;
        fireChanged();
    }

    /**
     * @param listener run whenever the open canvases or the active one change
     */
    public void addChangeListener(Runnable listener) {
        listeners.add(listener);
    }

    /**
     * Re-announces the current state without changing it. The strip shows an object's name and its
     * nesting level, and both live on the document rather than here, so adding, removing or
     * renaming an object has to say so even though no canvas opened or closed.
     */
    public void notifyChanged() {
        fireChanged();
    }

    private void fireChanged() {
        for (Runnable listener : new ArrayList<>(listeners)) {
            listener.run();
        }
    }

    private boolean isSelfOrDescendant(GraphObjectFrame candidate, GraphObjectFrame ancestor) {
        int guard = 0;
        for (GraphObjectFrame above = candidate;
                above != null && guard <= model.getFrames().size();
                above = model.enclosingOf(above), guard++) {
            if (above == ancestor) {
                return true;
            }
        }
        return false;
    }

    private GraphObjectFrame nearestOpenAncestor(GraphObjectFrame frame) {
        int guard = 0;
        for (GraphObjectFrame above = model.enclosingOf(frame);
                above != null && guard <= model.getFrames().size();
                above = model.enclosingOf(above), guard++) {
            if (open.contains(above)) {
                return above;
            }
        }
        return null;
    }
}

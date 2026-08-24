package ua.stetsenkoinna.graphpresentation;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

import ua.stetsenkoinna.graphnet.CanvasItem;
import ua.stetsenkoinna.graphnet.GraphElement;
import ua.stetsenkoinna.graphnet.GraphObjectFrame;

/**
 * What the canvas currently has selected, of either kind of thing it holds: places and
 * transitions, and the Petri-object frames that mark out the objects.
 *
 * <p>This exists because the canvas held those two kinds in three separate stores -
 * {@code choosenElements}, {@code choosenFrames} and a single-click {@code selectedFrame} - and
 * every operation had to remember all three by hand. Any that forgot silently skipped
 * Petri-objects, which is why {@code Ctrl+C} and {@code Ctrl+V} never carried an object,
 * {@code Ctrl+D} only ever duplicated the last-clicked one, and the eraser refused frames
 * outright. One store means an operation is written once and reaches everything.
 *
 * <p>It used to be a selection abstraction rather than a common supertype of both kinds, on
 * purpose: {@link GraphElement} is inside every {@code .pns} file on disk and declared no
 * {@code serialVersionUID}, so adding even a marker interface to it would have changed its
 * computed id and made every saved net unreadable. Both now implement
 * {@code ua.stetsenkoinna.graphnet.CanvasItem}, with that id pinned first - see the type for what
 * it does and does not claim to unify. The two live, mutable stores below stay exactly as they
 * were even so: {@link #elements()} in particular is handed out live because undoable edits mutate
 * it through {@code PetriNetsPanel.getChoosenElements()}, and a filtered view over one combined
 * list cannot promise that without either copying on every read or reimplementing {@code List}.
 * {@link #allItems()} is the new door in: everything selected, once, for an operation that
 * genuinely wants to treat both kinds the same way instead of writing two loops by hand.
 *
 * <p>The element list is handed out live and mutable ({@link #elements()}), because the undoable
 * edits mutate it through {@code PetriNetsPanel.getChoosenElements()} and have done so since
 * long before Petri-objects existed.
 */
public class CanvasSelection {

    /**
     * The colour a selected place or transition is drawn in. Public because a selected
     * Petri-object's own net is drawn in it too without ever entering the selection - see
     * {@code PetriNetsPanel.selectedElements()}, which is where that happens.
     */
    public static final Color SELECTED = Color.GREEN;

    private final List<GraphElement> elements = new ArrayList<>();
    private final List<GraphObjectFrame> frames = new ArrayList<>();

    /**
     * The frame a single click picked out, kept apart from {@link #frames} because a frame's own
     * context menu, its rename and its priority all act on exactly one object and need to know
     * which. Everything that acts on "the selection" treats it as one more selected frame.
     */
    private GraphObjectFrame selectedFrame;

    /**
     * @return the selected places and transitions, live and mutable - see the class doc for why
     */
    public List<GraphElement> elements() {
        return elements;
    }

    /**
     * @return the selected Petri-objects, live and mutable
     */
    public List<GraphObjectFrame> frames() {
        return frames;
    }

    /**
     * @return the single-click frame, or {@code null}
     */
    public GraphObjectFrame getSelectedFrame() {
        return selectedFrame;
    }

    public void setSelectedFrame(GraphObjectFrame frame) {
        selectedFrame = frame;
    }

    /**
     * Adds an element to the selection, and colours it as selected. Idempotent.
     */
    public void add(GraphElement element) {
        if (element != null && !elements.contains(element)) {
            elements.add(element);
            element.setColor(SELECTED);
        }
    }

    /**
     * Adds a Petri-object to the selection. Idempotent.
     */
    public void add(GraphObjectFrame frame) {
        if (frame != null && !frames.contains(frame)) {
            frames.add(frame);
        }
    }

    public void remove(GraphElement element) {
        elements.remove(element);
    }

    public void remove(GraphObjectFrame frame) {
        frames.remove(frame);
        if (selectedFrame == frame) {
            selectedFrame = null;
        }
    }

    public boolean contains(GraphElement element) {
        return elements.contains(element);
    }

    /**
     * @param frame a Petri-object frame
     * @return true if it is selected either as one of several or as the single-click frame -
     *         which is what decides whether it is drawn with the selection highlight
     */
    public boolean contains(GraphObjectFrame frame) {
        return frame != null && (frame == selectedFrame || frames.contains(frame));
    }

    /**
     * Empties the selection. Does not reset element colours: the canvas repaints those wholesale
     * through its own {@code setDefaultColorGraphElements}, which also covers elements that were
     * coloured for reasons other than being selected.
     */
    public void clear() {
        elements.clear();
        frames.clear();
        selectedFrame = null;
    }

    /**
     * @return true when nothing at all is selected, of either kind
     */
    public boolean isEmpty() {
        return elements.isEmpty() && frames.isEmpty() && selectedFrame == null;
    }

    /**
     * @return true when no element and no frame is selected other than possibly the single-click
     *         frame - the condition the Delete key uses to tell "one object is selected" from
     *         "a whole rubber-band selection is"
     */
    public boolean holdsOnlyTheClickedFrame() {
        return elements.isEmpty() && frames.isEmpty() && selectedFrame != null;
    }

    /**
     * @return how many things are selected, counting both kinds; the single-click frame counts
     *         only when it is not also in {@link #frames}
     */
    public int size() {
        int size = elements.size() + frames.size();
        if (selectedFrame != null && !frames.contains(selectedFrame)) {
            size++;
        }
        return size;
    }

    /**
     * @return every selected Petri-object exactly once, the single-click one included - what an
     *         operation over "the selected objects" iterates
     */
    public List<GraphObjectFrame> allFrames() {
        List<GraphObjectFrame> all = new ArrayList<>(frames);
        if (selectedFrame != null && !all.contains(selectedFrame)) {
            all.add(selectedFrame);
        }
        return all;
    }

    /**
     * @return every selected thing exactly once, elements and Petri-objects alike - built fresh
     *         each call, the same as {@link #allFrames()}, so it stays a read view rather than a
     *         third store that could drift from the two live ones it is built from
     */
    public List<CanvasItem> allItems() {
        List<CanvasItem> all = new ArrayList<>(elements.size() + frames.size() + 1);
        all.addAll(elements);
        all.addAll(allFrames());
        return all;
    }

    /**
     * Re-applies the selection colour to every selected element. Called before a repaint that
     * followed something which reset element colours wholesale, so a selection made before it
     * still reads as one; frames get their highlight at draw time from {@link #contains}.
     */
    public void paintHighlight() {
        for (GraphElement element : elements) {
            element.setColor(SELECTED);
        }
    }
}

package ua.stetsenkoinna.graphpresentation;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

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
 * <p>It is a selection abstraction rather than a common supertype of both kinds, and that is a
 * deliberate choice, not a shortcut. {@link GraphElement} is inside every {@code .pns} file on
 * disk and declares no {@code serialVersionUID}, so adding even a marker interface to it changes
 * its computed id and makes every saved net unreadable. A supertype implemented by
 * {@link GraphObjectFrame} alone would buy nothing. Java has no common ancestor to dispatch on
 * here, so the dispatch happens in one place per operation instead: each of the canvas's eight
 * selection operations iterates both kinds once and calls a per-kind primitive.
 *
 * <p>The element list is handed out live and mutable ({@link #elements()}), because the undoable
 * edits mutate it through {@code PetriNetsPanel.getChoosenElements()} and have done so since
 * long before Petri-objects existed.
 */
public class CanvasSelection {

    /** The colour a selected place or transition is drawn in. */
    private static final Color SELECTED = Color.GREEN;

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

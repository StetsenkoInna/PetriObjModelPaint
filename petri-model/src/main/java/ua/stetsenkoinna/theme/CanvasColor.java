package ua.stetsenkoinna.theme;

/**
 * The roles a colour can play in the net drawing. Each theme supplies one colour per role, and
 * {@link CanvasPalette} refuses to be built without all of them - so adding a role here is a
 * compile-and-run reminder to decide what it looks like in both themes, rather than a value that
 * silently stays null in whichever one was forgotten.
 */
public enum CanvasColor {

    /** The paper the net is drawn on. */
    CANVAS_BACKGROUND,

    /**
     * Inside a place or a transition. Equal to {@link #CANVAS_BACKGROUND} in both themes: a
     * shape is defined by its outline, and its fill exists only to hide what passes underneath.
     * Kept as its own role so that stops being an accident if it ever needs to stop being true.
     */
    ELEMENT_FILL,

    /** The outline of an unselected place, transition or arc, and the labels that go with it. */
    ELEMENT_STROKE,

    /** The same, while it is selected. */
    SELECTION,

    /** Transient guides: the arc being dragged, the marquee, a placement outline. */
    GUIDE,

    /** The editor's primary blue, for anything structural that needs to stand out. */
    ACCENT,

    FRAME_BORDER,
    FRAME_BORDER_SELECTED,
    FRAME_BODY,
    FRAME_BODY_SELECTED,
    FRAME_HEADER,
    FRAME_HEADER_SELECTED,
    FRAME_TEXT,

    /** Behind a collapsed object frame, which hides the net it holds. */
    COLLAPSED_FRAME_FILL,

    PORT_FILL_PLACE,
    PORT_FILL_TRANSITION,
    PORT_BORDER,
    PORT_HIGHLIGHT,

    /** Semi-opaque plate behind a port's label, so it stays readable over the net. */
    PORT_LABEL_BACKDROP,

    /**
     * The inside of a place that is linked to another one, rather than the plain element fill.
     *
     * <p>Replaces the ring colour that used to mark a shared place, now that a link is always
     * drawn as a line: the ring is gone, so a place carried nothing on it saying its marking is
     * not its own. Filling it says that wherever it is drawn, including on a canvas where the
     * other end is not visible at all.
     */
    LINKED_PLACE_FILL,
    FUSION_RING_SELECTED,

    /** What an element takes while it is the one firing in an animation. */
    ANIMATION_ACTIVE,

    /** The stand-in drawn for an arc that crosses an object boundary. */
    ANIMATION_CROSSING
}

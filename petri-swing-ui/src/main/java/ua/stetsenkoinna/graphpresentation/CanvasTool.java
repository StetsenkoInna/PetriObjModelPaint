package ua.stetsenkoinna.graphpresentation;

/**
 * The interaction mode the canvas is currently in — which single left-click-drag gesture
 * a {@link PetriNetsPanel} performs, since the canvas otherwise has no way to tell "move
 * this element", "select everything under the rectangle" and "pan the view" apart.
 */
public enum CanvasTool {
    /** Default pointer: click selects an element, dragging one moves it. */
    SELECT,
    /** Drag always rubber-band selects, even when the drag starts on top of an element. */
    MARQUEE,
    /** Drag moves the canvas view; nothing underneath the pointer is touched. */
    PAN,
    /** Click an element or arc to remove it immediately. */
    DELETE,
    /** Every click drops a new place at the click point; stays active for the next one. */
    ADD_PLACE,
    /** Every click drops a new transition at the click point; stays active for the next one. */
    ADD_TRANSITION
}

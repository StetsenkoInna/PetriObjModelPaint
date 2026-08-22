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
    ADD_TRANSITION,
    /**
     * Every click stamps another copy of the armed Petri-object template at the click point;
     * stays active for the next one. Which template is armed is held separately by the panel,
     * since every template shares this one mode.
     */
    ADD_PETRI_OBJECT,
    /**
     * Drag out a rectangle and release: whatever it captures becomes one new Petri-object, the
     * same gesture the web editor's own object tool draws. A frame joins only when the band
     * swallows it whole, wrapping it inside the new object; a place or transition joins by its
     * centre. A capture-nothing drag still creates an empty object at the drawn rectangle. Not
     * to be confused with {@link #ADD_PETRI_OBJECT}, which stamps a library template instead of
     * building a fresh object out of whatever the band encloses.
     */
    OBJECT_BAND,
    /**
     * A net has been loaded and is waiting for the user to say where it goes: an outline
     * follows the pointer and the next click drops it there. Unlike the tools above this one
     * is one-shot — it disarms itself once the net has been placed, because loading a net is a
     * single deliberate act rather than something repeated across clicks.
     */
    PLACE_LOADED_NET
}

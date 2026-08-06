package ua.stetsenkoinna.petriobj;

import java.io.Serializable;

/**
 * A weighted arc between a transition of one Petri-object and a place that belongs to
 * another Petri-object.
 *
 * <p>Arcs inside a single Petri net are stored as indices into the net's own place array
 * ({@link PetriT#getInP()} / {@link PetriT#getOutP()}), which makes them meaningless across
 * net boundaries. An external arc therefore holds a direct reference to the foreign
 * {@link PetriP} instead, so the transition can test, consume or produce tokens in a place
 * owned by a different Petri-object.
 *
 * <p>External arcs are never part of a {@link PetriNet}: they are wired by
 * {@link PetriObjModel#applyLinks()} from the model's link declarations and are dropped by
 * {@link PetriNet#clone()}, so a cloned model rebuilds them from the same declarations.
 *
 * @see PetriObjLink
 */
public class ExternalArc implements Serializable {

    private final PetriP place;
    private final int quantity;
    private final boolean informational;

    /**
     * @param place the foreign place this arc is attached to
     * @param quantity arc multiplicity — how many tokens are tested, consumed or produced
     * @param informational {@code true} for a test arc that only checks the marking without
     *        consuming it; meaningful for input arcs only, ignored for output arcs
     */
    public ExternalArc(PetriP place, int quantity, boolean informational) {
        if (place == null) {
            throw new IllegalArgumentException("External arc requires a target place");
        }
        if (quantity < 1) {
            throw new IllegalArgumentException("External arc multiplicity must be positive, got " + quantity);
        }
        this.place = place;
        this.quantity = quantity;
        this.informational = informational;
    }

    /**
     * @return the foreign place this arc is attached to
     */
    public PetriP getPlace() {
        return place;
    }

    /**
     * @return arc multiplicity
     */
    public int getQuantity() {
        return quantity;
    }

    /**
     * @return {@code true} if this input arc only tests the marking without consuming tokens
     */
    public boolean isInformational() {
        return informational;
    }
}

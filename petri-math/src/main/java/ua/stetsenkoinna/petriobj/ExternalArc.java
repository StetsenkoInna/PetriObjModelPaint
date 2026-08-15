package ua.stetsenkoinna.petriobj;

import java.io.Serializable;

/**
 * A weighted arc from a transition of one Petri-object into a place that belongs to
 * another Petri-object.
 *
 * <p>Arcs inside a single Petri net are stored as indices into the net's own place array
 * ({@link PetriT#getInP()} / {@link PetriT#getOutP()}), which makes them meaningless across
 * net boundaries. An external arc therefore holds a direct reference to the foreign
 * {@link PetriP} instead, so the transition can produce tokens in a place owned by a
 * different Petri-object.
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

    /**
     * @param place the foreign place this arc is attached to
     * @param quantity arc multiplicity, how many tokens each firing delivers
     */
    public ExternalArc(PetriP place, int quantity) {
        if (place == null) {
            throw new IllegalArgumentException("External arc requires a target place");
        }
        if (quantity < 1) {
            throw new IllegalArgumentException("External arc multiplicity must be positive, got " + quantity);
        }
        this.place = place;
        this.quantity = quantity;
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
}

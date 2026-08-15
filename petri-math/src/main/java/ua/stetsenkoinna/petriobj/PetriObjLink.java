package ua.stetsenkoinna.petriobj;

import java.io.Serializable;
import java.util.Objects;

/**
 * A declaration of one link between two Petri-objects of a {@link PetriObjModel}.
 *
 * <p>A link is stored as plain indices — the position of an object in the model's object
 * list and the position of a place or a transition inside that object's net — never as
 * object references. That keeps a link valid across {@link PetriObjModel#clone()} and makes
 * it directly representable in an interchange format such as PNML.
 *
 * <p>Links are declarative: {@link PetriObjModel#applyLinks()} turns them into the actual
 * wiring (shared place instances and {@link ExternalArc}s), and can do so again on a fresh
 * copy of the same objects.
 */
public final class PetriObjLink implements Serializable {

    private final PetriObjLinkType type;
    private final int sourceObject;
    private final int sourceElement;
    private final int targetObject;
    private final int targetElement;
    private final int quantity;

    private PetriObjLink(PetriObjLinkType type, int sourceObject, int sourceElement,
                         int targetObject, int targetElement, int quantity) {
        if (sourceObject < 0 || targetObject < 0 || sourceElement < 0 || targetElement < 0) {
            throw new IllegalArgumentException("Petri-object link indices must not be negative");
        }
        if (quantity < 1) {
            throw new IllegalArgumentException("Petri-object link multiplicity must be positive, got " + quantity);
        }
        this.type = Objects.requireNonNull(type, "type");
        this.sourceObject = sourceObject;
        this.sourceElement = sourceElement;
        this.targetObject = targetObject;
        this.targetElement = targetElement;
        this.quantity = quantity;
    }

    /**
     * Declares that the source object's place and the target object's place are one place.
     *
     * @param sourceObject index of the object whose place slot is redirected
     * @param sourcePlace index of that place inside the source object's net
     * @param targetObject index of the object that owns the resulting shared place
     * @param targetPlace index of that place inside the target object's net
     */
    public static PetriObjLink placeFusion(int sourceObject, int sourcePlace,
                                           int targetObject, int targetPlace) {
        return new PetriObjLink(PetriObjLinkType.PLACE_FUSION,
                sourceObject, sourcePlace, targetObject, targetPlace, 1);
    }

    /**
     * Declares that a transition of the source object produces tokens into a place of the
     * target object every time it fires.
     *
     * @param sourceObject index of the object that owns the firing transition
     * @param sourceTransition index of that transition inside the source object's net
     * @param targetObject index of the object that owns the receiving place
     * @param targetPlace index of that place inside the target object's net
     * @param quantity how many tokens each firing delivers
     */
    public static PetriObjLink transitionToPlace(int sourceObject, int sourceTransition,
                                                 int targetObject, int targetPlace, int quantity) {
        return new PetriObjLink(PetriObjLinkType.TRANSITION_TO_PLACE,
                sourceObject, sourceTransition, targetObject, targetPlace, quantity);
    }

    public PetriObjLinkType getType() {
        return type;
    }

    /**
     * @return index of the source object in the model's object list
     */
    public int getSourceObject() {
        return sourceObject;
    }

    /**
     * @return index of the source element inside the source object's net — a place for
     *         {@link PetriObjLinkType#PLACE_FUSION}, a transition for
     *         {@link PetriObjLinkType#TRANSITION_TO_PLACE}
     */
    public int getSourceElement() {
        return sourceElement;
    }

    /**
     * @return index of the target object in the model's object list
     */
    public int getTargetObject() {
        return targetObject;
    }

    /**
     * @return index of the target element inside the target object's net, always a place,
     *         whichever link type this is
     */
    public int getTargetElement() {
        return targetElement;
    }

    /**
     * @return arc multiplicity; always 1 for a place fusion
     */
    public int getQuantity() {
        return quantity;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof PetriObjLink other)) {
            return false;
        }
        return type == other.type
                && sourceObject == other.sourceObject
                && sourceElement == other.sourceElement
                && targetObject == other.targetObject
                && targetElement == other.targetElement
                && quantity == other.quantity;
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, sourceObject, sourceElement, targetObject, targetElement,
                quantity);
    }

    @Override
    public String toString() {
        return switch (type) {
            case PLACE_FUSION -> "O" + sourceObject + ".p[" + sourceElement + "] = O"
                    + targetObject + ".p[" + targetElement + "]";
            case TRANSITION_TO_PLACE -> "O" + sourceObject + ".t[" + sourceElement + "] -> O"
                    + targetObject + ".p[" + targetElement + "] x" + quantity;
        };
    }
}

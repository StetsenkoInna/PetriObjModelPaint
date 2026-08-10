package ua.stetsenkoinna.petriobj;

/**
 * The kinds of link that may connect two Petri-objects of a {@link PetriObjModel}.
 *
 * @see PetriObjLink
 */
public enum PetriObjLinkType {

    /**
     * Two places become a single shared place: the source object's place slot starts
     * pointing at the target object's place instance. This is the classic composition
     * operation of the Petri-object simulation technique — both objects then read and
     * change the same marking.
     */
    PLACE_FUSION,

    /**
     * A transition of the source object produces tokens directly into a place of the
     * target object, without the source object owning an output place of its own.
     */
    TRANSITION_TO_PLACE,

    /**
     * A transition of the target object takes a place of the source object as an extra
     * input: consuming (a regular arc) or only testing its marking (an informational arc).
     */
    PLACE_TO_TRANSITION
}

package ua.stetsenkoinna.graphnet;

import java.io.Serializable;
import java.util.List;

/**
 * A group of Petri-objects as a saved document states it: a name and the objects that belong to
 * it, addressed the way every other link in the document addresses an object — by index.
 *
 * <p>The counterpart of {@link GraphObjectGroup}, which is the live thing on the canvas. They are
 * kept apart for the same reason a link is a {@code GraphPlaceFusion} on the canvas and a
 * {@code PetriObjLink} in the model: one holds references to drawn objects, the other holds
 * positions, and positions are what survives being written to a file and read back.
 *
 * <p>A group carries no semantics of its own. It says these objects were stamped together, which
 * lets the editor go on treating them as one; the model those objects make up is exactly the
 * model they would make up without it. That is why a reader may ignore this entirely and still
 * read the document correctly — and why it is written as tool-specific information rather than
 * asked of the PNML grammar, which has nothing to say about it.
 *
 * @param name the group's name
 * @param memberObjects indices of its member objects, in stamping order
 * @param templateMethod the net library method the shared net came from, or {@code null}
 */
public record PetriObjectGroupRef(String name, List<Integer> memberObjects, String templateMethod)
        implements Serializable {

    /**
     * @param name the group's name
     * @param memberObjects indices of its members; copied, so the caller may keep its own list
     * @param templateMethod the library method behind the shared net, or {@code null}
     */
    public PetriObjectGroupRef {
        memberObjects = List.copyOf(memberObjects);
    }
}

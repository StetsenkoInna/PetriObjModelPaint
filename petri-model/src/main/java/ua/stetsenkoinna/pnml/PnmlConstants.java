package ua.stetsenkoinna.pnml;

/**
 * Constants for PNML format according to ISO/IEC 15909
 */
public final class PnmlConstants {

    private PnmlConstants() {
        // Utility class
    }

    // XML namespaces
    public static final String PNML_NAMESPACE = "http://www.pnml.org/version-2009/grammar/pnml";
    public static final String PTNET_TYPE = "http://www.pnml.org/version-2009/grammar/ptnet";

    // XML element names
    public static final String ELEMENT_PNML = "pnml";
    public static final String ELEMENT_NET = "net";
    public static final String ELEMENT_PAGE = "page";
    public static final String ELEMENT_PLACE = "place";
    public static final String ELEMENT_TRANSITION = "transition";
    public static final String ELEMENT_ARC = "arc";
    public static final String ELEMENT_NAME = "name";
    public static final String ELEMENT_TEXT = "text";
    public static final String ELEMENT_INITIAL_MARKING = "initialMarking";
    public static final String ELEMENT_INSCRIPTION = "inscription";
    public static final String ELEMENT_GRAPHICS = "graphics";
    public static final String ELEMENT_POSITION = "position";
    public static final String ELEMENT_OFFSET = "offset";
    public static final String ELEMENT_TOOLSPECIFIC = "toolspecific";
    public static final String ELEMENT_COORDINATES = "coordinates";
    public static final String ELEMENT_INFORMATIONAL = "informational";
    public static final String ELEMENT_INITIAL_MARKING_PARAMETER = "initialMarkingParameter";

    /**
     * ISO/IEC 15909-2 reference nodes: a node on one page that stands for a node on another.
     * They are how the standard says "these two pages share this element", the only way an
     * inter-object link is visible to a reader that does not know this tool.
     */
    public static final String ELEMENT_REFERENCE_PLACE = "referencePlace";
    public static final String ELEMENT_REFERENCE_TRANSITION = "referenceTransition";

    // XML attribute names
    public static final String ATTR_ID = "id";
    public static final String ATTR_TYPE = "type";
    public static final String ATTR_SOURCE = "source";
    public static final String ATTR_TARGET = "target";
    public static final String ATTR_TOOL = "tool";
    public static final String ATTR_VERSION = "version";
    public static final String ATTR_X = "x";
    public static final String ATTR_Y = "y";
    public static final String ATTR_XMLNS = "xmlns";

    /** Points a reference node at the node it stands for. */
    public static final String ATTR_REF = "ref";

    // Tool-specific values
    public static final String TOOL_PETRI_OBJ_MODEL = "PetriObjModel";
    public static final String TOOL_VERSION = "1.0";

    /**
     * Tool-specific version of the first composed format, pages plus a positional link
     * block, with no reference nodes. Still written by other tools in this family and still
     * sitting in saved files, so it stays a value readers must accept.
     */
    public static final String TOOL_VERSION_OBJECT_MODEL = "2.0";

    /**
     * Tool-specific version stamped on the page-level and net-level blocks of a document
     * whose inter-object structure is also expressed with reference nodes.
     *
     * <p>It is a hint about what else the document carries, never a filter: a reader that
     * selects tool-specific blocks by their {@code version} would drop the object metadata
     * of every document written by a newer build. Match on {@link #ATTR_TOOL} only.
     */
    public static final String TOOL_VERSION_OBJECT_MODEL_CONFORMANT = "2.1";

    // Petri-object model extension: one <page> per Petri-object, links at net level
    public static final String ELEMENT_PETRI_OBJECT = "petriObject";
    public static final String ELEMENT_NET_TEMPLATE = "netTemplate";
    public static final String ELEMENT_TEMPLATE_ARGUMENT = "argument";
    public static final String ELEMENT_PETRI_OBJECT_LINKS = "petriObjectLinks";
    public static final String ELEMENT_LINK = "link";

    /**
     * Tells apart the two things a reference node can mean to the Petri-object technique.
     * Flattening a document deliberately erases the difference, to a conformant reader both
     * are node merges, so the distinction can only live in a tool-specific block.
     */
    public static final String ELEMENT_REFERENCE_ROLE = "referenceRole";

    /** The object's own place slot <em>is</em> the shared place: it has no place of its own. */
    public static final String ROLE_FUSION = "fusion";

    /** The page only draws a stand-in for a place or transition that belongs to another object. */
    public static final String ROLE_REPRESENTATIVE = "representative";

    /**
     * The initial marking a fused-away place used to carry. It has no effect once the fusion
     * is wired, the target's place is the surviving one, so it is not part of the standard
     * projection, but a drawing that loses it silently loses what the user typed.
     */
    public static final String ELEMENT_FUSED_INITIAL_MARKING = "fusedInitialMarking";

    public static final String ATTR_INDEX = "index";
    public static final String ATTR_NAME = "name";
    public static final String ATTR_PRIORITY = "priority";
    public static final String ATTR_METHOD = "method";
    public static final String ATTR_LINK_TYPE = "type";
    public static final String ATTR_SOURCE_OBJECT = "sourceObject";
    public static final String ATTR_SOURCE_ELEMENT = "sourceElement";
    public static final String ATTR_TARGET_OBJECT = "targetObject";
    public static final String ATTR_TARGET_ELEMENT = "targetElement";
    public static final String ATTR_QUANTITY = "quantity";

    /**
     * Retired with {@link #LINK_TYPE_PLACE_TO_TRANSITION}, the only link type that ever
     * carried it. No link is written with it and none is read from it; a test arc is a
     * property of an arc inside one object's own net, where it is written as
     * {@link #ELEMENT_INFORMATIONAL}.
     */
    public static final String ATTR_INFORMATIONAL = "informational";

    /**
     * Id-valued companions of {@link #ATTR_SOURCE_ELEMENT} / {@link #ATTR_TARGET_ELEMENT}.
     * The positional indices keep their meaning, but an id survives a reader that numbers
     * elements differently, so it is what a conformant reader matches on first.
     */
    public static final String ATTR_SOURCE_ELEMENT_ID = "sourceElementId";
    public static final String ATTR_TARGET_ELEMENT_ID = "targetElementId";
    public static final String ATTR_WIDTH = "width";
    public static final String ATTR_HEIGHT = "height";
    public static final String ATTR_COLLAPSED = "collapsed";
    /**
     * The index of the Petri-object this one is nested inside, absent for a top-level
     * object. Tool-specific, like the rest of the petriObject element: standard PNML has no
     * nesting between sibling pages, so a foreign reader simply sees flat pages, exactly
     * what it saw before this attribute existed.
     */
    public static final String ATTR_PARENT_OBJECT = "parentObject";

    // Link type values, kept stable regardless of how the enum constants are named
    public static final String LINK_TYPE_PLACE_FUSION = "placeFusion";
    public static final String LINK_TYPE_TRANSITION_TO_PLACE = "transitionToPlace";

    /**
     * A retired link type, kept only so that a reader recognises it and can say why the
     * document is refused. It made a place of one Petri-object an extra input of a transition
     * of another, which gave that transition a second set of input places and so contradicted
     * the definition of a Petri net transition. Nothing writes it any more, and a document
     * that declares it is rejected rather than converted, because a silent conversion would
     * change what the stored model says.
     */
    public static final String LINK_TYPE_PLACE_TO_TRANSITION = "placeToTransition";

    // Default values
    public static final String DEFAULT_NET_ID = "net1";
    public static final String DEFAULT_PAGE_ID = "page1";

    /** Prefix of the generated page id of the n-th Petri-object. */
    public static final String OBJECT_PAGE_ID_PREFIX = "object";

    /**
     * Prefix of a representative reference node's id, followed by the page id and the id of
     * the element it stands for. Reserved: an element id is never allowed to start with it.
     */
    public static final String REFERENCE_NODE_ID_PREFIX = "ref_";

    /** Prefix of the id of an arc that realises a link, followed by the link's index. */
    public static final String LINK_ARC_ID_PREFIX = "larc_";

    /**
     * Prefix that namespaces an element id by its object, used only when the same id occurs
     * in more than one object of a document.
     */
    public static final String OBJECT_ID_NAMESPACE_FORMAT = "o%d_%s";

    /** Matches a namespace prefix already applied, so that re-export stays idempotent. */
    public static final String OBJECT_ID_NAMESPACE_PATTERN = "^o\\d+_";

    /**
     * How far a chain of reference nodes is followed before the document is declared
     * malformed. A legitimate chain is one or two hops; anything deeper is a cycle that a
     * naive follower would spin on forever.
     */
    public static final int MAX_REFERENCE_DEPTH = 16;

    // Error messages
    public static final String ERROR_INVALID_ROOT = "Invalid PNML file: root element must be 'pnml'";
    public static final String ERROR_NO_NET = "No net element found in PNML file";
    public static final String ERROR_OBJECT_MODEL_NOT_SUPPORTED =
            "This PNML document describes a Petri-object model of %d objects. "
                    + "Read it with PnmlModelParser, or send it to the v2 simulation API.";
    public static final String ERROR_DUPLICATE_ID =
            "Duplicate element id '%s': a reference node's ref= would be ambiguous";
    public static final String ERROR_REFERENCE_CYCLE =
            "Reference node '%s' stands for itself, directly or through a cycle";
    public static final String ERROR_DANGLING_REFERENCE =
            "Reference node '%s' points at '%s', which is not an element of this net";
    public static final String ERROR_NO_OBJECTS =
            "A Petri-object model document needs at least one object";

    /**
     * Refuses a {@code <link type="placeToTransition">} declaration. The document is not
     * converted, because a conversion would change what the file states, so the message has
     * to spell out the canonical form instead.
     */
    public static final String ERROR_RETIRED_LINK_TYPE_DECLARED =
            "This document declares a link of type '%s' from place %d of Petri-object %d to "
                    + "transition %d of Petri-object %d. That link type is no longer supported: "
                    + "a transition takes its input places from its own Petri-object only. "
                    + "Express it as a place fusion that shares the place with the Petri-object "
                    + "owning the transition, plus an ordinary arc from the shared place to that "
                    + "transition inside that Petri-object.";

    /**
     * Refuses the same link stated in the standard's own terms: a reference node standing for
     * a foreign element, with an arc that makes it an input of a local transition.
     */
    public static final String ERROR_RETIRED_LINK_TYPE_DRAWN =
            "Arc '%s' makes a place of one Petri-object an input of a transition of another, "
                    + "through reference node '%s'. That is no longer supported: a transition "
                    + "takes its input places from its own Petri-object only. Draw the place as "
                    + "a shared place of the page that owns the transition, a <referencePlace> "
                    + "standing in the page's own place slot, and run an ordinary arc from it to "
                    + "the transition on that same page.";
}

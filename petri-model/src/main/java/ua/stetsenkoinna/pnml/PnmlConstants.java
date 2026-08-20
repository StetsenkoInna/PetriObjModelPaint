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

    /** This project's own identity, the one its readers look for first. */
    public static final String TOOL_PETRI_OBJ_MODEL = "PetriObjModel";

    /**
     * The identity of the web application that shares this PNML dialect.
     *
     * <p>Every document written here carries the same payload twice, once under each tool
     * name, so that a file written by either tool opens in both. Each tool finds its own
     * block, ignores the other, and nothing has to be converted on the way across.
     * {@link #TOOL_PETRI_OBJ_MODEL} is written first, so a reader that stops at the first
     * block it recognises reads exactly what it read before the second one existed.
     */
    public static final String TOOL_PETRI_NET_SIM = "PetriNetSim";

    /**
     * The release of this project that the {@link #TOOL_PETRI_OBJ_MODEL} vocabulary belongs
     * to, taken from this project's own {@code pom.xml}.
     *
     * <p>It states a release, not a format revision, and no reader in this family filters on
     * it; see {@link #TOOL_VERSION_OBJECT_MODEL_CONFORMANT}. That is what makes it safe for
     * the value to move with the project.
     */
    public static final String TOOL_VERSION_PETRI_OBJ_MODEL = "2.2.2";

    /**
     * The release of the web application that the {@link #TOOL_PETRI_NET_SIM} vocabulary
     * belongs to, taken from that project's {@code package.json}.
     */
    public static final String TOOL_VERSION_PETRI_NET_SIM = "1.0.0";

    /**
     * The element-level version of the first format, stamped on the block of a place, a
     * transition or an arc.
     *
     * <p>Nothing writes it any more: the writers stamp {@link #TOOL_VERSION_PETRI_OBJ_MODEL}
     * on every block they produce. It sits in every file saved before that change, so it
     * stays a value readers must accept.
     */
    public static final String TOOL_VERSION = "1.0";

    /**
     * Tool-specific version of the first composed format, pages plus a positional link
     * block, with no reference nodes.
     *
     * <p>This project never wrote it, and no writer produces it now: the writers stamp
     * {@link #TOOL_VERSION_PETRI_OBJ_MODEL}. Other tools in this family did write it, and it
     * is still sitting in saved files, so it stays a value readers must accept.
     */
    public static final String TOOL_VERSION_OBJECT_MODEL = "2.0";

    /**
     * Tool-specific version stamped on the page-level and net-level blocks of a document
     * whose inter-object structure is also expressed with reference nodes.
     *
     * <p>A version is a hint about what else the document carries, never a filter: a reader
     * that selects tool-specific blocks by their {@code version} would drop the object
     * metadata of every document written by a newer build. Match on {@link #ATTR_TOOL} only.
     *
     * <p>No writer stamps it any more: they state this project's release,
     * {@link #TOOL_VERSION_PETRI_OBJ_MODEL}. It stays here as a value readers must accept
     * rather than one they may expect.
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
    /**
     * Refuses a nested document whose pages do not state a usable object index. Document order
     * is not object order once pages nest, and the links address objects by that index, so the
     * order cannot be guessed from the document without re-binding every link.
     */
    public static final String ERROR_UNUSABLE_PAGE_INDEX =
            "This document nests its pages, so every page must state a unique "
                    + "<petriObject index> in 0..%d. Page order cannot be taken from the "
                    + "document, and the links address objects by that index.";

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

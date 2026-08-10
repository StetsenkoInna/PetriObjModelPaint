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

    // Tool-specific values
    public static final String TOOL_PETRI_OBJ_MODEL = "PetriObjModel";
    public static final String TOOL_VERSION = "1.0";

    /** Tool-specific version that marks a document carrying a composed Petri-object model. */
    public static final String TOOL_VERSION_OBJECT_MODEL = "2.0";

    // Petri-object model extension: one <page> per Petri-object, links at net level
    public static final String ELEMENT_PETRI_OBJECT = "petriObject";
    public static final String ELEMENT_NET_TEMPLATE = "netTemplate";
    public static final String ELEMENT_TEMPLATE_ARGUMENT = "argument";
    public static final String ELEMENT_PETRI_OBJECT_LINKS = "petriObjectLinks";
    public static final String ELEMENT_LINK = "link";

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
    public static final String ATTR_INFORMATIONAL = "informational";
    public static final String ATTR_WIDTH = "width";
    public static final String ATTR_HEIGHT = "height";
    public static final String ATTR_COLLAPSED = "collapsed";

    // Link type values, kept stable regardless of how the enum constants are named
    public static final String LINK_TYPE_PLACE_FUSION = "placeFusion";
    public static final String LINK_TYPE_TRANSITION_TO_PLACE = "transitionToPlace";
    public static final String LINK_TYPE_PLACE_TO_TRANSITION = "placeToTransition";

    // Default values
    public static final String DEFAULT_NET_ID = "net1";
    public static final String DEFAULT_PAGE_ID = "page1";

    /** Prefix of the generated page id of the n-th Petri-object. */
    public static final String OBJECT_PAGE_ID_PREFIX = "object";

    // Error messages
    public static final String ERROR_INVALID_ROOT = "Invalid PNML file: root element must be 'pnml'";
    public static final String ERROR_NO_NET = "No net element found in PNML file";
    public static final String ERROR_OBJECT_MODEL_NOT_SUPPORTED =
            "This PNML document describes a Petri-object model of %d objects. "
                    + "Read it with PnmlModelParser, or send it to the v2 simulation API.";
}

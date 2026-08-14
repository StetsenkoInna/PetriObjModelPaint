package ua.stetsenkoinna.pnml;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.*;
import ua.stetsenkoinna.petriobj.*;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.File;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

/**
 * Parser for PNML (Petri Net Markup Language) format according to ISO/IEC 15909
 *
 * @author Serhii Rybak
 */
public class PnmlParser {

    private static final Logger log = LoggerFactory.getLogger(PnmlParser.class);

    private final Map<String, Integer> placeIdToNumber = new HashMap<>();
    private final Map<String, Integer> transitionIdToNumber = new HashMap<>();
    private final Map<Integer, java.awt.geom.Point2D.Double> placeCoordinates = new HashMap<>();
    private final Map<Integer, java.awt.geom.Point2D.Double> transitionCoordinates = new HashMap<>();

    /**
     * Parse PNML from an XML string.
     *
     * @param xml PNML document as a string
     * @return PetriNet object
     * @throws Exception if parsing fails
     */
    public PetriNet parseXml(String xml) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document document = builder.parse(new InputSource(new StringReader(xml)));
        return buildNet(document);
    }

    /**
     * Parse PNML file and create PetriNet object
     *
     * @param file PNML file to parse
     * @return PetriNet object
     * @throws Exception if parsing fails
     */
    public PetriNet parse(File file) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = factory.newDocumentBuilder();
        return buildNet(builder.parse(file));
    }

    private PetriNet buildNet(Document document) throws Exception {
        Element netElement = findNetElement(document);
        int pages = XmlHelper.directChildren(netElement, PnmlConstants.ELEMENT_PAGE).size();
        if (pages > 1) {
            // Every page is a Petri-object of its own; flattening them would silently merge
            // nets that only a composed model can run.
            throw new Exception(String.format(PnmlConstants.ERROR_OBJECT_MODEL_NOT_SUPPORTED, pages));
        }
        return parseScope(netElement, netElement.getAttribute(PnmlConstants.ATTR_ID));
    }

    /**
     * Locates the single {@code <net>} element of a PNML document.
     *
     * @param document parsed PNML document
     * @return the net element
     * @throws Exception if the document is not PNML or holds no net
     */
    static Element findNetElement(Document document) throws Exception {
        Element root = document.getDocumentElement();
        if (!PnmlConstants.ELEMENT_PNML.equals(root.getTagName())) {
            throw new Exception(PnmlConstants.ERROR_INVALID_ROOT);
        }

        NodeList netNodes = root.getElementsByTagName(PnmlConstants.ELEMENT_NET);
        if (netNodes.getLength() == 0) {
            throw new Exception(PnmlConstants.ERROR_NO_NET);
        }
        return (Element) netNodes.item(0);
    }

    /**
     * Builds one Petri net from the places, transitions and arcs found below the given
     * element — a whole {@code <net>} for a plain document, one {@code <page>} for a single
     * Petri-object of a composed model.
     *
     * <p>Each parser instance keeps its own id-to-number maps, so a caller reading several
     * pages has to use a fresh parser per page.
     *
     * @param scope the element to read the net from
     * @param netName name to give the resulting net
     * @return the parsed net
     * @throws ExceptionInvalidTimeDelay if the described net has an invalid structure
     */
    PetriNet parseScope(Element scope, String netName) throws ExceptionInvalidTimeDelay {
        return parseScope(scope, netName, null);
    }

    /**
     * Builds one Petri net from a page of a composed document, taking the page's reference
     * nodes into account.
     *
     * <p>A fusion {@code <referencePlace>} is materialised as an ordinary place of this net,
     * at exactly the position of the {@code <place>} it replaces, the object still has that
     * slot, it simply does not own the place that fills it, and the fusion link declared for
     * the slot is what makes the two objects share one instance at wiring time. Its marking
     * is the one the reference node preserved, so a document that is read and written again
     * says the same thing.
     *
     * <p>A representative reference node is not part of this net at all: neither it nor the
     * arcs that touch it, which are links rather than arcs of the object.
     *
     * @param scope the element to read the net from
     * @param netName name to give the resulting net
     * @param references what the document's reference nodes mean for this page, or
     *        {@code null} for a legacy document, which is then read exactly as before
     * @return the parsed net
     * @throws ExceptionInvalidTimeDelay if the described net has an invalid structure
     */
    PetriNet parseScope(Element scope, String netName, ReferenceNodeIndex.PageReferences references)
            throws ExceptionInvalidTimeDelay {
        ArrayList<PetriP> places = parsePlaces(scope, references);
        ArrayList<PetriT> transitions = parseTransitions(scope);
        ArrayList<ArcIn> arcIns = new ArrayList<>();
        ArrayList<ArcOut> arcOuts = new ArrayList<>();
        parseArcs(scope, arcIns, arcOuts, references);

        return new PetriNet(netName, places, transitions, arcIns, arcOuts);
    }

    /**
     * Parse places from net element
     */
    private ArrayList<PetriP> parsePlaces(Element netElement,
                                          ReferenceNodeIndex.PageReferences references) {
        ArrayList<PetriP> places = new ArrayList<>();
        if (references == null) {
            NodeList placeNodes = netElement.getElementsByTagName(PnmlConstants.ELEMENT_PLACE);
            for (int i = 0; i < placeNodes.getLength(); i++) {
                places.add(parsePlace((Element) placeNodes.item(i)));
            }
            return places;
        }
        // The slot list already interleaves the fusion references with the page's own
        // places, in the document order that defines every link index.
        for (Element slot : references.placeSlots()) {
            places.add(PnmlConstants.ELEMENT_REFERENCE_PLACE.equals(slot.getTagName())
                    ? parseFusionReferencePlace(slot)
                    : parsePlace(slot));
        }
        return places;
    }

    /**
     * Reads one {@code <place>} and registers it so that arcs can find it by id.
     */
    private PetriP parsePlace(Element placeElement) {
        String id = placeElement.getAttribute(PnmlConstants.ATTR_ID);

        // Get place name
        String name = XmlHelper.getTextContent(placeElement, PnmlConstants.ELEMENT_NAME);
        if (name == null) {
            name = id; // default to ID
        }

        // Get initial marking
        String markingText = XmlHelper.getTextContent(placeElement, PnmlConstants.ELEMENT_INITIAL_MARKING);
        int marking = XmlHelper.parseIntSafe(markingText, 0);

        // Parse place parameters from toolspecific section
        String markingParam = null;
        NodeList toolspecificNodes = placeElement.getElementsByTagName(PnmlConstants.ELEMENT_TOOLSPECIFIC);
        for (int j = 0; j < toolspecificNodes.getLength(); j++) {
            Element toolElement = (Element) toolspecificNodes.item(j);
            if (PnmlConstants.TOOL_PETRI_OBJ_MODEL.equals(toolElement.getAttribute(PnmlConstants.ATTR_TOOL))) {
                NodeList markingParamNodes = toolElement.getElementsByTagName(PnmlConstants.ELEMENT_INITIAL_MARKING_PARAMETER);
                if (markingParamNodes.getLength() > 0) {
                    markingParam = markingParamNodes.item(0).getTextContent();
                }
            }
        }

        PetriP place = new PetriP(id, name, marking);

        // Set marking parameter if present
        if (XmlHelper.isNotEmpty(markingParam)) {
            place.setMarkParam(markingParam);
        }

        placeIdToNumber.put(id, place.getNumber());

        // Parse coordinates from toolspecific elements
        parseCoordinatesForPlace(placeElement, place.getNumber());
        return place;
    }

    /**
     * Materialises a fusion {@code <referencePlace>} as this object's place in that slot.
     *
     * <p>The place carries the reference node's own id, so the page's arcs keep resolving
     * unchanged, and the marking the reference node preserved, a reference node may not
     * carry an {@code <initialMarking>}, since after flattening it is the same node as the
     * one it stands for and the tokens would be counted twice.
     */
    private PetriP parseFusionReferencePlace(Element reference) {
        String id = reference.getAttribute(PnmlConstants.ATTR_ID);
        String name = XmlHelper.getTextContent(reference, PnmlConstants.ELEMENT_NAME);
        if (name == null) {
            name = id;
        }
        int marking = XmlHelper.parseIntSafe(
                XmlHelper.getToolSpecificText(reference, PnmlConstants.ELEMENT_FUSED_INITIAL_MARKING), 0);

        PetriP place = new PetriP(id, name, marking);
        // The slot may still be driven by a model parameter; that belongs to the object, not
        // to the place it borrows, so it travels with the reference node.
        String markingParam = XmlHelper.getToolSpecificText(reference, PnmlConstants.ELEMENT_INITIAL_MARKING_PARAMETER);
        if (XmlHelper.isNotEmpty(markingParam)) {
            place.setMarkParam(markingParam);
        }
        placeIdToNumber.put(id, place.getNumber());
        parseCoordinatesForPlace(reference, place.getNumber());
        return place;
    }

    /**
     * Parse transitions from net element
     */
    private ArrayList<PetriT> parseTransitions(Element netElement) {
        ArrayList<PetriT> transitions = new ArrayList<>();
        NodeList transitionNodes = netElement.getElementsByTagName(PnmlConstants.ELEMENT_TRANSITION);

        for (int i = 0; i < transitionNodes.getLength(); i++) {
            Element transitionElement = (Element) transitionNodes.item(i);
            String id = transitionElement.getAttribute(PnmlConstants.ATTR_ID);

            // Get transition name
            String name = XmlHelper.getTextContent(transitionElement, PnmlConstants.ELEMENT_NAME);
            if (name == null) {
                name = id; // default to ID
            }

            // Parse transition parameters from toolspecific section
            double timeDelay = 0.0;
            double delayMeanValue = 0.0;
            double standardDeviation = 0.0;
            int priority = 0;
            double probability = 1.0;
            String distribution = null;

            String timeDelayParam = null;
            String priorityParam = null;
            String probabilityParam = null;
            String distributionParam = null;

            NodeList toolspecificNodes = transitionElement.getElementsByTagName(PnmlConstants.ELEMENT_TOOLSPECIFIC);
            for (int j = 0; j < toolspecificNodes.getLength(); j++) {
                Element toolElement = (Element) toolspecificNodes.item(j);
                if (PnmlConstants.TOOL_PETRI_OBJ_MODEL.equals(toolElement.getAttribute(PnmlConstants.ATTR_TOOL))) {
                    // Parse time delay or its parameter
                    NodeList delayNodes = toolElement.getElementsByTagName("timeDelay");
                    if (delayNodes.getLength() > 0) {
                        timeDelay = XmlHelper.parseDoubleSafe(delayNodes.item(0).getTextContent(), 0.0);
                    }
                    NodeList delayParamNodes = toolElement.getElementsByTagName("timeDelayParameter");
                    if (delayParamNodes.getLength() > 0) {
                        timeDelayParam = delayParamNodes.item(0).getTextContent();
                    }

                    // Parse delay mean value
                    NodeList delayMeanNodes = toolElement.getElementsByTagName("delayMeanValue");
                    if (delayMeanNodes.getLength() > 0) {
                        delayMeanValue = XmlHelper.parseDoubleSafe(delayMeanNodes.item(0).getTextContent(), 0.0);
                    }

                    // Parse standard deviation
                    NodeList stdDeviationNodes = toolElement.getElementsByTagName("standardDeviation");
                    if (stdDeviationNodes.getLength() > 0) {
                        standardDeviation = XmlHelper.parseDoubleSafe(stdDeviationNodes.item(0).getTextContent(), 0.0);
                    }

                    // Parse priority or its parameter
                    NodeList priorityNodes = toolElement.getElementsByTagName("priority");
                    if (priorityNodes.getLength() > 0) {
                        priority = XmlHelper.parseIntSafe(priorityNodes.item(0).getTextContent(), 0);
                    }
                    NodeList priorityParamNodes = toolElement.getElementsByTagName("priorityParameter");
                    if (priorityParamNodes.getLength() > 0) {
                        priorityParam = priorityParamNodes.item(0).getTextContent();
                    }

                    // Parse probability or its parameter
                    NodeList probabilityNodes = toolElement.getElementsByTagName("probability");
                    if (probabilityNodes.getLength() > 0) {
                        probability = XmlHelper.parseDoubleSafe(probabilityNodes.item(0).getTextContent(), 1.0);
                    }
                    NodeList probabilityParamNodes = toolElement.getElementsByTagName("probabilityParameter");
                    if (probabilityParamNodes.getLength() > 0) {
                        probabilityParam = probabilityParamNodes.item(0).getTextContent();
                    }

                    // Parse distribution or its parameter
                    NodeList distributionNodes = toolElement.getElementsByTagName("distribution");
                    if (distributionNodes.getLength() > 0) {
                        distribution = distributionNodes.item(0).getTextContent();
                    }
                    NodeList distributionParamNodes = toolElement.getElementsByTagName("distributionParameter");
                    if (distributionParamNodes.getLength() > 0) {
                        distributionParam = distributionParamNodes.item(0).getTextContent();
                    }
                }
            }

            // Use delayMeanValue if available, otherwise use timeDelay
            double meanValue = (delayMeanValue > 0) ? delayMeanValue : timeDelay;
            PetriT transition = new PetriT(id, name, meanValue);

            // Set additional properties
            if (priority != 0) {
                transition.setPriority(priority);
            }
            if (probability != 1.0) {
                transition.setProbability(probability);
            }
            if (distribution != null && !distribution.isEmpty()) {
                transition.setDistribution(distribution, meanValue);
            }

            // Set standard deviation if available
            if (standardDeviation > 0) {
                transition.setParamDeviation(standardDeviation);
            }

            // Set parameter names if present
            if (XmlHelper.isNotEmpty(timeDelayParam)) {
                transition.setParameterParam(timeDelayParam);
            }
            if (XmlHelper.isNotEmpty(priorityParam)) {
                transition.setPriorityParam(priorityParam);
            }
            if (XmlHelper.isNotEmpty(probabilityParam)) {
                transition.setProbabilityParam(probabilityParam);
            }
            if (XmlHelper.isNotEmpty(distributionParam)) {
                transition.setDistributionParam(distributionParam);
            }

            transitions.add(transition);
            transitionIdToNumber.put(id, transition.getNumber());

            // Parse coordinates from toolspecific elements
            parseCoordinatesForTransition(transitionElement, transition.getNumber());
        }

        return transitions;
    }

    /**
     * Parse arcs from net element
     */
    private void parseArcs(Element netElement, ArrayList<ArcIn> arcIns, ArrayList<ArcOut> arcOuts,
                           ReferenceNodeIndex.PageReferences references) {
        NodeList arcNodes = netElement.getElementsByTagName(PnmlConstants.ELEMENT_ARC);

        for (int i = 0; i < arcNodes.getLength(); i++) {
            Element arcElement = (Element) arcNodes.item(i);
            String arcId = arcElement.getAttribute(PnmlConstants.ATTR_ID);
            String source = arcElement.getAttribute(PnmlConstants.ATTR_SOURCE);
            String target = arcElement.getAttribute(PnmlConstants.ATTR_TARGET);

            // An arc that touches a representative reference node crosses an object boundary;
            // it is read as a link, not as an arc of this object's net.
            if (references != null && references.linkArcIds().contains(arcId)) {
                continue;
            }

            // Get arc weight
            String weightText = XmlHelper.getTextContent(arcElement, PnmlConstants.ELEMENT_INSCRIPTION);
            int weight = XmlHelper.parseIntSafe(weightText, 1);

            // Parse toolspecific information for informational arcs and parameters
            boolean isInformational = false;
            String infParamName = null;
            String kParamName = null;

            NodeList toolspecificNodes = arcElement.getElementsByTagName(PnmlConstants.ELEMENT_TOOLSPECIFIC);
            for (int j = 0; j < toolspecificNodes.getLength(); j++) {
                Element toolElement = (Element) toolspecificNodes.item(j);
                if (PnmlConstants.TOOL_PETRI_OBJ_MODEL.equals(toolElement.getAttribute(PnmlConstants.ATTR_TOOL))) {
                    // Check for informational flag
                    NodeList infNodes = toolElement.getElementsByTagName(PnmlConstants.ELEMENT_INFORMATIONAL);
                    if (infNodes.getLength() > 0) {
                        isInformational = "true".equals(infNodes.item(0).getTextContent());
                    }

                    // Check for arc type (inhibitor/read arcs map to informational arcs)
                    NodeList arcTypeNodes = toolElement.getElementsByTagName("arcType");
                    if (arcTypeNodes.getLength() > 0) {
                        String arcType = arcTypeNodes.item(0).getTextContent();
                        if ("inhibitor".equalsIgnoreCase(arcType) || "read".equalsIgnoreCase(arcType)) {
                            isInformational = true;
                        }
                    }

                    // Check for informational parameter
                    NodeList infParamNodes = toolElement.getElementsByTagName("informationalParameter");
                    if (infParamNodes.getLength() > 0) {
                        infParamName = infParamNodes.item(0).getTextContent();
                    }

                    // Check for multiplicity parameter
                    NodeList kParamNodes = toolElement.getElementsByTagName("multiplicityParameter");
                    if (kParamNodes.getLength() > 0) {
                        kParamName = kParamNodes.item(0).getTextContent();
                    }
                }
            }

            // Standard PNML inhibitor-arc marker: <type value="inhibitorArc"/>
            NodeList typeNodes = arcElement.getElementsByTagName("type");
            for (int j = 0; j < typeNodes.getLength(); j++) {
                String typeValue = ((Element) typeNodes.item(j)).getAttribute("value");
                if (typeValue.toLowerCase().contains("inhibitor")) {
                    isInformational = true;
                }
            }

            // Determine if it's an input or output arc
            if (placeIdToNumber.containsKey(source) && transitionIdToNumber.containsKey(target)) {
                // Place to Transition - Input Arc
                int placeNum = placeIdToNumber.get(source);
                int transitionNum = transitionIdToNumber.get(target);
                ArcIn arcIn = new ArcIn(arcId, placeNum, transitionNum, weight);
                arcIn.setNameP(source);
                arcIn.setNameT(target);

                // Set informational flag
                arcIn.setInf(isInformational);

                // Set informational parameter if present
                if (XmlHelper.isNotEmpty(infParamName)) {
                    arcIn.setInfParam(infParamName);
                }

                // Set multiplicity parameter if present
                if (XmlHelper.isNotEmpty(kParamName)) {
                    arcIn.setKParam(kParamName);
                }

                arcIns.add(arcIn);
            } else if (transitionIdToNumber.containsKey(source) && placeIdToNumber.containsKey(target)) {
                // Transition to Place - Output Arc
                int transitionNum = transitionIdToNumber.get(source);
                int placeNum = placeIdToNumber.get(target);
                ArcOut arcOut = new ArcOut(arcId, transitionNum, placeNum, weight);
                arcOut.setNameT(source);
                arcOut.setNameP(target);

                // Set multiplicity parameter if present
                if (XmlHelper.isNotEmpty(kParamName)) {
                    arcOut.setKParam(kParamName);
                }

                arcOuts.add(arcOut);
            } else {
                // Dropping the arc keeps the rest of the net readable, but doing it silently
                // turns a broken document into a net that runs and quietly means something
                // else, which is the worst outcome available here.
                log.warn("Dropping arc {}: endpoints {} -> {} are not both on this page",
                        arcId, source, target);
            }
        }
    }

    /**
     * Parse coordinates from toolspecific element for a place
     */
    private void parseCoordinatesForPlace(Element placeElement, int placeNumber) {
        boolean coordinatesFound = false;

        // First try to parse from tool-specific coordinates (preferred)
        NodeList toolspecificNodes = placeElement.getElementsByTagName(PnmlConstants.ELEMENT_TOOLSPECIFIC);
        for (int i = 0; i < toolspecificNodes.getLength() && !coordinatesFound; i++) {
            Element toolElement = (Element) toolspecificNodes.item(i);
            if (PnmlConstants.TOOL_PETRI_OBJ_MODEL.equals(toolElement.getAttribute(PnmlConstants.ATTR_TOOL))) {
                NodeList coordinatesNodes = toolElement.getElementsByTagName(PnmlConstants.ELEMENT_COORDINATES);
                if (coordinatesNodes.getLength() > 0) {
                    Element coordElement = (Element) coordinatesNodes.item(0);
                    try {
                        double x = Double.parseDouble(coordElement.getAttribute(PnmlConstants.ATTR_X));
                        double y = Double.parseDouble(coordElement.getAttribute(PnmlConstants.ATTR_Y));
                        placeCoordinates.put(placeNumber, new java.awt.geom.Point2D.Double(x, y));
                        coordinatesFound = true;
                    } catch (NumberFormatException e) {
                        // Ignore invalid coordinates
                    }
                }
            }
        }

        // If no tool-specific coordinates found, try standard graphics coordinates
        if (!coordinatesFound) {
            NodeList graphicsNodes = placeElement.getElementsByTagName(PnmlConstants.ELEMENT_GRAPHICS);
            // Find graphics element with position child (not offset)
            for (int i = 0; i < graphicsNodes.getLength() && !coordinatesFound; i++) {
                Element graphicsElement = (Element) graphicsNodes.item(i);
                NodeList positionNodes = graphicsElement.getElementsByTagName(PnmlConstants.ELEMENT_POSITION);
                if (positionNodes.getLength() > 0) {
                    Element positionElement = (Element) positionNodes.item(0);
                    try {
                        double x = Double.parseDouble(positionElement.getAttribute(PnmlConstants.ATTR_X));
                        double y = Double.parseDouble(positionElement.getAttribute(PnmlConstants.ATTR_Y));
                        if (x != 0.0 || y != 0.0) {
                            placeCoordinates.put(placeNumber, new java.awt.geom.Point2D.Double(x, y));
                            coordinatesFound = true;
                        }
                    } catch (NumberFormatException e) {
                        // Ignore invalid coordinates
                    }
                }
            }
        }
    }

    /**
     * Parse coordinates from toolspecific element for a transition
     */
    private void parseCoordinatesForTransition(Element transitionElement, int transitionNumber) {
        boolean coordinatesFound = false;

        // First try to parse from tool-specific coordinates (preferred)
        NodeList toolspecificNodes = transitionElement.getElementsByTagName(PnmlConstants.ELEMENT_TOOLSPECIFIC);
        for (int i = 0; i < toolspecificNodes.getLength() && !coordinatesFound; i++) {
            Element toolElement = (Element) toolspecificNodes.item(i);
            if (PnmlConstants.TOOL_PETRI_OBJ_MODEL.equals(toolElement.getAttribute(PnmlConstants.ATTR_TOOL))) {
                NodeList coordinatesNodes = toolElement.getElementsByTagName(PnmlConstants.ELEMENT_COORDINATES);
                if (coordinatesNodes.getLength() > 0) {
                    Element coordElement = (Element) coordinatesNodes.item(0);
                    try {
                        double x = Double.parseDouble(coordElement.getAttribute(PnmlConstants.ATTR_X));
                        double y = Double.parseDouble(coordElement.getAttribute(PnmlConstants.ATTR_Y));
                        transitionCoordinates.put(transitionNumber, new java.awt.geom.Point2D.Double(x, y));
                        coordinatesFound = true;
                    } catch (NumberFormatException e) {
                        // Ignore invalid coordinates
                    }
                }
            }
        }

        // If no tool-specific coordinates found, try standard graphics coordinates
        if (!coordinatesFound) {
            NodeList graphicsNodes = transitionElement.getElementsByTagName(PnmlConstants.ELEMENT_GRAPHICS);
            // Find graphics element with position child (not offset)
            for (int i = 0; i < graphicsNodes.getLength() && !coordinatesFound; i++) {
                Element graphicsElement = (Element) graphicsNodes.item(i);
                NodeList positionNodes = graphicsElement.getElementsByTagName(PnmlConstants.ELEMENT_POSITION);
                if (positionNodes.getLength() > 0) {
                    Element positionElement = (Element) positionNodes.item(0);
                    try {
                        double x = Double.parseDouble(positionElement.getAttribute(PnmlConstants.ATTR_X));
                        double y = Double.parseDouble(positionElement.getAttribute(PnmlConstants.ATTR_Y));
                        if (x != 0.0 || y != 0.0) {
                            transitionCoordinates.put(transitionNumber, new java.awt.geom.Point2D.Double(x, y));
                            coordinatesFound = true;
                        }
                    } catch (NumberFormatException e) {
                        // Ignore invalid coordinates
                    }
                }
            }
        }
    }

    /**
     * Get coordinates for a place by its number
     *
     * @param placeNumber the place number
     * @return coordinates as Point2D.Double or null if not found
     */
    public java.awt.geom.Point2D.Double getPlaceCoordinates(int placeNumber) {
        return placeCoordinates.get(placeNumber);
    }

    /**
     * Get coordinates for a transition by its number
     *
     * @param transitionNumber the transition number
     * @return coordinates as Point2D.Double or null if not found
     */
    public java.awt.geom.Point2D.Double getTransitionCoordinates(int transitionNumber) {
        return transitionCoordinates.get(transitionNumber);
    }

    /**
     * Get all place coordinates
     *
     * @return map of place numbers to coordinates
     */
    public Map<Integer, java.awt.geom.Point2D.Double> getAllPlaceCoordinates() {
        return new HashMap<>(placeCoordinates);
    }

    /**
     * Get all transition coordinates
     *
     * @return map of transition numbers to coordinates
     */
    public Map<Integer, java.awt.geom.Point2D.Double> getAllTransitionCoordinates() {
        return new HashMap<>(transitionCoordinates);
    }
}
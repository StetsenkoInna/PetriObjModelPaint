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
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
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
    private final List<String> warnings = new ArrayList<>();

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
        // A parser instance may be reused across documents; getWarnings() promises the
        // warnings of the most recent one, not an accumulation across every call.
        warnings.clear();
        // Before anything else reads an id out of the document, so every id below is already
        // a valid NCName, whether the document supplied one or this fixed it in place.
        warnings.addAll(XmlHelper.sanitizeIds(document));

        Element netElement = findNetElement(document);
        // The whole page subtree, since a page written inside another page is a Petri-object
        // exactly like a sibling one, and counting only the net's direct children would let a
        // nested model through as if it were a single net.
        int pages = XmlHelper.descendantPages(netElement).size();
        if (pages > 1) {
            // Every page is a Petri-object of its own; flattening them would silently merge
            // nets that only a composed model can run.
            throw new Exception(String.format(PnmlConstants.ERROR_OBJECT_MODEL_NOT_SUPPORTED, pages));
        }

        // The net's own display name, the same preference order PnmlModelParser already
        // gives a model's name: <name><text> first, the id only when there is no name at all.
        String netName = XmlHelper.getDirectTextContent(netElement, PnmlConstants.ELEMENT_NAME);
        if (!XmlHelper.isNotEmpty(netName)) {
            netName = netElement.getAttribute(PnmlConstants.ATTR_ID);
        }
        return parseScope(netElement, netName);
    }

    /**
     * Warnings collected while reading the most recent document: an id that was not a valid
     * XML id and was imported under a different one, a value that did not parse as the number
     * it named, an arc dropped because its endpoints were not both on this page. Parsing
     * continues past all of them; this is what lets a caller show them to a user afterwards
     * instead.
     *
     * @return the warnings, in document order, empty when the document raised none
     */
    public List<String> getWarnings() {
        return Collections.unmodifiableList(warnings);
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
     * <p>A page written inside the given page is a Petri-object of its own, and nothing it
     * holds is read into this net: the net of a page is what the page itself holds.
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
            for (Element placeElement
                    : XmlHelper.scopedElements(netElement, PnmlConstants.ELEMENT_PLACE)) {
                places.add(parsePlace(placeElement));
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
        int marking = XmlHelper.parseIntSafe(markingText, 0, warnings,
                "place '" + id + "'", PnmlConstants.ELEMENT_INITIAL_MARKING);

        // Parse place parameters from toolspecific section
        String markingParam = null;
        for (Element toolElement : XmlHelper.toolSpecificBlocks(placeElement)) {
            NodeList markingParamNodes = toolElement.getElementsByTagName(PnmlConstants.ELEMENT_INITIAL_MARKING_PARAMETER);
            if (markingParamNodes.getLength() > 0) {
                markingParam = markingParamNodes.item(0).getTextContent();
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
                XmlHelper.getToolSpecificText(reference, PnmlConstants.ELEMENT_FUSED_INITIAL_MARKING), 0,
                warnings, "reference place '" + id + "'", PnmlConstants.ELEMENT_FUSED_INITIAL_MARKING);

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

        for (Element transitionElement
                : XmlHelper.scopedElements(netElement, PnmlConstants.ELEMENT_TRANSITION)) {
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
            String description = "transition '" + id + "'";

            for (Element toolElement : XmlHelper.toolSpecificBlocks(transitionElement)) {
                // Parse time delay or its parameter
                NodeList delayNodes = toolElement.getElementsByTagName("timeDelay");
                if (delayNodes.getLength() > 0) {
                    timeDelay = XmlHelper.parseDoubleSafe(delayNodes.item(0).getTextContent(), 0.0,
                            warnings, description, "timeDelay");
                }
                NodeList delayParamNodes = toolElement.getElementsByTagName("timeDelayParameter");
                if (delayParamNodes.getLength() > 0) {
                    timeDelayParam = delayParamNodes.item(0).getTextContent();
                }

                // Parse delay mean value
                NodeList delayMeanNodes = toolElement.getElementsByTagName("delayMeanValue");
                if (delayMeanNodes.getLength() > 0) {
                    delayMeanValue = XmlHelper.parseDoubleSafe(delayMeanNodes.item(0).getTextContent(), 0.0,
                            warnings, description, "delayMeanValue");
                }

                // Parse standard deviation
                NodeList stdDeviationNodes = toolElement.getElementsByTagName("standardDeviation");
                if (stdDeviationNodes.getLength() > 0) {
                    standardDeviation = XmlHelper.parseDoubleSafe(stdDeviationNodes.item(0).getTextContent(), 0.0,
                            warnings, description, "standardDeviation");
                }

                // Parse priority or its parameter
                NodeList priorityNodes = toolElement.getElementsByTagName("priority");
                if (priorityNodes.getLength() > 0) {
                    priority = XmlHelper.parseIntSafe(priorityNodes.item(0).getTextContent(), 0,
                            warnings, description, "priority");
                }
                NodeList priorityParamNodes = toolElement.getElementsByTagName("priorityParameter");
                if (priorityParamNodes.getLength() > 0) {
                    priorityParam = priorityParamNodes.item(0).getTextContent();
                }

                // Parse probability or its parameter
                NodeList probabilityNodes = toolElement.getElementsByTagName("probability");
                if (probabilityNodes.getLength() > 0) {
                    probability = XmlHelper.parseDoubleSafe(probabilityNodes.item(0).getTextContent(), 1.0,
                            warnings, description, "probability");
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
        for (Element arcElement : XmlHelper.scopedElements(netElement, PnmlConstants.ELEMENT_ARC)) {
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
            int weight = XmlHelper.parseIntSafe(weightText, 1, warnings,
                    "arc '" + arcId + "'", PnmlConstants.ELEMENT_INSCRIPTION);

            // Parse toolspecific information for informational arcs and parameters
            boolean isInformational = false;
            String infParamName = null;
            String kParamName = null;

            for (Element toolElement : XmlHelper.toolSpecificBlocks(arcElement)) {
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
                warnings.add(String.format(PnmlConstants.WARNING_CROSS_PAGE_ARC_DROPPED,
                        arcId, source, target));
            }
        }
    }

    /**
     * Reads a place's or transition's position: the standard {@code <graphics><position>}
     * first, which is what a foreign tool that only understands the ISO vocabulary can have
     * written or edited, and the tool-specific {@code <coordinates>} only when the standard
     * graphics carry none at all, or are malformed. A position of (0,0) is accepted at face
     * value: only the complete absence of both is "no coordinates", which is what the fallback
     * grid layout in {@code GraphNetBuilder} is for.
     *
     * <p>A {@code <position>} whose x or y does not parse as a number is not defaulted to 0.0
     * for the bad half: that would commit to the standard source with a fabricated coordinate
     * and never give the tool-specific {@code <coordinates>} a chance to supply the real one.
     * It is instead treated as though the whole {@code <position>} were absent, after warning
     * about it, so parsing falls through to the coordinates fallback exactly as it would for a
     * standard graphics block that never stated a position at all.
     *
     * @return the position, or {@code null} when the element states neither
     */
    private java.awt.geom.Point2D.Double parseNodePosition(Element element, String description) {
        Element graphics = XmlHelper.firstDirectChild(element, PnmlConstants.ELEMENT_GRAPHICS);
        Element position = graphics == null
                ? null : XmlHelper.firstDirectChild(graphics, PnmlConstants.ELEMENT_POSITION);
        if (position != null) {
            java.awt.geom.Point2D.Double standard = parseStandardPosition(position, description);
            if (standard != null) {
                return standard;
            }
            // Falls through: a malformed standard position does not commit to (0,0).
        }
        for (Element toolElement : XmlHelper.toolSpecificBlocks(element)) {
            Element coordinates = XmlHelper.firstDirectChild(toolElement, PnmlConstants.ELEMENT_COORDINATES);
            if (coordinates != null) {
                return parsePoint(coordinates, description, PnmlConstants.ELEMENT_COORDINATES);
            }
        }
        return null;
    }

    /**
     * Parses the standard {@code <position>} strictly: a malformed x or y is warned about and
     * the position is reported as absent rather than defaulted, which is what lets {@link
     * #parseNodePosition} fall through to the tool-specific coordinates instead of shadowing
     * them with a fabricated (0,0)-or-half-fabricated point.
     *
     * @return the position, or {@code null} when either coordinate fails to parse
     */
    private java.awt.geom.Point2D.Double parseStandardPosition(Element position, String description) {
        String xText = position.getAttribute(PnmlConstants.ATTR_X);
        String yText = position.getAttribute(PnmlConstants.ATTR_Y);
        boolean malformed = false;
        if (!isParsableOrAbsent(xText)) {
            warnings.add(String.format(PnmlConstants.WARNING_MALFORMED_POSITION,
                    description, PnmlConstants.ELEMENT_POSITION + " x", xText.trim()));
            malformed = true;
        }
        if (!isParsableOrAbsent(yText)) {
            warnings.add(String.format(PnmlConstants.WARNING_MALFORMED_POSITION,
                    description, PnmlConstants.ELEMENT_POSITION + " y", yText.trim()));
            malformed = true;
        }
        return malformed ? null : parsePoint(position, description, PnmlConstants.ELEMENT_POSITION);
    }

    /** @return whether {@code text} is either absent (default applies) or a parsable number */
    private static boolean isParsableOrAbsent(String text) {
        if (text == null || text.trim().isEmpty()) {
            return true;
        }
        try {
            Double.parseDouble(text.trim());
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private java.awt.geom.Point2D.Double parsePoint(Element element, String description, String field) {
        double x = XmlHelper.parseDoubleSafe(element.getAttribute(PnmlConstants.ATTR_X), 0.0,
                warnings, description, field + " x");
        double y = XmlHelper.parseDoubleSafe(element.getAttribute(PnmlConstants.ATTR_Y), 0.0,
                warnings, description, field + " y");
        return new java.awt.geom.Point2D.Double(x, y);
    }

    /**
     * Parse coordinates for a place
     */
    private void parseCoordinatesForPlace(Element placeElement, int placeNumber) {
        java.awt.geom.Point2D.Double position = parseNodePosition(placeElement,
                "place '" + placeElement.getAttribute(PnmlConstants.ATTR_ID) + "'");
        if (position != null) {
            placeCoordinates.put(placeNumber, position);
        }
    }

    /**
     * Parse coordinates for a transition
     */
    private void parseCoordinatesForTransition(Element transitionElement, int transitionNumber) {
        java.awt.geom.Point2D.Double position = parseNodePosition(transitionElement,
                "transition '" + transitionElement.getAttribute(PnmlConstants.ATTR_ID) + "'");
        if (position != null) {
            transitionCoordinates.put(transitionNumber, position);
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
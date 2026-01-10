package ua.stetsenkoinna.pnml;

import org.w3c.dom.*;
import ua.stetsenkoinna.PetriObj.*;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

/**
 * Parser for PNML (Petri Net Markup Language) format according to ISO/IEC 15909
 *
 * @author Serhii Rybak
 */
public class PnmlParser {

    private final Map<String, Integer> placeIdToNumber = new HashMap<>();
    private final Map<String, Integer> transitionIdToNumber = new HashMap<>();
    private final Map<Integer, java.awt.geom.Point2D.Double> placeCoordinates = new HashMap<>();
    private final Map<Integer, java.awt.geom.Point2D.Double> transitionCoordinates = new HashMap<>();

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
        Document document = builder.parse(file);

        // Get the root element
        Element root = document.getDocumentElement();
        if (!PnmlConstants.ELEMENT_PNML.equals(root.getTagName())) {
            throw new Exception(PnmlConstants.ERROR_INVALID_ROOT);
        }

        // Get the net element
        NodeList netNodes = root.getElementsByTagName(PnmlConstants.ELEMENT_NET);
        if (netNodes.getLength() == 0) {
            throw new Exception(PnmlConstants.ERROR_NO_NET);
        }

        Element netElement = (Element) netNodes.item(0);
        String netId = netElement.getAttribute(PnmlConstants.ATTR_ID);

        // Parse places, transitions, and arcs
        ArrayList<PetriP> places = parsePlaces(netElement);
        ArrayList<PetriT> transitions = parseTransitions(netElement);
        ArrayList<ArcIn> arcIns = new ArrayList<>();
        ArrayList<ArcOut> arcOuts = new ArrayList<>();
        parseArcs(netElement, arcIns, arcOuts);

        return new PetriNet(netId, places, transitions, arcIns, arcOuts);
    }

    /**
     * Parse places from net element
     */
    private ArrayList<PetriP> parsePlaces(Element netElement) {
        ArrayList<PetriP> places = new ArrayList<>();
        NodeList placeNodes = netElement.getElementsByTagName(PnmlConstants.ELEMENT_PLACE);

        for (int i = 0; i < placeNodes.getLength(); i++) {
            Element placeElement = (Element) placeNodes.item(i);
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
                    NodeList markingParamNodes = toolElement.getElementsByTagName("initialMarkingParameter");
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

            places.add(place);
            placeIdToNumber.put(id, place.getNumber());

            // Parse coordinates from toolspecific elements
            parseCoordinatesForPlace(placeElement, place.getNumber());
        }

        return places;
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
    private void parseArcs(Element netElement, ArrayList<ArcIn> arcIns, ArrayList<ArcOut> arcOuts) {
        NodeList arcNodes = netElement.getElementsByTagName(PnmlConstants.ELEMENT_ARC);

        for (int i = 0; i < arcNodes.getLength(); i++) {
            Element arcElement = (Element) arcNodes.item(i);
            String arcId = arcElement.getAttribute(PnmlConstants.ATTR_ID);
            String source = arcElement.getAttribute(PnmlConstants.ATTR_SOURCE);
            String target = arcElement.getAttribute(PnmlConstants.ATTR_TARGET);

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
                    NodeList infNodes = toolElement.getElementsByTagName("informational");
                    if (infNodes.getLength() > 0) {
                        isInformational = "true".equals(infNodes.item(0).getTextContent());
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
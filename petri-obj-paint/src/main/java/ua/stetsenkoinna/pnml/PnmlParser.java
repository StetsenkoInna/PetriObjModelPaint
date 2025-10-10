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

    private Map<String, Integer> placeIdToNumber = new HashMap<>();
    private Map<String, Integer> transitionIdToNumber = new HashMap<>();
    private Map<Integer, java.awt.geom.Point2D.Double> placeCoordinates = new HashMap<>();
    private Map<Integer, java.awt.geom.Point2D.Double> transitionCoordinates = new HashMap<>();

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
        if (!"pnml".equals(root.getTagName())) {
            throw new Exception("Invalid PNML file: root element must be 'pnml'");
        }

        // Get the net element
        NodeList netNodes = root.getElementsByTagName("net");
        if (netNodes.getLength() == 0) {
            throw new Exception("No net element found in PNML file");
        }

        Element netElement = (Element) netNodes.item(0);
        String netId = netElement.getAttribute("id");
        String netType = netElement.getAttribute("type");

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
        NodeList placeNodes = netElement.getElementsByTagName("place");

        for (int i = 0; i < placeNodes.getLength(); i++) {
            Element placeElement = (Element) placeNodes.item(i);
            String id = placeElement.getAttribute("id");

            // Get place name
            String name = id; // default to ID
            NodeList nameNodes = placeElement.getElementsByTagName("name");
            if (nameNodes.getLength() > 0) {
                Element nameElement = (Element) nameNodes.item(0);
                NodeList textNodes = nameElement.getElementsByTagName("text");
                if (textNodes.getLength() > 0) {
                    name = textNodes.item(0).getTextContent();
                }
            }

            // Get initial marking
            int marking = 0;
            NodeList markingNodes = placeElement.getElementsByTagName("initialMarking");
            if (markingNodes.getLength() > 0) {
                Element markingElement = (Element) markingNodes.item(0);
                NodeList textNodes = markingElement.getElementsByTagName("text");
                if (textNodes.getLength() > 0) {
                    String markingText = textNodes.item(0).getTextContent();
                    try {
                        marking = Integer.parseInt(markingText);
                    } catch (NumberFormatException e) {
                        marking = 0;
                    }
                }
            }

            // Parse place parameters from toolspecific section
            String markingParam = null;
            NodeList toolspecificNodes = placeElement.getElementsByTagName("toolspecific");
            for (int j = 0; j < toolspecificNodes.getLength(); j++) {
                Element toolElement = (Element) toolspecificNodes.item(j);
                if ("PetriObjModel".equals(toolElement.getAttribute("tool"))) {
                    NodeList markingParamNodes = toolElement.getElementsByTagName("initialMarkingParameter");
                    if (markingParamNodes.getLength() > 0) {
                        markingParam = markingParamNodes.item(0).getTextContent();
                    }
                }
            }

            PetriP place = new PetriP(id, name, marking);

            // Set marking parameter if present
            if (markingParam != null && !markingParam.trim().isEmpty()) {
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
        NodeList transitionNodes = netElement.getElementsByTagName("transition");

        for (int i = 0; i < transitionNodes.getLength(); i++) {
            Element transitionElement = (Element) transitionNodes.item(i);
            String id = transitionElement.getAttribute("id");

            // Get transition name
            String name = id; // default to ID
            NodeList nameNodes = transitionElement.getElementsByTagName("name");
            if (nameNodes.getLength() > 0) {
                Element nameElement = (Element) nameNodes.item(0);
                NodeList textNodes = nameElement.getElementsByTagName("text");
                if (textNodes.getLength() > 0) {
                    name = textNodes.item(0).getTextContent();
                }
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

            NodeList toolspecificNodes = transitionElement.getElementsByTagName("toolspecific");
            for (int j = 0; j < toolspecificNodes.getLength(); j++) {
                Element toolElement = (Element) toolspecificNodes.item(j);
                if ("PetriObjModel".equals(toolElement.getAttribute("tool"))) {
                    // Parse time delay or its parameter
                    NodeList delayNodes = toolElement.getElementsByTagName("timeDelay");
                    if (delayNodes.getLength() > 0) {
                        try {
                            timeDelay = Double.parseDouble(delayNodes.item(0).getTextContent());
                        } catch (NumberFormatException e) {
                            timeDelay = 0.0;
                        }
                    }
                    NodeList delayParamNodes = toolElement.getElementsByTagName("timeDelayParameter");
                    if (delayParamNodes.getLength() > 0) {
                        timeDelayParam = delayParamNodes.item(0).getTextContent();
                    }

                    // Parse delay mean value
                    NodeList delayMeanNodes = toolElement.getElementsByTagName("delayMeanValue");
                    if (delayMeanNodes.getLength() > 0) {
                        try {
                            delayMeanValue = Double.parseDouble(delayMeanNodes.item(0).getTextContent());
                        } catch (NumberFormatException e) {
                            delayMeanValue = 0.0;
                        }
                    }

                    // Parse standard deviation
                    NodeList stdDeviationNodes = toolElement.getElementsByTagName("standardDeviation");
                    if (stdDeviationNodes.getLength() > 0) {
                        try {
                            standardDeviation = Double.parseDouble(stdDeviationNodes.item(0).getTextContent());
                        } catch (NumberFormatException e) {
                            standardDeviation = 0.0;
                        }
                    }

                    // Parse priority or its parameter
                    NodeList priorityNodes = toolElement.getElementsByTagName("priority");
                    if (priorityNodes.getLength() > 0) {
                        try {
                            priority = Integer.parseInt(priorityNodes.item(0).getTextContent());
                        } catch (NumberFormatException e) {
                            priority = 0;
                        }
                    }
                    NodeList priorityParamNodes = toolElement.getElementsByTagName("priorityParameter");
                    if (priorityParamNodes.getLength() > 0) {
                        priorityParam = priorityParamNodes.item(0).getTextContent();
                    }

                    // Parse probability or its parameter
                    NodeList probabilityNodes = toolElement.getElementsByTagName("probability");
                    if (probabilityNodes.getLength() > 0) {
                        try {
                            probability = Double.parseDouble(probabilityNodes.item(0).getTextContent());
                        } catch (NumberFormatException e) {
                            probability = 1.0;
                        }
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
            if (timeDelayParam != null && !timeDelayParam.trim().isEmpty()) {
                transition.setParametrParam(timeDelayParam);
            }
            if (priorityParam != null && !priorityParam.trim().isEmpty()) {
                transition.setPriorityParam(priorityParam);
            }
            if (probabilityParam != null && !probabilityParam.trim().isEmpty()) {
                transition.setProbabilityParam(probabilityParam);
            }
            if (distributionParam != null && !distributionParam.trim().isEmpty()) {
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
        NodeList arcNodes = netElement.getElementsByTagName("arc");

        for (int i = 0; i < arcNodes.getLength(); i++) {
            Element arcElement = (Element) arcNodes.item(i);
            String id = arcElement.getAttribute("id");
            String source = arcElement.getAttribute("source");
            String target = arcElement.getAttribute("target");

            // Get arc weight
            int weight = 1; // default weight
            NodeList inscriptionNodes = arcElement.getElementsByTagName("inscription");
            if (inscriptionNodes.getLength() > 0) {
                Element inscriptionElement = (Element) inscriptionNodes.item(0);
                NodeList textNodes = inscriptionElement.getElementsByTagName("text");
                if (textNodes.getLength() > 0) {
                    try {
                        weight = Integer.parseInt(textNodes.item(0).getTextContent());
                    } catch (NumberFormatException e) {
                        weight = 1;
                    }
                }
            }

            // Parse toolspecific information for informational arcs and parameters
            boolean isInformational = false;
            String infParamName = null;
            String kParamName = null;

            NodeList toolspecificNodes = arcElement.getElementsByTagName("toolspecific");
            for (int j = 0; j < toolspecificNodes.getLength(); j++) {
                Element toolElement = (Element) toolspecificNodes.item(j);
                if ("PetriObjModel".equals(toolElement.getAttribute("tool"))) {
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
                ArcIn arcIn = new ArcIn(placeNum, transitionNum, weight);
                arcIn.setNameP(source);
                arcIn.setNameT(target);

                // Set informational flag
                arcIn.setInf(isInformational);

                // Set informational parameter if present
                if (infParamName != null && !infParamName.trim().isEmpty()) {
                    arcIn.setInfParam(infParamName);
                }

                // Set multiplicity parameter if present
                if (kParamName != null && !kParamName.trim().isEmpty()) {
                    arcIn.setKParam(kParamName);
                }

                arcIns.add(arcIn);
            } else if (transitionIdToNumber.containsKey(source) && placeIdToNumber.containsKey(target)) {
                // Transition to Place - Output Arc
                int transitionNum = transitionIdToNumber.get(source);
                int placeNum = placeIdToNumber.get(target);
                ArcOut arcOut = new ArcOut(transitionNum, placeNum, weight);
                arcOut.setNameT(source);
                arcOut.setNameP(target);

                // Set multiplicity parameter if present
                if (kParamName != null && !kParamName.trim().isEmpty()) {
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
        NodeList toolspecificNodes = placeElement.getElementsByTagName("toolspecific");
        for (int i = 0; i < toolspecificNodes.getLength() && !coordinatesFound; i++) {
            Element toolElement = (Element) toolspecificNodes.item(i);
            if ("PetriObjModel".equals(toolElement.getAttribute("tool"))) {
                NodeList coordinatesNodes = toolElement.getElementsByTagName("coordinates");
                if (coordinatesNodes.getLength() > 0) {
                    Element coordElement = (Element) coordinatesNodes.item(0);
                    try {
                        double x = Double.parseDouble(coordElement.getAttribute("x"));
                        double y = Double.parseDouble(coordElement.getAttribute("y"));
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
            NodeList graphicsNodes = placeElement.getElementsByTagName("graphics");
            if (graphicsNodes.getLength() > 0) {
                Element graphicsElement = (Element) graphicsNodes.item(0);
                NodeList positionNodes = graphicsElement.getElementsByTagName("position");
                if (positionNodes.getLength() > 0) {
                    Element positionElement = (Element) positionNodes.item(0);
                    try {
                        double x = Double.parseDouble(positionElement.getAttribute("x"));
                        double y = Double.parseDouble(positionElement.getAttribute("y"));
                        // Only use if coordinates are not (0,0) which is often a placeholder
                        if (x != 0.0 || y != 0.0) {
                            placeCoordinates.put(placeNumber, new java.awt.geom.Point2D.Double(x, y));
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
        NodeList toolspecificNodes = transitionElement.getElementsByTagName("toolspecific");
        for (int i = 0; i < toolspecificNodes.getLength() && !coordinatesFound; i++) {
            Element toolElement = (Element) toolspecificNodes.item(i);
            if ("PetriObjModel".equals(toolElement.getAttribute("tool"))) {
                NodeList coordinatesNodes = toolElement.getElementsByTagName("coordinates");
                if (coordinatesNodes.getLength() > 0) {
                    Element coordElement = (Element) coordinatesNodes.item(0);
                    try {
                        double x = Double.parseDouble(coordElement.getAttribute("x"));
                        double y = Double.parseDouble(coordElement.getAttribute("y"));
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
            NodeList graphicsNodes = transitionElement.getElementsByTagName("graphics");
            if (graphicsNodes.getLength() > 0) {
                Element graphicsElement = (Element) graphicsNodes.item(0);
                NodeList positionNodes = graphicsElement.getElementsByTagName("position");
                if (positionNodes.getLength() > 0) {
                    Element positionElement = (Element) positionNodes.item(0);
                    try {
                        double x = Double.parseDouble(positionElement.getAttribute("x"));
                        double y = Double.parseDouble(positionElement.getAttribute("y"));
                        // Only use if coordinates are not (0,0) which is often a placeholder
                        if (x != 0.0 || y != 0.0) {
                            transitionCoordinates.put(transitionNumber, new java.awt.geom.Point2D.Double(x, y));
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
package ua.stetsenkoinna.pnml;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import ua.stetsenkoinna.PetriObj.*;
import ua.stetsenkoinna.graphnet.*;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.awt.geom.Point2D;
import java.io.File;
import java.util.HashMap;
import java.util.Map;

/**
 * Generator for PNML (Petri Net Markup Language) format according to ISO/IEC 15909
 *
 * @author Serhii Rybak
 */
public class PnmlGenerator {

    private final Map<Integer, String> placeNumberToId = new HashMap<>();
    private final Map<Integer, String> transitionNumberToId = new HashMap<>();

    private GraphPetriNet graphPetriNet;

    /**
     * Generate PNML file from PetriNet object
     *
     * @param petriNet PetriNet to export
     * @param file     Output file
     * @throws Exception if generation fails
     */
    public void generate(PetriNet petriNet, File file) throws Exception {
        generate(petriNet, file, null);
    }

    /**
     * Generate PNML file from PetriNet object with coordinates from GraphPetriNet
     *
     * @param petriNet      PetriNet to export
     * @param file          Output file
     * @param graphPetriNet GraphPetriNet containing coordinate information (optional)
     * @throws Exception if generation fails
     */
    public void generate(PetriNet petriNet, File file, GraphPetriNet graphPetriNet) throws Exception {
        this.graphPetriNet = graphPetriNet;
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document document = builder.newDocument();

        // Create root element
        Element pnmlElement = document.createElement(PnmlConstants.ELEMENT_PNML);
        pnmlElement.setAttribute(PnmlConstants.ATTR_XMLNS, PnmlConstants.PNML_NAMESPACE);
        document.appendChild(pnmlElement);

        // Create net element
        Element netElement = document.createElement(PnmlConstants.ELEMENT_NET);
        netElement.setAttribute(PnmlConstants.ATTR_ID,
                XmlHelper.isNotEmpty(petriNet.getName()) ? petriNet.getName() : PnmlConstants.DEFAULT_NET_ID);
        netElement.setAttribute(PnmlConstants.ATTR_TYPE, PnmlConstants.PTNET_TYPE);
        pnmlElement.appendChild(netElement);

        // Add name to net
        if (XmlHelper.isNotEmpty(petriNet.getName())) {
            Element nameElement = createNameElement(document, petriNet.getName());
            netElement.appendChild(nameElement);
        }

        // Create page element for better compatibility with tools like Tina
        Element pageElement = document.createElement(PnmlConstants.ELEMENT_PAGE);
        pageElement.setAttribute(PnmlConstants.ATTR_ID, PnmlConstants.DEFAULT_PAGE_ID);
        netElement.appendChild(pageElement);

        // Generate places
        generatePlaces(document, pageElement, petriNet.getListP());

        // Generate transitions
        generateTransitions(document, pageElement, petriNet.getListT());

        // Generate arcs
        generateArcs(document, pageElement, petriNet.getArcIn(), petriNet.getArcOut());

        // Write to file
        TransformerFactory transformerFactory = TransformerFactory.newInstance();
        Transformer transformer = transformerFactory.newTransformer();
        transformer.setOutputProperty(OutputKeys.INDENT, "yes");
        transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");
        transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");

        DOMSource source = new DOMSource(document);
        StreamResult result = new StreamResult(file);
        transformer.transform(source, result);
    }

    /**
     * Generate places in PNML format
     */
    private void generatePlaces(Document document, Element netElement, PetriP[] places) {
        for (PetriP place : places) {
            String placeId = place.getId() != null ? place.getId() : "p" + place.getNumber();
            placeNumberToId.put(place.getNumber(), placeId);

            Element placeElement = document.createElement(PnmlConstants.ELEMENT_PLACE);
            placeElement.setAttribute(PnmlConstants.ATTR_ID, placeId);
            netElement.appendChild(placeElement);

            // Add name (without offset - let other tools use their own defaults)
            if (XmlHelper.isNotEmpty(place.getName())) {
                placeElement.appendChild(createNameElement(document, place.getName()));
            }

            // Add initial marking
            if (!place.markIsParam() && place.getMark() > 0) {
                Element markingElement = document.createElement(PnmlConstants.ELEMENT_INITIAL_MARKING);
                Element textElement = document.createElement(PnmlConstants.ELEMENT_TEXT);
                textElement.setTextContent(String.valueOf(place.getMark()));
                markingElement.appendChild(textElement);
                placeElement.appendChild(markingElement);
            }

            // Add toolspecific information (coordinates and parameters)
            boolean needsToolspecific = place.markIsParam();
            GraphPetriPlace graphPlace = findGraphPlaceByNumber(place.getNumber());
            if (graphPlace != null) {
                needsToolspecific = true;
            }

            if (needsToolspecific) {
                Element toolspecificElement = document.createElement(PnmlConstants.ELEMENT_TOOLSPECIFIC);
                toolspecificElement.setAttribute(PnmlConstants.ATTR_TOOL, PnmlConstants.TOOL_PETRI_OBJ_MODEL);
                toolspecificElement.setAttribute(PnmlConstants.ATTR_VERSION, PnmlConstants.TOOL_VERSION);

                // Add coordinates if available
                if (graphPlace != null) {
                    Element coordinatesElement = document.createElement(PnmlConstants.ELEMENT_COORDINATES);
                    Point2D center = graphPlace.getGraphElementCenter();
                    coordinatesElement.setAttribute(PnmlConstants.ATTR_X, String.valueOf(center.getX()));
                    coordinatesElement.setAttribute(PnmlConstants.ATTR_Y, String.valueOf(center.getY()));
                    toolspecificElement.appendChild(coordinatesElement);
                }

                // Add marking parameter if present
                if (place.markIsParam() && place.getMarkParamName() != null) {
                    Element markParamElement = document.createElement("initialMarkingParameter");
                    markParamElement.setTextContent(place.getMarkParamName());
                    toolspecificElement.appendChild(markParamElement);
                }

                placeElement.appendChild(toolspecificElement);
            }

            // Add graphics information with real coordinates for PNML compatibility
            Element graphicsElement = document.createElement(PnmlConstants.ELEMENT_GRAPHICS);
            Element positionElement = document.createElement(PnmlConstants.ELEMENT_POSITION);

            // Use coordinates from GraphPetriNet if available, otherwise use defaults
            GraphPetriPlace graphPlaceForGraphics = findGraphPlaceByNumber(place.getNumber());
            if (graphPlaceForGraphics != null) {
                Point2D center = graphPlaceForGraphics.getGraphElementCenter();
                positionElement.setAttribute(PnmlConstants.ATTR_X, String.valueOf((int)center.getX()));
                positionElement.setAttribute(PnmlConstants.ATTR_Y, String.valueOf((int)center.getY()));
            } else {
                positionElement.setAttribute(PnmlConstants.ATTR_X, "0");
                positionElement.setAttribute(PnmlConstants.ATTR_Y, "0");
            }

            graphicsElement.appendChild(positionElement);
            placeElement.appendChild(graphicsElement);
        }
    }

    /**
     * Generate transitions in PNML format
     */
    private void generateTransitions(Document document, Element netElement, PetriT[] transitions) {
        for (PetriT transition : transitions) {
            String transitionId = transition.getId() != null ? transition.getId() : "t" + transition.getNumber();
            transitionNumberToId.put(transition.getNumber(), transitionId);

            Element transitionElement = document.createElement(PnmlConstants.ELEMENT_TRANSITION);
            transitionElement.setAttribute(PnmlConstants.ATTR_ID, transitionId);
            netElement.appendChild(transitionElement);

            // Add name (without offset - let other tools use their own defaults)
            if (XmlHelper.isNotEmpty(transition.getName())) {
                transitionElement.appendChild(createNameElement(document, transition.getName()));
            }

            // Add toolspecific information for extended properties and coordinates
            Element toolspecificElement = document.createElement(PnmlConstants.ELEMENT_TOOLSPECIFIC);
            toolspecificElement.setAttribute(PnmlConstants.ATTR_TOOL, PnmlConstants.TOOL_PETRI_OBJ_MODEL);
            toolspecificElement.setAttribute(PnmlConstants.ATTR_VERSION, PnmlConstants.TOOL_VERSION);

            // Add coordinates from GraphPetriNet if available
            GraphPetriTransition graphTransition = findGraphTransitionByNumber(transition.getNumber());
            if (graphTransition != null) {
                Element coordinatesElement = document.createElement(PnmlConstants.ELEMENT_COORDINATES);
                Point2D center = graphTransition.getGraphElementCenter();
                coordinatesElement.setAttribute(PnmlConstants.ATTR_X, String.valueOf(center.getX()));
                coordinatesElement.setAttribute(PnmlConstants.ATTR_Y, String.valueOf(center.getY()));
                toolspecificElement.appendChild(coordinatesElement);
            }

            // Add time delay or its parameter
            if (transition.parametrIsParam() && transition.getParameterParamName() != null) {
                Element timeDelayParamElement = document.createElement("timeDelayParameter");
                timeDelayParamElement.setTextContent(transition.getParameterParamName());
                toolspecificElement.appendChild(timeDelayParamElement);
            } else if (transition.getParameter() > 0) {
                Element timeDelayElement = document.createElement("timeDelay");
                timeDelayElement.setTextContent(String.valueOf(transition.getParameter()));
                toolspecificElement.appendChild(timeDelayElement);
            }

            // Add delay mean value (always export if > 0)
            if (transition.getParameter() > 0) {
                Element delayMeanElement = document.createElement("delayMeanValue");
                delayMeanElement.setTextContent(String.valueOf(transition.getParameter()));
                toolspecificElement.appendChild(delayMeanElement);
            }

            // Add standard deviation (always export if > 0)
            if (transition.getParamDeviation() > 0) {
                Element stdDeviationElement = document.createElement("standardDeviation");
                stdDeviationElement.setTextContent(String.valueOf(transition.getParamDeviation()));
                toolspecificElement.appendChild(stdDeviationElement);
            }

            // Add priority or its parameter
            if (transition.priorityIsParam() && transition.getPriorityParamName() != null) {
                Element priorityParamElement = document.createElement("priorityParameter");
                priorityParamElement.setTextContent(transition.getPriorityParamName());
                toolspecificElement.appendChild(priorityParamElement);
            } else if (transition.getPriority() != 0) {
                Element priorityElement = document.createElement("priority");
                priorityElement.setTextContent(String.valueOf(transition.getPriority()));
                toolspecificElement.appendChild(priorityElement);
            }

            // Add probability or its parameter
            if (transition.probabilityIsParam() && transition.getProbabilityParamName() != null) {
                Element probabilityParamElement = document.createElement("probabilityParameter");
                probabilityParamElement.setTextContent(transition.getProbabilityParamName());
                toolspecificElement.appendChild(probabilityParamElement);
            } else if (transition.getProbability() != 1.0) {
                Element probabilityElement = document.createElement("probability");
                probabilityElement.setTextContent(String.valueOf(transition.getProbability()));
                toolspecificElement.appendChild(probabilityElement);
            }

            // Add distribution or its parameter
            if (transition.distributionIsParam() && transition.getDistributionParamName() != null) {
                Element distributionParamElement = document.createElement("distributionParameter");
                distributionParamElement.setTextContent(transition.getDistributionParamName());
                toolspecificElement.appendChild(distributionParamElement);
            } else if (transition.getDistribution() != null && !transition.getDistribution().isEmpty()) {
                Element distributionElement = document.createElement("distribution");
                distributionElement.setTextContent(transition.getDistribution());
                toolspecificElement.appendChild(distributionElement);
            }

            if (toolspecificElement.hasChildNodes()) {
                transitionElement.appendChild(toolspecificElement);
            }

            // Add graphics information with real coordinates for PNML compatibility
            Element graphicsElement = document.createElement(PnmlConstants.ELEMENT_GRAPHICS);
            Element positionElement = document.createElement(PnmlConstants.ELEMENT_POSITION);

            // Use coordinates from GraphPetriNet if available, otherwise use defaults
            GraphPetriTransition graphTransitionForGraphics = findGraphTransitionByNumber(transition.getNumber());
            if (graphTransitionForGraphics != null) {
                Point2D center = graphTransitionForGraphics.getGraphElementCenter();
                positionElement.setAttribute(PnmlConstants.ATTR_X, String.valueOf((int)center.getX()));
                positionElement.setAttribute(PnmlConstants.ATTR_Y, String.valueOf((int)center.getY()));
            } else {
                positionElement.setAttribute(PnmlConstants.ATTR_X, "0");
                positionElement.setAttribute(PnmlConstants.ATTR_Y, "0");
            }

            graphicsElement.appendChild(positionElement);
            transitionElement.appendChild(graphicsElement);
        }
    }

    /**
     * Generate arcs in PNML format
     */
    private void generateArcs(Document document, Element netElement, ArcIn[] arcIns, ArcOut[] arcOuts) {
        int arcCounter = 1;

        // Generate input arcs (Place to Transition)
        for (ArcIn arcIn : arcIns) {
            Element arcElement = document.createElement(PnmlConstants.ELEMENT_ARC);
            String arcId = arcIn.getId() != null ? arcIn.getId() : "arc" + arcCounter++;
            arcElement.setAttribute(PnmlConstants.ATTR_ID, arcId);
            arcElement.setAttribute(PnmlConstants.ATTR_SOURCE, placeNumberToId.get(arcIn.getNumP()));
            arcElement.setAttribute(PnmlConstants.ATTR_TARGET, transitionNumberToId.get(arcIn.getNumT()));
            netElement.appendChild(arcElement);

            // Add inscription (weight) - always include even if quantity is 1
            Element inscriptionElement = document.createElement(PnmlConstants.ELEMENT_INSCRIPTION);
            Element textElement = document.createElement(PnmlConstants.ELEMENT_TEXT);
            textElement.setTextContent(String.valueOf(arcIn.getQuantity()));
            inscriptionElement.appendChild(textElement);
            arcElement.appendChild(inscriptionElement);

            // Add toolspecific information for informational arcs and parameters
            boolean needsToolspecific = arcIn.getIsInf() || arcIn.infIsParam() || arcIn.kIsParam();
            if (needsToolspecific) {
                Element toolspecificElement = document.createElement(PnmlConstants.ELEMENT_TOOLSPECIFIC);
                toolspecificElement.setAttribute(PnmlConstants.ATTR_TOOL, PnmlConstants.TOOL_PETRI_OBJ_MODEL);
                toolspecificElement.setAttribute(PnmlConstants.ATTR_VERSION, PnmlConstants.TOOL_VERSION);

                if (arcIn.getIsInf()) {
                    Element infElement = document.createElement("informational");
                    infElement.setTextContent("true");
                    toolspecificElement.appendChild(infElement);
                }

                if (arcIn.infIsParam() && arcIn.getInfParamName() != null) {
                    Element infParamElement = document.createElement("informationalParameter");
                    infParamElement.setTextContent(arcIn.getInfParamName());
                    toolspecificElement.appendChild(infParamElement);
                }

                if (arcIn.kIsParam() && arcIn.getKParamName() != null) {
                    Element kParamElement = document.createElement("multiplicityParameter");
                    kParamElement.setTextContent(arcIn.getKParamName());
                    toolspecificElement.appendChild(kParamElement);
                }

                arcElement.appendChild(toolspecificElement);
            }
        }

        // Generate output arcs (Transition to Place)
        for (ArcOut arcOut : arcOuts) {
            Element arcElement = document.createElement(PnmlConstants.ELEMENT_ARC);
            String arcId = arcOut.getId() != null ? arcOut.getId() : "arc" + arcCounter++;
            arcElement.setAttribute(PnmlConstants.ATTR_ID, arcId);
            arcElement.setAttribute(PnmlConstants.ATTR_SOURCE, transitionNumberToId.get(arcOut.getNumT()));
            arcElement.setAttribute(PnmlConstants.ATTR_TARGET, placeNumberToId.get(arcOut.getNumP()));
            netElement.appendChild(arcElement);

            // Add inscription (weight) - always include even if quantity is 1
            Element inscriptionElement = document.createElement(PnmlConstants.ELEMENT_INSCRIPTION);
            Element textElement = document.createElement(PnmlConstants.ELEMENT_TEXT);
            textElement.setTextContent(String.valueOf(arcOut.getQuantity()));
            inscriptionElement.appendChild(textElement);
            arcElement.appendChild(inscriptionElement);

            // Add toolspecific information for parameters
            if (arcOut.kIsParam() && arcOut.getKParamName() != null) {
                Element toolspecificElement = document.createElement(PnmlConstants.ELEMENT_TOOLSPECIFIC);
                toolspecificElement.setAttribute(PnmlConstants.ATTR_TOOL, PnmlConstants.TOOL_PETRI_OBJ_MODEL);
                toolspecificElement.setAttribute(PnmlConstants.ATTR_VERSION, PnmlConstants.TOOL_VERSION);

                Element kParamElement = document.createElement("multiplicityParameter");
                kParamElement.setTextContent(arcOut.getKParamName());
                toolspecificElement.appendChild(kParamElement);

                arcElement.appendChild(toolspecificElement);
            }
        }
    }

    /**
     * Creates name element with text content
     */
    private Element createNameElement(Document document, String name) {
        Element nameElement = document.createElement(PnmlConstants.ELEMENT_NAME);
        Element textElement = document.createElement(PnmlConstants.ELEMENT_TEXT);
        textElement.setTextContent(name);
        nameElement.appendChild(textElement);
        return nameElement;
    }

    /**
     * Find GraphPetriPlace by its number
     */
    private GraphPetriPlace findGraphPlaceByNumber(int number) {
        if (graphPetriNet == null) {
            return null;
        }
        for (GraphPetriPlace place : graphPetriNet.getGraphPetriPlaceList()) {
            if (place.getPetriPlace().getNumber() == number) {
                return place;
            }
        }
        return null;
    }

    /**
     * Find GraphPetriTransition by its number
     */
    private GraphPetriTransition findGraphTransitionByNumber(int number) {
        if (graphPetriNet == null) {
            return null;
        }
        for (GraphPetriTransition transition : graphPetriNet.getGraphPetriTransitionList()) {
            if (transition.getPetriTransition().getNumber() == number) {
                return transition;
            }
        }
        return null;
    }
}
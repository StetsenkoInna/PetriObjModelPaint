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
        Element pnmlElement = document.createElement("pnml");
        pnmlElement.setAttribute("xmlns", "http://www.pnml.org/version-2009/grammar/pnml");
        document.appendChild(pnmlElement);

        // Create net element
        Element netElement = document.createElement("net");
        netElement.setAttribute("id", petriNet.getName() != null ? petriNet.getName() : "net1");
        netElement.setAttribute("type", "http://www.pnml.org/version-2009/grammar/ptnet");
        pnmlElement.appendChild(netElement);

        // Add name to net
        if (petriNet.getName() != null && !petriNet.getName().isEmpty()) {
            Element nameElement = document.createElement("name");
            Element textElement = document.createElement("text");
            textElement.setTextContent(petriNet.getName());
            nameElement.appendChild(textElement);
            netElement.appendChild(nameElement);
        }

        // Create page element for better compatibility with tools like Tina
        Element pageElement = document.createElement("page");
        pageElement.setAttribute("id", "page1");
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

            Element placeElement = document.createElement("place");
            placeElement.setAttribute("id", placeId);
            netElement.appendChild(placeElement);

            // Add name (without offset - let other tools use their own defaults)
            if (place.getName() != null && !place.getName().isEmpty()) {
                Element nameElement = document.createElement("name");
                Element textElement = document.createElement("text");
                textElement.setTextContent(place.getName());
                nameElement.appendChild(textElement);
                placeElement.appendChild(nameElement);
            }

            // Add initial marking
            if (!place.markIsParam() && place.getMark() > 0) {
                Element markingElement = document.createElement("initialMarking");
                Element textElement = document.createElement("text");
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
                Element toolspecificElement = document.createElement("toolspecific");
                toolspecificElement.setAttribute("tool", "PetriObjModel");
                toolspecificElement.setAttribute("version", "1.0");

                // Add coordinates if available
                if (graphPlace != null) {
                    Element coordinatesElement = document.createElement("coordinates");
                    Point2D center = graphPlace.getGraphElementCenter();
                    coordinatesElement.setAttribute("x", String.valueOf(center.getX()));
                    coordinatesElement.setAttribute("y", String.valueOf(center.getY()));
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
            Element graphicsElement = document.createElement("graphics");
            Element positionElement = document.createElement("position");

            // Use coordinates from GraphPetriNet if available, otherwise use defaults
            GraphPetriPlace graphPlaceForGraphics = findGraphPlaceByNumber(place.getNumber());
            if (graphPlaceForGraphics != null) {
                Point2D center = graphPlaceForGraphics.getGraphElementCenter();
                positionElement.setAttribute("x", String.valueOf((int)center.getX()));
                positionElement.setAttribute("y", String.valueOf((int)center.getY()));
            } else {
                positionElement.setAttribute("x", "0");
                positionElement.setAttribute("y", "0");
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

            Element transitionElement = document.createElement("transition");
            transitionElement.setAttribute("id", transitionId);
            netElement.appendChild(transitionElement);

            // Add name (without offset - let other tools use their own defaults)
            if (transition.getName() != null && !transition.getName().isEmpty()) {
                Element nameElement = document.createElement("name");
                Element textElement = document.createElement("text");
                textElement.setTextContent(transition.getName());
                nameElement.appendChild(textElement);
                transitionElement.appendChild(nameElement);
            }

            // Add toolspecific information for extended properties and coordinates
            Element toolspecificElement = document.createElement("toolspecific");
            toolspecificElement.setAttribute("tool", "PetriObjModel");
            toolspecificElement.setAttribute("version", "1.0");

            // Add coordinates from GraphPetriNet if available
            GraphPetriTransition graphTransition = findGraphTransitionByNumber(transition.getNumber());
            if (graphTransition != null) {
                Element coordinatesElement = document.createElement("coordinates");
                Point2D center = graphTransition.getGraphElementCenter();
                coordinatesElement.setAttribute("x", String.valueOf(center.getX()));
                coordinatesElement.setAttribute("y", String.valueOf(center.getY()));
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
            Element graphicsElement = document.createElement("graphics");
            Element positionElement = document.createElement("position");

            // Use coordinates from GraphPetriNet if available, otherwise use defaults
            GraphPetriTransition graphTransitionForGraphics = findGraphTransitionByNumber(transition.getNumber());
            if (graphTransitionForGraphics != null) {
                Point2D center = graphTransitionForGraphics.getGraphElementCenter();
                positionElement.setAttribute("x", String.valueOf((int)center.getX()));
                positionElement.setAttribute("y", String.valueOf((int)center.getY()));
            } else {
                positionElement.setAttribute("x", "0");
                positionElement.setAttribute("y", "0");
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
            Element arcElement = document.createElement("arc");
            arcElement.setAttribute("id", "arc" + arcCounter++);
            arcElement.setAttribute("source", placeNumberToId.get(arcIn.getNumP()));
            arcElement.setAttribute("target", transitionNumberToId.get(arcIn.getNumT()));
            netElement.appendChild(arcElement);

            // Add inscription (weight)
            if (arcIn.getQuantity() != 1) {
                Element inscriptionElement = document.createElement("inscription");
                Element textElement = document.createElement("text");
                textElement.setTextContent(String.valueOf(arcIn.getQuantity()));
                inscriptionElement.appendChild(textElement);
                arcElement.appendChild(inscriptionElement);
            }

            // Add toolspecific information for informational arcs and parameters
            boolean needsToolspecific = arcIn.getIsInf() || arcIn.infIsParam() || arcIn.kIsParam();
            if (needsToolspecific) {
                Element toolspecificElement = document.createElement("toolspecific");
                toolspecificElement.setAttribute("tool", "PetriObjModel");
                toolspecificElement.setAttribute("version", "1.0");

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
            Element arcElement = document.createElement("arc");
            arcElement.setAttribute("id", "arc" + arcCounter++);
            arcElement.setAttribute("source", transitionNumberToId.get(arcOut.getNumT()));
            arcElement.setAttribute("target", placeNumberToId.get(arcOut.getNumP()));
            netElement.appendChild(arcElement);

            // Add inscription (weight)
            if (arcOut.getQuantity() != 1) {
                Element inscriptionElement = document.createElement("inscription");
                Element textElement = document.createElement("text");
                textElement.setTextContent(String.valueOf(arcOut.getQuantity()));
                inscriptionElement.appendChild(textElement);
                arcElement.appendChild(inscriptionElement);
            }

            // Add toolspecific information for parameters
            if (arcOut.kIsParam() && arcOut.getKParamName() != null) {
                Element toolspecificElement = document.createElement("toolspecific");
                toolspecificElement.setAttribute("tool", "PetriObjModel");
                toolspecificElement.setAttribute("version", "1.0");

                Element kParamElement = document.createElement("multiplicityParameter");
                kParamElement.setTextContent(arcOut.getKParamName());
                toolspecificElement.appendChild(kParamElement);

                arcElement.appendChild(toolspecificElement);
            }
        }
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
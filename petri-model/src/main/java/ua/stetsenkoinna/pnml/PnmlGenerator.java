package ua.stetsenkoinna.pnml;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import ua.stetsenkoinna.petriobj.*;
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
import java.io.StringWriter;
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
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document document = builder.newDocument();

        // Create root element. Every element below is created with createElementNS in this
        // same namespace, so the root carrying it is enough: the serializer states it exactly
        // once, on <pnml> itself, with nothing to override further down.
        Element pnmlElement = document.createElementNS(PnmlConstants.PNML_NAMESPACE, PnmlConstants.ELEMENT_PNML);
        document.appendChild(pnmlElement);

        // Create net element
        Element netElement = document.createElementNS(PnmlConstants.PNML_NAMESPACE, PnmlConstants.ELEMENT_NET);
        netElement.setAttribute(PnmlConstants.ATTR_ID, XmlHelper.isNotEmpty(petriNet.getName())
                ? PnmlIds.sanitize(petriNet.getName()) : PnmlConstants.DEFAULT_NET_ID);
        netElement.setAttribute(PnmlConstants.ATTR_TYPE, PnmlConstants.PTNET_TYPE);
        pnmlElement.appendChild(netElement);

        // Add name to net
        if (XmlHelper.isNotEmpty(petriNet.getName())) {
            Element nameElement = createNameElement(document, petriNet.getName());
            netElement.appendChild(nameElement);
        }

        // Create page element for better compatibility with tools like Tina
        Element pageElement = document.createElementNS(PnmlConstants.PNML_NAMESPACE, PnmlConstants.ELEMENT_PAGE);
        pageElement.setAttribute(PnmlConstants.ATTR_ID, PnmlConstants.DEFAULT_PAGE_ID);
        netElement.appendChild(pageElement);

        writeNetInto(document, pageElement, petriNet, graphPetriNet);

        // The same invariants the composed writer holds itself to, on the same shared check:
        // ids sanitized where they can be, and only genuinely broken structure refused.
        WriterInvariants.assertValid(document);

        writeDocument(document, file);
    }

    /**
     * Writes the places, transitions and arcs of one net below the given element.
     *
     * <p>A generator instance keeps the id maps that connect arcs to their endpoints, so a
     * caller writing several nets into one document — one page per Petri-object — needs a
     * fresh generator per net.
     *
     * @param document the document being built
     * @param parent element to append the net's elements to, typically a {@code <page>}
     * @param petriNet the net to write
     * @param graphPetriNet the drawing the net came from, for coordinates; may be {@code null}
     */
    void writeNetInto(Document document, Element parent, PetriNet petriNet, GraphPetriNet graphPetriNet) {
        this.graphPetriNet = graphPetriNet;
        generatePlaces(document, parent, petriNet.getListP());
        generateTransitions(document, parent, petriNet.getListT());
        generateArcs(document, parent, petriNet.getArcIn(), petriNet.getArcOut());
    }

    /**
     * Serialises a PNML document to a file with the formatting the tool uses everywhere.
     *
     * @param document the document to write
     * @param file destination file
     * @throws Exception if the document cannot be written
     */
    static void writeDocument(Document document, File file) throws Exception {
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
     * Serialises a PNML document to a string, for callers that ship it over the wire.
     *
     * @param document the document to write
     * @return the PNML text
     * @throws Exception if the document cannot be written
     */
    static String toXml(Document document) throws Exception {
        TransformerFactory transformerFactory = TransformerFactory.newInstance();
        Transformer transformer = transformerFactory.newTransformer();
        transformer.setOutputProperty(OutputKeys.INDENT, "yes");
        transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");
        transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");

        StringWriter writer = new StringWriter();
        transformer.transform(new DOMSource(document), new StreamResult(writer));
        return writer.toString();
    }

    /**
     * Generate places in PNML format
     */
    private void generatePlaces(Document document, Element netElement, PetriP[] places) {
        for (PetriP place : places) {
            String placeId = place.getId() != null ? place.getId() : "p" + place.getNumber();
            placeNumberToId.put(place.getNumber(), placeId);

            Element placeElement = document.createElementNS(PnmlConstants.PNML_NAMESPACE, PnmlConstants.ELEMENT_PLACE);
            placeElement.setAttribute(PnmlConstants.ATTR_ID, placeId);
            netElement.appendChild(placeElement);

            // Add name (without offset - let other tools use their own defaults)
            if (XmlHelper.isNotEmpty(place.getName())) {
                placeElement.appendChild(createNameElement(document, place.getName()));
            }

            // Add initial marking
            if (!place.markIsParam() && place.getMark() > 0) {
                Element markingElement = document.createElementNS(PnmlConstants.PNML_NAMESPACE, PnmlConstants.ELEMENT_INITIAL_MARKING);
                Element textElement = document.createElementNS(PnmlConstants.PNML_NAMESPACE, PnmlConstants.ELEMENT_TEXT);
                textElement.setTextContent(String.valueOf(place.getMark()));
                markingElement.appendChild(textElement);
                placeElement.appendChild(markingElement);
            }

            // Add toolspecific information (marking parameter, if any). Built unconditionally
            // and only appended when it ends up carrying something: a place whose only would-be
            // content was its coordinates now has nothing tool-specific to say at all.
            Element toolspecificElement = document.createElementNS(PnmlConstants.PNML_NAMESPACE, PnmlConstants.ELEMENT_TOOLSPECIFIC);
            toolspecificElement.setAttribute(PnmlConstants.ATTR_TOOL, PnmlConstants.TOOL_PETRI_OBJ_MODEL);
            toolspecificElement.setAttribute(PnmlConstants.ATTR_VERSION, PnmlConstants.TOOL_VERSION_PETRI_OBJ_MODEL);

            if (place.markIsParam() && place.getMarkParamName() != null) {
                Element markParamElement = document.createElementNS(PnmlConstants.PNML_NAMESPACE, PnmlConstants.ELEMENT_INITIAL_MARKING_PARAMETER);
                markParamElement.setTextContent(place.getMarkParamName());
                toolspecificElement.appendChild(markParamElement);
            }

            if (toolspecificElement.hasChildNodes()) {
                placeElement.appendChild(toolspecificElement);
            }

            GraphPetriPlace graphPlace = findGraphPlaceByNumber(place.getNumber());

            // Add graphics information with real coordinates for PNML compatibility. Omitted
            // entirely when there is no drawing to take a position from: the schema makes node
            // graphics optional, and a written (0,0) placeholder is indistinguishable from a
            // real position at the origin, which pins the node there on read instead of
            // leaving it to the fallback grid.
            if (graphPlace != null) {
                Element graphicsElement = document.createElementNS(PnmlConstants.PNML_NAMESPACE, PnmlConstants.ELEMENT_GRAPHICS);
                Element positionElement = document.createElementNS(PnmlConstants.PNML_NAMESPACE, PnmlConstants.ELEMENT_POSITION);
                Point2D center = graphPlace.getGraphElementCenter();
                positionElement.setAttribute(PnmlConstants.ATTR_X, String.valueOf(Math.round(center.getX())));
                positionElement.setAttribute(PnmlConstants.ATTR_Y, String.valueOf(Math.round(center.getY())));
                graphicsElement.appendChild(positionElement);
                placeElement.appendChild(graphicsElement);
            }
        }
    }

    /**
     * Generate transitions in PNML format
     */
    private void generateTransitions(Document document, Element netElement, PetriT[] transitions) {
        for (PetriT transition : transitions) {
            String transitionId = transition.getId() != null ? transition.getId() : "t" + transition.getNumber();
            transitionNumberToId.put(transition.getNumber(), transitionId);

            Element transitionElement = document.createElementNS(PnmlConstants.PNML_NAMESPACE, PnmlConstants.ELEMENT_TRANSITION);
            transitionElement.setAttribute(PnmlConstants.ATTR_ID, transitionId);
            netElement.appendChild(transitionElement);

            // Add name (without offset - let other tools use their own defaults)
            if (XmlHelper.isNotEmpty(transition.getName())) {
                transitionElement.appendChild(createNameElement(document, transition.getName()));
            }

            // Add toolspecific information for extended properties
            Element toolspecificElement = document.createElementNS(PnmlConstants.PNML_NAMESPACE, PnmlConstants.ELEMENT_TOOLSPECIFIC);
            toolspecificElement.setAttribute(PnmlConstants.ATTR_TOOL, PnmlConstants.TOOL_PETRI_OBJ_MODEL);
            toolspecificElement.setAttribute(PnmlConstants.ATTR_VERSION, PnmlConstants.TOOL_VERSION_PETRI_OBJ_MODEL);

            GraphPetriTransition graphTransition = findGraphTransitionByNumber(transition.getNumber());

            // Add time delay or its parameter
            if (transition.parametrIsParam() && transition.getParameterParamName() != null) {
                Element timeDelayParamElement = document.createElementNS(PnmlConstants.PNML_NAMESPACE, "timeDelayParameter");
                timeDelayParamElement.setTextContent(transition.getParameterParamName());
                toolspecificElement.appendChild(timeDelayParamElement);
            } else if (transition.getParameter() > 0) {
                Element timeDelayElement = document.createElementNS(PnmlConstants.PNML_NAMESPACE, "timeDelay");
                timeDelayElement.setTextContent(String.valueOf(transition.getParameter()));
                toolspecificElement.appendChild(timeDelayElement);
            }

            // Add delay mean value (always export if > 0)
            if (transition.getParameter() > 0) {
                Element delayMeanElement = document.createElementNS(PnmlConstants.PNML_NAMESPACE, "delayMeanValue");
                delayMeanElement.setTextContent(String.valueOf(transition.getParameter()));
                toolspecificElement.appendChild(delayMeanElement);
            }

            // Add standard deviation (always export if > 0)
            if (transition.getParamDeviation() > 0) {
                Element stdDeviationElement = document.createElementNS(PnmlConstants.PNML_NAMESPACE, "standardDeviation");
                stdDeviationElement.setTextContent(String.valueOf(transition.getParamDeviation()));
                toolspecificElement.appendChild(stdDeviationElement);
            }

            // Add priority or its parameter
            if (transition.priorityIsParam() && transition.getPriorityParamName() != null) {
                Element priorityParamElement = document.createElementNS(PnmlConstants.PNML_NAMESPACE, "priorityParameter");
                priorityParamElement.setTextContent(transition.getPriorityParamName());
                toolspecificElement.appendChild(priorityParamElement);
            } else if (transition.getPriority() != 0) {
                Element priorityElement = document.createElementNS(PnmlConstants.PNML_NAMESPACE, "priority");
                priorityElement.setTextContent(String.valueOf(transition.getPriority()));
                toolspecificElement.appendChild(priorityElement);
            }

            // Add probability or its parameter
            if (transition.probabilityIsParam() && transition.getProbabilityParamName() != null) {
                Element probabilityParamElement = document.createElementNS(PnmlConstants.PNML_NAMESPACE, "probabilityParameter");
                probabilityParamElement.setTextContent(transition.getProbabilityParamName());
                toolspecificElement.appendChild(probabilityParamElement);
            } else if (transition.getProbability() != 1.0) {
                Element probabilityElement = document.createElementNS(PnmlConstants.PNML_NAMESPACE, "probability");
                probabilityElement.setTextContent(String.valueOf(transition.getProbability()));
                toolspecificElement.appendChild(probabilityElement);
            }

            // Add distribution or its parameter
            if (transition.distributionIsParam() && transition.getDistributionParamName() != null) {
                Element distributionParamElement = document.createElementNS(PnmlConstants.PNML_NAMESPACE, "distributionParameter");
                distributionParamElement.setTextContent(transition.getDistributionParamName());
                toolspecificElement.appendChild(distributionParamElement);
            } else if (transition.getDistribution() != null && !transition.getDistribution().isEmpty()) {
                Element distributionElement = document.createElementNS(PnmlConstants.PNML_NAMESPACE, "distribution");
                distributionElement.setTextContent(transition.getDistribution());
                toolspecificElement.appendChild(distributionElement);
            }

            if (toolspecificElement.hasChildNodes()) {
                transitionElement.appendChild(toolspecificElement);
            }

            // Add graphics information with real coordinates for PNML compatibility. Omitted
            // entirely when there is no drawing to take a position from: the schema makes node
            // graphics optional, and a written (0,0) placeholder is indistinguishable from a
            // real position at the origin, which pins the node there on read instead of
            // leaving it to the fallback grid.
            if (graphTransition != null) {
                Element graphicsElement = document.createElementNS(PnmlConstants.PNML_NAMESPACE, PnmlConstants.ELEMENT_GRAPHICS);
                Element positionElement = document.createElementNS(PnmlConstants.PNML_NAMESPACE, PnmlConstants.ELEMENT_POSITION);
                Point2D center = graphTransition.getGraphElementCenter();
                positionElement.setAttribute(PnmlConstants.ATTR_X, String.valueOf(Math.round(center.getX())));
                positionElement.setAttribute(PnmlConstants.ATTR_Y, String.valueOf(Math.round(center.getY())));
                graphicsElement.appendChild(positionElement);
                transitionElement.appendChild(graphicsElement);
            }
        }
    }

    /**
     * Generate arcs in PNML format
     */
    private void generateArcs(Document document, Element netElement, ArcIn[] arcIns, ArcOut[] arcOuts) {
        int arcCounter = 1;

        // Generate input arcs (Place to Transition)
        for (ArcIn arcIn : arcIns) {
            Element arcElement = document.createElementNS(PnmlConstants.PNML_NAMESPACE, PnmlConstants.ELEMENT_ARC);
            String arcId = arcIn.getId() != null ? arcIn.getId() : "arc" + arcCounter++;
            arcElement.setAttribute(PnmlConstants.ATTR_ID, arcId);
            arcElement.setAttribute(PnmlConstants.ATTR_SOURCE, placeNumberToId.get(arcIn.getNumP()));
            arcElement.setAttribute(PnmlConstants.ATTR_TARGET, transitionNumberToId.get(arcIn.getNumT()));
            netElement.appendChild(arcElement);

            // Add inscription (weight) - always include even if quantity is 1
            Element inscriptionElement = document.createElementNS(PnmlConstants.PNML_NAMESPACE, PnmlConstants.ELEMENT_INSCRIPTION);
            Element textElement = document.createElementNS(PnmlConstants.PNML_NAMESPACE, PnmlConstants.ELEMENT_TEXT);
            textElement.setTextContent(String.valueOf(arcIn.getQuantity()));
            inscriptionElement.appendChild(textElement);
            arcElement.appendChild(inscriptionElement);

            // Add toolspecific information for informational arcs and parameters
            boolean needsToolspecific = arcIn.getIsInf() || arcIn.infIsParam() || arcIn.kIsParam();
            if (needsToolspecific) {
                Element toolspecificElement = document.createElementNS(PnmlConstants.PNML_NAMESPACE, PnmlConstants.ELEMENT_TOOLSPECIFIC);
                toolspecificElement.setAttribute(PnmlConstants.ATTR_TOOL, PnmlConstants.TOOL_PETRI_OBJ_MODEL);
                toolspecificElement.setAttribute(PnmlConstants.ATTR_VERSION, PnmlConstants.TOOL_VERSION_PETRI_OBJ_MODEL);

                if (arcIn.getIsInf()) {
                    Element infElement = document.createElementNS(PnmlConstants.PNML_NAMESPACE, "informational");
                    infElement.setTextContent("true");
                    toolspecificElement.appendChild(infElement);
                }

                if (arcIn.infIsParam() && arcIn.getInfParamName() != null) {
                    Element infParamElement = document.createElementNS(PnmlConstants.PNML_NAMESPACE, "informationalParameter");
                    infParamElement.setTextContent(arcIn.getInfParamName());
                    toolspecificElement.appendChild(infParamElement);
                }

                if (arcIn.kIsParam() && arcIn.getKParamName() != null) {
                    Element kParamElement = document.createElementNS(PnmlConstants.PNML_NAMESPACE, "multiplicityParameter");
                    kParamElement.setTextContent(arcIn.getKParamName());
                    toolspecificElement.appendChild(kParamElement);
                }

                arcElement.appendChild(toolspecificElement);
            }
        }

        // Generate output arcs (Transition to Place)
        for (ArcOut arcOut : arcOuts) {
            Element arcElement = document.createElementNS(PnmlConstants.PNML_NAMESPACE, PnmlConstants.ELEMENT_ARC);
            String arcId = arcOut.getId() != null ? arcOut.getId() : "arc" + arcCounter++;
            arcElement.setAttribute(PnmlConstants.ATTR_ID, arcId);
            arcElement.setAttribute(PnmlConstants.ATTR_SOURCE, transitionNumberToId.get(arcOut.getNumT()));
            arcElement.setAttribute(PnmlConstants.ATTR_TARGET, placeNumberToId.get(arcOut.getNumP()));
            netElement.appendChild(arcElement);

            // Add inscription (weight) - always include even if quantity is 1
            Element inscriptionElement = document.createElementNS(PnmlConstants.PNML_NAMESPACE, PnmlConstants.ELEMENT_INSCRIPTION);
            Element textElement = document.createElementNS(PnmlConstants.PNML_NAMESPACE, PnmlConstants.ELEMENT_TEXT);
            textElement.setTextContent(String.valueOf(arcOut.getQuantity()));
            inscriptionElement.appendChild(textElement);
            arcElement.appendChild(inscriptionElement);

            // Add toolspecific information for parameters
            if (arcOut.kIsParam() && arcOut.getKParamName() != null) {
                Element toolspecificElement = document.createElementNS(PnmlConstants.PNML_NAMESPACE, PnmlConstants.ELEMENT_TOOLSPECIFIC);
                toolspecificElement.setAttribute(PnmlConstants.ATTR_TOOL, PnmlConstants.TOOL_PETRI_OBJ_MODEL);
                toolspecificElement.setAttribute(PnmlConstants.ATTR_VERSION, PnmlConstants.TOOL_VERSION_PETRI_OBJ_MODEL);

                Element kParamElement = document.createElementNS(PnmlConstants.PNML_NAMESPACE, "multiplicityParameter");
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
        Element nameElement = document.createElementNS(PnmlConstants.PNML_NAMESPACE, PnmlConstants.ELEMENT_NAME);
        Element textElement = document.createElementNS(PnmlConstants.PNML_NAMESPACE, PnmlConstants.ELEMENT_TEXT);
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
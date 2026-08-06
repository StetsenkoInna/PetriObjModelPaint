package ua.stetsenkoinna.pnml;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.InputSource;
import ua.stetsenkoinna.graphnet.GraphNetBuilder;
import ua.stetsenkoinna.graphnet.GraphPetriObjModel;
import ua.stetsenkoinna.graphnet.GraphPetriObject;
import ua.stetsenkoinna.graphnet.NetTemplateRef;
import ua.stetsenkoinna.petriobj.ArcIn;
import ua.stetsenkoinna.petriobj.ArcOut;
import ua.stetsenkoinna.petriobj.PetriNet;
import ua.stetsenkoinna.petriobj.PetriObjLink;
import ua.stetsenkoinna.petriobj.PetriP;
import ua.stetsenkoinna.petriobj.PetriT;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.awt.Point;
import java.io.File;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads a PNML document as a composed {@link GraphPetriObjModel}.
 *
 * <p>Each {@code <page>} of the document's net becomes one Petri-object, and the links
 * between them are taken from the net-level {@code <toolspecific tool="PetriObjModel">}
 * block. A document written before Petri-object composition existed — a single page, or no
 * page at all, and no link block — reads back as a model of one object, so every net saved
 * so far keeps opening.
 *
 * <p>Element numbers are handed out by static counters in {@link PetriP} and {@link PetriT}
 * and double as indices into each net's own arrays, so the counters are reset before every
 * page. A caller that parses concurrently has to serialise those parses.
 *
 * @see PnmlModelGenerator
 */
public class PnmlModelParser {

    private static final Logger log = LoggerFactory.getLogger(PnmlModelParser.class);

    /**
     * Reads a model from a PNML file.
     *
     * @param file the file to read
     * @return the model described by the document
     * @throws Exception if the document is not readable PNML
     */
    public GraphPetriObjModel parse(File file) throws Exception {
        DocumentBuilder builder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
        return buildModel(builder.parse(file));
    }

    /**
     * Reads a model from a PNML string.
     *
     * @param xml the document text
     * @return the model described by the document
     * @throws Exception if the document is not readable PNML
     */
    public GraphPetriObjModel parseXml(String xml) throws Exception {
        DocumentBuilder builder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
        return buildModel(builder.parse(new InputSource(new StringReader(xml))));
    }

    private GraphPetriObjModel buildModel(Document document) throws Exception {
        Element netElement = PnmlParser.findNetElement(document);

        String modelName = XmlHelper.getDirectTextContent(netElement, PnmlConstants.ELEMENT_NAME);
        if (!XmlHelper.isNotEmpty(modelName)) {
            modelName = netElement.getAttribute(PnmlConstants.ATTR_ID);
        }
        GraphPetriObjModel model = new GraphPetriObjModel(modelName);

        List<Element> pages = XmlHelper.directChildren(netElement, PnmlConstants.ELEMENT_PAGE);
        if (pages.isEmpty()) {
            // A document whose elements sit directly under <net>: one Petri-object.
            model.addObject(readObject(netElement, netElement, modelName, 0));
        } else {
            for (int index = 0; index < pages.size(); index++) {
                Element page = pages.get(index);
                model.addObject(readObject(page, page, defaultObjectName(modelName, pages.size(), index), index));
            }
        }

        for (PetriObjLink link : readLinks(netElement)) {
            try {
                model.addLink(link);
            } catch (IllegalArgumentException invalid) {
                log.warn("Ignoring link that does not fit the parsed model: {}", invalid.getMessage());
            }
        }
        return model;
    }

    /**
     * Names an object that carries no metadata of its own: a lone object is the model
     * itself, several objects are numbered after it.
     */
    private static String defaultObjectName(String modelName, int objectCount, int index) {
        return objectCount == 1 ? modelName : modelName + " " + (index + 1);
    }

    /**
     * Reads one Petri-object: its net and the metadata that turns the net into an object.
     *
     * @param scope element holding the net's places, transitions and arcs
     * @param metadataScope element whose tool-specific block holds the object's metadata
     * @param fallbackName name to use when the document does not carry one
     * @param index position of the object in the model
     */
    private GraphPetriObject readObject(Element scope, Element metadataScope,
                                        String fallbackName, int index) throws Exception {
        Element objectElement = findObjectElement(metadataScope);

        String name = objectElement != null
                ? objectElement.getAttribute(PnmlConstants.ATTR_NAME) : null;
        if (!XmlHelper.isNotEmpty(name)) {
            name = XmlHelper.getDirectTextContent(scope, PnmlConstants.ELEMENT_NAME);
        }
        if (!XmlHelper.isNotEmpty(name)) {
            name = fallbackName;
        }

        resetElementCounters();
        PnmlParser parser = new PnmlParser();
        PetriNet net = parser.parseScope(scope, name);

        GraphPetriObject object = new GraphPetriObject(name,
                GraphNetBuilder.build(net, parser.getAllPlaceCoordinates(),
                        parser.getAllTransitionCoordinates(), null));

        if (objectElement != null) {
            object.setPriority(XmlHelper.parseIntSafe(
                    objectElement.getAttribute(PnmlConstants.ATTR_PRIORITY), 0));
            object.setPosition(new Point(
                    XmlHelper.parseIntSafe(objectElement.getAttribute(PnmlConstants.ATTR_X), 0),
                    XmlHelper.parseIntSafe(objectElement.getAttribute(PnmlConstants.ATTR_Y), 0)));
            object.setSize(
                    XmlHelper.parseIntSafe(objectElement.getAttribute(PnmlConstants.ATTR_WIDTH), 0),
                    XmlHelper.parseIntSafe(objectElement.getAttribute(PnmlConstants.ATTR_HEIGHT), 0));
            object.setCollapsed(
                    Boolean.parseBoolean(objectElement.getAttribute(PnmlConstants.ATTR_COLLAPSED)));
        }
        object.setTemplate(readTemplate(metadataScope));
        return object;
    }

    /**
     * Element numbers are indices into the net's own arrays, so numbering has to start over
     * for every Petri-object.
     */
    private static void resetElementCounters() {
        PetriP.initNext();
        PetriT.initNext();
        ArcIn.initNext();
        ArcOut.initNext();
    }

    private static Element findObjectElement(Element scope) {
        for (Element toolspecific : toolSpecificBlocks(scope)) {
            Element object = XmlHelper.firstDirectChild(toolspecific, PnmlConstants.ELEMENT_PETRI_OBJECT);
            if (object != null) {
                return object;
            }
        }
        return null;
    }

    private static NetTemplateRef readTemplate(Element scope) {
        for (Element toolspecific : toolSpecificBlocks(scope)) {
            Element template = XmlHelper.firstDirectChild(toolspecific, PnmlConstants.ELEMENT_NET_TEMPLATE);
            if (template == null) {
                continue;
            }
            String method = template.getAttribute(PnmlConstants.ATTR_METHOD);
            if (!XmlHelper.isNotEmpty(method)) {
                continue;
            }
            List<String> arguments = new ArrayList<>();
            for (Element argument : XmlHelper.directChildren(template, PnmlConstants.ELEMENT_TEMPLATE_ARGUMENT)) {
                arguments.add(argument.getTextContent());
            }
            return new NetTemplateRef(method, arguments);
        }
        return null;
    }

    private static List<PetriObjLink> readLinks(Element netElement) {
        List<PetriObjLink> links = new ArrayList<>();
        for (Element toolspecific : toolSpecificBlocks(netElement)) {
            Element linksElement =
                    XmlHelper.firstDirectChild(toolspecific, PnmlConstants.ELEMENT_PETRI_OBJECT_LINKS);
            if (linksElement == null) {
                continue;
            }
            for (Element linkElement : XmlHelper.directChildren(linksElement, PnmlConstants.ELEMENT_LINK)) {
                PetriObjLink link = readLink(linkElement);
                if (link != null) {
                    links.add(link);
                }
            }
        }
        return links;
    }

    private static PetriObjLink readLink(Element element) {
        int sourceObject = XmlHelper.parseIntSafe(element.getAttribute(PnmlConstants.ATTR_SOURCE_OBJECT), -1);
        int sourceElement = XmlHelper.parseIntSafe(element.getAttribute(PnmlConstants.ATTR_SOURCE_ELEMENT), -1);
        int targetObject = XmlHelper.parseIntSafe(element.getAttribute(PnmlConstants.ATTR_TARGET_OBJECT), -1);
        int targetElement = XmlHelper.parseIntSafe(element.getAttribute(PnmlConstants.ATTR_TARGET_ELEMENT), -1);
        int quantity = XmlHelper.parseIntSafe(element.getAttribute(PnmlConstants.ATTR_QUANTITY), 1);
        boolean informational = Boolean.parseBoolean(element.getAttribute(PnmlConstants.ATTR_INFORMATIONAL));
        String type = element.getAttribute(PnmlConstants.ATTR_LINK_TYPE);

        try {
            return switch (type) {
                case PnmlConstants.LINK_TYPE_PLACE_FUSION ->
                        PetriObjLink.placeFusion(sourceObject, sourceElement, targetObject, targetElement);
                case PnmlConstants.LINK_TYPE_TRANSITION_TO_PLACE ->
                        PetriObjLink.transitionToPlace(sourceObject, sourceElement, targetObject,
                                targetElement, quantity);
                case PnmlConstants.LINK_TYPE_PLACE_TO_TRANSITION ->
                        PetriObjLink.placeToTransition(sourceObject, sourceElement, targetObject,
                                targetElement, quantity, informational);
                default -> {
                    log.warn("Ignoring link of unknown type '{}'", type);
                    yield null;
                }
            };
        } catch (IllegalArgumentException malformed) {
            log.warn("Ignoring malformed link: {}", malformed.getMessage());
            return null;
        }
    }

    /**
     * @return the tool-specific blocks this tool wrote, direct children of the given element
     */
    private static List<Element> toolSpecificBlocks(Element scope) {
        List<Element> blocks = new ArrayList<>();
        for (Element toolspecific : XmlHelper.directChildren(scope, PnmlConstants.ELEMENT_TOOLSPECIFIC)) {
            if (PnmlConstants.TOOL_PETRI_OBJ_MODEL.equals(toolspecific.getAttribute(PnmlConstants.ATTR_TOOL))) {
                blocks.add(toolspecific);
            }
        }
        return blocks;
    }
}

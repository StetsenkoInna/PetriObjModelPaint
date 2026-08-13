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
import ua.stetsenkoinna.petriobj.PetriObjLinkType;
import ua.stetsenkoinna.petriobj.PetriP;
import ua.stetsenkoinna.petriobj.PetriT;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.awt.Point;
import java.io.File;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Reads a PNML document as a composed {@link GraphPetriObjModel}.
 *
 * <p>Each {@code <page>} of the document's net becomes one Petri-object. A document written
 * before Petri-object composition existed, a single page, or no page at all, and no link
 * block, reads back as a model of one object, so every net saved so far keeps opening.
 *
 * <h2>Two dialects, one reader</h2>
 *
 * <p>Where the links come from depends on what the document carries, and the choice is made
 * on structure rather than on a version attribute, which a writer may stamp for any reason:
 *
 * <ul>
 *   <li><b>Legacy</b>, no reference nodes. The links are read from the net-level
 *       {@code <toolspecific tool="PetriObjModel">} block, exactly as they always were.
 *       This is the path every saved file and every other tool in this family takes, and it
 *       is deliberately left untouched: a reader that only understood the new shape would be
 *       a data-loss bug.</li>
 *   <li><b>Conformant</b>, at least one {@code <referencePlace>} or
 *       {@code <referenceTransition>}. The inter-object structure is then stated in the
 *       standard's own terms, and {@link ReferenceNodeIndex} is the authority on it. The
 *       tool-specific link block is still read, but only to recover what the structure
 *       cannot state; see {@link #mergeDeclaredLinks}.</li>
 * </ul>
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
        // The reference nodes decide which reader runs, and they are built before any page is
        // read because a page reader needs to know which of its arcs are really links.
        ReferenceNodeIndex references = ReferenceNodeIndex.isConformant(document)
                ? new ReferenceNodeIndex(netElement, pages)
                : null;
        if (references == null) {
            warnAboutDuplicateIds(pages);
        }

        if (pages.isEmpty()) {
            // A document whose elements sit directly under <net>: one Petri-object.
            model.addObject(readObject(netElement, netElement, modelName, 0,
                    references == null ? null : references.pageReferences(0)));
        } else {
            for (int index = 0; index < pages.size(); index++) {
                Element page = pages.get(index);
                model.addObject(readObject(page, page,
                        defaultObjectName(modelName, pages.size(), index), index,
                        references == null ? null : references.pageReferences(index)));
            }
        }

        for (PetriObjLink link : readModelLinks(netElement, references)) {
            try {
                model.addLink(link);
            } catch (IllegalArgumentException invalid) {
                log.warn("Ignoring link that does not fit the parsed model: {}", invalid.getMessage());
            }
        }
        return model;
    }

    /**
     * Decides what the model's links are.
     *
     * <p>For a legacy document there is one source and it is the tool-specific block. For a
     * conformant one the structure comes first, and the two disagree in a way that has to be
     * settled deliberately:
     *
     * <ul>
     *   <li><b>Fusions</b>: the reference nodes are the complete set and every declared
     *       fusion is ignored. Fusion is destructive and not idempotent, replaying a
     *       declaration over an already fused model chains it further rather than
     *       reproducing it, so it gets exactly one source of truth.</li>
     *   <li><b>Arc-like links</b>: the union, keyed on endpoints. A declaration the structure
     *       also states adds nothing, and where they differ on multiplicity or on the
     *       informational flag the structure wins, because that is what every other reader
     *       of the document sees. A declaration the structure does not state is applied: that
     *       is how a legacy-style declaration inside a conformant document survives.</li>
     * </ul>
     */
    private static List<PetriObjLink> readModelLinks(Element netElement, ReferenceNodeIndex references) {
        List<PetriObjLink> declared = readLinks(netElement, references);
        if (references == null) {
            return declared;
        }
        return mergeDeclaredLinks(references.structuralLinks(), declared);
    }

    /**
     * Merges the declared links into the structural ones under the rules described in
     * {@link #readModelLinks}.
     */
    private static List<PetriObjLink> mergeDeclaredLinks(List<PetriObjLink> structural,
                                                         List<PetriObjLink> declared) {
        List<PetriObjLink> links = new ArrayList<>(structural);
        Map<String, PetriObjLink> byEndpoints = new LinkedHashMap<>();
        for (PetriObjLink link : structural) {
            byEndpoints.put(endpointKey(link), link);
        }
        for (PetriObjLink link : declared) {
            if (link.getType() == PetriObjLinkType.PLACE_FUSION) {
                continue;
            }
            PetriObjLink structured = byEndpoints.get(endpointKey(link));
            if (structured == null) {
                links.add(link);
                byEndpoints.put(endpointKey(link), link);
            } else if (structured.getQuantity() != link.getQuantity()
                    || structured.isInformational() != link.isInformational()) {
                log.warn("Link {} disagrees with the declaration {}; the document's structure wins",
                        structured, link);
            }
        }
        return links;
    }

    /** @return what makes two link declarations the same link, ignoring their annotations */
    private static String endpointKey(PetriObjLink link) {
        return link.getType() + ":" + link.getSourceObject() + ":" + link.getSourceElement()
                + ":" + link.getTargetObject() + ":" + link.getTargetElement();
    }

    /**
     * Warns when a legacy document reuses an element id on two pages.
     *
     * <p>It parses fine, every page is read with its own id table, but the same document
     * read as conformant would be rejected, and a user who is about to hit that deserves to
     * hear about it while the file still opens.
     */
    private static void warnAboutDuplicateIds(List<Element> pages) {
        Set<String> seen = new HashSet<>();
        for (Element page : pages) {
            for (Element element : XmlHelper.directChildren(page)) {
                switch (element.getTagName()) {
                    case PnmlConstants.ELEMENT_PLACE, PnmlConstants.ELEMENT_TRANSITION,
                            PnmlConstants.ELEMENT_ARC -> {
                        String id = element.getAttribute(PnmlConstants.ATTR_ID);
                        if (XmlHelper.isNotEmpty(id) && !seen.add(id)) {
                            log.warn("Element id '{}' is used on more than one page; "
                                    + "a document that also carried reference nodes would be rejected", id);
                        }
                    }
                    default -> { /* pages carry names, tool-specific blocks and nothing else */ }
                }
            }
        }
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
     * @param references what this page's reference nodes mean, or {@code null} for a legacy
     *        document
     */
    private GraphPetriObject readObject(Element scope, Element metadataScope,
                                        String fallbackName, int index,
                                        ReferenceNodeIndex.PageReferences references) throws Exception {
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
        PetriNet net = parser.parseScope(scope, name, references);

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
        for (Element toolspecific : XmlHelper.toolSpecificBlocks(scope)) {
            Element object = XmlHelper.firstDirectChild(toolspecific, PnmlConstants.ELEMENT_PETRI_OBJECT);
            if (object != null) {
                return object;
            }
        }
        return null;
    }

    private static NetTemplateRef readTemplate(Element scope) {
        for (Element toolspecific : XmlHelper.toolSpecificBlocks(scope)) {
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

    /**
     * Reads the link declarations of the net-level tool-specific block.
     *
     * <p>The block is matched on its tool alone, never on its version: both {@code "2.0"} and
     * {@code "2.1"} are current, and a version filter here would drop the links of every
     * document written by a build newer than this one.
     *
     * @param netElement the document's net
     * @param references the document's reference nodes, or {@code null} for a legacy
     *        document; when present, an endpoint given by id is resolved through it
     */
    private static List<PetriObjLink> readLinks(Element netElement, ReferenceNodeIndex references) {
        List<PetriObjLink> links = new ArrayList<>();
        for (Element toolspecific : XmlHelper.toolSpecificBlocks(netElement)) {
            Element linksElement =
                    XmlHelper.firstDirectChild(toolspecific, PnmlConstants.ELEMENT_PETRI_OBJECT_LINKS);
            if (linksElement == null) {
                continue;
            }
            for (Element linkElement : XmlHelper.directChildren(linksElement, PnmlConstants.ELEMENT_LINK)) {
                PetriObjLink link = readLink(linkElement, references);
                if (link != null) {
                    links.add(link);
                }
            }
        }
        return links;
    }

    private static PetriObjLink readLink(Element element, ReferenceNodeIndex references) {
        int sourceObject = XmlHelper.parseIntSafe(element.getAttribute(PnmlConstants.ATTR_SOURCE_OBJECT), -1);
        int sourceElement = XmlHelper.parseIntSafe(element.getAttribute(PnmlConstants.ATTR_SOURCE_ELEMENT), -1);
        int targetObject = XmlHelper.parseIntSafe(element.getAttribute(PnmlConstants.ATTR_TARGET_OBJECT), -1);
        int targetElement = XmlHelper.parseIntSafe(element.getAttribute(PnmlConstants.ATTR_TARGET_ELEMENT), -1);

        // An id says which element the user meant even if the document's elements are
        // numbered differently than when the declaration was written, so it wins.
        int[] source = slotOf(references, element.getAttribute(PnmlConstants.ATTR_SOURCE_ELEMENT_ID));
        if (source != null) {
            sourceObject = source[0];
            sourceElement = source[1];
        }
        int[] target = slotOf(references, element.getAttribute(PnmlConstants.ATTR_TARGET_ELEMENT_ID));
        if (target != null) {
            targetObject = target[0];
            targetElement = target[1];
        }

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
     * @return the object and element index the given id addresses, or {@code null} when the
     *         document has no such element or does not use ids at all
     */
    private static int[] slotOf(ReferenceNodeIndex references, String elementId) {
        if (references == null || !XmlHelper.isNotEmpty(elementId)) {
            return null;
        }
        int[] slot = references.slotOf(elementId);
        if (slot == null) {
            log.warn("Link declaration names element '{}', which the document does not contain",
                    elementId);
        }
        return slot;
    }
}

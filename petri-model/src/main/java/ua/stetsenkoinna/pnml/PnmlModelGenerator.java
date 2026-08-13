package ua.stetsenkoinna.pnml;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import ua.stetsenkoinna.graphnet.GraphPetriObjModel;
import ua.stetsenkoinna.graphnet.GraphPetriObject;
import ua.stetsenkoinna.graphnet.NetTemplateRef;
import ua.stetsenkoinna.petriobj.ExceptionInvalidNetStructure;
import ua.stetsenkoinna.petriobj.ExceptionInvalidTimeDelay;
import ua.stetsenkoinna.petriobj.PetriObjLink;
import ua.stetsenkoinna.petriobj.PetriObjLinkType;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Writes a composed {@link GraphPetriObjModel} as a PNML document.
 *
 * <p>The composition is expressed with the structuring PNML already has: one {@code <page>}
 * per Petri-object inside a single {@code <net>}, and, this is the part that makes the
 * document mean the same thing to a reader that is not this tool, one
 * {@code <referencePlace>} or {@code <referenceTransition>} per link. A reference node is
 * how ISO/IEC 15909-2 says "this node is shared with another page"; without them the pages
 * are unrelated nets and every link is invisible, however faithfully a tool-specific block
 * records it.
 *
 * <h2>How each kind of link is drawn</h2>
 *
 * <p>Uniformly: the reference node lives on the <em>source</em> object's page and stands for
 * the <em>target</em> element.
 *
 * <ul>
 *   <li><b>Place fusion</b> {@code A.p = B.q}, page A's {@code <place>} is <em>replaced</em>
 *       by {@code <referencePlace id="A.p" ref="B.q"/>}. The replacement, rather than an
 *       addition, is what keeps the page's place ordinals unchanged, and it is the right way
 *       round because wiring a fusion redirects the source object's slot to the target's
 *       instance: the target's place is the one that survives. Every arc of page A that
 *       touched the place keeps pointing at the same id.</li>
 *   <li><b>Transition to place</b> {@code A.t -> B.p}, page A gains a stand-in
 *       {@code <referencePlace>} for {@code B.p} and an ordinary arc into it.</li>
 *   <li><b>Place to transition</b> {@code A.p -> B.t}, page A gains a stand-in
 *       {@code <referenceTransition>} for {@code B.t} and an ordinary arc into it.</li>
 * </ul>
 *
 * <h2>What still has to be tool-specific</h2>
 *
 * <p>Only what a P/T net cannot say: an informational (test) arc, which has no P/T form at
 * all; the object's priority, position and template; whether a reference node is the
 * object's own slot or a stand-in, a difference flattening deliberately erases; and the
 * marking of a place that ceases to exist on flattening.
 *
 * <p>The {@code <petriObjectLinks>} block is still written and is now <em>redundant</em>
 * with the structure. It earns its place as compatibility, a reader that predates reference
 * nodes recovers the two arc-like link types from it in full, and as a record of what the
 * user declared, addressed by element id rather than by position alone. It is not a second
 * source of truth, and readers here treat it as subordinate.
 *
 * @see PnmlModelParser
 */
public class PnmlModelGenerator {

    private static final Logger log = LoggerFactory.getLogger(PnmlModelGenerator.class);

    /**
     * Writes the model to a PNML file.
     *
     * @param model the model to export
     * @param file destination file
     * @throws ExceptionInvalidNetStructure if one of the object nets is not a valid Petri net
     * @throws ExceptionInvalidTimeDelay if one of the object nets has an invalid time delay
     * @throws Exception if the document cannot be written
     */
    public void generate(GraphPetriObjModel model, File file) throws Exception {
        PnmlGenerator.writeDocument(buildDocument(model), file);
    }

    /**
     * Renders the model as a PNML string, for callers that send it over the wire instead of
     * storing it.
     *
     * @param model the model to export
     * @return the PNML text
     * @throws Exception if the document cannot be built
     */
    public String generateXml(GraphPetriObjModel model) throws Exception {
        return PnmlGenerator.toXml(buildDocument(model));
    }

    private Document buildDocument(GraphPetriObjModel model) throws Exception {
        if (model.getObjectCount() == 0) {
            throw new Exception(PnmlConstants.ERROR_NO_OBJECTS);
        }
        DocumentBuilder builder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
        Document document = builder.newDocument();

        Element pnmlElement = document.createElement(PnmlConstants.ELEMENT_PNML);
        pnmlElement.setAttribute(PnmlConstants.ATTR_XMLNS, PnmlConstants.PNML_NAMESPACE);
        document.appendChild(pnmlElement);

        Element netElement = document.createElement(PnmlConstants.ELEMENT_NET);
        netElement.setAttribute(PnmlConstants.ATTR_ID, model.getName());
        netElement.setAttribute(PnmlConstants.ATTR_TYPE, PnmlConstants.PTNET_TYPE);
        netElement.appendChild(createNameElement(document, model.getName()));
        pnmlElement.appendChild(netElement);

        List<Element> pages = new ArrayList<>(model.getObjectCount());
        for (int index = 0; index < model.getObjectCount(); index++) {
            Element page = createPage(document, model.getObject(index), index);
            netElement.appendChild(page);
            pages.add(page);
        }

        // Ids have to be settled before anything points at them: a reference node names the
        // element it stands for, and a link declaration names both of its endpoints.
        ElementIds ids = ElementIds.of(pages);
        projectLinks(document, model, pages, ids);

        netElement.appendChild(createLinksBlock(document, model, ids));
        assertWriterInvariants(document);
        return document;
    }

    /**
     * Builds the page that carries one Petri-object: its metadata and its net.
     */
    private Element createPage(Document document, GraphPetriObject object, int index)
            throws ExceptionInvalidNetStructure, ExceptionInvalidTimeDelay {
        Element page = document.createElement(PnmlConstants.ELEMENT_PAGE);
        page.setAttribute(PnmlConstants.ATTR_ID, pageId(index));
        page.appendChild(createNameElement(document, object.getName()));

        Element toolspecific = createToolspecific(document,
                PnmlConstants.TOOL_VERSION_OBJECT_MODEL_CONFORMANT);
        Element objectElement = document.createElement(PnmlConstants.ELEMENT_PETRI_OBJECT);
        objectElement.setAttribute(PnmlConstants.ATTR_INDEX, String.valueOf(index));
        objectElement.setAttribute(PnmlConstants.ATTR_NAME, object.getName());
        objectElement.setAttribute(PnmlConstants.ATTR_PRIORITY, String.valueOf(object.getPriority()));
        objectElement.setAttribute(PnmlConstants.ATTR_X, String.valueOf(object.getPosition().x));
        objectElement.setAttribute(PnmlConstants.ATTR_Y, String.valueOf(object.getPosition().y));
        if (object.getWidth() > 0 && object.getHeight() > 0) {
            objectElement.setAttribute(PnmlConstants.ATTR_WIDTH, String.valueOf(object.getWidth()));
            objectElement.setAttribute(PnmlConstants.ATTR_HEIGHT, String.valueOf(object.getHeight()));
        }
        if (object.isCollapsed()) {
            objectElement.setAttribute(PnmlConstants.ATTR_COLLAPSED, "true");
        }
        toolspecific.appendChild(objectElement);

        NetTemplateRef template = object.getTemplate();
        if (template != null) {
            Element templateElement = document.createElement(PnmlConstants.ELEMENT_NET_TEMPLATE);
            templateElement.setAttribute(PnmlConstants.ATTR_METHOD, template.getMethodName());
            for (String argument : template.getArguments()) {
                Element argumentElement = document.createElement(PnmlConstants.ELEMENT_TEMPLATE_ARGUMENT);
                argumentElement.setTextContent(argument);
                templateElement.appendChild(argumentElement);
            }
            toolspecific.appendChild(templateElement);
        }
        page.appendChild(toolspecific);

        // Rebuilding the net makes element numbers match their position in the graph lists,
        // which is what the link declarations address.
        object.getGraphNet().createPetriNet(object.getName());
        new PnmlGenerator().writeNetInto(document, page,
                object.getGraphNet().getPetriNet(), object.getGraphNet());
        return page;
    }

    /** @return the id of the page that carries the n-th Petri-object */
    private static String pageId(int index) {
        return PnmlConstants.OBJECT_PAGE_ID_PREFIX + index;
    }

    /**
     * Draws every link into the pages, as described in this class's documentation.
     *
     * <p>A link the structure cannot express is skipped rather than fatal, a user's export
     * must not fail because one declaration is odd, but it is skipped loudly, and it is
     * still written into the tool-specific block.
     */
    private void projectLinks(Document document, GraphPetriObjModel model,
                              List<Element> pages, ElementIds ids) {
        List<PetriObjLink> links = model.getLinks();
        Map<String, Integer> lastFusionPerSlot = lastFusionPerSlot(links);
        Map<String, Element> representatives = new HashMap<>();

        for (int index = 0; index < links.size(); index++) {
            PetriObjLink link = links.get(index);
            if (link.getSourceObject() == link.getTargetObject()) {
                log.warn("Not drawing link {}: a link inside one Petri-object has no structural form",
                        link);
                continue;
            }
            String sourceId = ids.elementId(link.getSourceObject(), link.getSourceElement(),
                    link.getType() != PetriObjLinkType.TRANSITION_TO_PLACE);
            String targetId = ids.elementId(link.getTargetObject(), link.getTargetElement(),
                    link.getType() != PetriObjLinkType.PLACE_TO_TRANSITION);
            if (sourceId == null || targetId == null) {
                log.warn("Not drawing link {}: it addresses an element the objects do not have", link);
                continue;
            }

            switch (link.getType()) {
                case PLACE_FUSION -> {
                    // Wiring keeps only the last fusion of a slot, the earlier ones are
                    // overwritten, so drawing them all would say something the engine never does.
                    if (Integer.valueOf(index).equals(lastFusionPerSlot.get(slotKey(link)))) {
                        fusePlace(document, pages.get(link.getSourceObject()), sourceId, targetId);
                    } else {
                        log.warn("Not drawing link {}: a later fusion replaces the same place slot", link);
                    }
                }
                case TRANSITION_TO_PLACE -> {
                    Element reference = representative(document, pages, representatives,
                            link.getSourceObject(), link.getTargetObject(), targetId,
                            PnmlConstants.ELEMENT_REFERENCE_PLACE);
                    appendLinkArc(document, pages.get(link.getSourceObject()), index,
                            sourceId, reference.getAttribute(PnmlConstants.ATTR_ID),
                            link.getQuantity(), false);
                }
                case PLACE_TO_TRANSITION -> {
                    Element reference = representative(document, pages, representatives,
                            link.getSourceObject(), link.getTargetObject(), targetId,
                            PnmlConstants.ELEMENT_REFERENCE_TRANSITION);
                    appendLinkArc(document, pages.get(link.getSourceObject()), index,
                            sourceId, reference.getAttribute(PnmlConstants.ATTR_ID),
                            link.getQuantity(), link.isInformational());
                }
            }
        }
    }

    /**
     * @return for every place slot several fusions share, the index of the last one, the
     *         only one wiring actually leaves in place
     */
    private static Map<String, Integer> lastFusionPerSlot(List<PetriObjLink> links) {
        Map<String, Integer> last = new HashMap<>();
        for (int index = 0; index < links.size(); index++) {
            PetriObjLink link = links.get(index);
            // A fusion inside one object is not drawable at all, so letting it claim the slot
            // would suppress a drawable fusion of the same slot for nothing.
            if (link.getType() == PetriObjLinkType.PLACE_FUSION
                    && link.getSourceObject() != link.getTargetObject()) {
                last.put(slotKey(link), index);
            }
        }
        return last;
    }

    private static String slotKey(PetriObjLink link) {
        return link.getSourceObject() + ":" + link.getSourceElement();
    }

    /**
     * Turns the source object's own {@code <place>} into the reference node that says the
     * place belongs to another object.
     *
     * <p>Everything the drawing needs is moved across, including the marking the place used
     * to carry: it has no effect once the fusion is wired, so it is not part of the standard
     * projection, but a user who typed it would not expect it to vanish from the file.
     */
    private void fusePlace(Document document, Element page, String placeId, String targetId) {
        Element place = findById(page, PnmlConstants.ELEMENT_PLACE, placeId);
        if (place == null) {
            log.warn("Not fusing place {}: the page does not contain it", placeId);
            return;
        }

        Element reference = document.createElement(PnmlConstants.ELEMENT_REFERENCE_PLACE);
        reference.setAttribute(PnmlConstants.ATTR_ID, placeId);
        reference.setAttribute(PnmlConstants.ATTR_REF, targetId);

        Element name = XmlHelper.firstDirectChild(place, PnmlConstants.ELEMENT_NAME);
        if (name != null) {
            reference.appendChild(name.cloneNode(true));
        }

        Element toolspecific = createToolspecific(document,
                PnmlConstants.TOOL_VERSION_OBJECT_MODEL_CONFORMANT);
        toolspecific.appendChild(textElement(document, PnmlConstants.ELEMENT_REFERENCE_ROLE,
                PnmlConstants.ROLE_FUSION));
        for (Element block : XmlHelper.toolSpecificBlocks(place)) {
            // Coordinates and any marking parameter belong to the drawing, not to the net, so
            // they follow the node rather than the place that stops existing.
            for (Element carried : XmlHelper.directChildren(block)) {
                toolspecific.appendChild(carried.cloneNode(true));
            }
        }
        int marking = XmlHelper.parseIntSafe(
                XmlHelper.getTextContent(place, PnmlConstants.ELEMENT_INITIAL_MARKING), 0);
        if (marking > 0) {
            toolspecific.appendChild(textElement(document,
                    PnmlConstants.ELEMENT_FUSED_INITIAL_MARKING, String.valueOf(marking)));
        }
        reference.appendChild(toolspecific);

        Element graphics = XmlHelper.firstDirectChild(place, PnmlConstants.ELEMENT_GRAPHICS);
        if (graphics != null) {
            reference.appendChild(graphics.cloneNode(true));
        }

        page.replaceChild(reference, place);
    }

    /**
     * Finds or creates the stand-in a page draws for another object's element.
     *
     * <p>One stand-in per referenced element per page: two links into the same place of the
     * same object are two arcs into one node, not two nodes.
     */
    private Element representative(Document document, List<Element> pages,
                                   Map<String, Element> representatives,
                                   int page, int targetPage, String targetId, String tagName) {
        String key = page + "|" + targetId;
        Element existing = representatives.get(key);
        if (existing != null) {
            return existing;
        }

        Element reference = document.createElement(tagName);
        reference.setAttribute(PnmlConstants.ATTR_ID,
                PnmlConstants.REFERENCE_NODE_ID_PREFIX + pageId(page) + "_" + targetId);
        reference.setAttribute(PnmlConstants.ATTR_REF, targetId);

        // The stand-in is drawn with the name and position of what it stands for, so that a
        // reader without this tool still shows the user something recognisable.
        Element target = findById(pages.get(targetPage), null, targetId);
        Element name = target == null ? null : XmlHelper.firstDirectChild(target, PnmlConstants.ELEMENT_NAME);
        if (name != null) {
            reference.appendChild(name.cloneNode(true));
        }

        Element toolspecific = createToolspecific(document,
                PnmlConstants.TOOL_VERSION_OBJECT_MODEL_CONFORMANT);
        toolspecific.appendChild(textElement(document, PnmlConstants.ELEMENT_REFERENCE_ROLE,
                PnmlConstants.ROLE_REPRESENTATIVE));
        Element coordinates = target == null ? null
                : firstToolSpecificChild(target, PnmlConstants.ELEMENT_COORDINATES);
        if (coordinates != null) {
            toolspecific.appendChild(coordinates.cloneNode(true));
        }
        reference.appendChild(toolspecific);

        Element graphics = target == null ? null
                : XmlHelper.firstDirectChild(target, PnmlConstants.ELEMENT_GRAPHICS);
        if (graphics != null) {
            reference.appendChild(graphics.cloneNode(true));
        }

        // Stand-ins sit after the object's own nodes and before every arc, so that the place
        // slots a link index addresses stay exactly the object's own places.
        Element firstArc = XmlHelper.firstDirectChild(pages.get(page), PnmlConstants.ELEMENT_ARC);
        if (firstArc != null) {
            pages.get(page).insertBefore(reference, firstArc);
        } else {
            pages.get(page).appendChild(reference);
        }
        representatives.put(key, reference);
        return reference;
    }

    /**
     * Appends the arc that realises one link. It is an ordinary arc in every respect a P/T
     * net has; only the informational flag, which has no P/T form, stays tool-specific.
     */
    private void appendLinkArc(Document document, Element page, int linkIndex,
                               String sourceId, String targetId, int quantity, boolean informational) {
        Element arc = document.createElement(PnmlConstants.ELEMENT_ARC);
        arc.setAttribute(PnmlConstants.ATTR_ID, PnmlConstants.LINK_ARC_ID_PREFIX + linkIndex);
        arc.setAttribute(PnmlConstants.ATTR_SOURCE, sourceId);
        arc.setAttribute(PnmlConstants.ATTR_TARGET, targetId);

        Element inscription = document.createElement(PnmlConstants.ELEMENT_INSCRIPTION);
        inscription.appendChild(textElement(document, PnmlConstants.ELEMENT_TEXT,
                String.valueOf(Math.max(1, quantity))));
        arc.appendChild(inscription);

        if (informational) {
            Element toolspecific = createToolspecific(document, PnmlConstants.TOOL_VERSION);
            toolspecific.appendChild(textElement(document, PnmlConstants.ELEMENT_INFORMATIONAL, "true"));
            arc.appendChild(toolspecific);
        }
        page.appendChild(arc);
    }

    /**
     * Builds the net-level block listing the links between Petri-objects.
     *
     * <p>Redundant with the reference nodes by design. It is what a reader that predates them
     * uses to recover the arc-like links, it cannot rescue such a reader on a fusion, since
     * the conformant projection removes a {@code <place>}, and it records the link set as
     * the user declared it, now addressed by element id as well as by position.
     */
    private Element createLinksBlock(Document document, GraphPetriObjModel model, ElementIds ids) {
        Element toolspecific = createToolspecific(document,
                PnmlConstants.TOOL_VERSION_OBJECT_MODEL_CONFORMANT);
        Element linksElement = document.createElement(PnmlConstants.ELEMENT_PETRI_OBJECT_LINKS);
        for (PetriObjLink link : model.getLinks()) {
            Element linkElement = document.createElement(PnmlConstants.ELEMENT_LINK);
            linkElement.setAttribute(PnmlConstants.ATTR_LINK_TYPE, linkTypeName(link));
            linkElement.setAttribute(PnmlConstants.ATTR_SOURCE_OBJECT, String.valueOf(link.getSourceObject()));
            linkElement.setAttribute(PnmlConstants.ATTR_SOURCE_ELEMENT, String.valueOf(link.getSourceElement()));
            setElementId(linkElement, PnmlConstants.ATTR_SOURCE_ELEMENT_ID,
                    ids.elementId(link.getSourceObject(), link.getSourceElement(),
                            link.getType() != PetriObjLinkType.TRANSITION_TO_PLACE));
            linkElement.setAttribute(PnmlConstants.ATTR_TARGET_OBJECT, String.valueOf(link.getTargetObject()));
            linkElement.setAttribute(PnmlConstants.ATTR_TARGET_ELEMENT, String.valueOf(link.getTargetElement()));
            setElementId(linkElement, PnmlConstants.ATTR_TARGET_ELEMENT_ID,
                    ids.elementId(link.getTargetObject(), link.getTargetElement(),
                            link.getType() != PetriObjLinkType.PLACE_TO_TRANSITION));
            switch (link.getType()) {
                case PLACE_FUSION -> { /* a fusion has neither multiplicity nor a test flag */ }
                case TRANSITION_TO_PLACE ->
                        linkElement.setAttribute(PnmlConstants.ATTR_QUANTITY, String.valueOf(link.getQuantity()));
                case PLACE_TO_TRANSITION -> {
                    linkElement.setAttribute(PnmlConstants.ATTR_QUANTITY, String.valueOf(link.getQuantity()));
                    linkElement.setAttribute(PnmlConstants.ATTR_INFORMATIONAL, String.valueOf(link.isInformational()));
                }
            }
            linksElement.appendChild(linkElement);
        }
        toolspecific.appendChild(linksElement);
        return toolspecific;
    }

    private static void setElementId(Element linkElement, String attribute, String id) {
        if (XmlHelper.isNotEmpty(id)) {
            linkElement.setAttribute(attribute, id);
        }
    }

    /**
     * @return the stable document name of a link type, independent of the enum constant name
     */
    static String linkTypeName(PetriObjLink link) {
        return switch (link.getType()) {
            case PLACE_FUSION -> PnmlConstants.LINK_TYPE_PLACE_FUSION;
            case TRANSITION_TO_PLACE -> PnmlConstants.LINK_TYPE_TRANSITION_TO_PLACE;
            case PLACE_TO_TRANSITION -> PnmlConstants.LINK_TYPE_PLACE_TO_TRANSITION;
        };
    }

    private Element createToolspecific(Document document, String version) {
        Element toolspecific = document.createElement(PnmlConstants.ELEMENT_TOOLSPECIFIC);
        toolspecific.setAttribute(PnmlConstants.ATTR_TOOL, PnmlConstants.TOOL_PETRI_OBJ_MODEL);
        toolspecific.setAttribute(PnmlConstants.ATTR_VERSION, version);
        return toolspecific;
    }

    private Element createNameElement(Document document, String name) {
        Element nameElement = document.createElement(PnmlConstants.ELEMENT_NAME);
        nameElement.appendChild(textElement(document, PnmlConstants.ELEMENT_TEXT, name));
        return nameElement;
    }

    private static Element textElement(Document document, String tagName, String text) {
        Element element = document.createElement(tagName);
        element.setTextContent(text);
        return element;
    }

    /**
     * @param tagName tag the element must have, or {@code null} for any
     * @return the direct child of the page with that id, or {@code null}
     */
    private static Element findById(Element page, String tagName, String id) {
        for (Element child : XmlHelper.directChildren(page)) {
            if ((tagName == null || tagName.equals(child.getTagName()))
                    && id.equals(child.getAttribute(PnmlConstants.ATTR_ID))) {
                return child;
            }
        }
        return null;
    }

    private static Element firstToolSpecificChild(Element element, String tagName) {
        for (Element block : XmlHelper.toolSpecificBlocks(element)) {
            Element child = XmlHelper.firstDirectChild(block, tagName);
            if (child != null) {
                return child;
            }
        }
        return null;
    }

    /**
     * The element ids of a document, and the one rule that decides how they are written.
     *
     * <p>Ids are emitted verbatim, which keeps a document byte-stable across re-export and
     * keeps simulation results keyed by the ids the canvas already uses. That only works
     * while ids are unique across the whole document, and they need not be: two objects
     * loaded from different files can carry the same id. So the check is whole-document and
     * the answer is all-or-nothing, if any id occurs in more than one object, every id in
     * the document is namespaced by its object. Re-namespacing an already namespaced id is a
     * no-op, so exporting twice is stable.
     */
    private static final class ElementIds {

        private final List<List<String>> placeIds;
        private final List<List<String>> transitionIds;

        private ElementIds(List<List<String>> placeIds, List<List<String>> transitionIds) {
            this.placeIds = placeIds;
            this.transitionIds = transitionIds;
        }

        /**
         * @param pages the document's pages, whose element ids are rewritten in place when
         *        the document needs namespacing
         * @throws Exception if one object carries the same id twice, which no rule can repair
         *        , the arcs of that page would be ambiguous
         */
        private static ElementIds of(List<Element> pages) throws Exception {
            List<List<Element>> perPage = new ArrayList<>(pages.size());
            Set<String> seen = new HashSet<>();
            boolean namespace = false;
            for (Element page : pages) {
                List<Element> elements = new ArrayList<>();
                Set<String> onPage = new HashSet<>();
                for (Element child : XmlHelper.directChildren(page)) {
                    if (!isIdentifiedElement(child)) {
                        continue;
                    }
                    String id = child.getAttribute(PnmlConstants.ATTR_ID);
                    if (!onPage.add(id)) {
                        throw new Exception(String.format(PnmlConstants.ERROR_DUPLICATE_ID, id));
                    }
                    elements.add(child);
                    namespace |= !seen.add(id);
                }
                perPage.add(elements);
            }
            if (namespace) {
                for (int page = 0; page < perPage.size(); page++) {
                    applyNamespace(pages.get(page), perPage.get(page), page);
                }
            }

            List<List<String>> placeIds = new ArrayList<>(pages.size());
            List<List<String>> transitionIds = new ArrayList<>(pages.size());
            for (List<Element> elements : perPage) {
                List<String> places = new ArrayList<>();
                List<String> transitions = new ArrayList<>();
                for (Element element : elements) {
                    if (PnmlConstants.ELEMENT_PLACE.equals(element.getTagName())) {
                        places.add(element.getAttribute(PnmlConstants.ATTR_ID));
                    } else if (PnmlConstants.ELEMENT_TRANSITION.equals(element.getTagName())) {
                        transitions.add(element.getAttribute(PnmlConstants.ATTR_ID));
                    }
                }
                placeIds.add(places);
                transitionIds.add(transitions);
            }
            return new ElementIds(placeIds, transitionIds);
        }

        private static boolean isIdentifiedElement(Element element) {
            return switch (element.getTagName()) {
                case PnmlConstants.ELEMENT_PLACE, PnmlConstants.ELEMENT_TRANSITION,
                        PnmlConstants.ELEMENT_ARC -> true;
                default -> false;
            };
        }

        /** Rewrites one page's ids and the arc endpoints that name them. */
        private static void applyNamespace(Element page, List<Element> elements, int index) {
            Map<String, String> renamed = new HashMap<>();
            for (Element element : elements) {
                String id = element.getAttribute(PnmlConstants.ATTR_ID);
                String namespaced = String.format(PnmlConstants.OBJECT_ID_NAMESPACE_FORMAT, index,
                        id.replaceFirst(PnmlConstants.OBJECT_ID_NAMESPACE_PATTERN, ""));
                renamed.put(id, namespaced);
                element.setAttribute(PnmlConstants.ATTR_ID, namespaced);
            }
            for (Element arc : XmlHelper.directChildren(page, PnmlConstants.ELEMENT_ARC)) {
                renameEndpoint(arc, PnmlConstants.ATTR_SOURCE, renamed);
                renameEndpoint(arc, PnmlConstants.ATTR_TARGET, renamed);
            }
        }

        private static void renameEndpoint(Element arc, String attribute, Map<String, String> renamed) {
            String namespaced = renamed.get(arc.getAttribute(attribute));
            if (namespaced != null) {
                arc.setAttribute(attribute, namespaced);
            }
        }

        /**
         * @param object index of the Petri-object
         * @param element index of the element inside that object's net
         * @param place whether the index addresses a place rather than a transition
         * @return the id the document gives that element, or {@code null} when the object has
         *         no such element
         */
        private String elementId(int object, int element, boolean place) {
            List<List<String>> ids = place ? placeIds : transitionIds;
            if (object < 0 || object >= ids.size() || element < 0 || element >= ids.get(object).size()) {
                return null;
            }
            return ids.get(object).get(element);
        }
    }

    /**
     * Checks the document against the invariants a PNML reader is entitled to assume and a
     * RELAX NG schema cannot state, most importantly that an arc never leaves its page,
     * which the schema types as a plain IDREF and therefore cannot see.
     *
     * <p>Cheap, and it is what keeps a regression here from reaching a reader as a model that
     * quietly means something else.
     *
     * @throws Exception naming the first violation
     */
    private static void assertWriterInvariants(Document document) throws Exception {
        Element netElement = PnmlParser.findNetElement(document);
        List<Element> pages = XmlHelper.directChildren(netElement, PnmlConstants.ELEMENT_PAGE);
        if (pages.isEmpty()) {
            throw new Exception(PnmlConstants.ERROR_NO_OBJECTS);
        }

        Set<String> allIds = new HashSet<>();
        Map<String, String> referenceTargets = new HashMap<>();
        for (Element page : pages) {
            if (!XmlHelper.directChildren(page, PnmlConstants.ELEMENT_PAGE).isEmpty()) {
                throw new Exception("Page " + page.getAttribute(PnmlConstants.ATTR_ID)
                        + " contains a page: nested pages are not written");
            }
            Set<String> nodesOnPage = new HashSet<>();
            for (Element child : XmlHelper.directChildren(page)) {
                String id = child.getAttribute(PnmlConstants.ATTR_ID);
                boolean reference = PnmlConstants.ELEMENT_REFERENCE_PLACE.equals(child.getTagName())
                        || PnmlConstants.ELEMENT_REFERENCE_TRANSITION.equals(child.getTagName());
                boolean node = reference
                        || PnmlConstants.ELEMENT_PLACE.equals(child.getTagName())
                        || PnmlConstants.ELEMENT_TRANSITION.equals(child.getTagName());
                if (!node && !PnmlConstants.ELEMENT_ARC.equals(child.getTagName())) {
                    continue;
                }
                if (!allIds.add(id)) {
                    throw new Exception(String.format(PnmlConstants.ERROR_DUPLICATE_ID, id));
                }
                if (node) {
                    nodesOnPage.add(id);
                }
                if (reference) {
                    if (child.getElementsByTagName(PnmlConstants.ELEMENT_INITIAL_MARKING).getLength() > 0) {
                        throw new Exception("Reference node " + id + " carries an initial marking, "
                                + "which would double the tokens of the node it stands for");
                    }
                    referenceTargets.put(id, child.getAttribute(PnmlConstants.ATTR_REF));
                }
            }
            for (Element arc : XmlHelper.directChildren(page, PnmlConstants.ELEMENT_ARC)) {
                String source = arc.getAttribute(PnmlConstants.ATTR_SOURCE);
                String target = arc.getAttribute(PnmlConstants.ATTR_TARGET);
                if (!nodesOnPage.contains(source) || !nodesOnPage.contains(target)) {
                    throw new Exception("Arc " + arc.getAttribute(PnmlConstants.ATTR_ID)
                            + " runs from " + source + " to " + target
                            + ", which are not both on page " + page.getAttribute(PnmlConstants.ATTR_ID));
                }
                // A weight below one is not a P/T arc at all; writing it out would hand a
                // reader a document that cannot mean anything, so the net is fixed instead.
                int weight = XmlHelper.parseIntSafe(
                        XmlHelper.getTextContent(arc, PnmlConstants.ELEMENT_INSCRIPTION), 1);
                if (weight < 1) {
                    throw new Exception("Arc " + arc.getAttribute(PnmlConstants.ATTR_ID)
                            + " has multiplicity " + weight + "; a Petri net arc moves at least one token");
                }
            }
        }

        for (String id : referenceTargets.keySet()) {
            String current = id;
            for (int depth = 0; ; depth++) {
                String next = referenceTargets.get(current);
                if (next == null) {
                    if (!allIds.contains(current)) {
                        throw new Exception(String.format(
                                PnmlConstants.ERROR_DANGLING_REFERENCE, id, current));
                    }
                    break;
                }
                if (next.equals(id) || depth > PnmlConstants.MAX_REFERENCE_DEPTH) {
                    throw new Exception(String.format(PnmlConstants.ERROR_REFERENCE_CYCLE, id));
                }
                current = next;
            }
        }
    }
}

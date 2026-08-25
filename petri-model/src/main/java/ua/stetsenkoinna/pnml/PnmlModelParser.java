package ua.stetsenkoinna.pnml;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
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
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Reads a PNML document as a composed {@link GraphPetriObjModel}.
 *
 * <p>Each {@code <page>} of the document's net becomes one Petri-object, wherever in the net
 * it sits. A document written before Petri-object composition existed, a single page, or no
 * page at all, and no link block, reads back as a model of one object, so every net saved so
 * far keeps opening.
 *
 * <h2>The page structure is the hierarchy</h2>
 *
 * <p>Which object encloses which is read from the document's own structure and from nowhere
 * else: a page written inside another page is the child of that page's object, the way
 * ISO/IEC 15909-2 states a page hierarchy, and a page directly under the net belongs to
 * nobody. A document whose pages are flat siblings therefore reads as a set of top-level
 * objects. That is also what a document written before the pages were nested reads as: it
 * carries its hierarchy in a tool-specific attribute that is no longer written and no longer
 * read, so its objects open flattened.
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

    private final List<String> warnings = new ArrayList<>();

    /**
     * Warnings collected while reading the most recent document: an id that was not a valid
     * XML id and was imported under a different one, a value that did not parse as the number
     * it named, a duplicate id in a legacy document, a declared link that disagreed with or
     * could not be bound to the document's structure. Parsing continues past all of them; this
     * is what lets a caller show them to a user afterwards instead.
     *
     * @return the warnings, in document order, empty when the document raised none
     */
    public List<String> getWarnings() {
        return Collections.unmodifiableList(warnings);
    }

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
        // A parser instance may be reused across documents; getWarnings() promises the
        // warnings of the most recent one, not an accumulation across every call.
        warnings.clear();
        // Before anything else reads an id out of the document, so every id below is already
        // a valid NCName, whether the document supplied one or this fixed it in place.
        warnings.addAll(XmlHelper.sanitizeIds(document));

        Element netElement = PnmlParser.findNetElement(document);

        String modelName = XmlHelper.getDirectTextContent(netElement, PnmlConstants.ELEMENT_NAME);
        if (!XmlHelper.isNotEmpty(modelName)) {
            modelName = netElement.getAttribute(PnmlConstants.ATTR_ID);
        }
        GraphPetriObjModel model = new GraphPetriObjModel(modelName);

        List<Element> pages = orderedPages(netElement, warnings);
        // The reference nodes decide which reader runs, and they are built before any page is
        // read because a page reader needs to know which of its arcs are really links.
        ReferenceNodeIndex references = ReferenceNodeIndex.isConformant(document)
                ? new ReferenceNodeIndex(netElement, pages, warnings)
                : null;
        if (references == null) {
            warnAboutDuplicateIds(pages, warnings);
        }

        if (pages.isEmpty()) {
            // A document whose elements sit directly under <net>: one Petri-object.
            model.addObject(readObject(netElement, netElement, modelName, 0, -1,
                    references == null ? null : references.pageReferences(0)));
        } else {
            int[] enclosing = enclosingObjects(pages);
            for (int index = 0; index < pages.size(); index++) {
                Element page = pages.get(index);
                model.addObject(readObject(page, page,
                        defaultObjectName(modelName, pages.size(), index), index, enclosing[index],
                        references == null ? null : references.pageReferences(index)));
            }
        }

        for (PetriObjLink link : readModelLinks(netElement, references, warnings)) {
            try {
                model.addLink(link);
            } catch (IllegalArgumentException invalid) {
                log.warn("Ignoring link that does not fit the parsed model: {}", invalid.getMessage());
                warnings.add(String.format(PnmlConstants.WARNING_LINK_UNBOUND, invalid.getMessage()));
            }
        }
        model.getGroups().addAll(readGroups(netElement, model.getObjectCount(), warnings));
        return model;
    }

    /**
     * Reads the record of which objects were stamped together as a group.
     *
     * <p>Every kind of damage is answered by dropping the group and saying so, never by refusing
     * the document. A group carries no semantics - the pages describe the same model with or
     * without it - so refusing to open a file over an unreadable grouping would trade something
     * that matters for something that does not.
     */
    private static List<ua.stetsenkoinna.graphnet.PetriObjectGroupRef> readGroups(
            Element netElement, int objectCount, List<String> warnings) {
        List<ua.stetsenkoinna.graphnet.PetriObjectGroupRef> groups = new ArrayList<>();
        for (Element block : XmlHelper.toolSpecificBlocks(netElement)) {
            Element groupsElement = XmlHelper.firstDirectChild(
                    block, PnmlConstants.ELEMENT_PETRI_OBJECT_GROUPS);
            if (groupsElement == null) {
                continue;
            }
            for (Element element
                    : XmlHelper.directChildren(groupsElement, PnmlConstants.ELEMENT_GROUP)) {
                String name = element.getAttribute(PnmlConstants.ATTR_NAME);
                List<Integer> members = new ArrayList<>();
                boolean sound = true;
                for (String token
                        : element.getAttribute(PnmlConstants.ATTR_MEMBERS).trim().split("\s+")) {
                    if (token.isEmpty()) {
                        continue;
                    }
                    try {
                        int index = Integer.parseInt(token);
                        if (index < 0 || index >= objectCount) {
                            sound = false;
                            break;
                        }
                        members.add(index);
                    } catch (NumberFormatException notANumber) {
                        sound = false;
                        break;
                    }
                }
                if (!sound || members.size() < 2) {
                    warnings.add("Dropped the Petri-object group '" + name
                            + "': it does not name at least two objects of this document");
                    continue;
                }
                String template = element.hasAttribute(PnmlConstants.ATTR_TEMPLATE_METHOD)
                        ? element.getAttribute(PnmlConstants.ATTR_TEMPLATE_METHOD)
                        : null;
                groups.add(new ua.stetsenkoinna.graphnet.PetriObjectGroupRef(
                        name, members, template));
            }
        }
        return groups;
    }

    /**
     * The document's pages, one per Petri-object, in the order the object indices run.
     *
     * <p>Pages are collected from the whole net subtree, not only from its direct children:
     * a child object's page is written inside its parent's, which is how ISO/IEC 15909-2
     * states a page hierarchy, and a reader that looked at the direct children alone would
     * see only the top-level objects.
     *
     * <p>Nesting also parts document order from object index: a child of the first object is
     * written before a second top-level object, whatever index it carries. So the index each
     * page states is what orders them, and it is used only when the document states one for
     * every page and the indices are exactly the object indices of the model, {@code 0} to
     * {@code n - 1}.
     *
     * <p>A document that states anything else is answered by what its own shape allows. One
     * with no nesting is read in document order, which is all a foreign document, and any
     * document written before object metadata existed, can offer, and where nothing nests
     * that order is the object order. A nested one is refused instead: there the two have
     * parted, and the links address objects by the index, so reading on would bind every one
     * of them to a different object than the document names.
     *
     * @throws Exception when a nested document does not state a usable index on every page
     */
    private static List<Element> orderedPages(Element netElement, List<String> warnings) throws Exception {
        List<Element> pages = XmlHelper.descendantPages(netElement);
        Element[] byIndex = new Element[pages.size()];
        for (Element page : pages) {
            Element objectElement = findObjectElement(page);
            int index = objectElement == null ? -1 : XmlHelper.parseIntSafe(
                    objectElement.getAttribute(PnmlConstants.ATTR_INDEX), -1, warnings,
                    "page '" + page.getAttribute(PnmlConstants.ATTR_ID) + "'", PnmlConstants.ATTR_INDEX);
            if (index < 0 || index >= byIndex.length || byIndex[index] != null) {
                if (isNested(pages)) {
                    // Document order is not object order once pages nest: a child of object 0
                    // is written before a second top-level object. Links address objects by
                    // the stated index, so falling back here would quietly bind every one of
                    // them to a different object than the document names.
                    throw new Exception(String.format(
                            PnmlConstants.ERROR_UNUSABLE_PAGE_INDEX, pages.size() - 1));
                }
                return pages;
            }
            byIndex[index] = page;
        }
        return List.of(byIndex);
    }

    /** Whether any page of the document sits inside another page. */
    private static boolean isNested(List<Element> pages) {
        for (Element page : pages) {
            Node parent = page.getParentNode();
            if (parent instanceof Element enclosing
                    && PnmlConstants.ELEMENT_PAGE.equals(enclosing.getTagName())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Works out what encloses each page.
     *
     * @param pages the document's pages, in object index order
     * @return for every page, the index of the object whose page encloses it, or {@code -1}
     *         when it sits directly under the net
     */
    private static int[] enclosingObjects(List<Element> pages) {
        Map<Element, Integer> indexOfPage = new IdentityHashMap<>();
        for (int index = 0; index < pages.size(); index++) {
            indexOfPage.put(pages.get(index), index);
        }
        int[] enclosing = new int[pages.size()];
        for (int index = 0; index < pages.size(); index++) {
            Node parent = pages.get(index).getParentNode();
            enclosing[index] = parent instanceof Element page
                    && PnmlConstants.ELEMENT_PAGE.equals(page.getTagName())
                    ? indexOfPage.getOrDefault(page, -1)
                    : -1;
        }
        return enclosing;
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
     *       also states adds nothing, and where they differ on multiplicity the structure
     *       wins, because that is what every other reader of the document sees. A declaration
     *       the structure does not state is applied: that is how a legacy-style declaration
     *       inside a conformant document survives.</li>
     * </ul>
     *
     * @throws Exception if the document declares a link of a retired type
     */
    private static List<PetriObjLink> readModelLinks(Element netElement, ReferenceNodeIndex references,
                                                     List<String> warnings) throws Exception {
        List<PetriObjLink> declared = readLinks(netElement, references, warnings);
        if (references == null) {
            return declared;
        }
        return mergeDeclaredLinks(references.structuralLinks(), declared, warnings);
    }

    /**
     * Merges the declared links into the structural ones under the rules described in
     * {@link #readModelLinks}.
     */
    private static List<PetriObjLink> mergeDeclaredLinks(List<PetriObjLink> structural,
                                                         List<PetriObjLink> declared, List<String> warnings) {
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
            } else if (structured.getQuantity() != link.getQuantity()) {
                log.warn("Link {} disagrees with the declaration {}; the document's structure wins",
                        structured, link);
                warnings.add(String.format(PnmlConstants.WARNING_LINK_DISAGREES_WITH_STRUCTURE,
                        structured, link));
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
    private static void warnAboutDuplicateIds(List<Element> pages, List<String> warnings) {
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
                            warnings.add(String.format(PnmlConstants.WARNING_DUPLICATE_LEGACY_ID, id));
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
     * @param enclosing index of the object whose page encloses this one, or {@code -1}
     * @param references what this page's reference nodes mean, or {@code null} for a legacy
     *        document
     */
    private GraphPetriObject readObject(Element scope, Element metadataScope,
                                        String fallbackName, int index, int enclosing,
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
        // Ids in this page are already valid: buildModel sanitized the whole document before
        // any page was read. What the page reader still finds are the numeric and structural
        // warnings only it can see, and they belong on this model's own list.
        warnings.addAll(parser.getWarnings());

        // A page with our own petriObject metadata carries the user's exact canvas
        // coordinates; normalizing them to the (50,50) corner is a defense that only
        // foreign documents need, and applying it to our own made every reimported net
        // drift and pile up.
        boolean ownDocument = objectElement != null;
        GraphPetriObject object = new GraphPetriObject(name,
                GraphNetBuilder.build(net, parser.getAllPlaceCoordinates(),
                        parser.getAllTransitionCoordinates(), null, !ownDocument));
        object.setAbsoluteLayout(ownDocument);

        if (objectElement != null) {
            String description = "Petri-object '" + name + "'";
            object.setPriority(XmlHelper.parseIntSafe(
                    objectElement.getAttribute(PnmlConstants.ATTR_PRIORITY), 0,
                    warnings, description, PnmlConstants.ATTR_PRIORITY));
            object.setPosition(new Point(
                    XmlHelper.parseIntSafe(objectElement.getAttribute(PnmlConstants.ATTR_X), 0,
                            warnings, description, PnmlConstants.ATTR_X),
                    XmlHelper.parseIntSafe(objectElement.getAttribute(PnmlConstants.ATTR_Y), 0,
                            warnings, description, PnmlConstants.ATTR_Y)));
            object.setSize(
                    XmlHelper.parseIntSafe(objectElement.getAttribute(PnmlConstants.ATTR_WIDTH), 0,
                            warnings, description, PnmlConstants.ATTR_WIDTH),
                    XmlHelper.parseIntSafe(objectElement.getAttribute(PnmlConstants.ATTR_HEIGHT), 0,
                            warnings, description, PnmlConstants.ATTR_HEIGHT));
            object.setCollapsed(
                    Boolean.parseBoolean(objectElement.getAttribute(PnmlConstants.ATTR_COLLAPSED)));
        }
        // Where the page sits is the whole answer. Nothing a page says about itself can add
        // to it or contradict it, so no page can be given a parent that its position denies.
        object.setParentIndex(enclosing);
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
     * <p>The block is matched on its tool alone, never on its version: {@code "2.0"},
     * {@code "2.1"} and the release strings written since are all current, and a version
     * filter here would drop the links of every document written by a build newer than this
     * one. Which tool name is matched is {@link XmlHelper#toolSpecificBlocks}'s single rule.
     *
     * @param netElement the document's net
     * @param references the document's reference nodes, or {@code null} for a legacy
     *        document; when present, an endpoint given by id is resolved through it
     * @throws Exception if the block declares a link of a retired type
     */
    private static List<PetriObjLink> readLinks(Element netElement, ReferenceNodeIndex references,
                                                List<String> warnings) throws Exception {
        List<PetriObjLink> links = new ArrayList<>();
        for (Element toolspecific : XmlHelper.toolSpecificBlocks(netElement)) {
            Element linksElement =
                    XmlHelper.firstDirectChild(toolspecific, PnmlConstants.ELEMENT_PETRI_OBJECT_LINKS);
            if (linksElement == null) {
                continue;
            }
            for (Element linkElement : XmlHelper.directChildren(linksElement, PnmlConstants.ELEMENT_LINK)) {
                PetriObjLink link = readLink(linkElement, references, warnings);
                if (link != null) {
                    links.add(link);
                }
            }
        }
        return links;
    }

    /**
     * Reads one link declaration.
     *
     * @return the link, or {@code null} when the declaration is of an unknown type or is
     *         malformed, both of which the reader steps over
     * @throws Exception if the declaration is of a retired type, which is a statement about
     *         the model the reader must not quietly drop or quietly rewrite
     */
    private static PetriObjLink readLink(Element element, ReferenceNodeIndex references, List<String> warnings)
            throws Exception {
        // Read first, so the numeric fields below have something more specific than "link" to
        // name in a warning about themselves.
        String type = element.getAttribute(PnmlConstants.ATTR_LINK_TYPE);
        String description = "link '" + type + "'";

        int sourceObject = XmlHelper.parseIntSafe(element.getAttribute(PnmlConstants.ATTR_SOURCE_OBJECT), -1,
                warnings, description, PnmlConstants.ATTR_SOURCE_OBJECT);
        int sourceElement = XmlHelper.parseIntSafe(element.getAttribute(PnmlConstants.ATTR_SOURCE_ELEMENT), -1,
                warnings, description, PnmlConstants.ATTR_SOURCE_ELEMENT);
        int targetObject = XmlHelper.parseIntSafe(element.getAttribute(PnmlConstants.ATTR_TARGET_OBJECT), -1,
                warnings, description, PnmlConstants.ATTR_TARGET_OBJECT);
        int targetElement = XmlHelper.parseIntSafe(element.getAttribute(PnmlConstants.ATTR_TARGET_ELEMENT), -1,
                warnings, description, PnmlConstants.ATTR_TARGET_ELEMENT);

        // An id says which element the user meant even if the document's elements are
        // numbered differently than when the declaration was written, so it wins.
        int[] source = slotOf(references, element.getAttribute(PnmlConstants.ATTR_SOURCE_ELEMENT_ID), warnings);
        if (source != null) {
            sourceObject = source[0];
            sourceElement = source[1];
        }
        int[] target = slotOf(references, element.getAttribute(PnmlConstants.ATTR_TARGET_ELEMENT_ID), warnings);
        if (target != null) {
            targetObject = target[0];
            targetElement = target[1];
        }

        int quantity = XmlHelper.parseIntSafe(element.getAttribute(PnmlConstants.ATTR_QUANTITY), 1,
                warnings, description, PnmlConstants.ATTR_QUANTITY);

        // Refused before anything else is done with it: this declaration says the model has a
        // shape the technique no longer has, and there is no reading of it that is both silent
        // and honest.
        if (PnmlConstants.LINK_TYPE_PLACE_TO_TRANSITION.equals(type)) {
            throw new Exception(String.format(PnmlConstants.ERROR_RETIRED_LINK_TYPE_DECLARED,
                    type, sourceElement, sourceObject, targetElement, targetObject));
        }

        try {
            return switch (type) {
                case PnmlConstants.LINK_TYPE_PLACE_FUSION ->
                        PetriObjLink.placeFusion(sourceObject, sourceElement, targetObject, targetElement);
                case PnmlConstants.LINK_TYPE_TRANSITION_TO_PLACE ->
                        PetriObjLink.transitionToPlace(sourceObject, sourceElement, targetObject,
                                targetElement, quantity);
                default -> {
                    log.warn("Ignoring link of unknown type '{}'", type);
                    warnings.add(String.format(PnmlConstants.WARNING_LINK_UNKNOWN_TYPE, type));
                    yield null;
                }
            };
        } catch (IllegalArgumentException malformed) {
            log.warn("Ignoring malformed link: {}", malformed.getMessage());
            warnings.add(String.format(PnmlConstants.WARNING_LINK_MALFORMED, malformed.getMessage()));
            return null;
        }
    }

    /**
     * @return the object and element index the given id addresses, or {@code null} when the
     *         document has no such element or does not use ids at all
     */
    private static int[] slotOf(ReferenceNodeIndex references, String elementId, List<String> warnings) {
        if (references == null || !XmlHelper.isNotEmpty(elementId)) {
            return null;
        }
        int[] slot = references.slotOf(elementId);
        if (slot == null) {
            log.warn("Link declaration names element '{}', which the document does not contain",
                    elementId);
            warnings.add(String.format(PnmlConstants.WARNING_LINK_UNKNOWN_ELEMENT_ID, elementId));
        }
        return slot;
    }
}

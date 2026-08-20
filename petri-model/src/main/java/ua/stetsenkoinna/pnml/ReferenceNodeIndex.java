package ua.stetsenkoinna.pnml;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import ua.stetsenkoinna.petriobj.PetriObjLink;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Reads the inter-object structure of a PNML document out of its reference nodes.
 *
 * <p>ISO/IEC 15909-2 says a node shared between two pages is drawn on one of them as a
 * {@code <referencePlace>} or {@code <referenceTransition>} pointing at the node it stands
 * for. That is the only part of a composed model a reader which is not this tool can see:
 * the {@code <petriObjectLinks>} block is tool-specific, so a document whose links live
 * only there means something else to everyone else, a set of unrelated nets.
 *
 * <p>A document is <em>conformant</em> as soon as it carries one such node, and this index
 * is then the authority on what the links are. A document without them is
 * <em>legacy</em>: everything written before this change, which still has to parse into
 * exactly the same model, so the reader never builds this index for one.
 *
 * <p>Two things a reference node can mean, and only the tool-specific
 * {@link PnmlConstants#ELEMENT_REFERENCE_ROLE} tells them apart, because flattening the
 * document deliberately erases the difference:
 * <ul>
 *   <li>{@link PnmlConstants#ROLE_FUSION}, the object has no place of its own in that
 *       slot; the place it shows belongs to another object. The node therefore
 *       <em>occupies a place slot</em> and keeps the ordinal the replaced place had, which
 *       is what stops every link index of the document from shifting by one.</li>
 *   <li>{@link PnmlConstants#ROLE_REPRESENTATIVE}, the object draws a stand-in so that an
 *       arc to another object's element has something to attach to. It is not a slot, takes
 *       no ordinal, and neither it nor its arcs are part of the object's own net. Only an arc
 *       running from a transition into a place is a link this way; the other direction would
 *       give a transition an input place it does not own and is refused.</li>
 * </ul>
 *
 * <p>A foreign document carries no role, so one is inferred: a reference node no arc on its
 * page touches can only be there to be shared, which is a fusion; one with arcs is a
 * stand-in for them.
 *
 * @see PnmlModelParser
 */
final class ReferenceNodeIndex {

    private static final Logger log = LoggerFactory.getLogger(ReferenceNodeIndex.class);

    /**
     * What the reader of one page needs to know about the reference nodes drawn on it.
     *
     * @param placeSlots the page's place slots in document order, its own {@code <place>}
     *        elements with the fusion {@code <referencePlace>} elements interleaved at the
     *        position of the place each one replaces
     * @param linkArcIds ids of the arcs that realise a link and are therefore not arcs of
     *        the object's own net
     */
    record PageReferences(List<Element> placeSlots, Set<String> linkArcIds) {
    }

    /** One place, transition or reference node of the document, with where it sits. */
    private static final class Node {
        private final String id;
        private final int page;
        private final boolean placeKind;
        private final boolean reference;
        private final boolean fusion;
        private final String ref;
        /** Slot ordinal inside the page, or -1 for a representative, which is not a slot. */
        private int ordinal = -1;

        private Node(String id, int page, boolean placeKind, boolean reference,
                     boolean fusion, String ref) {
            this.id = id;
            this.page = page;
            this.placeKind = placeKind;
            this.reference = reference;
            this.fusion = fusion;
            this.ref = ref;
        }
    }

    private final Map<String, Node> byId = new HashMap<>();
    private final List<Element> scopes;
    private final List<PageReferences> pageReferences = new ArrayList<>();
    private final List<Element> referenceNodes = new ArrayList<>();
    private final Map<Element, Node> nodeOfElement = new HashMap<>();
    private final List<PetriObjLink> structural = new ArrayList<>();
    private final List<String> warnings;

    /**
     * @param document the document to look at
     * @return {@code true} if it uses reference nodes at all, i.e. is of the conformant
     *         dialect
     */
    static boolean isConformant(Document document) {
        return document.getElementsByTagName(PnmlConstants.ELEMENT_REFERENCE_PLACE).getLength() > 0
                || document.getElementsByTagName(PnmlConstants.ELEMENT_REFERENCE_TRANSITION).getLength() > 0;
    }

    /**
     * Indexes every place, transition and reference node of the document and works out the
     * slot ordinals every link index is expressed in.
     *
     * @param netElement the document's net
     * @param pages every page of the net, nested ones included, in the order the object
     *        indices run; empty for a document whose elements sit directly under the net.
     *        A page nested inside another is a Petri-object like any other and is indexed
     *        against its own direct children, never against the page enclosing it.
     * @param warnings sink for numeric text that named a link arc's multiplicity but did not
     *        parse as one
     * @throws Exception if two elements share an id, which would make a {@code ref}
     *         ambiguous, or if a reference chain does not end at a real node
     */
    ReferenceNodeIndex(Element netElement, List<Element> pages, List<String> warnings) throws Exception {
        this.scopes = pages.isEmpty() ? List.of(netElement) : pages;
        this.warnings = warnings;
        for (int page = 0; page < scopes.size(); page++) {
            indexPage(scopes.get(page), page);
        }
        // Reading the links also marks which arcs realise them, and a page reader needs that
        // before it reads anything, so it happens here rather than on first ask.
        for (Element reference : referenceNodes) {
            Node node = nodeOfElement.get(reference);
            Node target = resolve(node);
            if (node.fusion) {
                addFusion(node, target);
            } else {
                addArcLinks(node, target);
            }
        }
    }

    /**
     * Indexes one page: which of its nodes are slots, in which order, and which of its arcs
     * exist only to realise a link.
     */
    private void indexPage(Element scope, int page) throws Exception {
        List<Element> arcs = XmlHelper.directChildren(scope, PnmlConstants.ELEMENT_ARC);
        Set<String> touchedByArcs = new HashSet<>();
        for (Element arc : arcs) {
            touchedByArcs.add(arc.getAttribute(PnmlConstants.ATTR_SOURCE));
            touchedByArcs.add(arc.getAttribute(PnmlConstants.ATTR_TARGET));
        }

        List<Element> placeSlots = new ArrayList<>();
        int placeOrdinal = 0;
        int transitionOrdinal = 0;
        // Document order is what defines the ordinals, so the page is walked once, in it.
        for (Element child : XmlHelper.directChildren(scope)) {
            String id = child.getAttribute(PnmlConstants.ATTR_ID);
            Node node = switch (child.getTagName()) {
                case PnmlConstants.ELEMENT_PLACE -> new Node(id, page, true, false, false, null);
                case PnmlConstants.ELEMENT_TRANSITION -> new Node(id, page, false, false, false, null);
                case PnmlConstants.ELEMENT_REFERENCE_PLACE -> new Node(id, page, true, true,
                        isFusion(child, touchedByArcs), child.getAttribute(PnmlConstants.ATTR_REF));
                // There is no transition-fusion link, so a reference transition can only ever
                // be a stand-in; inferring a role for it would produce a nonsensical link.
                case PnmlConstants.ELEMENT_REFERENCE_TRANSITION -> new Node(id, page, false, true,
                        false, child.getAttribute(PnmlConstants.ATTR_REF));
                default -> null;
            };
            if (node == null) {
                continue;
            }
            if (byId.putIfAbsent(id, node) != null) {
                throw new Exception(String.format(PnmlConstants.ERROR_DUPLICATE_ID, id));
            }
            if (node.reference) {
                referenceNodes.add(child);
                nodeOfElement.put(child, node);
            }
            if (node.placeKind && (!node.reference || node.fusion)) {
                node.ordinal = placeOrdinal++;
                placeSlots.add(child);
            } else if (!node.placeKind && !node.reference) {
                node.ordinal = transitionOrdinal++;
            }
        }
        pageReferences.add(new PageReferences(placeSlots, new LinkedHashSet<>()));
    }

    /**
     * A document this tool wrote says the role outright. A foreign one does not, and then
     * the arcs give it away: a reference node the page never draws an arc to is there only
     * to be shared with the page that owns it.
     */
    private static boolean isFusion(Element reference, Set<String> touchedByArcs) {
        String role = XmlHelper.getToolSpecificText(reference, PnmlConstants.ELEMENT_REFERENCE_ROLE);
        if (XmlHelper.isNotEmpty(role)) {
            return PnmlConstants.ROLE_FUSION.equalsIgnoreCase(role);
        }
        return !touchedByArcs.contains(reference.getAttribute(PnmlConstants.ATTR_ID));
    }

    /**
     * Follows a reference through however many reference nodes stand between it and a real
     * element.
     *
     * @throws Exception if the chain leaves the net or comes back to where it started
     */
    private Node resolve(Node start) throws Exception {
        Node current = start;
        Set<String> visited = new HashSet<>();
        for (int depth = 0; depth <= PnmlConstants.MAX_REFERENCE_DEPTH; depth++) {
            if (!current.reference) {
                return current;
            }
            if (!visited.add(current.id)) {
                throw new Exception(String.format(PnmlConstants.ERROR_REFERENCE_CYCLE, start.id));
            }
            Node next = byId.get(current.ref);
            if (next == null) {
                throw new Exception(String.format(
                        PnmlConstants.ERROR_DANGLING_REFERENCE, current.id, current.ref));
            }
            current = next;
        }
        throw new Exception(String.format(PnmlConstants.ERROR_REFERENCE_CYCLE, start.id));
    }

    /**
     * @param page index of the page
     * @return what the reader of that page needs to know about its reference nodes
     */
    PageReferences pageReferences(int page) {
        return pageReferences.get(page);
    }

    /**
     * The links the document's structure states, the complete set, since a reader that does
     * not know this tool sees exactly these and nothing else.
     *
     * @return the links, in document order: page by page, and within a page in the order the
     *         reference nodes are drawn
     */
    List<PetriObjLink> structuralLinks() {
        return structural;
    }

    /**
     * A fusion says the object's slot <em>is</em> the other object's place. Wiring it
     * redirects the source object's slot to the target's instance, so the ordinal the
     * reference node occupies is exactly the index the link addresses.
     */
    private void addFusion(Node node, Node target) {
        if (!target.placeKind) {
            log.warn("Ignoring fusion reference {}: it stands for a transition", node.id);
            return;
        }
        if (node.ordinal < 0 || target.ordinal < 0) {
            log.warn("Ignoring fusion reference {}: it does not stand for a place slot", node.id);
            return;
        }
        structural.add(PetriObjLink.placeFusion(node.page, node.ordinal, target.page, target.ordinal));
    }

    /**
     * A representative carries the link on its arcs: every arc of the page that touches it
     * crosses an object boundary, and is a link rather than an arc of the object's net.
     *
     * <p>Only one direction is a link: a transition producing tokens into a place of another
     * object. An arc the other way round would make a foreign place an input of a local
     * transition, which is refused, since the same model is expressed by sharing the place
     * with this page and drawing an ordinary arc from it.
     *
     * @throws Exception if an arc states the refused direction
     */
    private void addArcLinks(Node node, Node target) throws Exception {
        Set<String> linkArcIds = pageReferences.get(node.page).linkArcIds();
        for (Element arc : XmlHelper.directChildren(scopes.get(node.page), PnmlConstants.ELEMENT_ARC)) {
            String source = arc.getAttribute(PnmlConstants.ATTR_SOURCE);
            String arcTarget = arc.getAttribute(PnmlConstants.ATTR_TARGET);
            boolean outgoing = node.id.equals(source);
            if (!outgoing && !node.id.equals(arcTarget)) {
                continue;
            }
            String arcId = arc.getAttribute(PnmlConstants.ATTR_ID);
            if (!linkArcIds.add(arcId)) {
                // Already accounted for, an arc between two representatives, which has no
                // endpoint in the object at all and cannot say which objects it links.
                continue;
            }
            Node other = byId.get(outgoing ? arcTarget : source);
            if (other == null || other.page != node.page || other.ordinal < 0
                    || other.placeKind == node.placeKind) {
                log.warn("Dropping link arc {}: its other endpoint is not an element of page {}",
                        arcId, node.page);
                continue;
            }
            // A stand-in place with an arc leaving it, or a stand-in transition with an arc
            // entering it, both say the same refused thing: a place of one object drives a
            // transition of another.
            if (node.placeKind == outgoing) {
                throw new Exception(String.format(
                        PnmlConstants.ERROR_RETIRED_LINK_TYPE_DRAWN, arcId, node.id));
            }
            int quantity = quantityOf(arc);
            structural.add(node.placeKind
                    // The stand-in is a place of another object, fed by a transition here.
                    ? PetriObjLink.transitionToPlace(node.page, other.ordinal,
                            target.page, target.ordinal, quantity)
                    // The stand-in is a transition of another object, feeding a place here.
                    : PetriObjLink.transitionToPlace(target.page, target.ordinal,
                            node.page, other.ordinal, quantity));
        }
    }

    /**
     * @return the arc's multiplicity; 1 when the document omits the inscription, which the
     *         standard allows even though this tool always writes it
     */
    private int quantityOf(Element arc) {
        return Math.max(1, XmlHelper.parseIntSafe(
                XmlHelper.getTextContent(arc, PnmlConstants.ELEMENT_INSCRIPTION), 1,
                warnings, "arc '" + arc.getAttribute(PnmlConstants.ATTR_ID) + "'",
                PnmlConstants.ELEMENT_INSCRIPTION));
    }

    /**
     * Locates an element the {@code <petriObjectLinks>} block addresses by id.
     *
     * @param id an element id, possibly of a reference node
     * @return {@code {page, ordinal}}, or {@code null} when the document has no such slot
     */
    int[] slotOf(String id) {
        Node node = byId.get(id);
        if (node == null || node.ordinal < 0) {
            return null;
        }
        return new int[] {node.page, node.ordinal};
    }
}

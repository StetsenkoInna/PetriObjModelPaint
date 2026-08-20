package ua.stetsenkoinna.pnml;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Helper class for XML operations in PNML parsing/generation
 */
final class XmlHelper {

    private XmlHelper() {
        // Utility class
    }

    /**
     * Collects the direct children of an element that carry the given tag name.
     *
     * <p>Unlike {@link Element#getElementsByTagName(String)} this does not descend into
     * nested pages, which matters as soon as one document holds several Petri-objects.
     */
    static List<Element> directChildren(Element parent, String tagName) {
        List<Element> children = new ArrayList<>();
        NodeList nodes = parent.getChildNodes();
        for (int i = 0; i < nodes.getLength(); i++) {
            Node node = nodes.item(i);
            if (node.getNodeType() == Node.ELEMENT_NODE && tagName.equals(node.getNodeName())) {
                children.add((Element) node);
            }
        }
        return children;
    }

    /**
     * Collects every element child of an element, in document order.
     *
     * <p>Order matters wherever it carries meaning: the position of a place among a page's
     * place slots is the index every link declaration of the document is written in.
     */
    static List<Element> directChildren(Element parent) {
        List<Element> children = new ArrayList<>();
        NodeList nodes = parent.getChildNodes();
        for (int i = 0; i < nodes.getLength(); i++) {
            Node node = nodes.item(i);
            if (node.getNodeType() == Node.ELEMENT_NODE) {
                children.add((Element) node);
            }
        }
        return children;
    }

    /**
     * Collects every {@code <page>} of a net, however deeply nested, in document order.
     *
     * <p>ISO/IEC 15909-2 expresses a page hierarchy by nesting a page inside a page, so the
     * pages of a document are its whole page subtree and not only the direct children of the
     * net. A parent is visited before its children.
     *
     * @param scope the net, or any element whose page subtree is wanted
     * @return the pages below it, outermost first
     */
    static List<Element> descendantPages(Element scope) {
        List<Element> pages = new ArrayList<>();
        collectPages(scope, pages);
        return pages;
    }

    private static void collectPages(Element parent, List<Element> pages) {
        for (Element page : directChildren(parent, PnmlConstants.ELEMENT_PAGE)) {
            pages.add(page);
            collectPages(page, pages);
        }
    }

    /**
     * Collects the elements of one scope with the given tag name, without crossing into a
     * page that scope contains.
     *
     * <p>A page inside a page is a Petri-object of its own and its nodes are its own;
     * {@link Element#getElementsByTagName(String)} would gather them into the enclosing
     * object's net instead. A whole {@code <net>} is the one scope whose pages are entered:
     * a plain document keeps its only net inside a single page, and that net is the
     * document's own.
     *
     * <p>A {@code <net>} scope does enter its pages, one level of them: a document whose whole
     * net is written inside a single page reads the same as one that writes it directly under
     * the net, which is what the plain net reader has always done.
     *
     * @param scope a {@code <page>} whose own net is wanted, or a {@code <net>}
     * @param tagName tag of the elements to collect
     */
    static List<Element> scopedElements(Element scope, String tagName) {
        List<Element> found = new ArrayList<>();
        collectScoped(scope, tagName,
                !PnmlConstants.ELEMENT_PAGE.equals(scope.getTagName()), found);
        return found;
    }

    private static void collectScoped(Element parent, String tagName, boolean enterPages,
                                      List<Element> found) {
        for (Element child : directChildren(parent)) {
            if (PnmlConstants.ELEMENT_PAGE.equals(child.getTagName())) {
                if (enterPages) {
                    collectScoped(child, tagName, false, found);
                }
                continue;
            }
            if (tagName.equals(child.getTagName())) {
                found.add(child);
            }
            collectScoped(child, tagName, false, found);
        }
    }

    /**
     * @return the first direct child with the given tag name, or {@code null} if there is none
     */
    static Element firstDirectChild(Element parent, String tagName) {
        List<Element> children = directChildren(parent, tagName);
        return children.isEmpty() ? null : children.getFirst();
    }

    /**
     * Reads {@code <tagName><text>…</text></tagName>} from the direct children only.
     *
     * @return the text, or {@code null} when the element or its text node is missing
     */
    static String getDirectTextContent(Element parent, String tagName) {
        Element child = firstDirectChild(parent, tagName);
        if (child == null) {
            return null;
        }
        Element text = firstDirectChild(child, PnmlConstants.ELEMENT_TEXT);
        return text == null ? null : text.getTextContent();
    }

    /**
     * Collects the tool-specific blocks this family of tools wrote, among the direct
     * children of the given element.
     *
     * <p>This project's own identity wins: the blocks of {@link
     * PnmlConstants#TOOL_PETRI_OBJ_MODEL} are returned whenever the element carries any, and
     * the blocks of {@link PnmlConstants#TOOL_PETRI_NET_SIM} only when it carries none. Each
     * tool of this family writes only its own identity now, so this fallback is what lets a
     * document written by the web application open here at all. The two are never mixed in
     * one result, so a caller that takes the first match it finds cannot read half of each.
     *
     * <p>Selection is on {@code tool} alone, never on {@code version}: a document written by
     * a newer build carries a higher version on the very blocks that hold the object
     * metadata, and filtering them out would silently turn a composed model into a pile of
     * unrelated pages.
     */
    static List<Element> toolSpecificBlocks(Element scope) {
        List<Element> own = new ArrayList<>();
        List<Element> sibling = new ArrayList<>();
        for (Element toolspecific : directChildren(scope, PnmlConstants.ELEMENT_TOOLSPECIFIC)) {
            String tool = toolspecific.getAttribute(PnmlConstants.ATTR_TOOL);
            if (PnmlConstants.TOOL_PETRI_OBJ_MODEL.equals(tool)) {
                own.add(toolspecific);
            } else if (PnmlConstants.TOOL_PETRI_NET_SIM.equals(tool)) {
                sibling.add(toolspecific);
            }
        }
        return own.isEmpty() ? sibling : own;
    }

    /**
     * Reads {@code <tagName>…</tagName>} from the tool-specific blocks of an element.
     *
     * @return the trimmed text of the first match, or {@code null} when there is none
     */
    static String getToolSpecificText(Element scope, String tagName) {
        for (Element toolspecific : toolSpecificBlocks(scope)) {
            Element child = firstDirectChild(toolspecific, tagName);
            if (child != null) {
                return child.getTextContent().trim();
            }
        }
        return null;
    }

    /**
     * Gets text content from first child element with given tag name
     */
    static String getTextContent(Element parent, String tagName) {
        NodeList nodes = parent.getElementsByTagName(tagName);
        if (nodes.getLength() > 0) {
            Element element = (Element) nodes.item(0);
            NodeList textNodes = element.getElementsByTagName(PnmlConstants.ELEMENT_TEXT);
            if (textNodes.getLength() > 0) {
                return textNodes.item(0).getTextContent();
            }
        }
        return null;
    }

    /**
     * Parses integer from text content, returns default value if parsing fails
     */
    static int parseIntSafe(String text, int defaultValue) {
        if (text == null || text.trim().isEmpty()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(text.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    /**
     * Parses double from text content, returns default value if parsing fails
     */
    static double parseDoubleSafe(String text, double defaultValue) {
        if (text == null || text.trim().isEmpty()) {
            return defaultValue;
        }
        try {
            return Double.parseDouble(text.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    /**
     * The sink-aware counterpart of {@link #parseIntSafe(String, int)}: text that is present
     * but does not parse is reported, not only defaulted. Text that is simply absent is not
     * malformed and is never reported.
     *
     * @param warnings sink to append a message to, or {@code null} to behave exactly like the
     *        two-argument overload
     * @param elementDescription what the value belongs to, e.g. {@code "place 'p1'"}
     * @param field the attribute or tag the value came from, e.g. {@code "initialMarking"}
     */
    static int parseIntSafe(String text, int defaultValue, List<String> warnings,
                            String elementDescription, String field) {
        if (text == null || text.trim().isEmpty()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(text.trim());
        } catch (NumberFormatException e) {
            addMalformedNumberWarning(warnings, elementDescription, field, text, String.valueOf(defaultValue));
            return defaultValue;
        }
    }

    /** The double-valued counterpart of {@link #parseIntSafe(String, int, List, String, String)}. */
    static double parseDoubleSafe(String text, double defaultValue, List<String> warnings,
                                  String elementDescription, String field) {
        if (text == null || text.trim().isEmpty()) {
            return defaultValue;
        }
        try {
            return Double.parseDouble(text.trim());
        } catch (NumberFormatException e) {
            addMalformedNumberWarning(warnings, elementDescription, field, text, String.valueOf(defaultValue));
            return defaultValue;
        }
    }

    private static void addMalformedNumberWarning(List<String> warnings, String elementDescription,
                                                   String field, String text, String defaultValue) {
        if (warnings != null) {
            warnings.add(String.format(PnmlConstants.WARNING_MALFORMED_NUMBER,
                    elementDescription, field, text.trim(), defaultValue));
        }
    }

    /** Tags that carry an id some other attribute in the document may point at. */
    private static final List<String> IDENTIFIED_TAGS = List.of(
            PnmlConstants.ELEMENT_PLACE, PnmlConstants.ELEMENT_TRANSITION, PnmlConstants.ELEMENT_ARC,
            PnmlConstants.ELEMENT_REFERENCE_PLACE, PnmlConstants.ELEMENT_REFERENCE_TRANSITION);

    /**
     * Rewrites every place, transition, arc and reference-node id in the document that is not
     * a valid NCName ({@link PnmlIds#isValid}) to a sanitized, document-unique replacement, and
     * rewrites every attribute elsewhere in the document that names one of those ids: an arc's
     * {@code source}/{@code target}, a reference node's {@code ref}, and a declared link's
     * {@code sourceElementId}/{@code targetElementId}.
     *
     * <p>Meant to run once, on the whole document, before anything else reads an id out of it,
     * so that a reader downstream never has to tell a valid id from a sanitized one. The writer
     * runs it too, over the document it is about to write, on exactly the same ids a hand-built
     * {@code PetriP} or {@code PetriT} may carry without ever having gone through this
     * project's own id generator.
     *
     * <p>Two elements that share the same invalid raw id stay sharing one after this runs: every
     * occurrence of that raw id is rewritten to the same sanitized replacement, one remap entry
     * per raw id rather than one per element. The uniqueness a replacement is made unique
     * against only ever resolves a collision between two <em>different</em> raw ids that
     * sanitize to the same thing, or a collision with an id that was already valid; it never
     * separates two occurrences of the same raw id from each other. That is what keeps the
     * duplicate-id check the caller runs after this one able to see the duplicate at all: two
     * elements named {@code "my place"} come out of here both named {@code "my-place"}, still
     * duplicates, rather than {@code "my-place"} and {@code "my-place-2"}, which would make
     * every existing reference to {@code "my place"} silently rebind to whichever element was
     * sanitized last.
     *
     * @return one message per distinct raw id replaced, in document order, empty when every id
     *         was already valid
     */
    static List<String> sanitizeIds(Document document) {
        List<Element> idElements = new ArrayList<>();
        for (String tagName : IDENTIFIED_TAGS) {
            NodeList nodes = document.getElementsByTagName(tagName);
            for (int i = 0; i < nodes.getLength(); i++) {
                idElements.add((Element) nodes.item(i));
            }
        }

        Set<String> used = new HashSet<>();
        for (Element element : idElements) {
            String id = element.getAttribute(PnmlConstants.ATTR_ID);
            if (PnmlIds.isValid(id)) {
                used.add(id);
            }
        }

        List<String> warnings = new ArrayList<>();
        Map<String, String> remap = new LinkedHashMap<>();
        for (Element element : idElements) {
            String id = element.getAttribute(PnmlConstants.ATTR_ID);
            if (PnmlIds.isValid(id)) {
                continue;
            }
            // The same raw id seen again: reuse its earlier replacement rather than minting a
            // fresh, unique one, so the two elements stay recognisably the same duplicate id.
            String replacement = remap.get(id);
            if (replacement == null) {
                replacement = PnmlIds.makeUnique(PnmlIds.sanitize(id), used);
                remap.put(id, replacement);
                warnings.add(String.format(PnmlConstants.WARNING_INVALID_ID, id, replacement));
            }
            element.setAttribute(PnmlConstants.ATTR_ID, replacement);
        }

        if (!remap.isEmpty()) {
            rewriteIdAttribute(document, PnmlConstants.ELEMENT_ARC, PnmlConstants.ATTR_SOURCE, remap);
            rewriteIdAttribute(document, PnmlConstants.ELEMENT_ARC, PnmlConstants.ATTR_TARGET, remap);
            rewriteIdAttribute(document, PnmlConstants.ELEMENT_REFERENCE_PLACE, PnmlConstants.ATTR_REF, remap);
            rewriteIdAttribute(document, PnmlConstants.ELEMENT_REFERENCE_TRANSITION, PnmlConstants.ATTR_REF, remap);
            rewriteIdAttribute(document, PnmlConstants.ELEMENT_LINK, PnmlConstants.ATTR_SOURCE_ELEMENT_ID, remap);
            rewriteIdAttribute(document, PnmlConstants.ELEMENT_LINK, PnmlConstants.ATTR_TARGET_ELEMENT_ID, remap);
        }
        return warnings;
    }

    private static void rewriteIdAttribute(Document document, String tagName, String attribute,
                                           Map<String, String> remap) {
        NodeList nodes = document.getElementsByTagName(tagName);
        for (int i = 0; i < nodes.getLength(); i++) {
            Element element = (Element) nodes.item(i);
            String replacement = remap.get(element.getAttribute(attribute));
            if (replacement != null) {
                element.setAttribute(attribute, replacement);
            }
        }
    }

    /**
     * Checks if string is not null and not empty
     */
    static boolean isNotEmpty(String str) {
        return str != null && !str.isEmpty();
    }
}

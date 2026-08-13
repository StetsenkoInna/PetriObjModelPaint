package ua.stetsenkoinna.pnml;

import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.util.ArrayList;
import java.util.List;

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
     * Collects the tool-specific blocks this tool wrote, among the direct children of the
     * given element.
     *
     * <p>Selection is on {@code tool} alone, never on {@code version}: a document written by
     * a newer build carries a higher version on the very blocks that hold the object
     * metadata, and filtering them out would silently turn a composed model into a pile of
     * unrelated pages.
     */
    static List<Element> toolSpecificBlocks(Element scope) {
        List<Element> blocks = new ArrayList<>();
        for (Element toolspecific : directChildren(scope, PnmlConstants.ELEMENT_TOOLSPECIFIC)) {
            if (PnmlConstants.TOOL_PETRI_OBJ_MODEL.equals(toolspecific.getAttribute(PnmlConstants.ATTR_TOOL))) {
                blocks.add(toolspecific);
            }
        }
        return blocks;
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
     * Checks if string is not null and not empty
     */
    static boolean isNotEmpty(String str) {
        return str != null && !str.isEmpty();
    }
}

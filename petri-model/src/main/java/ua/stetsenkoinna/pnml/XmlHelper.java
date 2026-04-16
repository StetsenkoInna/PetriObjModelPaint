package ua.stetsenkoinna.pnml;

import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

/**
 * Helper class for XML operations in PNML parsing/generation
 */
final class XmlHelper {

    private XmlHelper() {
        // Utility class
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

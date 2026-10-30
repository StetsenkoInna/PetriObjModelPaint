package ua.stetsenkoinna.graphpresentation.training;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

/** Compare PNML values by XML element ID/path; formatting and attribute order are ignored. */
public final class TrainingRevisionDiff {
    private TrainingRevisionDiff() { }
    public record Change(String category, String path, String before, String after, String elementName) { }
    private record Values(Map<String, String> values, Map<String, String> names) { }

    public static List<Change> compare(byte[] left, byte[] right, boolean includeLayout) throws Exception {
        Values leftValues = flatten(left), rightValues = flatten(right);
        Map<String, String> first = leftValues.values(), second = rightValues.values();
        var paths = new java.util.TreeSet<>(first.keySet()); paths.addAll(second.keySet());
        List<Change> changes = new ArrayList<>();
        for (String path : paths) {
            if (java.util.Objects.equals(first.get(path), second.get(path))) continue;
            String category = path.contains("/graphics") || path.contains("/position") || path.contains("/dimension")
                    || path.contains("/@x") || path.contains("/@y") || path.contains("/@width") || path.contains("/@height") ? "layout"
                    : path.contains("initialMarking") ? "marking"
                    : path.contains("/name") ? "label" : "structure-or-parameter";
            if (includeLayout || !category.equals("layout")) changes.add(new Change(category, path, first.get(path), second.get(path), label(path, rightValues.names(), leftValues.names())));
        }
        return List.copyOf(changes);
    }

    private static String label(String path, Map<String, String> current, Map<String, String> previous) {
        for (String prefix = path; prefix.contains("/"); prefix = prefix.substring(0, prefix.lastIndexOf('/'))) {
            if (current.containsKey(prefix)) return current.get(prefix);
            if (previous.containsKey(prefix)) return previous.get(prefix);
        }
        return null;
    }

    private static Values flatten(byte[] xml) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, ""); factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        factory.setXIncludeAware(false); factory.setExpandEntityReferences(false);
        var document = factory.newDocumentBuilder().parse(new ByteArrayInputStream(xml));
        Map<String, String> result = new TreeMap<>();
        Map<String, String> names = new TreeMap<>();
        walk(document.getDocumentElement(), "", 0, result, names);
        return new Values(result, names);
    }

    private static void walk(Element element, String parent, int ordinal, Map<String, String> result, Map<String, String> names) {
        String name = element.getLocalName() == null ? element.getTagName() : element.getLocalName();
        String path = parent + "/" + name + (element.hasAttribute("id") ? "[@id=" + element.getAttribute("id") + "]" : "[" + ordinal + "]");
        result.put(path + "/@element", name);
        var attributes = element.getAttributes();
        for (int index = 0; index < attributes.getLength(); index++) {
            Node attribute = attributes.item(index);
            if (XMLConstants.XMLNS_ATTRIBUTE_NS_URI.equals(attribute.getNamespaceURI())) continue;
            String local = attribute.getLocalName() == null ? attribute.getNodeName() : attribute.getLocalName();
            result.put(path + "/@" + local, attribute.getNodeValue());
        }
        Map<String, Integer> counts = new java.util.HashMap<>();
        var children = element.getChildNodes();
        StringBuilder text = new StringBuilder();
        for (int index = 0; index < children.getLength(); index++) {
            Node child = children.item(index);
            if (child instanceof Element nested) {
                String local = nested.getLocalName() == null ? nested.getTagName() : nested.getLocalName();
                if (local.equals("name") && !nested.getTextContent().isBlank()) names.put(path, nested.getTextContent().strip());
                walk(nested, path, counts.merge(local, 1, Integer::sum) - 1, result, names);
            } else if (child.getNodeType() == Node.TEXT_NODE || child.getNodeType() == Node.CDATA_SECTION_NODE) text.append(child.getNodeValue());
        }
        if (!text.toString().isBlank()) result.put(path + "/text", text.toString().strip());
    }
}

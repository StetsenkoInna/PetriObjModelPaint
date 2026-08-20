package pnml;

import com.thaiopensource.util.PropertyMapBuilder;
import com.thaiopensource.validate.ValidateProperty;
import com.thaiopensource.validate.ValidationDriver;
import com.thaiopensource.validate.rng.SAXSchemaReader;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.StringReader;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * The two checks {@link PnmlRngConformanceTest} runs against writer output, shared so any other
 * test that has an XML document to check, {@link WebInteropTest} included, can run them against
 * a document it did not write itself.
 *
 * <p>Extracted out of {@link PnmlRngConformanceTest} rather than duplicated: both checks are
 * self-contained (a document string or a parsed {@link Document} in, an assertion failure or
 * nothing out) and carry no dependency on how the caller obtained the document.
 */
final class PnmlConformanceAssertions {

    private static final String SCHEMA = "/schema/ptnet.pntd";

    private PnmlConformanceAssertions() {
    }

    // ---------------------------------------------------------------- RELAX NG validation

    /** @throws AssertionError naming every schema violation jing reported, if any */
    static void assertSchemaValid(String xml) throws Exception {
        java.io.File schemaFile = Paths.get(PnmlConformanceAssertions.class.getResource(SCHEMA).toURI()).toFile();

        List<String> problems = new ArrayList<>();
        org.xml.sax.ErrorHandler collector = new org.xml.sax.ErrorHandler() {
            @Override
            public void warning(SAXParseException exception) {
                problems.add("warning: " + exception.getMessage());
            }

            @Override
            public void error(SAXParseException exception) {
                problems.add("error: " + exception.getMessage());
            }

            @Override
            public void fatalError(SAXParseException exception) throws SAXException {
                problems.add("fatal: " + exception.getMessage());
                throw exception;
            }
        };

        // jing's own CHECK_ID_IDREF flag is deliberately left off: it rejects this schema's
        // <arc> pattern as an ID-type ambiguity, a restriction on the RELAX NG pattern shape
        // rather than a statement about any document. Id uniqueness and reference resolution
        // are exactly the ground {@link #assertConformant} covers instead, in Java, which is
        // also where the contract puts them: things a schema cannot state.
        PropertyMapBuilder properties = new PropertyMapBuilder();
        properties.put(ValidateProperty.ERROR_HANDLER, collector);

        ValidationDriver driver = new ValidationDriver(properties.toPropertyMap(), SAXSchemaReader.getInstance());
        boolean schemaLoaded = driver.loadSchema(ValidationDriver.fileInputSource(schemaFile));
        boolean valid = schemaLoaded
                && driver.validate(new InputSource(new StringReader(xml)));

        assertTrue("expected the document to be valid PNML; jing reported: " + problems,
                valid && problems.isEmpty());
    }

    // ---------------------------------------------------------------- semantic rules RNG cannot state

    /**
     * Everything a RELAX NG schema types as a plain {@code ID}/{@code IDREF} and therefore
     * cannot see: document-wide id uniqueness (also checked by jing itself, above, this is the
     * independent check), a reference chain terminating rather than cycling or dangling, every
     * arc's endpoints sitting on its own page, and the net staying bipartite (a place never
     * directly touching another place, a transition never directly touching another transition)
     * once every reference is resolved to what it stands for.
     */
    static void assertConformant(Document document) {
        List<Element> pages = descendantPages(document.getDocumentElement());
        assertFalse("a document needs at least one page", pages.isEmpty());

        Set<String> allIds = new HashSet<>();
        Map<String, String> kindOf = new HashMap<>();
        Map<String, String> refOf = new HashMap<>();
        Map<Element, Set<String>> nodesOnPage = new HashMap<>();

        for (Element page : pages) {
            Set<String> onPage = new HashSet<>();
            for (Element child : directChildren(page)) {
                String tag = child.getTagName();
                boolean isPlaceKind = "place".equals(tag) || "referencePlace".equals(tag);
                boolean isTransitionKind = "transition".equals(tag) || "referenceTransition".equals(tag);
                if (!isPlaceKind && !isTransitionKind) {
                    continue;
                }
                String id = child.getAttribute("id");
                assertTrue("id '" + id + "' is unique across the whole document", allIds.add(id));
                kindOf.put(id, isPlaceKind ? "place" : "transition");
                onPage.add(id);
                if (child.hasAttribute("ref")) {
                    refOf.put(id, child.getAttribute("ref"));
                }
            }
            nodesOnPage.put(page, onPage);
        }

        // Every reference resolves: not to itself, not through a cycle, and to a real node.
        for (String id : refOf.keySet()) {
            resolveKind(id, kindOf, refOf);
        }

        for (Element page : pages) {
            Set<String> onPage = nodesOnPage.get(page);
            for (Element arc : directChildren(page, "arc")) {
                String arcId = arc.getAttribute("id");
                String source = arc.getAttribute("source");
                String target = arc.getAttribute("target");
                assertTrue("arc '" + arcId + "' source is on its own page", onPage.contains(source));
                assertTrue("arc '" + arcId + "' target is on its own page", onPage.contains(target));

                String sourceKind = resolveKind(source, kindOf, refOf);
                String targetKind = resolveKind(target, kindOf, refOf);
                assertNotEquals("arc '" + arcId + "' must connect a place and a transition, "
                        + "not two " + sourceKind + "s", sourceKind, targetKind);
            }
        }
    }

    /**
     * Follows a chain of {@code ref=} attributes to a real place or transition.
     *
     * @throws AssertionError if the chain cycles, dangles, or runs implausibly long
     */
    private static String resolveKind(String id, Map<String, String> kindOf, Map<String, String> refOf) {
        Set<String> visited = new HashSet<>();
        String current = id;
        for (int depth = 0; depth <= 32; depth++) {
            if (!visited.add(current)) {
                fail("reference '" + id + "' cycles back to '" + current + "'");
            }
            String next = refOf.get(current);
            if (next == null) {
                String kind = kindOf.get(current);
                assertTrue("reference '" + id + "' resolves to '" + current
                        + "', which is not an element of this document", kind != null);
                return kind;
            }
            current = next;
        }
        fail("reference '" + id + "' does not resolve within a plausible chain length");
        return null;
    }

    // ---------------------------------------------------------------- DOM helpers

    static Document parse(String xml) throws Exception {
        return DocumentBuilderFactory.newInstance().newDocumentBuilder()
                .parse(new InputSource(new StringReader(xml)));
    }

    /**
     * Every {@code <page>} of the document, however deeply nested, in document order. Both
     * writers here always write at least one page (the plain dialect wraps its single net in
     * one for compatibility with tools like Tina), so the fallback below, treating a page-less
     * document's {@code <net>} itself as the one scope to check, exists only for a
     * hypothetical foreign document neither writer here produces.
     *
     * @param root the document's {@code <pnml>} root element
     */
    private static List<Element> descendantPages(Element root) {
        List<Element> pages = elementsByTagName(root, "page");
        if (pages.isEmpty()) {
            return elementsByTagName(root, "net");
        }
        return pages;
    }

    private static List<Element> elementsByTagName(Element root, String tagName) {
        List<Element> found = new ArrayList<>();
        NodeList nodes = root.getElementsByTagName(tagName);
        for (int i = 0; i < nodes.getLength(); i++) {
            found.add((Element) nodes.item(i));
        }
        return found;
    }

    private static List<Element> directChildren(Element parent) {
        return directChildren(parent, null);
    }

    private static List<Element> directChildren(Element parent, String tagName) {
        List<Element> children = new ArrayList<>();
        NodeList nodes = parent.getChildNodes();
        for (int i = 0; i < nodes.getLength(); i++) {
            org.w3c.dom.Node node = nodes.item(i);
            if (node.getNodeType() == org.w3c.dom.Node.ELEMENT_NODE
                    && (tagName == null || tagName.equals(node.getNodeName()))) {
                children.add((Element) node);
            }
        }
        return children;
    }
}

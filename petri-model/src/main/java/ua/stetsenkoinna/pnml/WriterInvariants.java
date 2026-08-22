package ua.stetsenkoinna.pnml;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Checks a finished document against the invariants a PNML reader is entitled to assume, and
 * that a RELAX NG schema cannot state on its own, most importantly that an arc never leaves
 * its page, which the schema types as a plain IDREF and therefore cannot see.
 *
 * <p>Run by both writers, on the document each is about to write. What can be fixed is fixed
 * first: {@link XmlHelper#sanitizeIds} is the write side of the same rule the reader applies to
 * a foreign document, and it is what lets a hand-built {@code PetriP} or {@code PetriT} with an
 * id this project's own generator never produced still be written as valid PNML. What is left
 * after that is genuinely broken structure (a duplicate id, an arc that reaches off its own
 * page, a weight below one, a reference that dangles or cycles) and is refused rather than
 * written.
 *
 * <p>A plain document has no reference nodes at all, so the reference-target and cycle checks
 * below simply have nothing to look at for one; the same method serves both dialects.
 */
final class WriterInvariants {

    private WriterInvariants() {
        // Utility class
    }

    /**
     * @param document the finished document, fixed in place where {@link
     *        XmlHelper#sanitizeIds} can fix it
     * @throws Exception naming the first violation that sanitizing ids could not fix
     */
    static void assertValid(Document document) throws Exception {
        XmlHelper.sanitizeIds(document);

        Element netElement = PnmlParser.findNetElement(document);
        List<Element> pages = XmlHelper.descendantPages(netElement);
        if (pages.isEmpty()) {
            throw new Exception(PnmlConstants.ERROR_NO_OBJECTS);
        }

        Set<String> allIds = new HashSet<>();
        Map<String, String> referenceTargets = new HashMap<>();
        for (Element page : pages) {
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
                // reader a document that cannot mean anything, so the net must be fixed instead.
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

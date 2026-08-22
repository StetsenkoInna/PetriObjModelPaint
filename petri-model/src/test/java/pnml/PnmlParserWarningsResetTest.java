package pnml;

import org.junit.Test;
import ua.stetsenkoinna.graphnet.GraphPetriObjModel;
import ua.stetsenkoinna.petriobj.PetriNet;
import ua.stetsenkoinna.pnml.ImportResult;
import ua.stetsenkoinna.pnml.PnmlModelParser;
import ua.stetsenkoinna.pnml.PnmlParser;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * {@code getWarnings()} on either reader promises the warnings of the most recent document, not
 * an accumulation across every document that instance ever parsed. Before this fix neither
 * reader cleared its warnings list between calls, so a reused instance reported the previous
 * file's warnings against the next one.
 */
public class PnmlParserWarningsResetTest {

    /** An id with a space is not a valid NCName, so parsing it always raises a warning. */
    private static final String WITH_INVALID_ID =
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                    + "<pnml xmlns=\"http://www.pnml.org/version-2009/grammar/pnml\">\n"
                    + "  <net id=\"n1\" type=\"http://www.pnml.org/version-2009/grammar/ptnet\">\n"
                    + "    <page id=\"page1\"><place id=\"bad id\"/></page>\n"
                    + "  </net>\n"
                    + "</pnml>\n";

    /** Nothing here can raise a warning: a valid id, no numbers, no arcs. */
    private static final String CLEAN =
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                    + "<pnml xmlns=\"http://www.pnml.org/version-2009/grammar/pnml\">\n"
                    + "  <net id=\"n2\" type=\"http://www.pnml.org/version-2009/grammar/ptnet\">\n"
                    + "    <page id=\"page1\"><place id=\"p0\"/></page>\n"
                    + "  </net>\n"
                    + "</pnml>\n";

    // ---------------------------------------------------------------- PnmlParser

    @Test
    public void pnmlParserReportsOnlyTheMostRecentDocumentsWarnings() throws Exception {
        PnmlParser parser = new PnmlParser();

        parser.parseXml(WITH_INVALID_ID);
        assertFalse("the first document's invalid id should raise a warning",
                parser.getWarnings().isEmpty());

        parser.parseXml(CLEAN);
        assertTrue("a reused parser must not still report the previous document's warnings",
                parser.getWarnings().isEmpty());
    }

    /**
     * An {@link ImportResult} built from a parser must keep the warnings of the document it was
     * actually built from, even if that same parser instance is reused for a later document.
     */
    @Test
    public void importResultSnapshotsTheWarningsRatherThanTrackingTheLiveParser() throws Exception {
        PnmlParser parser = new PnmlParser();

        PetriNet net = parser.parseXml(WITH_INVALID_ID);
        ImportResult result = new ImportResult(net, parser);
        assertFalse("the snapshot should carry the first document's warning",
                result.getWarnings().isEmpty());

        parser.parseXml(CLEAN);

        assertFalse("a snapshot taken earlier must not change when the parser is reused",
                result.getWarnings().isEmpty());
        assertTrue("the parser itself now reports only the second, clean document's warnings",
                parser.getWarnings().isEmpty());
    }

    // ---------------------------------------------------------------- PnmlModelParser

    @Test
    public void pnmlModelParserReportsOnlyTheMostRecentDocumentsWarnings() throws Exception {
        PnmlModelParser parser = new PnmlModelParser();

        GraphPetriObjModel first = parser.parseXml(WITH_INVALID_ID);
        assertFalse("the first document's invalid id should raise a warning",
                parser.getWarnings().isEmpty());
        assertFalse(first.getObjects().isEmpty());

        parser.parseXml(CLEAN);
        assertTrue("a reused model parser must not still report the previous document's warnings",
                parser.getWarnings().isEmpty());
    }
}

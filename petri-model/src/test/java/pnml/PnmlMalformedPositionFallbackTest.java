package pnml;

import org.junit.Test;
import ua.stetsenkoinna.petriobj.PetriNet;
import ua.stetsenkoinna.petriobj.PetriP;
import ua.stetsenkoinna.pnml.PnmlParser;

import java.awt.geom.Point2D;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * A standard {@code <graphics><position>} whose x or y does not parse as a number used to
 * commit to that source anyway, defaulting the bad half to 0.0, which shadows a perfectly
 * valid tool-specific {@code <coordinates>} on the same node. The fix treats a malformed
 * standard position as though it were entirely absent, after warning about it, so parsing
 * falls through to the coordinates fallback exactly as it would for a node with no standard
 * graphics at all.
 */
public class PnmlMalformedPositionFallbackTest {

    private static final String XML =
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                    + "<pnml xmlns=\"http://www.pnml.org/version-2009/grammar/pnml\">\n"
                    + "  <net id=\"n\" type=\"http://www.pnml.org/version-2009/grammar/ptnet\">\n"
                    + "    <page id=\"page1\">\n"
                    + "      <place id=\"p0\">\n"
                    + "        <graphics><position x=\"abc\" y=\"80\"/></graphics>\n"
                    + "        <toolspecific tool=\"PetriObjModel\" version=\"2.2.2\">\n"
                    + "          <coordinates x=\"60\" y=\"80\"/>\n"
                    + "        </toolspecific>\n"
                    + "      </place>\n"
                    + "    </page>\n"
                    + "  </net>\n"
                    + "</pnml>\n";

    @Test
    public void aMalformedStandardPositionFallsThroughToTheCoordinates() throws Exception {
        PnmlParser parser = new PnmlParser();
        PetriNet net = parser.parseXml(XML);

        PetriP place = net.getListP()[0];
        Point2D.Double position = parser.getPlaceCoordinates(place.getNumber());

        assertNotNull("the tool-specific coordinates must still be found", position);
        assertEquals("x comes from the coordinates fallback, not a half-defaulted position",
                60.0, position.x, 0.0001);
        assertEquals(80.0, position.y, 0.0001);

        boolean warnedAboutTheMalformedNumber = parser.getWarnings().stream()
                .anyMatch(warning -> warning.contains("not a number") && warning.contains("abc"));
        assertTrue("a malformed-number warning should still be raised, warnings were: "
                        + parser.getWarnings(),
                warnedAboutTheMalformedNumber);
    }
}

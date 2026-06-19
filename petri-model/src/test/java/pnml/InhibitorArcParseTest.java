package pnml;

import org.junit.Before;
import org.junit.Test;
import ua.stetsenkoinna.petriobj.ArcIn;
import ua.stetsenkoinna.petriobj.PetriNet;
import ua.stetsenkoinna.pnml.PnmlParser;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Verifies that the parser recognises inhibitor arcs in the PNML emitted by the
 * Text2PNML exporter, which marks them with {@code <type value="inhibitorArc"/>}
 * and/or {@code <toolspecific><arcType>inhibitor</arcType>}. Both map to the
 * PetriObjModel "informational" arc flag.
 */
public class InhibitorArcParseTest {

    private PnmlParser parser;

    @Before
    public void setUp() {
        parser = new PnmlParser();
    }

    private static String pnml(String inhibitorArcMarkup) {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"no\"?>\n"
                + "<pnml xmlns=\"http://www.pnml.org/version-2009/grammar/pnml\">\n"
                + "  <net id=\"net1\" type=\"http://www.pnml.org/version-2009/grammar/ptnet\">\n"
                + "    <name><text>InhibitorTest</text></name>\n"
                + "    <page id=\"page1\">\n"
                + "      <place id=\"p1\"><name><text>P1</text></name><initialMarking><text>1</text></initialMarking></place>\n"
                + "      <place id=\"p2\"><name><text>P2</text></name></place>\n"
                + "      <place id=\"p3\"><name><text>P3</text></name><initialMarking><text>1</text></initialMarking></place>\n"
                + "      <transition id=\"t1\"><name><text>T1</text></name>\n"
                + "        <toolspecific tool=\"PetriObjModel\" version=\"1.0\">\n"
                + "          <timeDelay>1.0</timeDelay><delayMeanValue>1.0</delayMeanValue><distribution>exp</distribution><priority>0</priority>\n"
                + "        </toolspecific>\n"
                + "      </transition>\n"
                + "      <arc id=\"a1\" source=\"p1\" target=\"t1\">\n"
                + "        <inscription><text>1</text></inscription>\n"
                + inhibitorArcMarkup
                + "      </arc>\n"
                + "      <arc id=\"a3\" source=\"p3\" target=\"t1\"><inscription><text>1</text></inscription></arc>\n"
                + "      <arc id=\"a2\" source=\"t1\" target=\"p2\"><inscription><text>1</text></inscription></arc>\n"
                + "    </page>\n"
                + "  </net>\n"
                + "</pnml>\n";
    }

    private static ArcIn arcFrom(PetriNet net, String sourceId) {
        for (ArcIn arc : net.getArcIn()) {
            if (sourceId.equals(arc.getNameP())) {
                return arc;
            }
        }
        return null;
    }

    @Test
    public void recognisesInhibitorArcFromTypeElement() throws Exception {
        PetriNet net = parser.parseXml(pnml("        <type value=\"inhibitorArc\"/>\n"));

        ArcIn inhibitor = arcFrom(net, "p1");
        ArcIn normal = arcFrom(net, "p3");
        assertNotNull(inhibitor);
        assertNotNull(normal);
        assertTrue("p1->t1 must be informational (inhibitor)", inhibitor.getIsInf());
        assertFalse("p3->t1 must stay a normal arc", normal.getIsInf());
    }

    @Test
    public void recognisesInhibitorArcFromToolspecificArcType() throws Exception {
        PetriNet net = parser.parseXml(pnml(
                "        <toolspecific tool=\"PetriObjModel\" version=\"1.0\"><arcType>inhibitor</arcType></toolspecific>\n"));

        assertTrue(arcFrom(net, "p1").getIsInf());
        assertFalse(arcFrom(net, "p3").getIsInf());
    }

    @Test
    public void normalArcsStayNormal() throws Exception {
        PetriNet net = parser.parseXml(pnml(""));

        assertEquals(2, net.getArcIn().length);
        assertFalse(arcFrom(net, "p1").getIsInf());
        assertFalse(arcFrom(net, "p3").getIsInf());
    }
}

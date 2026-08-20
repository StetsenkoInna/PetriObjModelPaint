package pnml;

import org.junit.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import ua.stetsenkoinna.graphnet.GraphPetriObjModel;
import ua.stetsenkoinna.graphnet.GraphPetriObject;
import ua.stetsenkoinna.pnml.PnmlModelGenerator;
import ua.stetsenkoinna.pnml.PnmlModelParser;

import java.awt.geom.Point2D;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static pnml.PnmlConformanceAssertions.assertConformant;
import static pnml.PnmlConformanceAssertions.assertSchemaValid;
import static pnml.PnmlConformanceAssertions.parse;

/**
 * Proves the desktop parser reads PNML this project's other real tools actually produce, not
 * only the synthetic fixtures the rest of this module writes by hand. Two real files:
 *
 * <ul>
 *   <li>{@link #WEB_FIXTURE}, copied verbatim from the web application's own golden fixture,
 *       {@code backend/tests/fixtures/composed_conformant_v21.pnml} in the petri-net-sim
 *       repository, exactly what its exporter is checked against in its own test suite: a
 *       single {@code tool="PetriNetSim"} toolspecific block, no toolspecific
 *       {@code <coordinates>}, and no retired {@code placeToTransition} link, since the web
 *       exporter now refuses to write one instead of projecting it.</li>
 *   <li>{@link #LEGACY_FIXTURE}, a file an actual 2.2.2-era build of this desktop wrote from a
 *       real user's session, never touched by hand afterward.</li>
 * </ul>
 *
 * <p>Reading the web fixture exercises the reader's other-identity fallback ({@link
 * ua.stetsenkoinna.pnml.XmlHelper#toolSpecificBlocks}) for real: nothing here states
 * {@code tool="PetriObjModel"}, so every piece of object metadata this reader recovers, and the
 * standard {@code <graphics><position>} it falls back to for a node position, comes from the
 * web application's own blocks alone.
 */
public class WebInteropTest {

    private static String fixture(String resource) throws Exception {
        return Files.readString(
                Paths.get(WebInteropTest.class.getResource(resource).toURI()),
                StandardCharsets.UTF_8);
    }

    // ---------------------------------------------------------------- the web application's export

    private static final String WEB_FIXTURE = "/pnml/web_composed_conformant_v21.pnml";

    /**
     * Ground truth read directly off the file, independent of {@link PnmlModelParser}: two
     * pages, one per Petri-object, and two link declarations, one of each kind the web
     * application's exporter can still produce, in a single {@code tool="PetriNetSim"} block
     * stating that application's own release.
     */
    @Test
    public void theWebFixtureDeclaresTwoObjectsAndTwoLinksOfTwoKinds() throws Exception {
        String xml = fixture(WEB_FIXTURE);
        Document document = parse(xml);

        assertEquals(2, document.getElementsByTagName("page").getLength());
        assertEquals(1, countDeclaredLinksOfType(document, "placeFusion"));
        assertEquals(1, countDeclaredLinksOfType(document, "transitionToPlace"));
        assertEquals(0, countDeclaredLinksOfType(document, "placeToTransition"));

        assertTrue("only the web application's own toolspecific identity is present",
                xml.contains("tool=\"PetriNetSim\" version=\"1.0.0\""));
        assertFalse("no PetriObjModel block anywhere", xml.contains("tool=\"PetriObjModel\""));
        assertFalse("no toolspecific <coordinates> is written any more", xml.contains("<coordinates"));
        assertTrue("place \"Pool\" states its center in the standard <graphics><position>",
                xml.contains("<position x=\"88\" y=\"108\"/>"));
    }

    /**
     * The file is fully valid, conformant PNML by the standard's own rules: schema-valid per
     * ISO/IEC 15909-2's own RELAX NG grammar, ids unique, every reference resolving, every arc
     * on its own page, the net bipartite.
     */
    @Test
    public void theWebFixtureIsValidPnmlTheStandardsOwnGrammarAccepts() throws Exception {
        String xml = fixture(WEB_FIXTURE);
        assertSchemaValid(xml);
        assertConformant(parse(xml));
    }

    /**
     * The web fixture parses cleanly, with nothing here for a warning to fall over: the model
     * it yields has exactly the object and link counts {@link
     * #theWebFixtureDeclaresTwoObjectsAndTwoLinksOfTwoKinds} reads directly off the file, and a
     * concrete center coordinate, place "Pool"'s, passes through unchanged from the file's
     * standard {@code <graphics><position>}. Re-exporting what was read is PNML the standard's
     * own grammar still accepts, and re-importing that re-export yields the same counts again.
     */
    @Test
    public void theWebFixtureParsesCleanlyAndRoundTripsThroughTheRngGrammar() throws Exception {
        String xml = fixture(WEB_FIXTURE);
        PnmlModelParser parser = new PnmlModelParser();
        GraphPetriObjModel model = parser.parseXml(xml);

        assertEquals("a clean import: nothing in this file should need a warning",
                List.of(), parser.getWarnings());
        assertEquals(2, model.getObjectCount());
        assertEquals(2, model.getLinks().size());

        GraphPetriObject generator = model.getObject(0);
        assertEquals("Generator", generator.getName());
        Point2D poolCenter = generator.getGraphNet().getGraphPetriPlaceList().stream()
                .filter(place -> "p_pool".equals(place.getPetriPlace().getId()))
                .findFirst().orElseThrow(() -> new AssertionError("place p_pool not found"))
                .getGraphElementCenter();
        assertEquals(88.0, poolCenter.getX(), 0.0);
        assertEquals(108.0, poolCenter.getY(), 0.0);

        String reExported = new PnmlModelGenerator().generateXml(model);
        assertSchemaValid(reExported);
        assertConformant(parse(reExported));

        GraphPetriObjModel reimported = new PnmlModelParser().parseXml(reExported);
        assertEquals(model.getObjectCount(), reimported.getObjectCount());
        assertEquals(model.getLinks().size(), reimported.getLinks().size());
    }

    /** @return how many links the document declares of the given type */
    private static int countDeclaredLinksOfType(Document document, String type) {
        NodeList links = document.getElementsByTagName("link");
        int count = 0;
        for (int i = 0; i < links.getLength(); i++) {
            if (type.equals(((Element) links.item(i)).getAttribute("type"))) {
                count++;
            }
        }
        return count;
    }

    // ---------------------------------------------------------------- a real desktop user's file

    private static final String LEGACY_FIXTURE = "/pnml/legacy_desktop_new_petrinet.pnml";

    /**
     * A file an actual 2.2.2-era build of this desktop wrote for a real user, never touched by
     * hand afterward: {@code "New PetriNet"}, this desktop's own former default project name
     * left unchanged, which is a space and therefore not a valid {@code xs:ID}.
     *
     * <p>The invalid net id produces no failure and no warning: nothing in this document
     * addresses the net by its own id, only by the id each place, transition, arc and reference
     * node carries in its own {@code id=} attribute, so the net id is not element-id territory
     * and the sanitizer that fixes up invalid element ids never has reason to look at it. The
     * display name instead comes from {@code <name><text>}, which is exactly the same string,
     * so this file cannot by itself tell the two apart; {@link
     * pnml.ComposedPnmlConformanceTest} and the sanitizer's own tests cover the case where they
     * differ.
     *
     * <p>Coordinates come through unchanged, center as written: place {@code p-p3-91569318}
     * sits at (575, 214) in the file and comes out of the parsed model at exactly that point.
     * This fixture is a historical export, from before this project stopped writing dual
     * toolspecific blocks: it still carries both a {@code PetriObjModel} and a
     * {@code PetriNetSim} block on every element, and both a toolspecific
     * {@code <coordinates>} and a standard {@code <graphics><position>} on every node. It is
     * kept exactly as it was written, which is what makes it worth reading here.
     */
    @Test
    public void theLegacyDesktopFixtureParsesDespiteItsInvalidNetId() throws Exception {
        PnmlModelParser parser = new PnmlModelParser();
        GraphPetriObjModel model = parser.parseXml(fixture(LEGACY_FIXTURE));

        assertEquals("no warning: the net id is not something any reference in this "
                + "document points at", List.of(), parser.getWarnings());
        assertEquals("the display name comes from <name><text>, not the invalid net id",
                "New PetriNet", model.getName());
        assertEquals("the two nesting wrappers, the object they nest, and the free-elements "
                + "bucket are all still four Petri-objects", 4, model.getObjectCount());
        assertEquals(2, model.getLinks().size());

        GraphPetriObject object1 = model.getObject(0);
        assertEquals("Object 1", object1.getName());
        Point2D p3Center = object1.getGraphNet().getGraphPetriPlaceList().stream()
                .filter(place -> "p-p3-91569318".equals(place.getPetriPlace().getId()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("place p-p3-91569318 not found"))
                .getGraphElementCenter();
        assertEquals(575.0, p3Center.getX(), 0.0);
        assertEquals(214.0, p3Center.getY(), 0.0);
    }
}

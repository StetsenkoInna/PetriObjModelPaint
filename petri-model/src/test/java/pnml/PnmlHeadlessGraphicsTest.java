package pnml;

import org.junit.After;
import org.junit.Test;
import ua.stetsenkoinna.petriobj.ArcIn;
import ua.stetsenkoinna.petriobj.ArcOut;
import ua.stetsenkoinna.petriobj.PetriNet;
import ua.stetsenkoinna.petriobj.PetriP;
import ua.stetsenkoinna.petriobj.PetriT;
import ua.stetsenkoinna.pnml.PnmlGenerator;
import ua.stetsenkoinna.pnml.PnmlParser;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * {@link PnmlGenerator#generate(PetriNet, File)} runs headless, with no {@code GraphPetriNet}
 * to take a position from. It used to fill that gap with a {@code <graphics><position x="0"
 * y="0"/>} placeholder on every node, which a reader can no longer tell apart from a real
 * position at the origin: the document came out with every node stacked on top of each other
 * instead of laid out by {@code GraphNetBuilder}'s fallback grid.
 *
 * <p>The fix is to omit {@code <graphics>} entirely when there is no drawing to take a
 * position from, which the schema allows. This pins that: nothing a headless write produces
 * parses back into a coordinate at all.
 */
public class PnmlHeadlessGraphicsTest {

    private final File tempFile = new File("target/test-classes/headless_graphics_junit.pnml");

    @After
    public void tearDown() {
        if (tempFile.exists()) {
            tempFile.delete();
        }
    }

    @Test
    public void headlessWriteOmitsGraphicsAndLeavesTheFallbackGridReachable() throws Exception {
        PetriP p1 = new PetriP("HeadlessPlace1", 1);
        PetriP p2 = new PetriP("HeadlessPlace2", 0);
        PetriT t1 = new PetriT("HeadlessTransition", 1.0);
        ArrayList<PetriP> places = new ArrayList<>(List.of(p1, p2));
        ArrayList<PetriT> transitions = new ArrayList<>(List.of(t1));
        ArrayList<ArcIn> arcsIn = new ArrayList<>(List.of(new ArcIn(p1, t1, 1)));
        ArrayList<ArcOut> arcsOut = new ArrayList<>(List.of(new ArcOut(t1, p2, 1)));
        PetriNet net = new PetriNet("Headless Net", places, transitions, arcsIn, arcsOut);

        // The two-argument overload: no GraphPetriNet, exactly the headless path.
        new PnmlGenerator().generate(net, tempFile);

        String xml = Files.readString(tempFile.toPath(), StandardCharsets.UTF_8);
        assertFalse("no placeholder graphics should be written when there is no drawing",
                xml.contains("<graphics>"));

        PnmlParser parser = new PnmlParser();
        parser.parse(tempFile);

        assertTrue("no place should get a coordinate to shadow the fallback grid",
                parser.getAllPlaceCoordinates().isEmpty());
        assertTrue("no transition should get a coordinate to shadow the fallback grid",
                parser.getAllTransitionCoordinates().isEmpty());
    }
}

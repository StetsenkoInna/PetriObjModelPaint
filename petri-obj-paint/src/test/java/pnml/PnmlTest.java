package pnml;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

import ua.stetsenkoinna.PetriObj.*;
import ua.stetsenkoinna.pnml.PnmlGenerator;
import ua.stetsenkoinna.pnml.PnmlParser;

import java.io.File;

/**
 * JUnit test class for PNML import/export functionality
 *
 * @author Serhii Rybak
 */
public class PnmlTest {

    private PnmlGenerator generator;
    private PnmlParser parser;
    private File tempExportFile;

    @Before
    public void setUp() {
        generator = new PnmlGenerator();
        parser = new PnmlParser();
        tempExportFile = new File("target/test-classes/junit_exported_test.pnml");
    }

    @After
    public void tearDown() {
        if (tempExportFile != null && tempExportFile.exists()) {
            tempExportFile.delete();
        }
    }

    @Test
    public void testImportPnmlFile() throws Exception {
        File testFile = new File(PnmlTest.class.getResource("/pnml/test_petri_net.pnml").toURI());
        assertTrue("Test file should exist", testFile.exists());

        PetriNet petriNet = parser.parse(testFile);

        // Verify basic structure
        assertNotNull("PetriNet should not be null", petriNet);
        assertEquals("Net name should match", "testNet", petriNet.getName());
        assertEquals("Should have 2 places", 2, petriNet.getListP().length);
        assertEquals("Should have 1 transition", 1, petriNet.getListT().length);
        assertEquals("Should have 1 input arc", 1, petriNet.getArcIn().length);
        assertEquals("Should have 1 output arc", 1, petriNet.getArcOut().length);

        // Verify places details
        PetriP[] places = petriNet.getListP();
        assertNotNull("Places array should not be null", places);

        // Verify first place
        PetriP place1 = places[0];
        assertNotNull("First place should not be null", place1);
        assertEquals("First place should have correct name", "Place 1", place1.getName());
        assertEquals("First place should have correct marking", 2, place1.getMark());

        // Verify transitions details
        PetriT[] transitions = petriNet.getListT();
        assertNotNull("Transitions array should not be null", transitions);

        PetriT transition1 = transitions[0];
        assertNotNull("First transition should not be null", transition1);
        assertEquals("First transition should have correct name", "Transition 1", transition1.getName());
        assertEquals("First transition should have correct delay", 1.5, transition1.getParameter(), 0.001);
        assertEquals("First transition should have correct priority", 1, transition1.getPriority());
        assertEquals("First transition should have correct probability", 0.8, transition1.getProbability(), 0.001);
    }

    @Test
    public void testExportAndReimportPnmlFile() throws Exception {
        // Create a simple test Petri net
        PetriP p1 = new PetriP("TestPlace1", 1);
        PetriP p2 = new PetriP("TestPlace2", 0);
        PetriT t1 = new PetriT("TestTransition1", 2.0);
        ArcIn arcIn = new ArcIn(p1, t1, 1);
        ArcOut arcOut = new ArcOut(t1, p2, 1);

        PetriP[] places = {p1, p2};
        PetriT[] transitions = {t1};
        ArcIn[] arcIns = {arcIn};
        ArcOut[] arcOuts = {arcOut};

        PetriNet testNet = new PetriNet("Test Export Net", places, transitions, arcIns, arcOuts);

        // Export to PNML
        generator.generate(testNet, tempExportFile);
        assertTrue("Export file should be created", tempExportFile.exists());
        assertTrue("Export file should not be empty", tempExportFile.length() > 0);

        // Verify the exported file by importing it back
        PetriNet importedNet = parser.parse(tempExportFile);

        // Verify structure is preserved
        assertNotNull("Imported net should not be null", importedNet);
        assertEquals("Net name should be preserved", "Test Export Net", importedNet.getName());
        assertEquals("Number of places should be preserved", 2, importedNet.getListP().length);
        assertEquals("Number of transitions should be preserved", 1, importedNet.getListT().length);
        assertEquals("Number of input arcs should be preserved", 1, importedNet.getArcIn().length);
        assertEquals("Number of output arcs should be preserved", 1, importedNet.getArcOut().length);

        // Verify transition properties are preserved
        PetriT importedTransition = importedNet.getListT()[0];
        assertEquals("Transition name should be preserved", "TestTransition1", importedTransition.getName());
        assertEquals("Transition delay should be preserved", 2.0, importedTransition.getParameter(), 0.001);

        // Verify place properties are preserved
        PetriP[] importedPlaces = importedNet.getListP();
        boolean foundPlace1 = false, foundPlace2 = false;
        for (PetriP place : importedPlaces) {
            if ("TestPlace1".equals(place.getName())) {
                assertEquals("TestPlace1 marking should be preserved", 1, place.getMark());
                foundPlace1 = true;
            } else if ("TestPlace2".equals(place.getName())) {
                assertEquals("TestPlace2 marking should be preserved", 0, place.getMark());
                foundPlace2 = true;
            }
        }
        assertTrue("Should find TestPlace1", foundPlace1);
        assertTrue("Should find TestPlace2", foundPlace2);
    }
}
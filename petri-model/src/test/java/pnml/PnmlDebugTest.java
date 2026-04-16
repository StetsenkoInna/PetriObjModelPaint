package pnml;

import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

import ua.stetsenkoinna.PetriObj.*;
import ua.stetsenkoinna.pnml.PnmlParser;

import java.io.File;

/**
 * JUnit debug test class for PNML import functionality
 *
 * @author Serhii Rybak
 */
public class PnmlDebugTest {

    private PnmlParser parser;

    @Before
    public void setUp() {
        parser = new PnmlParser();
    }

    @Test
    public void testImportTest3PnmlFile() throws Exception {
        File testFile = new File(PnmlDebugTest.class.getResource("/pnml/test3.pnml").toURI());
        assertTrue("Test file should exist", testFile.exists());
        assertTrue("Test file should not be empty", testFile.length() > 0);

        PetriNet petriNet = parser.parse(testFile);

        // Verify basic structure
        assertNotNull("PetriNet should not be null", petriNet);
        assertNotNull("Net name should not be null", petriNet.getName());
        assertTrue("Should have at least one place", petriNet.getListP().length > 0);
        assertTrue("Should have at least one transition", petriNet.getListT().length > 0);
        assertTrue("Should have at least one input arc", petriNet.getArcIn().length > 0);
        assertTrue("Should have at least one output arc", petriNet.getArcOut().length > 0);

        // Verify places have valid properties
        for (int i = 0; i < petriNet.getListP().length; i++) {
            PetriP place = petriNet.getListP()[i];
            assertNotNull("Place " + i + " should not be null", place);
            assertNotNull("Place " + i + " should have a name", place.getName());
            assertNotNull("Place " + i + " should have an ID", place.getId());
            assertTrue("Place " + i + " should have valid number", place.getNumber() >= 0);
            assertTrue("Place " + i + " should have valid marking", place.getMark() >= 0);
        }

        // Verify transitions have valid properties
        for (int i = 0; i < petriNet.getListT().length; i++) {
            PetriT transition = petriNet.getListT()[i];
            assertNotNull("Transition " + i + " should not be null", transition);
            assertNotNull("Transition " + i + " should have a name", transition.getName());
            assertNotNull("Transition " + i + " should have an ID", transition.getId());
            assertTrue("Transition " + i + " should have valid number", transition.getNumber() >= 0);
            assertTrue("Transition " + i + " should have valid delay", transition.getParameter() >= 0);
        }

        // Verify input arcs have valid properties
        for (int i = 0; i < petriNet.getArcIn().length; i++) {
            ArcIn arc = petriNet.getArcIn()[i];
            assertNotNull("ArcIn " + i + " should not be null", arc);
            assertTrue("ArcIn " + i + " should have valid place number", arc.getNumP() >= 0);
            assertTrue("ArcIn " + i + " should have valid transition number", arc.getNumT() >= 0);
            assertTrue("ArcIn " + i + " should have positive weight", arc.getQuantity() > 0);
        }

        // Verify output arcs have valid properties
        for (int i = 0; i < petriNet.getArcOut().length; i++) {
            ArcOut arc = petriNet.getArcOut()[i];
            assertNotNull("ArcOut " + i + " should not be null", arc);
            assertTrue("ArcOut " + i + " should have valid transition number", arc.getNumT() >= 0);
            assertTrue("ArcOut " + i + " should have valid place number", arc.getNumP() >= 0);
            assertTrue("ArcOut " + i + " should have positive weight", arc.getQuantity() > 0);
        }
    }

    @Test
    public void testDetailedStructureValidation() throws Exception {
        File testFile = new File(PnmlDebugTest.class.getResource("/pnml/test3.pnml").toURI());
        PetriNet petriNet = parser.parse(testFile);

        // Test that all arcs connect valid places and transitions
        PetriP[] places = petriNet.getListP();
        PetriT[] transitions = petriNet.getListT();

        // Create sets of valid place and transition numbers
        java.util.Set<Integer> placeNumbers = new java.util.HashSet<>();
        java.util.Set<Integer> transitionNumbers = new java.util.HashSet<>();

        for (PetriP place : places) {
            placeNumbers.add(place.getNumber());
        }
        for (PetriT transition : transitions) {
            transitionNumbers.add(transition.getNumber());
        }

        // Verify all input arcs connect valid elements
        for (ArcIn arc : petriNet.getArcIn()) {
            assertTrue("ArcIn should connect to valid place: P" + arc.getNumP(),
                       placeNumbers.contains(arc.getNumP()));
            assertTrue("ArcIn should connect to valid transition: T" + arc.getNumT(),
                       transitionNumbers.contains(arc.getNumT()));
        }

        // Verify all output arcs connect valid elements
        for (ArcOut arc : petriNet.getArcOut()) {
            assertTrue("ArcOut should connect to valid transition: T" + arc.getNumT(),
                       transitionNumbers.contains(arc.getNumT()));
            assertTrue("ArcOut should connect to valid place: P" + arc.getNumP(),
                       placeNumbers.contains(arc.getNumP()));
        }
    }
}
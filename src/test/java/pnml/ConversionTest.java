package pnml;

import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

import ua.stetsenkoinna.PetriObj.*;
import ua.stetsenkoinna.graphnet.*;
import ua.stetsenkoinna.pnml.PnmlParser;

import java.io.File;
import java.util.ArrayList;

/**
 * JUnit test class for PetriNet to GraphPetriNet conversion
 *
 * @author Serhii Rybak
 */
public class ConversionTest {

    private PnmlParser parser;

    @Before
    public void setUp() {
        parser = new PnmlParser();
    }

    @Test
    public void testPetriNetToGraphPetriNetConversion() throws Exception {
        // Test with test3.pnml from resources
        File testFile = new File(ConversionTest.class.getResource("/pnml/test3.pnml").toURI());
        assertTrue("Test file should exist", testFile.exists());

        // Parse PNML
        PetriNet petriNet = parser.parse(testFile);
        assertNotNull("PetriNet should not be null", petriNet);
        assertTrue("Should have at least one place", petriNet.getListP().length > 0);
        assertTrue("Should have at least one transition", petriNet.getListT().length > 0);
        assertTrue("Should have at least one input arc", petriNet.getArcIn().length > 0);
        assertTrue("Should have at least one output arc", petriNet.getArcOut().length > 0);

        // Test conversion
        GraphPetriNet graphNet = convertPetriNetToGraphPetriNet(petriNet);
        assertNotNull("GraphPetriNet should not be null", graphNet);

        // Verify graph structure
        assertNotNull("Graph places list should not be null", graphNet.getGraphPetriPlaceList());
        assertNotNull("Graph transitions list should not be null", graphNet.getGraphPetriTransitionList());
        assertNotNull("Graph input arcs list should not be null", graphNet.getGraphArcInList());
        assertNotNull("Graph output arcs list should not be null", graphNet.getGraphArcOutList());

        assertEquals("Number of graph places should match original",
                     petriNet.getListP().length, graphNet.getGraphPetriPlaceList().size());
        assertEquals("Number of graph transitions should match original",
                     petriNet.getListT().length, graphNet.getGraphPetriTransitionList().size());
        assertEquals("Number of graph input arcs should match original",
                     petriNet.getArcIn().length, graphNet.getGraphArcInList().size());
        assertEquals("Number of graph output arcs should match original",
                     petriNet.getArcOut().length, graphNet.getGraphArcOutList().size());
    }

    @Test
    public void testGraphPetriNetToPetriNetRetrieval() throws Exception {
        File testFile = new File(ConversionTest.class.getResource("/pnml/test3.pnml").toURI());
        PetriNet originalNet = parser.parse(testFile);

        // Convert to GraphPetriNet and back
        GraphPetriNet graphNet = convertPetriNetToGraphPetriNet(originalNet);
        PetriNet retrievedNet = graphNet.getPetriNet();

        assertNotNull("Retrieved PetriNet should not be null", retrievedNet);
        assertEquals("Number of places should be preserved",
                     originalNet.getListP().length, retrievedNet.getListP().length);
        assertEquals("Number of transitions should be preserved",
                     originalNet.getListT().length, retrievedNet.getListT().length);
        assertEquals("Number of input arcs should be preserved",
                     originalNet.getArcIn().length, retrievedNet.getArcIn().length);
        assertEquals("Number of output arcs should be preserved",
                     originalNet.getArcOut().length, retrievedNet.getArcOut().length);
    }

    @Test
    public void testGraphElementCreation() throws Exception {
        File testFile = new File(ConversionTest.class.getResource("/pnml/test3.pnml").toURI());
        PetriNet petriNet = parser.parse(testFile);

        GraphPetriNet graphNet = convertPetriNetToGraphPetriNet(petriNet);

        // Verify that all graph places are created properly
        for (GraphPetriPlace graphPlace : graphNet.getGraphPetriPlaceList()) {
            assertNotNull("Graph place should not be null", graphPlace);
            assertNotNull("Graph place should have associated PetriP", graphPlace.getPetriPlace());
        }

        // Verify that all graph transitions are created properly
        for (GraphPetriTransition graphTransition : graphNet.getGraphPetriTransitionList()) {
            assertNotNull("Graph transition should not be null", graphTransition);
            assertNotNull("Graph transition should have associated PetriT", graphTransition.getPetriTransition());
        }

        // Verify that all graph arcs are created properly
        for (GraphArcIn graphArc : graphNet.getGraphArcInList()) {
            assertNotNull("Graph input arc should not be null", graphArc);
        }

        for (GraphArcOut graphArc : graphNet.getGraphArcOutList()) {
            assertNotNull("Graph output arc should not be null", graphArc);
        }
    }

    private static GraphPetriNet convertPetriNetToGraphPetriNet(PetriNet petriNet) throws Exception {
        try {
            // Reset counters to avoid conflicts with existing elements
            PetriP.initNext();
            PetriT.initNext();
            ArcIn.initNext();
            ArcOut.initNext();

            // Create lists for graph elements
            ArrayList<GraphPetriPlace> graphPlaces = new ArrayList<>();
            ArrayList<GraphPetriTransition> graphTransitions = new ArrayList<>();
            ArrayList<GraphArcIn> graphArcIns = new ArrayList<>();
            ArrayList<GraphArcOut> graphArcOuts = new ArrayList<>();

            // Convert places with proper constructor and coordinates
            for (int i = 0; i < petriNet.getListP().length; i++) {
                PetriP place = petriNet.getListP()[i];
                GraphPetriPlace graphPlace = new GraphPetriPlace(place, i);
                graphPlace.setNewCoordinates(new java.awt.geom.Point2D.Double(100 + i * 120, 100));
                graphPlaces.add(graphPlace);
            }

            // Convert transitions with proper constructor and coordinates
            for (int i = 0; i < petriNet.getListT().length; i++) {
                PetriT transition = petriNet.getListT()[i];
                GraphPetriTransition graphTransition = new GraphPetriTransition(transition, i);
                graphTransition.setNewCoordinates(new java.awt.geom.Point2D.Double(100 + i * 120, 250));
                graphTransitions.add(graphTransition);
            }

            // Convert input arcs (Place -> Transition)
            for (ArcIn arcIn : petriNet.getArcIn()) {
                GraphArcIn graphArcIn = new GraphArcIn(arcIn);

                // Find corresponding GraphPetriPlace and GraphPetriTransition
                GraphPetriPlace sourcePlace = findGraphPlaceByNumber(graphPlaces, arcIn.getNumP());
                GraphPetriTransition targetTransition = findGraphTransitionByNumber(graphTransitions, arcIn.getNumT());

                if (sourcePlace != null && targetTransition != null) {
                    // Initialize the arc properly
                    graphArcIn.settingNewArc(sourcePlace);
                    graphArcIn.finishSettingNewArc(targetTransition);
                } else {
                    System.out.println("Warning: Could not find elements for ArcIn P" + arcIn.getNumP() + " -> T" + arcIn.getNumT());
                }

                graphArcIns.add(graphArcIn);
            }

            // Convert output arcs (Transition -> Place)
            for (ArcOut arcOut : petriNet.getArcOut()) {
                GraphArcOut graphArcOut = new GraphArcOut(arcOut);

                // Find corresponding GraphPetriTransition and GraphPetriPlace
                GraphPetriTransition sourceTransition = findGraphTransitionByNumber(graphTransitions, arcOut.getNumT());
                GraphPetriPlace targetPlace = findGraphPlaceByNumber(graphPlaces, arcOut.getNumP());

                if (sourceTransition != null && targetPlace != null) {
                    // Initialize the arc properly
                    graphArcOut.settingNewArc(sourceTransition);
                    graphArcOut.finishSettingNewArc(targetPlace);
                } else {
                    System.out.println("Warning: Could not find elements for ArcOut T" + arcOut.getNumT() + " -> P" + arcOut.getNumP());
                }

                graphArcOuts.add(graphArcOut);
            }

            // Create GraphPetriNet using the existing PetriNet and graph elements
            GraphPetriNet graphNet = new GraphPetriNet(petriNet, graphPlaces, graphTransitions, graphArcIns, graphArcOuts);

            return graphNet;
        } catch (Exception e) {
            throw new Exception("Error converting PetriNet to GraphPetriNet: " + e.getMessage(), e);
        }
    }

    /**
     * Find GraphPetriPlace by its number
     */
    private static GraphPetriPlace findGraphPlaceByNumber(ArrayList<GraphPetriPlace> places, int number) {
        for (GraphPetriPlace place : places) {
            if (place.getPetriPlace().getNumber() == number) {
                return place;
            }
        }
        return null;
    }

    /**
     * Find GraphPetriTransition by its number
     */
    private static GraphPetriTransition findGraphTransitionByNumber(ArrayList<GraphPetriTransition> transitions, int number) {
        for (GraphPetriTransition transition : transitions) {
            if (transition.getPetriTransition().getNumber() == number) {
                return transition;
            }
        }
        return null;
    }
}
package pnml;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

import ua.stetsenkoinna.PetriObj.*;
import ua.stetsenkoinna.pnml.PnmlGenerator;
import ua.stetsenkoinna.pnml.PnmlParser;

import java.io.File;
import java.util.ArrayList;

/**
 * JUnit test class for Transition and Place parameter import/export functionality in PNML format
 *
 * @author Serhii Rybak
 */
public class TransitionPlaceParameterTest {

    private File tempFile;
    private PnmlGenerator generator;
    private PnmlParser parser;

    @Before
    public void setUp() {
        tempFile = new File("target/test-classes/test_transition_place_params_junit.pnml");
        generator = new PnmlGenerator();
        parser = new PnmlParser();
    }

    @After
    public void tearDown() {
        if (tempFile != null && tempFile.exists()) {
            tempFile.delete();
        }
    }

    @Test
    public void testPlaceInitialMarkingParameterExportImport() throws Exception {
        // Create test net with place marking parameter
        PetriNet testNet = createTestNetWithPlaceMarkingParameter();

        // Export and import
        generator.generate(testNet, tempFile);
        PetriNet importedNet = parser.parse(tempFile);

        // Verify place marking parameter is preserved
        PetriP[] originalPlaces = testNet.getListP();
        PetriP[] importedPlaces = importedNet.getListP();

        assertEquals("Number of places should match", originalPlaces.length, importedPlaces.length);

        PetriP originalPlace = findPlaceById("p1", originalPlaces);
        PetriP importedPlace = findPlaceById("p1", importedPlaces);

        assertNotNull("Should find original place", originalPlace);
        assertNotNull("Should find imported place", importedPlace);

        assertEquals("Mark parameter flag should be preserved", originalPlace.markIsParam(), importedPlace.markIsParam());
        assertTrue("Place should have mark parameter", importedPlace.markIsParam());
        assertEquals("Mark parameter name should be preserved", originalPlace.getMarkParamName(), importedPlace.getMarkParamName());
    }

    @Test
    public void testTransitionTimeDelayParameterExportImport() throws Exception {
        // Create test net with transition time delay parameter
        PetriNet testNet = createTestNetWithTransitionTimeDelayParameter();

        // Export and import
        generator.generate(testNet, tempFile);
        PetriNet importedNet = parser.parse(tempFile);

        // Verify transition time delay parameter is preserved
        PetriT[] originalTransitions = testNet.getListT();
        PetriT[] importedTransitions = importedNet.getListT();

        assertEquals("Number of transitions should match", originalTransitions.length, importedTransitions.length);

        PetriT originalTransition = findTransitionById("t1", originalTransitions);
        PetriT importedTransition = findTransitionById("t1", importedTransitions);

        assertNotNull("Should find original transition", originalTransition);
        assertNotNull("Should find imported transition", importedTransition);

        assertEquals("Time delay parameter flag should be preserved", originalTransition.parametrIsParam(), importedTransition.parametrIsParam());
        assertTrue("Transition should have time delay parameter", importedTransition.parametrIsParam());
        assertEquals("Time delay parameter name should be preserved", originalTransition.getParametrParamName(), importedTransition.getParametrParamName());
    }

    @Test
    public void testTransitionPriorityParameterExportImport() throws Exception {
        // Create test net with transition priority parameter
        PetriNet testNet = createTestNetWithTransitionPriorityParameter();

        // Export and import
        generator.generate(testNet, tempFile);
        PetriNet importedNet = parser.parse(tempFile);

        // Verify transition priority parameter is preserved
        PetriT[] originalTransitions = testNet.getListT();
        PetriT[] importedTransitions = importedNet.getListT();

        assertEquals("Number of transitions should match", originalTransitions.length, importedTransitions.length);

        PetriT originalTransition = findTransitionById("t1", originalTransitions);
        PetriT importedTransition = findTransitionById("t1", importedTransitions);

        assertNotNull("Should find original transition", originalTransition);
        assertNotNull("Should find imported transition", importedTransition);

        assertEquals("Priority parameter flag should be preserved", originalTransition.priorityIsParam(), importedTransition.priorityIsParam());
        assertTrue("Transition should have priority parameter", importedTransition.priorityIsParam());
        assertEquals("Priority parameter name should be preserved", originalTransition.getPriorityParamName(), importedTransition.getPriorityParamName());
    }

    @Test
    public void testTransitionProbabilityParameterExportImport() throws Exception {
        // Create test net with transition probability parameter
        PetriNet testNet = createTestNetWithTransitionProbabilityParameter();

        // Export and import
        generator.generate(testNet, tempFile);
        PetriNet importedNet = parser.parse(tempFile);

        // Verify transition probability parameter is preserved
        PetriT[] originalTransitions = testNet.getListT();
        PetriT[] importedTransitions = importedNet.getListT();

        assertEquals("Number of transitions should match", originalTransitions.length, importedTransitions.length);

        PetriT originalTransition = findTransitionById("t1", originalTransitions);
        PetriT importedTransition = findTransitionById("t1", importedTransitions);

        assertNotNull("Should find original transition", originalTransition);
        assertNotNull("Should find imported transition", importedTransition);

        assertEquals("Probability parameter flag should be preserved", originalTransition.probabilityIsParam(), importedTransition.probabilityIsParam());
        assertTrue("Transition should have probability parameter", importedTransition.probabilityIsParam());
        assertEquals("Probability parameter name should be preserved", originalTransition.getProbabilityParamName(), importedTransition.getProbabilityParamName());
    }

    @Test
    public void testTransitionDistributionParameterExportImport() throws Exception {
        // Create test net with transition distribution parameter
        PetriNet testNet = createTestNetWithTransitionDistributionParameter();

        // Export and import
        generator.generate(testNet, tempFile);
        PetriNet importedNet = parser.parse(tempFile);

        // Verify transition distribution parameter is preserved
        PetriT[] originalTransitions = testNet.getListT();
        PetriT[] importedTransitions = importedNet.getListT();

        assertEquals("Number of transitions should match", originalTransitions.length, importedTransitions.length);

        PetriT originalTransition = findTransitionById("t1", originalTransitions);
        PetriT importedTransition = findTransitionById("t1", importedTransitions);

        assertNotNull("Should find original transition", originalTransition);
        assertNotNull("Should find imported transition", importedTransition);

        assertEquals("Distribution parameter flag should be preserved", originalTransition.distributionIsParam(), importedTransition.distributionIsParam());
        assertTrue("Transition should have distribution parameter", importedTransition.distributionIsParam());
        assertEquals("Distribution parameter name should be preserved", originalTransition.getDistributionParamName(), importedTransition.getDistributionParamName());
    }

    @Test
    public void testTransitionWithMultipleParametersExportImport() throws Exception {
        // Create test net with transition having multiple parameters
        PetriNet testNet = createTestNetWithMultipleTransitionParameters();

        // Export and import
        generator.generate(testNet, tempFile);
        PetriNet importedNet = parser.parse(tempFile);

        // Verify all transition parameters are preserved
        PetriT[] originalTransitions = testNet.getListT();
        PetriT[] importedTransitions = importedNet.getListT();

        assertEquals("Number of transitions should match", originalTransitions.length, importedTransitions.length);

        PetriT originalTransition = findTransitionById("t1", originalTransitions);
        PetriT importedTransition = findTransitionById("t1", importedTransitions);

        assertNotNull("Should find original transition", originalTransition);
        assertNotNull("Should find imported transition", importedTransition);

        // Verify time delay parameter
        assertEquals("Time delay parameter flag should be preserved", originalTransition.parametrIsParam(), importedTransition.parametrIsParam());
        assertTrue("Transition should have time delay parameter", importedTransition.parametrIsParam());
        assertEquals("Time delay parameter name should be preserved", originalTransition.getParametrParamName(), importedTransition.getParametrParamName());

        // Verify priority parameter
        assertEquals("Priority parameter flag should be preserved", originalTransition.priorityIsParam(), importedTransition.priorityIsParam());
        assertTrue("Transition should have priority parameter", importedTransition.priorityIsParam());
        assertEquals("Priority parameter name should be preserved", originalTransition.getPriorityParamName(), importedTransition.getPriorityParamName());

        // Verify probability parameter
        assertEquals("Probability parameter flag should be preserved", originalTransition.probabilityIsParam(), importedTransition.probabilityIsParam());
        assertTrue("Transition should have probability parameter", importedTransition.probabilityIsParam());
        assertEquals("Probability parameter name should be preserved", originalTransition.getProbabilityParamName(), importedTransition.getProbabilityParamName());

        // Verify distribution parameter
        assertEquals("Distribution parameter flag should be preserved", originalTransition.distributionIsParam(), importedTransition.distributionIsParam());
        assertTrue("Transition should have distribution parameter", importedTransition.distributionIsParam());
        assertEquals("Distribution parameter name should be preserved", originalTransition.getDistributionParamName(), importedTransition.getDistributionParamName());
    }

    @Test
    public void testTransitionMeanAndStandardDeviationExportImport() throws Exception {
        // Create test net with transition having mean value and standard deviation
        PetriNet testNet = createTestNetWithMeanAndStandardDeviation();

        // Export and import
        generator.generate(testNet, tempFile);
        PetriNet importedNet = parser.parse(tempFile);

        // Verify mean and standard deviation are preserved
        PetriT[] originalTransitions = testNet.getListT();
        PetriT[] importedTransitions = importedNet.getListT();

        assertEquals("Number of transitions should match", originalTransitions.length, importedTransitions.length);

        PetriT originalTransition = findTransitionById("t1", originalTransitions);
        PetriT importedTransition = findTransitionById("t1", importedTransitions);

        assertNotNull("Should find original transition", originalTransition);
        assertNotNull("Should find imported transition", importedTransition);

        assertEquals("Mean value should be preserved", originalTransition.getParametr(), importedTransition.getParametr(), 0.001);
        assertEquals("Standard deviation should be preserved", originalTransition.getParamDeviation(), importedTransition.getParamDeviation(), 0.001);
    }

    @Test
    public void testMixedParametersAndValuesExportImport() throws Exception {
        // Create test net with mixed parameters and fixed values
        PetriNet testNet = createTestNetWithMixedParametersAndValues();

        // Export and import
        generator.generate(testNet, tempFile);
        PetriNet importedNet = parser.parse(tempFile);

        // Verify places
        PetriP[] originalPlaces = testNet.getListP();
        PetriP[] importedPlaces = importedNet.getListP();

        assertEquals("Number of places should match", originalPlaces.length, importedPlaces.length);

        // Check place with parameter
        PetriP originalPlaceParam = findPlaceById("p1", originalPlaces);
        PetriP importedPlaceParam = findPlaceById("p1", importedPlaces);
        assertEquals("Place parameter flag should be preserved", originalPlaceParam.markIsParam(), importedPlaceParam.markIsParam());
        assertTrue("Place should have mark parameter", importedPlaceParam.markIsParam());
        assertEquals("Place parameter name should be preserved", originalPlaceParam.getMarkParamName(), importedPlaceParam.getMarkParamName());

        // Check place with fixed value
        PetriP originalPlaceFixed = findPlaceById("p2", originalPlaces);
        PetriP importedPlaceFixed = findPlaceById("p2", importedPlaces);
        assertEquals("Place parameter flag should be preserved", originalPlaceFixed.markIsParam(), importedPlaceFixed.markIsParam());
        assertFalse("Place should not have mark parameter", importedPlaceFixed.markIsParam());
        assertEquals("Place marking should be preserved", originalPlaceFixed.getMark(), importedPlaceFixed.getMark());

        // Verify transitions
        PetriT[] originalTransitions = testNet.getListT();
        PetriT[] importedTransitions = importedNet.getListT();

        assertEquals("Number of transitions should match", originalTransitions.length, importedTransitions.length);

        // Check transition with parameters
        PetriT originalTransParam = findTransitionById("t1", originalTransitions);
        PetriT importedTransParam = findTransitionById("t1", importedTransitions);
        assertEquals("Transition parameter flag should be preserved", originalTransParam.parametrIsParam(), importedTransParam.parametrIsParam());
        assertTrue("Transition should have time delay parameter", importedTransParam.parametrIsParam());

        // Check transition with fixed values
        PetriT originalTransFixed = findTransitionById("t2", originalTransitions);
        PetriT importedTransFixed = findTransitionById("t2", importedTransitions);
        assertEquals("Transition parameter flag should be preserved", originalTransFixed.parametrIsParam(), importedTransFixed.parametrIsParam());
        assertFalse("Transition should not have time delay parameter", importedTransFixed.parametrIsParam());
        assertEquals("Transition time delay should be preserved", originalTransFixed.getParametr(), importedTransFixed.getParametr(), 0.001);
        assertEquals("Transition priority should be preserved", originalTransFixed.getPriority(), importedTransFixed.getPriority());
        assertEquals("Transition probability should be preserved", originalTransFixed.getProbability(), importedTransFixed.getProbability(), 0.001);
        assertEquals("Transition distribution should be preserved", originalTransFixed.getDistribution(), importedTransFixed.getDistribution());
    }

    private PetriNet createTestNetWithPlaceMarkingParameter() throws Exception {
        ArrayList<PetriP> places = new ArrayList<>();
        ArrayList<PetriT> transitions = new ArrayList<>();
        ArrayList<ArcIn> arcIns = new ArrayList<>();
        ArrayList<ArcOut> arcOuts = new ArrayList<>();

        PetriP p1 = new PetriP("p1", "Place1", 0);
        p1.setMarkParam("testMarkParam");
        PetriT t1 = new PetriT("t1", "Transition1", 1.0);
        places.add(p1);
        transitions.add(t1);

        ArcIn arcIn = new ArcIn(p1.getNumber(), t1.getNumber(), 1);
        arcIn.setNameP(p1.getId());
        arcIn.setNameT(t1.getId());
        arcIns.add(arcIn);

        ArcOut arcOut = new ArcOut(t1.getNumber(), p1.getNumber(), 1);
        arcOut.setNameT(t1.getId());
        arcOut.setNameP(p1.getId());
        arcOuts.add(arcOut);

        return new PetriNet("TestNet", places, transitions, arcIns, arcOuts);
    }

    private PetriNet createTestNetWithTransitionTimeDelayParameter() throws Exception {
        ArrayList<PetriP> places = new ArrayList<>();
        ArrayList<PetriT> transitions = new ArrayList<>();
        ArrayList<ArcIn> arcIns = new ArrayList<>();
        ArrayList<ArcOut> arcOuts = new ArrayList<>();

        PetriP p1 = new PetriP("p1", "Place1", 1);
        PetriT t1 = new PetriT("t1", "Transition1", 0.0);
        t1.setParametrParam("testTimeDelayParam");
        places.add(p1);
        transitions.add(t1);

        ArcIn arcIn = new ArcIn(p1.getNumber(), t1.getNumber(), 1);
        arcIn.setNameP(p1.getId());
        arcIn.setNameT(t1.getId());
        arcIns.add(arcIn);

        ArcOut arcOut = new ArcOut(t1.getNumber(), p1.getNumber(), 1);
        arcOut.setNameT(t1.getId());
        arcOut.setNameP(p1.getId());
        arcOuts.add(arcOut);

        return new PetriNet("TestNet", places, transitions, arcIns, arcOuts);
    }

    private PetriNet createTestNetWithTransitionPriorityParameter() throws Exception {
        ArrayList<PetriP> places = new ArrayList<>();
        ArrayList<PetriT> transitions = new ArrayList<>();
        ArrayList<ArcIn> arcIns = new ArrayList<>();
        ArrayList<ArcOut> arcOuts = new ArrayList<>();

        PetriP p1 = new PetriP("p1", "Place1", 1);
        PetriT t1 = new PetriT("t1", "Transition1", 1.0);
        t1.setPriorityParam("testPriorityParam");
        places.add(p1);
        transitions.add(t1);

        ArcIn arcIn = new ArcIn(p1.getNumber(), t1.getNumber(), 1);
        arcIn.setNameP(p1.getId());
        arcIn.setNameT(t1.getId());
        arcIns.add(arcIn);

        ArcOut arcOut = new ArcOut(t1.getNumber(), p1.getNumber(), 1);
        arcOut.setNameT(t1.getId());
        arcOut.setNameP(p1.getId());
        arcOuts.add(arcOut);

        return new PetriNet("TestNet", places, transitions, arcIns, arcOuts);
    }

    private PetriNet createTestNetWithTransitionProbabilityParameter() throws Exception {
        ArrayList<PetriP> places = new ArrayList<>();
        ArrayList<PetriT> transitions = new ArrayList<>();
        ArrayList<ArcIn> arcIns = new ArrayList<>();
        ArrayList<ArcOut> arcOuts = new ArrayList<>();

        PetriP p1 = new PetriP("p1", "Place1", 1);
        PetriT t1 = new PetriT("t1", "Transition1", 0.5);
        t1.setProbabilityParam("testProbabilityParam");
        places.add(p1);
        transitions.add(t1);

        ArcIn arcIn = new ArcIn(p1.getNumber(), t1.getNumber(), 1);
        arcIn.setNameP(p1.getId());
        arcIn.setNameT(t1.getId());
        arcIns.add(arcIn);

        ArcOut arcOut = new ArcOut(t1.getNumber(), p1.getNumber(), 1);
        arcOut.setNameT(t1.getId());
        arcOut.setNameP(p1.getId());
        arcOuts.add(arcOut);

        return new PetriNet("TestNet", places, transitions, arcIns, arcOuts);
    }

    private PetriNet createTestNetWithTransitionDistributionParameter() throws Exception {
        ArrayList<PetriP> places = new ArrayList<>();
        ArrayList<PetriT> transitions = new ArrayList<>();
        ArrayList<ArcIn> arcIns = new ArrayList<>();
        ArrayList<ArcOut> arcOuts = new ArrayList<>();

        PetriP p1 = new PetriP("p1", "Place1", 1);
        PetriT t1 = new PetriT("t1", "Transition1", 1.5);
        t1.setDistributionParam("testDistributionParam");
        places.add(p1);
        transitions.add(t1);

        ArcIn arcIn = new ArcIn(p1.getNumber(), t1.getNumber(), 1);
        arcIn.setNameP(p1.getId());
        arcIn.setNameT(t1.getId());
        arcIns.add(arcIn);

        ArcOut arcOut = new ArcOut(t1.getNumber(), p1.getNumber(), 1);
        arcOut.setNameT(t1.getId());
        arcOut.setNameP(p1.getId());
        arcOuts.add(arcOut);

        return new PetriNet("TestNet", places, transitions, arcIns, arcOuts);
    }

    private PetriNet createTestNetWithMultipleTransitionParameters() throws Exception {
        ArrayList<PetriP> places = new ArrayList<>();
        ArrayList<PetriT> transitions = new ArrayList<>();
        ArrayList<ArcIn> arcIns = new ArrayList<>();
        ArrayList<ArcOut> arcOuts = new ArrayList<>();

        PetriP p1 = new PetriP("p1", "Place1", 1);
        PetriT t1 = new PetriT("t1", "Transition1", 0.0);
        t1.setParametrParam("testTimeDelayParam");
        t1.setPriorityParam("testPriorityParam");
        t1.setProbabilityParam("testProbabilityParam");
        t1.setDistributionParam("testDistributionParam");

        places.add(p1);
        transitions.add(t1);

        ArcIn arcIn = new ArcIn(p1.getNumber(), t1.getNumber(), 1);
        arcIn.setNameP(p1.getId());
        arcIn.setNameT(t1.getId());
        arcIns.add(arcIn);

        ArcOut arcOut = new ArcOut(t1.getNumber(), p1.getNumber(), 1);
        arcOut.setNameT(t1.getId());
        arcOut.setNameP(p1.getId());
        arcOuts.add(arcOut);

        return new PetriNet("TestNet", places, transitions, arcIns, arcOuts);
    }

    private PetriNet createTestNetWithMixedParametersAndValues() throws Exception {
        ArrayList<PetriP> places = new ArrayList<>();
        ArrayList<PetriT> transitions = new ArrayList<>();
        ArrayList<ArcIn> arcIns = new ArrayList<>();
        ArrayList<ArcOut> arcOuts = new ArrayList<>();

        // Place with parameter
        PetriP p1 = new PetriP("p1", "Place1", 0);
        p1.setMarkParam("testMarkParam");
        places.add(p1);

        // Place with fixed value
        PetriP p2 = new PetriP("p2", "Place2", 3);
        places.add(p2);

        // Transition with parameter
        PetriT t1 = new PetriT("t1", "Transition1", 0.0);
        t1.setParametrParam("testTimeDelayParam");
        transitions.add(t1);

        // Transition with fixed values
        PetriT t2 = new PetriT("t2", "Transition2", 2.5);
        t2.setPriority(1);
        t2.setProbability(0.8);
        t2.setDistribution("exponential", 2.5);
        transitions.add(t2);

        // Create arcs
        ArcIn arcIn1 = new ArcIn(p1.getNumber(), t1.getNumber(), 1);
        arcIn1.setNameP(p1.getId());
        arcIn1.setNameT(t1.getId());
        arcIns.add(arcIn1);

        ArcIn arcIn2 = new ArcIn(p2.getNumber(), t2.getNumber(), 1);
        arcIn2.setNameP(p2.getId());
        arcIn2.setNameT(t2.getId());
        arcIns.add(arcIn2);

        ArcOut arcOut1 = new ArcOut(t1.getNumber(), p2.getNumber(), 1);
        arcOut1.setNameT(t1.getId());
        arcOut1.setNameP(p2.getId());
        arcOuts.add(arcOut1);

        ArcOut arcOut2 = new ArcOut(t2.getNumber(), p1.getNumber(), 1);
        arcOut2.setNameT(t2.getId());
        arcOut2.setNameP(p1.getId());
        arcOuts.add(arcOut2);

        return new PetriNet("TestNet", places, transitions, arcIns, arcOuts);
    }

    private PetriNet createTestNetWithMeanAndStandardDeviation() throws Exception {
        ArrayList<PetriP> places = new ArrayList<>();
        ArrayList<PetriT> transitions = new ArrayList<>();
        ArrayList<ArcIn> arcIns = new ArrayList<>();
        ArrayList<ArcOut> arcOuts = new ArrayList<>();

        PetriP p1 = new PetriP("p1", "Place1", 1);
        PetriP p2 = new PetriP("p2", "Place2", 0);
        PetriT t1 = new PetriT("t1", "Transition1", 2.5); // mean value
        t1.setParamDeviation(0.7); // standard deviation
        places.add(p1);
        places.add(p2);
        transitions.add(t1);

        ArcIn arcIn = new ArcIn(p1.getNumber(), t1.getNumber(), 1);
        arcIn.setNameP(p1.getId());
        arcIn.setNameT(t1.getId());
        arcIns.add(arcIn);

        ArcOut arcOut = new ArcOut(t1.getNumber(), p2.getNumber(), 1);
        arcOut.setNameT(t1.getId());
        arcOut.setNameP(p2.getId());
        arcOuts.add(arcOut);

        return new PetriNet("TestNet", places, transitions, arcIns, arcOuts);
    }

    private PetriP findPlaceById(String id, PetriP[] places) {
        for (PetriP place : places) {
            if (id.equals(place.getId())) {
                return place;
            }
        }
        return null;
    }

    private PetriT findTransitionById(String id, PetriT[] transitions) {
        for (PetriT transition : transitions) {
            if (id.equals(transition.getId())) {
                return transition;
            }
        }
        return null;
    }
}
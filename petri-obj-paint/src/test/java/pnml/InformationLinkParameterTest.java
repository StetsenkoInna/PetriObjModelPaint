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
 * JUnit test class for Information link parameter import/export functionality in PNML format
 *
 * @author Serhii Rybak
 */
public class InformationLinkParameterTest {

    private File tempFile;
    private PnmlGenerator generator;
    private PnmlParser parser;

    @Before
    public void setUp() {
        tempFile = new File("target/test-classes/test_info_link_param_junit.pnml");
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
    public void testInformationalFlagExportImport() throws Exception {
        // Create test net with informational flag
        PetriNet testNet = createSimpleTestNetWithInformationalFlag();

        // Export and import
        generator.generate(testNet, tempFile);
        PetriNet importedNet = parser.parse(tempFile);

        // Verify informational flag is preserved
        ArcIn[] originalArcs = testNet.getArcIn();
        ArcIn[] importedArcs = importedNet.getArcIn();

        assertEquals("Number of input arcs should match", originalArcs.length, importedArcs.length);

        // Find the informational arc specifically
        ArcIn originalInfArc = null;
        for (ArcIn arc : originalArcs) {
            if (arc.getIsInf()) {
                originalInfArc = arc;
                break;
            }
        }
        assertNotNull("Should have an informational arc in original", originalInfArc);

        ArcIn importedInfArc = findMatchingArcIn(originalInfArc, importedArcs);
        assertNotNull("Should find matching imported informational arc", importedInfArc);
        assertEquals("Informational flag should be preserved", originalInfArc.getIsInf(), importedInfArc.getIsInf());
        assertTrue("Arc should be informational", importedInfArc.getIsInf());
    }

    @Test
    public void testInformationalParameterExportImport() throws Exception {
        // Create test net with informational parameter
        PetriNet testNet = createSimpleTestNetWithInformationalParameter();

        // Export and import
        generator.generate(testNet, tempFile);
        PetriNet importedNet = parser.parse(tempFile);

        // Verify informational parameter is preserved
        ArcIn[] originalArcs = testNet.getArcIn();
        ArcIn[] importedArcs = importedNet.getArcIn();

        assertEquals("Number of input arcs should match", originalArcs.length, importedArcs.length);

        ArcIn originalArc = originalArcs[0];
        ArcIn importedArc = findMatchingArcIn(originalArc, importedArcs);

        assertNotNull("Should find matching imported arc", importedArc);
        assertEquals("Informational parameter flag should be preserved", originalArc.infIsParam(), importedArc.infIsParam());
        assertTrue("Arc should have informational parameter", importedArc.infIsParam());
        assertEquals("Informational parameter name should be preserved",
            originalArc.getInfParamName(), importedArc.getInfParamName());
    }

    @Test
    public void testMultiplicityParameterExportImport() throws Exception {
        // Create test net with multiplicity parameter
        PetriNet testNet = createSimpleTestNetWithMultiplicityParameter();

        // Export and import
        generator.generate(testNet, tempFile);
        PetriNet importedNet = parser.parse(tempFile);

        // Verify multiplicity parameter is preserved
        ArcIn[] originalArcs = testNet.getArcIn();
        ArcIn[] importedArcs = importedNet.getArcIn();

        assertEquals("Number of input arcs should match", originalArcs.length, importedArcs.length);

        ArcIn originalArc = originalArcs[0];
        ArcIn importedArc = findMatchingArcIn(originalArc, importedArcs);

        assertNotNull("Should find matching imported arc", importedArc);
        assertEquals("Multiplicity parameter flag should be preserved", originalArc.kIsParam(), importedArc.kIsParam());
        assertTrue("Arc should have multiplicity parameter", importedArc.kIsParam());
        assertEquals("Multiplicity parameter name should be preserved",
            originalArc.getKParamName(), importedArc.getKParamName());
    }

    @Test
    public void testCombinedParametersExportImport() throws Exception {
        // Create test net with both informational and multiplicity parameters
        PetriNet testNet = createTestNetWithCombinedParameters();

        // Export and import
        generator.generate(testNet, tempFile);
        PetriNet importedNet = parser.parse(tempFile);

        // Verify both parameters are preserved
        ArcIn[] originalArcs = testNet.getArcIn();
        ArcIn[] importedArcs = importedNet.getArcIn();

        assertEquals("Number of input arcs should match", originalArcs.length, importedArcs.length);

        ArcIn originalArc = originalArcs[0];
        ArcIn importedArc = findMatchingArcIn(originalArc, importedArcs);

        assertNotNull("Should find matching imported arc", importedArc);

        // Verify informational parameter
        assertEquals("Informational parameter flag should be preserved", originalArc.infIsParam(), importedArc.infIsParam());
        assertTrue("Arc should have informational parameter", importedArc.infIsParam());
        assertEquals("Informational parameter name should be preserved",
            originalArc.getInfParamName(), importedArc.getInfParamName());

        // Verify multiplicity parameter
        assertEquals("Multiplicity parameter flag should be preserved", originalArc.kIsParam(), importedArc.kIsParam());
        assertTrue("Arc should have multiplicity parameter", importedArc.kIsParam());
        assertEquals("Multiplicity parameter name should be preserved",
            originalArc.getKParamName(), importedArc.getKParamName());
    }

    @Test
    public void testOutputArcMultiplicityParameter() throws Exception {
        // Create test net with output arc multiplicity parameter
        PetriNet testNet = createTestNetWithOutputArcParameter();

        // Export and import
        generator.generate(testNet, tempFile);
        PetriNet importedNet = parser.parse(tempFile);

        // Verify output arc parameter is preserved
        ArcOut[] originalArcs = testNet.getArcOut();
        ArcOut[] importedArcs = importedNet.getArcOut();

        assertEquals("Number of output arcs should match", originalArcs.length, importedArcs.length);

        ArcOut originalArc = originalArcs[0];
        ArcOut importedArc = findMatchingArcOut(originalArc, importedArcs);

        assertNotNull("Should find matching imported arc", importedArc);
        assertEquals("Multiplicity parameter flag should be preserved", originalArc.kIsParam(), importedArc.kIsParam());
        assertTrue("Arc should have multiplicity parameter", importedArc.kIsParam());
        assertEquals("Multiplicity parameter name should be preserved",
            originalArc.getKParamName(), importedArc.getKParamName());
    }

    @Test
    public void testComplexNetworkWithMixedParameters() throws Exception {
        // Create complex test network with various parameter combinations
        PetriNet testNet = createComplexTestNet();

        // Export and import
        generator.generate(testNet, tempFile);
        PetriNet importedNet = parser.parse(tempFile);

        // Verify all parameters are preserved
        verifyCompleteParameterPreservation(testNet, importedNet);
    }

    private PetriNet createSimpleTestNetWithInformationalFlag() throws Exception {
        ArrayList<PetriP> places = new ArrayList<>();
        ArrayList<PetriT> transitions = new ArrayList<>();
        ArrayList<ArcIn> arcIns = new ArrayList<>();
        ArrayList<ArcOut> arcOuts = new ArrayList<>();

        PetriP p1 = new PetriP("p1", "Place1", 1);
        PetriP p2 = new PetriP("p2", "Place2", 0);
        PetriP p3 = new PetriP("p3", "Place3", 1);
        PetriT t1 = new PetriT("t1", "Transition1", 1.0);
        places.add(p1);
        places.add(p2);
        places.add(p3);
        transitions.add(t1);

        // Informational arc (this is what we're testing)
        ArcIn arcInInf = new ArcIn(p1.getNumber(), t1.getNumber(), 1);
        arcInInf.setNameP(p1.getId());
        arcInInf.setNameT(t1.getId());
        arcInInf.setInf(true);
        arcIns.add(arcInInf);

        // Regular arc (needed for transition validation)
        ArcIn arcInRegular = new ArcIn(p3.getNumber(), t1.getNumber(), 1);
        arcInRegular.setNameP(p3.getId());
        arcInRegular.setNameT(t1.getId());
        arcIns.add(arcInRegular);

        ArcOut arcOut = new ArcOut(t1.getNumber(), p2.getNumber(), 1);
        arcOut.setNameT(t1.getId());
        arcOut.setNameP(p2.getId());
        arcOuts.add(arcOut);

        return new PetriNet("TestNet", places, transitions, arcIns, arcOuts);
    }

    private PetriNet createSimpleTestNetWithInformationalParameter() throws Exception {
        ArrayList<PetriP> places = new ArrayList<>();
        ArrayList<PetriT> transitions = new ArrayList<>();
        ArrayList<ArcIn> arcIns = new ArrayList<>();
        ArrayList<ArcOut> arcOuts = new ArrayList<>();

        PetriP p1 = new PetriP("p1", "Place1", 1);
        PetriP p2 = new PetriP("p2", "Place2", 0);
        PetriT t1 = new PetriT("t1", "Transition1", 1.0);
        places.add(p1);
        places.add(p2);
        transitions.add(t1);

        ArcIn arcIn = new ArcIn(p1.getNumber(), t1.getNumber(), 1);
        arcIn.setNameP(p1.getId());
        arcIn.setNameT(t1.getId());
        arcIn.setInfParam("testInfoParam");
        arcIns.add(arcIn);

        ArcOut arcOut = new ArcOut(t1.getNumber(), p2.getNumber(), 1);
        arcOut.setNameT(t1.getId());
        arcOut.setNameP(p2.getId());
        arcOuts.add(arcOut);

        return new PetriNet("TestNet", places, transitions, arcIns, arcOuts);
    }

    private PetriNet createSimpleTestNetWithMultiplicityParameter() throws Exception {
        ArrayList<PetriP> places = new ArrayList<>();
        ArrayList<PetriT> transitions = new ArrayList<>();
        ArrayList<ArcIn> arcIns = new ArrayList<>();
        ArrayList<ArcOut> arcOuts = new ArrayList<>();

        PetriP p1 = new PetriP("p1", "Place1", 1);
        PetriP p2 = new PetriP("p2", "Place2", 0);
        PetriT t1 = new PetriT("t1", "Transition1", 1.0);
        places.add(p1);
        places.add(p2);
        transitions.add(t1);

        ArcIn arcIn = new ArcIn(p1.getNumber(), t1.getNumber(), 1);
        arcIn.setNameP(p1.getId());
        arcIn.setNameT(t1.getId());
        arcIn.setKParam("testKParam");
        arcIns.add(arcIn);

        ArcOut arcOut = new ArcOut(t1.getNumber(), p2.getNumber(), 1);
        arcOut.setNameT(t1.getId());
        arcOut.setNameP(p2.getId());
        arcOuts.add(arcOut);

        return new PetriNet("TestNet", places, transitions, arcIns, arcOuts);
    }

    private PetriNet createTestNetWithCombinedParameters() throws Exception {
        ArrayList<PetriP> places = new ArrayList<>();
        ArrayList<PetriT> transitions = new ArrayList<>();
        ArrayList<ArcIn> arcIns = new ArrayList<>();
        ArrayList<ArcOut> arcOuts = new ArrayList<>();

        PetriP p1 = new PetriP("p1", "Place1", 1);
        PetriP p2 = new PetriP("p2", "Place2", 0);
        PetriT t1 = new PetriT("t1", "Transition1", 1.0);
        places.add(p1);
        places.add(p2);
        transitions.add(t1);

        ArcIn arcIn = new ArcIn(p1.getNumber(), t1.getNumber(), 1);
        arcIn.setNameP(p1.getId());
        arcIn.setNameT(t1.getId());
        arcIn.setInfParam("testInfoParam");
        arcIn.setKParam("testKParam");
        arcIns.add(arcIn);

        ArcOut arcOut = new ArcOut(t1.getNumber(), p2.getNumber(), 1);
        arcOut.setNameT(t1.getId());
        arcOut.setNameP(p2.getId());
        arcOuts.add(arcOut);

        return new PetriNet("TestNet", places, transitions, arcIns, arcOuts);
    }

    private PetriNet createTestNetWithOutputArcParameter() throws Exception {
        ArrayList<PetriP> places = new ArrayList<>();
        ArrayList<PetriT> transitions = new ArrayList<>();
        ArrayList<ArcIn> arcIns = new ArrayList<>();
        ArrayList<ArcOut> arcOuts = new ArrayList<>();

        PetriP p1 = new PetriP("p1", "Place1", 1);
        PetriP p2 = new PetriP("p2", "Place2", 0);
        PetriT t1 = new PetriT("t1", "Transition1", 1.0);
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
        arcOut.setKParam("testOutputKParam");
        arcOuts.add(arcOut);

        return new PetriNet("TestNet", places, transitions, arcIns, arcOuts);
    }

    private PetriNet createComplexTestNet() throws Exception {
        ArrayList<PetriP> places = new ArrayList<>();
        ArrayList<PetriT> transitions = new ArrayList<>();
        ArrayList<ArcIn> arcIns = new ArrayList<>();
        ArrayList<ArcOut> arcOuts = new ArrayList<>();

        // Create places
        PetriP p1 = new PetriP("p1", "Place1", 1);
        PetriP p2 = new PetriP("p2", "Place2", 0);
        PetriP p3 = new PetriP("p3", "Place3", 2);
        places.add(p1);
        places.add(p2);
        places.add(p3);

        // Create transitions
        PetriT t1 = new PetriT("t1", "Transition1", 1.0);
        PetriT t2 = new PetriT("t2", "Transition2", 0.5);
        transitions.add(t1);
        transitions.add(t2);

        // Arc with boolean informational flag
        ArcIn arcIn1 = new ArcIn(p1.getNumber(), t1.getNumber(), 1);
        arcIn1.setNameP(p1.getId());
        arcIn1.setNameT(t1.getId());
        arcIn1.setInf(true);
        arcIns.add(arcIn1);

        // Arc with information parameter name
        ArcIn arcIn2 = new ArcIn(p2.getNumber(), t1.getNumber(), 1);
        arcIn2.setNameP(p2.getId());
        arcIn2.setNameT(t1.getId());
        arcIn2.setInfParam("infoParam1");
        arcIns.add(arcIn2);

        // Arc with multiplicity parameter
        ArcIn arcIn3 = new ArcIn(p3.getNumber(), t2.getNumber(), 1);
        arcIn3.setNameP(p3.getId());
        arcIn3.setNameT(t2.getId());
        arcIn3.setKParam("kParam1");
        arcIns.add(arcIn3);

        // Arc with both parameters
        ArcIn arcIn4 = new ArcIn(p1.getNumber(), t2.getNumber(), 1);
        arcIn4.setNameP(p1.getId());
        arcIn4.setNameT(t2.getId());
        arcIn4.setInfParam("infoParam2");
        arcIn4.setKParam("kParam2");
        arcIns.add(arcIn4);

        // Normal output arc
        ArcOut arcOut1 = new ArcOut(t1.getNumber(), p2.getNumber(), 2);
        arcOut1.setNameT(t1.getId());
        arcOut1.setNameP(p2.getId());
        arcOuts.add(arcOut1);

        // Output arc with multiplicity parameter
        ArcOut arcOut2 = new ArcOut(t2.getNumber(), p3.getNumber(), 1);
        arcOut2.setNameT(t2.getId());
        arcOut2.setNameP(p3.getId());
        arcOut2.setKParam("kOutParam1");
        arcOuts.add(arcOut2);

        return new PetriNet("ComplexTestNet", places, transitions, arcIns, arcOuts);
    }

    private void verifyCompleteParameterPreservation(PetriNet original, PetriNet imported) {
        // Verify input arcs
        ArcIn[] originalArcIns = original.getArcIn();
        ArcIn[] importedArcIns = imported.getArcIn();

        assertEquals("Number of input arcs should match", originalArcIns.length, importedArcIns.length);

        for (ArcIn orig : originalArcIns) {
            ArcIn imp = findMatchingArcIn(orig, importedArcIns);
            assertNotNull("Should find matching imported arc for P" + orig.getNumP() + " -> T" + orig.getNumT(), imp);

            assertEquals("Informational flag should match", orig.getIsInf(), imp.getIsInf());
            assertEquals("Informational parameter flag should match", orig.infIsParam(), imp.infIsParam());
            assertEquals("Multiplicity parameter flag should match", orig.kIsParam(), imp.kIsParam());

            if (orig.infIsParam()) {
                assertEquals("Informational parameter name should match", orig.getInfParamName(), imp.getInfParamName());
            }

            if (orig.kIsParam()) {
                assertEquals("Multiplicity parameter name should match", orig.getKParamName(), imp.getKParamName());
            }
        }

        // Verify output arcs
        ArcOut[] originalArcOuts = original.getArcOut();
        ArcOut[] importedArcOuts = imported.getArcOut();

        assertEquals("Number of output arcs should match", originalArcOuts.length, importedArcOuts.length);

        for (ArcOut orig : originalArcOuts) {
            ArcOut imp = findMatchingArcOut(orig, importedArcOuts);
            assertNotNull("Should find matching imported arc for T" + orig.getNumT() + " -> P" + orig.getNumP(), imp);

            assertEquals("Multiplicity parameter flag should match", orig.kIsParam(), imp.kIsParam());

            if (orig.kIsParam()) {
                assertEquals("Multiplicity parameter name should match", orig.getKParamName(), imp.getKParamName());
            }
        }
    }

    private ArcIn findMatchingArcIn(ArcIn target, ArcIn[] arcs) {
        // Try to match by names first (more reliable for imported arcs)
        for (ArcIn arc : arcs) {
            if (equalStrings(arc.getNameP(), target.getNameP()) &&
                equalStrings(arc.getNameT(), target.getNameT())) {
                return arc;
            }
        }
        // Fallback to number matching
        for (ArcIn arc : arcs) {
            if (arc.getNumP() == target.getNumP() && arc.getNumT() == target.getNumT()) {
                return arc;
            }
        }
        return null;
    }

    private ArcOut findMatchingArcOut(ArcOut target, ArcOut[] arcs) {
        // Try to match by names first (more reliable for imported arcs)
        for (ArcOut arc : arcs) {
            if (equalStrings(arc.getNameT(), target.getNameT()) &&
                equalStrings(arc.getNameP(), target.getNameP())) {
                return arc;
            }
        }
        // Fallback to number matching
        for (ArcOut arc : arcs) {
            if (arc.getNumT() == target.getNumT() && arc.getNumP() == target.getNumP()) {
                return arc;
            }
        }
        return null;
    }

    private static boolean equalStrings(String s1, String s2) {
        if (s1 == null && s2 == null) return true;
        if (s1 == null || s2 == null) return false;
        return s1.equals(s2);
    }
}
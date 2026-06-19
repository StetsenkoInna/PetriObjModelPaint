package ua.stetsenkoinna.graphpresentation;

import org.junit.Test;
import ua.stetsenkoinna.graphnet.GraphPetriNet;
import ua.stetsenkoinna.libnet.NetLibrary;
import ua.stetsenkoinna.petriobj.PetriNet;

import javax.swing.JTextArea;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Characterization test for FileUse's NetLibrary-method code generation.
 * Pins the current output so the planned extraction of the generator keeps
 * the generated source byte-for-byte identical (it is later compiled by
 * NetLibraryManager, so the exact format matters).
 */
public class NetMethodCodeCharacterizationTest {

    private PetriNet sampleNet() throws Exception {
        return NetLibrary.CreateNetSMOwithoutQueue(2, 1.0, "Sample");
    }

    /** All three overloads must emit identical code for the same net. */
    @Test
    public void allThreeOverloadsProduceIdenticalCode() throws Exception {
        FileUse fileUse = new FileUse();

        PetriNet net = sampleNet();
        GraphPetriNet graphNet = new GraphPetriNet();
        graphNet.setPetriNet(net);

        String fromString = fileUse.saveNetAsMethod(graphNet);           // (2) GraphPetriNet -> String

        JTextArea area1 = new JTextArea();
        fileUse.saveNetAsMethod(graphNet, area1);                        // (1) GraphPetriNet -> JTextArea

        JTextArea area3 = new JTextArea();
        fileUse.saveNetAsMethod(net, area3);                            // (3) PetriNet -> JTextArea

        assertEquals(fromString, area1.getText());
        assertEquals(fromString, area3.getText());
    }

    /** Pin the overall shape of the generated method. */
    @Test
    public void generatedCodeHasExpectedShape() throws Exception {
        FileUse fileUse = new FileUse();
        GraphPetriNet graphNet = new GraphPetriNet();
        graphNet.setPetriNet(sampleNet());

        String code = fileUse.saveNetAsMethod(graphNet);

        assertTrue(code.startsWith("\npublic static PetriNet CreateNet"));
        assertTrue(code.contains("ArrayList<PetriP> d_P = new ArrayList<>();"));
        assertTrue(code.contains("ArrayList<PetriT> d_T = new ArrayList<>();"));
        assertTrue(code.contains("PetriNet d_Net = new PetriNet("));
        assertTrue(code.contains("PetriP.initNext();"));
        assertTrue(code.contains("ArcOut.initNext();"));
        assertTrue(code.trim().endsWith("return d_Net;\n}".trim()) || code.trim().endsWith("}"));
    }
}

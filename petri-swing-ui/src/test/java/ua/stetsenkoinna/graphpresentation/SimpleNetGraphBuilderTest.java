package ua.stetsenkoinna.graphpresentation;

import org.junit.Test;
import ua.stetsenkoinna.graphnet.GraphPetriNet;
import ua.stetsenkoinna.libnet.NetLibrary;
import ua.stetsenkoinna.petriobj.PetriNet;

import java.awt.Point;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

/**
 * Characterization test for SimpleNetGraphBuilder (extracted verbatim from
 * FileUse.generateGraphNetBySimpleNet). Confirms the built graph mirrors the
 * structure of the source net.
 */
public class SimpleNetGraphBuilderTest {

    @Test
    public void buildsGraphMirroringTheNetStructure() throws Exception {
        PetriNet net = NetLibrary.CreateNetSMOwithoutQueue(2, 1.0, "Sample");

        GraphPetriNet graph = SimpleNetGraphBuilder.build(net, new Point(100, 100));

        assertSame(net, graph.getPetriNet());
        assertEquals(net.getListP().length, graph.getGraphPetriPlaceList().size());
        assertEquals(net.getListT().length, graph.getGraphPetriTransitionList().size());
        assertEquals(net.getArcIn().length, graph.getGraphArcInList().size());
        assertEquals(net.getArcOut().length, graph.getGraphArcOutList().size());
    }
}

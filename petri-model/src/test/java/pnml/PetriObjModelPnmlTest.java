package pnml;

import org.junit.Test;
import ua.stetsenkoinna.graphnet.GraphNetBuilder;
import ua.stetsenkoinna.graphnet.GraphPetriNet;
import ua.stetsenkoinna.graphnet.GraphPetriObjModel;
import ua.stetsenkoinna.graphnet.GraphPetriObject;
import ua.stetsenkoinna.graphnet.NetTemplateRef;
import ua.stetsenkoinna.petriobj.ArcIn;
import ua.stetsenkoinna.petriobj.ArcOut;
import ua.stetsenkoinna.petriobj.PetriNet;
import ua.stetsenkoinna.petriobj.PetriObjLink;
import ua.stetsenkoinna.petriobj.PetriObjLinkType;
import ua.stetsenkoinna.petriobj.PetriObjModel;
import ua.stetsenkoinna.petriobj.PetriP;
import ua.stetsenkoinna.petriobj.PetriT;
import ua.stetsenkoinna.pnml.PnmlModelGenerator;
import ua.stetsenkoinna.pnml.PnmlModelParser;
import ua.stetsenkoinna.pnml.PnmlParser;

import java.awt.Point;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Round-trip of a composed Petri-object model through PNML, and the behaviour of the plain
 * net reader when it meets such a document.
 */
public class PetriObjModelPnmlTest {

    /** Builds {@code P0 -> T0 -> P1} with the given initial marking of {@code P0}. */
    private static GraphPetriNet chainNet(String name, int startTokens) throws Exception {
        PetriP.initNext();
        PetriT.initNext();
        ArcIn.initNext();
        ArcOut.initNext();
        ArrayList<PetriP> places = new ArrayList<>();
        places.add(new PetriP("P0", startTokens));
        places.add(new PetriP("P1", 0));
        ArrayList<PetriT> transitions = new ArrayList<>();
        transitions.add(new PetriT("T0", 1.0));
        ArrayList<ArcIn> arcsIn = new ArrayList<>();
        arcsIn.add(new ArcIn(places.get(0), transitions.get(0), 1));
        ArrayList<ArcOut> arcsOut = new ArrayList<>();
        arcsOut.add(new ArcOut(transitions.get(0), places.get(1), 1));
        PetriNet net = new PetriNet(name, places, transitions, arcsIn, arcsOut);
        return GraphNetBuilder.build(net, Collections.emptyMap(), Collections.emptyMap(), null);
    }

    private static GraphPetriObjModel twoObjectModel() throws Exception {
        GraphPetriObjModel model = new GraphPetriObjModel("QueueingSystem");
        GraphPetriObject producer = new GraphPetriObject("Generator", chainNet("Generator", 1));
        producer.setPosition(new Point(40, 60));
        producer.setTemplate(new NetTemplateRef("CreateNetGenerator", List.of("2.0")));
        model.addObject(producer);

        GraphPetriObject consumer = new GraphPetriObject("Server", chainNet("Server", 0));
        consumer.setPriority(3);
        consumer.setPosition(new Point(260, 60));
        model.addObject(consumer);

        model.addLink(PetriObjLink.placeFusion(0, 1, 1, 0));
        model.addLink(PetriObjLink.transitionToPlace(1, 0, 0, 0, 2));
        model.addLink(PetriObjLink.placeToTransition(1, 1, 0, 0, 1, true));
        return model;
    }

    @Test
    public void modelSurvivesAPnmlRoundTrip() throws Exception {
        String xml = new PnmlModelGenerator().generateXml(twoObjectModel());
        GraphPetriObjModel restored = new PnmlModelParser().parseXml(xml);

        assertEquals("QueueingSystem", restored.getName());
        assertEquals(2, restored.getObjectCount());

        GraphPetriObject producer = restored.getObject(0);
        assertEquals("Generator", producer.getName());
        assertEquals(2, producer.getPlaceCount());
        assertEquals(1, producer.getTransitionCount());
        assertEquals(new Point(40, 60), producer.getPosition());
        assertNotNull(producer.getTemplate());
        assertEquals("CreateNetGenerator", producer.getTemplate().getMethodName());
        assertEquals(List.of("2.0"), producer.getTemplate().getArguments());

        GraphPetriObject consumer = restored.getObject(1);
        assertEquals("Server", consumer.getName());
        assertEquals(3, consumer.getPriority());

        assertEquals(3, restored.getLinks().size());
        assertEquals(PetriObjLinkType.PLACE_FUSION, restored.getLinks().get(0).getType());
        PetriObjLink delivery = restored.getLinks().get(1);
        assertEquals(PetriObjLinkType.TRANSITION_TO_PLACE, delivery.getType());
        assertEquals(2, delivery.getQuantity());
        PetriObjLink test = restored.getLinks().get(2);
        assertEquals(PetriObjLinkType.PLACE_TO_TRANSITION, test.getType());
        assertTrue(test.isInformational());
    }

    @Test
    public void restoredModelBuildsARunnableSimulation() throws Exception {
        String xml = new PnmlModelGenerator().generateXml(twoObjectModel());
        GraphPetriObjModel restored = new PnmlModelParser().parseXml(xml);

        PetriObjModel model = restored.createPetriObjModel("round-trip");
        assertEquals(2, model.getListObj().size());
        assertEquals("Server", model.getListObj().get(1).getName());
        assertEquals(3, model.getListObj().get(1).getPriority());
        assertSame("the fused place must be one instance in both objects",
                model.getListObj().get(1).getNet().getListP()[0],
                model.getListObj().get(0).getNet().getListP()[1]);

        model.setIsProtokol(false);
        model.go(10.0);
    }

    @Test
    public void plainNetDocumentReadsAsAModelOfOneObject() throws Exception {
        GraphPetriObjModel single = GraphPetriObjModel.singleObject(chainNet("Simple", 1), "Simple");
        String xml = new PnmlModelGenerator().generateXml(single);

        GraphPetriObjModel restored = new PnmlModelParser().parseXml(xml);
        assertTrue(restored.isSingleObject());
        assertEquals("Simple", restored.getObject(0).getName());

        // A one-object document stays an ordinary Petri net for the plain reader.
        PetriNet net = new PnmlParser().parseXml(xml);
        assertEquals(2, net.getListP().length);
        assertEquals(1, net.getListT().length);
    }

    @Test
    public void plainNetReaderRefusesAComposedModel() throws Exception {
        String xml = new PnmlModelGenerator().generateXml(twoObjectModel());
        try {
            new PnmlParser().parseXml(xml);
            fail("a document of several Petri-objects must not be read as one net");
        } catch (Exception expected) {
            assertTrue("the message should point at the Petri-object model, was: " + expected.getMessage(),
                    expected.getMessage().contains("Petri-object model"));
        }
    }

    @Test
    public void removingAnObjectDropsItsLinksAndRenumbersTheRest() throws Exception {
        GraphPetriObjModel model = twoObjectModel();
        model.addObject(new GraphPetriObject("Third", chainNet("Third", 0)));
        model.addLink(PetriObjLink.placeFusion(2, 1, 1, 0));

        model.removeObject(0);

        assertEquals(2, model.getObjectCount());
        assertEquals("only the link between the surviving objects is left", 1, model.getLinks().size());
        PetriObjLink survivor = model.getLinks().getFirst();
        assertEquals(1, survivor.getSourceObject());
        assertEquals(0, survivor.getTargetObject());
    }
}

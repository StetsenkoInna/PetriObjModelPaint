package ua.stetsenkoinna.graphpresentation.objmodel;

import org.junit.Assume;
import org.junit.Test;
import ua.stetsenkoinna.graphnet.GraphPetriNet;
import ua.stetsenkoinna.graphnet.GraphPetriObjModel;
import ua.stetsenkoinna.graphnet.GraphPetriObject;
import ua.stetsenkoinna.graphpresentation.SimpleNetGraphBuilder;
import ua.stetsenkoinna.graphpresentation.statistic.dto.data.StatisticGraphMonitor;
import ua.stetsenkoinna.libnet.NetLibrary;
import ua.stetsenkoinna.petriobj.PetriObjLink;

import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.Point;
import java.awt.image.BufferedImage;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * The structure layer: that a composed model draws, and that the window built on top of it
 * assembles.
 */
public class ObjectStructurePanelTest {

    private static GraphPetriObjModel queueingModel() throws Exception {
        GraphPetriObjModel model = new GraphPetriObjModel("Queueing system");
        GraphPetriNet generator = SimpleNetGraphBuilder.build(
                NetLibrary.CreateNetGenerator(2.0), new Point(200, 150));
        GraphPetriNet server = SimpleNetGraphBuilder.build(
                NetLibrary.CreateNetSMOwithoutQueue(1, 0.6, "First"), new Point(200, 150));

        GraphPetriObject source = new GraphPetriObject("Generator", generator);
        source.setPosition(new Point(40, 40));
        model.addObject(source);

        GraphPetriObject sink = new GraphPetriObject("Server", server);
        sink.setPosition(new Point(320, 40));
        sink.setPriority(2);
        model.addObject(sink);

        model.addLink(PetriObjLink.placeFusion(0, 1, 1, 0));
        model.addLink(PetriObjLink.transitionToPlace(1, 0, 0, 0, 1));
        model.addLink(PetriObjLink.placeToTransition(1, 2, 0, 0, 1, true));
        return model;
    }

    @Test
    public void aComposedModelDrawsItsObjectsAndLinks() throws Exception {
        ObjectStructurePanel panel = new ObjectStructurePanel();
        panel.setModel(queueingModel());
        panel.setSize(900, 600);

        BufferedImage image = new BufferedImage(900, 600, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        panel.paint(graphics);
        graphics.dispose();

        assertTrue("the canvas must grow to hold the nodes", panel.getPreferredSize().width >= 490);
    }

    @Test
    public void newNodesAreLaidOutInRows() throws Exception {
        ObjectStructurePanel panel = new ObjectStructurePanel();
        panel.setModel(queueingModel());

        Point next = panel.nextFreePosition();

        assertTrue("a third node goes to the right of the first two", next.x > 400);
        assertEquals(40, next.y);
    }

    @Test
    public void aNodeKnowsTheLinksAttachedToIt() throws Exception {
        ObjectStructurePanel panel = new ObjectStructurePanel();
        panel.setModel(queueingModel());

        assertEquals(3, panel.linksOf(0).size());
        assertEquals(3, panel.linksOf(1).size());
    }

    @Test
    public void theStructureWindowAssemblesAroundAModel() throws Exception {
        Assume.assumeFalse("needs a display", GraphicsEnvironment.isHeadless());

        GraphPetriObjModel model = queueingModel();
        ModelStructureFrame frame = new ModelStructureFrame(null, new StubEditor(), model);
        try {
            frame.pack();
            assertEquals(model, frame.getModel());
            assertNotNull(frame.getJMenuBar());
            assertEquals(4, frame.getJMenuBar().getMenuCount());
        } finally {
            frame.dispose();
        }
    }

    /** A net editor that records nothing — the window under test only queries it. */
    private static final class StubEditor implements NetEditorBridge {

        @Override
        public void openNet(GraphPetriNet net, String name) {
        }

        @Override
        public GraphPetriNet getCanvasNet() {
            return null;
        }

        @Override
        public Point getCanvasCentre() {
            return new Point(200, 150);
        }

        @Override
        public double getSimulationTime() {
            return 100.0;
        }

        @Override
        public StatisticGraphMonitor createStatisticMonitor(boolean blocking) {
            return null;
        }
    }
}

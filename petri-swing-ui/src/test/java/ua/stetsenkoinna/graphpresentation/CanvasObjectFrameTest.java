package ua.stetsenkoinna.graphpresentation;

import org.junit.Test;
import ua.stetsenkoinna.graphnet.GraphObjectFrame;
import ua.stetsenkoinna.graphnet.GraphPetriNet;
import ua.stetsenkoinna.graphnet.GraphPetriObjModel;
import ua.stetsenkoinna.graphnet.GraphPetriPlace;
import ua.stetsenkoinna.libnet.NetLibrary;

import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * The canvas holding Petri-object frames: what a frame owns, what moving one does, and that
 * a drawing with frames still paints.
 */
public class CanvasObjectFrameTest {

    /**
     * Builds a panel showing a generator net, with a frame drawn around all of it.
     */
    private static PetriNetsPanel panelWithFramedNet() throws Exception {
        PetriNetsPanel panel = new PetriNetsPanel(null, false);
        GraphPetriNet net = SimpleNetGraphBuilder.build(NetLibrary.CreateNetGenerator(2.0), new Point(300, 200));
        panel.setGraphNet(net);
        panel.addObjectFrame(new GraphObjectFrame("Generator", new Rectangle(0, 0, 900, 600)));
        return panel;
    }

    @Test
    public void aFrameOwnsWhatIsDrawnInsideIt() throws Exception {
        PetriNetsPanel panel = panelWithFramedNet();
        GraphObjectFrame frame = panel.getCanvasModel().getFrames().getFirst();

        int elements = panel.getGraphNet().getGraphPetriPlaceList().size()
                + panel.getGraphNet().getGraphPetriTransitionList().size();
        assertEquals(elements, panel.countElementsIn(frame));
    }

    @Test
    public void theCanvasReadsItselfAsAModelOfOneObject() throws Exception {
        PetriNetsPanel panel = panelWithFramedNet();

        GraphPetriObjModel model = panel.getCanvasModel().toObjModel();

        assertEquals(1, model.getObjectCount());
        assertEquals("Generator", model.getObject(0).getName());
        assertTrue(model.getLinks().isEmpty());
    }

    @Test
    public void aFrameWithoutAnythingInsideCollapsesAndExpandsBack() throws Exception {
        PetriNetsPanel panel = panelWithFramedNet();
        GraphObjectFrame frame = panel.getCanvasModel().getFrames().getFirst();
        Rectangle expanded = new Rectangle(frame.getBounds());

        frame.setCollapsed(true);
        assertEquals(GraphObjectFrame.COLLAPSED_HEIGHT, frame.getBounds().height);

        frame.setCollapsed(false);
        assertEquals(expanded, frame.getBounds());
        assertFalse(frame.isCollapsed());
    }

    @Test
    public void draggingAFrameCarriesItsNetAlong() throws Exception {
        PetriNetsPanel panel = panelWithFramedNet();
        GraphPetriPlace place = panel.getGraphNet().getGraphPetriPlaceList().getFirst();
        double before = place.getGraphElementCenter().getX();

        // Same path the mouse takes, through the public canvas API.
        GraphObjectFrame frame = panel.getCanvasModel().getFrames().getFirst();
        int dx = 120;
        java.lang.reflect.Method move = PetriNetsPanel.class
                .getDeclaredMethod("moveFrameWithContents", GraphObjectFrame.class, int.class, int.class);
        move.setAccessible(true);
        move.invoke(panel, frame, frame.getBounds().x + dx, frame.getBounds().y);

        assertEquals(before + dx, place.getGraphElementCenter().getX(), 0.001);
        assertEquals(dx, frame.getBounds().x);
    }

    @Test
    public void aCanvasWithFramesPaints() throws Exception {
        PetriNetsPanel panel = panelWithFramedNet();
        panel.getCanvasModel().getFrames().getFirst().setCollapsed(true);
        panel.setSize(900, 600);

        BufferedImage image = new BufferedImage(900, 600, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        panel.paintComponent(graphics);
        graphics.dispose();

        assertNotNull(panel.getCanvasModel());
    }
}

package ua.stetsenkoinna.graphpresentation;

import org.junit.Test;
import ua.stetsenkoinna.graphnet.FramePort;
import ua.stetsenkoinna.graphnet.GraphObjectFrame;
import ua.stetsenkoinna.graphnet.GraphPetriNet;
import ua.stetsenkoinna.graphnet.GraphPetriObjModel;
import ua.stetsenkoinna.graphnet.GraphPetriPlace;
import ua.stetsenkoinna.graphnet.GraphPetriTransition;
import ua.stetsenkoinna.libnet.NetLibrary;
import ua.stetsenkoinna.petriobj.ArcIn;
import ua.stetsenkoinna.petriobj.ArcOut;
import ua.stetsenkoinna.petriobj.PetriP;
import ua.stetsenkoinna.petriobj.PetriT;

import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.lang.reflect.Method;
import java.util.List;

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

    /**
     * Two frames, each holding one place and one transition of their own, with no links
     * between them yet.
     */
    private static PetriNetsPanel twoFramedObjectsPanel() throws Exception {
        PetriP.initNext();
        PetriT.initNext();
        ArcIn.initNext();
        ArcOut.initNext();

        PetriNetsPanel panel = new PetriNetsPanel(null, false);
        GraphPetriPlace placeA = new GraphPetriPlace(new PetriP("PA", 1), 0);
        placeA.setNewCoordinates(new Point2D.Double(80, 80));
        GraphPetriTransition transitionA = new GraphPetriTransition(new PetriT("TA", 1.0), 1);
        transitionA.setNewCoordinates(new Point2D.Double(180, 80));
        panel.getGraphNet().getGraphPetriPlaceList().add(placeA);
        panel.getGraphNet().getGraphPetriTransitionList().add(transitionA);
        panel.addObjectFrame(new GraphObjectFrame("A", new Rectangle(0, 0, 300, 300)));

        GraphPetriPlace placeB = new GraphPetriPlace(new PetriP("PB", 0), 2);
        placeB.setNewCoordinates(new Point2D.Double(480, 80));
        GraphPetriTransition transitionB = new GraphPetriTransition(new PetriT("TB", 1.0), 3);
        transitionB.setNewCoordinates(new Point2D.Double(580, 80));
        panel.getGraphNet().getGraphPetriPlaceList().add(placeB);
        panel.getGraphNet().getGraphPetriTransitionList().add(transitionB);
        panel.addObjectFrame(new GraphObjectFrame("B", new Rectangle(400, 0, 300, 300)));

        return panel;
    }

    private static FramePort portOf(PetriNetsPanel panel, GraphObjectFrame frame, String elementName) {
        for (FramePort port : panel.getCanvasModel().portsOf(frame)) {
            if (port.getLabel().equals(elementName)) {
                return port;
            }
        }
        throw new AssertionError("no port for " + elementName);
    }

    /** Drives {@code finishPortDrag} the way a completed drag from one port to another would. */
    private static void dragPort(PetriNetsPanel panel, FramePort from, FramePort to) throws Exception {
        java.lang.reflect.Field fromField = PetriNetsPanel.class.getDeclaredField("draggedFromPort");
        fromField.setAccessible(true);
        fromField.set(panel, from);

        Method finish = PetriNetsPanel.class.getDeclaredMethod("finishPortDrag", FramePort.class);
        finish.setAccessible(true);
        finish.invoke(panel, to);
    }

    @Test
    public void draggingBetweenTwoPlacePortsSharesThePlace() throws Exception {
        PetriNetsPanel panel = twoFramedObjectsPanel();
        List<GraphObjectFrame> frames = panel.getCanvasModel().getFrames();
        FramePort pa = portOf(panel, frames.get(0), "PA");
        FramePort pb = portOf(panel, frames.get(1), "PB");

        dragPort(panel, pa, pb);

        assertEquals(1, panel.getCanvasModel().getFusions().size());
        assertTrue(panel.getCanvasModel().getFusions().getFirst().involves((GraphPetriPlace) pa.getElement()));
    }

    @Test
    public void draggingFromAPlacePortToATransitionPortAddsAnInputArc() throws Exception {
        PetriNetsPanel panel = twoFramedObjectsPanel();
        List<GraphObjectFrame> frames = panel.getCanvasModel().getFrames();
        FramePort pa = portOf(panel, frames.get(0), "PA");
        FramePort tb = portOf(panel, frames.get(1), "TB");

        dragPort(panel, pa, tb);

        assertEquals(1, panel.getGraphNet().getGraphArcInList().size());
        assertEquals(pa.getElement(), panel.getGraphNet().getGraphArcInList().getFirst().getBeginElement());
        assertEquals(tb.getElement(), panel.getGraphNet().getGraphArcInList().getFirst().getEndElement());
    }

    @Test
    public void draggingFromATransitionPortToAPlacePortAddsAnOutputArc() throws Exception {
        PetriNetsPanel panel = twoFramedObjectsPanel();
        List<GraphObjectFrame> frames = panel.getCanvasModel().getFrames();
        FramePort ta = portOf(panel, frames.get(0), "TA");
        FramePort pb = portOf(panel, frames.get(1), "PB");

        dragPort(panel, ta, pb);

        assertEquals(1, panel.getGraphNet().getGraphArcOutList().size());
        assertEquals(ta.getElement(), panel.getGraphNet().getGraphArcOutList().getFirst().getBeginElement());
        assertEquals(pb.getElement(), panel.getGraphNet().getGraphArcOutList().getFirst().getEndElement());
    }

    @Test
    public void droppingAPortDragOnEmptySpaceCreatesNothing() throws Exception {
        PetriNetsPanel panel = twoFramedObjectsPanel();
        FramePort pa = portOf(panel, panel.getCanvasModel().getFrames().getFirst(), "PA");

        dragPort(panel, pa, null);

        assertTrue(panel.getCanvasModel().getFusions().isEmpty());
        assertTrue(panel.getGraphNet().getGraphArcInList().isEmpty());
        assertTrue(panel.getGraphNet().getGraphArcOutList().isEmpty());
    }

    @Test
    public void objectNetFiltersOutJustThatFramesOwnElements() throws Exception {
        PetriNetsPanel panel = twoFramedObjectsPanel();
        GraphObjectFrame frameA = panel.getCanvasModel().getFrames().getFirst();

        Method build = PetriNetsPanel.class.getDeclaredMethod("buildObjectNet", GraphObjectFrame.class);
        build.setAccessible(true);
        GraphPetriNet objectNet = (GraphPetriNet) build.invoke(panel, frameA);

        assertEquals(1, objectNet.getGraphPetriPlaceList().size());
        assertEquals("PA", objectNet.getGraphPetriPlaceList().getFirst().getName());
        assertEquals(1, objectNet.getGraphPetriTransitionList().size());
        assertEquals("TA", objectNet.getGraphPetriTransitionList().getFirst().getName());
    }

    @Test
    public void objectNetExcludesArcsCrossingToAnotherObject() throws Exception {
        PetriNetsPanel panel = twoFramedObjectsPanel();
        List<GraphObjectFrame> frames = panel.getCanvasModel().getFrames();
        FramePort ta = portOf(panel, frames.get(0), "TA");
        FramePort pb = portOf(panel, frames.get(1), "PB");
        dragPort(panel, ta, pb); // TA -> PB, a crossing arc that belongs to neither object's own net

        Method build = PetriNetsPanel.class.getDeclaredMethod("buildObjectNet", GraphObjectFrame.class);
        build.setAccessible(true);
        GraphPetriNet netA = (GraphPetriNet) build.invoke(panel, frames.get(0));
        GraphPetriNet netB = (GraphPetriNet) build.invoke(panel, frames.get(1));

        assertTrue("the crossing arc is a link, not part of A's own net", netA.getGraphArcOutList().isEmpty());
        assertTrue("nor of B's", netB.getGraphArcInList().isEmpty());
    }

    @Test
    public void reconcileAddsWhatIsNewAndDropsWhatWasRemoved() throws Exception {
        Method reconcile = PetriNetsPanel.class.getDeclaredMethod(
                "reconcile", List.class, List.class, List.class);
        reconcile.setAccessible(true);

        String survivor = "kept";
        String removed = "gone";
        String added = "new";
        java.util.List<String> main = new java.util.ArrayList<>(List.of(survivor, removed));
        List<String> before = List.of(survivor, removed);
        List<String> after = List.of(survivor, added);

        reconcile.invoke(null, main, before, after);

        assertEquals(List.of(survivor, added), main);
    }

    @Test
    public void theEditorToolbarAddsAPlaceAndATransition() throws Exception {
        PetriNetsPanel editorPanel = new PetriNetsPanel(null, true);
        editorPanel.setGraphNet(new GraphPetriNet());
        ua.stetsenkoinna.graphpresentation.objmodel.ObjectEditorFrame editor =
                new ua.stetsenkoinna.graphpresentation.objmodel.ObjectEditorFrame(null, "Test", editorPanel);

        Method addPlace = editor.getClass().getDeclaredMethod("addPlace");
        addPlace.setAccessible(true);
        addPlace.invoke(editor);
        Method addTransition = editor.getClass().getDeclaredMethod("addTransition");
        addTransition.setAccessible(true);
        addTransition.invoke(editor);

        assertEquals(1, editorPanel.getGraphNet().getGraphPetriPlaceList().size());
        assertEquals(1, editorPanel.getGraphNet().getGraphPetriTransitionList().size());
        editor.dispose();
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

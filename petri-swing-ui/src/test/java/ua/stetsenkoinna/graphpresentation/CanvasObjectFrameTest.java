package ua.stetsenkoinna.graphpresentation;

import org.junit.Test;
import ua.stetsenkoinna.graphnet.FramePort;
import ua.stetsenkoinna.graphnet.GraphElement;
import ua.stetsenkoinna.graphnet.GraphObjectFrame;
import ua.stetsenkoinna.graphnet.GraphPetriNet;
import ua.stetsenkoinna.graphnet.GraphPetriObjModel;
import ua.stetsenkoinna.graphnet.GraphPetriPlace;
import ua.stetsenkoinna.graphnet.GraphPetriTransition;
import ua.stetsenkoinna.graphpresentation.undoable_edits.AddGraphElementEdit;
import ua.stetsenkoinna.libnet.NetLibrary;
import ua.stetsenkoinna.petriobj.ArcIn;
import ua.stetsenkoinna.petriobj.ArcOut;
import ua.stetsenkoinna.petriobj.PetriP;
import ua.stetsenkoinna.petriobj.PetriT;

import javax.swing.JButton;
import javax.swing.JLabel;
import java.awt.Component;
import java.awt.Container;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.MouseEvent;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
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
        GraphObjectFrame frame = new GraphObjectFrame("Generator", new Rectangle(0, 0, 900, 600));
        for (GraphPetriPlace place : net.getGraphPetriPlaceList()) {
            panel.getCanvasModel().claim(frame, place);
        }
        for (GraphPetriTransition transition : net.getGraphPetriTransitionList()) {
            panel.getCanvasModel().claim(frame, transition);
        }
        panel.addObjectFrame(frame);
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

    private static java.lang.reflect.Method moveFrameMethod() throws NoSuchMethodException {
        java.lang.reflect.Method move = PetriNetsPanel.class
                .getDeclaredMethod("moveFrame", GraphObjectFrame.class, int.class, int.class);
        move.setAccessible(true);
        return move;
    }

    @Test
    public void draggingAFrameCarriesItsElementsAlong() throws Exception {
        // Elements always stay inside their frame as it moves — they are fixed relative to the
        // frame, only repositioned individually through the object's own editor, never left
        // behind on the canvas by a drag of the frame itself.
        PetriNetsPanel panel = panelWithFramedNet();
        GraphPetriPlace place = panel.getGraphNet().getGraphPetriPlaceList().getFirst();
        double beforeX = place.getGraphElementCenter().getX();
        double beforeY = place.getGraphElementCenter().getY();
        GraphObjectFrame frame = panel.getCanvasModel().getFrames().getFirst();
        int dx = 120;
        int dy = 40;

        // Same path the mouse takes, through the public canvas API.
        moveFrameMethod().invoke(panel, frame, frame.getBounds().x + dx, frame.getBounds().y + dy);

        assertEquals(dx, frame.getBounds().x);
        assertEquals(dy, frame.getBounds().y);
        assertEquals(beforeX + dx, place.getGraphElementCenter().getX(), 0.001);
        assertEquals(beforeY + dy, place.getGraphElementCenter().getY(), 0.001);
    }

    @Test
    public void draggingAFrameIntoTheCanvasEdgeMovesElementsByTheClampedDeltaNotTheRawOne() throws Exception {
        // The reported bug: moveTo() clamps a negative target to 0, but the delta applied to
        // the elements used to be measured from the raw, unclamped target — so dragging a
        // frame into the top or left edge kept moving its elements by the full amount while the
        // frame itself stopped dead at the edge, drifting them away from it a little further
        // with every such drag. The delta must come from where the frame actually ended up.
        PetriNetsPanel panel = panelWithFramedNet();
        GraphObjectFrame frame = panel.getCanvasModel().getFrames().getFirst();
        // Start away from the edge, so the clamp about to happen is a real one, not a no-op.
        moveFrameMethod().invoke(panel, frame, 100, 100);
        GraphPetriPlace place = panel.getGraphNet().getGraphPetriPlaceList().getFirst();
        double atOneHundredX = place.getGraphElementCenter().getX();
        double atOneHundredY = place.getGraphElementCenter().getY();

        // Requests (-50,-50); moveTo clamps the frame to (0,0) — an actual delta of -100, not
        // the -150 the raw, unclamped target would suggest.
        moveFrameMethod().invoke(panel, frame, -50, -50);

        assertEquals("moveTo clamps the frame itself to the canvas", 0, frame.getBounds().x);
        assertEquals(0, frame.getBounds().y);
        assertEquals("moved by the frame's actual, clamped delta (-100), not the raw one (-150)",
                atOneHundredX - 100, place.getGraphElementCenter().getX(), 0.001);
        assertEquals(atOneHundredY - 100, place.getGraphElementCenter().getY(), 0.001);
    }

    @Test
    public void movingAFrameDoesNotCaptureElementsItPassesOver() throws Exception {
        PetriNetsPanel panel = panelWithFramedNet();
        GraphPetriPlace stray = new GraphPetriPlace(new PetriP("Stray", 0), 999);
        stray.setNewCoordinates(new Point2D.Double(2000, 2000));
        panel.getGraphNet().getGraphPetriPlaceList().add(stray);
        GraphObjectFrame frame = panel.getCanvasModel().getFrames().getFirst();

        // Drag the frame so its new rectangle lands right on top of the stray element.
        moveFrameMethod().invoke(panel, frame, 1700, 1700);

        assertNull("a free element the frame merely passed over must not become a member",
                panel.getCanvasModel().ownerOf(stray));
    }

    @Test
    public void removingAFrameReleasesItsMembers() throws Exception {
        // What a removed object held moves one level out: to the object that enclosed it, or to
        // the free elements when nothing did. Never to whatever frame happens to be drawn over it,
        // which is what used to hand an outer object's whole net to the object nested inside it.
        PetriNetsPanel panel = panelWithFramedNet();
        GraphObjectFrame frame = panel.getCanvasModel().getFrames().getFirst();
        GraphPetriPlace place = panel.getGraphNet().getGraphPetriPlaceList().getFirst();

        panel.removeObjectFrame(frame);

        assertNull(panel.getCanvasModel().ownerOf(place));
    }

    @Test
    public void removingANestedFrameHandsItsMembersToTheObjectThatEnclosedIt() throws Exception {
        PetriNetsPanel panel = panelWithFramedNet();
        GraphObjectFrame parent = panel.getCanvasModel().getFrames().getFirst();
        GraphPetriPlace place = panel.getGraphNet().getGraphPetriPlaceList().getFirst();
        GraphObjectFrame child = new GraphObjectFrame("Inner", new Rectangle(100, 100, 200, 160));
        panel.getCanvasModel().nest(child, parent);
        panel.addObjectFrame(child);
        panel.getCanvasModel().claim(child, place);

        panel.removeObjectFrame(child);

        assertSame(parent, panel.getCanvasModel().ownerOf(place));
    }

    /**
     * Two frames, each holding one place and one transition of their own, with no links
     * between them yet.
     */
    private static RefusalCapturingPanel twoFramedObjectsPanel() throws Exception {
        PetriP.initNext();
        PetriT.initNext();
        ArcIn.initNext();
        ArcOut.initNext();

        RefusalCapturingPanel panel = new RefusalCapturingPanel();
        GraphPetriPlace placeA = new GraphPetriPlace(new PetriP("PA", 1), 0);
        placeA.setNewCoordinates(new Point2D.Double(80, 80));
        GraphPetriTransition transitionA = new GraphPetriTransition(new PetriT("TA", 1.0), 1);
        transitionA.setNewCoordinates(new Point2D.Double(180, 80));
        panel.getGraphNet().getGraphPetriPlaceList().add(placeA);
        panel.getGraphNet().getGraphPetriTransitionList().add(transitionA);
        GraphObjectFrame frameA = new GraphObjectFrame("A", new Rectangle(0, 0, 300, 300));
        panel.getCanvasModel().claim(frameA, placeA);
        panel.getCanvasModel().claim(frameA, transitionA);
        panel.addObjectFrame(frameA);

        GraphPetriPlace placeB = new GraphPetriPlace(new PetriP("PB", 0), 2);
        placeB.setNewCoordinates(new Point2D.Double(480, 80));
        GraphPetriTransition transitionB = new GraphPetriTransition(new PetriT("TB", 1.0), 3);
        transitionB.setNewCoordinates(new Point2D.Double(580, 80));
        panel.getGraphNet().getGraphPetriPlaceList().add(placeB);
        panel.getGraphNet().getGraphPetriTransitionList().add(transitionB);
        GraphObjectFrame frameB = new GraphObjectFrame("B", new Rectangle(400, 0, 300, 300));
        panel.getCanvasModel().claim(frameB, placeB);
        panel.getCanvasModel().claim(frameB, transitionB);
        panel.addObjectFrame(frameB);

        return panel;
    }

    /** Captures what the canvas refuses instead of putting a modal dialog on the screen. */
    private static class RefusalCapturingPanel extends PetriNetsPanel {

        private String refusal;

        RefusalCapturingPanel() {
            super(null, false);
        }

        @Override
        void reportRefusal(String message) {
            refusal = message;
        }
    }

    private static FramePort portOf(PetriNetsPanel panel, GraphObjectFrame frame, String elementName) {
        for (FramePort port : panel.getCanvasModel().portsOf(frame)) {
            if (port.getLabel().equals(elementName)) {
                return port;
            }
        }
        throw new AssertionError("no port for " + elementName);
    }

    private static GraphElement elementNamed(PetriNetsPanel panel, String name) {
        for (GraphPetriPlace place : panel.getGraphNet().getGraphPetriPlaceList()) {
            if (place.getName().equals(name)) {
                return place;
            }
        }
        for (GraphPetriTransition transition : panel.getGraphNet().getGraphPetriTransitionList()) {
            if (transition.getName().equals(name)) {
                return transition;
            }
        }
        throw new AssertionError("no element named " + name);
    }

    /**
     * Drops an element with one of the canvas's own placement tools, the way a click does, and
     * returns the undoable edit it posted. Goes through {@code addElementAt} rather than adding to
     * the net directly, so the claim an object's canvas makes on what is drawn there is exercised.
     */
    private static AddGraphElementEdit addWithTool(PetriNetsPanel panel, CanvasTool tool, Point at)
            throws Exception {
        panel.setTool(tool);
        Method add = PetriNetsPanel.class.getDeclaredMethod("addElementAt", CanvasTool.class, Point.class);
        add.setAccessible(true);
        java.util.List<javax.swing.undo.UndoableEdit> posted = new java.util.ArrayList<>();
        javax.swing.event.UndoableEditListener listener = event -> posted.add(event.getEdit());
        PetriNetsFrame.getUndoSupport().addUndoableEditListener(listener);
        try {
            add.invoke(panel, tool, at);
        } finally {
            PetriNetsFrame.getUndoSupport().removeUndoableEditListener(listener);
        }
        assertEquals("the placement tool posts exactly one undoable edit", 1, posted.size());
        return (AddGraphElementEdit) posted.getFirst();
    }

    private static <T extends Component> T findComponent(Container root, Class<T> type, Predicate<T> matches) {
        for (Component c : root.getComponents()) {
            if (type.isInstance(c) && matches.test(type.cast(c))) {
                return type.cast(c);
            }
            if (c instanceof Container container) {
                T found = findComponent(container, type, matches);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
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

    /**
     * Drives {@code finishPortDragToFreeElement} the way a completed drag from a port onto a
     * free (unframed) place or transition would.
     */
    private static void dragPortToFreeElement(PetriNetsPanel panel, FramePort from, GraphElement to)
            throws Exception {
        java.lang.reflect.Field fromField = PetriNetsPanel.class.getDeclaredField("draggedFromPort");
        fromField.setAccessible(true);
        fromField.set(panel, from);

        Method finish = PetriNetsPanel.class.getDeclaredMethod("finishPortDragToFreeElement", GraphElement.class);
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

    /**
     * A port reaches the rest of the drawing, but it cannot reach past what a transition may be
     * fed by: its input places are its own object's. The drag is refused and explained rather
     * than silently dropped, since landing on that particular port was clearly deliberate.
     */
    @Test
    public void draggingFromAPlacePortToATransitionPortIsRefused() throws Exception {
        RefusalCapturingPanel panel = twoFramedObjectsPanel();
        List<GraphObjectFrame> frames = panel.getCanvasModel().getFrames();
        FramePort pa = portOf(panel, frames.get(0), "PA");
        FramePort tb = portOf(panel, frames.get(1), "TB");

        dragPort(panel, pa, tb);

        assertTrue("no input arc may run from one Petri-object into another",
                panel.getGraphNet().getGraphArcInList().isEmpty());
        assertNotNull("the user must be told why nothing happened", panel.refusal);
        assertTrue("the refusal must name both ends: " + panel.refusal,
                panel.refusal.contains("PA") && panel.refusal.contains("TB"));
        assertTrue("the refusal must point at the shared place instead: " + panel.refusal,
                panel.refusal.contains("share"));
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
    public void draggingFromAPlacePortToAFreePlaceSharesThePlace() throws Exception {
        PetriNetsPanel panel = twoFramedObjectsPanel();
        FramePort pa = portOf(panel, panel.getCanvasModel().getFrames().getFirst(), "PA");
        GraphPetriPlace free = new GraphPetriPlace(new PetriP("Free", 0), 99);
        free.setNewCoordinates(new Point2D.Double(900, 900));
        panel.getGraphNet().getGraphPetriPlaceList().add(free);

        dragPortToFreeElement(panel, pa, free);

        assertEquals(1, panel.getCanvasModel().getFusions().size());
        assertTrue(panel.getCanvasModel().getFusions().getFirst().involves(free));
        assertNull("the free place stays free — a shared place does not move it into the object",
                panel.getCanvasModel().ownerOf(free));
    }

    /** The free elements are an object of their own, so the same refusal applies to them. */
    @Test
    public void draggingFromAPlacePortToAFreeTransitionIsRefused() throws Exception {
        RefusalCapturingPanel panel = twoFramedObjectsPanel();
        FramePort pa = portOf(panel, panel.getCanvasModel().getFrames().getFirst(), "PA");
        GraphPetriTransition free = new GraphPetriTransition(new PetriT("FreeT", 1.0), 99);
        free.setNewCoordinates(new Point2D.Double(900, 900));
        panel.getGraphNet().getGraphPetriTransitionList().add(free);

        dragPortToFreeElement(panel, pa, free);

        assertTrue("a framed place may not feed a transition outside its object",
                panel.getGraphNet().getGraphArcInList().isEmpty());
        assertNotNull("the user must be told why nothing happened", panel.refusal);
        assertTrue("the refusal must name both ends: " + panel.refusal,
                panel.refusal.contains("PA") && panel.refusal.contains("FreeT"));
    }

    @Test
    public void draggingFromATransitionPortToAFreePlaceAddsAnOutputArc() throws Exception {
        PetriNetsPanel panel = twoFramedObjectsPanel();
        FramePort ta = portOf(panel, panel.getCanvasModel().getFrames().getFirst(), "TA");
        GraphPetriPlace free = new GraphPetriPlace(new PetriP("FreeP", 0), 99);
        free.setNewCoordinates(new Point2D.Double(900, 900));
        panel.getGraphNet().getGraphPetriPlaceList().add(free);

        dragPortToFreeElement(panel, ta, free);

        assertEquals(1, panel.getGraphNet().getGraphArcOutList().size());
        assertEquals(ta.getElement(), panel.getGraphNet().getGraphArcOutList().getFirst().getBeginElement());
        assertEquals(free, panel.getGraphNet().getGraphArcOutList().getFirst().getEndElement());
    }

    @Test
    public void droppingAPortDragOnAFramedElementOutsideItsOwnPortCreatesNothing() throws Exception {
        // A framed element only takes a link through its own port — landing on top of its
        // drawn body, rather than its port circle, must not silently reach it anyway.
        PetriNetsPanel panel = twoFramedObjectsPanel();
        List<GraphObjectFrame> frames = panel.getCanvasModel().getFrames();
        FramePort pa = portOf(panel, frames.getFirst(), "PA");
        GraphPetriPlace pb = null;
        for (GraphPetriPlace place : panel.getGraphNet().getGraphPetriPlaceList()) {
            if (place.getName().equals("PB")) {
                pb = place;
            }
        }

        Method free = PetriNetsPanel.class.getDeclaredMethod("freeElementAt", Point2D.class);
        free.setAccessible(true);
        Object resolved = free.invoke(panel, pb.getGraphElementCenter());

        assertNull("PB belongs to frame B, so it must not resolve as a free drop target", resolved);
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
    public void addingAPlaceOnAnObjectCanvasClaimsItForThatObject() throws Exception {
        // What the deleted modal editor's own Place/Transition buttons did, now done by the main
        // canvas's own Add Place / Add Transition tool while an object's canvas is active. There is
        // no second panel and no second net any more, so the new element lands in the one document
        // already claimed for the object it was drawn into.
        PetriNetsPanel panel = twoFramedObjectsPanel();
        GraphObjectFrame frameA = panel.getCanvasModel().getFrames().getFirst();
        panel.openObjectCanvas(frameA);
        int placesBefore = panel.getGraphNet().getGraphPetriPlaceList().size();

        addWithTool(panel, CanvasTool.ADD_PLACE, new Point(120, 200));
        addWithTool(panel, CanvasTool.ADD_TRANSITION, new Point(200, 200));

        assertEquals(placesBefore + 1, panel.getGraphNet().getGraphPetriPlaceList().size());
        GraphPetriPlace added = panel.getGraphNet().getGraphPetriPlaceList().getLast();
        GraphPetriTransition addedTransition =
                panel.getGraphNet().getGraphPetriTransitionList().getLast();
        assertSame("drawn on the object's canvas means drawn into the object",
                frameA, panel.getCanvasModel().ownerOf(added));
        assertSame(frameA, panel.getCanvasModel().ownerOf(addedTransition));
    }

    @Test
    public void theNewElementFollowsThePointerOnAnObjectCanvas() throws Exception {
        // Same assertion the deleted editor's toolbar test made: a newly added element is set as
        // current, which is what the canvas's own mouseMoved handling needs for it to follow the
        // pointer until clicked into place instead of sitting wherever a fresh one starts out.
        PetriNetsPanel panel = twoFramedObjectsPanel();
        panel.openObjectCanvas(panel.getCanvasModel().getFrames().getFirst());

        addWithTool(panel, CanvasTool.ADD_PLACE, new Point(120, 200));
        assertSame(panel.getGraphNet().getGraphPetriPlaceList().getLast(), panel.getCurrent());

        addWithTool(panel, CanvasTool.ADD_TRANSITION, new Point(200, 200));
        assertSame(panel.getGraphNet().getGraphPetriTransitionList().getLast(), panel.getCurrent());
    }

    @Test
    public void leavingAnObjectCanvasKeepsEveryEditMadeOnIt() throws Exception {
        // There is no Save and no Cancel, so there is nothing to assert about a commit: an edit
        // made on an object's canvas is an edit to the model at the moment it is made, and going
        // back to the net's canvas cannot discard it.
        PetriNetsPanel panel = twoFramedObjectsPanel();
        GraphObjectFrame frameA = panel.getCanvasModel().getFrames().getFirst();
        panel.openObjectCanvas(frameA);
        addWithTool(panel, CanvasTool.ADD_PLACE, new Point(120, 200));
        GraphPetriPlace added = panel.getGraphNet().getGraphPetriPlaceList().getLast();

        panel.openObjectCanvas(null);

        assertTrue("the element is on the one shared canvas",
                panel.getGraphNet().getGraphPetriPlaceList().contains(added));
        assertSame("and still belongs to the object it was drawn into",
                frameA, panel.getCanvasModel().ownerOf(added));
    }

    @Test
    public void constructingAPanelDoesNotResetTheSharedIdGenerator() {
        // The actual cause of arcs drawn inside an object's own editor getting matched — and
        // their weight bumped — against a completely unrelated, pre-existing arc: opening the
        // editor constructs a fresh PetriNetsPanel, whose constructor used to reset the id
        // generator every open view's elements draw their own ids from, right back to zero.
        ua.stetsenkoinna.graphnet.GraphElementIdGenerator.reset();
        int before = ua.stetsenkoinna.graphnet.GraphElementIdGenerator.next();

        new PetriNetsPanel(null, true); // simulates a second view of the same document

        int after = ua.stetsenkoinna.graphnet.GraphElementIdGenerator.next();
        assertEquals("constructing another panel must not roll the id generator back",
                before + 1, after);
    }

    @Test
    public void propertyDialogsStayUsableWhileAModalDialogIsUp() throws Exception {
        // These are plain, ownerless JFrames, so Swing has no way to tell they belong with the
        // canvas and blocks them the instant any application-modal dialog shows. The modal window
        // an object's net used to be edited in is gone, but JOptionPane confirmations - the
        // "move it to the other Petri-object?" question, the delete confirmations - are still
        // application-modal, so the exemption is still what keeps a place's own properties usable.
        PetriNetsPanel panel = new PetriNetsPanel(null, true);

        java.awt.Dialog.ModalExclusionType exclude = java.awt.Dialog.ModalExclusionType.APPLICATION_EXCLUDE;
        assertEquals(exclude, panel.setPositionFrame.getModalExclusionType());
        assertEquals(exclude, panel.setTransitionFrame.getModalExclusionType());
        assertEquals(exclude, panel.setArcFrame.getModalExclusionType());
    }

    @Test
    public void hiddenElementsIncludesOnlyEyeHiddenOrCollapsedFrameMembers() throws Exception {
        PetriNetsPanel panel = twoFramedObjectsPanel();
        List<GraphObjectFrame> frames = panel.getCanvasModel().getFrames();
        frames.get(0).setContentVisible(false);

        Method hiddenElements = PetriNetsPanel.class.getDeclaredMethod("hiddenElements");
        hiddenElements.setAccessible(true);
        @SuppressWarnings("unchecked")
        Set<GraphElement> hidden = (Set<GraphElement>) hiddenElements.invoke(panel);

        assertTrue(hidden.contains(elementNamed(panel, "PA")));
        assertTrue(hidden.contains(elementNamed(panel, "TA")));
        assertFalse("frame B is still shown", hidden.contains(elementNamed(panel, "PB")));
        assertFalse(hidden.contains(elementNamed(panel, "TB")));
    }

    @Test
    public void connectionEndpointUsesThePortOnlyWhileHidden() throws Exception {
        PetriNetsPanel panel = twoFramedObjectsPanel();
        GraphObjectFrame frameA = panel.getCanvasModel().getFrames().getFirst();
        GraphElement placeA = elementNamed(panel, "PA");

        Method connectionEndpoint = PetriNetsPanel.class.getDeclaredMethod(
                "connectionEndpoint", GraphObjectFrame.class, GraphElement.class);
        connectionEndpoint.setAccessible(true);

        Point shown = (Point) connectionEndpoint.invoke(panel, frameA, placeA);
        assertEquals("visible, so the connection anchors to the real element",
                (int) placeA.getGraphElementCenter().getX(), shown.x);

        frameA.setContentVisible(false);
        Point hiddenPoint = (Point) connectionEndpoint.invoke(panel, frameA, placeA);
        assertEquals("hidden, so the connection anchors to the port instead",
                portOf(panel, frameA, "PA").getPosition(), hiddenPoint);
    }

    @Test
    public void aCanvasWithAHiddenObjectAndACrossingArcStillPaints() throws Exception {
        PetriNetsPanel panel = twoFramedObjectsPanel();
        List<GraphObjectFrame> frames = panel.getCanvasModel().getFrames();
        dragPort(panel, portOf(panel, frames.get(0), "TA"), portOf(panel, frames.get(1), "PB"));
        frames.getFirst().setContentVisible(false);
        panel.setSize(900, 600);

        BufferedImage image = new BufferedImage(900, 600, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        panel.paintComponent(graphics);
        graphics.dispose();

        assertNotNull(panel.getCanvasModel());
    }

    @Test
    public void undoUndoesAnEditMadeOnAnObjectCanvas() throws Exception {
        // What replaces Cancel. There is no discard, because nothing is held back to discard: the
        // edit is in the model already, and Ctrl+Z is the way out of it - including across a
        // canvas switch, since there is one document and one undo history.
        PetriNetsPanel panel = twoFramedObjectsPanel();
        GraphObjectFrame frameA = panel.getCanvasModel().getFrames().getFirst();
        panel.openObjectCanvas(frameA);
        int before = panel.getGraphNet().getGraphPetriPlaceList().size();

        AddGraphElementEdit edit = addWithTool(panel, CanvasTool.ADD_PLACE, new Point(120, 200));
        GraphPetriPlace added = panel.getGraphNet().getGraphPetriPlaceList().getLast();
        panel.openObjectCanvas(null);
        edit.undo();

        assertEquals(before, panel.getGraphNet().getGraphPetriPlaceList().size());
        assertNull("undo releases it from the object too", panel.getCanvasModel().ownerOf(added));

        edit.redo();
        assertTrue(panel.getGraphNet().getGraphPetriPlaceList().contains(added));
        assertSame("redo puts it back in the same object", frameA,
                panel.getCanvasModel().ownerOf(added));
    }

    @Test
    public void boundsAroundFitsEveryGivenElementWithRoomToSpare() throws Exception {
        // This is the helper every creation path fits a frame with, so an object's own elements,
        // wherever they ended up, are what decides its outline.
        PetriP.initNext();
        GraphPetriPlace farLeft = new GraphPetriPlace(new PetriP("L", 0), 501);
        farLeft.setNewCoordinates(new Point2D.Double(20, 20));
        GraphPetriPlace farRight = new GraphPetriPlace(new PetriP("R", 0), 502);
        farRight.setNewCoordinates(new Point2D.Double(400, 300));

        Method boundsAround = PetriNetsPanel.class.getDeclaredMethod("boundsAround", List.class);
        boundsAround.setAccessible(true);
        Rectangle bounds = (Rectangle) boundsAround.invoke(null, List.of(farLeft, farRight));

        assertTrue("must reach out to the left-most element", bounds.x <= 20);
        assertTrue("must reach out to the top-most element", bounds.y <= 20);
        assertTrue("must reach out to the right-most element", bounds.x + bounds.width >= 400);
        assertTrue("must reach out to the bottom-most element", bounds.y + bounds.height >= 300);
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

    @Test
    public void rightClickNeverSelectsAnElement() throws Exception {
        PetriP.initNext();
        PetriNetsPanel panel = new PetriNetsPanel(null, true);
        GraphPetriPlace place = new GraphPetriPlace(new PetriP("Free", 0), 900);
        place.setNewCoordinates(new Point2D.Double(100, 100));
        panel.getGraphNet().getGraphPetriPlaceList().add(place);

        // popupTrigger=false matches how a right-click actually arrives on Windows, where the
        // popup fires on release rather than on press — the exact case that let a right-click
        // reach the same selection code a left-click does.
        PetriNetsPanel.MouseHandler handler = panel.new MouseHandler();
        handler.mousePressed(new MouseEvent(
                panel, MouseEvent.MOUSE_PRESSED, System.currentTimeMillis(), 0, 100, 100, 1, false, MouseEvent.BUTTON3));
        handler.mouseClicked(new MouseEvent(
                panel, MouseEvent.MOUSE_CLICKED, System.currentTimeMillis(), 0, 100, 100, 1, false, MouseEvent.BUTTON3));

        assertNull("a right-click must never select an element", panel.getChoosen());
        assertNull(panel.getCurrent());
    }

    @Test
    public void leftClickStillSelectsAnElement() throws Exception {
        // Sanity check alongside rightClickNeverSelectsAnElement, so that test isn't secretly
        // passing because nothing about this gesture selects anything any more.
        PetriP.initNext();
        PetriNetsPanel panel = new PetriNetsPanel(null, true);
        GraphPetriPlace place = new GraphPetriPlace(new PetriP("Free", 0), 900);
        place.setNewCoordinates(new Point2D.Double(100, 100));
        panel.getGraphNet().getGraphPetriPlaceList().add(place);

        PetriNetsPanel.MouseHandler handler = panel.new MouseHandler();
        handler.mousePressed(new MouseEvent(
                panel, MouseEvent.MOUSE_PRESSED, System.currentTimeMillis(), 0, 100, 100, 1, false, MouseEvent.BUTTON1));
        handler.mouseClicked(new MouseEvent(
                panel, MouseEvent.MOUSE_CLICKED, System.currentTimeMillis(), 0, 100, 100, 1, false, MouseEvent.BUTTON1));

        assertEquals(place, panel.getChoosen());
    }

    @Test
    public void selectAllAlsoSelectsEveryFrameOnThisCanvas() throws Exception {
        // Both halves are asserted. Only checking the frames would have hidden the change that
        // matters most: select-all no longer sweeps another object's members into the element
        // selection, which is what let a regrouping claim elements a frame already held.
        PetriNetsPanel panel = twoFramedObjectsPanel();

        panel.selectAll();

        assertEquals("every top-level object", new HashSet<>(panel.getCanvasModel().getFrames()),
                new HashSet<>(panel.getSelection().frames()));
        assertTrue("and nothing inside any of them", panel.getChoosenElements().isEmpty());
    }

    @Test
    public void selectAllOnAnObjectCanvasTakesItsMembersAndNothingElse() throws Exception {
        PetriNetsPanel panel = twoFramedObjectsPanel();
        GraphObjectFrame frameA = panel.getCanvasModel().getFrames().getFirst();
        panel.openObjectCanvas(frameA);

        panel.selectAll();

        assertEquals("A's own place and transition", 2, panel.getChoosenElements().size());
        assertTrue(panel.getChoosenElements().contains(elementNamed(panel, "PA")));
        assertFalse("B's are on another canvas entirely",
                panel.getChoosenElements().contains(elementNamed(panel, "PB")));
        assertTrue("A has nothing nested in it, so no frames", panel.getSelection().frames().isEmpty());
    }

    @Test
    public void draggingFromAVisibleElementsOwnBodySharesAPlace() throws Exception {
        // Both frames are shown by default (the eye starts open), so this drags from PA's own
        // drawn circle — not its, in that case undrawn, port — straight to PB's, the same
        // gesture request asked for: reach a locked, but currently visible, element directly.
        PetriNetsPanel panel = twoFramedObjectsPanel();
        GraphPetriPlace pa = (GraphPetriPlace) elementNamed(panel, "PA");
        GraphPetriPlace pb = (GraphPetriPlace) elementNamed(panel, "PB");
        assertTrue("fixture sanity check", panel.getCanvasModel().getFrames().getFirst().isContentShown());

        Point paCenter = new Point(
                (int) pa.getGraphElementCenter().getX(), (int) pa.getGraphElementCenter().getY());
        Point pbCenter = new Point(
                (int) pb.getGraphElementCenter().getX(), (int) pb.getGraphElementCenter().getY());

        PetriNetsPanel.MouseHandler handler = panel.new MouseHandler();
        handler.mousePressed(new MouseEvent(panel, MouseEvent.MOUSE_PRESSED, System.currentTimeMillis(), 0,
                paCenter.x, paCenter.y, 1, false, MouseEvent.BUTTON1));
        handler.mouseReleased(new MouseEvent(panel, MouseEvent.MOUSE_RELEASED, System.currentTimeMillis(), 0,
                pbCenter.x, pbCenter.y, 1, false, MouseEvent.BUTTON1));

        assertEquals(1, panel.getCanvasModel().getFusions().size());
        assertTrue(panel.getCanvasModel().getFusions().getFirst().involves(pa));
        assertTrue(panel.getCanvasModel().getFusions().getFirst().involves(pb));
    }

    @Test
    public void resizeHandleWinsEvenWhenAnOwnedElementSitsOnTopOfIt() throws Exception {
        // Ports could never overlap the resize handle before — they only ever sat on the
        // frame's own border — but an element's full body, now also reachable there while
        // shown, can if the frame has been shrunk small enough. The handle must still win.
        PetriP.initNext();
        PetriNetsPanel panel = new PetriNetsPanel(null, true);
        GraphObjectFrame frame = new GraphObjectFrame("F", new Rectangle(0, 0, 120, 80)); // MIN size
        GraphPetriPlace place = new GraphPetriPlace(new PetriP("P", 0), 900);
        place.setNewCoordinates(new Point2D.Double(117, 77)); // inside the resize handle's own square
        panel.getGraphNet().getGraphPetriPlaceList().add(place);
        panel.getCanvasModel().claim(frame, place);
        panel.addObjectFrame(frame);
        assertTrue("fixture sanity check: the place really does sit on the handle",
                frame.isOnResizeHandle(new Point2D.Double(117, 77)));

        PetriNetsPanel.MouseHandler handler = panel.new MouseHandler();
        handler.mousePressed(new MouseEvent(panel, MouseEvent.MOUSE_PRESSED, System.currentTimeMillis(), 0,
                117, 77, 1, false, MouseEvent.BUTTON1));

        Field resizedFrameField = PetriNetsPanel.class.getDeclaredField("resizedFrame");
        resizedFrameField.setAccessible(true);
        assertSame("the resize handle must win over the element sitting on top of it",
                frame, resizedFrameField.get(panel));

        Field draggedFromPortField = PetriNetsPanel.class.getDeclaredField("draggedFromPort");
        draggedFromPortField.setAccessible(true);
        assertNull("a link-drag must not have started instead", draggedFromPortField.get(panel));
    }

    @Test
    public void draggingFromAHiddenElementsBodyDoesNothing() throws Exception {
        // The counterpart to the test above: once content is hidden, the element's own drawn
        // body is gone, so a press at its old coordinate must not still secretly reach it.
        PetriNetsPanel panel = twoFramedObjectsPanel();
        GraphObjectFrame frameA = panel.getCanvasModel().getFrames().getFirst();
        frameA.setContentVisible(false);
        GraphPetriPlace pa = (GraphPetriPlace) elementNamed(panel, "PA");
        GraphPetriPlace pb = (GraphPetriPlace) elementNamed(panel, "PB");
        Point paCenter = new Point(
                (int) pa.getGraphElementCenter().getX(), (int) pa.getGraphElementCenter().getY());
        Point pbCenter = new Point(
                (int) pb.getGraphElementCenter().getX(), (int) pb.getGraphElementCenter().getY());

        PetriNetsPanel.MouseHandler handler = panel.new MouseHandler();
        handler.mousePressed(new MouseEvent(panel, MouseEvent.MOUSE_PRESSED, System.currentTimeMillis(), 0,
                paCenter.x, paCenter.y, 1, false, MouseEvent.BUTTON1));
        handler.mouseReleased(new MouseEvent(panel, MouseEvent.MOUSE_RELEASED, System.currentTimeMillis(), 0,
                pbCenter.x, pbCenter.y, 1, false, MouseEvent.BUTTON1));

        assertTrue(panel.getCanvasModel().getFusions().isEmpty());
    }

    @Test
    public void transitionInScopeFindsTheCorrectElementDespiteACollidingNumberElsewhere() throws Exception {
        // What animateNet() actually does to every object's own net right before a run:
        // renumber it from zero, independently of every other object's. Two different
        // transitions can end up sharing the number 0 this way — scope is what tells them
        // apart; searching the whole canvas by number alone, as this used to, could not.
        PetriNetsPanel panel = twoFramedObjectsPanel();
        GraphPetriTransition ta = (GraphPetriTransition) elementNamed(panel, "TA");
        GraphPetriTransition tb = (GraphPetriTransition) elementNamed(panel, "TB");
        ta.getPetriTransition().setNumber(0);
        tb.getPetriTransition().setNumber(0);
        GraphPetriNet scopeA = new GraphPetriNet();
        scopeA.getGraphPetriTransitionList().add(ta);

        PetriT firing = new PetriT("Firing", 1.0);
        firing.setNumber(0);

        Method transitionInScope = PetriNetsPanel.class.getDeclaredMethod(
                "transitionInScope", PetriT.class, GraphPetriNet.class);
        transitionInScope.setAccessible(true);
        Object found = transitionInScope.invoke(panel, firing, scopeA);

        assertSame("must resolve to TA, not TB, despite the colliding number", ta, found);
    }

    @Test
    public void transitionInScopeFallsBackToTheWholeCanvasWhenThereIsNoScope() throws Exception {
        PetriNetsPanel panel = twoFramedObjectsPanel();
        GraphPetriTransition ta = (GraphPetriTransition) elementNamed(panel, "TA");
        ta.getPetriTransition().setNumber(42);
        PetriT firing = new PetriT("Firing", 1.0);
        firing.setNumber(42);

        Method transitionInScope = PetriNetsPanel.class.getDeclaredMethod(
                "transitionInScope", PetriT.class, GraphPetriNet.class);
        transitionInScope.setAccessible(true);
        Object found = transitionInScope.invoke(panel, firing, null);

        assertSame(ta, found);
    }

    @SuppressWarnings("unchecked")
    private static Set<GraphObjectFrame> activeAnimationFrames(PetriNetsPanel panel) throws Exception {
        Field field = PetriNetsPanel.class.getDeclaredField("activeAnimationFrames");
        field.setAccessible(true);
        return (Set<GraphObjectFrame>) field.get(panel);
    }

    @Test
    public void setActiveAnimationFrameReplacesWhicheverWasActiveBefore() throws Exception {
        PetriNetsPanel panel = twoFramedObjectsPanel();
        List<GraphObjectFrame> frames = panel.getCanvasModel().getFrames();
        GraphObjectFrame frameA = frames.get(0);
        GraphObjectFrame frameB = frames.get(1);

        Method setActive = PetriNetsPanel.class.getDeclaredMethod("setActiveAnimationFrame", GraphObjectFrame.class);
        setActive.setAccessible(true);
        setActive.invoke(panel, frameA);
        assertNotNull(frameA.getHighlightColor());
        assertEquals(Set.of(frameA), activeAnimationFrames(panel));

        setActive.invoke(panel, frameB);

        assertNull("A is no longer the active spotlight", frameA.getHighlightColor());
        assertNotNull(frameB.getHighlightColor());
        assertEquals(Set.of(frameB), activeAnimationFrames(panel));
    }

    @Test
    public void addActiveAnimationFrameWidensTheSpotlightWithoutClearingIt() throws Exception {
        PetriNetsPanel panel = twoFramedObjectsPanel();
        List<GraphObjectFrame> frames = panel.getCanvasModel().getFrames();
        GraphObjectFrame frameA = frames.get(0);
        GraphObjectFrame frameB = frames.get(1);

        Method setActive = PetriNetsPanel.class.getDeclaredMethod("setActiveAnimationFrame", GraphObjectFrame.class);
        setActive.setAccessible(true);
        Method addActive = PetriNetsPanel.class.getDeclaredMethod("addActiveAnimationFrame", GraphObjectFrame.class);
        addActive.setAccessible(true);

        setActive.invoke(panel, frameA);
        addActive.invoke(panel, frameB);

        assertEquals(Set.of(frameA, frameB), activeAnimationFrames(panel));
        assertNotNull("A is still lit", frameA.getHighlightColor());
        assertNotNull("B is lit too, alongside it", frameB.getHighlightColor());
    }

    @Test
    public void clearAnimationHighlightTurnsEverythingOff() throws Exception {
        PetriNetsPanel panel = twoFramedObjectsPanel();
        GraphObjectFrame frameA = panel.getCanvasModel().getFrames().getFirst();
        Method setActive = PetriNetsPanel.class.getDeclaredMethod("setActiveAnimationFrame", GraphObjectFrame.class);
        setActive.setAccessible(true);
        setActive.invoke(panel, frameA);

        panel.clearAnimationHighlight();

        assertNull(frameA.getHighlightColor());
        assertTrue(activeAnimationFrames(panel).isEmpty());
    }

    @Test
    public void animateCrossingIgnoresBothEndsInTheSameObject() throws Exception {
        // Fast path: no sleep, since there is nothing to flag as a crossing here.
        PetriNetsPanel panel = twoFramedObjectsPanel();
        GraphObjectFrame frameA = panel.getCanvasModel().getFrames().getFirst();
        GraphPetriPlace pa = (GraphPetriPlace) elementNamed(panel, "PA");

        Method animateCrossing = PetriNetsPanel.class.getDeclaredMethod(
                "animateCrossing", GraphElement.class, GraphObjectFrame.class);
        animateCrossing.setAccessible(true);
        animateCrossing.invoke(panel, pa, frameA);

        assertTrue(activeAnimationFrames(panel).isEmpty());
    }

    @Test
    public void animateCrossingHighlightsTheOtherEndsFrame() throws Exception {
        PetriNetsPanel panel = twoFramedObjectsPanel();
        GraphObjectFrame frameA = panel.getCanvasModel().getFrames().get(0);
        GraphObjectFrame frameB = panel.getCanvasModel().getFrames().get(1);
        GraphPetriPlace pb = (GraphPetriPlace) elementNamed(panel, "PB");

        Method animateCrossing = PetriNetsPanel.class.getDeclaredMethod(
                "animateCrossing", GraphElement.class, GraphObjectFrame.class);
        animateCrossing.setAccessible(true);
        // pb belongs to frame B; passing frameA as "the firing transition's own frame" makes
        // this look exactly like a link crossing from A to B.
        animateCrossing.invoke(panel, pb, frameA);

        assertEquals(Set.of(frameB), activeAnimationFrames(panel));
        assertNotNull(frameB.getHighlightColor());
    }

    @Test
    public void animateCrossingHighlightsAFreeElementsSideAsNoFrameAtAll() throws Exception {
        // The "model <-> separate element" case: the far end has no owner, so there is no
        // second frame to add to the spotlight, but it must still count as a crossing worth
        // flagging (the free place itself still gets its own pulse, exercised separately).
        PetriNetsPanel panel = twoFramedObjectsPanel();
        GraphObjectFrame frameA = panel.getCanvasModel().getFrames().getFirst();
        GraphPetriPlace free = new GraphPetriPlace(new PetriP("Free", 0), 999);
        free.setNewCoordinates(new Point2D.Double(900, 900));
        panel.getGraphNet().getGraphPetriPlaceList().add(free);

        Method animateCrossing = PetriNetsPanel.class.getDeclaredMethod(
                "animateCrossing", GraphElement.class, GraphObjectFrame.class);
        animateCrossing.setAccessible(true);
        animateCrossing.invoke(panel, free, frameA);

        assertTrue("a free element has no frame to add to the spotlight",
                activeAnimationFrames(panel).isEmpty());
    }
}

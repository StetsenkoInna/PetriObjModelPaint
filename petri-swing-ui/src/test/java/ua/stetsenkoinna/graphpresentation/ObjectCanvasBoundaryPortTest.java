package ua.stetsenkoinna.graphpresentation;

import org.junit.Test;
import ua.stetsenkoinna.graphnet.FramePort;
import ua.stetsenkoinna.graphnet.GraphArcIn;
import ua.stetsenkoinna.graphnet.GraphElement;
import ua.stetsenkoinna.graphnet.GraphObjectFrame;
import ua.stetsenkoinna.graphnet.GraphPetriPlace;
import ua.stetsenkoinna.graphnet.GraphPetriTransition;
import ua.stetsenkoinna.petriobj.PetriP;
import ua.stetsenkoinna.petriobj.PetriT;

import java.awt.Rectangle;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionListener;
import java.awt.geom.Point2D;
import java.lang.reflect.Method;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/**
 * Pins how an object's own canvas presents its connections to the rest of the document, and
 * that editing there stays a plain Select-tool experience.
 *
 * <p>Two defects sat here. Leaving arc mode was impossible: the Arc tool is SELECT plus a
 * flag, so the Select button's {@code setTool(SELECT)} early-returned as a no-op and every
 * element press kept starting arcs instead of moving anything. And a link to something
 * outside the object was drawn from a bare point on the focused frame's border, which this
 * canvas deliberately never paints, so the arrow appeared out of nowhere with nothing saying
 * what was on its far end. Such links now come in through labelled boundary ports on the
 * focused frame's left edge.
 */
public class ObjectCanvasBoundaryPortTest {

    private static int idCounter = 1;

    private static PetriNetsPanel freshPanel() {
        PetriP.initNext();
        PetriT.initNext();
        idCounter = 1;
        return new PetriNetsPanel(null, true);
    }

    private static PetriNetsPanel.MouseHandler mouseHandlerOf(PetriNetsPanel panel) {
        for (java.awt.event.MouseListener listener : panel.getMouseListeners()) {
            if (listener instanceof PetriNetsPanel.MouseHandler handler) {
                return handler;
            }
        }
        throw new AssertionError("the panel registered no MouseHandler");
    }

    private static void drag(PetriNetsPanel panel, int fromX, int fromY, int toX, int toY) {
        PetriNetsPanel.MouseHandler handler = mouseHandlerOf(panel);
        MouseMotionListener motion = panel.getMouseMotionListeners()[0];
        handler.mousePressed(new MouseEvent(panel, MouseEvent.MOUSE_PRESSED,
                System.currentTimeMillis(), 0, fromX, fromY, 1, false, MouseEvent.BUTTON1));
        motion.mouseDragged(new MouseEvent(panel, MouseEvent.MOUSE_DRAGGED,
                System.currentTimeMillis(), 0, (fromX + toX) / 2, (fromY + toY) / 2, 1, false, MouseEvent.BUTTON1));
        motion.mouseDragged(new MouseEvent(panel, MouseEvent.MOUSE_DRAGGED,
                System.currentTimeMillis(), 0, toX, toY, 1, false, MouseEvent.BUTTON1));
        handler.mouseReleased(new MouseEvent(panel, MouseEvent.MOUSE_RELEASED,
                System.currentTimeMillis(), 0, toX, toY, 1, false, MouseEvent.BUTTON1));
    }

    private static GraphPetriPlace placeAt(PetriNetsPanel panel, String name, int x, int y) {
        GraphPetriPlace place = new GraphPetriPlace(new PetriP(name, 0), idCounter++);
        place.setNewCoordinates(new Point2D.Double(x, y));
        panel.getGraphNet().getGraphPetriPlaceList().add(place);
        return place;
    }

    private static GraphPetriTransition transitionAt(PetriNetsPanel panel, String name, int x, int y) {
        GraphPetriTransition transition = new GraphPetriTransition(new PetriT(name, 1.0), idCounter++);
        transition.setNewCoordinates(new Point2D.Double(x, y));
        panel.getGraphNet().getGraphPetriTransitionList().add(transition);
        return transition;
    }

    /** Object A holding transition T, with a crossing arc from free place P into T. */
    private static PetriNetsPanel focusedObjectWithIncomingArc(
            GraphPetriTransition[] memberOut, GraphPetriPlace[] outerOut) {
        PetriNetsPanel panel = freshPanel();
        GraphPetriTransition member = transitionAt(panel, "T1", 250, 200);
        GraphObjectFrame frame = new GraphObjectFrame("A", new Rectangle(180, 110, 270, 160));
        panel.addObjectFrame(frame);
        panel.getCanvasModel().claim(frame, member);
        GraphPetriPlace outer = placeAt(panel, "POut", 800, 500);
        GraphArcIn arc = new GraphArcIn();
        arc.settingNewArc(outer);
        arc.finishSettingNewArc(member);
        arc.updateCoordinates();
        panel.getGraphNet().getGraphArcInList().add(arc);
        panel.openObjectCanvas(frame);
        memberOut[0] = member;
        outerOut[0] = outer;
        return panel;
    }

    // ------------------------------------------------------------------ leaving arc mode

    /**
     * The Arc tool is SELECT plus the isSettingArc flag, so the Select button's
     * setTool(SELECT) is a same-enum switch; the early return used to swallow it, and since
     * the Arc tool stays armed across misses there was no way out of arc mode at all.
     */
    @Test
    public void theSelectButtonLeavesArcMode() {
        PetriNetsPanel panel = freshPanel();
        GraphPetriPlace place = placeAt(panel, "P1", 200, 200);
        panel.setIsSettingArc(true);

        panel.setTool(CanvasTool.SELECT);

        drag(panel, 200, 200, 320, 300);
        Point2D centre = place.getGraphElementCenter();
        assertEquals("after picking Select, a drag moves the element again",
                320, (int) centre.getX());
        assertEquals(300, (int) centre.getY());
        assertEquals("and no arc was started", 0, panel.getGraphNet().getGraphArcInList().size());
    }

    // ------------------------------------------------------------------ editing next to a crossing arc

    /**
     * The user-reported shape of the arc-mode trap: on the object's own canvas, with a
     * crossing arc present, dragging a member must move it.
     */
    @Test
    public void aMemberWithACrossingArcStillDragsOnItsOwnCanvas() {
        GraphPetriTransition[] member = new GraphPetriTransition[1];
        GraphPetriPlace[] outer = new GraphPetriPlace[1];
        PetriNetsPanel panel = focusedObjectWithIncomingArc(member, outer);

        drag(panel, 250, 200, 340, 300);

        Point2D centre = member[0].getGraphElementCenter();
        assertEquals("the member moved with the plain Select tool", 340, (int) centre.getX());
        assertEquals(300, (int) centre.getY());
    }

    // ------------------------------------------------------------------ boundary ports

    @Test
    public void aCrossingArcGetsALabelledBoundaryPortOnTheLeftEdge() throws Exception {
        GraphPetriTransition[] member = new GraphPetriTransition[1];
        GraphPetriPlace[] outer = new GraphPetriPlace[1];
        PetriNetsPanel panel = focusedObjectWithIncomingArc(member, outer);

        Method portsMethod = PetriNetsPanel.class.getDeclaredMethod("focusedBoundaryPorts");
        portsMethod.setAccessible(true);
        @SuppressWarnings("unchecked")
        List<FramePort> ports = (List<FramePort>) portsMethod.invoke(panel);

        assertEquals("one boundary port for the one outside element", 1, ports.size());
        FramePort port = ports.get(0);
        assertSame("the port stands for the outside element, so it carries its name",
                outer[0], port.getElement());
        assertEquals("and sits on the focused frame's left edge",
                180, port.getPosition().x);
    }

    @Test
    public void theCrossingArcsVisibleLineStartsAtItsBoundaryPort() throws Exception {
        GraphPetriTransition[] member = new GraphPetriTransition[1];
        GraphPetriPlace[] outer = new GraphPetriPlace[1];
        PetriNetsPanel panel = focusedObjectWithIncomingArc(member, outer);

        Method portsMethod = PetriNetsPanel.class.getDeclaredMethod("focusedBoundaryPorts");
        portsMethod.setAccessible(true);
        @SuppressWarnings("unchecked")
        List<FramePort> ports = (List<FramePort>) portsMethod.invoke(panel);
        FramePort port = ports.get(0);

        GraphArcIn arc = panel.getGraphNet().getGraphArcInList().get(0);
        Method settle = PetriNetsPanel.class.getDeclaredMethod(
                "settleCrossingSubstitute",
                ua.stetsenkoinna.graphnet.GraphArc.class, ua.stetsenkoinna.graphnet.GraphArc.class);
        settle.setAccessible(true);
        GraphArcIn temp = new GraphArcIn();
        assertTrue("the crossing arc is drawn as a substitute on this canvas",
                (boolean) settle.invoke(panel, arc, temp));

        Point2D lineStart = new Point2D.Double(
                temp.getGraphElement().getX1(), temp.getGraphElement().getY1());
        assertTrue("the visible line starts at the boundary port, not out of nowhere: "
                        + lineStart + " vs port " + port.getPosition(),
                lineStart.distance(port.getPosition().x, port.getPosition().y)
                        <= FramePort.RADIUS + 2);
    }

    /**
     * On the root canvas nothing changes: no boundary ports exist there, and a crossing
     * arc between a free element and an expanded object's member is drawn directly.
     */
    @Test
    public void theRootCanvasHasNoBoundaryPorts() throws Exception {
        GraphPetriTransition[] member = new GraphPetriTransition[1];
        GraphPetriPlace[] outer = new GraphPetriPlace[1];
        PetriNetsPanel panel = focusedObjectWithIncomingArc(member, outer);
        panel.activateRootCanvas();

        Method portsMethod = PetriNetsPanel.class.getDeclaredMethod("focusedBoundaryPorts");
        portsMethod.setAccessible(true);
        @SuppressWarnings("unchecked")
        List<FramePort> ports = (List<FramePort>) portsMethod.invoke(panel);

        assertTrue("boundary ports belong to an object's own canvas only", ports.isEmpty());
    }

    /**
     * An outside end already reachable through a port on screen - a collapsed object drawn
     * on this canvas - keeps using that object's own port instead of getting a second one.
     */
    @Test
    public void aCollapsedChildsElementDoesNotGetABoundaryPort() throws Exception {
        PetriNetsPanel panel = freshPanel();
        GraphPetriTransition member = transitionAt(panel, "T1", 250, 200);
        GraphObjectFrame frame = new GraphObjectFrame("A", new Rectangle(180, 110, 400, 300));
        panel.addObjectFrame(frame);
        panel.getCanvasModel().claim(frame, member);
        GraphPetriPlace childPlace = placeAt(panel, "PC", 350, 300);
        GraphObjectFrame child = new GraphObjectFrame("C", new Rectangle(300, 250, 160, 120));
        panel.addObjectFrame(child);
        panel.getCanvasModel().claim(child, childPlace);
        panel.getCanvasModel().nest(child, frame);
        child.setCollapsed(true);
        GraphArcIn arc = new GraphArcIn();
        arc.settingNewArc(childPlace);
        arc.finishSettingNewArc(member);
        arc.updateCoordinates();
        panel.getGraphNet().getGraphArcInList().add(arc);
        panel.openObjectCanvas(frame);

        Method portsMethod = PetriNetsPanel.class.getDeclaredMethod("focusedBoundaryPorts");
        portsMethod.setAccessible(true);
        @SuppressWarnings("unchecked")
        List<FramePort> ports = (List<FramePort>) portsMethod.invoke(panel);

        assertFalse("the collapsed child is drawn here, so its own port serves the arc",
                ports.stream().anyMatch(port -> port.getElement() == childPlace));
    }
}

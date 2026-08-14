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
import java.awt.geom.Line2D;
import java.awt.geom.Point2D;
import java.lang.reflect.Method;

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

    // ------------------------------------------------------------------ boundary stubs

    private static Line2D settledSubstitute(PetriNetsPanel panel, GraphArcIn arc) throws Exception {
        Method settle = PetriNetsPanel.class.getDeclaredMethod(
                "settleCrossingSubstitute",
                ua.stetsenkoinna.graphnet.GraphArc.class, ua.stetsenkoinna.graphnet.GraphArc.class);
        settle.setAccessible(true);
        GraphArcIn temp = new GraphArcIn();
        return (boolean) settle.invoke(panel, arc, temp) ? temp.getGraphElement() : null;
    }

    /**
     * A connection to something outside the object draws as a short stub by the connected
     * element, pointing where the outside element actually lies - not as a line strung from
     * the frame's border across the whole layout.
     */
    @Test
    public void theCrossingArcDrawsAsAShortStubTowardTheOutsideElement() throws Exception {
        GraphPetriTransition[] member = new GraphPetriTransition[1];
        GraphPetriPlace[] outer = new GraphPetriPlace[1];
        PetriNetsPanel panel = focusedObjectWithIncomingArc(member, outer);

        Line2D line = settledSubstitute(panel, panel.getGraphNet().getGraphArcInList().get(0));

        assertTrue("the crossing arc is drawn as a substitute on this canvas", line != null);
        Point2D start = new Point2D.Double(line.getX1(), line.getY1());
        Point2D innerCentre = member[0].getGraphElementCenter();
        Point2D outerCentre = outer[0].getGraphElementCenter();
        assertTrue("the free end stays a short stub away from the element: "
                        + start.distance(innerCentre) + "px", start.distance(innerCentre) <= 56);
        assertTrue("and points toward the outside element",
                start.distance(outerCentre) < innerCentre.distance(outerCentre));
    }

    @Test
    public void aParkedStubStaysWhereTheUserPutIt() throws Exception {
        GraphPetriTransition[] member = new GraphPetriTransition[1];
        GraphPetriPlace[] outer = new GraphPetriPlace[1];
        PetriNetsPanel panel = focusedObjectWithIncomingArc(member, outer);
        GraphArcIn arc = panel.getGraphNet().getGraphArcInList().get(0);

        // Grab the derived stub end and drag it above the element.
        Line2D derived = settledSubstitute(panel, arc);
        drag(panel, (int) derived.getX1(), (int) derived.getY1(), 250, 120);

        assertTrue("the drag parked an offset on the arc", arc.getBoundaryStubOffset() != null);
        Line2D parked = settledSubstitute(panel, arc);
        assertEquals("the stub now sits where it was dropped",
                250, (int) parked.getX1());
        assertEquals(120, (int) parked.getY1());
        Point2D centre = member[0].getGraphElementCenter();
        assertEquals("and the element did not move", 250, (int) centre.getX());
        assertEquals(200, (int) centre.getY());
    }

    /**
     * On the root canvas nothing changes: a crossing arc between a free element and an
     * expanded object's member has both ends on screen and is drawn directly.
     */
    @Test
    public void theRootCanvasDrawsTheCrossingArcDirectly() throws Exception {
        GraphPetriTransition[] member = new GraphPetriTransition[1];
        GraphPetriPlace[] outer = new GraphPetriPlace[1];
        PetriNetsPanel panel = focusedObjectWithIncomingArc(member, outer);
        panel.activateRootCanvas();

        assertTrue("no substitute on the root canvas, the real arc is drawn",
                settledSubstitute(panel, panel.getGraphNet().getGraphArcInList().get(0)) == null);
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

        Line2D line = settledSubstitute(panel, arc);
        assertTrue("the crossing arc is substituted on this canvas", line != null);
        Point2D start = new Point2D.Double(line.getX1(), line.getY1());
        boolean atChildsOwnPort = false;
        for (FramePort port : panel.getCanvasModel().portsOf(child)) {
            if (port.getElement() == childPlace
                    && start.distance(port.getPosition().x, port.getPosition().y)
                            <= FramePort.RADIUS + 2) {
                atChildsOwnPort = true;
            }
        }
        assertTrue("the collapsed child is drawn here, so its own port serves the arc, "
                + "not a stub: " + start, atChildsOwnPort);
    }
}

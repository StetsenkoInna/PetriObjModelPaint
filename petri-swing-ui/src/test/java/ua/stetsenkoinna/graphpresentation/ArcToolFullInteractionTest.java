package ua.stetsenkoinna.graphpresentation;

import org.junit.Test;

import ua.stetsenkoinna.graphnet.GraphArc;
import ua.stetsenkoinna.graphnet.GraphArcOut;
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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Drives the Arc tool through the real {@code MouseHandler}, press to release, instead of calling
 * {@code arcToolTargetAt} directly - the isolated-method tests in {@code ArcToolCrossObjectTest}
 * prove the resolution logic is right, but not that the whole gesture actually reaches it and
 * ends up with a correctly trimmed line the way an ordinary same-canvas arc already is.
 */
public class ArcToolFullInteractionTest {

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

    private static MouseMotionListener motionHandlerOf(PetriNetsPanel panel) {
        MouseMotionListener[] listeners = panel.getMouseMotionListeners();
        assertTrue("the panel registered no mouse motion listener", listeners.length > 0);
        return listeners[0];
    }

    private static MouseEvent event(PetriNetsPanel panel, int id, int x, int y) {
        return new MouseEvent(panel, id, System.currentTimeMillis(), 0, x, y, 1, false, MouseEvent.BUTTON1);
    }

    /** Draws an arc with the real Arc tool: click the source, drag, release on the target. */
    private static void drawArcWithTool(PetriNetsPanel panel, int fromX, int fromY, int toX, int toY) {
        panel.setIsSettingArc(true);
        PetriNetsPanel.MouseHandler handler = mouseHandlerOf(panel);
        MouseMotionListener motion = motionHandlerOf(panel);
        handler.mousePressed(event(panel, MouseEvent.MOUSE_PRESSED, fromX, fromY));
        motion.mouseDragged(event(panel, MouseEvent.MOUSE_DRAGGED, (fromX + toX) / 2, (fromY + toY) / 2));
        handler.mouseReleased(event(panel, MouseEvent.MOUSE_RELEASED, toX, toY));
    }

    @Test
    public void theArcToolConnectsAFreeTransitionToAPlaceInsideAnExpandedObject() {
        PetriNetsPanel panel = freshPanel();

        GraphPetriTransition freeTransition = new GraphPetriTransition(new PetriT("Free", 1.0), idCounter++);
        freeTransition.setNewCoordinates(new Point2D.Double(400, 140));
        panel.getGraphNet().getGraphPetriTransitionList().add(freeTransition);

        GraphPetriPlace framedPlace = new GraphPetriPlace(new PetriP("Pin", 1), idCounter++);
        framedPlace.setNewCoordinates(new Point2D.Double(120, 140));
        panel.getGraphNet().getGraphPetriPlaceList().add(framedPlace);
        GraphObjectFrame frame = new GraphObjectFrame("Other", new Rectangle(40, 40, 160, 160));
        panel.getCanvasModel().claim(frame, framedPlace);
        panel.addObjectFrame(frame);

        drawArcWithTool(panel, 400, 140, 120, 140);

        assertEquals("the arc tool must actually create the crossing arc",
                1, panel.getGraphNet().getGraphArcOutList().size());
        GraphArc created = panel.getGraphNet().getGraphArcOutList().get(0);
        assertEquals(freeTransition, created.getBeginElement());
        assertEquals(framedPlace, created.getEndElement());
    }

    @Test
    public void theArcToolConnectsAFreePlaceToATransitionInsideACollapsedObject() {
        PetriNetsPanel panel = freshPanel();

        GraphPetriPlace freePlace = new GraphPetriPlace(new PetriP("Free", 1), idCounter++);
        freePlace.setNewCoordinates(new Point2D.Double(400, 140));
        panel.getGraphNet().getGraphPetriPlaceList().add(freePlace);

        GraphPetriTransition framedTransition = new GraphPetriTransition(new PetriT("Tin", 1.0), idCounter++);
        framedTransition.setNewCoordinates(new Point2D.Double(120, 140));
        panel.getGraphNet().getGraphPetriTransitionList().add(framedTransition);
        GraphObjectFrame frame = new GraphObjectFrame("Hidden", new Rectangle(40, 40, 160, 160));
        panel.getCanvasModel().claim(frame, framedTransition);
        panel.addObjectFrame(frame);
        frame.setCollapsed(true);

        var port = panel.getCanvasModel().portsOf(frame).stream()
                .filter(p -> p.getElement() == framedTransition)
                .findFirst()
                .orElseThrow();

        drawArcWithTool(panel, 400, 140, port.getPosition().x, port.getPosition().y);

        assertEquals("the arc tool must reach the transition through its port",
                1, panel.getGraphNet().getGraphArcInList().size());
        GraphArc created = panel.getGraphNet().getGraphArcInList().get(0);
        assertEquals(freePlace, created.getBeginElement());
        assertEquals(framedTransition, created.getEndElement());
    }

    @Test
    public void aCrossObjectArcIsTrimmedToTheElementsBordersLikeAnOrdinaryArc() {
        PetriNetsPanel panel = freshPanel();

        GraphPetriTransition freeTransition = new GraphPetriTransition(new PetriT("Free", 1.0), idCounter++);
        freeTransition.setNewCoordinates(new Point2D.Double(400, 140));
        panel.getGraphNet().getGraphPetriTransitionList().add(freeTransition);

        GraphPetriPlace framedPlace = new GraphPetriPlace(new PetriP("Pin", 1), idCounter++);
        framedPlace.setNewCoordinates(new Point2D.Double(120, 140));
        panel.getGraphNet().getGraphPetriPlaceList().add(framedPlace);
        GraphObjectFrame frame = new GraphObjectFrame("Other", new Rectangle(40, 40, 160, 160));
        panel.getCanvasModel().claim(frame, framedPlace);
        panel.addObjectFrame(frame);

        drawArcWithTool(panel, 400, 140, 120, 140);

        GraphArcOut created = panel.getGraphNet().getGraphArcOutList().get(0);
        Line2D line = created.getGraphElement();

        Point2D placeCentre = framedPlace.getGraphElementCenter();
        Point2D transitionCentre = freeTransition.getGraphElementCenter();
        double placeRadius = framedPlace.getBorder();

        // Neither end may sit at the other shape's exact centre - changeBorder() must have
        // pulled the line in to the border on both sides, the same as any same-canvas arc.
        assertTrue("the line must stop short of the place's centre, at its border",
                line.getP2().distance(placeCentre) > placeRadius - 1);
        assertTrue("the line must not reach all the way into the transition's centre either",
                line.getP1().distance(transitionCentre) > 0.5);
    }
}

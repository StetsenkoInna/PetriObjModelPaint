package ua.stetsenkoinna.graphpresentation;

import org.junit.Test;

import ua.stetsenkoinna.graphnet.GraphObjectFrame;
import ua.stetsenkoinna.graphnet.GraphPetriPlace;
import ua.stetsenkoinna.petriobj.PetriP;

import java.awt.Rectangle;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionListener;
import java.awt.geom.Point2D;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * A shared place between a free place and one inside a Petri-object is not an arc - arcs only
 * ever connect a place to a transition, on one canvas or across an object boundary alike, which
 * is exactly why {@code finishSettingNewArc} rejects a place-to-place pairing outright (see
 * {@code ArcToolFullInteractionTest} for the arc side of the same boundary). Two places sharing
 * one marking is a fusion instead, reachable only by dragging from a place's own port - never
 * from the Arc tool - which is what this pins down for the cross-object case specifically.
 */
public class PlaceFusionAcrossObjectTest {

    private static int idCounter = 1;

    private static PetriNetsPanel freshPanel() {
        PetriP.initNext();
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

    private static void dragFromTo(PetriNetsPanel panel, int fromX, int fromY, int toX, int toY) {
        PetriNetsPanel.MouseHandler handler = mouseHandlerOf(panel);
        MouseMotionListener motion = motionHandlerOf(panel);
        handler.mousePressed(event(panel, MouseEvent.MOUSE_PRESSED, fromX, fromY));
        motion.mouseDragged(event(panel, MouseEvent.MOUSE_DRAGGED, (fromX + toX) / 2, (fromY + toY) / 2));
        handler.mouseReleased(event(panel, MouseEvent.MOUSE_RELEASED, toX, toY));
    }

    @Test
    public void draggingFromTheFramedPlacesPortToAFreePlaceFusesThem() {
        PetriNetsPanel panel = freshPanel();

        GraphPetriPlace framedPlace = new GraphPetriPlace(new PetriP("Pin", 1), idCounter++);
        framedPlace.setNewCoordinates(new Point2D.Double(120, 140));
        panel.getGraphNet().getGraphPetriPlaceList().add(framedPlace);
        GraphObjectFrame frame = new GraphObjectFrame("Other", new Rectangle(40, 40, 160, 160));
        panel.getCanvasModel().claim(frame, framedPlace);
        panel.addObjectFrame(frame);

        GraphPetriPlace freePlace = new GraphPetriPlace(new PetriP("Free", 1), idCounter++);
        freePlace.setNewCoordinates(new Point2D.Double(400, 140));
        panel.getGraphNet().getGraphPetriPlaceList().add(freePlace);

        var port = panel.getCanvasModel().portsOf(frame).stream()
                .filter(p -> p.getElement() == framedPlace)
                .findFirst()
                .orElseThrow();

        // The drag starts ON THE PORT, not on either place's own body - a place has no port
        // until something is claimed for it, and starting from the free place instead would
        // just be an ordinary element drag, never a link gesture.
        dragFromTo(panel, port.getPosition().x, port.getPosition().y, 400, 140);

        assertEquals("the two places must now share one fusion",
                1, panel.getCanvasModel().getFusions().size());
        var fusion = panel.getCanvasModel().getFusions().get(0);
        assertTrue("both the framed and the free place are the two sides of it",
                (fusion.getMaster() == framedPlace && fusion.getJoined() == freePlace)
                        || (fusion.getMaster() == freePlace && fusion.getJoined() == framedPlace));
    }

    @Test
    public void theArcToolItselfNeverCreatesAFusionEvenAcrossAnObjectBoundary() {
        // The negative case the user actually hit: starting from a free PLACE with the Arc tool
        // and releasing on a place inside an object. finishSettingNewArc rejects same-class
        // pairs by design - this is true on a single plain canvas too, nothing to do with objects.
        PetriNetsPanel panel = freshPanel();

        GraphPetriPlace freePlace = new GraphPetriPlace(new PetriP("Free", 1), idCounter++);
        freePlace.setNewCoordinates(new Point2D.Double(400, 140));
        panel.getGraphNet().getGraphPetriPlaceList().add(freePlace);

        GraphPetriPlace framedPlace = new GraphPetriPlace(new PetriP("Pin", 1), idCounter++);
        framedPlace.setNewCoordinates(new Point2D.Double(120, 140));
        panel.getGraphNet().getGraphPetriPlaceList().add(framedPlace);
        GraphObjectFrame frame = new GraphObjectFrame("Other", new Rectangle(40, 40, 160, 160));
        panel.getCanvasModel().claim(frame, framedPlace);
        panel.addObjectFrame(frame);

        panel.setIsSettingArc(true);
        dragFromTo(panel, 400, 140, 120, 140);

        assertEquals("no arc gets created for a place-to-place pairing",
                0, panel.getGraphNet().getGraphArcInList().size());
        assertEquals("and no fusion either - the Arc tool cannot make one",
                0, panel.getCanvasModel().getFusions().size());
    }
}

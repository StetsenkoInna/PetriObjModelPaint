package ua.stetsenkoinna.graphpresentation;

import org.junit.Test;

import ua.stetsenkoinna.graphnet.FramePort;
import ua.stetsenkoinna.graphnet.GraphArcFactory;
import ua.stetsenkoinna.graphnet.GraphArcIn;
import ua.stetsenkoinna.graphnet.GraphObjectFrame;
import ua.stetsenkoinna.graphnet.GraphPetriPlace;
import ua.stetsenkoinna.graphnet.GraphPetriTransition;
import ua.stetsenkoinna.petriobj.PetriP;
import ua.stetsenkoinna.petriobj.PetriT;

import java.awt.Point;
import java.awt.Rectangle;
import java.awt.geom.Point2D;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

/**
 * A click on a crossing arc's visible line must find the real arc, so it can be selected and
 * deleted - not just the real, mostly off-screen line {@link PetriNetsPanel#findArc} used to
 * hit-test instead.
 *
 * <p>Whenever one end of an arc belongs to an object whose content is hidden (collapsed, or the
 * eye toggled off), {@code paintCrossingArcSubstitute} draws the arc reaching to a port on that
 * object's border rather than to the real, invisible element - see its class doc. {@code findArc}
 * used to still hit-test the real arc's own line, which reaches all the way to the hidden element
 * and is never actually painted, so a click anywhere on the line the user can actually see missed
 * every time.
 */
public class CrossingArcHitTestTest {

    private static GraphObjectFrame frameHidingOnePlace(PetriNetsPanel panel, GraphPetriPlace place) {
        GraphObjectFrame frame = new GraphObjectFrame("Source", new Rectangle(40, 40, 160, 160));
        panel.getCanvasModel().claim(frame, place);
        panel.addObjectFrame(frame);
        // Without this the place's content is shown, so it is drawn (and hit-tested) directly,
        // the same as a free element - no substitute involved at all.
        frame.setCollapsed(true);
        return frame;
    }

    /**
     * A point well inside the visible segment between a hidden object's port and a free
     * element's centre, clear of the border-trimming at both ends - not the port position
     * itself, which the trim can pull the line back from.
     */
    private static Point2D midpointOfVisibleLine(
            PetriNetsPanel panel, GraphObjectFrame frame, GraphPetriPlace framedPlace, GraphPetriTransition free) {
        FramePort port = panel.getCanvasModel().portsOf(frame).stream()
                .filter(p -> p.getElement() == framedPlace)
                .findFirst()
                .orElseThrow();
        Point portPosition = port.getPosition();
        Point2D freeCentre = free.getGraphElementCenter();
        return new Point2D.Double(
                (portPosition.x + freeCentre.getX()) / 2,
                (portPosition.y + freeCentre.getY()) / 2);
    }

    @Test
    public void clickingTheVisibleCrossingLineFindsTheRealArc() {
        PetriNetsPanel panel = new PetriNetsPanel(null, false);

        GraphPetriPlace framedPlace = new GraphPetriPlace(new PetriP("Pin", 1), 1);
        framedPlace.setNewCoordinates(new Point2D.Double(120, 140));
        panel.getGraphNet().getGraphPetriPlaceList().add(framedPlace);
        GraphObjectFrame frame = frameHidingOnePlace(panel, framedPlace);

        GraphPetriTransition freeTransition = new GraphPetriTransition(new PetriT("Sink", 1.0), 2);
        freeTransition.setNewCoordinates(new Point2D.Double(500, 120));
        panel.getGraphNet().getGraphPetriTransitionList().add(freeTransition);

        GraphArcIn arc = GraphArcFactory.inArc(framedPlace, freeTransition, 1, false);
        panel.getGraphNet().getGraphArcInList().add(arc);

        Point2D onTheVisibleLine = midpointOfVisibleLine(panel, frame, framedPlace, freeTransition);

        assertSame("a click on the line actually drawn must find the real arc",
                arc, panel.findArc(onTheVisibleLine));
    }

    @Test
    public void anArcFullyInsideAHiddenObjectIsNotHitAtAll() {
        // The other half of the same fix: an arc where BOTH ends belong to the same hidden
        // object has no substitute at all - paintCrossingArcSubstitute lets it simply vanish
        // with the rest of the object's net - so findArc must not invent a hit for it either.
        PetriNetsPanel panel = new PetriNetsPanel(null, false);

        GraphPetriPlace place = new GraphPetriPlace(new PetriP("P", 1), 1);
        place.setNewCoordinates(new Point2D.Double(100, 100));
        panel.getGraphNet().getGraphPetriPlaceList().add(place);
        GraphPetriTransition transition = new GraphPetriTransition(new PetriT("T", 1.0), 2);
        transition.setNewCoordinates(new Point2D.Double(160, 100));
        panel.getGraphNet().getGraphPetriTransitionList().add(transition);

        GraphObjectFrame frame = new GraphObjectFrame("Hidden", new Rectangle(40, 40, 200, 160));
        panel.getCanvasModel().claim(frame, place);
        panel.getCanvasModel().claim(frame, transition);
        panel.addObjectFrame(frame);
        frame.setCollapsed(true);

        GraphArcIn arc = GraphArcFactory.inArc(place, transition, 1, false);
        panel.getGraphNet().getGraphArcInList().add(arc);

        assertNull("nothing of a fully-hidden object's net, arcs included, is reachable here",
                panel.findArc(new Point2D.Double(130, 100)));
    }

    @Test
    public void theFoundArcCanActuallyBeRemoved() {
        PetriNetsPanel panel = new PetriNetsPanel(null, false);

        GraphPetriPlace framedPlace = new GraphPetriPlace(new PetriP("Pin", 1), 1);
        framedPlace.setNewCoordinates(new Point2D.Double(120, 140));
        panel.getGraphNet().getGraphPetriPlaceList().add(framedPlace);
        GraphObjectFrame frame = frameHidingOnePlace(panel, framedPlace);

        GraphPetriTransition freeTransition = new GraphPetriTransition(new PetriT("Sink", 1.0), 2);
        freeTransition.setNewCoordinates(new Point2D.Double(500, 120));
        panel.getGraphNet().getGraphPetriTransitionList().add(freeTransition);

        GraphArcIn arc = GraphArcFactory.inArc(framedPlace, freeTransition, 1, false);
        panel.getGraphNet().getGraphArcInList().add(arc);

        Point2D onTheVisibleLine = midpointOfVisibleLine(panel, frame, framedPlace, freeTransition);
        assertNotNull(panel.findArc(onTheVisibleLine));

        panel.removeArc(arc);

        assertNull("removeArc must actually take the crossing arc off the net",
                panel.findArc(onTheVisibleLine));
    }
}

package pnml;

import org.junit.Test;
import ua.stetsenkoinna.graphnet.CanvasItem;
import ua.stetsenkoinna.graphnet.GraphObjectFrame;
import ua.stetsenkoinna.graphnet.GraphPetriPlace;
import ua.stetsenkoinna.graphnet.GraphPetriTransition;
import ua.stetsenkoinna.graphnet.PortAnchor;
import ua.stetsenkoinna.petriobj.PetriP;
import ua.stetsenkoinna.petriobj.PetriT;

import java.awt.Rectangle;
import java.awt.geom.Point2D;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * What every {@link CanvasItem} promises regardless of which kind it turned out to be: a place
 * or transition ({@link ua.stetsenkoinna.graphnet.GraphElement}), or the frame that marks out a
 * Petri-object ({@link GraphObjectFrame}). Each kind keeps its own anchor underneath - an
 * element's centre, a frame's top-left corner - these pin that the interface still reports the
 * same hit-test, bounds and delta-move regardless of which anchor is doing the work.
 */
public class CanvasItemTest {

    private static int idCounter = 1;

    private static void resetCounters() {
        PetriP.initNext();
        PetriT.initNext();
        idCounter = 1;
    }

    private static GraphPetriPlace place(double x, double y) {
        GraphPetriPlace place = new GraphPetriPlace(new PetriP("P" + idCounter, 0), idCounter++);
        place.setNewCoordinates(new Point2D.Double(x, y));
        return place;
    }

    private static GraphPetriTransition transition(double x, double y) {
        GraphPetriTransition transition = new GraphPetriTransition(new PetriT("T" + idCounter, 1.0), idCounter++);
        transition.setNewCoordinates(new Point2D.Double(x, y));
        return transition;
    }

    @Test
    public void aPlaceHitTestsAtItsCentreAndMissesFarAway() {
        resetCounters();
        CanvasItem place = place(100, 100);
        assertTrue(place.containsPoint(new Point2D.Double(100, 100)));
        assertFalse(place.containsPoint(new Point2D.Double(500, 500)));
    }

    @Test
    public void aPlaceMovesByADeltaKeepingItsCentreAnchor() {
        resetCounters();
        GraphPetriPlace place = place(100, 100);
        ((CanvasItem) place).moveBy(30, -10);

        assertEquals(new Point2D.Double(130, 90), place.getGraphElementCenter());
        // getBounds() must move with it, not just the centre getGraphElementCenter() reports.
        Rectangle bounds = ((CanvasItem) place).getBounds();
        assertTrue("moved bounds must still be centred on the new centre",
                bounds.contains(130, 90));
    }

    @Test
    public void aTransitionHitTestsAndMovesTheSameWayAPlaceDoes() {
        resetCounters();
        GraphPetriTransition transition = transition(200, 200);
        CanvasItem item = transition;

        assertTrue(item.containsPoint(new Point2D.Double(200, 200)));

        item.moveBy(-50, 25);
        assertEquals(new Point2D.Double(150, 225), transition.getGraphElementCenter());
    }

    @Test
    public void aFrameHitTestsWithinItsBoundsAndMissesOutsideThem() {
        GraphObjectFrame frame = new GraphObjectFrame("O", new Rectangle(50, 50, 200, 100));
        CanvasItem item = frame;

        assertTrue(item.containsPoint(new Point2D.Double(60, 60)));
        assertFalse(item.containsPoint(new Point2D.Double(10, 10)));
    }

    @Test
    public void aFrameMovesByADeltaKeepingItsTopLeftAnchor() {
        GraphObjectFrame frame = new GraphObjectFrame("O", new Rectangle(50, 50, 200, 100));
        CanvasItem item = frame;

        item.moveBy(40, 15);

        assertEquals(new Rectangle(90, 65, 200, 100), frame.getBounds());
        assertEquals(new Rectangle(90, 65, 200, 100), item.getBounds());
    }

    @Test
    public void aFrameNeverMovesPastTheCanvasEdge() {
        // GraphObjectFrame.moveTo clamps to (0,0); moveBy delegates to it rather than bypassing
        // that clamp, the same way the panel's own drag handling already relied on.
        GraphObjectFrame frame = new GraphObjectFrame("O", new Rectangle(10, 10, 200, 100));
        CanvasItem item = frame;

        item.moveBy(-100, -100);

        assertEquals(0, frame.getBounds().x);
        assertEquals(0, frame.getBounds().y);
    }

    @Test
    public void aPortAnchorReportsAnHonestBoundingBoxAroundItsBorder() {
        // An anchor has no shape of its own to draw - see the class doc - but its CanvasItem
        // bounds should still be the square GraphArc#changeBorder() already treats it as
        // occupying via getBorder(), not an empty or null rectangle.
        PortAnchor anchor = new PortAnchor(new Point2D.Double(100, 100), 8);
        CanvasItem item = anchor;

        assertEquals(new Rectangle(92, 92, 16, 16), item.getBounds());
    }
}

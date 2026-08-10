package pnml;

import org.junit.Test;
import ua.stetsenkoinna.graphnet.GraphArcIn;
import ua.stetsenkoinna.graphnet.GraphElement;
import ua.stetsenkoinna.graphnet.GraphPetriPlace;
import ua.stetsenkoinna.graphnet.GraphPetriTransition;
import ua.stetsenkoinna.graphnet.PortAnchor;
import ua.stetsenkoinna.petriobj.ArcIn;
import ua.stetsenkoinna.petriobj.ArcOut;
import ua.stetsenkoinna.petriobj.PetriP;
import ua.stetsenkoinna.petriobj.PetriT;

import java.awt.geom.Line2D;
import java.awt.geom.Point2D;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * {@link ua.stetsenkoinna.graphnet.GraphArc#changeBorder()} trims a drawn arc to the border of
 * whatever it connects rather than the bare centre — a place (or a stand-in for one, like a
 * {@link PortAnchor}) by a simple radius, a transition by its rectangle. These pin down the
 * exact trimmed line for known layouts, so a refactor of how that routing decision is made
 * (which is exactly what introducing {@code GraphElement.isCircular()} was) cannot silently
 * change what actually gets drawn.
 */
public class GraphArcTest {

    private static int idCounter = 1;

    private static void resetCounters() {
        PetriP.initNext();
        PetriT.initNext();
        ArcIn.initNext();
        ArcOut.initNext();
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

    private static Line2D trimmedLine(GraphElement begin, GraphElement end) {
        GraphArcIn arc = new GraphArcIn();
        arc.settingNewArc(begin); // also what initializes the arc's own Line2D
        arc.setEndElement(end);
        arc.changeBorder();
        return arc.getGraphElement();
    }

    @Test
    public void twoCircularEndsAreEachTrimmedByTheirOwnRadius() {
        resetCounters();
        GraphPetriPlace a = place(0, 0);
        GraphPetriPlace b = place(100, 0);
        // A place's diameter is 40 everywhere in this codebase, so its radius is 20; the line
        // between two of them 100 apart should stop 20 in from each centre, on both sides.
        assertEquals(20, a.getBorder());

        Line2D line = trimmedLine(a, b);

        assertEquals(20.0, line.getX1(), 0.001);
        assertEquals(0.0, line.getY1(), 0.001);
        assertEquals(80.0, line.getX2(), 0.001);
        assertEquals(0.0, line.getY2(), 0.001);
    }

    @Test
    public void aTransitionIsStillTrimmedByItsRectangleNotARadius() {
        // Confirms isCircular() routing did not quietly turn a transition circular: it must
        // still stop at its rectangle's edge, not at centre-minus-radius the way a place would.
        resetCounters();
        GraphPetriPlace p = place(0, 0);
        GraphPetriTransition t = transition(200, 0);
        assertEquals(19, t.getWidth());

        Line2D line = trimmedLine(p, t);

        assertEquals(20.0, line.getX1(), 0.001); // p's own radius, unaffected by t's shape
        assertEquals(0.0, line.getY1(), 0.001);
        // t's rectangle spans x in [190.5, 209.5]; approached from the left, the line stops at
        // the near edge, 9.5 (half of t's width) short of its centre — not at its centre.
        assertEquals(190.5, line.getX2(), 0.001);
        assertEquals(0.0, line.getY2(), 0.001);
    }

    @Test
    public void aPortAnchorTrimsExactlyLikeAPlaceOfItsOwnRadius() {
        resetCounters();
        PortAnchor port = new PortAnchor(new Point2D.Double(0, 0), 6);
        GraphPetriPlace b = place(100, 0);

        Line2D line = trimmedLine(port, b);

        assertEquals(6.0, line.getX1(), 0.001);  // the port's own radius, not a place's
        assertEquals(0.0, line.getY1(), 0.001);
        assertEquals(80.0, line.getX2(), 0.001); // b's radius (20), same as the place-place case
        assertEquals(0.0, line.getY2(), 0.001);
    }

    @Test
    public void aZeroBorderPortAnchorIsNotTrimmedAtAll() {
        // What the drag-in-progress preview's loose end (the pointer, not yet anything real)
        // uses: nothing to trim against, so the line reaches exactly to it.
        resetCounters();
        GraphPetriPlace a = place(0, 0);
        PortAnchor pointer = new PortAnchor(new Point2D.Double(100, 0), 0);

        Line2D line = trimmedLine(a, pointer);

        assertEquals(20.0, line.getX1(), 0.001);
        assertEquals(100.0, line.getX2(), 0.001);
        assertTrue("a zero-border anchor is reached exactly, not trimmed short",
                line.getX2() == 100.0);
    }
}

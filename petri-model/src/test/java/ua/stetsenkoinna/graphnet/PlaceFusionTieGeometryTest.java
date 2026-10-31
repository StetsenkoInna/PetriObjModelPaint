package ua.stetsenkoinna.graphnet;

import org.junit.Test;

import java.awt.geom.Path2D;
import java.awt.geom.PathIterator;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * A shared place's tie is a double line with an open arrowhead at the joined end, pointing from
 * the master to the joined half. These pin the geometry {@link GraphPlaceFusion#drawBetweenPorts}
 * strokes, since nothing else checks what a tie looks like.
 */
public class PlaceFusionTieGeometryTest {

    private static final Point2D MASTER = new Point2D.Double(0, 0);
    private static final Point2D JOINED = new Point2D.Double(100, 0);

    private static List<double[]> segments(Path2D path) {
        List<double[]> points = new ArrayList<>();
        double[] coords = new double[6];
        for (PathIterator it = path.getPathIterator(null); !it.isDone(); it.next()) {
            int type = it.currentSegment(coords);
            points.add(new double[] {type, coords[0], coords[1]});
        }
        return points;
    }

    @Test
    public void theTieIsTwoParallelRailsEitherSideOfTheLine() {
        List<double[]> rails = segments(GraphPlaceFusion.tieRails(MASTER, JOINED));

        assertEquals("two rails, each a move and a line", 4, rails.size());
        assertEquals(PathIterator.SEG_MOVETO, (int) rails.get(0)[0]);
        assertEquals(PathIterator.SEG_MOVETO, (int) rails.get(2)[0]);
        assertEquals("one rail above the line", 2.5, rails.get(0)[2], 1e-9);
        assertEquals(2.5, rails.get(1)[2], 1e-9);
        assertEquals("and one below it", -2.5, rails.get(2)[2], 1e-9);
        assertEquals(-2.5, rails.get(3)[2], 1e-9);
        assertEquals("the rails start at the master", 0, rails.get(0)[1], 1e-9);
        assertEquals("and stop short of the tip, inside the arrowhead",
                100 - 2.5 / Math.tan(Math.toRadians(30)), rails.get(1)[1], 1e-9);
    }

    @Test
    public void theArrowheadTipIsAtTheJoinedEnd() {
        List<double[]> arrow = segments(GraphPlaceFusion.tieArrowhead(MASTER, JOINED));

        assertEquals("an open arrowhead: two arms meeting at the tip", 3, arrow.size());
        assertEquals("the tip is on the joined half", 100, arrow.get(1)[1], 1e-9);
        assertEquals(0, arrow.get(1)[2], 1e-9);
        assertTrue("the arms sweep back toward the master", arrow.get(0)[1] < 100);
        assertTrue(arrow.get(2)[1] < 100);
    }

    @Test
    public void aZeroLengthTieStillDrawsWithoutNaN() {
        Point2D same = new Point2D.Double(5, 5);
        for (double[] point : segments(GraphPlaceFusion.tieRails(same, same))) {
            assertFalse(Double.isNaN(point[1]) || Double.isNaN(point[2]));
        }
        for (double[] point : segments(GraphPlaceFusion.tieArrowhead(same, same))) {
            assertFalse(Double.isNaN(point[1]) || Double.isNaN(point[2]));
        }
    }
}

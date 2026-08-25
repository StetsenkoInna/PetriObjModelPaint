package ua.stetsenkoinna.graphpresentation;

import org.junit.Test;

import ua.stetsenkoinna.graphnet.GraphObjectFrame;
import ua.stetsenkoinna.graphnet.GraphPetriPlace;
import ua.stetsenkoinna.graphnet.GraphPlaceFusion;
import ua.stetsenkoinna.petriobj.PetriP;

import java.awt.Rectangle;
import java.awt.geom.Line2D;
import java.awt.geom.Point2D;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/**
 * The line a fusion anchored to a Petri-object is drawn as must stop at each half's own border,
 * the same way every other connection on the canvas does - {@link GraphPlaceFusion#drawBetweenPorts}
 * just draws whatever line it is handed, so the trimming has to happen before that, in
 * {@code PetriNetsPanel#trimmedFusionLine}. It used to draw straight to each half's raw centre
 * point, which read as the connection ending inside the place instead of at its edge.
 */
public class PlaceFusionRenderingTest {

    private static int idCounter = 1;

    private static PetriNetsPanel freshPanel() {
        PetriP.initNext();
        idCounter = 1;
        return new PetriNetsPanel(null, true);
    }

    private static Line2D trimmedFusionLine(PetriNetsPanel panel, GraphPlaceFusion fusion) {
        try {
            Method method = PetriNetsPanel.class.getDeclaredMethod("trimmedFusionLine", GraphPlaceFusion.class);
            method.setAccessible(true);
            return (Line2D) method.invoke(panel, fusion);
        } catch (InvocationTargetException failure) {
            throw new AssertionError(failure.getCause());
        } catch (ReflectiveOperationException broken) {
            throw new AssertionError(broken);
        }
    }

    @Test
    public void theLineStopsAtEachPlacesBorderNotItsCentre() {
        PetriNetsPanel panel = freshPanel();

        GraphPetriPlace framedPlace = new GraphPetriPlace(new PetriP("Pin", 1), idCounter++);
        framedPlace.setNewCoordinates(new Point2D.Double(120, 140));
        panel.getGraphNet().getGraphPetriPlaceList().add(framedPlace);
        GraphObjectFrame frame = new GraphObjectFrame("Other", new Rectangle(40, 40, 160, 160));
        panel.getCanvasModel().claim(frame, framedPlace);
        panel.addObjectFrame(frame);

        GraphPetriPlace freePlace = new GraphPetriPlace(new PetriP("Free", 1), idCounter++);
        freePlace.setNewCoordinates(new Point2D.Double(500, 140));
        panel.getGraphNet().getGraphPetriPlaceList().add(freePlace);

        GraphPlaceFusion fusion = panel.getCanvasModel().joinPlaces(framedPlace, freePlace);

        Line2D line = trimmedFusionLine(panel, fusion);

        double framedRadius = framedPlace.getBorder();
        double freeRadius = freePlace.getBorder();
        Point2D framedCentre = framedPlace.getGraphElementCenter();
        Point2D freeCentre = freePlace.getGraphElementCenter();

        assertTrue("the end near the framed place must stop at its border, not reach its centre",
                Math.min(line.getP1().distance(framedCentre), line.getP2().distance(framedCentre))
                        >= framedRadius - 1);
        assertTrue("the end near the free place must stop at its border, not reach its centre",
                Math.min(line.getP1().distance(freeCentre), line.getP2().distance(freeCentre))
                        >= freeRadius - 1);
    }

    @Test
    public void theLineIsTrimmedThroughAPortWhenTheObjectIsCollapsed() {
        PetriNetsPanel panel = freshPanel();

        GraphPetriPlace framedPlace = new GraphPetriPlace(new PetriP("Pin", 1), idCounter++);
        framedPlace.setNewCoordinates(new Point2D.Double(120, 140));
        panel.getGraphNet().getGraphPetriPlaceList().add(framedPlace);
        GraphObjectFrame frame = new GraphObjectFrame("Hidden", new Rectangle(40, 40, 160, 160));
        panel.getCanvasModel().claim(frame, framedPlace);
        panel.addObjectFrame(frame);
        frame.setCollapsed(true);

        GraphPetriPlace freePlace = new GraphPetriPlace(new PetriP("Free", 1), idCounter++);
        freePlace.setNewCoordinates(new Point2D.Double(500, 140));
        panel.getGraphNet().getGraphPetriPlaceList().add(freePlace);

        GraphPlaceFusion fusion = panel.getCanvasModel().joinPlaces(framedPlace, freePlace);

        Line2D line = trimmedFusionLine(panel, fusion);

        // The framed place itself is nowhere on screen now, so the line must reach for the
        // port instead - nowhere near the place's own (invisible) centre.
        Point2D framedCentre = framedPlace.getGraphElementCenter();
        assertTrue("neither end may land on the hidden place's own centre",
                Math.min(line.getP1().distance(framedCentre), line.getP2().distance(framedCentre)) > 1);
    }

    /**
     * A link between two places that belong to no object is drawn, and clickable, as a line.
     *
     * <p>Such a link could not be made at all until one-to-many arrived, and the drawing code
     * had a dormant second form waiting for it: two free places were to be stacked on one point
     * and ringed. That form is retired, because a source repeated by several places would have
     * piled every copy onto the same spot. This pins the replacement - a line between them,
     * both places left where they were.
     */
    @Test
    public void aLinkBetweenTwoFreePlacesIsALineAndBothPlacesStayPut() {
        PetriNetsPanel panel = freshPanel();
        GraphPetriPlace source = new GraphPetriPlace(new PetriP("P0", 1), idCounter++);
        source.setNewCoordinates(new Point2D.Double(120, 300));
        panel.getGraphNet().getGraphPetriPlaceList().add(source);
        GraphPetriPlace copy = new GraphPetriPlace(new PetriP("P1", 0), idCounter++);
        copy.setNewCoordinates(new Point2D.Double(520, 300));
        panel.getGraphNet().getGraphPetriPlaceList().add(copy);

        GraphPlaceFusion link = panel.getCanvasModel().joinPlaces(source, copy);
        panel.getCanvasModel().syncFusions();

        assertEquals("the source stayed where it was drawn",
                120.0, source.getGraphElementCenter().getX(), 0.001);
        assertEquals("and so did the copy - nothing is stacked",
                520.0, copy.getGraphElementCenter().getX(), 0.001);

        Line2D line = trimmedFusionLine(panel, link);
        assertNotNull("a free-to-free link is drawn as a line", line);
        assertTrue("which runs between the two places",
                line.getX1() > 120 && line.getX2() < 520);

        assertSame("and a click on that line picks the link out",
                link, panel.findSharedPlace(new Point2D.Double(
                        (line.getX1() + line.getX2()) / 2, 300)));
    }
}

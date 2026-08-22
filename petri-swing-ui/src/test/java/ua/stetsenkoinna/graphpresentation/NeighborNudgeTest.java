package ua.stetsenkoinna.graphpresentation;

import org.junit.Test;
import ua.stetsenkoinna.graphnet.GraphElement;
import ua.stetsenkoinna.graphnet.GraphObjectFrame;
import ua.stetsenkoinna.graphnet.GraphPetriPlace;
import ua.stetsenkoinna.petriobj.PetriP;
import ua.stetsenkoinna.petriobj.PetriT;

import javax.swing.event.UndoableEditListener;
import javax.swing.undo.UndoableEdit;
import java.awt.Rectangle;
import java.awt.geom.Point2D;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

/**
 * The margin a just-created Petri-object frame keeps from whatever it did not claim.
 *
 * <p>{@code boundsAround} already keeps a frame's own members clear of its border from the
 * inside, and {@code paddedForNesting} already keeps a nested frame clear of its parent's
 * border. Neither says anything about a place or transition that is not a member of the new
 * frame and happens to sit right next to it - that gap is what these tests pin: such an
 * element is pushed straight out to the same margin, as part of the frame's own creation, so
 * one {@code Ctrl+Z} takes both back together.
 */
public class NeighborNudgeTest {

    private static int idCounter = 9000;

    private static PetriNetsPanel freshPanel() {
        PetriP.initNext();
        PetriT.initNext();
        return new PetriNetsPanel(null, true);
    }

    private static GraphPetriPlace placeAt(PetriNetsPanel panel, String name, double x, double y) {
        GraphPetriPlace place = new GraphPetriPlace(new PetriP(name, 0), idCounter++);
        place.setNewCoordinates(new Point2D.Double(x, y));
        panel.getGraphNet().getGraphPetriPlaceList().add(place);
        return place;
    }

    /** The clearance {@code groupIntoObject} fits around a frame's own members - read by
     * reflection so the test tracks the real constant instead of a copy of its value. */
    private static int boundsPadding() {
        try {
            Field field = PetriNetsPanel.class.getDeclaredField("BOUNDS_PADDING");
            field.setAccessible(true);
            return field.getInt(null);
        } catch (ReflectiveOperationException broken) {
            throw new AssertionError(broken);
        }
    }

    /** What {@code groupIntoObject} is about to fit around {@code elements}, predicted ahead of
     * time so a neighbour can be placed exactly at the margin's edge before the frame exists. */
    private static Rectangle boundsAround(List<GraphElement> elements) {
        try {
            Method method = PetriNetsPanel.class.getDeclaredMethod("boundsAround", List.class);
            method.setAccessible(true);
            return (Rectangle) method.invoke(null, elements);
        } catch (InvocationTargetException failure) {
            throw new AssertionError(failure.getCause());
        } catch (ReflectiveOperationException broken) {
            throw new AssertionError(broken);
        }
    }

    private static List<UndoableEdit> editsPostedBy(Runnable action) {
        List<UndoableEdit> posted = new ArrayList<>();
        UndoableEditListener listener = event -> posted.add(event.getEdit());
        PetriNetsFrame.getUndoSupport().addUndoableEditListener(listener);
        try {
            action.run();
        } finally {
            PetriNetsFrame.getUndoSupport().removeUndoableEditListener(listener);
        }
        return posted;
    }

    /**
     * Places a neighbour whose own drawn extent sits {@code insideMargin} pixels closer to
     * {@code frameBounds} than the margin allows, on the frame's right side.
     */
    private static GraphPetriPlace neighbourInsideMargin(PetriNetsPanel panel, Rectangle frameBounds,
            int margin, int insideMargin) {
        int border = 20;
        double x = frameBounds.getMaxX() + (margin - insideMargin) + border;
        double y = frameBounds.y + frameBounds.height / 2.0;
        return placeAt(panel, "PNeighbour", x, y);
    }

    @Test
    public void anElementJustOutsideTheNewFrameIsPushedOutToTheMargin() {
        PetriNetsPanel panel = freshPanel();
        GraphPetriPlace grouped = placeAt(panel, "PGrouped", 300, 300);

        Rectangle predicted = boundsAround(List.of(grouped));
        int margin = boundsPadding();
        GraphPetriPlace neighbour = neighbourInsideMargin(panel, predicted, margin, 10);

        GraphObjectFrame frame = panel.groupIntoObject(List.of(grouped), "Object");

        assertEquals("fixture: the frame landed exactly where boundsAround said it would",
                predicted, frame.getBounds());
        double clearance = (neighbour.getGraphElementCenter().getX() - 20) - frame.getBounds().getMaxX();
        assertEquals("the neighbour was pushed out to exactly the margin",
                margin, clearance, 0.001);
    }

    @Test
    public void anElementFarFromTheNewFrameIsNotMoved() {
        PetriNetsPanel panel = freshPanel();
        GraphPetriPlace grouped = placeAt(panel, "PGrouped", 300, 300);
        GraphPetriPlace faraway = placeAt(panel, "PFaraway", 900, 900);

        panel.groupIntoObject(List.of(grouped), "Object");

        assertEquals("nothing pushes an element nowhere near the new frame",
                900.0, faraway.getGraphElementCenter().getX(), 0.001);
        assertEquals(900.0, faraway.getGraphElementCenter().getY(), 0.001);
    }

    @Test
    public void anElementAnotherObjectAlreadyClaimsIsNotMoved() {
        PetriNetsPanel panel = freshPanel();
        GraphPetriPlace grouped = placeAt(panel, "PGrouped", 300, 300);
        // Deep inside where the new frame is about to land, so it would certainly be pushed
        // if it were free - the only thing standing in the way is that another object already
        // claims it.
        GraphPetriPlace claimed = placeAt(panel, "PClaimed", 340, 300);
        GraphObjectFrame other = new GraphObjectFrame("Other", new Rectangle(320, 260, 200, 160));
        panel.addObjectFrame(other);
        panel.getCanvasModel().claim(other, claimed);

        panel.groupIntoObject(List.of(grouped), "Object");

        assertEquals("what another Petri-object already claims is that object's business",
                340.0, claimed.getGraphElementCenter().getX(), 0.001);
        assertEquals(300.0, claimed.getGraphElementCenter().getY(), 0.001);
    }

    @Test
    public void theNudgeIsUndoneTogetherWithTheCreationByOneUndo() {
        PetriNetsPanel panel = freshPanel();
        GraphPetriPlace grouped = placeAt(panel, "PGrouped", 300, 300);

        Rectangle predicted = boundsAround(List.of(grouped));
        int margin = boundsPadding();
        GraphPetriPlace neighbour = neighbourInsideMargin(panel, predicted, margin, 10);
        double startX = neighbour.getGraphElementCenter().getX();
        double startY = neighbour.getGraphElementCenter().getY();

        List<UndoableEdit> posted = editsPostedBy(
                () -> panel.groupIntoObject(List.of(grouped), "Object"));

        assertEquals("grouping, push included, is one undo step", 1, posted.size());
        double pushedX = neighbour.getGraphElementCenter().getX();
        assertNotEquals("fixture: the neighbour really was pushed", startX, pushedX, 0.001);

        posted.getFirst().undo();

        assertEquals("undo puts the neighbour back where it was",
                startX, neighbour.getGraphElementCenter().getX(), 0.001);
        assertEquals(startY, neighbour.getGraphElementCenter().getY(), 0.001);
        assertEquals("and takes the frame off again", 0, panel.getCanvasModel().getFrames().size());

        posted.getFirst().redo();

        assertEquals("redo pushes it out again the same way",
                pushedX, neighbour.getGraphElementCenter().getX(), 0.001);
        assertEquals(1, panel.getCanvasModel().getFrames().size());
    }
}

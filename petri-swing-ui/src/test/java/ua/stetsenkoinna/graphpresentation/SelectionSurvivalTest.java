package ua.stetsenkoinna.graphpresentation;

import org.junit.Test;
import ua.stetsenkoinna.graphnet.GraphObjectFrame;
import ua.stetsenkoinna.graphnet.GraphPetriPlace;
import ua.stetsenkoinna.graphnet.GraphPetriTransition;
import ua.stetsenkoinna.graphnet.GraphPlaceFusion;
import ua.stetsenkoinna.petriobj.PetriP;
import ua.stetsenkoinna.petriobj.PetriT;

import java.awt.Color;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionListener;
import java.awt.geom.Point2D;
import java.lang.reflect.Field;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/**
 * The two ways the canvas used to lose a selection it was supposed to keep.
 *
 * <p>Pressing one member of a selection to drag it looked like it collapsed the selection to
 * that one thing: the press reset every element's colour wholesale and then painted only the
 * pressed one, so everything else went back to its default colour. The model kept the selection
 * and the drag moved all of it, which is why this read as a drawing defect rather than a
 * selection defect; the colours only came back from the second drag event onwards, once
 * {@code mouseDragged} reached its own {@code paintHighlight}.
 *
 * <p>Switching to the Select tool cleared the selection outright, because the tool switch
 * abandoned the gesture in flight and the selection along with it. Every tool still abandons
 * the gesture; only Select keeps what is selected, since it is the one tool whose gestures act
 * on a selection rather than on whatever is under the pointer.
 */
public class SelectionSurvivalTest {

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
        return new MouseEvent(panel, id, System.currentTimeMillis(), 0, x, y, 1, false,
                MouseEvent.BUTTON1);
    }

    private static void press(PetriNetsPanel panel, int x, int y) {
        mouseHandlerOf(panel).mousePressed(event(panel, MouseEvent.MOUSE_PRESSED, x, y));
    }

    private static void dragTo(PetriNetsPanel panel, int x, int y) {
        motionHandlerOf(panel).mouseDragged(event(panel, MouseEvent.MOUSE_DRAGGED, x, y));
    }

    private static void release(PetriNetsPanel panel, int x, int y) {
        mouseHandlerOf(panel).mouseReleased(event(panel, MouseEvent.MOUSE_RELEASED, x, y));
    }

    private static void drag(PetriNetsPanel panel, int fromX, int fromY, int toX, int toY) {
        press(panel, fromX, fromY);
        dragTo(panel, (fromX + toX) / 2, (fromY + toY) / 2);
        dragTo(panel, toX, toY);
        release(panel, toX, toY);
    }

    private static GraphPetriPlace placeAt(PetriNetsPanel panel, String name, int x, int y) {
        GraphPetriPlace place = new GraphPetriPlace(new PetriP(name, 0), idCounter++);
        place.setNewCoordinates(new Point2D.Double(x, y));
        panel.getGraphNet().getGraphPetriPlaceList().add(place);
        return place;
    }

    private static GraphPetriTransition transitionAt(PetriNetsPanel panel, String name,
            int x, int y) {
        GraphPetriTransition transition =
                new GraphPetriTransition(new PetriT(name, 1.0), idCounter++);
        transition.setNewCoordinates(new Point2D.Double(x, y));
        panel.getGraphNet().getGraphPetriTransitionList().add(transition);
        return transition;
    }

    private static GraphObjectFrame framedPlace(PetriNetsPanel panel, String frameName,
            Rectangle bounds, GraphPetriPlace member) {
        GraphObjectFrame frame = new GraphObjectFrame(frameName, bounds);
        panel.getCanvasModel().getFrames().add(frame);
        panel.getCanvasModel().claim(frame, member);
        return frame;
    }

    /**
     * The colour a selected element is drawn in, read from the selection itself rather than
     * restated here: what "still looks selected" has to mean for these assertions to be about
     * the mechanism instead of about a constant copied out of it.
     */
    private static Color selectionColour() {
        GraphPetriPlace probe = new GraphPetriPlace(new PetriP("Probe", 0), 9999);
        new CanvasSelection().add(probe);
        return probe.getColor();
    }

    private static Object fieldOf(PetriNetsPanel panel, String name) {
        try {
            Field field = PetriNetsPanel.class.getDeclaredField(name);
            field.setAccessible(true);
            return field.get(panel);
        } catch (ReflectiveOperationException broken) {
            throw new AssertionError(broken);
        }
    }

    private static void setField(PetriNetsPanel panel, String name, Object value) {
        try {
            Field field = PetriNetsPanel.class.getDeclaredField(name);
            field.setAccessible(true);
            field.set(panel, value);
        } catch (ReflectiveOperationException broken) {
            throw new AssertionError(broken);
        }
    }

    /** Two loose places, selected together by a rubber band drawn over empty canvas. */
    private static PetriNetsPanel twoSelectedPlaces() {
        PetriNetsPanel panel = freshPanel();
        placeAt(panel, "P1", 200, 200);
        placeAt(panel, "P2", 320, 200);
        drag(panel, 140, 140, 380, 260);
        assertEquals("the band must select both places to begin with",
                2, panel.getSelection().elements().size());
        return panel;
    }

    private static GraphPetriPlace place(PetriNetsPanel panel, int index) {
        return panel.getGraphNet().getGraphPetriPlaceList().get(index);
    }

    // ------------------------------------------------------------------ a switch into Select

    /**
     * The reported defect: select things, reach for the pointer to move them, and they were
     * gone. Select is the one tool whose gestures act on a selection, so it is the one tool a
     * switch into must not clear.
     */
    @Test
    public void switchingToTheSelectToolKeepsTheElementSelection() {
        PetriNetsPanel panel = freshPanel();
        GraphPetriPlace first = placeAt(panel, "P1", 200, 200);
        GraphPetriPlace second = placeAt(panel, "P2", 320, 200);
        panel.setTool(CanvasTool.MARQUEE);
        drag(panel, 140, 140, 380, 260);
        assertTrue("the band must select both places to begin with",
                panel.getSelection().contains(first) && panel.getSelection().contains(second));

        panel.setTool(CanvasTool.SELECT);

        assertTrue("the selection survives the switch into Select",
                panel.getSelection().contains(first) && panel.getSelection().contains(second));
    }

    /** The same for a selected Petri-object: a marquee catches frames as well as elements. */
    @Test
    public void switchingToTheSelectToolKeepsASelectedPetriObject() {
        PetriNetsPanel panel = freshPanel();
        GraphPetriPlace member = placeAt(panel, "PM", 500, 230);
        GraphObjectFrame frame =
                framedPlace(panel, "Obj", new Rectangle(420, 140, 160, 140), member);
        panel.setTool(CanvasTool.MARQUEE);
        drag(panel, 380, 100, 620, 300);
        assertTrue("the band must select the object to begin with",
                panel.getSelection().contains(frame));

        panel.setTool(CanvasTool.SELECT);

        assertTrue("the selected object survives the switch into Select",
                panel.getSelection().contains(frame));
    }

    /**
     * Keeping the selection is only half the promise: the switch resets element colours
     * wholesale, so a selection that stays has to still be drawn as one.
     */
    @Test
    public void switchingToTheSelectToolKeepsTheSelectionLookingSelected() {
        PetriNetsPanel panel = freshPanel();
        GraphPetriPlace first = placeAt(panel, "P1", 200, 200);
        GraphPetriPlace second = placeAt(panel, "P2", 320, 200);
        panel.setTool(CanvasTool.MARQUEE);
        drag(panel, 140, 140, 380, 260);

        panel.setTool(CanvasTool.SELECT);

        assertEquals("the selection must still be drawn as selected",
                selectionColour(), first.getColor());
        assertEquals("the selection must still be drawn as selected",
                selectionColour(), second.getColor());
    }

    /**
     * What the switch must still throw away: a half-finished gesture means nothing under
     * another tool, whether or not the selection outlives it. These cannot all be dirtied by a
     * single real gesture (the marquee's running corner is only set while nothing is selected,
     * the selection-drag flag only while something is), so they are dirtied directly here and
     * the realistic mid-gesture case is pinned by the test below.
     */
    @Test
    public void switchingToTheSelectToolStillResetsTheDragBookkeeping() {
        PetriNetsPanel panel = freshPanel();
        GraphPetriPlace framed = placeAt(panel, "PIn", 200, 200);
        GraphPetriPlace free = placeAt(panel, "PFree", 700, 200);
        framedPlace(panel, "Obj", new Rectangle(140, 140, 160, 140), framed);
        GraphPlaceFusion fusion = panel.getCanvasModel().joinPlaces(framed, free);
        panel.setTool(CanvasTool.MARQUEE);
        panel.getSelection().add(free);

        setField(panel, "startDragMouseLocation", new Point(10, 10));
        setField(panel, "currentDragMouseLocation", new Point(90, 90));
        setField(panel, "leftMouseButtonPressed", true);
        setField(panel, "selectionDragged", true);
        setField(panel, "dragCompleted", true);
        setField(panel, "choosenFusion", fusion);
        panel.setCurrent(free);
        panel.setChoosen(free);

        panel.setTool(CanvasTool.SELECT);

        assertNull("the marquee anchor dies with its gesture",
                fieldOf(panel, "startDragMouseLocation"));
        assertNull("so does its running corner", fieldOf(panel, "currentDragMouseLocation"));
        assertEquals("no button is held after a tool switch",
                Boolean.FALSE, fieldOf(panel, "leftMouseButtonPressed"));
        assertEquals("no selection drag is in flight after a tool switch",
                Boolean.FALSE, fieldOf(panel, "selectionDragged"));
        assertEquals("no drag has just completed after a tool switch",
                Boolean.FALSE, fieldOf(panel, "dragCompleted"));
        assertNull("the pressed element dies with its gesture", panel.getCurrent());
        assertNull("so does the clicked element", panel.getChoosen());
        assertNull("so does the clicked arc", panel.getChoosenArc());
        assertNull("so does the clicked shared place", fieldOf(panel, "choosenFusion"));
        assertTrue("and the selection is what survives all of it",
                panel.getSelection().contains(free));
    }

    /**
     * The same rule against a gesture that is genuinely mid-flight: pressed on a member of the
     * selection, dragged, and never released when the tool changed underneath it.
     */
    @Test
    public void aSwitchIntoSelectDropsAGestureThatWasStillInFlight() {
        PetriNetsPanel panel = twoSelectedPlaces();
        GraphPetriPlace first = place(panel, 0);

        press(panel, 200, 200);
        dragTo(panel, 230, 230);
        panel.setTool(CanvasTool.MARQUEE);
        panel.setTool(CanvasTool.SELECT);

        assertNull("the drag origin does not outlive the tool it was made under",
                fieldOf(panel, "startDragMouseLocation"));
        assertEquals("nor does the held button",
                Boolean.FALSE, fieldOf(panel, "leftMouseButtonPressed"));
        assertNull("nor the pressed element", panel.getCurrent());
        assertFalse("and the Marquee tool on the way through still cleared the selection",
                panel.getSelection().contains(first));
    }

    /**
     * A tool that acts on whatever is under the pointer has no use for a selection, and
     * dropping it on the way in is the behaviour those tools have today.
     */
    @Test
    public void switchingToAToolThatActsUnderThePointerStillClearsTheSelection() {
        for (CanvasTool tool : new CanvasTool[] {
                CanvasTool.MARQUEE, CanvasTool.PAN, CanvasTool.DELETE, CanvasTool.ADD_PLACE,
                CanvasTool.ADD_TRANSITION, CanvasTool.ADD_PETRI_OBJECT}) {
            PetriNetsPanel panel = twoSelectedPlaces();

            panel.setTool(tool);

            assertTrue("switching to " + tool + " must still drop the selection",
                    panel.getSelection().isEmpty());
        }
    }

    /**
     * The Arc tool is the Select tool plus an armed flag, so leaving it is a same-enum switch.
     * It still disarms, and the selection is no longer the price of getting out of it.
     */
    @Test
    public void leavingTheArcToolForSelectDisarmsItAndKeepsTheSelection() {
        PetriNetsPanel panel = twoSelectedPlaces();
        GraphPetriPlace first = place(panel, 0);
        panel.setIsSettingArc(true);

        panel.setTool(CanvasTool.SELECT);

        assertFalse("the Arc tool is left by the switch", panel.isArcToolArmed());
        assertTrue("and the selection is not the price of leaving it",
                panel.getSelection().contains(first));
    }

    /**
     * An arc drawn to its first endpoint only is still abandoned by a tool switch: it is a
     * gesture in flight, not a selection.
     */
    @Test
    public void aToolSwitchStillDiscardsAnArcDrawnToItsFirstEndpointOnly() {
        PetriNetsPanel panel = freshPanel();
        placeAt(panel, "P1", 200, 200);
        transitionAt(panel, "T1", 400, 200);
        panel.setIsSettingArc(true);
        press(panel, 200, 200);
        assertEquals("the press must start an arc for this test to mean anything",
                1, panel.getGraphNet().getGraphArcInList().size());

        panel.setTool(CanvasTool.PAN);

        assertEquals("the half-drawn arc goes with the tool that was drawing it",
                0, panel.getGraphNet().getGraphArcInList().size());
        assertNull("and nothing is left holding it", panel.getCurrentGraphArc());
    }

    // ------------------------------------------------------------------ a press on a member

    /**
     * The reported defect: the press that starts the drag made the selection look like it had
     * collapsed to the one thing under the pointer.
     */
    @Test
    public void pressingASelectedElementKeepsTheWholeSelectionLookingSelected() {
        PetriNetsPanel panel = twoSelectedPlaces();
        GraphPetriPlace pressed = place(panel, 0);
        GraphPetriPlace other = place(panel, 1);

        press(panel, 200, 200);

        assertTrue("the selection itself is kept, as it always was",
                panel.getSelection().contains(pressed) && panel.getSelection().contains(other));
        assertEquals("the pressed element still reads as selected",
                selectionColour(), pressed.getColor());
        assertEquals("and so does the rest of the selection",
                selectionColour(), other.getColor());
    }

    /**
     * From the press through the drag to the release, not only once the drag is under way: the
     * highlight used to come back on the second drag event, which left the press itself, and a
     * press held still, visibly wrong.
     */
    @Test
    public void theHighlightSurvivesEveryStageOfADragFromInsideTheSelection() {
        PetriNetsPanel panel = twoSelectedPlaces();
        GraphPetriPlace pressed = place(panel, 0);
        GraphPetriPlace other = place(panel, 1);

        press(panel, 200, 200);
        assertEquals("at the press", selectionColour(), other.getColor());
        dragTo(panel, 210, 210);
        assertEquals("on the first drag event", selectionColour(), other.getColor());
        assertEquals("on the first drag event", selectionColour(), pressed.getColor());
        dragTo(panel, 260, 260);
        assertEquals("on the next one", selectionColour(), other.getColor());
        release(panel, 260, 260);

        assertEquals("and after the release", selectionColour(), pressed.getColor());
        assertEquals("and after the release", selectionColour(), other.getColor());
        assertTrue("with the whole selection still selected",
                panel.getSelection().contains(pressed) && panel.getSelection().contains(other));
        assertEquals("and all of it moved by the drag delta",
                260, (int) pressed.getGraphElementCenter().getX());
        assertEquals("and all of it moved by the drag delta",
                380, (int) other.getGraphElementCenter().getX());
    }

    /**
     * Frames never lost their highlight the way elements did, since a frame is drawn as
     * selected from the selection itself rather than from a colour stored on it. A mixed
     * selection is what pins that: pressing an element must not cost the object its highlight,
     * and pressing the object must not cost the elements theirs.
     */
    @Test
    public void aMixedSelectionSurvivesAPressOnEitherKind() {
        PetriNetsPanel panel = freshPanel();
        GraphPetriPlace loose = placeAt(panel, "P1", 200, 200);
        GraphPetriPlace member = placeAt(panel, "PM", 500, 230);
        GraphObjectFrame frame =
                framedPlace(panel, "Obj", new Rectangle(420, 140, 160, 140), member);
        drag(panel, 140, 120, 620, 300);
        assertTrue("the band must select both kinds to begin with",
                panel.getSelection().contains(loose) && panel.getSelection().contains(frame));

        press(panel, 200, 200);
        assertTrue("pressing the element keeps the object selected",
                panel.getSelection().contains(frame));
        assertEquals("and the element still reads as selected",
                selectionColour(), loose.getColor());
        release(panel, 200, 200);

        drag(panel, 140, 120, 620, 300);
        press(panel, 450, 250);
        assertTrue("pressing the object keeps the element selected",
                panel.getSelection().contains(loose));
        assertEquals("and the element still reads as selected",
                selectionColour(), loose.getColor());
        assertSame("with the pressed object the one the frame menus act on",
                frame, panel.getSelection().getSelectedFrame());
    }

    // ------------------------------------------------------------------ what must not change

    /** A press on something outside the selection replaces it, highlight and all. */
    @Test
    public void pressingAnUnselectedElementStillReplacesTheSelection() {
        PetriNetsPanel panel = twoSelectedPlaces();
        GraphPetriPlace wasSelected = place(panel, 0);
        GraphPetriPlace outside = placeAt(panel, "P3", 700, 500);

        press(panel, 700, 500);

        assertFalse("the old selection is replaced", panel.getSelection().contains(wasSelected));
        assertNotEquals("and stops looking selected",
                selectionColour(), wasSelected.getColor());
        assertSame("the pressed element is what the gesture now holds",
                outside, panel.getChoosen());
    }

    /** A press on empty canvas still deselects, including a selection kept across a switch. */
    @Test
    public void aPressOnEmptyCanvasStillDeselects() {
        PetriNetsPanel panel = twoSelectedPlaces();
        panel.setTool(CanvasTool.MARQUEE);
        drag(panel, 140, 140, 380, 260);
        panel.setTool(CanvasTool.SELECT);
        assertFalse("the selection has to survive the switch for this test to mean anything",
                panel.getSelection().isEmpty());

        press(panel, 800, 600);

        assertTrue("a press on nothing clears the selection", panel.getSelection().isEmpty());
    }

    /** A marquee started on empty canvas still replaces whatever was selected before it. */
    @Test
    public void aMarqueeStillReplacesTheSelectionItStartsWith() {
        PetriNetsPanel panel = twoSelectedPlaces();
        GraphPetriPlace wasSelected = place(panel, 0);
        GraphPetriPlace elsewhere = placeAt(panel, "P3", 700, 500);

        drag(panel, 640, 440, 780, 560);

        assertFalse("the old selection is gone", panel.getSelection().contains(wasSelected));
        assertTrue("the band's own catch is what is selected",
                panel.getSelection().contains(elsewhere));
        assertEquals("and nothing else came along with it",
                1, panel.getSelection().elements().size());
    }

    /**
     * Drawing an arc from an element that is part of a selection: the arc is drawn and nothing
     * is dragged, which is what the arc gesture's own guard against the selection move is for.
     */
    @Test
    public void anArcStillDrawsFromASelectedElement() {
        PetriNetsPanel panel = freshPanel();
        GraphPetriPlace place = placeAt(panel, "P1", 200, 200);
        GraphPetriTransition transition = transitionAt(panel, "T1", 320, 200);
        drag(panel, 140, 140, 380, 260);
        assertEquals("both ends must be selected for this test to mean anything",
                2, panel.getSelection().elements().size());
        panel.setIsSettingArc(true);

        drag(panel, 200, 200, 320, 200);

        assertEquals("the arc is drawn", 1, panel.getGraphNet().getGraphArcInList().size());
        assertEquals("and the selected place did not move with the gesture",
                200, (int) place.getGraphElementCenter().getX());
        assertEquals("and neither did the selected transition",
                320, (int) transition.getGraphElementCenter().getX());
    }
}

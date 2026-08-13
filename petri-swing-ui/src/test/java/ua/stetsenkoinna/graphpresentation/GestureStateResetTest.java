package ua.stetsenkoinna.graphpresentation;

import org.junit.Test;
import ua.stetsenkoinna.graphnet.GraphObjectFrame;
import ua.stetsenkoinna.graphnet.GraphPetriPlace;
import ua.stetsenkoinna.graphnet.GraphPetriTransition;
import ua.stetsenkoinna.petriobj.PetriP;
import ua.stetsenkoinna.petriobj.PetriT;

import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionListener;
import java.awt.event.MouseWheelEvent;
import java.awt.geom.Point2D;
import java.lang.reflect.Field;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/**
 * Pins that per-gesture state actually dies with its gesture. Every defect here had the same
 * shape: something armed by one press (a drag origin, a hold timer, the selection, the arc
 * tool's own armed flag) survived into the next gesture, which then acted on the leftovers -
 * a frame teleported across the canvas by a stale delta, a marquee dragged the previous
 * selection along, a plain click snapped an element to the pointer, or the Arc tool silently
 * became the move tool after one missed arc.
 */
public class GestureStateResetTest {

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

    private static void drag(PetriNetsPanel panel, int fromX, int fromY, int toX, int toY) {
        PetriNetsPanel.MouseHandler handler = mouseHandlerOf(panel);
        MouseMotionListener motion = motionHandlerOf(panel);
        handler.mousePressed(event(panel, MouseEvent.MOUSE_PRESSED, fromX, fromY));
        motion.mouseDragged(event(panel, MouseEvent.MOUSE_DRAGGED,
                (fromX + toX) / 2, (fromY + toY) / 2));
        motion.mouseDragged(event(panel, MouseEvent.MOUSE_DRAGGED, toX, toY));
        handler.mouseReleased(event(panel, MouseEvent.MOUSE_RELEASED, toX, toY));
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

    private static GraphObjectFrame framedPlace(PetriNetsPanel panel, String frameName,
            Rectangle bounds, GraphPetriPlace member) {
        GraphObjectFrame frame = new GraphObjectFrame(frameName, bounds);
        panel.getCanvasModel().getFrames().add(frame);
        panel.getCanvasModel().claim(frame, member);
        return frame;
    }

    private static void spinWheel(PetriNetsPanel panel, int rotation) {
        for (java.awt.event.MouseWheelListener listener : panel.getMouseWheelListeners()) {
            listener.mouseWheelMoved(new MouseWheelEvent(panel, MouseEvent.MOUSE_WHEEL,
                    System.currentTimeMillis(), 0, 400, 300, 0, false,
                    MouseWheelEvent.WHEEL_UNIT_SCROLL, 1, rotation));
        }
    }

    // ------------------------------------------------------------------ zoom

    /**
     * A fast spin delivers several notches in one wheel event; guarding only the single
     * -1 step let the scale shoot straight through the floor and go negative, at which
     * point the whole drawing silently disappeared.
     */
    @Test
    public void aFastWheelSpinNeverDrivesTheScaleToZeroOrBelow() {
        PetriNetsPanel panel = freshPanel();
        for (int i = 0; i < 10; i++) {
            spinWheel(panel, -3);
        }
        assertTrue("scale must stay positive after any spin, was " + panel.getScale(),
                panel.getScale() >= 0.1);
    }

    @Test
    public void aFastWheelSpinNeverDrivesTheScaleAboveTheCeiling() {
        PetriNetsPanel panel = freshPanel();
        for (int i = 0; i < 10; i++) {
            spinWheel(panel, 30);
        }
        assertTrue("scale must stay bounded after any spin, was " + panel.getScale(),
                panel.getScale() <= 5.0);
    }

    // ------------------------------------------------------------------ stale selection

    /**
     * The selection used to survive an empty-canvas press, and the drag that was meant to
     * rubber-band a new selection then moved the old one across the canvas instead.
     */
    @Test
    public void aMarqueeOverEmptyCanvasNeverMovesThePreviousSelection() {
        PetriNetsPanel panel = freshPanel();
        GraphPetriPlace place = placeAt(panel, "P1", 200, 150);
        panel.getSelection().add(place);

        drag(panel, 600, 400, 700, 500);

        Point2D centre = place.getGraphElementCenter();
        assertEquals("the place must not move during a marquee elsewhere", 200, (int) centre.getX());
        assertEquals("the place must not move during a marquee elsewhere", 150, (int) centre.getY());
        assertFalse("the marquee replaced the old selection", panel.getSelection().contains(place));
    }

    /** The frame-shaped twin of the marquee defect: a selected frame rode along with the band. */
    @Test
    public void aMarqueeOverEmptyCanvasNeverMovesTheSelectedFrame() {
        PetriNetsPanel panel = freshPanel();
        GraphPetriPlace place = placeAt(panel, "P1", 200, 150);
        GraphObjectFrame frame = framedPlace(panel, "Obj", new Rectangle(140, 90, 160, 140), place);
        panel.getSelection().setSelectedFrame(frame);

        drag(panel, 600, 400, 700, 500);

        assertEquals("the frame must not move during a marquee elsewhere",
                new Rectangle(140, 90, 160, 140), frame.getBounds());
    }

    // ------------------------------------------------------------------ stale drag origin

    /**
     * The drag origin survived from gesture to gesture, so the first drag event after a
     * body-press moved the frame by the vector from wherever the previous gesture ended -
     * a one-pixel drag teleported the object across the canvas.
     */
    @Test
    public void aBodyPressStartsItsDragFromItsOwnPress() {
        PetriNetsPanel panel = freshPanel();
        GraphPetriPlace free = placeAt(panel, "Free", 900, 150);
        GraphPetriPlace member = placeAt(panel, "P1", 200, 150);
        GraphObjectFrame frame = framedPlace(panel, "Obj", new Rectangle(140, 90, 160, 140), member);

        // First gesture: drag the free place far away, leaving a distant drag origin behind.
        drag(panel, 900, 150, 900, 400);

        // Second gesture: grab the frame's body and drag a single pixel.
        drag(panel, 200, 200, 201, 201);

        assertEquals("a one-pixel body drag moves the frame exactly one pixel",
                new Rectangle(141, 91, 160, 140), frame.getBounds());
    }

    // ------------------------------------------------------------------ selection replacement

    @Test
    public void pressingAnUnselectedElementReplacesTheSelection() {
        PetriNetsPanel panel = freshPanel();
        GraphPetriPlace first = placeAt(panel, "P1", 200, 150);
        GraphPetriPlace second = placeAt(panel, "P2", 500, 150);
        panel.getSelection().add(first);

        PetriNetsPanel.MouseHandler handler = mouseHandlerOf(panel);
        handler.mousePressed(event(panel, MouseEvent.MOUSE_PRESSED, 500, 150));
        handler.mouseReleased(event(panel, MouseEvent.MOUSE_RELEASED, 500, 150));

        assertFalse("the old selection is gone", panel.getSelection().contains(first));
    }

    /**
     * Grabbing one element of a multi-selection used to move it twice per drag event - once
     * snapped to the pointer, once again with the selection's own delta - so it ran ahead
     * of everything else being dragged.
     */
    @Test
    public void anElementGrabbedInsideASelectionMovesWithIt() {
        PetriNetsPanel panel = freshPanel();
        GraphPetriPlace grabbed = placeAt(panel, "P1", 200, 150);
        GraphPetriPlace other = placeAt(panel, "P2", 300, 150);
        panel.getSelection().add(grabbed);
        panel.getSelection().add(other);

        drag(panel, 200, 150, 260, 210);

        Point2D grabbedCentre = grabbed.getGraphElementCenter();
        Point2D otherCentre = other.getGraphElementCenter();
        assertEquals("the grabbed element moves by the drag delta", 260, (int) grabbedCentre.getX());
        assertEquals("the grabbed element moves by the drag delta", 210, (int) grabbedCentre.getY());
        assertEquals("the rest of the selection moves by the same delta", 360, (int) otherCentre.getX());
        assertEquals("the rest of the selection moves by the same delta", 210, (int) otherCentre.getY());
    }

    // ------------------------------------------------------------------ arc tool

    /**
     * One missed arc used to silently disarm the tool, so the user's next attempt moved the
     * element instead of drawing an arc - with no visual feedback that anything changed.
     */
    @Test
    public void theArcToolStaysArmedAfterAMissedArc() {
        PetriNetsPanel panel = freshPanel();
        placeAt(panel, "P1", 200, 150);
        transitionAt(panel, "T1", 400, 150);
        panel.setIsSettingArc(true);

        // Miss: release over empty canvas.
        drag(panel, 200, 150, 300, 400);
        assertEquals("the miss must not leave a half-built arc behind",
                0, panel.getGraphNet().getGraphArcInList().size());

        // The very next gesture draws the arc - the tool must still be armed.
        drag(panel, 200, 150, 400, 150);
        assertEquals("the next gesture still draws an arc",
                1, panel.getGraphNet().getGraphArcInList().size());
    }

    @Test
    public void theArcToolStaysArmedAfterAPressOnEmptyCanvas() {
        PetriNetsPanel panel = freshPanel();
        placeAt(panel, "P1", 200, 150);
        transitionAt(panel, "T1", 400, 150);
        panel.setIsSettingArc(true);

        // A press-release on nothing at all.
        drag(panel, 600, 400, 610, 410);

        drag(panel, 200, 150, 400, 150);
        assertEquals("the tool survived the empty click and drew the arc",
                1, panel.getGraphNet().getGraphArcInList().size());
    }

    // ------------------------------------------------------------------ right-click selection survival

    /**
     * Runs a Windows-style popup right-click (plain press, popup-trigger release) and
     * reports which of the Petri-object menus the release decided on. The panel is not in
     * a window, so {@code JPopupMenu.show} always throws before anything is painted, and
     * the frame it throws from names the decision.
     */
    private static String menuOpenedByRightClick(PetriNetsPanel panel, int x, int y) {
        PetriNetsPanel.MouseHandler handler = mouseHandlerOf(panel);
        handler.mousePressed(new MouseEvent(panel, MouseEvent.MOUSE_PRESSED,
                System.currentTimeMillis(), 0, x, y, 1, false, MouseEvent.BUTTON3));
        try {
            handler.mouseReleased(new MouseEvent(panel, MouseEvent.MOUSE_RELEASED,
                    System.currentTimeMillis(), 0, x, y, 1, true, MouseEvent.BUTTON3));
        } catch (RuntimeException expected) {
            for (StackTraceElement frame : expected.getStackTrace()) {
                if (frame.getMethodName().startsWith("show") && frame.getMethodName().endsWith("Menu")) {
                    return frame.getMethodName();
                }
            }
        }
        return "none";
    }

    /**
     * The select-elements, right-click, "group selection into a Petri-object" flow: the
     * right press must leave the selection alone for the release's menu to offer it. With
     * the Marquee tool armed, the press used to clear it, so the menu opened as if nothing
     * was selected.
     */
    @Test
    public void aRightClickWithTheMarqueeToolKeepsTheSelectionForTheMenu() {
        PetriNetsPanel panel = freshPanel();
        GraphPetriPlace first = placeAt(panel, "P1", 200, 200);
        GraphPetriPlace second = placeAt(panel, "P2", 320, 200);
        panel.setTool(CanvasTool.MARQUEE);
        drag(panel, 140, 140, 380, 260);
        assertTrue(panel.getSelection().contains(first) && panel.getSelection().contains(second));

        String menu = menuOpenedByRightClick(panel, 260, 200);

        assertTrue("the selection survives the right press",
                panel.getSelection().contains(first) && panel.getSelection().contains(second));
        assertEquals("and the menu offers to group it", "showGroupSelectionMenu", menu);
    }

    @Test
    public void aRightClickWithTheSelectToolKeepsTheSelectionForTheMenu() {
        PetriNetsPanel panel = freshPanel();
        GraphPetriPlace first = placeAt(panel, "P1", 200, 200);
        GraphPetriPlace second = placeAt(panel, "P2", 320, 200);
        drag(panel, 140, 140, 380, 260);
        assertTrue(panel.getSelection().contains(first) && panel.getSelection().contains(second));

        String menu = menuOpenedByRightClick(panel, 260, 200);

        assertTrue("the selection survives the right press",
                panel.getSelection().contains(first) && panel.getSelection().contains(second));
        assertEquals("and the menu offers to group it", "showGroupSelectionMenu", menu);
    }

    /**
     * Same rule when the right press lands on a frame's body: the left press replaces the
     * selection there, but the right press is on its way to the context menu, whose group
     * option combines the selected elements with the clicked object.
     */
    @Test
    public void aRightClickOnAFrameBodyKeepsTheElementSelection() {
        PetriNetsPanel panel = freshPanel();
        GraphPetriPlace loose = placeAt(panel, "Loose", 200, 200);
        GraphPetriPlace member = placeAt(panel, "PM", 600, 200);
        GraphObjectFrame frame = framedPlace(panel, "Obj", new Rectangle(540, 140, 160, 140), member);
        panel.getSelection().add(loose);

        PetriNetsPanel.MouseHandler handler = mouseHandlerOf(panel);
        handler.mousePressed(new MouseEvent(panel, MouseEvent.MOUSE_PRESSED,
                System.currentTimeMillis(), 0, 570, 250, 1, false, MouseEvent.BUTTON3));

        assertTrue("the element selection survives a right press on an object",
                panel.getSelection().contains(loose));
    }

    // ------------------------------------------------------------------ hold timer

    /**
     * The 500ms hold timer armed by a right-button press leaked when the release opened the
     * context menu, leaving isMouseButtonHold stuck true - the next plain left click then
     * snapped the clicked element's centre to the pointer before any drag began.
     */
    @Test
    public void theHoldTimerDiesWithTheGestureEvenWhenTheMenuShows() throws Exception {
        PetriNetsPanel panel = freshPanel();
        placeAt(panel, "P1", 200, 150);
        PetriNetsPanel.MouseHandler handler = mouseHandlerOf(panel);

        handler.mousePressed(new MouseEvent(panel, MouseEvent.MOUSE_PRESSED,
                System.currentTimeMillis(), 0, 600, 400, 1, false, MouseEvent.BUTTON3));
        Thread.sleep(700);

        try {
            handler.mouseReleased(new MouseEvent(panel, MouseEvent.MOUSE_RELEASED,
                    System.currentTimeMillis(), 0, 600, 400, 1, true, MouseEvent.BUTTON3));
        } catch (RuntimeException expectedOnDisplaylessPanel) {
            // JPopupMenu.show cannot show on a panel that is not in a window; the timer
            // must already be dead by the time the menu is asked to appear.
        }

        Field hold = PetriNetsPanel.MouseHandler.class.getDeclaredField("isMouseButtonHold");
        hold.setAccessible(true);
        assertFalse("the hold flag must not survive the gesture", (boolean) hold.get(handler));
    }

    // ------------------------------------------------------------------ body-drag re-nesting

    /**
     * Only a header drag used to get the re-nest check on release; a frame moved by its body
     * (or caught in a dragged selection) kept its old parent no matter where it landed, so
     * it could sit visibly outside the frame the model still said enclosed it.
     */
    @Test
    public void aBodyDragIntoAnotherObjectReNestsTheFrame() {
        PetriNetsPanel panel = freshPanel();
        GraphPetriPlace memberA = placeAt(panel, "PA", 200, 150);
        GraphObjectFrame frameA = framedPlace(panel, "A", new Rectangle(140, 90, 160, 140), memberA);
        GraphPetriPlace memberB = placeAt(panel, "PB", 700, 400);
        GraphObjectFrame frameB = framedPlace(panel, "B", new Rectangle(600, 300, 300, 260), memberB);

        // Grab A by its body (below its header, clear of PA) and drop its centre inside B.
        drag(panel, 200, 200, 700, 450);

        assertSame("the frame dropped inside another object is nested in it",
                frameB, panel.getCanvasModel().enclosingOf(frameA));
    }

    @Test
    public void aBodyDragClearOfTheParentOnItsCanvasLiftsTheFrameToTheTopLevel() {
        PetriNetsPanel panel = freshPanel();
        GraphPetriPlace memberB = placeAt(panel, "PB", 700, 400);
        GraphObjectFrame frameB = framedPlace(panel, "B", new Rectangle(600, 300, 300, 260), memberB);
        GraphPetriPlace memberA = placeAt(panel, "PA", 660, 350);
        GraphObjectFrame frameA = framedPlace(panel, "A", new Rectangle(620, 330, 100, 80), memberA);
        panel.getCanvasModel().nest(frameA, frameB);
        // A nested frame moves only from its parent's own canvas (FrameEditScopeTest).
        panel.openObjectCanvas(frameB);

        // Grab A by its body, clear of PA and of its header, and drop it far outside B.
        drag(panel, 630, 400, 200, 700);

        assertNull("the frame dropped clear of the parent's rectangle is no longer nested",
                panel.getCanvasModel().enclosingOf(frameA));
    }

    /**
     * The focused frame is invisible to every hit test, so a drop inside it used to read as
     * "left every frame": a 15px tidy-up nudge of a nested object on its parent's own canvas
     * silently tore the child out of the parent.
     */
    @Test
    public void aNudgeInsideTheParentOnItsCanvasKeepsTheNesting() {
        PetriNetsPanel panel = freshPanel();
        GraphPetriPlace memberB = placeAt(panel, "PB", 700, 400);
        GraphObjectFrame frameB = framedPlace(panel, "B", new Rectangle(600, 300, 300, 260), memberB);
        GraphPetriPlace memberA = placeAt(panel, "PA", 660, 350);
        GraphObjectFrame frameA = framedPlace(panel, "A", new Rectangle(620, 330, 100, 80), memberA);
        panel.getCanvasModel().nest(frameA, frameB);
        panel.openObjectCanvas(frameB);

        // A small header drag that keeps A well inside B's rectangle.
        drag(panel, 640, 335, 655, 350);

        assertSame("a tidy-up nudge inside the parent must not change the nesting",
                frameB, panel.getCanvasModel().enclosingOf(frameA));
    }
}

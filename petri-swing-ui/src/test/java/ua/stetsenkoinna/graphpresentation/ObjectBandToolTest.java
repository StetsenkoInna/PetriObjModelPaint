package ua.stetsenkoinna.graphpresentation;

import org.junit.Test;
import ua.stetsenkoinna.graphnet.GraphObjectFrame;
import ua.stetsenkoinna.graphnet.GraphPetriPlace;
import ua.stetsenkoinna.graphnet.GraphPetriTransition;
import ua.stetsenkoinna.petriobj.PetriP;
import ua.stetsenkoinna.petriobj.PetriT;

import java.awt.Cursor;
import java.awt.Rectangle;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionListener;
import java.awt.geom.Point2D;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertFalse;

/**
 * The Petri-object band tool: drag a rectangle, release, and what it captures becomes one new
 * Petri-object - the same gesture {@code resolveObjectBand} implements for the web editor (see
 * {@code frontend/src/lib/petri-object-model.ts}), reproduced here on top of the marquee's own
 * drag machinery.
 *
 * <p>Each of the six rules the gesture was specified against is pinned by calling
 * {@link PetriNetsPanel#resolveObjectBand} and {@link PetriNetsPanel#createObjectFromBand}
 * directly against a hand-built canvas and a drawn rectangle - both are dialog-free by
 * construction, the same split {@code groupIntoObject} already keeps from
 * {@code askAndGroupIntoObject}, since a modal {@code JOptionPane} cannot run in a test JVM (and,
 * with no owner window, showing one for real would block the test run waiting for a click that
 * never comes rather than failing outright). What real synthetic {@link MouseEvent}s through the
 * panel's own handlers CAN safely pin without ever reaching that dialog - the drag threshold, the
 * shared press handling, and that the tool leaves the gestures around it alone - is pinned that
 * way instead; the naming dialog itself is left untested for the same reason
 * {@code askAndGroupIntoObject} is.
 */
public class ObjectBandToolTest {

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

    /** A full marquee-style drag: press, two drag events, release - never used with the
     *  Petri-object band tool itself once the drag clears the threshold, since that would reach
     *  the naming dialog; safe for the Marquee tool, which never shows one. */
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

    /** A frame put straight on the canvas model, the way {@code SelectionSurvivalTest} builds
     *  its fixtures - no undo edit, no selection change, just a fact about the canvas. */
    private static GraphObjectFrame topLevelFrame(PetriNetsPanel panel, String name, Rectangle bounds) {
        GraphObjectFrame frame = new GraphObjectFrame(name, bounds);
        panel.getCanvasModel().getFrames().add(frame);
        return frame;
    }

    private static Rectangle marqueeRectangleOf(PetriNetsPanel panel) {
        try {
            Method method = PetriNetsPanel.class.getDeclaredMethod("marqueeRectangle");
            method.setAccessible(true);
            return (Rectangle) method.invoke(panel);
        } catch (ReflectiveOperationException broken) {
            throw new AssertionError(broken);
        }
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

    // ------------------------------------------------------------------ rule 1: swallow, not centre

    @Test
    public void aFrameJoinsOnlyWhenTheBandSwallowsItWhole() {
        PetriNetsPanel panel = freshPanel();
        GraphObjectFrame big = topLevelFrame(panel, "Big", new Rectangle(0, 0, 400, 300));
        // A small band sitting over the big frame's own centre - enough for the centre to fall
        // inside the band, nowhere near enough to swallow the frame it belongs to.
        Rectangle band = new Rectangle(150, 120, 60, 60);

        PetriNetsPanel.ObjectBandCapture capture = panel.resolveObjectBand(band);

        assertFalse("the frame's centre alone must not be read as a capture",
                capture.frames().contains(big));
        assertSame("with the frame excluded, it is still where the new object is built - its "
                + "own centre is inside the band",
                big, capture.parent());
    }

    // ------------------------------------------------------------------ rule 2: elements by centre

    @Test
    public void aPlaceAndATransitionJoinByTheirCentre() {
        PetriNetsPanel panel = freshPanel();
        GraphPetriPlace place = placeAt(panel, "P1", 100, 100);
        GraphPetriTransition transition = transitionAt(panel, "T1", 300, 100);
        Rectangle band = new Rectangle(50, 50, 300, 100);

        PetriNetsPanel.ObjectBandCapture capture = panel.resolveObjectBand(band);

        assertEquals(2, capture.elements().size());
        assertTrue(capture.elements().contains(place));
        assertTrue(capture.elements().contains(transition));

        GraphObjectFrame created = panel.createObjectFromBand(band, "Both");

        assertSame("the place really joined the new object",
                created, panel.getCanvasModel().ownerOf(place));
        assertSame("and so did the transition",
                created, panel.getCanvasModel().ownerOf(transition));
    }

    // ------------------------------------------------------------------ rule 3: swallow wraps

    @Test
    public void aBandThatSwallowsAFrameWrapsItRatherThanBecomingItsParent() {
        // The reported bug: a band drawn concentric with an existing object - its own centre
        // landing exactly on the frame's centre, the worst case for reading centre alone - used
        // to build the new object INSIDE the frame it was supposed to wrap, which read as the
        // two objects having swapped names, and closed a parent cycle on a third pass.
        PetriNetsPanel panel = freshPanel();
        GraphObjectFrame target = topLevelFrame(panel, "Target", new Rectangle(100, 100, 200, 150));
        Rectangle band = new Rectangle(90, 90, 220, 170);
        assertEquals("fixture sanity check: the band's own centre must coincide with the "
                + "frame's, the exact case the bug needed",
                new Point2D.Double(200, 175),
                new Point2D.Double(band.getCenterX(), band.getCenterY()));

        PetriNetsPanel.ObjectBandCapture capture = panel.resolveObjectBand(band);
        assertTrue("the swallowed frame is captured", capture.frames().contains(target));
        assertNull("but it is never read as its own parent", capture.parent());

        GraphObjectFrame wrapper = panel.createObjectFromBand(band, "Wrapper");

        assertSame("the target now sits inside the new object",
                wrapper, panel.getCanvasModel().enclosingOf(target));
        assertNull("and the new object itself stays at the top level - not inside the frame it "
                + "just wrapped, which is what the swapped-names bug looked like",
                panel.getCanvasModel().enclosingOf(wrapper));
    }

    // ------------------------------------------------------------------ rule 4: the parent search

    @Test
    public void theNewObjectIsBuiltInsideTheInnermostUnswallowedFrameAtTheBandCentre() {
        PetriNetsPanel panel = freshPanel();
        GraphObjectFrame outer = topLevelFrame(panel, "Outer", new Rectangle(0, 0, 400, 300));
        Rectangle band = new Rectangle(150, 120, 60, 60);

        GraphObjectFrame inner = panel.createObjectFromBand(band, "Inner");

        assertSame(outer, panel.getCanvasModel().enclosingOf(inner));
    }

    @Test
    public void withNoContainingFrameTheNewObjectGoesAtTheTopLevel() {
        PetriNetsPanel panel = freshPanel();
        Rectangle band = new Rectangle(50, 50, 80, 60);

        GraphObjectFrame created = panel.createObjectFromBand(band, "Standalone");

        assertNull(panel.getCanvasModel().enclosingOf(created));
    }

    @Test
    public void theInnermostFrameSearchCascadesPastOneLevelOfNesting() {
        // Not just the frame drawn directly on this canvas: a band centre sitting inside an
        // already-expanded nested object picks THAT object, the same "innermost" rule 4 asks
        // for, not the outer one it happens to be inside too.
        PetriNetsPanel panel = freshPanel();
        GraphObjectFrame outer = topLevelFrame(panel, "Outer", new Rectangle(0, 0, 400, 300));
        GraphObjectFrame inner = new GraphObjectFrame("Inner", new Rectangle(100, 100, 120, 100));
        panel.getCanvasModel().getFrames().add(inner);
        panel.getCanvasModel().nest(inner, outer);
        Rectangle band = new Rectangle(120, 120, 40, 40);

        PetriNetsPanel.ObjectBandCapture capture = panel.resolveObjectBand(band);

        assertSame("the deeper, still-visible frame wins over the one enclosing it",
                inner, capture.parent());
    }

    // ------------------------------------------------------------------ rule 5: only what is loose

    @Test
    public void aFrameOwnedByAnotherObjectIsLeftAloneEvenWhenTheBandSwallowsIt() {
        // The edit-scope rule groupIntoObject already enforces for a marquee-selected chunk:
        // something nested inside an object elsewhere on the canvas cannot be yanked into a
        // brand-new one just because a band geometrically covers it.
        PetriNetsPanel panel = freshPanel();
        GraphObjectFrame owner = topLevelFrame(panel, "Owner", new Rectangle(0, 0, 400, 300));
        GraphObjectFrame stray = new GraphObjectFrame("Stray", new Rectangle(600, 600, 100, 100));
        panel.getCanvasModel().getFrames().add(stray);
        panel.getCanvasModel().nest(stray, owner);
        Rectangle band = new Rectangle(590, 590, 120, 120);

        PetriNetsPanel.ObjectBandCapture capture = panel.resolveObjectBand(band);

        assertNull("nothing at the top level contains the band's centre",
                capture.parent());
        assertTrue("the frame belongs to another object, so it is not swept up despite being "
                + "geometrically inside the band",
                capture.frames().isEmpty());

        GraphObjectFrame created = panel.createObjectFromBand(band, "Empty");

        assertSame("the stray frame is untouched - still exactly where it was",
                owner, panel.getCanvasModel().enclosingOf(stray));
        assertNull(panel.getCanvasModel().enclosingOf(created));
    }

    @Test
    public void aSwallowedFramesOwnMembersTravelWithItRatherThanBeingTornOut() {
        PetriNetsPanel panel = freshPanel();
        GraphObjectFrame target = topLevelFrame(panel, "Target", new Rectangle(50, 50, 200, 150));
        GraphPetriPlace member = placeAt(panel, "M1", 100, 100);
        panel.getCanvasModel().claim(target, member);
        Rectangle band = new Rectangle(30, 30, 240, 190);

        PetriNetsPanel.ObjectBandCapture capture = panel.resolveObjectBand(band);

        assertTrue(capture.frames().contains(target));
        assertFalse("the member's centre is inside the band too, but it is not loose at this "
                + "level - it belongs to the frame that is being wrapped, not to whatever "
                + "encloses that frame",
                capture.elements().contains(member));

        GraphObjectFrame wrapper = panel.createObjectFromBand(band, "Wrap");

        assertSame("the member is still exactly where it was claimed",
                target, panel.getCanvasModel().ownerOf(member));
        assertSame(wrapper, panel.getCanvasModel().enclosingOf(target));
    }

    // ------------------------------------------------------------------ rule 6: empty capture

    @Test
    public void aBandThatCapturesNothingStillBuildsAnEmptyObjectAtItsOwnBounds() {
        PetriNetsPanel panel = freshPanel();
        Rectangle band = new Rectangle(200, 200, 80, 60);

        GraphObjectFrame created = panel.createObjectFromBand(band, "Empty");

        assertNull(panel.getCanvasModel().enclosingOf(created));
        assertEquals("floored to the frame's own minimum size where the band is thinner than it",
                new Rectangle(200, 200,
                        Math.max(GraphObjectFrame.MIN_WIDTH, 80),
                        Math.max(GraphObjectFrame.MIN_HEIGHT, 60)),
                created.getBounds());
    }

    @Test
    public void anEmptyCaptureStillNestsInsideWhateverRuleFourResolved() {
        PetriNetsPanel panel = freshPanel();
        GraphObjectFrame outer = topLevelFrame(panel, "Outer", new Rectangle(0, 0, 400, 300));
        Rectangle band = new Rectangle(150, 90, 150, 100);

        GraphObjectFrame created = panel.createObjectFromBand(band, "Inner");

        assertSame(outer, panel.getCanvasModel().enclosingOf(created));
        assertEquals("no flooring needed when the band already clears the minimum on both sides",
                band, created.getBounds());
    }

    // ------------------------------------------------------------------ the ancestor-cycle guard

    @Test
    public void theNewObjectCannotBeNestedIntoOneOfItsOwnFutureChildren() {
        PetriNetsPanel panel = freshPanel();
        GraphObjectFrame frame = topLevelFrame(panel, "Loop", new Rectangle(100, 100, 200, 150));

        GraphObjectFrame result = panel.groupIntoObject(List.of(), List.of(frame), frame, "Impossible");

        assertNull("asking to build the new object inside a frame that is also handed to it as "
                + "a future child refuses rather than closing a cycle",
                result);
        assertEquals("nothing at all was created", 1, panel.getCanvasModel().getFrames().size());
        assertNull("and the existing frame is left exactly where it was",
                panel.getCanvasModel().enclosingOf(frame));
    }

    // ------------------------------------------------------------------ real mouse events

    /**
     * Driven all the way through {@code mouseReleased} on purpose: a drag under the threshold
     * never reaches {@code askAndCreateObjectFromBand}, so this is the one full gesture that can
     * be pinned with real events without a modal dialog anywhere on the call stack.
     */
    @Test
    public void aDragUnderTheThresholdOnBothSidesCreatesNothing() {
        PetriNetsPanel panel = freshPanel();
        panel.setTool(CanvasTool.OBJECT_BAND);
        int framesBefore = panel.getCanvasModel().getFrames().size();

        press(panel, 100, 100);
        dragTo(panel, 104, 103);
        release(panel, 104, 103);

        assertEquals("a drag under 10 pixels on both sides is a click, not a gesture",
                framesBefore, panel.getCanvasModel().getFrames().size());
    }

    /**
     * The press and drag halves of the gesture, proven against the same private
     * {@code marqueeRectangle} the Marquee tool itself reads from - the machinery the tool was
     * asked to reuse rather than duplicate. Deliberately never released with a drag past the
     * threshold: that would reach the naming dialog.
     */
    @Test
    public void pressAndDragArmTheSharedMarqueeRectangle() {
        PetriNetsPanel panel = freshPanel();
        panel.setTool(CanvasTool.OBJECT_BAND);

        press(panel, 60, 60);
        dragTo(panel, 260, 220);

        assertEquals(new Rectangle(60, 60, 200, 160), marqueeRectangleOf(panel));
    }

    @Test
    public void aPressInsideAFrameArmsABandRatherThanSelectingTheFrame() {
        // Drawing a band inside a frame is how an object is built inside another one, which is
        // the whole of rule 4. Every other tool treats a press on a frame body as "select this
        // frame", and taking that path here left rule 4 unreachable through the gesture: a band
        // could only ever be started on empty canvas.
        PetriNetsPanel panel = freshPanel();
        topLevelFrame(panel, "Existing", new Rectangle(100, 100, 200, 150));
        panel.setTool(CanvasTool.OBJECT_BAND);

        press(panel, 150, 150);
        dragTo(panel, 250, 220);

        assertEquals(new Rectangle(150, 150, 100, 70), marqueeRectangleOf(panel));
        assertNull("the frame under the press is not selected by a band gesture",
                panel.getSelection().getSelectedFrame());
    }

    @Test
    public void aPressOnSomethingSelectedStillBandsRatherThanDraggingTheSelection() {
        // The marquee lets a press inside the selection drag it; a tool whose only gesture is
        // "draw a band" must not inherit that, or the band the user reached for would silently
        // become a move.
        PetriNetsPanel panel = freshPanel();
        GraphPetriPlace place = placeAt(panel, "P1", 200, 200);
        panel.getSelection().add(place);
        panel.setTool(CanvasTool.OBJECT_BAND);

        press(panel, 200, 200);
        dragTo(panel, 320, 300);

        assertEquals(new Rectangle(200, 200, 120, 100), marqueeRectangleOf(panel));
        assertEquals("the element did not move", 200, (int) place.getGraphElementCenter().getX());
    }

    @Test
    public void switchingToTheObjectBandToolClearsSelectionAndArmsACrosshair() {
        PetriNetsPanel panel = freshPanel();
        GraphPetriPlace place = placeAt(panel, "P1", 200, 200);
        panel.setTool(CanvasTool.MARQUEE);
        drag(panel, 140, 140, 260, 260);
        assertTrue("fixture sanity check: the marquee must select the place first",
                panel.getSelection().contains(place));

        panel.setTool(CanvasTool.OBJECT_BAND);

        assertTrue("a tool that acts on whatever the band encloses has no use for a stale "
                + "selection, the same as every other action tool",
                panel.getSelection().isEmpty());
        assertEquals("a drawing tool gets the crosshair, same as Add Place or Add Transition",
                Cursor.CROSSHAIR_CURSOR, panel.getCursor().getType());
    }

    @Test
    public void theNewToolLeavesPlainMarqueeSelectionUnaffected() {
        PetriNetsPanel panel = freshPanel();
        GraphPetriPlace place = placeAt(panel, "P1", 200, 200);
        panel.setTool(CanvasTool.OBJECT_BAND);
        panel.setTool(CanvasTool.MARQUEE);

        drag(panel, 140, 140, 260, 260);

        assertTrue("the Marquee tool still plainly selects, unaffected by sharing its press "
                + "handling with the new tool",
                panel.getSelection().contains(place));
        assertEquals("and nothing was created by it",
                0, panel.getCanvasModel().getFrames().size());
    }
}

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
import java.awt.geom.Point2D;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/**
 * Sweeps the Petri-object frame code for one class of defect: state written on one path and
 * read back on another.
 *
 * <p>That class was the root of the reported "the new object's editor opened empty". Ownership was
 * written from six callers of {@code GraphObjectFrame.addMember}, none of which checked whether
 * anyone else already claimed the element, and read on one path that scanned for the first match -
 * so two frames could claim the same element and only one of them ever answered for it. Every test
 * here now pins the fixed behaviour, and each says which write/read pair it is about.
 *
 * <p>Two tests deliberately keep pinning a refuted hypothesis: the right-click press does not clear
 * the selection the release is about to read. That was the first guess at the reported bug and it
 * was wrong; the tests stay so nobody spends the time again.
 */
public class ObjectFrameDefectSweepTest {

    // ------------------------------------------------------------------ fixtures

    /** An editable canvas with two free places, far enough apart to marquee across both. */
    private static PetriNetsPanel panelWithTwoFreePlaces() {
        PetriNetsPanel panel = freshPanel();
        panel.getGraphNet().getGraphPetriPlaceList().add(placeAt("P1", 150, 150));
        panel.getGraphNet().getGraphPetriPlaceList().add(placeAt("P2", 250, 150));
        return panel;
    }

    private static PetriNetsPanel freshPanel() {
        PetriP.initNext();
        PetriT.initNext();
        return new PetriNetsPanel(null, true);
    }

    private static GraphPetriPlace placeAt(String name, int x, int y) {
        GraphPetriPlace place = new GraphPetriPlace(new PetriP(name, 0), nextId());
        place.setNewCoordinates(new Point2D.Double(x, y));
        return place;
    }

    private static GraphPetriTransition transitionAt(String name, int x, int y) {
        GraphPetriTransition transition = new GraphPetriTransition(new PetriT(name, 1.0), nextId());
        transition.setNewCoordinates(new Point2D.Double(x, y));
        return transition;
    }

    private static int idCounter = 900;

    private static int nextId() {
        return idCounter++;
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

    private static MouseEvent event(PetriNetsPanel panel, int id, int x, int y,
            boolean popupTrigger, int button) {
        return new MouseEvent(panel, id, System.currentTimeMillis(), 0, x, y, 1, popupTrigger, button);
    }

    /** Rubber-band selects from one corner to the other with the left button: press, drag, release. */
    private static void marquee(PetriNetsPanel panel, int fromX, int fromY, int toX, int toY) {
        PetriNetsPanel.MouseHandler handler = mouseHandlerOf(panel);
        MouseMotionListener motion = motionHandlerOf(panel);
        handler.mousePressed(event(panel, MouseEvent.MOUSE_PRESSED, fromX, fromY, false, MouseEvent.BUTTON1));
        motion.mouseDragged(event(panel, MouseEvent.MOUSE_DRAGGED,
                (fromX + toX) / 2, (fromY + toY) / 2, false, MouseEvent.BUTTON1));
        motion.mouseDragged(event(panel, MouseEvent.MOUSE_DRAGGED, toX, toY, false, MouseEvent.BUTTON1));
        handler.mouseReleased(event(panel, MouseEvent.MOUSE_RELEASED, toX, toY, false, MouseEvent.BUTTON1));
    }

    /**
     * Runs a popup-trigger release and reports which of the Petri-object menus it decided on.
     * The panel is not showing in a window, so {@code JPopupMenu.show} always throws before
     * anything is painted, and the frame it throws from names the decision.
     *
     * @return the simple name of the {@code show...Menu} method the release called
     */
    private static String menuOpenedByPopupRelease(PetriNetsPanel panel, int x, int y) {
        try {
            mouseHandlerOf(panel).mouseReleased(
                    event(panel, MouseEvent.MOUSE_RELEASED, x, y, true, MouseEvent.BUTTON3));
        } catch (RuntimeException expected) {
            for (StackTraceElement frame : expected.getStackTrace()) {
                if (frame.getMethodName().startsWith("show") && frame.getMethodName().endsWith("Menu")) {
                    return frame.getMethodName();
                }
            }
            throw new AssertionError("a menu was attempted, but not from a show...Menu method", expected);
        }
        throw new AssertionError("no menu was opened at all by the popup-trigger release");
    }

    private static Object invoke(PetriNetsPanel panel, String name, Class<?>[] types, Object... args) {
        try {
            Method method = PetriNetsPanel.class.getDeclaredMethod(name, types);
            method.setAccessible(true);
            return method.invoke(panel, args);
        } catch (InvocationTargetException failure) {
            throw new AssertionError(failure.getCause());
        } catch (ReflectiveOperationException broken) {
            throw new AssertionError(broken);
        }
    }

    // ------------------------------------------- 1. the right-click gesture itself

    /**
     * The requested proof, and it refutes the hypothesis. Windows delivers the popup trigger on
     * the release, so the ordinary press handling runs in between: BUTTON3 press with
     * popupTrigger=false, then BUTTON3 release with popupTrigger=true. If that press cleared the
     * selection, the release would fall through to the "new empty Petri-object" menu.
     *
     * <p>Outcome: it does not. The press reaches no statement that touches the selection on a
     * right button, and the release still sees both places.
     */
    @Test
    public void aRightClickPressDoesNotClearTheSelectionTheReleaseIsAboutToRead() {
        PetriNetsPanel panel = panelWithTwoFreePlaces();
        marquee(panel, 60, 60, 340, 260);
        assertEquals("fixture: the marquee caught both places", 2, panel.getChoosenElements().size());

        mouseHandlerOf(panel).mousePressed(
                event(panel, MouseEvent.MOUSE_PRESSED, 420, 420, false, MouseEvent.BUTTON3));
        assertEquals("the press left the selection alone", 2, panel.getChoosenElements().size());

        assertEquals("so the release still offers to group it",
                "showGroupSelectionMenu", menuOpenedByPopupRelease(panel, 420, 420));
        assertEquals("and showing the menu did not consume the selection either",
                2, panel.getChoosenElements().size());
    }

    /**
     * The same sequence with the right-click landing on one of the selected places rather than
     * on empty canvas, which is what a user actually aims at. Also unaffected: a right button
     * never reaches the branch that picks up an element.
     */
    @Test
    public void aRightClickOnTopOfASelectedPlaceKeepsTheSelectionToo() {
        PetriNetsPanel panel = panelWithTwoFreePlaces();
        marquee(panel, 60, 60, 340, 260);

        mouseHandlerOf(panel).mousePressed(
                event(panel, MouseEvent.MOUSE_PRESSED, 150, 150, false, MouseEvent.BUTTON3));

        assertEquals(2, panel.getChoosenElements().size());
        assertEquals("showGroupSelectionMenu", menuOpenedByPopupRelease(panel, 150, 150));
    }

    // ------------------------------------------- 2. what the selection is allowed to contain

    /**
     * The gesture-level trigger for the reported symptom, now closed. A rubber band drawn over an
     * existing object used to select that object's own places and transitions, because the marquee
     * sweep walked the whole net with no owner filter - and grouping that selection then claimed
     * elements another frame still held. A selection only ever holds what is on the active canvas.
     */
    @Test
    public void aMarqueeSkipsElementsAnObjectClaims() {
        PetriNetsPanel panel = panelWithTwoFreePlaces();
        List<GraphPetriPlace> places = panel.getGraphNet().getGraphPetriPlaceList();
        GraphObjectFrame existing = new GraphObjectFrame("First", new Rectangle(100, 100, 220, 160));
        panel.addObjectFrame(existing);
        for (GraphPetriPlace place : places) {
            panel.getCanvasModel().claim(existing, place);
        }

        marquee(panel, 60, 60, 300, 260);

        assertTrue("an object's own elements are not selectable from the canvas above it",
                panel.getChoosenElements().isEmpty());
        assertSame("they are still claimed by the object",
                existing, panel.getCanvasModel().ownerOf(places.getFirst()));
    }

    /**
     * The other half of the same asymmetry. A Petri-object used to be caught only when the band
     * enclosed it whole, while its elements were caught by their centre - so a band across part of
     * an object selected its contents and left the object unselected. One rule now: by the centre,
     * both kinds.
     */
    @Test
    public void aMarqueeCatchesAnObjectWhoseCentreIsInTheBand() {
        PetriNetsPanel panel = panelWithTwoFreePlaces();
        GraphObjectFrame existing = new GraphObjectFrame("First", new Rectangle(100, 100, 220, 160));
        panel.addObjectFrame(existing);
        panel.getSelection().clear();

        // The band covers the frame's centre (210,180) without enclosing the frame whole.
        marquee(panel, 60, 60, 260, 260);

        assertTrue("the object is caught by its centre, exactly like a place would be",
                panel.getSelection().contains(existing));
    }

    /**
     * Ctrl+A stops at the boundary of an object, which was the second, easier route into the same
     * selection: it used to take every place and transition on the canvas regardless of owner.
     */
    @Test
    public void selectAllTakesWhatIsOnThisCanvasAndNothingInsideAnObject() {
        PetriNetsPanel panel = panelWithTwoFreePlaces();
        List<GraphPetriPlace> places = panel.getGraphNet().getGraphPetriPlaceList();
        GraphObjectFrame existing = new GraphObjectFrame("First", new Rectangle(100, 100, 220, 160));
        panel.addObjectFrame(existing);
        panel.getCanvasModel().claim(existing, places.getFirst());

        panel.selectAll();

        assertEquals("only the free place", 1, panel.getChoosenElements().size());
        assertSame(places.get(1), panel.getChoosenElements().getFirst());
        assertTrue("and the object itself", panel.getSelection().contains(existing));
    }

    /**
     * The same rule read from inside an object: its own members and the objects nested directly in
     * it, nothing above and nothing below those.
     */
    @Test
    public void selectAllOnAnObjectCanvasTakesItsMembersAndItsChildFrames() {
        PetriNetsPanel panel = panelWithTwoFreePlaces();
        List<GraphPetriPlace> places = panel.getGraphNet().getGraphPetriPlaceList();
        GraphObjectFrame parent = new GraphObjectFrame("Parent", new Rectangle(60, 60, 400, 300));
        panel.addObjectFrame(parent);
        panel.getCanvasModel().claim(parent, places.getFirst());
        GraphObjectFrame child = new GraphObjectFrame("Child", new Rectangle(100, 100, 180, 140));
        panel.getCanvasModel().nest(child, parent);
        panel.addObjectFrame(child);
        panel.getCanvasModel().claim(child, places.get(1));

        panel.openObjectCanvas(parent);
        panel.selectAll();

        assertEquals("the parent's own member, and not the child's", 1, panel.getChoosenElements().size());
        assertSame(places.getFirst(), panel.getChoosenElements().getFirst());
        assertEquals(List.of(child), panel.getSelection().frames());
    }

    /**
     * The defect itself, reached from the gesture - and it is now the nesting feature. Grouping
     * always groups what is on the active canvas, so grouping inside an object produces an object
     * nested in it that really does hold the chunk.
     */
    @Test
    public void groupingOnAnObjectsOwnCanvasMakesANestedObjectThatHoldsTheChunk() {
        PetriNetsPanel panel = panelWithTwoFreePlaces();
        List<GraphPetriPlace> places = panel.getGraphNet().getGraphPetriPlaceList();
        GraphObjectFrame parent = new GraphObjectFrame("Parent", new Rectangle(60, 60, 400, 300));
        panel.addObjectFrame(parent);
        for (GraphPetriPlace place : places) {
            panel.getCanvasModel().claim(parent, place);
        }

        panel.openObjectCanvas(parent);
        marquee(panel, 60, 60, 300, 260);
        assertEquals("its own members are selectable on its own canvas",
                2, panel.getChoosenElements().size());

        GraphObjectFrame child = panel.groupIntoObject(
                List.copyOf(panel.getChoosenElements()), "Child");

        assertSame("the new object is nested in the one being edited",
                parent, panel.getCanvasModel().enclosingOf(child));
        assertEquals(2, panel.getCanvasModel().levelOf(child));
        assertEquals("and it holds the chunk", 2, panel.countElementsIn(child));
        assertSame(child, panel.getCanvasModel().ownerOf(places.getFirst()));
        assertEquals("which the parent no longer claims directly", 0, parent.getMembers().size());
        assertEquals("though from outside the parent still holds both",
                2, panel.countElementsIn(parent));
        assertTrue("a nested object is created collapsed, as asked", child.isCollapsed());
        assertEquals(GraphObjectFrame.COLLAPSED_WIDTH, child.getBounds().width);
        assertEquals(GraphObjectFrame.COLLAPSED_HEIGHT, child.getBounds().height);
    }

    // ------------------------------------------- 3. membership written one way, read another

    /**
     * Deleting an element releases it. The frame used to go on claiming an element the canvas no
     * longer drew, so {@code getMembers()} and {@code countElementsIn} disagreed forever.
     */
    @Test
    public void deletingAnElementReleasesItFromItsObject() throws Exception {
        PetriNetsPanel panel = panelWithTwoFreePlaces();
        GraphPetriPlace place = panel.getGraphNet().getGraphPetriPlaceList().getFirst();
        GraphObjectFrame frame = new GraphObjectFrame("Object", new Rectangle(100, 100, 220, 160));
        panel.addObjectFrame(frame);
        panel.getCanvasModel().claim(frame, place);

        panel.remove(place);

        assertEquals("the canvas no longer draws it", 0, panel.countElementsIn(frame));
        assertFalse("and the frame no longer claims it either", frame.hasMember(place));
        assertTrue(frame.getMembers().isEmpty());
    }

    /** The other direction: undo is symmetric with that release. */
    @Test
    public void undoingThatDeleteReturnsItToTheSameObject() {
        PetriNetsPanel panel = panelWithTwoFreePlaces();
        GraphPetriPlace place = panel.getGraphNet().getGraphPetriPlaceList().getFirst();
        GraphObjectFrame frame = new GraphObjectFrame("Object", new Rectangle(100, 100, 220, 160));
        panel.addObjectFrame(frame);
        panel.getCanvasModel().claim(frame, place);

        ua.stetsenkoinna.graphpresentation.undoable_edits.DeleteGraphElementsEdit edit =
                new ua.stetsenkoinna.graphpresentation.undoable_edits.DeleteGraphElementsEdit(
                        panel, place, new java.util.ArrayList<>(), new java.util.ArrayList<>());
        edit.rememberOwner(place, panel.getCanvasModel().ownerOf(place));
        invoke(panel, "remove", new Class<?>[]{ua.stetsenkoinna.graphnet.GraphElement.class}, place);
        assertFalse(frame.hasMember(place));

        edit.undo();

        assertSame("undo puts it back into the object it was deleted out of",
                frame, panel.getCanvasModel().ownerOf(place));
        assertEquals(1, panel.countElementsIn(frame));
    }

    /**
     * A fused place answers with whatever encloses it, not with the frame that happened to own it
     * when the fusion was made. The fusion short-circuit in {@code ownerOf} used to outrank real
     * membership and nothing cleared it, so after the owning frame was removed the place still
     * resolved to an object that was no longer on the canvas.
     */
    @Test
    public void removingAFrameLeavesItsFusedPlaceAnsweringWithWhateverEnclosedIt() {
        PetriNetsPanel panel = panelWithTwoFreePlaces();
        List<GraphPetriPlace> places = panel.getGraphNet().getGraphPetriPlaceList();
        GraphPetriPlace framed = places.getFirst();
        GraphPetriPlace free = places.get(1);
        GraphObjectFrame owner = new GraphObjectFrame("Owner", new Rectangle(100, 100, 140, 120));
        panel.addObjectFrame(owner);
        panel.getCanvasModel().claim(owner, framed);
        panel.getCanvasModel().joinPlaces(framed, free);

        panel.removeObjectFrame(owner);

        assertFalse("the frame is off the canvas",
                panel.getCanvasModel().getFrames().contains(owner));
        assertNull("and the place is free, since nothing enclosed the frame",
                panel.getCanvasModel().ownerOf(framed));
        assertFalse(owner.hasMember(framed));
    }

    // ------------------------------------------- 4. overlap, which is what nesting is

    /**
     * {@code ownerOf} and {@code frameAt} break an overlap tie the same way now: deeper wins. They
     * used to answer in opposite directions - first claimer versus last container - which is the
     * thing that had to be settled before a frame could sit inside another frame.
     */
    @Test
    public void frameAtPicksTheInnermostFrameAndOwnerOfTheOneThatClaims() {
        PetriNetsPanel panel = freshPanel();
        GraphPetriPlace place = placeAt("PInside", 250, 250);
        panel.getGraphNet().getGraphPetriPlaceList().add(place);

        GraphObjectFrame outer = new GraphObjectFrame("Outer", new Rectangle(100, 100, 400, 300));
        panel.addObjectFrame(outer);
        GraphObjectFrame inner = new GraphObjectFrame("Inner", new Rectangle(200, 200, 150, 120));
        panel.getCanvasModel().nest(inner, outer);
        panel.addObjectFrame(inner);
        panel.getCanvasModel().claim(inner, place);

        assertSame("frameAt: the innermost frame containing the point", inner,
                panel.getCanvasModel().frameAt(place.getGraphElementCenter()));
        assertSame("ownerOf: the frame that claims it, which is the same one", inner,
                panel.getCanvasModel().ownerOf(place));
    }

    /**
     * Removing a frame lifts what it held one level out. Geometry is out of {@code releaseMembers}
     * entirely: the frame drawn inside the removed one gets nothing, where it used to silently
     * inherit the outer object's whole net.
     */
    @Test
    public void removingAFrameLiftsItsNetToWhateverEnclosedIt() {
        PetriNetsPanel panel = freshPanel();
        GraphPetriPlace place = placeAt("PInside", 250, 250);
        panel.getGraphNet().getGraphPetriPlaceList().add(place);

        GraphObjectFrame outer = new GraphObjectFrame("Outer", new Rectangle(100, 100, 400, 300));
        panel.addObjectFrame(outer);
        panel.getCanvasModel().claim(outer, place);
        GraphObjectFrame inner = new GraphObjectFrame("Inner", new Rectangle(200, 200, 150, 120));
        panel.addObjectFrame(inner);

        panel.removeObjectFrame(outer);

        assertFalse("the inner object does not take the outer object's place", inner.hasMember(place));
        assertEquals(0, panel.countElementsIn(inner));
        assertNull("nothing enclosed the outer object, so its net is free",
                panel.getCanvasModel().ownerOf(place));
    }

    // ------------------------------------------- 5. do the creation paths agree

    /**
     * The copy is locked inside its own object and not left selected, the way every other creation
     * path already behaved. {@code duplicateObject} used to go through {@code addNetFragment},
     * which selects and greens what it adds, so the copy sat in the selection while also being
     * locked inside an object - and Delete, or a drag from empty canvas, then acted on it.
     */
    @Test
    public void duplicatingAnObjectLeavesTheCopyLockedAndNotSelected() {
        PetriNetsPanel panel = freshPanel();
        GraphPetriPlace place = placeAt("PDup", 200, 200);
        GraphPetriTransition transition = transitionAt("TDup", 300, 200);
        panel.getGraphNet().getGraphPetriPlaceList().add(place);
        panel.getGraphNet().getGraphPetriTransitionList().add(transition);
        GraphObjectFrame frame = new GraphObjectFrame("Object", new Rectangle(150, 150, 220, 160));
        panel.addObjectFrame(frame);
        panel.getCanvasModel().claim(frame, place);
        panel.getCanvasModel().claim(frame, transition);

        GraphObjectFrame copy = panel.duplicateObject(frame, "Object copy");

        assertEquals("two objects now", 2, panel.getCanvasModel().getFrames().size());
        assertEquals("the copy holds the whole net", 2, panel.countElementsIn(copy));
        assertTrue("and none of it is left selected", panel.getChoosenElements().isEmpty());
    }

    /**
     * The copy's net sits inside its own frame where the original's did.
     * {@code duplicateObject} used to translate the original's rectangle by the frame width and
     * forget that {@code addNetFragment} added a paste offset of 15,15 to every element on top
     * of that, so the copy's net drifted 15 pixels inside its own frame. The whole subtree -
     * frame rectangles and elements alike - is now translated by one shared delta, so the copy
     * keeps the original's exact fit: the same frame size, with the net at the same offset
     * inside it. (A refit with {@code boundsAround} would be wrong for a nest: it cannot see
     * the nested frames' own rectangles, only elements.)
     */
    @Test
    public void theDuplicateKeepsTheOriginalsFitAroundItsNet() {
        PetriNetsPanel panel = freshPanel();
        GraphPetriPlace place = placeAt("PDrift", 200, 200);
        panel.getGraphNet().getGraphPetriPlaceList().add(place);
        GraphObjectFrame frame = new GraphObjectFrame("Object", new Rectangle(150, 150, 220, 160));
        panel.addObjectFrame(frame);
        panel.getCanvasModel().claim(frame, place);

        GraphObjectFrame copyFrame = panel.duplicateObject(frame, "Object copy");

        GraphPetriPlace copiedPlace = panel.getGraphNet().getGraphPetriPlaceList().get(1);
        Point2D copied = copiedPlace.getGraphElementCenter();
        assertTrue("the copy's own element is inside its own frame: "
                        + copyFrame.getBounds() + " vs " + copied,
                copyFrame.getBounds().contains(copied.getX(), copied.getY()));
        assertEquals("the copy keeps the original's frame size",
                frame.getBounds().getSize(), copyFrame.getBounds().getSize());
        Point2D original = place.getGraphElementCenter();
        assertEquals("the net sits at the same offset inside the copy's frame",
                original.getX() - frame.getBounds().x,
                copied.getX() - copyFrame.getBounds().x, 0.0);
        assertEquals("the net sits at the same offset inside the copy's frame",
                original.getY() - frame.getBounds().y,
                copied.getY() - copyFrame.getBounds().y, 0.0);
    }

    /**
     * The same path used to forget that the object was collapsed: the copy took the collapsed
     * rectangle as its size but not the collapsed flag, so it came out as an expanded frame
     * smaller than the minimum a user could resize one to, with its own net drawn outside it.
     */
    @Test
    public void duplicatingACollapsedObjectProducesACollapsedCopy() {
        PetriNetsPanel panel = freshPanel();
        GraphPetriPlace place = placeAt("PColl", 200, 260);
        panel.getGraphNet().getGraphPetriPlaceList().add(place);
        GraphObjectFrame frame = new GraphObjectFrame("Object", new Rectangle(150, 150, 300, 220));
        panel.addObjectFrame(frame);
        panel.getCanvasModel().claim(frame, place);
        frame.setCollapsed(true);

        GraphObjectFrame copy = panel.duplicateObject(frame, "Object copy");

        assertTrue("the copy is collapsed too", copy.isCollapsed());
        assertEquals(GraphObjectFrame.COLLAPSED_HEIGHT, copy.getBounds().height);
        assertEquals(GraphObjectFrame.COLLAPSED_WIDTH, copy.getBounds().width);
    }

    // ------------------------------------------- 6. operations that used to skip frames

    /**
     * Dragging a multi-selection reparents every element that landed in a different object, with
     * one confirmation for the whole drag. It used to change no membership at all, because the
     * confirmation read a single {@code draggedElement} that a bulk drag never set.
     */
    @Test
    public void draggingAMultiSelectionIntoAnObjectReparentsEveryElement() {
        PetriNetsPanel panel = panelWithTwoFreePlaces();
        GraphObjectFrame target = new GraphObjectFrame("Target", new Rectangle(400, 300, 700, 700));
        panel.addObjectFrame(target);
        marquee(panel, 60, 60, 340, 260);
        assertEquals(2, panel.getChoosenElements().size());

        panel.moveSelectionBy(600, 500);
        // The reparenting the drag's release performs, minus its confirmation dialog - which is
        // exactly why the operation and the asking are separate methods.
        panel.applyReparenting(panel.pendingReparenting());

        GraphPetriPlace moved = panel.getGraphNet().getGraphPetriPlaceList().getFirst();
        assertSame("the place is drawn inside the object now", target,
                panel.getCanvasModel().frameAt(moved.getGraphElementCenter()));
        assertSame("and belongs to it", target, panel.getCanvasModel().ownerOf(moved));
        assertEquals(2, panel.countElementsIn(target));
    }

    /**
     * "Locate net in center" moves every frame with its own net. It used to call
     * {@code GraphPetriNet.changeLocation} directly, which has no notion of a frame, so every
     * object's net slid out of its own frame by the same offset.
     */
    @Test
    public void locatingTheNetInTheCentreMovesEveryFrameWithItsNet() {
        PetriNetsPanel panel = panelWithTwoFreePlaces();
        GraphPetriPlace place = panel.getGraphNet().getGraphPetriPlaceList().getFirst();
        GraphObjectFrame frame = new GraphObjectFrame("Object", new Rectangle(100, 100, 220, 160));
        panel.addObjectFrame(frame);
        panel.getCanvasModel().claim(frame, place);
        double offsetBeforeX = place.getGraphElementCenter().getX() - frame.getBounds().x;
        double offsetBeforeY = place.getGraphElementCenter().getY() - frame.getBounds().y;

        panel.centreCanvasAt(new Point(900, 700));

        assertEquals("the place keeps its position inside its own frame", offsetBeforeX,
                place.getGraphElementCenter().getX() - frame.getBounds().x, 0.001);
        assertEquals(offsetBeforeY,
                place.getGraphElementCenter().getY() - frame.getBounds().y, 0.001);
        assertTrue("and it is still inside it",
                frame.getBounds().contains(place.getGraphElementCenter().getX(),
                        place.getGraphElementCenter().getY()));
    }

}

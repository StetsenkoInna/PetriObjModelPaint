package ua.stetsenkoinna.graphpresentation;

import org.junit.Test;
import ua.stetsenkoinna.graphnet.GraphCanvasModel;
import ua.stetsenkoinna.graphnet.GraphElement;
import ua.stetsenkoinna.graphnet.GraphObjectFrame;
import ua.stetsenkoinna.graphnet.GraphPetriNet;
import ua.stetsenkoinna.graphnet.GraphPetriPlace;
import ua.stetsenkoinna.graphnet.GraphPetriTransition;
import ua.stetsenkoinna.graphnet.GraphPlaceFusion;
import ua.stetsenkoinna.petriobj.PetriP;
import ua.stetsenkoinna.petriobj.PetriT;

import java.awt.Rectangle;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionListener;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/**
 * Selecting a whole document of Petri-objects and dragging it somewhere else.
 *
 * <p>The gesture used to come apart in four separate places, all of which are pinned here.
 * Adding a document to the canvas copies its net so two documents cannot collide over an id,
 * and the frames and shared places that came with it were added pointing at the originals - so
 * every element arrived unowned inside an empty object, {@code Ctrl+A} picked all of it up as
 * free elements, the release then asked to move each one into "a different Petri-object", and
 * the shared-place links stayed behind at their old coordinates because both of their ends were
 * off-canvas ghosts. Dragging towards the top-left corner sheared the objects apart, because
 * each frame stopped at the origin on its own while the rest kept travelling. Pasting several
 * objects left only the last of them selected, so the drag that usually follows a paste pulled
 * one object out of the group. And a drag left no undo step at all, so {@code Ctrl+Z} after one
 * reached past it and took back whatever had been done before.
 */
public class ObjectSelectionDragTest {

    private static int idCounter = 7000;

    /**
     * Two objects, a place and a transition each, with the second object's place shared with the
     * first's - the shape of a composed document, small enough to assert about.
     */
    private static GraphCanvasModel twoLinkedObjects() {
        GraphCanvasModel canvas = new GraphCanvasModel();
        GraphPetriNet net = new GraphPetriNet();
        canvas.setNet(net);
        canvas.setName("Two linked objects");

        GraphObjectFrame left = new GraphObjectFrame("Left", new Rectangle(100, 100, 300, 200));
        canvas.getFrames().add(left);
        GraphPetriPlace leftOut = placeAt(net, 150, 180);
        canvas.claim(left, leftOut);
        canvas.claim(left, transitionAt(net, 250, 180));

        GraphObjectFrame right = new GraphObjectFrame("Right", new Rectangle(600, 100, 300, 200));
        canvas.getFrames().add(right);
        GraphPetriPlace rightIn = placeAt(net, 650, 180);
        canvas.claim(right, rightIn);
        canvas.claim(right, transitionAt(net, 750, 180));

        canvas.joinPlaces(rightIn, leftOut);
        return canvas;
    }

    /** A panel that already holds a net, so adding a document takes the merge-and-copy path. */
    private static PetriNetsPanel panelHoldingSomething() {
        PetriP.initNext();
        PetriT.initNext();
        PetriNetsPanel panel = new PetriNetsPanel(null, true);
        GraphCanvasModel existing = new GraphCanvasModel();
        GraphPetriNet net = new GraphPetriNet();
        existing.setNet(net);
        placeAt(net, 1400, 900);
        panel.setCanvasModel(existing);
        return panel;
    }

    private static List<GraphElement> allElements(PetriNetsPanel panel) {
        List<GraphElement> all = new ArrayList<>();
        all.addAll(panel.getGraphNet().getGraphPetriPlaceList());
        all.addAll(panel.getGraphNet().getGraphPetriTransitionList());
        return all;
    }

    /** Membership is an identity set, so "which one" is arbitrary - only that it moves matters. */
    private static GraphElement anyMemberOf(GraphObjectFrame frame) {
        return frame.getMembers().iterator().next();
    }

    private static PetriNetsPanel.MouseHandler mouseHandlerOf(PetriNetsPanel panel) {
        for (java.awt.event.MouseListener listener : panel.getMouseListeners()) {
            if (listener instanceof PetriNetsPanel.MouseHandler handler) {
                return handler;
            }
        }
        throw new AssertionError("the panel registered no MouseHandler");
    }

    private static MouseEvent event(PetriNetsPanel panel, int id, int x, int y) {
        return new MouseEvent(panel, id, System.currentTimeMillis(), 0, x, y, 1, false,
                MouseEvent.BUTTON1);
    }

    /** Press, drag and release with the real handlers, the way a user's pointer would. */
    private static void dragFrom(PetriNetsPanel panel, int x, int y, int dx, int dy) {
        PetriNetsPanel.MouseHandler handler = mouseHandlerOf(panel);
        MouseMotionListener motion = panel.getMouseMotionListeners()[0];
        handler.mousePressed(event(panel, MouseEvent.MOUSE_PRESSED, x, y));
        motion.mouseDragged(event(panel, MouseEvent.MOUSE_DRAGGED, x + dx / 2, y + dy / 2));
        motion.mouseDragged(event(panel, MouseEvent.MOUSE_DRAGGED, x + dx, y + dy));
        handler.mouseReleased(event(panel, MouseEvent.MOUSE_RELEASED, x + dx, y + dy));
    }

    /** Ctrl+A, then a drag from one point, answering with how far each object actually moved. */
    private static List<Point2D> selectAllAndDragFrom(PetriNetsPanel panel, int x, int y) {
        panel.selectAll();
        List<Point2D> before = new ArrayList<>();
        for (GraphObjectFrame frame : panel.getCanvasModel().getFrames()) {
            before.add(new Point2D.Double(frame.getBounds().x, frame.getBounds().y));
        }
        dragFrom(panel, x, y, 100, 80);
        List<Point2D> deltas = new ArrayList<>();
        int index = 0;
        for (GraphObjectFrame frame : panel.getCanvasModel().getFrames()) {
            Point2D was = before.get(index++);
            deltas.add(new Point2D.Double(frame.getBounds().x - was.getX(),
                    frame.getBounds().y - was.getY()));
        }
        return deltas;
    }

    private static void assertEveryObjectMovedBy(List<Point2D> deltas, int dx, int dy) {
        for (Point2D delta : deltas) {
            assertEquals("every selected object moves by the same amount", dx, delta.getX(), 0.001);
            assertEquals("every selected object moves by the same amount", dy, delta.getY(), 0.001);
        }
    }

    private static GraphPetriPlace placeAt(GraphPetriNet net, int x, int y) {
        GraphPetriPlace place = new GraphPetriPlace(new PetriP("P" + idCounter, 0), idCounter++);
        place.setNewCoordinates(new Point2D.Double(x, y));
        net.getGraphPetriPlaceList().add(place);
        return place;
    }

    private static GraphPetriTransition transitionAt(GraphPetriNet net, int x, int y) {
        GraphPetriTransition transition =
                new GraphPetriTransition(new PetriT("T" + idCounter, 1.0), idCounter++);
        transition.setNewCoordinates(new Point2D.Double(x, y));
        net.getGraphPetriTransitionList().add(transition);
        return transition;
    }

    @Test
    public void anAddedDocumentKeepsEveryElementInsideTheObjectThatHeldIt() {
        PetriNetsPanel panel = panelHoldingSomething();

        panel.addCanvasModel(twoLinkedObjects());

        GraphCanvasModel canvas = panel.getCanvasModel();
        assertEquals(2, canvas.getFrames().size());
        int owned = 0;
        for (GraphElement element : allElements(panel)) {
            if (canvas.ownerOf(element) != null) {
                owned++;
            }
        }
        // Four of the five: the place the panel already held is free and stays free.
        assertEquals(4, owned);
        assertEquals(2, canvas.getFrames().get(0).getMembers().size());
        assertEquals(2, canvas.getFrames().get(1).getMembers().size());
    }

    @Test
    public void anAddedDocumentsSharedPlaceIsAnchoredToTheElementsOnTheCanvas() {
        PetriNetsPanel panel = panelHoldingSomething();

        panel.addCanvasModel(twoLinkedObjects());

        assertEquals(1, panel.getCanvasModel().getFusions().size());
        GraphPlaceFusion fusion = panel.getCanvasModel().getFusions().get(0);
        List<GraphElement> onCanvas = allElements(panel);
        assertTrue("the master half must be a place the canvas actually draws",
                onCanvas.stream().anyMatch(e -> e == fusion.getMaster()));
        assertTrue("the joined half must be a place the canvas actually draws",
                onCanvas.stream().anyMatch(e -> e == fusion.getJoined()));
        // And it knows which object each half sits in, which is what decides how it is drawn.
        assertNotNull(fusion.getMasterOwner());
        assertNotNull(fusion.getJoinedOwner());
    }

    @Test
    public void selectAllThenDragMovesEverythingAndChangesNoMembership() {
        PetriNetsPanel panel = panelHoldingSomething();
        panel.addCanvasModel(twoLinkedObjects());
        GraphCanvasModel canvas = panel.getCanvasModel();
        GraphObjectFrame left = canvas.getFrames().get(0);
        GraphElement leftMember = anyMemberOf(left);
        Point2D memberBefore = leftMember.getGraphElementCenter();

        panel.selectAll();
        panel.moveSelectionBy(90, 70);

        assertEquals(190, left.getBounds().x);
        assertEquals(170, left.getBounds().y);
        assertEquals(memberBefore.getX() + 90, leftMember.getGraphElementCenter().getX(), 0.001);
        assertEquals(memberBefore.getY() + 70, leftMember.getGraphElementCenter().getY(), 0.001);
        assertSame("the drag moved the object and its net together, so nothing changed hands",
                left, canvas.ownerOf(leftMember));
        // Which is what the release asks about: a rigid move proposes no reparenting at all,
        // so there is nothing left for a confirmation to interrupt.
        assertTrue(panel.pendingReparenting().isEmpty());
    }

    @Test
    public void aSelectionStoppedByTheCanvasEdgeStaysRigid() {
        PetriNetsPanel panel = panelHoldingSomething();
        panel.addCanvasModel(twoLinkedObjects());
        GraphCanvasModel canvas = panel.getCanvasModel();
        GraphObjectFrame left = canvas.getFrames().get(0);
        GraphObjectFrame right = canvas.getFrames().get(1);
        GraphElement leftMember = anyMemberOf(left);
        Point2D memberBefore = leftMember.getGraphElementCenter();

        panel.selectAll();
        // Far past the origin: the left object has 100 to give in each direction, the right one
        // has 600 horizontally, so the whole selection may only move by the tighter of the two.
        panel.moveSelectionBy(-400, -400);

        assertEquals(0, left.getBounds().x);
        assertEquals(0, left.getBounds().y);
        assertEquals("the objects kept the distance between them", 500, right.getBounds().x);
        assertEquals(0, right.getBounds().y);
        assertEquals(memberBefore.getX() - 100, leftMember.getGraphElementCenter().getX(), 0.001);
        assertEquals(memberBefore.getY() - 100, leftMember.getGraphElementCenter().getY(), 0.001);
    }

    @Test
    public void pastingSeveralObjectsSelectsAllOfThemSoTheNextDragMovesThePasteWhole() {
        PetriNetsPanel panel = panelHoldingSomething();
        panel.addCanvasModel(twoLinkedObjects());

        panel.selectAll();
        panel.copySelection();
        panel.pasteClipboard();

        assertEquals(4, panel.getCanvasModel().getFrames().size());
        assertEquals("both pasted objects are selected, not only the last one created",
                2, panel.getSelection().allFrames().size());

        List<Rectangle> pastedBefore = new ArrayList<>();
        for (GraphObjectFrame frame : panel.getSelection().allFrames()) {
            pastedBefore.add(new Rectangle(frame.getBounds()));
        }
        panel.moveSelectionBy(40, 30);
        int index = 0;
        for (GraphObjectFrame frame : panel.getSelection().allFrames()) {
            Rectangle before = pastedBefore.get(index++);
            assertEquals(before.x + 40, frame.getBounds().x);
            assertEquals(before.y + 30, frame.getBounds().y);
        }
    }

    @Test
    public void grabbingAnObjectByItsHeaderInsideASelectionDragsTheWholeSelection() {
        PetriNetsPanel panel = panelHoldingSomething();
        panel.addCanvasModel(twoLinkedObjects());
        Rectangle left = panel.getCanvasModel().getFrames().get(0).getBounds();

        // The header band, which used to pull this one object out of the group it was in.
        assertEveryObjectMovedBy(
                selectAllAndDragFrom(panel, left.x + 150, left.y + 8), 100, 80);
    }

    @Test
    public void grabbingAnElementInsideASelectedObjectDragsTheWholeSelection() {
        PetriNetsPanel panel = panelHoldingSomething();
        panel.addCanvasModel(twoLinkedObjects());
        GraphElement member = anyMemberOf(panel.getCanvasModel().getFrames().get(0));
        Point2D centre = member.getGraphElementCenter();

        // An element of a shown object doubles as its own port, so this used to start a link
        // and move nothing at all.
        assertEveryObjectMovedBy(selectAllAndDragFrom(
                panel, (int) centre.getX(), (int) centre.getY()), 100, 80);
    }

    @Test
    public void grabbingAnObjectsFloorInsideASelectionDragsTheWholeSelection() {
        PetriNetsPanel panel = panelHoldingSomething();
        panel.addCanvasModel(twoLinkedObjects());
        Rectangle left = panel.getCanvasModel().getFrames().get(0).getBounds();

        assertEveryObjectMovedBy(selectAllAndDragFrom(
                panel, left.x + 20, left.y + left.height - 20), 100, 80);
    }

    @Test
    public void aLoneObjectIsStillGrabbedByItsHeaderOnItsOwn() {
        PetriNetsPanel panel = panelHoldingSomething();
        panel.addCanvasModel(twoLinkedObjects());
        GraphObjectFrame left = panel.getCanvasModel().getFrames().get(0);
        GraphObjectFrame right = panel.getCanvasModel().getFrames().get(1);
        Rectangle rightBefore = new Rectangle(right.getBounds());

        // Selecting one object and dragging it is unchanged: the multi-selection rule only
        // applies above one selected thing.
        panel.getSelection().clear();
        panel.getSelection().setSelectedFrame(left);
        dragFrom(panel, left.getBounds().x + 150, left.getBounds().y + 8, 60, 40);

        assertEquals(160, left.getBounds().x);
        assertEquals(140, left.getBounds().y);
        assertEquals("the object that was not selected stayed where it was",
                rightBefore.x, right.getBounds().x);
    }

    @Test
    public void undoingADragPutsEveryObjectAndItsNetBackWhereItWas() {
        PetriNetsPanel panel = panelHoldingSomething();
        panel.addCanvasModel(twoLinkedObjects());
        GraphCanvasModel canvas = panel.getCanvasModel();
        GraphObjectFrame left = canvas.getFrames().get(0);
        GraphElement leftMember = anyMemberOf(left);
        double memberX = leftMember.getGraphElementCenter().getX();

        ua.stetsenkoinna.graphpresentation.undoable_edits.CanvasLayoutSnapshot before =
                new ua.stetsenkoinna.graphpresentation.undoable_edits.CanvasLayoutSnapshot(canvas);
        panel.selectAll();
        panel.moveSelectionBy(120, 80);
        ua.stetsenkoinna.graphpresentation.undoable_edits.CanvasLayoutSnapshot after =
                new ua.stetsenkoinna.graphpresentation.undoable_edits.CanvasLayoutSnapshot(canvas);
        assertTrue("a drag that moved things must be worth an undo step", after.differsFrom(before));

        ua.stetsenkoinna.graphpresentation.undoable_edits.MoveCanvasItemsEdit edit =
                new ua.stetsenkoinna.graphpresentation.undoable_edits.MoveCanvasItemsEdit(
                        panel, before, after);
        edit.undo();

        assertEquals(100, left.getBounds().x);
        assertEquals(100, left.getBounds().y);
        assertEquals(memberX, leftMember.getGraphElementCenter().getX(), 0.001);
        assertSame(left, canvas.ownerOf(leftMember));

        edit.redo();
        assertEquals(220, left.getBounds().x);
        assertEquals(180, left.getBounds().y);
        assertEquals(memberX + 120, leftMember.getGraphElementCenter().getX(), 0.001);
    }

    @Test
    public void adragThatMovedNothingIsWorthNoUndoStep() {
        PetriNetsPanel panel = panelHoldingSomething();
        panel.addCanvasModel(twoLinkedObjects());
        GraphCanvasModel canvas = panel.getCanvasModel();

        ua.stetsenkoinna.graphpresentation.undoable_edits.CanvasLayoutSnapshot before =
                new ua.stetsenkoinna.graphpresentation.undoable_edits.CanvasLayoutSnapshot(canvas);
        ua.stetsenkoinna.graphpresentation.undoable_edits.CanvasLayoutSnapshot after =
                new ua.stetsenkoinna.graphpresentation.undoable_edits.CanvasLayoutSnapshot(canvas);

        org.junit.Assert.assertFalse(after.differsFrom(before));
    }
}

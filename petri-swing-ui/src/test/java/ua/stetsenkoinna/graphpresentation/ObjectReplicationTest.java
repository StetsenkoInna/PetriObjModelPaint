package ua.stetsenkoinna.graphpresentation;

import org.junit.Test;
import ua.stetsenkoinna.graphnet.GraphObjectFrame;
import ua.stetsenkoinna.graphnet.GraphObjectGroup;
import ua.stetsenkoinna.graphnet.GraphPetriPlace;
import ua.stetsenkoinna.graphnet.GraphPetriTransition;
import ua.stetsenkoinna.graphnet.GraphPlaceFusion;
import ua.stetsenkoinna.petriobj.PetriP;
import ua.stetsenkoinna.petriobj.PetriT;

import java.awt.Rectangle;
import java.awt.geom.Point2D;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/**
 * Stamping one Petri-object into a group, and spreading a shared place across it.
 *
 * <p>This is the technique's {@code multiply(net, lists, k)} and its connector-to-a-group,
 * {@code g.net.p_b = o.net.p_a ⟺ ∀o_i ∈ g: o_i.net.p_b = o.net.p_a} — the half of the
 * Petri-object approach the editor did not have. Describing a hundred like nodes once, rather
 * than drawing a hundred of them, is the reason the approach exists.
 *
 * <p>A group is an editing-time construct: it <em>is</em> its objects, so what these tests check
 * is that the objects and links it produces are the ordinary ones the rest of the editor, the
 * simulation and the file format already deal with.
 */
public class ObjectReplicationTest {

    private static int idCounter = 1;

    private PetriNetsPanel panel;

    private void freshPanel() {
        PetriP.initNext();
        PetriT.initNext();
        idCounter = 1;
        panel = new PetriNetsPanel(null, true);
    }

    private GraphPetriPlace placeAt(String name, int x, int y) {
        GraphPetriPlace place = new GraphPetriPlace(new PetriP(name, 0), idCounter++);
        place.setNewCoordinates(new Point2D.Double(x, y));
        panel.getGraphNet().getGraphPetriPlaceList().add(place);
        return place;
    }

    private GraphPetriTransition transitionAt(String name, int x, int y) {
        GraphPetriTransition transition =
                new GraphPetriTransition(new PetriT(name, 1.0), idCounter++);
        transition.setNewCoordinates(new Point2D.Double(x, y));
        panel.getGraphNet().getGraphPetriTransitionList().add(transition);
        return transition;
    }

    /** An object with two places and a transition of its own — enough to be worth stamping. */
    private GraphObjectFrame objectAt(String name, int x) {
        GraphObjectFrame frame = new GraphObjectFrame(name, new Rectangle(x, 0, 260, 300));
        panel.getCanvasModel().getFrames().add(frame);
        panel.getCanvasModel().claim(frame, placeAt(name + ".in", x + 40, 60));
        panel.getCanvasModel().claim(frame, transitionAt(name + ".t", x + 120, 60));
        panel.getCanvasModel().claim(frame, placeAt(name + ".out", x + 200, 60));
        return frame;
    }

    /** Stamps {@code frame} into a group of {@code count}, answering the count dialog for it. */
    private void replicate(GraphObjectFrame frame, int count) {
        invoke("replicateObjectInto", new Class<?>[]{GraphObjectFrame.class, int.class},
                frame, count);
    }

    private void replicateAcross(GraphPlaceFusion link, GraphObjectGroup group) {
        invoke("replicateLinkAcrossGroup",
                new Class<?>[]{GraphPlaceFusion.class, GraphObjectGroup.class}, link, group);
    }

    private void invoke(String name, Class<?>[] types, Object... args) {
        try {
            Method method = PetriNetsPanel.class.getDeclaredMethod(name, types);
            method.setAccessible(true);
            method.invoke(panel, args);
        } catch (InvocationTargetException failure) {
            throw new AssertionError(failure.getCause());
        } catch (ReflectiveOperationException broken) {
            throw new AssertionError(broken);
        }
    }

    // ------------------------------------------------------------------ stamping

    @Test
    public void replicatingStampsTheObjectTheRequestedNumberOfTimes() {
        freshPanel();
        GraphObjectFrame first = objectAt("Server", 0);

        replicate(first, 4);

        List<GraphObjectGroup> groups = panel.getCanvasModel().getGroups();
        assertEquals("one group", 1, groups.size());
        assertEquals("holding four objects", 4, groups.getFirst().size());
        assertEquals("all of them on the canvas", 4, panel.getCanvasModel().getFrames().size());
    }

    /**
     * The members are ordinary Petri-objects, each with its own net. A group that shared one net
     * between its members would not be a group of objects at all — every one of them would fire
     * the same transitions.
     */
    @Test
    public void everyMemberGetsItsOwnNet() {
        freshPanel();
        GraphObjectFrame first = objectAt("Server", 0);
        int placesBefore = panel.getGraphNet().getGraphPetriPlaceList().size();

        replicate(first, 3);

        assertEquals("three objects' worth of places",
                placesBefore * 3, panel.getGraphNet().getGraphPetriPlaceList().size());
        GraphObjectGroup group = panel.getCanvasModel().getGroups().getFirst();
        for (GraphObjectFrame member : group.getMembers()) {
            assertEquals("each member owns its own two places",
                    2, panel.getCanvasModel().placesOf(member).size());
        }
    }

    /** The members are numbered 1..n, and the group carries the name they share. */
    @Test
    public void theGroupIsNamedAndItsMembersNumbered() {
        freshPanel();
        GraphObjectFrame first = objectAt("Server", 0);

        replicate(first, 3);

        GraphObjectGroup group = panel.getCanvasModel().getGroups().getFirst();
        assertEquals("Server", group.getName());
        assertEquals("Server 1", group.getMembers().get(0).getName());
        assertEquals("Server 2", group.getMembers().get(1).getName());
        assertEquals("Server 3", group.getMembers().get(2).getName());
    }

    /** Stamped side by side rather than piled on one spot. */
    @Test
    public void theMembersAreLaidOutBesideEachOther() {
        freshPanel();
        GraphObjectFrame first = objectAt("Server", 0);

        replicate(first, 3);

        List<GraphObjectFrame> members = panel.getCanvasModel().getGroups().getFirst().getMembers();
        int firstX = members.get(0).getBounds().x;
        int secondX = members.get(1).getBounds().x;
        int thirdX = members.get(2).getBounds().x;
        assertTrue("the second stands clear of the first", secondX > firstX);
        assertTrue("and the third clear of the second", thirdX > secondX);
    }

    /** A whole replication is one gesture, so it takes one undo. */
    @Test
    public void aReplicationComesBackOnOneUndo() {
        freshPanel();
        GraphObjectFrame first = objectAt("Server", 0);
        javax.swing.undo.UndoManager undo = new javax.swing.undo.UndoManager();
        PetriNetsFrame.getUndoSupport().addUndoableEditListener(undo);

        replicate(first, 4);
        assertEquals(4, panel.getCanvasModel().getFrames().size());

        undo.undo();

        assertEquals("the stamped copies went together", 1,
                panel.getCanvasModel().getFrames().size());
    }

    // ------------------------------------------------------------------ groups and deletion

    /**
     * A group that has lost all but one member is dissolved. One object stamped from a template
     * is just an object; a group of one would draw a stack around it and offer to replicate a
     * connector across a group of one.
     */
    @Test
    public void aGroupDownToOneMemberIsDissolved() {
        freshPanel();
        GraphObjectFrame first = objectAt("Server", 0);
        replicate(first, 3);
        GraphObjectGroup group = panel.getCanvasModel().getGroups().getFirst();

        for (GraphObjectFrame member : List.copyOf(group.getMembers()).subList(1, 3)) {
            panel.getCanvasModel().getFrames().remove(member);
        }
        panel.getCanvasModel().removeDanglingGroupMembers();

        assertTrue("no group is left", panel.getCanvasModel().getGroups().isEmpty());
    }

    @Test
    public void aGroupForgetsAMemberThatHasGone() {
        freshPanel();
        GraphObjectFrame first = objectAt("Server", 0);
        replicate(first, 4);
        GraphObjectGroup group = panel.getCanvasModel().getGroups().getFirst();
        GraphObjectFrame doomed = group.getMembers().get(2);

        panel.getCanvasModel().getFrames().remove(doomed);
        panel.getCanvasModel().removeDanglingGroupMembers();

        assertEquals(3, group.size());
        assertTrue("and the rest stayed", group.contains(group.getMembers().getFirst()));
        assertNull("the removed one is in no group", panel.getCanvasModel().groupOf(doomed));
    }

    /**
     * The defect that made a group vanish the moment its document was opened.
     *
     * <p>Everything upstream was right - the file carried the group, the parser read it, the
     * canvas built it - and then the panel replaced its own canvas with the loaded one by
     * copying across the frames and the links, and left the groups behind. A collection added to
     * the model has to be added to every path that copies one, and this is the path a user
     * actually travels.
     */
    @Test
    public void openingADocumentKeepsItsGroups() {
        freshPanel();
        GraphObjectFrame server = objectAt("Server", 0);
        replicate(server, 3);

        ua.stetsenkoinna.graphnet.GraphCanvasModel loaded =
                ua.stetsenkoinna.graphnet.GraphCanvasModel.fromObjModel(
                        panel.getCanvasModel().toObjModel());
        assertEquals("the loaded canvas has it", 1, loaded.getGroups().size());

        PetriNetsPanel other = new PetriNetsPanel(null, true);
        other.setCanvasModel(loaded);

        assertEquals("and so does the panel it was put on", 1, other.getCanvasModel().getGroups().size());
        assertEquals(3, other.getCanvasModel().getGroups().getFirst().size());
    }

    /** A copied canvas keeps its groups too - the other path that duplicates one. */
    @Test
    public void copyingACanvasKeepsItsGroups() {
        freshPanel();
        GraphObjectFrame server = objectAt("Server", 0);
        replicate(server, 3);

        ua.stetsenkoinna.graphnet.GraphCanvasModel copy =
                new ua.stetsenkoinna.graphnet.GraphCanvasModel(panel.getCanvasModel());

        assertEquals(1, copy.getGroups().size());
        assertEquals(3, copy.getGroups().getFirst().size());
        assertNotSame("rebuilt around the copy's own frames, not the original's",
                panel.getCanvasModel().getGroups().getFirst().getMembers().getFirst(),
                copy.getGroups().getFirst().getMembers().getFirst());
    }

    /** Erasing a member with the Delete tool takes it out of its group. */
    @Test(timeout = 10000)
    public void erasingAMemberTakesItOutOfTheGroup() {
        freshPanel();
        GraphObjectFrame server = objectAt("Server", 0);
        replicate(server, 4);
        GraphObjectGroup group = panel.getCanvasModel().getGroups().getFirst();
        GraphObjectFrame doomed = group.getMembers().get(1);

        panel.setTool(CanvasTool.DELETE);
        java.awt.Rectangle bounds = doomed.getBounds();
        eraserClick(bounds.x + 6, bounds.y + 6);

        assertEquals("the object went", 3, panel.getCanvasModel().getFrames().size());
        assertEquals("and the group is down to three", 3,
                panel.getCanvasModel().getGroups().getFirst().size());
    }

    /** Erased down to one, the group dissolves rather than lingering as a group of one. */
    @Test(timeout = 10000)
    public void erasingDownToOneMemberDissolvesTheGroup() {
        freshPanel();
        GraphObjectFrame server = objectAt("Server", 0);
        replicate(server, 3);
        GraphObjectGroup group = panel.getCanvasModel().getGroups().getFirst();

        panel.setTool(CanvasTool.DELETE);
        for (GraphObjectFrame member : List.copyOf(group.getMembers()).subList(1, 3)) {
            java.awt.Rectangle bounds = member.getBounds();
            eraserClick(bounds.x + 6, bounds.y + 6);
        }

        assertTrue("no group of one is left", panel.getCanvasModel().getGroups().isEmpty());
    }

    private void eraserClick(int x, int y) {
        PetriNetsPanel.MouseHandler handler = null;
        for (java.awt.event.MouseListener listener : panel.getMouseListeners()) {
            if (listener instanceof PetriNetsPanel.MouseHandler found) {
                handler = found;
            }
        }
        assertNotNull(handler);
        handler.mousePressed(new java.awt.event.MouseEvent(panel,
                java.awt.event.MouseEvent.MOUSE_PRESSED, System.currentTimeMillis(), 0,
                x, y, 1, false, java.awt.event.MouseEvent.BUTTON1));
        handler.mouseReleased(new java.awt.event.MouseEvent(panel,
                java.awt.event.MouseEvent.MOUSE_RELEASED, System.currentTimeMillis(), 0,
                x, y, 1, false, java.awt.event.MouseEvent.BUTTON1));
        // The cleanup rides on the same pass that redraws, so ask for one.
        panel.getCanvasModel().syncFusions();
    }

    // ------------------------------------------------------------------ the group as one thing

    /** A point on the band: inside it, clear of every member. */
    private java.awt.Point onTheBand(GraphObjectGroup group) {
        java.awt.Rectangle first = group.getMembers().getFirst().getBounds();
        // The margin the band is drawn with is 16; half of it sits clear of any frame.
        return new java.awt.Point(first.x - 8, first.y + first.height / 2);
    }

    private void pressOn(java.awt.Point point) {
        for (java.awt.event.MouseListener listener : panel.getMouseListeners()) {
            if (listener instanceof PetriNetsPanel.MouseHandler handler) {
                handler.mousePressed(new java.awt.event.MouseEvent(panel,
                        java.awt.event.MouseEvent.MOUSE_PRESSED, System.currentTimeMillis(), 0,
                        point.x, point.y, 1, false, java.awt.event.MouseEvent.BUTTON1));
            }
        }
    }

    private void dragTo(java.awt.Point point) {
        panel.getMouseMotionListeners()[0].mouseDragged(new java.awt.event.MouseEvent(panel,
                java.awt.event.MouseEvent.MOUSE_DRAGGED, System.currentTimeMillis(), 0,
                point.x, point.y, 1, false, java.awt.event.MouseEvent.BUTTON1));
    }

    /** Clicking the band takes the whole group, not one object of it. */
    @Test
    public void clickingTheBandSelectsEveryMember() {
        freshPanel();
        GraphObjectFrame server = objectAt("Server", 0);
        replicate(server, 3);
        GraphObjectGroup group = panel.getCanvasModel().getGroups().getFirst();

        pressOn(onTheBand(group));

        for (GraphObjectFrame member : group.getMembers()) {
            assertTrue(member.getName() + " is selected", panel.getSelection().contains(member));
        }
    }

    /**
     * The selection has to survive the button coming back up.
     *
     * <p>A plain click is a press, a release and a click event, and the last of those treats a
     * point that hit no frame and no element as empty canvas - which the band is, geometrically.
     * Selecting on the press and clearing on the click meant the group flashed selected and went
     * back to normal before the user had let go.
     */
    @Test
    public void theSelectionSurvivesAPlainClickOnTheBand() {
        freshPanel();
        GraphObjectFrame server = objectAt("Server", 0);
        replicate(server, 3);
        GraphObjectGroup group = panel.getCanvasModel().getGroups().getFirst();

        java.awt.Point point = onTheBand(group);
        fullClickOn(point);

        for (GraphObjectFrame member : group.getMembers()) {
            assertTrue(member.getName() + " is still selected after the release",
                    panel.getSelection().contains(member));
        }
    }

    /** Press, release and the click event that follows - what a real click actually is. */
    private void fullClickOn(java.awt.Point point) {
        for (java.awt.event.MouseListener listener : panel.getMouseListeners()) {
            if (listener instanceof PetriNetsPanel.MouseHandler handler) {
                handler.mousePressed(mouseEvent(java.awt.event.MouseEvent.MOUSE_PRESSED, point));
                handler.mouseReleased(mouseEvent(java.awt.event.MouseEvent.MOUSE_RELEASED, point));
                handler.mouseClicked(mouseEvent(java.awt.event.MouseEvent.MOUSE_CLICKED, point));
            }
        }
    }

    private java.awt.event.MouseEvent mouseEvent(int id, java.awt.Point point) {
        return new java.awt.event.MouseEvent(panel, id, System.currentTimeMillis(), 0,
                point.x, point.y, 1, false, java.awt.event.MouseEvent.BUTTON1);
    }

    /**
     * Delete takes the whole group - frames and nets - and asks nothing.
     *
     * <p>It used to put up a confirmation, and a second one about the elements after it, so a
     * user who answered one and not the other was left with a model half taken apart. The
     * eraser already removes an object whole without asking; the key now means the same thing.
     *
     * <p>The timeout is the assertion that no dialog appears: a modal one here would hang the
     * test rather than fail it.
     */
    @Test(timeout = 10000)
    public void deleteRemovesTheWholeGroupWithoutAsking() {
        freshPanel();
        GraphObjectFrame server = objectAt("Server", 0);
        replicate(server, 3);
        GraphObjectGroup group = panel.getCanvasModel().getGroups().getFirst();

        pressOn(onTheBand(group));
        panel.deleteSelection();

        assertEquals("every frame went", 0, panel.getCanvasModel().getFrames().size());
        assertEquals("and the nets inside them", 0,
                panel.getGraphNet().getGraphPetriPlaceList().size());
        panel.getCanvasModel().syncFusions();
        assertTrue("and the group with them", panel.getCanvasModel().getGroups().isEmpty());
    }

    /**
     * The same, after having clicked an element first.
     *
     * <p>This is the case the previous test missed by starting from a clean panel. Delete looks
     * at what was last clicked on its own before it looks at the selection, so an element
     * clicked earlier and never cleared turned "delete this group" into "delete that element" -
     * the nets went, every frame stayed, and undo brought back only what had gone.
     */
    @Test(timeout = 10000)
    public void deletingAGroupWorksEvenAfterAnElementWasClickedFirst() {
        freshPanel();
        GraphObjectFrame server = objectAt("Server", 0);
        replicate(server, 3);
        GraphObjectGroup group = panel.getCanvasModel().getGroups().getFirst();

        // A click on a place of the first member, the way any real session starts.
        GraphPetriPlace place = panel.getCanvasModel().placesOf(group.getMembers().getFirst()).getFirst();
        java.awt.Point at = new java.awt.Point(
                (int) place.getGraphElementCenter().getX(),
                (int) place.getGraphElementCenter().getY());
        fullClickOn(at);

        fullClickOn(onTheBand(group));
        panel.deleteSelection();

        assertEquals("every frame went", 0, panel.getCanvasModel().getFrames().size());
        assertEquals("and every net with them", 0,
                panel.getGraphNet().getGraphPetriPlaceList().size());
    }

    /**
     * What a real session does: pick the group up, move it, let go, then press Delete.
     */
    @Test(timeout = 10000)
    public void deletingAGroupWorksAfterItHasBeenDragged() {
        freshPanel();
        GraphObjectFrame server = objectAt("Server", 0);
        replicate(server, 3);
        GraphObjectGroup group = panel.getCanvasModel().getGroups().getFirst();

        java.awt.Point from = onTheBand(group);
        pressOn(from);
        dragTo(new java.awt.Point(from.x + 40, from.y + 30));
        releaseAt(new java.awt.Point(from.x + 40, from.y + 30));

        assertEquals("the group is still what is selected",
                3, panel.getSelection().allFrames().size());

        panel.deleteSelection();

        assertEquals("every frame went", 0, panel.getCanvasModel().getFrames().size());
        assertEquals("and every net with them", 0,
                panel.getGraphNet().getGraphPetriPlaceList().size());
    }

    private void releaseAt(java.awt.Point point) {
        for (java.awt.event.MouseListener listener : panel.getMouseListeners()) {
            if (listener instanceof PetriNetsPanel.MouseHandler handler) {
                handler.mouseReleased(mouseEvent(java.awt.event.MouseEvent.MOUSE_RELEASED, point));
            }
        }
    }

    /**
     * The reported case, on the document it was reported against.
     *
     * <p>The synthetic panels above have no links between their objects; this one does, which is
     * the difference worth reproducing rather than assuming away.
     */
    @Test(timeout = 15000)
    public void deletingAGroupOfLinkedObjectsTakesTheFramesToo() throws Exception {
        java.io.File demo = new java.io.File("../petri-model/target/demo/release-2.3.0-demo.pnml");
        org.junit.Assume.assumeTrue("demo document present", demo.isFile());

        PetriP.initNext();
        PetriT.initNext();
        panel = new PetriNetsPanel(null, true);
        panel.setCanvasModel(ua.stetsenkoinna.graphnet.GraphCanvasModel.fromObjModel(
                new ua.stetsenkoinna.pnml.PnmlModelParser().parse(demo)));

        assertEquals("the document's group is here", 1, panel.getCanvasModel().getGroups().size());
        GraphObjectGroup group = panel.getCanvasModel().getGroups().getFirst();
        int framesBefore = panel.getCanvasModel().getFrames().size();

        fullClickOn(onTheBand(group));
        assertEquals("the whole group is selected", 4, panel.getSelection().allFrames().size());

        panel.deleteSelection();

        assertEquals("the four members went, the dispatcher stayed",
                framesBefore - 4, panel.getCanvasModel().getFrames().size());
        for (GraphObjectFrame member : group.getMembers()) {
            assertTrue("no member is left on the canvas",
                    !panel.getCanvasModel().getFrames().contains(member));
        }
    }

    /** An empty spot on a member's own floor - inside its frame, on no element. */
    private java.awt.Point emptyFloorOf(GraphObjectFrame frame) {
        java.awt.Rectangle bounds = frame.getBounds();
        return new java.awt.Point(bounds.x + 8, bounds.y + bounds.height - 10);
    }

    /**
     * Clicking a member's own floor and pressing Delete: the reported gesture.
     */
    @Test(timeout = 10000)
    public void clickingAMembersFloorAndDeletingTakesTheWholeObject() {
        freshPanel();
        GraphObjectFrame server = objectAt("Server", 0);
        replicate(server, 3);
        GraphObjectGroup group = panel.getCanvasModel().getGroups().getFirst();
        GraphObjectFrame member = group.getMembers().getFirst();
        int placesBefore = panel.getGraphNet().getGraphPetriPlaceList().size();

        fullClickOn(emptyFloorOf(member));
        panel.deleteSelection();

        assertTrue("the frame went too, not only its net",
                !panel.getCanvasModel().getFrames().contains(member));
        assertTrue("and its net went", placesBefore
                > panel.getGraphNet().getGraphPetriPlaceList().size());
    }

    /** The same spot, with the eraser: the other way it was reported. */
    @Test(timeout = 10000)
    public void erasingOnAMembersFloorTakesTheWholeObject() {
        freshPanel();
        GraphObjectFrame server = objectAt("Server", 0);
        replicate(server, 3);
        GraphObjectGroup group = panel.getCanvasModel().getGroups().getFirst();
        GraphObjectFrame member = group.getMembers().getFirst();

        panel.setTool(CanvasTool.DELETE);
        java.awt.Point spot = emptyFloorOf(member);
        eraserClick(spot.x, spot.y);

        assertTrue("the frame went too", !panel.getCanvasModel().getFrames().contains(member));
    }

    /** Loads the demo document onto a fresh panel, or skips when it has not been generated. */
    private GraphObjectGroup loadDemo() throws Exception {
        java.io.File demo = new java.io.File("../petri-model/target/demo/release-2.3.0-demo.pnml");
        org.junit.Assume.assumeTrue("demo document present", demo.isFile());
        PetriP.initNext();
        PetriT.initNext();
        panel = new PetriNetsPanel(null, true);
        panel.setCanvasModel(ua.stetsenkoinna.graphnet.GraphCanvasModel.fromObjModel(
                new ua.stetsenkoinna.pnml.PnmlModelParser().parse(demo)));
        return panel.getCanvasModel().getGroups().getFirst();
    }

    /**
     * The reported gesture on the reported document: a click on a linked member's own floor,
     * then Delete. Links to another object are the one thing the synthetic panels above do not
     * have, and they are what a member of a real group always has.
     */
    @Test(timeout = 15000)
    public void deletingALinkedMemberFromItsFloorTakesTheFrame() throws Exception {
        GraphObjectGroup group = loadDemo();
        GraphObjectFrame member = group.getMembers().getFirst();

        fullClickOn(emptyFloorOf(member));
        panel.deleteSelection();

        assertTrue("the frame went, not only its net",
                !panel.getCanvasModel().getFrames().contains(member));
    }

    /** And with the eraser, on the same document. */
    @Test(timeout = 15000)
    public void erasingALinkedMemberTakesTheFrame() throws Exception {
        GraphObjectGroup group = loadDemo();
        GraphObjectFrame member = group.getMembers().getFirst();

        panel.setTool(CanvasTool.DELETE);
        java.awt.Point spot = emptyFloorOf(member);
        eraserClick(spot.x, spot.y);

        assertTrue("the frame went", !panel.getCanvasModel().getFrames().contains(member));
    }

    /** And it all comes back on one undo, however many objects it was. */
    @Test(timeout = 10000)
    public void deletingAGroupComesBackOnOneUndo() {
        freshPanel();
        GraphObjectFrame server = objectAt("Server", 0);
        replicate(server, 3);
        GraphObjectGroup group = panel.getCanvasModel().getGroups().getFirst();
        javax.swing.undo.UndoManager undo = new javax.swing.undo.UndoManager();
        PetriNetsFrame.getUndoSupport().addUndoableEditListener(undo);

        pressOn(onTheBand(group));
        panel.deleteSelection();
        assertEquals(0, panel.getCanvasModel().getFrames().size());

        undo.undo();

        assertEquals("all three objects came back together",
                3, panel.getCanvasModel().getFrames().size());
    }

    /**
     * The drawn line itself has to be clickable.
     *
     * <p>A stroked outline straddles the rectangle it comes from, so half of what the user aims
     * at lies outside it. Testing the bare rectangle made the band's own contour the one part of
     * it that did not answer - which is exactly the part anyone aims at.
     */
    @Test
    public void clickingTheContourSelectsTheGroupToo() {
        freshPanel();
        GraphObjectFrame server = objectAt("Server", 0);
        replicate(server, 3);
        GraphObjectGroup group = panel.getCanvasModel().getGroups().getFirst();
        java.awt.Rectangle first = group.getMembers().getFirst().getBounds();

        // Two units outside the band's own edge: on the line as drawn, off the rectangle.
        pressOn(new java.awt.Point(first.x - 18, first.y + first.height / 2));

        assertTrue("the group is selected from its contour",
                panel.getSelection().contains(group.getMembers().getFirst()));
    }

    /** The band's rectangle as the drawing would compute it, right now. */
    private java.awt.Rectangle bandBoundsOf(GraphObjectGroup group) {
        try {
            Method method = PetriNetsPanel.class.getDeclaredMethod(
                    "groupBandBounds", GraphObjectGroup.class);
            method.setAccessible(true);
            return (java.awt.Rectangle) method.invoke(panel, group);
        } catch (InvocationTargetException failure) {
            throw new AssertionError(failure.getCause());
        } catch (ReflectiveOperationException broken) {
            throw new AssertionError(broken);
        }
    }

    /**
     * The band has to stop being drawn the moment its objects are gone, not the next time
     * something happens to tidy the group up.
     *
     * <p>This is the defect the model tests could not see. Deleting the group removed every
     * frame correctly, and the canvas went on drawing a band around them: the check that decides
     * what to enclose asked whether a frame would be drawn here, which is a question about
     * nesting and visibility rather than about existence. The band stayed on screen until a drag
     * or some other event ran the cleanup - so nudging the objects made it disappear, which is
     * exactly how it was reported.
     */
    @Test(timeout = 10000)
    public void theBandStopsBeingDrawnAsSoonAsItsObjectsAreGone() {
        freshPanel();
        GraphObjectFrame server = objectAt("Server", 0);
        replicate(server, 3);
        GraphObjectGroup group = panel.getCanvasModel().getGroups().getFirst();
        assertNotNull("a band while the objects are there", bandBoundsOf(group));

        fullClickOn(onTheBand(group));
        panel.deleteSelection();

        assertNull("and none once they are not - without anything else having to happen",
                bandBoundsOf(group));
    }

    /** One member gone: the band shrinks to what is left rather than keeping a ghost inside. */
    @Test(timeout = 10000)
    public void theBandShrinksWhenAMemberIsErased() {
        freshPanel();
        GraphObjectFrame server = objectAt("Server", 0);
        replicate(server, 3);
        GraphObjectGroup group = panel.getCanvasModel().getGroups().getFirst();
        int wideBefore = bandBoundsOf(group).width;

        GraphObjectFrame last = group.getMembers().getLast();
        panel.setTool(CanvasTool.DELETE);
        java.awt.Point spot = emptyFloorOf(last);
        eraserClick(spot.x, spot.y);

        assertTrue("the band no longer reaches where the erased object stood",
                bandBoundsOf(group).width < wideBefore);
    }

    /** Dragging the band moves every member by the same amount. */
    @Test
    public void draggingTheBandMovesTheWholeGroup() {
        freshPanel();
        GraphObjectFrame server = objectAt("Server", 0);
        replicate(server, 3);
        GraphObjectGroup group = panel.getCanvasModel().getGroups().getFirst();
        List<Integer> before = group.getMembers().stream().map(m -> m.getBounds().x).toList();

        java.awt.Point from = onTheBand(group);
        pressOn(from);
        dragTo(new java.awt.Point(from.x + 60, from.y + 40));

        List<Integer> after = group.getMembers().stream().map(m -> m.getBounds().x).toList();
        for (int i = 0; i < before.size(); i++) {
            assertEquals("member " + i + " moved with the rest",
                    before.get(i) + 60, (int) after.get(i));
        }
    }

    /** A click on a member is still that member's - the group does not swallow it. */
    @Test
    public void clickingOneMemberStillSelectsOnlyThatMember() {
        freshPanel();
        GraphObjectFrame server = objectAt("Server", 0);
        replicate(server, 3);
        GraphObjectGroup group = panel.getCanvasModel().getGroups().getFirst();
        GraphObjectFrame second = group.getMembers().get(1);

        java.awt.Rectangle bounds = second.getBounds();
        pressOn(new java.awt.Point(bounds.x + 6, bounds.y + 6));

        assertTrue(panel.getSelection().contains(second));
        assertTrue("and not the one beside it",
                !panel.getSelection().contains(group.getMembers().getFirst()));
    }

    // ------------------------------------------------------------------ connector to a group

    /**
     * The heart of it: one shared place, declared once against one member, given to every member
     * of the group. This is what turns "a hundred like nodes" from a hundred drawing actions
     * into one.
     */
    @Test
    public void aLinkToOneMemberIsReplicatedAcrossTheWholeGroup() {
        freshPanel();
        GraphObjectFrame server = objectAt("Server", 0);
        replicate(server, 4);
        GraphObjectGroup group = panel.getCanvasModel().getGroups().getFirst();
        GraphObjectFrame hub = objectAt("Hub", 2000);

        // The hub's place is the source; the group member's is the copy - which is the only way
        // round this can work, and the way the technique states it.
        GraphPetriPlace hubPlace = panel.getCanvasModel().placesOf(hub).getFirst();
        GraphPetriPlace memberPlace =
                panel.getCanvasModel().placesOf(group.getMembers().getFirst()).getFirst();
        GraphPlaceFusion link = panel.getCanvasModel().joinPlaces(hubPlace, memberPlace);

        replicateAcross(link, group);

        assertEquals("one link per member", 4, panel.getCanvasModel().getFusions().size());
        for (GraphObjectFrame member : group.getMembers()) {
            GraphPetriPlace place = panel.getCanvasModel().placesOf(member).getFirst();
            GraphPlaceFusion its = panel.getCanvasModel().sourceFusionOf(place);
            assertNotNull(member.getName() + " shares the place", its);
            assertSame("and shares it with the hub", hubPlace, its.getMaster());
        }
    }

    /** Replicating twice finishes the job rather than refusing it. */
    @Test
    public void replicatingAcrossAGroupTwiceChangesNothingTheSecondTime() {
        freshPanel();
        GraphObjectFrame server = objectAt("Server", 0);
        replicate(server, 3);
        GraphObjectGroup group = panel.getCanvasModel().getGroups().getFirst();
        GraphObjectFrame hub = objectAt("Hub", 2000);
        GraphPetriPlace hubPlace = panel.getCanvasModel().placesOf(hub).getFirst();
        GraphPlaceFusion link = panel.getCanvasModel().joinPlaces(hubPlace,
                panel.getCanvasModel().placesOf(group.getMembers().getFirst()).getFirst());

        replicateAcross(link, group);
        int after = panel.getCanvasModel().getFusions().size();
        replicateAcross(link, group);

        assertEquals("nothing was added the second time",
                after, panel.getCanvasModel().getFusions().size());
    }

    /**
     * The links a replication makes are the ordinary ones, so everything already built on them
     * keeps working — here, that they form one connector per pair of objects.
     */
    @Test
    public void theReplicatedLinksAreOrdinaryLinks() {
        freshPanel();
        GraphObjectFrame server = objectAt("Server", 0);
        replicate(server, 3);
        GraphObjectGroup group = panel.getCanvasModel().getGroups().getFirst();
        GraphObjectFrame hub = objectAt("Hub", 2000);
        GraphPetriPlace hubPlace = panel.getCanvasModel().placesOf(hub).getFirst();
        GraphPlaceFusion link = panel.getCanvasModel().joinPlaces(hubPlace,
                panel.getCanvasModel().placesOf(group.getMembers().getFirst()).getFirst());

        replicateAcross(link, group);

        assertEquals("the hub joins each member by its own connector",
                3, panel.getCanvasModel().connectors().size());
        for (GraphPlaceFusion made : panel.getCanvasModel().getFusions()) {
            assertEquals("each connector holds the one place",
                    1, panel.getCanvasModel().connectorOf(made).size());
        }
    }
}

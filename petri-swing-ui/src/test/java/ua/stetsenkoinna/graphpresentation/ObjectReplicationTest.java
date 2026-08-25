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

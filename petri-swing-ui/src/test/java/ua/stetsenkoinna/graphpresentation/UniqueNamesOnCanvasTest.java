package ua.stetsenkoinna.graphpresentation;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import java.awt.Point;
import java.awt.Rectangle;
import java.awt.geom.Point2D;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.swing.event.UndoableEditListener;
import javax.swing.undo.UndoableEdit;
import org.junit.Before;
import org.junit.Test;
import ua.stetsenkoinna.graphnet.GraphArcFactory;
import ua.stetsenkoinna.graphnet.GraphElement;
import ua.stetsenkoinna.graphnet.GraphObjectFrame;
import ua.stetsenkoinna.graphnet.GraphPetriNet;
import ua.stetsenkoinna.graphnet.GraphPetriPlace;
import ua.stetsenkoinna.graphnet.GraphPetriTransition;
import ua.stetsenkoinna.graphnet.GraphPlaceFusion;
import ua.stetsenkoinna.graphnet.NetTemplateRef;
import ua.stetsenkoinna.petriobj.PetriP;
import ua.stetsenkoinna.petriobj.PetriT;

/**
 * Whatever lands on the canvas by copying or stamping gets names nothing else uses, so a model
 * never ends up with two places called P1 that the statistics, the protocol and the PNML file
 * cannot tell apart. A shared place is the one exception: its halves are one place with one
 * name, and no one outside the pair may use it.
 */
public class UniqueNamesOnCanvasTest {

    // Far above anything the panel hands out: the net deletes by numeric id, so a test element
    // sharing an id with a copy the panel made would be deleted in the copy's place.
    private static int ids = 100_000;
    private PetriNetsPanel panel;

    @Before
    public void freshPanel() {
        PetriP.initNext();
        PetriT.initNext();
        ids = 100_000;
        panel = new PetriNetsPanel(null, true);
    }

    private GraphPetriPlace placeAt(String name, int x, int y) {
        GraphPetriPlace place = new GraphPetriPlace(new PetriP(name, 1), ids++);
        place.setNewCoordinates(new Point2D.Double(x, y));
        panel.getGraphNet().getGraphPetriPlaceList().add(place);
        return place;
    }

    private GraphPetriTransition transitionAt(String name, int x, int y) {
        GraphPetriTransition transition = new GraphPetriTransition(new PetriT(name, 1.0), ids++);
        transition.setNewCoordinates(new Point2D.Double(x, y));
        panel.getGraphNet().getGraphPetriTransitionList().add(transition);
        return transition;
    }

    /** An object named {@code name} holding a place, a transition and a place: P1 -> T1 -> P2. */
    private GraphObjectFrame objectAt(String name, int x) {
        return objectAt(name, x, "");
    }

    /** Same, with every element name prefixed, for setups whose originals must not clash. */
    private GraphObjectFrame objectAt(String name, int x, String prefix) {
        GraphObjectFrame frame = new GraphObjectFrame(name, new Rectangle(x, 0, 260, 200));
        panel.getCanvasModel().getFrames().add(frame);
        GraphPetriPlace in = placeAt(prefix + "P1", x + 40, 60);
        GraphPetriTransition work = transitionAt(prefix + "T1", x + 120, 60);
        GraphPetriPlace out = placeAt(prefix + "P2", x + 200, 60);
        panel.getGraphNet().getGraphArcInList().add(GraphArcFactory.inArc(in, work, 1, false));
        panel.getGraphNet().getGraphArcOutList().add(GraphArcFactory.outArc(work, out, 1));
        for (GraphElement element : List.of(in, work, out)) {
            panel.getCanvasModel().claim(frame, element);
        }
        return frame;
    }

    /** A freshly built library-like net with the names every library net starts with. */
    private GraphPetriNet libraryLikeNet() {
        GraphPetriPlace place = new GraphPetriPlace(new PetriP("P1", 0), ids++);
        place.setNewCoordinates(new Point2D.Double(700, 400));
        GraphPetriTransition transition = new GraphPetriTransition(new PetriT("T1", 1.0), ids++);
        transition.setNewCoordinates(new Point2D.Double(800, 400));
        GraphPetriNet net = new GraphPetriNet();
        net.getGraphPetriPlaceList().add(place);
        net.getGraphPetriTransitionList().add(transition);
        net.getGraphArcInList().add(GraphArcFactory.inArc(place, transition, 1, false));
        return net;
    }

    private Object invoke(String name, Class<?>[] types, Object... args) {
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

    private void stamp(GraphPetriNet net, String objectName) {
        invoke("placeGraphNet", new Class<?>[]{GraphPetriNet.class, String.class, NetTemplateRef.class},
                net, objectName, null);
    }

    /** Fails with the repeated names, if any name on the canvas belongs to two things. */
    private void assertNoRepeatedNames() {
        Map<String, Integer> seen = new HashMap<>();
        for (GraphPetriPlace place : panel.getGraphNet().getGraphPetriPlaceList()) {
            seen.merge(place.getName(), 1, Integer::sum);
        }
        for (GraphPetriTransition transition : panel.getGraphNet().getGraphPetriTransitionList()) {
            seen.merge(transition.getName(), 1, Integer::sum);
        }
        for (GraphObjectFrame frame : panel.getCanvasModel().getFrames()) {
            seen.merge(frame.getName(), 1, Integer::sum);
        }
        List<String> repeated = new ArrayList<>();
        seen.forEach((name, count) -> { if (count > 1) repeated.add(name + " x" + count); });
        assertTrue("repeated names: " + repeated, repeated.isEmpty());
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

    @Test
    public void duplicatingAnObjectNamesItsCopiesFreshly() {
        GraphObjectFrame original = objectAt("Server", 0);

        GraphObjectFrame copy = panel.duplicateObject(original, "Server copy");

        assertNoRepeatedNames();
        List<String> copied = new ArrayList<>();
        for (GraphElement element : panel.getCanvasModel().membersOfSubtree(copy)) {
            copied.add(element.getName());
        }
        assertEquals(java.util.Set.of("P1_2", "T1_2", "P2_2"), new java.util.HashSet<>(copied));
        List<String> kept = new ArrayList<>();
        for (GraphElement element : panel.getCanvasModel().membersOfSubtree(original)) {
            kept.add(element.getName());
        }
        assertEquals("the original keeps its names", java.util.Set.of("P1", "T1", "P2"), new java.util.HashSet<>(kept));
    }

    @Test
    public void duplicatingTwiceKeepsCounting() {
        GraphObjectFrame original = objectAt("Server", 0);
        panel.duplicateObject(original, "Server copy");
        panel.duplicateObject(original, "Server copy");

        assertNoRepeatedNames();
        assertEquals("Server copy 2", panel.getCanvasModel().getFrames().get(2).getName());
    }

    @Test
    public void duplicatingTheSameObjectTwiceDoesNotStackTheCopies() {
        GraphObjectFrame original = objectAt("Server", 0);
        GraphObjectFrame first = panel.duplicateObject(original, "Server copy");
        GraphObjectFrame second = panel.duplicateObject(original, "Server copy");

        assertTrue("the second copy goes to a free spot, not onto the first",
                !first.getBounds().intersects(second.getBounds()));
        assertTrue(!original.getBounds().intersects(second.getBounds()));
        for (GraphElement element : panel.getCanvasModel().membersOfSubtree(second)) {
            assertTrue(element.getName() + " sits inside its own copy",
                    second.getBounds().contains(element.getGraphElementCenter()));
        }
    }

    @Test
    public void pastedElementsTakeFreeNames() {
        GraphPetriPlace place = placeAt("Queue", 100, 100);
        GraphPetriTransition transition = transitionAt("Serve", 200, 100);
        panel.getSelection().elements().add(place);
        panel.getSelection().elements().add(transition);

        panel.copySelection();
        panel.pasteClipboard();
        panel.pasteClipboard();

        assertNoRepeatedNames();
        assertEquals("Queue", place.getName());
        assertEquals(6, panel.getGraphNet().getGraphPetriPlaceList().size()
                + panel.getGraphNet().getGraphPetriTransitionList().size());
    }

    @Test
    public void aPastedObjectAndItsNetGetFreeNames() {
        GraphObjectFrame object = objectAt("Server", 0);
        panel.getSelection().setSelectedFrame(object);

        panel.copySelection();
        panel.pasteClipboard();

        assertNoRepeatedNames();
    }

    @Test
    public void replicatingIntoAGroupGivesEveryMemberItsOwnNames() {
        GraphObjectFrame original = objectAt("Server", 0);

        invoke("replicateObjectInto", new Class<?>[]{GraphObjectFrame.class, int.class}, original, 4);

        assertEquals(4, panel.getCanvasModel().getFrames().size());
        assertNoRepeatedNames();
    }

    @Test
    public void stampingTheSameSavedNetTwiceDoesNotRepeatItsNames() {
        stamp(libraryLikeNet(), "Generator");
        stamp(libraryLikeNet(), "Generator");

        assertNoRepeatedNames();
        assertEquals("Generator", panel.getCanvasModel().getFrames().get(0).getName());
        assertEquals("Generator 2", panel.getCanvasModel().getFrames().get(1).getName());
    }

    @Test
    public void aStampDodgesNamesDrawnByHand() {
        placeAt("P1", 0, 0);
        transitionAt("T1", 100, 0);

        stamp(libraryLikeNet(), "Generator");

        assertNoRepeatedNames();
    }

    @Test
    public void undoAndRedoOfADuplicateKeepTheNewNames() {
        GraphObjectFrame original = objectAt("Server", 0);
        List<UndoableEdit> posted = editsPostedBy(() -> panel.duplicateObject(original, "Server copy"));

        posted.getFirst().undo();
        assertEquals(3, panel.getGraphNet().getGraphPetriPlaceList().size()
                + panel.getGraphNet().getGraphPetriTransitionList().size());
        posted.getFirst().redo();

        assertNoRepeatedNames();
    }

    @Test
    public void theAddToolsHandOutTheFirstFreeDefaultName() {
        placeAt("P1", 0, 0);
        placeAt("P2", 50, 0);
        transitionAt("T1", 100, 0);

        invoke("addElementAt", new Class<?>[]{CanvasTool.class, Point.class}, CanvasTool.ADD_PLACE, new Point(300, 300));
        invoke("addElementAt", new Class<?>[]{CanvasTool.class, Point.class}, CanvasTool.ADD_TRANSITION, new Point(400, 300));

        List<GraphPetriPlace> places = panel.getGraphNet().getGraphPetriPlaceList();
        List<GraphPetriTransition> transitions = panel.getGraphNet().getGraphPetriTransitionList();
        assertEquals("P3", places.getLast().getName());
        assertEquals("T2", transitions.getLast().getName());
    }

    @Test
    public void joiningPlacesGivesTheAliasTheMastersNameAndUndoGivesItsOwnBack() {
        GraphObjectFrame left = objectAt("Left", 0);
        GraphObjectFrame right = objectAt("Right", 400);
        GraphPetriPlace master = (GraphPetriPlace) panel.getCanvasModel().membersOfSubtree(left).getFirst();
        GraphPetriPlace alias = (GraphPetriPlace) panel.getCanvasModel().membersOfSubtree(right).getFirst();
        alias.getPetriPlace().setName("Buffer");

        List<UndoableEdit> posted = editsPostedBy(() -> invoke("linkPortToElement",
                new Class<?>[]{GraphElement.class, GraphElement.class}, master, alias));

        assertEquals("one shared place, one name", master.getName(), alias.getName());
        posted.getFirst().undo();
        assertEquals("Buffer", alias.getName());
        posted.getFirst().redo();
        assertEquals(master.getName(), alias.getName());
    }

    @Test
    public void duplicatingANestWithASharedPlaceKeepsThePairTogetherUnderANewName() {
        GraphObjectFrame nest = new GraphObjectFrame("Nest", new Rectangle(-20, -20, 900, 400));
        panel.getCanvasModel().getFrames().add(nest);
        GraphObjectFrame left = objectAt("Left", 0, "L");
        GraphObjectFrame right = objectAt("Right", 400, "R");
        panel.getCanvasModel().nest(left, nest);
        panel.getCanvasModel().nest(right, nest);
        GraphPetriPlace master = (GraphPetriPlace) panel.getCanvasModel().membersOfSubtree(left).getFirst();
        GraphPetriPlace alias = (GraphPetriPlace) panel.getCanvasModel().membersOfSubtree(right).getFirst();
        invoke("linkPortToElement", new Class<?>[]{GraphElement.class, GraphElement.class}, master, alias);
        int fusionsBefore = panel.getCanvasModel().getFusions().size();

        panel.duplicateObject(nest, "Nest copy");

        assertEquals("the copy brought its own shared place", fusionsBefore + 1,
                panel.getCanvasModel().getFusions().size());
        GraphPlaceFusion copied = panel.getCanvasModel().getFusions().getLast();
        assertEquals("the copied pair shares one name", copied.getMaster().getName(), copied.getJoined().getName());
        assertNotEquals("which is not the original pair's name", master.getName(), copied.getMaster().getName());
        // Apart from each pair, nothing repeats.
        Map<String, Integer> seen = new HashMap<>();
        for (GraphPetriPlace place : panel.getGraphNet().getGraphPetriPlaceList()) {
            if (place != alias && place != copied.getJoined()) seen.merge(place.getName(), 1, Integer::sum);
        }
        for (GraphPetriTransition transition : panel.getGraphNet().getGraphPetriTransitionList()) {
            seen.merge(transition.getName(), 1, Integer::sum);
        }
        seen.forEach((name, count) -> assertEquals(name, 1, count.intValue()));
    }
}

package ua.stetsenkoinna.graphpresentation;

import org.junit.Test;
import ua.stetsenkoinna.graphnet.GraphElement;
import ua.stetsenkoinna.graphnet.GraphObjectFrame;
import ua.stetsenkoinna.graphnet.GraphPetriPlace;
import ua.stetsenkoinna.graphnet.GraphPetriTransition;
import ua.stetsenkoinna.petriobj.PetriP;
import ua.stetsenkoinna.petriobj.PetriT;

import java.awt.Rectangle;
import java.awt.geom.Point2D;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/**
 * One selection holding both kinds of thing the canvas holds, and the operations written once
 * against it.
 *
 * <p>The canvas used to keep three separate stores - the element list, the frame list and the
 * single-click frame - and every operation had to remember all three. Any that forgot silently
 * skipped Petri-objects, which is why Ctrl+C and Ctrl+V never carried an object, Ctrl+D only ever
 * duplicated the last-clicked one, and the documentation's promise of parity had no mechanism
 * behind it. These tests are that mechanism.
 */
public class CanvasSelectionTest {

    private static int idCounter = 5000;

    private static GraphPetriPlace placeAt(String name, int x, int y) {
        GraphPetriPlace place = new GraphPetriPlace(new PetriP(name, 0), idCounter++);
        place.setNewCoordinates(new Point2D.Double(x, y));
        return place;
    }

    private static GraphPetriTransition transitionAt(String name, int x, int y) {
        GraphPetriTransition transition = new GraphPetriTransition(new PetriT(name, 1.0), idCounter++);
        transition.setNewCoordinates(new Point2D.Double(x, y));
        return transition;
    }

    /** One object holding a place and a transition, plus one free place well clear of it. */
    private static PetriNetsPanel oneObjectAndAFreePlace() {
        PetriP.initNext();
        PetriT.initNext();
        PetriNetsPanel panel = new PetriNetsPanel(null, true);
        GraphPetriPlace inside = placeAt("PIn", 200, 200);
        GraphPetriTransition insideT = transitionAt("TIn", 300, 200);
        GraphPetriPlace free = placeAt("PFree", 900, 700);
        panel.getGraphNet().getGraphPetriPlaceList().add(inside);
        panel.getGraphNet().getGraphPetriTransitionList().add(insideT);
        panel.getGraphNet().getGraphPetriPlaceList().add(free);
        GraphObjectFrame object = new GraphObjectFrame("Object", new Rectangle(120, 120, 300, 220));
        panel.addObjectFrame(object);
        panel.getCanvasModel().claim(object, inside);
        panel.getCanvasModel().claim(object, insideT);
        panel.getSelection().clear();
        return panel;
    }

    private static GraphElement named(PetriNetsPanel panel, String name) {
        for (GraphPetriPlace place : panel.getGraphNet().getGraphPetriPlaceList()) {
            if (place.getName().equals(name)) {
                return place;
            }
        }
        for (GraphPetriTransition transition : panel.getGraphNet().getGraphPetriTransitionList()) {
            if (transition.getName().equals(name)) {
                return transition;
            }
        }
        throw new AssertionError("no element named " + name);
    }

    @Test
    public void oneSelectionHoldsBothKindsAndCountsThemTogether() {
        CanvasSelection selection = new CanvasSelection();
        GraphPetriPlace place = placeAt("P", 10, 10);
        GraphObjectFrame frame = new GraphObjectFrame("F", new Rectangle(0, 0, 200, 150));

        selection.add(place);
        selection.add(frame);

        assertEquals(2, selection.size());
        assertTrue(selection.contains(place));
        assertTrue(selection.contains(frame));
        assertFalse(selection.isEmpty());
        assertEquals("the element list is the very same instance the undoable edits mutate",
                List.of(place), selection.elements());

        selection.clear();
        assertTrue(selection.isEmpty());
        assertEquals(0, selection.size());
    }

    @Test
    public void theSingleClickFrameCountsAsSelectedWithoutBeingCountedTwice() {
        CanvasSelection selection = new CanvasSelection();
        GraphObjectFrame frame = new GraphObjectFrame("F", new Rectangle(0, 0, 200, 150));

        selection.setSelectedFrame(frame);
        assertTrue("the highlight is decided by one question, not three", selection.contains(frame));
        assertEquals(1, selection.size());
        assertEquals(List.of(frame), selection.allFrames());

        selection.add(frame);
        assertEquals("still one thing selected, not two", 1, selection.size());
        assertEquals(List.of(frame), selection.allFrames());
    }

    @Test
    public void selectAllTakesExactlyThisCanvassItems() {
        PetriNetsPanel panel = oneObjectAndAFreePlace();

        panel.selectAll();

        assertEquals("the free place, and nothing an object claims", 1, panel.getChoosenElements().size());
        assertSame(named(panel, "PFree"), panel.getChoosenElements().getFirst());
        assertEquals(1, panel.getSelection().frames().size());
    }

    @Test
    public void deleteSelectionRemovesBothKinds() {
        PetriNetsPanel panel = oneObjectAndAFreePlace();
        GraphObjectFrame object = panel.getCanvasModel().getFrames().getFirst();
        panel.selectAll();
        assertEquals(1, panel.getChoosenElements().size());
        assertTrue(panel.getSelection().contains(object));

        // The frame half and the element half of the one operation, minus their confirmations.
        panel.removeObjectFrame(object);
        panel.deleteSelectedElements();

        assertTrue("the object is gone", panel.getCanvasModel().getFrames().isEmpty());
        assertEquals("and so is the free place, in the same gesture",
                2, panel.getGraphNet().getGraphPetriPlaceList().size()
                        + panel.getGraphNet().getGraphPetriTransitionList().size());
    }

    @Test
    public void duplicateReachesEverySelectedObjectNotOnlyTheLastClicked() {
        // The documented promise that had no mechanism: Ctrl+A then Ctrl+D used to do nothing at
        // all, because duplicate read the single-click frame and select-all never set it.
        PetriNetsPanel panel = oneObjectAndAFreePlace();
        GraphObjectFrame second = new GraphObjectFrame("Second", new Rectangle(600, 120, 240, 200));
        panel.addObjectFrame(second);
        panel.getCanvasModel().claim(second, named(panel, "PFree"));
        panel.selectAll();
        assertEquals("both objects are selected and nothing else",
                2, panel.getSelection().frames().size());

        panel.duplicateSelection();

        assertEquals("every selected object got a copy", 4, panel.getCanvasModel().getFrames().size());
        assertTrue("and no copy is left selected", panel.getChoosenElements().isEmpty());
    }

    @Test
    public void copyAndPasteCarryAnObjectAndReclaimItsCopies() {
        PetriNetsPanel panel = oneObjectAndAFreePlace();
        GraphObjectFrame object = panel.getCanvasModel().getFrames().getFirst();
        panel.getSelection().setSelectedFrame(object);

        panel.copySelection();
        panel.pasteClipboard();

        assertEquals("the pasted object is an object, not loose elements",
                2, panel.getCanvasModel().getFrames().size());
        GraphObjectFrame pasted = panel.getCanvasModel().getFrames().get(1);
        assertNotSame(object, pasted);
        assertEquals("holding the copies of what the original held", 2, panel.countElementsIn(pasted));
        assertEquals("while the original keeps its own", 2, panel.countElementsIn(object));
        for (GraphElement copied : panel.getCanvasModel().membersOfSubtree(pasted)) {
            assertSame(pasted, panel.getCanvasModel().ownerOf(copied));
        }
    }

    @Test
    public void copyingAnObjectDoesNotAlsoPasteItsNetLoose() {
        PetriNetsPanel panel = oneObjectAndAFreePlace();
        GraphObjectFrame object = panel.getCanvasModel().getFrames().getFirst();
        panel.selectAll();
        panel.getSelection().setSelectedFrame(object);

        panel.copySelection();
        panel.pasteClipboard();

        // The free place was selected too, so it is pasted loose; the object's own members are
        // carried by the object rather than a second time as loose elements on top of it.
        int elements = panel.getGraphNet().getGraphPetriPlaceList().size()
                + panel.getGraphNet().getGraphPetriTransitionList().size();
        assertEquals("three originals, two copies inside the pasted object, one loose copy",
                6, elements);
    }

    @Test
    public void locateInCentreMovesEveryFrameWithItsNet() {
        PetriNetsPanel panel = oneObjectAndAFreePlace();
        GraphObjectFrame object = panel.getCanvasModel().getFrames().getFirst();
        GraphElement inside = named(panel, "PIn");
        double offsetX = inside.getGraphElementCenter().getX() - object.getBounds().x;

        panel.centreCanvasAt(new java.awt.Point(1200, 900));

        assertEquals("the element keeps its place inside its own frame", offsetX,
                inside.getGraphElementCenter().getX() - object.getBounds().x, 0.001);
        assertTrue(object.getBounds().contains(inside.getGraphElementCenter().getX(),
                inside.getGraphElementCenter().getY()));
    }

    @Test
    public void theMarqueeCatchesBothKindsByTheirCentre() {
        PetriNetsPanel panel = oneObjectAndAFreePlace();
        GraphObjectFrame object = panel.getCanvasModel().getFrames().getFirst();

        // A band that covers the object's centre (270, 230) and the free place's (900, 700),
        // without enclosing the object whole.
        panel.selectIn(new Rectangle(200, 200, 800, 600));

        assertTrue("the object is caught by its centre, exactly like a place",
                panel.getSelection().contains(object));
        assertEquals("and the free place is caught the same way", 1, panel.getChoosenElements().size());
        assertSame(named(panel, "PFree"), panel.getChoosenElements().getFirst());
    }

    @Test
    public void movingTheSelectionMovesElementsAndObjectsAlike() {
        PetriNetsPanel panel = oneObjectAndAFreePlace();
        GraphObjectFrame object = panel.getCanvasModel().getFrames().getFirst();
        panel.selectAll();
        Rectangle frameBefore = new Rectangle(object.getBounds());
        Point2D freeBefore = named(panel, "PFree").getGraphElementCenter();
        Point2D insideBefore = named(panel, "PIn").getGraphElementCenter();

        panel.moveSelectionBy(60, 40);

        assertEquals("the object moved", frameBefore.x + 60, object.getBounds().x);
        assertEquals(frameBefore.y + 40, object.getBounds().y);
        assertEquals("and so did its net, with it", insideBefore.getX() + 60,
                named(panel, "PIn").getGraphElementCenter().getX(), 0.001);
        assertEquals("and the free element it was selected alongside", freeBefore.getX() + 60,
                named(panel, "PFree").getGraphElementCenter().getX(), 0.001);
    }

    @Test
    public void clickSelectPicksTheFrameOverTheElementLockedInsideIt() {
        PetriNetsPanel panel = oneObjectAndAFreePlace();
        GraphObjectFrame object = panel.getCanvasModel().getFrames().getFirst();
        GraphElement inside = named(panel, "PIn");

        Object picked = panel.selectAt(inside.getGraphElementCenter());

        assertSame("a frame wins over an element it locks", object, picked);
        assertTrue(panel.getChoosenElements().isEmpty());

        Object free = panel.selectAt(named(panel, "PFree").getGraphElementCenter());
        assertSame(named(panel, "PFree"), free);
        assertNotNull(panel.getChoosenElements());
        assertEquals(1, panel.getChoosenElements().size());
    }
}

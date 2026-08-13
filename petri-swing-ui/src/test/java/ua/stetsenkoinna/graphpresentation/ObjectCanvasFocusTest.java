package ua.stetsenkoinna.graphpresentation;

import org.junit.Test;
import ua.stetsenkoinna.graphnet.GraphElement;
import ua.stetsenkoinna.graphnet.GraphObjectFrame;
import ua.stetsenkoinna.graphnet.GraphPetriPlace;
import ua.stetsenkoinna.graphnet.GraphPetriTransition;
import ua.stetsenkoinna.petriobj.PetriP;
import ua.stetsenkoinna.petriobj.PetriT;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.MouseEvent;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/**
 * Editing a Petri-object in place: what its own canvas paints, what it lets the user touch, and
 * that leaving it puts the whole drawing back.
 *
 * <p>Focus is a view, not a second document - one canvas model, one net, one undo history - so
 * every assertion here is about what a predicate answers and what ends up painted, which is the
 * only part of it a machine without a display can confirm. The pixel counts use the same ink
 * technique the existing painting tests do.
 */
public class ObjectCanvasFocusTest {

    private static int idCounter = 3000;

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

    /**
     * One object holding a place and a transition around (200, 200), plus a control object far
     * away at (1400, 200) with a place of its own, and one free place between them.
     */
    private static PetriNetsPanel twoObjectsAndAFreePlace() {
        PetriP.initNext();
        PetriT.initNext();
        PetriNetsPanel panel = new PetriNetsPanel(null, true);

        GraphPetriPlace inside = placeAt("PIn", 200, 200);
        GraphPetriTransition insideT = transitionAt("TIn", 300, 200);
        GraphPetriPlace elsewhere = placeAt("PElse", 1400, 200);
        GraphPetriPlace free = placeAt("PFree", 800, 600);
        panel.getGraphNet().getGraphPetriPlaceList().add(inside);
        panel.getGraphNet().getGraphPetriTransitionList().add(insideT);
        panel.getGraphNet().getGraphPetriPlaceList().add(elsewhere);
        panel.getGraphNet().getGraphPetriPlaceList().add(free);

        GraphObjectFrame subject = new GraphObjectFrame("Subject", new Rectangle(120, 120, 300, 220));
        panel.addObjectFrame(subject);
        panel.getCanvasModel().claim(subject, inside);
        panel.getCanvasModel().claim(subject, insideT);

        GraphObjectFrame control = new GraphObjectFrame("Control", new Rectangle(1320, 120, 240, 200));
        panel.addObjectFrame(control);
        panel.getCanvasModel().claim(control, elsewhere);

        panel.getSelection().clear();
        return panel;
    }

    private static GraphObjectFrame subjectOf(PetriNetsPanel panel) {
        return panel.getCanvasModel().getFrames().getFirst();
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

    /** Counts the pixels the panel put ink on inside one region of the canvas. */
    private static int inkIn(PetriNetsPanel panel, Rectangle region) {
        panel.setSize(2000, 1000);
        BufferedImage image = new BufferedImage(region.width, region.height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        graphics.translate(-region.x, -region.y);
        panel.paintComponent(graphics);
        graphics.dispose();

        int background = Color.WHITE.getRGB();
        int ink = 0;
        for (int y = 0; y < region.height; y++) {
            for (int x = 0; x < region.width; x++) {
                int pixel = image.getRGB(x, y);
                if (pixel != background && (pixel >>> 24) != 0) {
                    ink++;
                }
            }
        }
        return ink;
    }

    @Test
    public void enteringAnObjectPaintsItsNetAndLeavesEverythingElseOffTheCanvas() {
        PetriNetsPanel panel = twoObjectsAndAFreePlace();
        Rectangle around = new Rectangle(100, 100, 360, 280);
        Rectangle aroundControl = new Rectangle(1300, 100, 300, 260);
        Rectangle aroundFree = new Rectangle(740, 540, 140, 140);
        assertTrue("fixture: the net's canvas paints all three", inkIn(panel, around) > 0);
        assertTrue(inkIn(panel, aroundControl) > 0);
        assertTrue(inkIn(panel, aroundFree) > 0);

        panel.openObjectCanvas(subjectOf(panel));

        assertTrue("the object's own net is still painted", inkIn(panel, around) > 0);
        assertEquals("a sibling object is not on this canvas at all", 0, inkIn(panel, aroundControl));
        assertEquals("nor are the free elements", 0, inkIn(panel, aroundFree));

        panel.openObjectCanvas(null);
        assertTrue("leaving restores the whole drawing", inkIn(panel, aroundControl) > 0);
        assertTrue(inkIn(panel, aroundFree) > 0);
    }

    @Test
    public void anObjectsOwnMembersBecomeDirectlyEditableOnItsCanvas() {
        PetriNetsPanel panel = twoObjectsAndAFreePlace();
        GraphObjectFrame subject = subjectOf(panel);
        GraphElement inside = named(panel, "PIn");

        assertFalse("on the net's canvas a framed element is locked",
                (Boolean) invoke(panel, "isOnThisCanvas", new Class<?>[]{GraphElement.class}, inside));

        panel.openObjectCanvas(subject);

        assertTrue("on its own object's canvas it is not",
                (Boolean) invoke(panel, "isOnThisCanvas", new Class<?>[]{GraphElement.class}, inside));
        assertFalse("while a sibling object's element still is",
                (Boolean) invoke(panel, "isOnThisCanvas",
                        new Class<?>[]{GraphElement.class}, named(panel, "PElse")));
    }

    @Test
    public void elementsOutsideTheObjectAreNotHitTestableOnItsCanvas() {
        PetriNetsPanel panel = twoObjectsAndAFreePlace();
        GraphElement free = named(panel, "PFree");
        GraphElement elsewhere = named(panel, "PElse");
        assertSame("fixture: they are reachable on the net's canvas",
                free, panel.find(free.getGraphElementCenter()));

        panel.openObjectCanvas(subjectOf(panel));

        assertNull("a free element is not on this canvas", panel.find(free.getGraphElementCenter()));
        assertNull("nor is a sibling object's", panel.find(elsewhere.getGraphElementCenter()));
        assertSame("the object's own member is", named(panel, "PIn"),
                panel.find(named(panel, "PIn").getGraphElementCenter()));
        assertNull("and no port of an off-canvas object can be grabbed either",
                invoke(panel, "portOnCanvasAt", new Class<?>[]{Point2D.class},
                        elsewhere.getGraphElementCenter()));
    }

    @Test
    public void theFocusedFramesOwnHeaderAndHandleAreInertAndClicksInsideItReachTheNet() {
        PetriNetsPanel panel = twoObjectsAndAFreePlace();
        GraphObjectFrame subject = subjectOf(panel);
        panel.openObjectCanvas(subject);
        Rectangle bounds = subject.getBounds();

        assertNull("its header is not a drag handle on its own canvas",
                invoke(panel, "frameHeaderAt", new Class<?>[]{Point2D.class},
                        new Point2D.Double(bounds.getCenterX(), bounds.y + 5)));
        assertNull("nor is its resize corner",
                invoke(panel, "frameHandleAt", new Class<?>[]{Point2D.class},
                        new Point2D.Double(bounds.getMaxX() - 4, bounds.getMaxY() - 4)));
        assertNull("and a click inside it does not select the room the user is standing in",
                invoke(panel, "frameAt", new Class<?>[]{Point2D.class},
                        new Point2D.Double(bounds.getCenterX(), bounds.getCenterY())));

        // The click therefore falls through to the element under it, which is the whole point.
        GraphElement inside = named(panel, "PIn");
        panel.new MouseHandler().mousePressed(new MouseEvent(panel, MouseEvent.MOUSE_PRESSED,
                System.currentTimeMillis(), 0,
                (int) inside.getGraphElementCenter().getX(),
                (int) inside.getGraphElementCenter().getY(), 1, false, MouseEvent.BUTTON1));
        assertSame(inside, panel.getChoosen());
    }

    @Test
    public void deleteOnAnObjectCanvasDeletesItsOwnElement() throws Exception {
        PetriNetsPanel panel = twoObjectsAndAFreePlace();
        GraphObjectFrame subject = subjectOf(panel);
        panel.openObjectCanvas(subject);
        panel.selectAll();
        assertEquals(2, panel.getChoosenElements().size());

        panel.deleteSelectedElements();

        assertEquals("the object's own net is gone", 0, panel.countElementsIn(subject));
        assertTrue("and the sibling object is untouched",
                panel.getGraphNet().getGraphPetriPlaceList().contains(named(panel, "PElse")));
        assertTrue("as are the free elements",
                panel.getGraphNet().getGraphPetriPlaceList().contains(named(panel, "PFree")));
    }

    @Test
    public void aNewElementDrawnOnAnObjectCanvasIsClaimedForThatObject() {
        PetriNetsPanel panel = twoObjectsAndAFreePlace();
        GraphObjectFrame subject = subjectOf(panel);
        panel.openObjectCanvas(subject);
        panel.setTool(CanvasTool.ADD_PLACE);

        invoke(panel, "addElementAt", new Class<?>[]{CanvasTool.class, Point.class},
                CanvasTool.ADD_PLACE, new Point(250, 300));

        GraphPetriPlace added = panel.getGraphNet().getGraphPetriPlaceList().getLast();
        assertSame(subject, panel.getCanvasModel().ownerOf(added));
        assertEquals(3, panel.countElementsIn(subject));
    }

    @Test
    public void startingARunActivatesTheNetsOwnCanvas() {
        // A run is a run of the whole model, so it is watched where the whole model is drawn.
        PetriNetsPanel panel = twoObjectsAndAFreePlace();
        panel.openObjectCanvas(subjectOf(panel));
        assertSame(subjectOf(panel), panel.getFocusedFrame());

        panel.activateRootCanvas();

        assertNull(panel.getFocusedFrame());
        assertNull(panel.getCanvasStack().getActive());
        assertEquals("the canvas stays open on the strip, it is just no longer the active one",
                2, panel.getCanvasStack().getOpen().size());
    }

    @Test
    public void anObjectWhoseFrameIsRemovedTakesItsCanvasWithIt() {
        PetriNetsPanel panel = twoObjectsAndAFreePlace();
        GraphObjectFrame subject = subjectOf(panel);
        panel.openObjectCanvas(subject);

        panel.removeObjectFrame(subject);

        assertNull("a canvas whose frame is gone cannot be painted at all", panel.getFocusedFrame());
        assertEquals(List.of(), panel.getCanvasStack().getOpen().stream()
                .filter(java.util.Objects::nonNull).toList());
    }

    @Test
    public void anObjectOnItsOwnCanvasIsDrawnAsAPlainNetWithNoFrame() {
        PetriNetsPanel panel = twoObjectsAndAFreePlace();
        GraphObjectFrame subject = subjectOf(panel);
        Rectangle bounds = subject.getBounds();

        // A strip along the frame's top edge, which is where its header and border are painted on
        // the net's canvas. Kept clear of the elements inside so only the frame can put ink here.
        Rectangle header = new Rectangle(bounds.x, bounds.y, bounds.width, 18);

        assertTrue("fixture: the net's canvas does draw the frame", inkIn(panel, header) > 0);

        panel.openObjectCanvas(subject);

        assertEquals("on its own canvas the object is the net, not a box around it",
                0, inkIn(panel, header));
        assertTrue("its own elements are still drawn",
                inkIn(panel, new Rectangle(170, 170, 170, 70)) > 0);
    }

    @Test
    public void aNestedObjectIsStillDrawnWhenItsParentIsTheCanvas() {
        PetriNetsPanel panel = twoObjectsAndAFreePlace();
        GraphObjectFrame parent = subjectOf(panel);
        GraphObjectFrame child = new GraphObjectFrame("Child", new Rectangle(150, 240, 120, 80));
        panel.addObjectFrame(child);
        panel.getCanvasModel().nest(child, parent);

        panel.openObjectCanvas(parent);

        // Only the focused object loses its box. What is nested inside it keeps one, which is how
        // the hierarchy stays visible from the level above it.
        assertTrue("a child object is still drawn as a frame",
                inkIn(panel, new Rectangle(150, 240, 120, 18)) > 0);
    }
}

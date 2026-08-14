package ua.stetsenkoinna.graphpresentation;

import org.junit.Test;
import ua.stetsenkoinna.graphnet.FramePort;
import ua.stetsenkoinna.graphnet.GraphArcFactory;
import ua.stetsenkoinna.graphnet.GraphCanvasModel;
import ua.stetsenkoinna.graphnet.GraphElement;
import ua.stetsenkoinna.graphnet.GraphObjectFrame;
import ua.stetsenkoinna.graphnet.GraphPetriObjModel;
import ua.stetsenkoinna.graphnet.GraphPetriPlace;
import ua.stetsenkoinna.graphnet.GraphPetriTransition;
import ua.stetsenkoinna.petriobj.PetriP;
import ua.stetsenkoinna.petriobj.PetriT;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/**
 * A Petri-object inside a Petri-object: how the relation is recorded, what the parent's canvas
 * shows of it, and what it turns into on the way out to a PNML document.
 */
public class NestedObjectTest {

    private static int idCounter = 4000;

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
     * One object holding four elements: two around (200, 200) that will be grouped into a nested
     * object, and two around (500, 200) that stay the parent's own.
     */
    private static PetriNetsPanel oneObjectWithFourElements() {
        PetriP.initNext();
        PetriT.initNext();
        PetriNetsPanel panel = new PetriNetsPanel(null, true);
        for (GraphPetriPlace place : List.of(placeAt("PA", 200, 200), placeAt("PB", 500, 200))) {
            panel.getGraphNet().getGraphPetriPlaceList().add(place);
        }
        for (GraphPetriTransition transition
                : List.of(transitionAt("TA", 280, 200), transitionAt("TB", 580, 200))) {
            panel.getGraphNet().getGraphPetriTransitionList().add(transition);
        }
        GraphObjectFrame parent = new GraphObjectFrame("Parent", new Rectangle(120, 120, 560, 220));
        panel.addObjectFrame(parent);
        for (GraphPetriPlace place : panel.getGraphNet().getGraphPetriPlaceList()) {
            panel.getCanvasModel().claim(parent, place);
        }
        for (GraphPetriTransition transition : panel.getGraphNet().getGraphPetriTransitionList()) {
            panel.getCanvasModel().claim(parent, transition);
        }
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

    /** Groups PA and TA into a nested object on the parent's own canvas. */
    private static GraphObjectFrame nestAChildIn(PetriNetsPanel panel, GraphObjectFrame parent) {
        panel.openObjectCanvas(parent);
        return panel.groupIntoObject(List.of(named(panel, "PA"), named(panel, "TA")), "Child");
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
    public void groupingOnAnObjectsCanvasYieldsAChildAtLevelTwoCreatedCollapsed() {
        PetriNetsPanel panel = oneObjectWithFourElements();
        GraphObjectFrame parent = panel.getCanvasModel().getFrames().getFirst();

        GraphObjectFrame child = nestAChildIn(panel, parent);

        assertSame(parent, panel.getCanvasModel().enclosingOf(child));
        assertEquals(2, panel.getCanvasModel().levelOf(child));
        assertTrue("the user asked for nested objects shown in collapsed form", child.isCollapsed());
        assertEquals(GraphObjectFrame.COLLAPSED_WIDTH, child.getBounds().width);
        assertEquals(GraphObjectFrame.COLLAPSED_HEIGHT, child.getBounds().height);
    }

    @Test
    public void theParentCountsTheWholeSubtreeWhileClaimingOnlyItsOwn() {
        PetriNetsPanel panel = oneObjectWithFourElements();
        GraphObjectFrame parent = panel.getCanvasModel().getFrames().getFirst();

        GraphObjectFrame child = nestAChildIn(panel, parent);

        assertEquals("from outside, a nest is one object", 4, panel.countElementsIn(parent));
        assertEquals(2, panel.countElementsIn(child));
        assertEquals("but ownerOf still answers with the direct owner", 2, parent.getMembers().size());
        assertSame(child, panel.getCanvasModel().ownerOf(named(panel, "PA")));
        assertSame(parent, panel.getCanvasModel().ownerOf(named(panel, "PB")));
    }

    @Test
    public void aCollapsedChildIsPaintedOnItsParentsCanvasAndItsMembersAreNot() {
        PetriNetsPanel panel = oneObjectWithFourElements();
        GraphObjectFrame parent = panel.getCanvasModel().getFrames().getFirst();
        GraphObjectFrame child = nestAChildIn(panel, parent);
        panel.openObjectCanvas(null);

        assertTrue("the collapsed box is drawn inside its parent's rectangle",
                parent.getBounds().contains(child.getBounds().x, child.getBounds().y));
        assertTrue("and it puts ink there", inkIn(panel, new Rectangle(child.getBounds())) > 0);

        // Its own members are inside that box's footprint, so the honest check is the predicate the
        // painter itself uses rather than a pixel count over a region the box covers.
        assertFalse("a collapsed object's members are not painted",
                (Boolean) invoke(panel, "isDrawnOnThisCanvas",
                        new Class<?>[]{GraphElement.class}, named(panel, "PA")));
        assertTrue("while the parent's own still are",
                (Boolean) invoke(panel, "isDrawnOnThisCanvas",
                        new Class<?>[]{GraphElement.class}, named(panel, "PB")));
        assertNull("and they are not hit-testable either",
                panel.find(named(panel, "PA").getGraphElementCenter()));
    }

    @Test
    public void enteringACollapsedObjectExpandsItSoThereIsARoomToEditIn() {
        // A 170x56 summary box is not a room: the net inside it is drawn at full size and would
        // spill straight out of it, which is a blank-looking canvas by another route.
        PetriNetsPanel panel = oneObjectWithFourElements();
        GraphObjectFrame parent = panel.getCanvasModel().getFrames().getFirst();
        GraphObjectFrame child = nestAChildIn(panel, parent);
        assertTrue("fixture: a nested object starts collapsed", child.isCollapsed());
        Rectangle fitted = new Rectangle(child.getBounds());

        panel.openObjectCanvas(child);

        assertFalse(child.isCollapsed());
        assertTrue("expanded back past the collapsed box it was showing",
                child.getBounds().width > fitted.width);
        assertTrue("and its own net is inside it",
                child.getBounds().contains(named(panel, "PA").getGraphElementCenter().getX(),
                        named(panel, "PA").getGraphElementCenter().getY()));
    }

    @Test
    public void expandingTheChildBringsItsNetBackOntoTheParentsCanvas() {
        PetriNetsPanel panel = oneObjectWithFourElements();
        GraphObjectFrame parent = panel.getCanvasModel().getFrames().getFirst();
        GraphObjectFrame child = nestAChildIn(panel, parent);
        panel.openObjectCanvas(null);

        invoke(panel, "toggleCollapsed", new Class<?>[]{GraphObjectFrame.class}, child);

        assertFalse(child.isCollapsed());
        assertTrue("nesting sets the initial state; it does not force it forever",
                (Boolean) invoke(panel, "isDrawnOnThisCanvas",
                        new Class<?>[]{GraphElement.class}, named(panel, "PA")));
    }

    @Test
    public void aCollapsedChildShowsAPortForEveryElementOfItsSubtree() {
        PetriNetsPanel panel = oneObjectWithFourElements();
        GraphObjectFrame parent = panel.getCanvasModel().getFrames().getFirst();
        GraphObjectFrame child = nestAChildIn(panel, parent);

        List<FramePort> ports = panel.getCanvasModel().portsOf(child);

        assertEquals("one per element it holds, so a collapsed object still shows where it "
                + "can be connected", 2, ports.size());
        assertEquals("and the parent's own ports cover the whole nest",
                4, panel.getCanvasModel().portsOf(parent).size());
    }

    @Test
    public void aCrossingArcAnchorsToTheOutermostCollapsedAncestorsPort() {
        PetriNetsPanel panel = oneObjectWithFourElements();
        GraphObjectFrame parent = panel.getCanvasModel().getFrames().getFirst();
        GraphObjectFrame child = nestAChildIn(panel, parent);
        panel.openObjectCanvas(null);
        GraphElement insideChild = named(panel, "PA");

        // Expanded parent, collapsed child: the child's border is the outermost thing on screen.
        java.awt.Point atChild = (java.awt.Point) invoke(panel, "connectionEndpoint",
                new Class<?>[]{GraphObjectFrame.class, GraphElement.class}, child, insideChild);
        assertNotNull(atChild);
        assertEquals(portOf(panel, child, "PA").getPosition(), atChild);

        // Collapse the parent too, and the same connection has to reach the parent's border, since
        // the child is not drawn anywhere at all any more.
        parent.setCollapsed(true);
        java.awt.Point atParent = (java.awt.Point) invoke(panel, "connectionEndpoint",
                new Class<?>[]{GraphObjectFrame.class, GraphElement.class}, child, insideChild);
        assertEquals(portOf(panel, parent, "PA").getPosition(), atParent);
        assertFalse("a frame nested inside a collapsed parent is not drawn at all, not even in "
                        + "miniature",
                (Boolean) invoke(panel, "isFrameDrawnOnThisCanvas",
                        new Class<?>[]{GraphObjectFrame.class}, child));
    }

    private static FramePort portOf(PetriNetsPanel panel, GraphObjectFrame frame, String elementName) {
        for (FramePort port : panel.getCanvasModel().portsOf(frame)) {
            if (port.getLabel().equals(elementName)) {
                return port;
            }
        }
        throw new AssertionError("no port for " + elementName + " on " + frame.getName());
    }

    @Test
    public void movingTheParentMovesTheChildAndBothNets() {
        PetriNetsPanel panel = oneObjectWithFourElements();
        GraphObjectFrame parent = panel.getCanvasModel().getFrames().getFirst();
        GraphObjectFrame child = nestAChildIn(panel, parent);
        panel.openObjectCanvas(null);
        Rectangle childBefore = new Rectangle(child.getBounds());
        Point2D insideChildBefore = named(panel, "PA").getGraphElementCenter();
        Point2D insideParentBefore = named(panel, "PB").getGraphElementCenter();

        invoke(panel, "moveFrame", new Class<?>[]{GraphObjectFrame.class, int.class, int.class},
                parent, parent.getBounds().x + 130, parent.getBounds().y + 70);

        assertEquals(childBefore.x + 130, child.getBounds().x);
        assertEquals(childBefore.y + 70, child.getBounds().y);
        assertEquals(insideChildBefore.getX() + 130,
                named(panel, "PA").getGraphElementCenter().getX(), 0.001);
        assertEquals(insideParentBefore.getY() + 70,
                named(panel, "PB").getGraphElementCenter().getY(), 0.001);
    }

    @Test
    public void aNestedCanvasExportsAsSiblingObjectsAndTheNestComesBack() {
        // PNML has no notion of one page inside another, so the nested object is still an
        // ordinary sibling page with its own index in the flat frame list - a foreign
        // reader sees what it always saw. The nest itself travels as a tool-specific
        // parentObject attribute and is restored on import; it used to be dropped, so the
        // reimported inner object sat inside the outer frame while belonging to nobody.
        PetriNetsPanel panel = oneObjectWithFourElements();
        GraphObjectFrame parent = panel.getCanvasModel().getFrames().getFirst();
        GraphObjectFrame child = nestAChildIn(panel, parent);
        // An arc across the nesting boundary, which is a link between two objects like any other.
        panel.getGraphNet().getGraphArcInList().add(GraphArcFactory.inArc(
                (GraphPetriPlace) named(panel, "PB"), (GraphPetriTransition) named(panel, "TA"),
                1, false));

        GraphPetriObjModel exported = panel.getCanvasModel().toObjModel();

        assertEquals("indexed by the flat frame list, parent first", 2, exported.getObjectCount());
        assertEquals("Parent", exported.getObject(0).getName());
        assertEquals("Child", exported.getObject(1).getName());
        assertEquals(panel.getCanvasModel().getFrames().indexOf(child), 1);
        assertEquals("the child records which sibling encloses it",
                0, exported.getObject(1).getParentIndex());
        assertEquals("the boundary-crossing arc is a link", 1, exported.getLinks().size());

        GraphCanvasModel reimported = GraphCanvasModel.fromObjModel(exported);
        assertEquals(2, reimported.getFrames().size());
        assertSame("and the nest is restored from the attribute",
                reimported.getFrames().get(0),
                reimported.enclosingOf(reimported.getFrames().get(1)));
    }

    @Test
    public void savingAnObjectThatContainsANestedOneKeepsTheWholeSubtree() {
        PetriNetsPanel panel = oneObjectWithFourElements();
        GraphObjectFrame parent = panel.getCanvasModel().getFrames().getFirst();
        nestAChildIn(panel, parent);

        ua.stetsenkoinna.graphnet.GraphPetriNet inside =
                (ua.stetsenkoinna.graphnet.GraphPetriNet) invoke(panel, "buildObjectNet",
                        new Class<?>[]{GraphObjectFrame.class}, parent);

        assertEquals("from outside, a nest is one object", 2, inside.getGraphPetriPlaceList().size());
        assertEquals(2, inside.getGraphPetriTransitionList().size());
    }
}

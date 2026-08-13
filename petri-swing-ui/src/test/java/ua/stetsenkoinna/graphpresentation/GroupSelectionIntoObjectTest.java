package ua.stetsenkoinna.graphpresentation;

import org.junit.Test;
import ua.stetsenkoinna.graphnet.GraphObjectFrame;
import ua.stetsenkoinna.graphnet.GraphPetriNet;
import ua.stetsenkoinna.graphnet.GraphPetriObjModel;
import ua.stetsenkoinna.graphnet.GraphPetriPlace;
import ua.stetsenkoinna.graphnet.GraphPetriTransition;
import ua.stetsenkoinna.petriobj.PetriObjLinkType;
import ua.stetsenkoinna.petriobj.PetriP;
import ua.stetsenkoinna.petriobj.PetriT;

import javax.swing.JScrollPane;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionListener;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/**
 * "Group selection into Petri-object", from the gesture that opens the menu to the net the new
 * object turns out to hold.
 *
 * <p>The reported symptom these tests were written against: a chunk of the net is selected,
 * right-clicked and grouped into a Petri-object, and the object then opens on an empty canvas.
 * Three separate things had to hold for that not to happen, and all three are pinned here - the
 * selection has to still be there when the popup trigger decides which menu to show, the elements
 * the new frame claims have to resolve back to it through {@code GraphCanvasModel.ownerOf}, and
 * entering the object has to actually bring its net into view rather than paint it off screen.
 */
public class GroupSelectionIntoObjectTest {

    /** An editable canvas with two free places, far enough apart to marquee across both. */
    private static PetriNetsPanel panelWithTwoFreePlaces() {
        PetriP.initNext();
        PetriT.initNext();
        PetriNetsPanel panel = new PetriNetsPanel(null, true);
        GraphPetriPlace left = new GraphPetriPlace(new PetriP("P1", 0), 700);
        left.setNewCoordinates(new Point2D.Double(150, 150));
        GraphPetriPlace right = new GraphPetriPlace(new PetriP("P2", 0), 701);
        right.setNewCoordinates(new Point2D.Double(250, 150));
        panel.getGraphNet().getGraphPetriPlaceList().add(left);
        panel.getGraphNet().getGraphPetriPlaceList().add(right);
        return panel;
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

    /**
     * Rubber-band selects across the whole canvas with the left button, exactly as the mouse
     * does: press on empty canvas, drag, release.
     */
    private static void marqueeAcrossEverything(PetriNetsPanel panel) {
        PetriNetsPanel.MouseHandler handler = mouseHandlerOf(panel);
        MouseMotionListener motion = motionHandlerOf(panel);
        handler.mousePressed(event(panel, MouseEvent.MOUSE_PRESSED, 60, 60, false, MouseEvent.BUTTON1));
        motion.mouseDragged(event(panel, MouseEvent.MOUSE_DRAGGED, 200, 120, false, MouseEvent.BUTTON1));
        motion.mouseDragged(event(panel, MouseEvent.MOUSE_DRAGGED, 340, 260, false, MouseEvent.BUTTON1));
        handler.mouseReleased(event(panel, MouseEvent.MOUSE_RELEASED, 340, 260, false, MouseEvent.BUTTON1));
    }

    /**
     * Runs a popup-trigger release and reports which of the two Petri-object menus it decided
     * on. The panel is not showing in a window, so {@code JPopupMenu.show} always throws
     * before anything is painted, and the frame it throws from names the decision: that makes
     * the branch observable without a display, and without touching the panel to observe it.
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

    @Test
    public void aRightClickOnAMarqueeSelectionStillSeesThatSelectionOnRelease() {
        // The hypothesis this was written against: on Windows the popup trigger arrives on
        // release, so the ordinary press handling runs in between - if that press cleared the
        // selection, the release would fall through to the "new empty Petri-object" menu and the
        // user would create an empty object believing they had grouped their chunk. It does not,
        // and the menu routing has to keep working through the selection rewrite.
        PetriNetsPanel panel = panelWithTwoFreePlaces();
        marqueeAcrossEverything(panel);
        assertEquals("fixture sanity check: the marquee caught both places",
                2, panel.getChoosenElements().size());

        // The press half of the right-click: popupTrigger=false, the way Windows delivers it.
        mouseHandlerOf(panel).mousePressed(
                event(panel, MouseEvent.MOUSE_PRESSED, 420, 420, false, MouseEvent.BUTTON3));

        assertEquals("the selection the release is about to read must survive the press",
                2, panel.getChoosenElements().size());
        assertEquals("showGroupSelectionMenu", menuOpenedByPopupRelease(panel, 420, 420));
    }

    @Test
    public void aClickDeliveredAfterAMarqueeReleaseKeepsTheSelection() {
        // The other half of the same gesture. A marquee selection used to outlive the release that
        // made it only because AWT happens to suppress MOUSE_CLICKED once a drag has been sent:
        // mouseClicked cleared the whole selection outright when nothing was under the pointer.
        // The canvas now guards that with a flag set on the drag itself, so the selection survives
        // whether or not the platform suppresses the click.
        PetriNetsPanel panel = panelWithTwoFreePlaces();
        marqueeAcrossEverything(panel);
        assertEquals(2, panel.getChoosenElements().size());

        mouseHandlerOf(panel).mouseClicked(
                event(panel, MouseEvent.MOUSE_CLICKED, 340, 260, false, MouseEvent.BUTTON1));

        assertEquals("the click that closes a drag gesture is not a click on nothing",
                2, panel.getChoosenElements().size());

        // And the guard is one-shot: the NEXT click, which really is a click on empty canvas,
        // clears the selection the way it always did.
        mouseHandlerOf(panel).mouseClicked(
                event(panel, MouseEvent.MOUSE_CLICKED, 500, 500, false, MouseEvent.BUTTON1));
        assertTrue(panel.getChoosenElements().isEmpty());
    }

    @Test
    public void aRightClickWithNoSelectionOpensTheNewObjectMenuInstead() {
        // The other branch, so the test above is not passing just because both branches would
        // report the same thing: with nothing selected, a right-click on empty canvas offers to
        // create a new object rather than to group anything.
        PetriNetsPanel panel = panelWithTwoFreePlaces();
        mouseHandlerOf(panel).mousePressed(
                event(panel, MouseEvent.MOUSE_PRESSED, 420, 420, false, MouseEvent.BUTTON3));

        assertTrue(panel.getChoosenElements().isEmpty());
        assertEquals("showNewObjectMenu", menuOpenedByPopupRelease(panel, 420, 420));
    }

    @Test
    public void claimingAnElementAnotherObjectHoldsMovesItInsteadOfSharingIt() {
        // The model-level fix. A claim is single-valued and the canvas is its only writer, so the
        // second frame takes the element off the first rather than both claiming it and ownerOf
        // answering with whichever came first in canvas order - which is what left a freshly
        // created object holding nothing as far as every reader was concerned.
        PetriNetsPanel panel = panelWithTwoFreePlaces();
        List<GraphPetriPlace> places = panel.getGraphNet().getGraphPetriPlaceList();
        GraphObjectFrame first = new GraphObjectFrame("First", new Rectangle(60, 60, 300, 200));
        panel.addObjectFrame(first);
        for (GraphPetriPlace place : places) {
            panel.getCanvasModel().claim(first, place);
        }

        GraphObjectFrame second = new GraphObjectFrame("Second", new Rectangle(60, 60, 300, 200));
        panel.addObjectFrame(second);
        for (GraphPetriPlace place : places) {
            panel.getCanvasModel().claim(second, place);
        }

        assertSame("ownerOf answers with the frame that claimed it last",
                second, panel.getCanvasModel().ownerOf(places.getFirst()));
        assertFalse("and the first frame let go of it", first.hasMember(places.getFirst()));
        assertEquals("so the new object really does hold both", 2, panel.countElementsIn(second));
        assertEquals(2, buildObjectNet(panel, second).getGraphPetriPlaceList().size());
        assertEquals("while the first one holds nothing", 0, panel.countElementsIn(first));
    }

    @Test
    public void aSharedPlaceResolvesToTheFrameThatClaimsIt() {
        // The fusion special case in ownerOf is gone. It used to outrank real membership, so
        // grouping the free half of a shared place into an object left that object without it.
        // A fusion has nothing to say about ownership: the two halves are no longer moved on top
        // of each other, so there is nothing for it to disambiguate.
        PetriNetsPanel panel = panelWithTwoFreePlaces();
        List<GraphPetriPlace> places = panel.getGraphNet().getGraphPetriPlaceList();
        GraphPetriPlace framed = places.getFirst();
        GraphPetriPlace free = places.get(1);
        GraphObjectFrame owner = new GraphObjectFrame("Owner", new Rectangle(100, 100, 140, 120));
        panel.addObjectFrame(owner);
        panel.getCanvasModel().claim(owner, framed);
        panel.getCanvasModel().joinPlaces(framed, free);

        GraphObjectFrame grouped = new GraphObjectFrame("Grouped", new Rectangle(240, 100, 140, 120));
        panel.addObjectFrame(grouped);
        panel.getCanvasModel().claim(grouped, free);

        assertSame("the shared place resolves to the frame that claims it",
                grouped, panel.getCanvasModel().ownerOf(free));
        assertTrue("so it is in the new object's own net",
                buildObjectNet(panel, grouped).getGraphPetriPlaceList().contains(free));

        // And the fusion is still a fusion: sharing a place between two objects is unaffected by
        // ownership no longer being decided by it.
        GraphPetriObjModel model = panel.getCanvasModel().toObjModel();
        assertTrue("the shared place is still exported as a fusion link",
                model.getLinks().stream().anyMatch(link -> link.getType() == PetriObjLinkType.PLACE_FUSION));
    }

    @Test
    public void boundsAroundNothingIsADegenerateRectangleAtTheOrigin() throws Exception {
        // What a frame built around an empty collection gets. The min/max sentinels used to go
        // unreplaced, so the rectangle landed about two billion units away with a width that came
        // out of an integer overflow - reachable from a saved prototype whose file builds nothing.
        Method boundsAround = PetriNetsPanel.class.getDeclaredMethod("boundsAround", List.class);
        boundsAround.setAccessible(true);

        Rectangle bounds = (Rectangle) boundsAround.invoke(null, List.of());

        assertEquals(new Rectangle(0, 0, 0, 0), bounds);
    }

    @Test
    public void aFrameNamesTheObjectThatEnclosesIt() {
        // Nesting is a relation the model carries now, not a drawing coincidence: a frame drawn
        // inside another frame says so, and its abstraction level is the depth of that chain.
        PetriNetsPanel panel = panelWithTwoFreePlaces();
        GraphObjectFrame outer = new GraphObjectFrame("Outer", new Rectangle(60, 60, 400, 300));
        GraphObjectFrame inner = new GraphObjectFrame("Inner", new Rectangle(100, 100, 180, 140));
        panel.addObjectFrame(outer);
        panel.addObjectFrame(inner);

        panel.getCanvasModel().nest(inner, outer);

        assertSame(outer, panel.getCanvasModel().enclosingOf(inner));
        assertEquals("a top-level object is level 1", 1, panel.getCanvasModel().levelOf(outer));
        assertEquals("nested in it, level 2", 2, panel.getCanvasModel().levelOf(inner));
        assertTrue("and the frame itself has the accessor that used not to exist",
                java.util.Arrays.stream(GraphObjectFrame.class.getMethods())
                        .anyMatch(method -> method.getName().equals("getEnclosing")));
    }

    @Test
    public void enteringAnObjectFarFromTheOriginBringsItIntoView() {
        // The reported bug, at the level it was actually caused. Nothing was ever wrong with the
        // model: the object held exactly the chunk that was grouped. The modal editor sized its
        // panel from the frame's WIDTH and HEIGHT only, ignoring its position, while the canvas
        // paints every element at its absolute coordinate - so a chunk grouped at 900,700 was
        // painted 900,700 out on a 500x400 view whose scroll pane had nothing to scroll to.
        // Entering the object now moves the viewport to it instead.
        PetriP.initNext();
        PetriT.initNext();
        PetriNetsPanel panel = new PetriNetsPanel(null, true);
        GraphPetriPlace left = new GraphPetriPlace(new PetriP("PFar1", 0), 710);
        left.setNewCoordinates(new Point2D.Double(900, 700));
        GraphPetriPlace right = new GraphPetriPlace(new PetriP("PFar2", 0), 711);
        right.setNewCoordinates(new Point2D.Double(1050, 700));
        GraphPetriTransition middle = new GraphPetriTransition(new PetriT("TFar", 1.0), 712);
        middle.setNewCoordinates(new Point2D.Double(975, 790));
        panel.getGraphNet().getGraphPetriPlaceList().add(left);
        panel.getGraphNet().getGraphPetriPlaceList().add(right);
        panel.getGraphNet().getGraphPetriTransitionList().add(middle);

        GraphObjectFrame frame = panel.groupIntoObject(
                List.of(left, right, middle), "Far");
        assertEquals("the object really does hold the chunk", 3, panel.countElementsIn(frame));

        // Laid out by hand: validate() does nothing for a component with no peer, and without a
        // laid-out viewport there is no extent for the scroll to be measured against.
        JScrollPane pane = new JScrollPane(panel);
        pane.setSize(500, 400);
        pane.doLayout();
        pane.getViewport().doLayout();

        panel.openObjectCanvas(frame);

        Rectangle visible = pane.getViewport().getViewRect();
        assertTrue("the viewport must have moved to the object: " + visible + " vs " + frame.getBounds(),
                visible.contains(frame.getBounds().x, frame.getBounds().y));
        assertTrue("and the net is painted inside what is on screen",
                inkPixelsIn(panel, visible) > 0);
    }

    @Test
    public void groupingASelectionThatIncludesAnObjectLeavesAMarginAroundItsFrame() {
        // The reported symptom: marqueeing a Petri-object together with some free elements
        // around it and grouping the lot used to fit the new frame exactly to the union of
        // their bounds, so its border landed flush against the nested object's own border - the
        // two looked fused into one rectangle instead of one object sitting inside another.
        PetriP.initNext();
        PetriT.initNext();
        PetriNetsPanel panel = new PetriNetsPanel(null, true);
        GraphPetriPlace left = new GraphPetriPlace(new PetriP("P1", 0), 700);
        left.setNewCoordinates(new Point2D.Double(150, 150));
        panel.getGraphNet().getGraphPetriPlaceList().add(left);
        GraphObjectFrame nested = new GraphObjectFrame("Generator", new Rectangle(400, 400, 200, 150));
        panel.addObjectFrame(nested);

        GraphObjectFrame outer = panel.groupIntoObject(List.of(left), List.of(nested), "Outer");

        int margin = frameMargin(panel);
        Rectangle inner = nested.getBounds();
        Rectangle outerBounds = outer.getBounds();
        assertTrue("left gap must be at least the frame margin",
                inner.x - outerBounds.x >= margin);
        assertTrue("top gap must be at least the frame margin",
                inner.y - outerBounds.y >= margin);
        assertTrue("right gap must be at least the frame margin",
                (outerBounds.x + outerBounds.width) - (inner.x + inner.width) >= margin);
        assertTrue("bottom gap must be at least the frame margin",
                (outerBounds.y + outerBounds.height) - (inner.y + inner.height) >= margin);
    }

    @Test
    public void growingAnEnclosingFrameKeepsAMarginAroundTheFrameItNowContains() throws Exception {
        PetriNetsPanel panel = panelWithTwoFreePlaces();
        GraphObjectFrame enclosing = new GraphObjectFrame("Outer", new Rectangle(0, 0, 100, 100));
        Rectangle inner = new Rectangle(500, 500, 50, 50); // escapes the enclosing frame entirely

        Method growToContain = PetriNetsPanel.class.getDeclaredMethod(
                "growToContain", GraphObjectFrame.class, Rectangle.class);
        growToContain.setAccessible(true);
        growToContain.invoke(panel, enclosing, inner);

        int margin = frameMargin(panel);
        Rectangle grown = enclosing.getBounds();
        assertTrue("right gap must be at least the frame margin",
                (grown.x + grown.width) - (inner.x + inner.width) >= margin);
        assertTrue("bottom gap must be at least the frame margin",
                (grown.y + grown.height) - (inner.y + inner.height) >= margin);
    }

    private static int frameMargin(PetriNetsPanel panel) {
        try {
            java.lang.reflect.Field field = PetriNetsPanel.class.getDeclaredField("FRAME_MARGIN");
            field.setAccessible(true);
            return field.getInt(panel);
        } catch (ReflectiveOperationException broken) {
            throw new AssertionError(broken);
        }
    }

    /**
     * Paints a panel the way Swing does inside a scrolled viewport - translated by the scroll
     * position, clipped to the visible rectangle - and counts the pixels it put ink on, the
     * background it fills itself with not counting as ink.
     */
    private static int inkPixelsIn(PetriNetsPanel panel, Rectangle visible) {
        BufferedImage image = new BufferedImage(visible.width, visible.height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        graphics.translate(-visible.x, -visible.y);
        panel.paintComponent(graphics);
        graphics.dispose();

        int background = Color.WHITE.getRGB();
        int ink = 0;
        for (int y = 0; y < visible.height; y++) {
            for (int x = 0; x < visible.width; x++) {
                int pixel = image.getRGB(x, y);
                if (pixel != background && (pixel >>> 24) != 0) {
                    ink++;
                }
            }
        }
        return ink;
    }

    private static GraphPetriNet buildObjectNet(PetriNetsPanel panel, GraphObjectFrame frame) {
        try {
            Method build = PetriNetsPanel.class.getDeclaredMethod("buildObjectNet", GraphObjectFrame.class);
            build.setAccessible(true);
            return (GraphPetriNet) build.invoke(panel, frame);
        } catch (InvocationTargetException failure) {
            throw new AssertionError(failure.getCause());
        } catch (ReflectiveOperationException broken) {
            throw new AssertionError(broken);
        }
    }
}

package ua.stetsenkoinna.graphpresentation;

import org.junit.Test;
import ua.stetsenkoinna.graphnet.GraphArcIn;
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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/**
 * Pins the geometry follow-ups and edit-scope rules added around frame resizing, drops onto
 * hidden objects, and arcs.
 *
 * <p>Resize used to be the one geometry gesture with no follow-up at all: a frame could be
 * shrunk below its own net (leaving elements drawn outside the border that claimed them) and
 * a nested frame could be grown far past its parent with no margin growth anywhere. A drop
 * onto a collapsed object silently nested the dragged thing into a hidden interior, where it
 * vanished. And arcs had no edit scope: the Delete tool could reach into a locked, nested
 * object from the root canvas and delete an internal arc its endpoints were protected from.
 */
public class FrameGeometryScopeTest {

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
        return panel.getMouseMotionListeners()[0];
    }

    private static MouseEvent event(PetriNetsPanel panel, int id, int x, int y) {
        return new MouseEvent(panel, id, System.currentTimeMillis(), 0, x, y, 1, false, MouseEvent.BUTTON1);
    }

    private static void drag(PetriNetsPanel panel, int fromX, int fromY, int toX, int toY) {
        PetriNetsPanel.MouseHandler handler = mouseHandlerOf(panel);
        MouseMotionListener motion = motionHandlerOf(panel);
        handler.mousePressed(event(panel, MouseEvent.MOUSE_PRESSED, fromX, fromY));
        motion.mouseDragged(event(panel, MouseEvent.MOUSE_DRAGGED, (fromX + toX) / 2, (fromY + toY) / 2));
        motion.mouseDragged(event(panel, MouseEvent.MOUSE_DRAGGED, toX, toY));
        handler.mouseReleased(event(panel, MouseEvent.MOUSE_RELEASED, toX, toY));
    }

    private static GraphPetriPlace placeAt(PetriNetsPanel panel, String name, int x, int y) {
        GraphPetriPlace place = new GraphPetriPlace(new PetriP(name, 0), idCounter++);
        place.setNewCoordinates(new Point2D.Double(x, y));
        panel.getGraphNet().getGraphPetriPlaceList().add(place);
        return place;
    }

    private static GraphPetriTransition transitionAt(PetriNetsPanel panel, String name, int x, int y) {
        GraphPetriTransition transition = new GraphPetriTransition(new PetriT(name, 1.0), idCounter++);
        transition.setNewCoordinates(new Point2D.Double(x, y));
        panel.getGraphNet().getGraphPetriTransitionList().add(transition);
        return transition;
    }

    private static GraphObjectFrame frameWith(PetriNetsPanel panel, String name,
            Rectangle bounds, GraphPetriPlace member) {
        GraphObjectFrame frame = new GraphObjectFrame(name, bounds);
        panel.getCanvasModel().getFrames().add(frame);
        if (member != null) {
            panel.getCanvasModel().claim(frame, member);
        }
        return frame;
    }

    // ------------------------------------------------------------------ resize

    @Test
    public void aFrameCannotBeShrunkBelowItsOwnNet() {
        PetriNetsPanel panel = freshPanel();
        GraphPetriPlace place = placeAt(panel, "P1", 400, 300);
        GraphObjectFrame frame = frameWith(panel, "Obj", new Rectangle(100, 100, 500, 400), place);

        // Grab the resize handle at the bottom-right corner and drag far up-left.
        drag(panel, 595, 495, 150, 150);

        Rectangle bounds = frame.getBounds();
        assertTrue("the frame still contains its place with its label clearance: " + bounds,
                bounds.x + bounds.width >= 400 + 20 && bounds.y + bounds.height >= 300 + 20);
    }

    @Test
    public void aFrameCannotBeShrunkBelowItsNestedObject() {
        PetriNetsPanel panel = freshPanel();
        GraphPetriPlace inner = placeAt(panel, "PI", 400, 300);
        GraphObjectFrame outer = frameWith(panel, "Outer", new Rectangle(100, 100, 600, 500), null);
        GraphObjectFrame child = frameWith(panel, "Inner", new Rectangle(340, 240, 160, 120), inner);
        panel.getCanvasModel().nest(child, outer);

        drag(panel, 695, 595, 200, 200);

        Rectangle bounds = outer.getBounds();
        Rectangle childBounds = child.getBounds();
        assertTrue("the parent still contains the nested object: " + bounds + " vs " + childBounds,
                bounds.contains(childBounds));
    }

    @Test
    public void growingANestedObjectPastItsParentGrowsTheParentChain() {
        PetriNetsPanel panel = freshPanel();
        GraphPetriPlace inner = placeAt(panel, "PI", 300, 250);
        GraphObjectFrame outer = frameWith(panel, "Outer", new Rectangle(100, 100, 400, 300), null);
        GraphObjectFrame child = frameWith(panel, "Inner", new Rectangle(240, 190, 160, 120), inner);
        panel.getCanvasModel().nest(child, outer);
        panel.openObjectCanvas(outer);

        // Drag the child's resize handle far past the parent's border.
        drag(panel, 395, 305, 700, 550);

        Rectangle parentBounds = outer.getBounds();
        Rectangle childBounds = child.getBounds();
        assertTrue("the parent grew to keep the resized child inside: "
                        + parentBounds + " vs " + childBounds,
                parentBounds.contains(childBounds));
    }

    @Test
    public void draggingAChildPartlyOutOfItsParentGrowsTheParent() {
        PetriNetsPanel panel = freshPanel();
        GraphPetriPlace inner = placeAt(panel, "PI", 300, 250);
        GraphObjectFrame outer = frameWith(panel, "Outer", new Rectangle(100, 100, 400, 300), null);
        GraphObjectFrame child = frameWith(panel, "Inner", new Rectangle(240, 190, 160, 120), inner);
        panel.getCanvasModel().nest(child, outer);
        panel.openObjectCanvas(outer);

        // Header-drag the child so part of it pushes past the parent's right border while
        // its centre stays inside the parent's rectangle.
        drag(panel, 320, 200, 470, 210);

        assertSame("still nested", outer, panel.getCanvasModel().enclosingOf(child));
        assertTrue("the parent grew to keep the moved child inside: "
                        + outer.getBounds() + " vs " + child.getBounds(),
                outer.getBounds().contains(child.getBounds()));
    }

    // ------------------------------------------------------------------ header-aware margins

    /**
     * The enclosing frame's header band occupies the top of its rectangle, so a uniform
     * margin left a nested object flush under the header: the gap the user sees on the
     * other three sides was missing above.
     */
    @Test
    public void groupingKeepsTheFullMarginVisibleAboveANestedObject() {
        PetriNetsPanel panel = freshPanel();
        GraphPetriPlace member = placeAt(panel, "PC", 300, 250);
        GraphObjectFrame child = frameWith(panel, "Child", new Rectangle(240, 190, 160, 120), member);

        GraphObjectFrame group = panel.groupIntoObject(
                java.util.List.of(), java.util.List.of(child), "Group");

        int gapAboveChild = child.getBounds().y
                - (group.getBounds().y + GraphObjectFrame.HEADER_HEIGHT);
        assertTrue("the margin above the nested object survives below the header: "
                        + gapAboveChild + "px", gapAboveChild >= 24);
    }

    @Test
    public void aDropNearTheParentsTopKeepsTheMarginBelowTheHeader() {
        PetriNetsPanel panel = freshPanel();
        GraphPetriPlace pa = placeAt(panel, "PA", 200, 200);
        GraphObjectFrame dragged = frameWith(panel, "Dragged", new Rectangle(140, 140, 160, 120), pa);
        GraphPetriPlace pb = placeAt(panel, "PB", 700, 500);
        GraphObjectFrame target = frameWith(panel, "Target", new Rectangle(600, 400, 300, 240), pb);

        // Header-drag so the dragged frame's centre lands just inside the target's top.
        drag(panel, 200, 150, 700, 430);

        assertSame(target, panel.getCanvasModel().enclosingOf(dragged));
        int gapAboveChild = dragged.getBounds().y
                - (target.getBounds().y + GraphObjectFrame.HEADER_HEIGHT);
        assertTrue("the grown parent leaves the full margin under its header: "
                        + gapAboveChild + "px", gapAboveChild >= 24);
    }

    // ------------------------------------------------------------------ hidden drop targets

    @Test
    public void aFrameDroppedOnACollapsedObjectDoesNotVanishInsideIt() {
        PetriNetsPanel panel = freshPanel();
        GraphPetriPlace pa = placeAt(panel, "PA", 200, 200);
        GraphObjectFrame dragged = frameWith(panel, "Dragged", new Rectangle(140, 140, 160, 120), pa);
        GraphPetriPlace pc = placeAt(panel, "PC", 700, 400);
        GraphObjectFrame collapsed = frameWith(panel, "Collapsed", new Rectangle(600, 300, 300, 240), pc);
        collapsed.setCollapsed(true);
        Rectangle box = collapsed.getBounds();

        // Header-drag the frame so its centre lands on the collapsed summary box.
        drag(panel, 200, 150, box.x + box.width / 2, box.y + 40);

        assertNull("the dragged object is not silently nested into a hidden interior",
                panel.getCanvasModel().enclosingOf(dragged));
        assertTrue("the collapsed box kept its summary size",
                collapsed.getBounds().width == GraphObjectFrame.COLLAPSED_WIDTH);
    }

    @Test
    public void anElementDroppedOnACollapsedObjectStaysOut() {
        PetriNetsPanel panel = freshPanel();
        GraphPetriPlace free = placeAt(panel, "Free", 200, 200);
        GraphPetriPlace pc = placeAt(panel, "PC", 700, 400);
        GraphObjectFrame collapsed = frameWith(panel, "Collapsed", new Rectangle(600, 300, 300, 240), pc);
        collapsed.setCollapsed(true);
        Rectangle box = collapsed.getBounds();

        // Drag the free place onto the collapsed summary box; without arcs there is no
        // confirmation dialog in the way.
        drag(panel, 200, 200, box.x + box.width / 2, box.y + 40);

        assertNull("the element is not claimed by the hidden interior",
                panel.getCanvasModel().ownerOf(free));
    }

    // ------------------------------------------------------------------ arc scope

    @Test
    public void theDeleteToolCannotReachANestedObjectsInternalArc() {
        PetriNetsPanel panel = freshPanel();
        GraphPetriPlace place = placeAt(panel, "P1", 300, 250);
        GraphPetriTransition transition = transitionAt(panel, "T1", 450, 250);
        GraphObjectFrame outer = frameWith(panel, "Outer", new Rectangle(100, 100, 600, 400), null);
        GraphObjectFrame inner = frameWith(panel, "Inner", new Rectangle(240, 180, 320, 160), place);
        panel.getCanvasModel().claim(inner, transition);
        panel.getCanvasModel().nest(inner, outer);
        GraphArcIn arc = new GraphArcIn();
        arc.settingNewArc(place);
        arc.finishSettingNewArc(transition);
        arc.updateCoordinates();
        panel.getGraphNet().getGraphArcInList().add(arc);

        panel.setTool(CanvasTool.DELETE);
        // Click the middle of the arc's line from the ROOT canvas, where Inner is locked. Press
        // and release both: the eraser waits for the release to tell a click from a sweep.
        eraserClick(panel, 375, 250);

        assertEquals("the locked object's internal arc survives the Delete tool",
                1, panel.getGraphNet().getGraphArcInList().size());

        // From the object's own canvas the same click deletes it.
        panel.openObjectCanvas(inner);
        panel.setTool(CanvasTool.DELETE);
        eraserClick(panel, 375, 250);
        assertEquals("on its own canvas the arc is the user's to delete",
                0, panel.getGraphNet().getGraphArcInList().size());
    }

    /**
     * A full eraser click: press and release on the same point. The Delete tool decides on the
     * release, since the press it starts from may still turn out to be the corner of a sweep.
     */
    private static void eraserClick(PetriNetsPanel panel, int x, int y) {
        PetriNetsPanel.MouseHandler handler = mouseHandlerOf(panel);
        handler.mousePressed(event(panel, MouseEvent.MOUSE_PRESSED, x, y));
        handler.mouseReleased(event(panel, MouseEvent.MOUSE_RELEASED, x, y));
    }

    // ------------------------------------------------------------------ click-tool gating

    @Test
    public void aDoubleClickWithThePlaceToolDoesNotOpenThePropertiesDialog() {
        PetriNetsPanel panel = freshPanel();
        panel.setTool(CanvasTool.ADD_PLACE);
        PetriNetsPanel.MouseHandler handler = mouseHandlerOf(panel);

        // A real double-click: press, release, click, press, release, click(2).
        handler.mousePressed(event(panel, MouseEvent.MOUSE_PRESSED, 300, 300));
        handler.mouseReleased(event(panel, MouseEvent.MOUSE_RELEASED, 300, 300));
        handler.mouseClicked(event(panel, MouseEvent.MOUSE_CLICKED, 300, 300));
        handler.mousePressed(event(panel, MouseEvent.MOUSE_PRESSED, 300, 300));
        handler.mouseReleased(event(panel, MouseEvent.MOUSE_RELEASED, 300, 300));
        handler.mouseClicked(new MouseEvent(panel, MouseEvent.MOUSE_CLICKED,
                System.currentTimeMillis(), 0, 300, 300, 2, false, MouseEvent.BUTTON1));

        assertTrue("the stamping tool never opens the properties dialog",
                !panel.setPositionFrame.isVisible());
    }

    // ------------------------------------------------------------------ arc self-click

    @Test(timeout = 10000)
    public void anArcToolClickOnAPlaceIsNotAFusionAttempt() {
        PetriNetsPanel panel = freshPanel();
        placeAt(panel, "P1", 300, 300);
        panel.setIsSettingArc(true);
        PetriNetsPanel.MouseHandler handler = mouseHandlerOf(panel);

        // Press and release on the same place: a plain click. It used to reach
        // joinPlaces(p, p) and pop an error dialog about shared places.
        handler.mousePressed(event(panel, MouseEvent.MOUSE_PRESSED, 300, 300));
        handler.mouseReleased(event(panel, MouseEvent.MOUSE_RELEASED, 300, 300));

        assertTrue("no fusion appears from a plain click",
                panel.getCanvasModel().getFusions().isEmpty());
        assertEquals("and no arc is left behind", 0, panel.getGraphNet().getGraphArcInList().size());
    }

    // ------------------------------------------------------------------ copy scope

    @Test
    public void copyingADeepNestedFrameFromTheRootCanvasCopiesNothing() {
        PetriNetsPanel panel = freshPanel();
        GraphPetriPlace inner = placeAt(panel, "PI", 300, 250);
        GraphObjectFrame outer = frameWith(panel, "Outer", new Rectangle(100, 100, 400, 300), null);
        GraphObjectFrame child = frameWith(panel, "Inner", new Rectangle(240, 190, 160, 120), inner);
        panel.getCanvasModel().nest(child, outer);

        // The marquee happily selects the deep frame; copying it from here must not let a
        // paste rebuild an object inside Outer from the root canvas.
        panel.getSelection().add(child);
        panel.copySelection();
        panel.pasteClipboard();

        assertEquals("no object was created from out-of-scope clipboard content",
                2, panel.getCanvasModel().getFrames().size());
    }

    @Test
    public void groupingCannotYankADeepNestedFrameOutOfItsObject() {
        PetriNetsPanel panel = freshPanel();
        GraphPetriPlace inner = placeAt(panel, "PI", 300, 250);
        GraphPetriPlace free = placeAt(panel, "Free", 700, 500);
        GraphObjectFrame outer = frameWith(panel, "Outer", new Rectangle(100, 100, 400, 300), null);
        GraphObjectFrame child = frameWith(panel, "Inner", new Rectangle(240, 190, 160, 120), inner);
        panel.getCanvasModel().nest(child, outer);

        GraphObjectFrame group = panel.groupIntoObject(
                java.util.List.of(free), java.util.List.of(child), "Group");

        assertSame("the deep-nested frame stays inside its own object",
                outer, panel.getCanvasModel().enclosingOf(child));
        assertSame("only the in-scope content was grouped",
                group, panel.getCanvasModel().ownerOf(free));
    }
}

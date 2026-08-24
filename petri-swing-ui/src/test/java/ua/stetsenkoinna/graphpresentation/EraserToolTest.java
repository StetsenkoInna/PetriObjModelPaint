package ua.stetsenkoinna.graphpresentation;

import org.junit.Test;
import ua.stetsenkoinna.graphnet.GraphArcIn;
import ua.stetsenkoinna.graphnet.GraphPetriPlace;
import ua.stetsenkoinna.graphnet.GraphPetriTransition;
import ua.stetsenkoinna.petriobj.PetriP;
import ua.stetsenkoinna.petriobj.PetriT;

import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionListener;
import java.awt.geom.Point2D;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * The Delete tool: clicking things away, and sweeping them away.
 *
 * <p>Two complaints are pinned here. Erasing did not always work, because the hit tests it
 * relies on are written in canvas units - an arc counts as hit within 3 of them, an element only
 * if the point is literally inside its shape - and those units shrink on screen as the view
 * zooms out, so at half zoom an arc was a pixel-and-a-half ribbon to aim at. And there was no
 * way to remove more than one thing at a time.
 */
public class EraserToolTest {

    private static int idCounter = 1;

    private static PetriNetsPanel freshPanel() {
        PetriP.initNext();
        PetriT.initNext();
        idCounter = 1;
        return new PetriNetsPanel(null, true);
    }

    private static PetriNetsPanel.MouseHandler mouseHandler(PetriNetsPanel panel) {
        for (java.awt.event.MouseListener listener : panel.getMouseListeners()) {
            if (listener instanceof PetriNetsPanel.MouseHandler handler) {
                return handler;
            }
        }
        throw new AssertionError("the panel registered no MouseHandler");
    }

    private static MouseMotionListener motionHandler(PetriNetsPanel panel) {
        return panel.getMouseMotionListeners()[0];
    }

    private static MouseEvent event(PetriNetsPanel panel, int id, int x, int y) {
        return new MouseEvent(panel, id, System.currentTimeMillis(), 0, x, y, 1, false,
                MouseEvent.BUTTON1);
    }

    /** A full click: the eraser decides on the release, not the press. */
    private static void click(PetriNetsPanel panel, int x, int y) {
        mouseHandler(panel).mousePressed(event(panel, MouseEvent.MOUSE_PRESSED, x, y));
        mouseHandler(panel).mouseReleased(event(panel, MouseEvent.MOUSE_RELEASED, x, y));
    }

    private static void sweep(PetriNetsPanel panel, int fromX, int fromY, int toX, int toY) {
        mouseHandler(panel).mousePressed(event(panel, MouseEvent.MOUSE_PRESSED, fromX, fromY));
        motionHandler(panel).mouseDragged(event(panel, MouseEvent.MOUSE_DRAGGED,
                (fromX + toX) / 2, (fromY + toY) / 2));
        motionHandler(panel).mouseDragged(event(panel, MouseEvent.MOUSE_DRAGGED, toX, toY));
        mouseHandler(panel).mouseReleased(event(panel, MouseEvent.MOUSE_RELEASED, toX, toY));
    }

    private static GraphPetriPlace placeAt(PetriNetsPanel panel, String name, int x, int y) {
        GraphPetriPlace place = new GraphPetriPlace(new PetriP(name, 0), idCounter++);
        place.setNewCoordinates(new Point2D.Double(x, y));
        panel.getGraphNet().getGraphPetriPlaceList().add(place);
        return place;
    }

    private static GraphPetriTransition transitionAt(PetriNetsPanel panel, String name, int x, int y) {
        GraphPetriTransition transition =
                new GraphPetriTransition(new PetriT(name, 1.0), idCounter++);
        transition.setNewCoordinates(new Point2D.Double(x, y));
        panel.getGraphNet().getGraphPetriTransitionList().add(transition);
        return transition;
    }

    private static GraphArcIn arcBetween(PetriNetsPanel panel,
                                         GraphPetriPlace from, GraphPetriTransition to) {
        GraphArcIn arc = new GraphArcIn();
        arc.settingNewArc(from);
        arc.finishSettingNewArc(to);
        arc.updateCoordinates();
        panel.getGraphNet().getGraphArcInList().add(arc);
        return arc;
    }

    private static int places(PetriNetsPanel panel) {
        return panel.getGraphNet().getGraphPetriPlaceList().size();
    }

    private static int arcs(PetriNetsPanel panel) {
        return panel.getGraphNet().getGraphArcInList().size();
    }

    // ------------------------------------------------------------------ clicking

    @Test
    public void aClickOnAnElementStillErasesIt() {
        PetriNetsPanel panel = freshPanel();
        placeAt(panel, "P1", 300, 300);
        panel.setTool(CanvasTool.DELETE);

        click(panel, 300, 300);

        assertEquals(0, places(panel));
    }

    /**
     * The near miss. An arc is hit only within 3 canvas units of its line, which is a thin
     * target at the best of times; a click a few units off it used to do nothing at all.
     */
    @Test
    public void aClickJustBesideAnArcStillErasesIt() {
        PetriNetsPanel panel = freshPanel();
        GraphPetriPlace place = placeAt(panel, "P1", 200, 300);
        GraphPetriTransition transition = transitionAt(panel, "T1", 500, 300);
        arcBetween(panel, place, transition);
        panel.setTool(CanvasTool.DELETE);

        // Four canvas units above the line: outside the arc's own 3-unit tolerance.
        click(panel, 350, 296);

        assertEquals("the arc a near miss was aiming at is gone", 0, arcs(panel));
    }

    @Test
    public void aClickOnEmptyCanvasErasesNothing() {
        PetriNetsPanel panel = freshPanel();
        placeAt(panel, "P1", 300, 300);
        panel.setTool(CanvasTool.DELETE);

        click(panel, 900, 900);

        assertEquals(1, places(panel));
    }

    // ------------------------------------------------------------------ sweeping

    @Test
    public void aSweepErasesEveryElementItEncloses() {
        PetriNetsPanel panel = freshPanel();
        placeAt(panel, "P1", 200, 200);
        placeAt(panel, "P2", 260, 240);
        placeAt(panel, "P3", 800, 800);
        panel.setTool(CanvasTool.DELETE);

        sweep(panel, 150, 150, 350, 350);

        assertEquals("both enclosed places went", 1, places(panel));
        assertEquals("and the one outside the band stayed",
                "P3", panel.getGraphNet().getGraphPetriPlaceList().getFirst().getName());
    }

    /**
     * An arc has no centre worth enclosing, so it is caught by being crossed. Sweeping over one
     * plainly means erasing it.
     */
    @Test
    public void aSweepAcrossAnArcErasesIt() {
        PetriNetsPanel panel = freshPanel();
        GraphPetriPlace place = placeAt(panel, "P1", 200, 300);
        GraphPetriTransition transition = transitionAt(panel, "T1", 700, 300);
        arcBetween(panel, place, transition);
        panel.setTool(CanvasTool.DELETE);

        // A band around the middle of the line, touching neither end.
        sweep(panel, 420, 260, 480, 340);

        assertEquals("the arc the band crossed went", 0, arcs(panel));
        assertEquals("its ends were not enclosed, so they stayed", 1, places(panel));
    }

    /**
     * A whole sweep is one gesture, so it takes one undo. Undoing it a piece at a time would
     * make a wide stroke tedious to take back, which is when a user most wants to.
     */
    @Test
    public void aSweepComesBackOnOneUndo() {
        PetriNetsPanel panel = freshPanel();
        placeAt(panel, "P1", 200, 200);
        placeAt(panel, "P2", 260, 240);
        placeAt(panel, "P3", 300, 300);
        javax.swing.undo.UndoManager undo = new javax.swing.undo.UndoManager();
        PetriNetsFrame.getUndoSupport().addUndoableEditListener(undo);
        panel.setTool(CanvasTool.DELETE);

        sweep(panel, 150, 150, 400, 400);
        assertEquals("all three went", 0, places(panel));

        undo.undo();

        assertEquals("and all three came back together", 3, places(panel));
    }

    /**
     * The threshold. Any real click wobbles by a pixel; without one, that wobble would be read
     * as a band enclosing nothing, and the eraser would answer an ordinary click by doing
     * nothing - exactly the complaint this change is about.
     */
    @Test
    public void aWobbleDuringAClickIsStillAClick() {
        PetriNetsPanel panel = freshPanel();
        placeAt(panel, "P1", 300, 300);
        panel.setTool(CanvasTool.DELETE);

        mouseHandler(panel).mousePressed(event(panel, MouseEvent.MOUSE_PRESSED, 300, 300));
        motionHandler(panel).mouseDragged(event(panel, MouseEvent.MOUSE_DRAGGED, 301, 300));
        mouseHandler(panel).mouseReleased(event(panel, MouseEvent.MOUSE_RELEASED, 301, 300));

        assertEquals("a one-pixel wobble erased the thing under the pointer", 0, places(panel));
    }

    @Test
    public void aSweepOverEmptyCanvasErasesNothing() {
        PetriNetsPanel panel = freshPanel();
        placeAt(panel, "P1", 300, 300);
        panel.setTool(CanvasTool.DELETE);

        sweep(panel, 700, 700, 900, 900);

        assertEquals(1, places(panel));
    }

    /** The tool stays armed across strokes, the way the other stamping tools do. */
    @Test
    public void theEraserStaysArmedAfterAStroke() {
        PetriNetsPanel panel = freshPanel();
        placeAt(panel, "P1", 200, 200);
        placeAt(panel, "P2", 600, 600);
        panel.setTool(CanvasTool.DELETE);

        sweep(panel, 150, 150, 300, 300);
        click(panel, 600, 600);

        assertTrue("the second gesture erased too", places(panel) == 0);
    }
}

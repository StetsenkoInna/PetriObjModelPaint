package ua.stetsenkoinna.graphpresentation;

import org.junit.Test;
import ua.stetsenkoinna.graphpresentation.input.InputShortcuts;
import ua.stetsenkoinna.petriobj.PetriP;
import ua.stetsenkoinna.petriobj.PetriT;

import javax.swing.JViewport;
import java.awt.Point;
import java.awt.event.InputEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.event.MouseWheelListener;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

/**
 * What the wheel and the trackpad do to the canvas.
 *
 * <p>Two things are being held here. That a plain wheel <em>scrolls</em>: the canvas is a fixed
 * 20000x20000 panel in a scroll pane with both scrollbars turned off, so before this the gesture
 * zoomed and there was no way to reach anything off-screen except the Pan tool - which on a
 * laptop trackpad, with no middle button either, left the user stuck. And that fractional
 * movement accumulates: a trackpad reports fractions of a notch, and reading the int
 * {@code getWheelRotation()} threw all of them away, so a slow two-finger drag did nothing at
 * all until it was fast enough to round up to a whole notch and jump.
 */
public class CanvasWheelTest {

    private PetriNetsPanel panel;
    private JViewport viewport;

    private void canvasInAViewportAt(int viewX, int viewY) {
        PetriP.initNext();
        PetriT.initNext();
        panel = new PetriNetsPanel(null, true);
        viewport = new JViewport();
        viewport.setView(panel);
        viewport.setSize(400, 300);
        viewport.doLayout();
        viewport.setViewPosition(new Point(viewX, viewY));
    }

    private MouseWheelListener wheel() {
        MouseWheelListener[] listeners = panel.getMouseWheelListeners();
        assertTrue("the panel registered no mouse wheel listener", listeners.length > 0);
        return listeners[0];
    }

    private void spin(int modifiers, double notches) {
        wheel().mouseWheelMoved(new MouseWheelEvent(panel, MouseWheelEvent.MOUSE_WHEEL,
                System.currentTimeMillis(), modifiers, 200, 150, 0, 0, 1, false,
                MouseWheelEvent.WHEEL_UNIT_SCROLL, 3, (int) notches, notches));
    }

    @Test
    public void plainWheelScrollsTheViewAndLeavesTheZoomAlone() {
        canvasInAViewportAt(1000, 1000);
        double before = panel.getScale();

        spin(0, 1);

        assertEquals("zoom must not change", before, panel.getScale(), 1e-9);
        assertTrue("scrolling down moves the view down",
                viewport.getViewPosition().y > 1000);
        assertEquals("and not sideways", 1000, viewport.getViewPosition().x);
    }

    @Test
    public void wheelUpScrollsBackTowardsTheTop() {
        canvasInAViewportAt(1000, 1000);

        spin(0, -1);

        assertTrue(viewport.getViewPosition().y < 1000);
    }

    /**
     * Shift+wheel on a mouse, and equally how the macOS JDK reports a two-finger horizontal
     * swipe on a trackpad.
     */
    @Test
    public void shiftWheelScrollsSideways() {
        canvasInAViewportAt(1000, 1000);

        spin(InputEvent.SHIFT_DOWN_MASK, 1);

        assertTrue("moves horizontally", viewport.getViewPosition().x > 1000);
        assertEquals("and not vertically", 1000, viewport.getViewPosition().y);
    }

    /**
     * The shortcut modifier is what zooms - Command on macOS, Control elsewhere. Notably not
     * Control on macOS, where Control+scroll is the system's own screen magnifier and would
     * never reach the application anyway.
     */
    @Test
    public void shortcutWheelZooms() {
        canvasInAViewportAt(1000, 1000);
        double before = panel.getScale();

        spin(InputShortcuts.menuMask(), -1);

        assertTrue("scrolling up with the modifier zooms in", panel.getScale() > before);

        spin(InputShortcuts.menuMask(), 2);
        assertTrue("and down zooms out again", panel.getScale() < before);
    }

    /**
     * The trackpad case. Six events of a third of a notch each must add up to real movement;
     * truncating each one to an int would leave the view exactly where it started.
     */
    @Test
    public void fractionalTrackpadMovementAccumulatesInsteadOfBeingLost() {
        canvasInAViewportAt(1000, 1000);

        for (int i = 0; i < 6; i++) {
            spin(0, 0.3);
        }

        assertNotEquals("six tenths of a notch of scrolling went nowhere",
                1000, viewport.getViewPosition().y);
        assertTrue(viewport.getViewPosition().y > 1000);
    }

    /** A zero-delta event - which trackpads do emit - must not be mistaken for a gesture. */
    @Test
    public void anEmptyEventChangesNothing() {
        canvasInAViewportAt(1000, 1000);
        double before = panel.getScale();

        spin(0, 0);

        assertEquals(new Point(1000, 1000), viewport.getViewPosition());
        assertEquals(before, panel.getScale(), 1e-9);
    }

    /** Zoom stays inside the range the canvas can actually draw at, however hard it is spun. */
    @Test
    public void zoomCannotBeSpunOutOfRange() {
        canvasInAViewportAt(1000, 1000);

        for (int i = 0; i < 200; i++) {
            spin(InputShortcuts.menuMask(), 5);
        }
        assertTrue("scale stayed positive", panel.getScale() > 0);
        assertEquals(0.1, panel.getScale(), 1e-9);

        for (int i = 0; i < 200; i++) {
            spin(InputShortcuts.menuMask(), -5);
        }
        assertEquals(5.0, panel.getScale(), 1e-9);
    }
}

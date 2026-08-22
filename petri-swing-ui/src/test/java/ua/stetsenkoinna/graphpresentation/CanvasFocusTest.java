package ua.stetsenkoinna.graphpresentation;

import org.junit.Assume;
import org.junit.Test;

import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.SwingUtilities;
import java.awt.Component;
import java.awt.Container;
import java.awt.GraphicsEnvironment;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * The canvas does not take the keyboard focus while it paints.
 *
 * <p>It used to, on every single paint, and that quietly broke anything opened over it. Showing
 * a drop-down repaints the canvas behind it, the repaint pulled focus back to the canvas, and a
 * drop-down without focus closes itself: the time-unit list opened and shut again within the
 * frame, every time, so the setting could not be changed at all.
 *
 * <p>Painting is not a gesture. What this pins is that only gestures move focus - a press on the
 * canvas, or picking a tool for it - so a repaint underneath an open popup leaves it alone.
 */
public class CanvasFocusTest {

    @Test
    public void repaintingTheCanvasLeavesAnOpenDropDownOpen() throws Exception {
        Assume.assumeFalse("needs a real display", GraphicsEnvironment.isHeadless());

        JFrame[] holder = new JFrame[1];
        SwingUtilities.invokeAndWait(() -> {
            PetriNetsFrame frame = new PetriNetsFrame();
            frame.setExtendedState(JFrame.NORMAL);
            frame.setSize(1200, 700);
            frame.setLocation(10, 10);
            frame.setVisible(true);
            holder[0] = frame;
        });
        PetriNetsFrame frame = (PetriNetsFrame) holder[0];
        try {
            PetriNetsPanel canvas = frame.getPetriNetsPanel();
            JComboBox<?> combo = find(frame, JComboBox.class);
            assertNotNull("the parameters row has a time-unit drop-down", combo);
            Thread.sleep(600);

            // What a click on it does: focus lands on the drop-down, then the list opens.
            SwingUtilities.invokeAndWait(combo::requestFocusInWindow);
            Thread.sleep(200);
            SwingUtilities.invokeAndWait(() -> combo.setPopupVisible(true));
            Thread.sleep(150);
            Assume.assumeTrue("the drop-down did not open at all", combo.isPopupVisible());

            for (int i = 0; i < 6; i++) {
                SwingUtilities.invokeAndWait(() ->
                        canvas.paintImmediately(0, 0, canvas.getWidth(), canvas.getHeight()));
                Thread.sleep(80);
            }

            assertTrue("the canvas repainting underneath must not close it",
                    combo.isPopupVisible());
        } finally {
            SwingUtilities.invokeAndWait(frame::dispose);
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> T find(Container root, Class<T> type) {
        for (Component child : root.getComponents()) {
            if (type.isInstance(child)) {
                return (T) child;
            }
            if (child instanceof Container container) {
                T found = find(container, type);
                if (found != null) {
                    return found;
                }
            }
        }
        return root instanceof JFrame frame ? find(frame.getContentPane(), type) : null;
    }
}

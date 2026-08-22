package ua.stetsenkoinna.graphpresentation;

import org.junit.Assume;
import org.junit.Test;

import javax.swing.JFrame;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import java.awt.Component;
import java.awt.Container;
import java.awt.GraphicsEnvironment;
import java.awt.KeyboardFocusManager;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;

/**
 * The canvas does not take the keyboard focus while it paints.
 *
 * <p>It used to, on every single paint, and that quietly broke anything opened over it or typed
 * into beside it. Showing a drop-down repaints the canvas behind it, the repaint pulled focus
 * back to the canvas, and a drop-down without focus closes itself: the time-unit list opened and
 * shut again within the frame, every time, so the setting could not be changed at all.
 *
 * <p>Painting is not a gesture. What this pins is that only gestures move focus - a press on the
 * canvas, or picking a tool for it - so whatever the user is working in keeps the focus while the
 * canvas repaints underneath.
 */
public class CanvasFocusTest {

    @Test
    public void repaintingTheCanvasLeavesTheFocusWhereTheUserPutIt() throws Exception {
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
            JTextField field = find(frame, JTextField.class);
            assertNotNull("the window has a text field to type into", field);
            Thread.sleep(600);

            SwingUtilities.invokeAndWait(field::requestFocusInWindow);
            Thread.sleep(250);
            Assume.assumeTrue("the field never got focus to begin with",
                    field == KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner());

            for (int i = 0; i < 6; i++) {
                SwingUtilities.invokeAndWait(() ->
                        canvas.paintImmediately(0, 0, canvas.getWidth(), canvas.getHeight()));
                Thread.sleep(80);
            }

            assertSame("the canvas repainting underneath must not take the focus",
                    field, KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner());
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

package ua.stetsenkoinna.graphpresentation.importimage;

import javax.swing.*;
import javax.swing.plaf.basic.BasicProgressBarUI;
import java.awt.*;

/**
 * A modal-like non-blocking dialog that displays an indeterminate loading animation
 * in the form of a circular spinner. This dialog is intended to be shown during
 * long-running background tasks (e.g., model recognition, file loading, network calls)
 * to inform the user that an operation is in progress.
 *
 * <p>The dialog uses a custom {@link SpinnerProgressBarUI} to transform a standard
 * {@link JProgressBar} into a circular animated spinner without requiring any
 * external libraries.
 *
 * <p>Example usage:
 * <pre>
 * LoadingDialog dialog = new LoadingDialog(parentFrame, "Processing...");
 * dialog.setVisible(true);
 * // Run background task...
 * dialog.dispose();
 * </pre>
 *
 * @author Bohdan Hrontkovskyi
 * @since 14.11.2025
 */
public class LoadingDialog extends JDialog {

    private final JProgressBar progressBar;
    private final JLabel messageLabel;

    /**
     * Creates a new loading dialog containing a title, a message label,
     * and a circular indeterminate progress indicator.
     *
     * @param parent  the parent frame used for positioning the dialog
     * @param message the initial message to display above the spinner
     */
    public LoadingDialog(Frame parent, String message) {
        super(parent, false);
        setSize(300, 120);
        setLocationRelativeTo(parent);
        setResizable(false);

        setTitle("Petri Net Recognition");
        setLayout(new BorderLayout(10, 10));
        setDefaultCloseOperation(JDialog.DO_NOTHING_ON_CLOSE);

        messageLabel = new JLabel(message, SwingConstants.CENTER);

        progressBar = new JProgressBar();
        progressBar.setIndeterminate(true);
        progressBar.setUI(new SpinnerProgressBarUI());
        progressBar.setPreferredSize(new Dimension(100, 100));

        add(messageLabel, BorderLayout.NORTH);
        add(progressBar, BorderLayout.CENTER);
    }

    /**
     * Updates the text displayed above the spinner.
     *
     * @param message the new status message to display
     */
    public void setMessage(String message) {
        messageLabel.setText(message);
    }

    /**
     * A custom UI delegate for {@link JProgressBar} that overrides the default
     * indeterminate progress bar painting logic and replaces it with a circular
     * animated spinner.
     *
     * <p>The spinner consists of:
     * <ul>
     *     <li>a faint circular background ring</li>
     *     <li>a rotating colored arc representing progress motion</li>
     * </ul>
     *
     * <p>The animation is driven by repainting the component on each frame and
     * computing the arc's angle based on system time.
     */
    private static class SpinnerProgressBarUI extends BasicProgressBarUI {
        @Override
        protected void paintIndeterminate(Graphics graphics, JComponent component) {
            Graphics2D g2d = (Graphics2D) graphics.create();
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            int width = component.getWidth();
            int height = component.getHeight();
            int size = Math.min(width, height) - 10;
            int strokeWidth = 6;

            int x = (width - size) / 2;
            int y = (height - size) / 2;

            g2d.setStroke(new BasicStroke(strokeWidth, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g2d.setColor(new Color(200, 200, 200, 100));
            g2d.drawOval(x, y, size, size);

            g2d.setColor(new Color(33, 150, 243));
            int angle = (int) ((System.currentTimeMillis() / 8) % 360);
            g2d.drawArc(x, y, size, size, angle, 90);

            g2d.dispose();

            component.repaint();
        }
    }
}

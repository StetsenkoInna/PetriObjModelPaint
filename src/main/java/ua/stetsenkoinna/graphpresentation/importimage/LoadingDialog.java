package ua.stetsenkoinna.graphpresentation.importimage;

import javax.swing.*;
import javax.swing.plaf.basic.BasicProgressBarUI;
import java.awt.*;

public class LoadingDialog extends JDialog {

    private final JProgressBar progressBar;
    private final JLabel messageLabel;

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

    public void setMessage(String message) {
        messageLabel.setText(message);
    }

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

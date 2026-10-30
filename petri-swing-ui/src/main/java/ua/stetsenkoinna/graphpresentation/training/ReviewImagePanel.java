package ua.stetsenkoinna.graphpresentation.training;

import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.function.Consumer;
import javax.swing.JComponent;

/** Region comments use source image pixels, independent of view zoom and screen DPI. */
public final class ReviewImagePanel extends JComponent {
    public record Mark(TrainingBundleWorkspace.Region region, String comment) { }
    private final Consumer<TrainingBundleWorkspace.Region> selected;
    private BufferedImage image;
    private String sha256;
    private double zoom = 1;
    private Point start;
    private Rectangle2D selection;
    private List<Mark> marks = List.of();
    private boolean markingEnabled;

    public ReviewImagePanel(Consumer<TrainingBundleWorkspace.Region> selected) {
        this.selected = selected;
        setToolTipText("Drag a rectangle to attach a finding to this image region");
        MouseAdapter mouse = new MouseAdapter() {
            @Override public void mousePressed(MouseEvent event) {
                if (!markingEnabled || image == null || event.getButton() != MouseEvent.BUTTON1) return;
                start = pixel(event); selection = null;
            }
            @Override public void mouseDragged(MouseEvent event) { if (start != null) { selection = rectangle(start, pixel(event)); repaint(); } }
            @Override public void mouseReleased(MouseEvent event) {
                if (start == null) return;
                selection = rectangle(start, pixel(event)); start = null; repaint();
                if (selection.getWidth() >= 2 && selection.getHeight() >= 2) selected.accept(new TrainingBundleWorkspace.Region(selection.getX(), selection.getY(), selection.getWidth(), selection.getHeight(), sha256));
            }
        };
        addMouseListener(mouse); addMouseMotionListener(mouse);
    }

    public void setImage(byte[] png, boolean markingEnabled, List<Mark> marks) throws Exception {
        image = TrainingBundleWorkspace.readImage(png); sha256 = TrainingBundleWorkspace.hash(png);
        this.markingEnabled = markingEnabled; this.marks = List.copyOf(marks); selection = null; start = null;
        setCursor(Cursor.getPredefinedCursor(markingEnabled ? Cursor.CROSSHAIR_CURSOR : Cursor.DEFAULT_CURSOR));
        updateSize();
    }
    public void setZoom(double zoom) { if (zoom <= 0 || zoom > 4) throw new IllegalArgumentException("Invalid image zoom"); this.zoom = zoom; updateSize(); }
    public void clearSelection() { selection = null; repaint(); }
    private void updateSize() {
        if (image != null) setPreferredSize(new Dimension((int) Math.ceil(image.getWidth() * zoom), (int) Math.ceil(image.getHeight() * zoom)));
        revalidate(); repaint();
    }
    private Point pixel(MouseEvent event) { return new Point(Math.max(0, Math.min(image.getWidth(), (int) Math.round(event.getX() / zoom))), Math.max(0, Math.min(image.getHeight(), (int) Math.round(event.getY() / zoom)))); }
    private Rectangle2D rectangle(Point first, Point last) { return new Rectangle2D.Double(Math.min(first.x, last.x), Math.min(first.y, last.y), Math.abs(last.x - first.x), Math.abs(last.y - first.y)); }

    @Override protected void paintComponent(Graphics graphics) {
        Graphics2D painter = (Graphics2D) graphics.create();
        try {
            painter.setColor(getBackground() == null ? Color.WHITE : getBackground()); painter.fillRect(0, 0, getWidth(), getHeight());
            if (image == null) return;
            painter.scale(zoom, zoom); painter.drawImage(image, 0, 0, null);
            painter.setStroke(new BasicStroke((float) (2 / zoom)));
            for (Mark mark : marks) {
                var region = mark.region(); painter.setColor(new Color(225, 125, 20)); painter.draw(new Rectangle2D.Double(region.x(), region.y(), region.width(), region.height()));
            }
            if (selection != null) {
                painter.setColor(new Color(220, 50, 40, 40)); painter.fill(selection);
                painter.setColor(new Color(220, 50, 40)); painter.draw(selection);
            }
        } finally { painter.dispose(); }
    }

    @Override public String getToolTipText(MouseEvent event) {
        if (image != null) {
            Point point = pixel(event);
            for (Mark mark : marks) if (new Rectangle2D.Double(mark.region().x(), mark.region().y(), mark.region().width(), mark.region().height()).contains(point)) return "Finding: " + mark.comment();
        }
        return markingEnabled ? "Drag a rectangle to create a review finding" : "QA screenshot; region comments use network views";
    }
}

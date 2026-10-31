package ua.stetsenkoinna.graphpresentation.input;

import java.awt.Point;
import java.awt.geom.Point2D;

/** Converts model coordinates to a finite Swing backing surface that can be rebased. */
public final class CanvasCamera {
    private double originX;
    private double originY;

    public Point2D.Double toWorld(Point2D point, double scale) {
        return new Point2D.Double((point.getX() - originX) / scale,
                (point.getY() - originY) / scale);
    }

    public Point2D.Double toPanel(Point2D point, double scale) {
        return new Point2D.Double(originX + point.getX() * scale,
                originY + point.getY() * scale);
    }

    public void center(double worldX, double worldY, double panelX, double panelY, double scale) {
        originX = panelX - worldX * scale;
        originY = panelY - worldY * scale;
    }

    public void rebase(double dx, double dy) {
        originX += dx;
        originY += dy;
    }

    public Point2D.Double getOrigin() { return new Point2D.Double(originX, originY); }

    /**
     * Picks a new backing position before Swing could clamp a scroll at an edge.
     * The caller applies the returned difference to the camera and active drag anchor.
     */
    public static Point backingPosition(Point requested, int maxX, int maxY) {
        int x = requested.x < 128 || requested.x > maxX - 128 ? maxX / 2 : requested.x;
        int y = requested.y < 128 || requested.y > maxY - 128 ? maxY / 2 : requested.y;
        return new Point(x, y);
    }
}

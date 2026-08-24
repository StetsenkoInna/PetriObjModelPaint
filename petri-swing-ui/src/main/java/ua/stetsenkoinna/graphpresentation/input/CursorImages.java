package ua.stetsenkoinna.graphpresentation.input;

import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.Point;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;

/**
 * Preparing an image for {@code Toolkit.createCustomCursor}, which is fussier than it looks.
 *
 * <p>Two things go wrong if an image is handed over as-is. Every platform accepts exactly one
 * cursor size, reported by {@code getBestCursorSize}, and anything else is scaled or padded by
 * the toolkit with results that differ between Windows and macOS - the eraser is a 64x64 asset
 * and Windows asks for 32x32. And the hotspot must lie inside the image: a hotspot outside its
 * bounds throws, which for a cursor built from an image that has not finished loading (and so
 * still measures -1 by -1) means the whole thing collapses to a fallback cursor without ever
 * saying why.
 *
 * <p>The toolkit call itself stays with the caller: {@code getBestCursorSize} needs a display and
 * throws headless, whereas everything here is arithmetic and {@link BufferedImage}, so it can be
 * tested.
 */
public final class CursorImages {

    private CursorImages() {
    }

    /**
     * Redraws an image at exactly the size the platform wants its cursors, keeping the aspect
     * ratio and centring what is left over.
     *
     * @param source the loaded image; must already have real dimensions, which means loading it
     *        through something synchronous such as {@code ImageIcon} rather than
     *        {@code Toolkit.getImage}, whose result is still empty when it returns
     * @param best   what {@code Toolkit.getBestCursorSize} asked for
     * @return an image of exactly {@code best}, or null if either size is unusable - the latter
     *         being how a platform says it does not support custom cursors at all
     */
    public static BufferedImage fitToCursor(Image source, Dimension best) {
        if (source == null || best == null || best.width <= 0 || best.height <= 0) {
            return null;
        }
        int sourceWidth = source.getWidth(null);
        int sourceHeight = source.getHeight(null);
        if (sourceWidth <= 0 || sourceHeight <= 0) {
            return null;
        }
        BufferedImage fitted = new BufferedImage(best.width, best.height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = fitted.createGraphics();
        try {
            g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                    RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            double factor = Math.min(
                    (double) best.width / sourceWidth,
                    (double) best.height / sourceHeight);
            int width = Math.max(1, (int) Math.round(sourceWidth * factor));
            int height = Math.max(1, (int) Math.round(sourceHeight * factor));
            g2.drawImage(source, (best.width - width) / 2, (best.height - height) / 2,
                    width, height, null);
        } finally {
            g2.dispose();
        }
        return fitted;
    }

    /**
     * Holds a hotspot inside the image it belongs to.
     *
     * @param desired where the hotspot should be
     * @param size    the cursor image's size
     * @return the hotspot, moved inside the bounds if it was outside them
     */
    public static Point clampHotspot(Point desired, Dimension size) {
        int x = Math.max(0, Math.min(desired.x, size.width - 1));
        int y = Math.max(0, Math.min(desired.y, size.height - 1));
        return new Point(x, y);
    }
}

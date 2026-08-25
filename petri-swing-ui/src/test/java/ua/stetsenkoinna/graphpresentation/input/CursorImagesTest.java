package ua.stetsenkoinna.graphpresentation.input;

import org.junit.Test;

import java.awt.Dimension;
import java.awt.Point;
import java.awt.image.BufferedImage;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

/**
 * That a cursor image is handed to the toolkit in the one shape it accepts.
 */
public class CursorImagesTest {

    private static BufferedImage image(int width, int height) {
        return new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
    }

    /**
     * The eraser asset is 64x64 and Windows asks for 32x32. Handing over the original and
     * letting each toolkit resize it its own way is the difference this exists to remove.
     */
    @Test
    public void anOversizeAssetComesBackAtExactlyTheRequestedSize() {
        BufferedImage fitted = CursorImages.fitToCursor(image(64, 64), new Dimension(32, 32));

        assertNotNull(fitted);
        assertEquals(32, fitted.getWidth());
        assertEquals(32, fitted.getHeight());
    }

    @Test
    public void anUndersizeAssetIsAlsoBroughtToTheRequestedSize() {
        BufferedImage fitted = CursorImages.fitToCursor(image(16, 16), new Dimension(48, 48));

        assertNotNull(fitted);
        assertEquals(48, fitted.getWidth());
        assertEquals(48, fitted.getHeight());
    }

    @Test
    public void aNonSquareRequestStillProducesExactlyThoseDimensions() {
        BufferedImage fitted = CursorImages.fitToCursor(image(64, 64), new Dimension(32, 24));

        assertNotNull(fitted);
        assertEquals(32, fitted.getWidth());
        assertEquals(24, fitted.getHeight());
    }

    /**
     * A zero best-cursor-size is how a platform says it supports no custom cursor at all. It has
     * to be answered with null so the caller falls back, not with an empty image the toolkit
     * would reject.
     */
    @Test
    public void aPlatformWithoutCustomCursorsGetsNothingBack() {
        assertNull(CursorImages.fitToCursor(image(64, 64), new Dimension(0, 0)));
        assertNull(CursorImages.fitToCursor(image(64, 64), null));
    }

    /**
     * The failure the eraser actually suffered: an image that has not finished loading reports
     * -1 by -1, and a cursor built from it throws rather than degrading.
     */
    @Test
    public void anUnloadedImageIsRefusedRatherThanPassedOn() {
        assertNull(CursorImages.fitToCursor(null, new Dimension(32, 32)));
    }

    @Test
    public void aHotspotOutsideTheImageIsBroughtBackInside() {
        assertEquals(new Point(31, 31),
                CursorImages.clampHotspot(new Point(64, 64), new Dimension(32, 32)));
        assertEquals(new Point(0, 0),
                CursorImages.clampHotspot(new Point(-5, -5), new Dimension(32, 32)));
    }

    @Test
    public void aHotspotAlreadyInsideIsLeftAlone() {
        assertEquals(new Point(4, 7),
                CursorImages.clampHotspot(new Point(4, 7), new Dimension(32, 32)));
    }
}

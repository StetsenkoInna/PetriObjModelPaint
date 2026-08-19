package ua.stetsenkoinna.graphpresentation.welcome;

import org.junit.Test;

import javax.swing.Icon;
import java.awt.image.BufferedImage;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Pure image-buffer assertions for {@link ProjectAvatarGenerator}. No Swing display is
 * needed, so these run fine headless.
 */
public class ProjectAvatarGeneratorTest {

    @Test
    public void sameSeedProducesPixelIdenticalImages() {
        BufferedImage first = ProjectAvatarGenerator.renderImage("alpha-project", 100);
        BufferedImage second = ProjectAvatarGenerator.renderImage("alpha-project", 100);

        assertEquals(first.getWidth(), second.getWidth());
        assertEquals(first.getHeight(), second.getHeight());
        assertTrue("same seed must render byte-identical images", imagesEqual(first, second));
    }

    @Test
    public void sameSeedProducesPixelIdenticalImagesAcrossRepeatedCalls() {
        // Guards against any hidden mutable/random state surviving between calls.
        BufferedImage reference = ProjectAvatarGenerator.renderImage("repeatability-check", 64);
        for (int i = 0; i < 5; i++) {
            BufferedImage repeat = ProjectAvatarGenerator.renderImage("repeatability-check", 64);
            assertTrue("call #" + i + " must match the reference image", imagesEqual(reference, repeat));
        }
    }

    @Test
    public void differentSeedsProduceDifferentImages() {
        BufferedImage a = ProjectAvatarGenerator.renderImage("project-one", 100);
        BufferedImage b = ProjectAvatarGenerator.renderImage("project-two", 100);

        assertFalse("different seeds should render visibly different images", imagesEqual(a, b));
    }

    @Test
    public void backgroundIsGenuinelyTransparentForSeveralSeeds() {
        String[] seeds = {"project-one", "project-two", "another-seed", "yet-another-seed"};
        for (String seed : seeds) {
            BufferedImage image = ProjectAvatarGenerator.renderImage(seed, 100);
            assertTrue(
                    "expected at least one fully-transparent pixel for seed '" + seed + "'",
                    hasFullyTransparentPixel(image));
        }
    }

    @Test
    public void imageTypeIsIntArgb() {
        BufferedImage image = ProjectAvatarGenerator.renderImage("type-check", 50);
        assertEquals(BufferedImage.TYPE_INT_ARGB, image.getType());
    }

    @Test
    public void renderedImageHasRequestedDimensions() {
        BufferedImage image = ProjectAvatarGenerator.renderImage("dimension-check", 77);
        assertEquals(77, image.getWidth());
        assertEquals(77, image.getHeight());
    }

    @Test
    public void generateReturnsUsableIcon() {
        Icon icon = ProjectAvatarGenerator.generate("icon-check", 64);
        assertNotNull(icon);
        assertEquals(64, icon.getIconWidth());
        assertEquals(64, icon.getIconHeight());
    }

    private static boolean imagesEqual(BufferedImage a, BufferedImage b) {
        if (a.getWidth() != b.getWidth() || a.getHeight() != b.getHeight()) {
            return false;
        }
        for (int y = 0; y < a.getHeight(); y++) {
            for (int x = 0; x < a.getWidth(); x++) {
                if (a.getRGB(x, y) != b.getRGB(x, y)) {
                    return false;
                }
            }
        }
        return true;
    }

    private static boolean hasFullyTransparentPixel(BufferedImage image) {
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                int argb = image.getRGB(x, y);
                int alpha = (argb >>> 24) & 0xFF;
                if (alpha == 0) {
                    return true;
                }
            }
        }
        return false;
    }
}

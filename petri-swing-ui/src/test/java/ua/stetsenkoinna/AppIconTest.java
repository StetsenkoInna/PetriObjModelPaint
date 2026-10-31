package ua.stetsenkoinna;

import org.junit.Test;

import java.awt.Image;
import java.util.List;

import static org.junit.Assert.assertEquals;

/**
 * Every size of the app icon is on the classpath and decodes. A missing or unreadable file would
 * not fail anything else: the window would quietly fall back to the Java coffee cup.
 */
public class AppIconTest {

    @Test
    public void everySizeLoadsAtItsOwnSize() {
        List<Image> images = AppIcon.images();
        int[] sizes = {16, 20, 24, 32, 40, 48, 64, 128, 256, 512};
        assertEquals("one image per shipped size", sizes.length, images.size());
        for (int i = 0; i < sizes.length; i++) {
            assertEquals(sizes[i], images.get(i).getWidth(null));
            assertEquals(sizes[i], images.get(i).getHeight(null));
        }
    }
}

package ua.stetsenkoinna.graphpresentation.training;

import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import javax.imageio.ImageIO;
import javax.swing.SwingUtilities;
import org.junit.Test;
import static org.junit.Assert.*;

public class ReviewImagePanelTest {
    @Test public void dragAtHalfZoomStoresSourceImagePixelsAndTheExactPngHash() throws Exception {
        var bytes = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(300, 200, BufferedImage.TYPE_INT_RGB), "png", bytes);
        AtomicReference<TrainingBundleWorkspace.Region> selected = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            try {
                var panel = new ReviewImagePanel(selected::set);
                panel.setImage(bytes.toByteArray(), true, List.of()); panel.setZoom(0.5); panel.setSize(panel.getPreferredSize());
                panel.dispatchEvent(new MouseEvent(panel, MouseEvent.MOUSE_PRESSED, 0, MouseEvent.BUTTON1_DOWN_MASK, 10, 15, 1, false, MouseEvent.BUTTON1));
                panel.dispatchEvent(new MouseEvent(panel, MouseEvent.MOUSE_DRAGGED, 1, MouseEvent.BUTTON1_DOWN_MASK, 50, 45, 0, false, MouseEvent.NOBUTTON));
                panel.dispatchEvent(new MouseEvent(panel, MouseEvent.MOUSE_RELEASED, 2, 0, 50, 45, 1, false, MouseEvent.BUTTON1));
            } catch (Exception failure) { throw new RuntimeException(failure); }
        });
        var region = selected.get(); assertNotNull(region);
        assertEquals(20, region.x(), 0); assertEquals(30, region.y(), 0);
        assertEquals(80, region.width(), 0); assertEquals(60, region.height(), 0);
        assertEquals(TrainingBundleWorkspace.hash(bytes.toByteArray()), region.imageSha256());
    }
}

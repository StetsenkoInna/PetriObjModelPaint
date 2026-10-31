package ua.stetsenkoinna.graphpresentation;

import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.geom.Point2D;
import java.io.ByteArrayInputStream;
import java.util.concurrent.atomic.AtomicReference;
import javax.imageio.ImageIO;
import javax.swing.JViewport;
import org.junit.Test;
import ua.stetsenkoinna.graphnet.GraphPetriPlace;
import ua.stetsenkoinna.graphpresentation.input.InputShortcuts;
import ua.stetsenkoinna.graphpresentation.training.NetworkImageExporter;
import ua.stetsenkoinna.graphpresentation.training.SwingCapture;
import ua.stetsenkoinna.petriobj.PetriP;
import static org.junit.Assert.*;

public class InfiniteCanvasTest {
    private static JViewport viewport(PetriNetsPanel panel) {
        JViewport view = new JViewport(); view.setView(panel); view.setSize(400, 300); view.doLayout();
        return view;
    }
    private static void close(PetriNetsPanel panel) {
        panel.setArcFrame.dispose(); panel.setPositionFrame.dispose(); panel.setTransitionFrame.dispose();
    }
    private static MouseEvent event(PetriNetsPanel panel, JViewport view, int id, int x, int y) {
        Point offset = view.getViewPosition();
        return new MouseEvent(panel, id, System.currentTimeMillis(), 0,
                offset.x + x, offset.y + y, 1, false, MouseEvent.BUTTON1);
    }
    private static void press(PetriNetsPanel panel, MouseEvent event) {
        for (var listener : panel.getMouseListeners()) listener.mousePressed(event);
    }
    private static void release(PetriNetsPanel panel, MouseEvent event) {
        for (var listener : panel.getMouseListeners()) listener.mouseReleased(event);
    }
    private static void drag(PetriNetsPanel panel, MouseEvent event) {
        for (var listener : panel.getMouseMotionListeners()) listener.mouseDragged(event);
    }

    @Test public void originIsCenteredAndMouseCreationUsesNegativeWorldCoordinates() throws Exception {
        SwingCapture.call(() -> {
            var panel = new PetriNetsPanel(null, true);
            try {
                var view = viewport(panel); panel.setCamera(0, 0, 1);
                assertEquals(-200, panel.getVisibleWorldBounds().x, 0.001);
                assertEquals(-150, panel.getVisibleWorldBounds().y, 0.001);
                panel.setTool(CanvasTool.ADD_PLACE);
                press(panel, event(panel, view, MouseEvent.MOUSE_PRESSED, 100, 80));
                release(panel, event(panel, view, MouseEvent.MOUSE_RELEASED, 100, 80));
                var place = panel.getGraphNet().getGraphPetriPlaceList().getFirst();
                assertEquals(-100, place.getGraphElementCenter().getX(), 0.001);
                assertEquals(-70, place.getGraphElementCenter().getY(), 0.001);
                panel.setNullPanel();
                assertEquals(0, panel.getCamera().center_x(), 0.001);
                assertEquals(0, panel.getCamera().center_y(), 0.001);
            } finally { close(panel); }
            return null;
        });
    }

    @Test public void panRebasesRepeatedlyInAllDirectionsWithoutChangingWorldMovement() throws Exception {
        SwingCapture.call(() -> {
            var panel = new PetriNetsPanel(null, true);
            try {
                var view = viewport(panel); panel.setCamera(0, 0, 1); panel.setTool(CanvasTool.PAN);
                press(panel, event(panel, view, MouseEvent.MOUSE_PRESSED, 100, 100));
                for (int i=1; i<=4; i++) {
                    drag(panel, event(panel, view, MouseEvent.MOUSE_DRAGGED, 100 + i * 15000, 100 + i * 15000));
                    assertEquals(-i * 15000, panel.getCamera().center_x(), 0.001);
                    assertEquals(-i * 15000, panel.getCamera().center_y(), 0.001);
                }
                drag(panel, event(panel, view, MouseEvent.MOUSE_DRAGGED, -60000, -45000));
                assertEquals(60100, panel.getCamera().center_x(), 0.001);
                assertEquals(45100, panel.getCamera().center_y(), 0.001);
                drag(panel, event(panel, view, MouseEvent.MOUSE_DRAGGED, 100, 100));
                assertEquals(0, panel.getCamera().center_x(), 0.001);
                assertEquals(0, panel.getCamera().center_y(), 0.001);
            } finally { close(panel); }
            return null;
        });
    }

    @Test public void zoomKeepsThePointerAnchoredFarFromTheWorldOrigin() throws Exception {
        SwingCapture.call(() -> {
            var panel = new PetriNetsPanel(null, true);
            try {
                var view = viewport(panel); panel.setCamera(-500000, 700000, 0.5);
                Point offset = view.getViewPosition();
                Point2D before = panel.toWorld(new Point(offset.x + 83, offset.y + 210));
                var wheel = new MouseWheelEvent(panel, MouseEvent.MOUSE_WHEEL, System.currentTimeMillis(),
                        InputShortcuts.menuMask(), offset.x + 83, offset.y + 210, 0, 0, 1, false,
                        MouseWheelEvent.WHEEL_UNIT_SCROLL, 3, -3, -3);
                for (var listener : panel.getMouseWheelListeners()) listener.mouseWheelMoved(wheel);
                offset = view.getViewPosition();
                Point2D after = panel.toWorld(new Point(offset.x + 83, offset.y + 210));
                assertEquals(before.getX(), after.getX(), 0.5 / panel.getScale() + 1e-6);
                assertEquals(before.getY(), after.getY(), 0.5 / panel.getScale() + 1e-6);
            } finally { close(panel); }
            return null;
        });
    }

    @Test public void nativeViewportScrollingRebasesWithoutChangingVisibleWorldCoordinates() throws Exception {
        SwingCapture.call(() -> {
            var panel = new PetriNetsPanel(null, true);
            try {
                var view = viewport(panel); panel.setCamera(0, 0, 1);
                for (int i = 1; i <= 4; i++) {
                    view.setViewPosition(new Point(0, 0));
                    assertEquals(-9800 * i, panel.getCamera().center_x(), 0.001);
                    assertEquals(-9850 * i, panel.getCamera().center_y(), 0.001);
                    assertEquals(new Point(9800, 9850), view.getViewPosition());
                }
                for (int i = 3; i >= 0; i--) {
                    view.setViewPosition(new Point(19600, 19700));
                    assertEquals(-9800 * i, panel.getCamera().center_x(), 0.001);
                    assertEquals(-9850 * i, panel.getCamera().center_y(), 0.001);
                    assertEquals(new Point(9800, 9850), view.getViewPosition());
                }
                panel.setCamera(-50000, 70000, 2);
                assertEquals(-50000, panel.getCamera().center_x(), 0.001);
                assertEquals(70000, panel.getCamera().center_y(), 0.001);
            } finally { close(panel); }
            return null;
        });
    }

    @Test public void reviewSelectionAndFitWorkInNegativeQuadrants() throws Exception {
        SwingCapture.call(() -> {
            var panel = new PetriNetsPanel(null, PetriNetsPanel.Mode.REVIEW);
            try {
                var view = viewport(panel);
                var place = new GraphPetriPlace(new PetriP("Negative place", 1), 910);
                place.setNewCoordinates(new Point(-400, -300)); panel.getGraphNet().getGraphPetriPlaceList().add(place);
                panel.fitDrawingToViewport();
                assertTrue(panel.getCamera().center_x() < 0); assertTrue(panel.getCamera().center_y() < 0);
                assertTrue(panel.getVisibleWorldBounds().contains(place.getBounds()));
                panel.setCamera(-400, -300, 1);
                AtomicReference<Rectangle> selected = new AtomicReference<>(); panel.setReviewSelectionListener(selected::set);
                press(panel, event(panel, view, MouseEvent.MOUSE_PRESSED, 200, 150));
                release(panel, event(panel, view, MouseEvent.MOUSE_RELEASED, 200, 150));
                assertEquals(place.getBounds(), selected.get());
                assertTrue(panel.getSelection().elements().contains(place));
            } finally { close(panel); }
            return null;
        });
    }

    @Test public void exportedImageAndGeometryDoNotDependOnCameraOrZoom() throws Exception {
        SwingCapture.call(() -> {
            var panel = new PetriNetsPanel(null, false);
            try {
                viewport(panel);
                var place = new GraphPetriPlace(new PetriP("Negative place", 3), 911);
                place.setNewCoordinates(new Point(-400, -300)); panel.getGraphNet().getGraphPetriPlaceList().add(place);
                var exporter = new NetworkImageExporter();
                var before = exporter.render(panel);
                panel.setCamera(900000, -800000, 3.7);
                var after = exporter.render(panel);
                assertArrayEquals(before.png(), after.png()); assertEquals(before.geometry(), after.geometry());
                var image = ImageIO.read(new ByteArrayInputStream(after.png()));
                int background = image.getRGB(0, 0), different = 0;
                for (int y=0; y<image.getHeight(); y++) for (int x=0; x<image.getWidth(); x++) if (image.getRGB(x,y)!=background) different++;
                assertTrue("The export contains the real drawing, not an empty image", different > 50);
                assertEquals(-400, place.getGraphElementCenter().getX(), 0.001);
            } finally { close(panel); }
            return null;
        });
    }
}

package ua.stetsenkoinna.graphpresentation.training;

import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.imageio.ImageIO;
import javax.swing.SwingUtilities;
import ua.stetsenkoinna.graphnet.GraphElement;
import ua.stetsenkoinna.graphnet.GraphPetriPlace;
import ua.stetsenkoinna.graphnet.GraphPetriTransition;
import ua.stetsenkoinna.graphpresentation.PetriNetsFrame;
import ua.stetsenkoinna.graphpresentation.PetriNetsPanel;
import ua.stetsenkoinna.pnml.PnmlModelGenerator;

/** Captures existing editor state without changing the document's save location. */
public final class FrameTrainingCapture {
    private FrameTrainingCapture() { }

    public static TrainingSnapshot capture(PetriNetsFrame frame, Map<String, Object> controls,
            String protocol, String statistics, boolean includeScreenshot) throws Exception {
        if (!SwingUtilities.isEventDispatchThread()) throw new IllegalStateException("Capture editor evidence on the Swing event thread");
        PetriNetsPanel panel = frame.getPetriNetsPanel();
        panel.getCanvasModel().setName(frame.getNameNet());
        String pnml = new PnmlModelGenerator().generateXml(panel.getCanvasModel().toObjModel());
        NetworkImageExporter.RenderedImage network = new NetworkImageExporter().render(panel);
        Map<String, Object> observations = new LinkedHashMap<>();
        observations.put("captured_at", Instant.now().toString());
        observations.put("provenance", "Visible editor text and current controls only; output may predate model edits and is not a verified simulation run.");
        observations.put("current_controls", controls);
        observations.put("protocol_text", protocol == null ? "" : protocol);
        observations.put("statistics_text", statistics == null ? "" : statistics);
        observations.put("network_image_scope", "Existing active-canvas painter, including its current collapsed/focused visibility. Geometry is unverified and is not ready-made detector labeling.");
        observations.put("source_nodes", sourceNodes(panel));
        Map<String, Object> viewport = new LinkedHashMap<>();
        viewport.put("coordinate_system", "Swing logical pixels; canvas model positions are scaled by zoom");
        viewport.put("zoom", panel.getScale());
        viewport.put("visible_rect", rectangle(panel.getVisibleRect()));
        viewport.put("canvas_bounds_in_frame", rectangle(SwingUtilities.convertRectangle(panel,
                new Rectangle(0, 0, panel.getWidth(), panel.getHeight()), frame)));
        if (panel.getParent() != null) viewport.put("viewport_bounds_in_frame", rectangle(SwingUtilities.convertRectangle(
                panel.getParent(), new Rectangle(0, 0, panel.getParent().getWidth(), panel.getParent().getHeight()), frame)));
        if (frame.isShowing()) {
            var location = frame.getLocationOnScreen();
            viewport.put("frame_screen_origin", Map.of("x", location.x, "y", location.y));
        }
        if (frame.getGraphicsConfiguration() != null) {
            AffineTransform transform = frame.getGraphicsConfiguration().getDefaultTransform();
            viewport.put("screen_device_scale", Map.of("x", transform.getScaleX(), "y", transform.getScaleY()));
        }
        observations.put("viewport", viewport);
        byte[] screenshot = null;
        if (includeScreenshot && frame.isShowing() && (long) frame.getWidth() * frame.getHeight() <= 24_000_000) {
            BufferedImage image = new BufferedImage(frame.getWidth(), frame.getHeight(), BufferedImage.TYPE_INT_RGB);
            Graphics2D graphics = image.createGraphics();
            try {
                frame.printAll(graphics);
            } finally {
                graphics.dispose();
            }
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            ImageIO.write(image, "png", output);
            screenshot = output.toByteArray();
            observations.put("qa_screenshot", Map.of("file", "screenshot.png", "width", image.getWidth(),
                    "height", image.getHeight(), "coordinate_system", "main-frame Swing logical pixels; owned dialogs are excluded"));
        }
        return new TrainingSnapshot(pnml, observations, network, screenshot, List.of(),
                "The editor does not observe the external client's complete MCP history or private reasoning");
    }

    private static List<Map<String, Object>> sourceNodes(PetriNetsPanel panel) {
        List<Map<String, Object>> nodes = new ArrayList<>();
        List<GraphElement> elements = new ArrayList<>();
        elements.addAll(panel.getGraphNet().getGraphPetriPlaceList());
        elements.addAll(panel.getGraphNet().getGraphPetriTransitionList());
        for (GraphElement element : elements) {
            Map<String, Object> node = new LinkedHashMap<>();
            node.put("canvas_id", element.getType() + ":" + element.getId());
            node.put("name", element.getName());
            node.put("source_model_id", element instanceof GraphPetriPlace place ? place.getPetriPlace().getId()
                    : element instanceof GraphPetriTransition transition ? transition.getPetriTransition().getId() : null);
            var center = element.getGraphElementCenter();
            if (center != null) node.put("model_center", Map.of("x", center.getX(), "y", center.getY()));
            nodes.add(node);
        }
        return nodes;
    }

    private static Map<String, Integer> rectangle(Rectangle value) {
        return Map.of("x", value.x, "y", value.y, "width", value.width, "height", value.height);
    }
}

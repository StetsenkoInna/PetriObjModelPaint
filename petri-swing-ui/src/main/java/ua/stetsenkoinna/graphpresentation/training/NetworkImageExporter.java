package ua.stetsenkoinna.graphpresentation.training;

import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javax.imageio.ImageIO;
import javax.imageio.IIOImage;
import javax.imageio.ImageTypeSpecifier;
import javax.imageio.metadata.IIOMetadataNode;
import javax.swing.SwingUtilities;
import ua.stetsenkoinna.graphnet.GraphArc;
import ua.stetsenkoinna.graphnet.GraphElement;
import ua.stetsenkoinna.graphnet.GraphObjectFrame;
import ua.stetsenkoinna.graphnet.GraphPetriTransition;
import ua.stetsenkoinna.graphpresentation.PetriNetsPanel;
import ua.stetsenkoinna.theme.CanvasColor;
import ua.stetsenkoinna.theme.CanvasPalette;

/** Reuses the active canvas painter for standalone PNGs and training bundles. */
public final class NetworkImageExporter {
    private static final long MAX_PIXELS = 24_000_000;
    private record ElementBounds(String id, String kind, Rectangle2D bounds) { }
    public record RenderedImage(byte[] png, Map<String, Object> geometry) { }

    public RenderedImage render(PetriNetsPanel panel) throws Exception {
        if (!SwingUtilities.isEventDispatchThread()) throw new IllegalStateException("Capture the canvas on the Swing event thread");
        List<ElementBounds> elements = new ArrayList<>();
        Rectangle2D extent = null;
        List<GraphElement> nodes = new ArrayList<>();
        nodes.addAll(panel.getGraphNet().getGraphPetriPlaceList());
        nodes.addAll(panel.getGraphNet().getGraphPetriTransitionList());
        for (GraphElement node : nodes) {
            Rectangle bounds = node.getBounds();
            if (bounds == null) continue;
            elements.add(new ElementBounds(node.getType() + ":" + node.getId(),
                    node instanceof GraphPetriTransition ? "transition" : "place", new Rectangle(bounds)));
            int labelLength = node.getName() == null ? 0 : node.getName().length();
            if (node instanceof GraphPetriTransition transition) {
                var model = transition.getPetriTransition();
                labelLength += length(model.getParameterParamName()) + length(model.getDistributionParamName()) + 24;
            }
            Rectangle padded = new Rectangle(bounds);
            padded.grow(Math.max(64, labelLength * 10), 96);
            extent = union(extent, padded);
        }
        List<GraphArc> arcs = new ArrayList<>();
        arcs.addAll(panel.getGraphNet().getGraphArcInList());
        arcs.addAll(panel.getGraphNet().getGraphArcOutList());
        int arcIndex = 0;
        for (GraphArc arc : arcs) {
            if (arc.getGraphElement() == null) continue;
            Rectangle2D bounds = arc.getGraphElement().getBounds2D();
            elements.add(new ElementBounds("canvas-arc-" + arcIndex++, "arc", bounds));
            Rectangle padded = bounds.getBounds();
            padded.grow(96, 96);
            extent = union(extent, padded);
        }
        int objectIndex = 0;
        for (GraphObjectFrame frame : panel.getCanvasModel().getFrames()) {
            Rectangle bounds = frame.getBounds();
            elements.add(new ElementBounds("canvas-object-" + objectIndex++, "object", new Rectangle(bounds)));
            Rectangle padded = new Rectangle(bounds);
            padded.grow(Math.max(64, length(frame.getName()) * 10), 96);
            extent = union(extent, padded);
        }
        if (extent == null) throw new IllegalArgumentException("The active canvas has no network to export");
        double scale = Math.min(1.0, Math.sqrt(MAX_PIXELS / Math.max(1.0, extent.getWidth() * extent.getHeight())));
        scale = Math.min(scale, 8192.0 / Math.max(extent.getWidth(), extent.getHeight()));
        int width = Math.max(1, (int) Math.ceil(extent.getWidth() * scale));
        int height = Math.max(1, (int) Math.ceil(extent.getHeight() * scale));
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setColor(CanvasPalette.current().get(CanvasColor.CANVAS_BACKGROUND));
            graphics.fillRect(0, 0, width, height);
            graphics.setFont(panel.getFont());
            graphics.translate(-extent.getX() * scale, -extent.getY() * scale);
            graphics.scale(scale / panel.getScale(), scale / panel.getScale());
            panel.paintComponent(graphics);
        } finally {
            graphics.dispose();
        }
        List<Map<String, Object>> labels = new ArrayList<>();
        for (ElementBounds element : elements) {
            Rectangle2D bounds = element.bounds();
            double x = (bounds.getX() - extent.getX()) * scale;
            double y = (bounds.getY() - extent.getY()) * scale;
            double w = bounds.getWidth() * scale;
            double h = bounds.getHeight() * scale;
            labels.add(Map.of("id", element.id(), "kind", element.kind(),
                    "bounds", Map.of("x", x, "y", y, "width", w, "height", h),
                    "clipped", x < 0 || y < 0 || x + w > width || y + h > height));
        }
        Map<String, Object> geometry = Map.of("coordinate_system", "image-pixels",
                "image", Map.of("file", "network.png", "width", width, "height", height),
                "transform", Map.of("origin_x", extent.getX(), "origin_y", extent.getY(), "scale", scale),
                "label_status", "unverified", "elements", labels);
        return new RenderedImage(encode(image, geometry), geometry);
    }

    public void write(Path output, RenderedImage image) throws Exception {
        write(output, image, false);
    }

    public void write(Path output, RenderedImage image, boolean overwrite) throws Exception {
        Path target = output.toAbsolutePath().normalize();
        Files.createDirectories(target.getParent());
        Path temporary = Files.createTempFile(target.getParent(), ".petri-image-", ".png");
        try {
            Files.write(temporary, image.png());
            if (overwrite) Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            else Files.move(temporary, target);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private static Rectangle2D union(Rectangle2D left, Rectangle2D right) {
        return left == null ? (Rectangle2D) right.clone() : left.createUnion(right);
    }

    private static int length(String value) { return value == null ? 0 : value.length(); }

    private static byte[] encode(BufferedImage image, Map<String, Object> geometry) throws Exception {
        var writer = ImageIO.getImageWritersByFormatName("png").next();
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (var output = ImageIO.createImageOutputStream(bytes)) {
            writer.setOutput(output);
            var parameters = writer.getDefaultWriteParam();
            var metadata = writer.getDefaultImageMetadata(ImageTypeSpecifier.createFromRenderedImage(image), parameters);
            String format = metadata.getNativeMetadataFormatName();
            IIOMetadataNode root = new IIOMetadataNode(format);
            IIOMetadataNode text = new IIOMetadataNode("tEXt");
            IIOMetadataNode entry = new IIOMetadataNode("tEXtEntry");
            entry.setAttribute("keyword", "petri-modeling-transform");
            entry.setAttribute("value", new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(Map.of(
                    "coordinate_system", "image-pixels", "transform", geometry.get("transform"),
                    "width", image.getWidth(), "height", image.getHeight(), "scope", "active-canvas",
                    "label_status", "unverified")));
            text.appendChild(entry);
            root.appendChild(text);
            metadata.mergeTree(format, root);
            writer.write(null, new IIOImage(image, null, metadata), parameters);
        } finally {
            writer.dispose();
        }
        return bytes.toByteArray();
    }
}

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
import java.util.Set;
import javax.imageio.ImageIO;
import javax.imageio.IIOImage;
import javax.imageio.ImageTypeSpecifier;
import javax.imageio.metadata.IIOMetadataNode;
import javax.swing.SwingUtilities;
import ua.stetsenkoinna.graphnet.GraphArc;
import ua.stetsenkoinna.graphnet.GraphCanvasModel;
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
    public record NetworkView(String id, String name, int objectIndex, String parentId, RenderedImage image) { }
    public record TrainingImages(RenderedImage overview, List<NetworkView> objects) { }
    public record Options(double scale, boolean transparentBackground, int maxDimension, boolean includeParameters) {
        public Options(double scale, boolean transparentBackground, int maxDimension) { this(scale, transparentBackground, maxDimension, false); }
        public Options {
            if (!Double.isFinite(scale) || scale <= 0 || scale > 4) throw new IllegalArgumentException("Image scale must be above 0 and at most 4");
            if (maxDimension < 256 || maxDimension > 8192) throw new IllegalArgumentException("Maximum image dimension must be 256-8192 pixels");
        }
        public static Options defaults() { return new Options(1, false, 8192); }
    }

    /** Render a separate document copy so focus, collapse, selection and undo stay untouched. */
    public TrainingImages renderTrainingImages(PetriNetsPanel source) throws Exception {
        return renderExpanded(source, new Options(1, false, 8192, true), true, null);
    }

    public Map<String, RenderedImage> renderConfigured(PetriNetsPanel source, String scope, List<Integer> indices,
            boolean includeOverview, Options options) throws Exception {
        if ("active".equals(scope)) return Map.of("network.png", renderActive(source, options));
        if ("selection".equals(scope)) return Map.of("selection.png", renderSelection(source, options));
        if (!Set.of("document", "objects").contains(scope)) throw new IllegalArgumentException("Image scope must be active, document or objects");
        Set<Integer> selected = "document".equals(scope) ? Set.of() : indices == null ? null : Set.copyOf(indices);
        if (selected != null && selected.stream().anyMatch(i -> i < 0 || i >= source.getCanvasModel().getFrames().size())) throw new IllegalArgumentException("Unknown object index");
        TrainingImages images = renderExpanded(source, options, "document".equals(scope) || includeOverview, selected);
        Map<String, RenderedImage> result = new java.util.LinkedHashMap<>();
        if (images.overview() != null) result.put("network.png", images.overview());
        for (NetworkView view : images.objects()) result.put("networks/" + view.id() + ".png", view.image());
        if (result.isEmpty()) throw new IllegalArgumentException("Select at least one image to export");
        return result;
    }

    private TrainingImages renderExpanded(PetriNetsPanel source, Options options, boolean includeOverview, Set<Integer> selected) throws Exception {
        if (!SwingUtilities.isEventDispatchThread()) throw new IllegalStateException("Capture the canvas on the Swing event thread");
        GraphCanvasModel copy = copyForImage(source.getCanvasModel());
        for (GraphObjectFrame frame : copy.getFrames()) {
            frame.setCollapsed(false);
            frame.setContentVisible(true);
        }
        PetriNetsPanel view = new PetriNetsPanel(null, false);
        try {
            view.setFont(source.getFont());
            view.setCanvasModel(copy);
            RenderedImage overview = includeOverview ? render(view, "network.png", "whole-document-expanded", null, null, options) : null;
            List<NetworkView> objects = new ArrayList<>();
            for (int index = 0; index < copy.getFrames().size(); index++) {
                if (selected != null && !selected.contains(index)) continue;
                GraphObjectFrame frame = copy.getFrames().get(index);
                String id = "object-" + String.format(java.util.Locale.ROOT, "%04d", index);
                String parent = frame.getEnclosing() == null ? "main"
                        : "object-" + String.format(java.util.Locale.ROOT, "%04d", copy.getFrames().indexOf(frame.getEnclosing()));
                view.openObjectCanvas(frame);
                objects.add(new NetworkView(id, frame.getName(), index, parent,
                        render(view, "networks/" + id + ".png", "object-subtree-expanded",
                                Set.copyOf(copy.membersOfSubtree(frame)), Set.copyOf(copy.subtreeOf(frame)), options)));
            }
            return new TrainingImages(overview, List.copyOf(objects));
        } finally {
            view.setArcFrame.dispose();
            view.setPositionFrame.dispose();
            view.setTransitionFrame.dispose();
        }
    }

    private RenderedImage renderSelection(PetriNetsPanel source, Options options) throws Exception {
        if (!SwingUtilities.isEventDispatchThread()) throw new IllegalStateException("Capture the canvas on the Swing event thread");
        Set<String> nodes = new java.util.HashSet<>();
        Set<Integer> frames = new java.util.HashSet<>();
        for (GraphElement element : source.getSelection().elements()) nodes.add(element.getType() + ":" + element.getId());
        for (GraphObjectFrame frame : source.getSelection().allFrames()) {
            for (GraphElement element : source.getCanvasModel().membersOfSubtree(frame)) nodes.add(element.getType() + ":" + element.getId());
            for (GraphObjectFrame nested : source.getCanvasModel().subtreeOf(frame)) frames.add(source.getCanvasModel().getFrames().indexOf(nested));
        }
        if (nodes.isEmpty() && frames.isEmpty()) throw new IllegalArgumentException("Select places, transitions or objects on the canvas first");
        GraphCanvasModel copy = copyForImage(source.getCanvasModel());
        var net = copy.getNet();
        net.getGraphPetriPlaceList().removeIf(node -> !nodes.contains(node.getType() + ":" + node.getId()));
        net.getGraphPetriTransitionList().removeIf(node -> !nodes.contains(node.getType() + ":" + node.getId()));
        java.util.function.Predicate<GraphArc> outside = arc -> arc.getBeginElement() == null || arc.getEndElement() == null
                || !nodes.contains(arc.getBeginElement().getType() + ":" + arc.getBeginElement().getId())
                || !nodes.contains(arc.getEndElement().getType() + ":" + arc.getEndElement().getId());
        net.getGraphArcInList().removeIf(outside); net.getGraphArcOutList().removeIf(outside);
        var allFrames = new ArrayList<>(copy.getFrames());
        for (int index = 0; index < allFrames.size(); index++) if (!frames.contains(index)) copy.getFrames().remove(allFrames.get(index));
        for (GraphObjectFrame frame : new ArrayList<>(copy.getFrames())) {
            if (!copy.getFrames().contains(frame.getEnclosing())) copy.nest(frame, null);
            frame.setCollapsed(false); frame.setContentVisible(true);
        }
        copy.getGroups().clear();
        copy.getFusions().removeIf(fusion -> !net.getGraphPetriPlaceList().contains(fusion.getMaster()) || !net.getGraphPetriPlaceList().contains(fusion.getJoined()));
        PetriNetsPanel view = new PetriNetsPanel(null, false);
        try {
            view.setFont(source.getFont()); view.setCanvasModel(copy);
            return render(view, "selection.png", "selected-elements-expanded", null, null, options);
        } finally { view.setArcFrame.dispose(); view.setPositionFrame.dispose(); view.setTransitionFrame.dispose(); }
    }

    /** Serialize only our own trusted in-memory model, never data read from an archive.
     * The ordinary copy constructor deliberately allocates new IDs and can omit parameter flags;
     * an image snapshot must retain exact IDs, labels and parameters without advancing counters. */
    private static GraphCanvasModel copyForImage(GraphCanvasModel source) throws Exception {
        return ua.stetsenkoinna.graphnet.GraphCanvasSnapshots.copy(source);
    }

    public RenderedImage render(PetriNetsPanel panel) throws Exception {
        return renderActive(panel, Options.defaults());
    }

    private RenderedImage renderActive(PetriNetsPanel panel, Options options) throws Exception {
        List<GraphElement> nodes = new ArrayList<>();
        nodes.addAll(panel.getGraphNet().getGraphPetriPlaceList()); nodes.addAll(panel.getGraphNet().getGraphPetriTransitionList());
        Set<GraphElement> included = new java.util.HashSet<>();
        for (GraphElement node : nodes) if (panel.isDrawnOnThisCanvas(node)) included.add(node);
        Set<GraphObjectFrame> frames = new java.util.HashSet<>();
        for (GraphObjectFrame frame : panel.getCanvasModel().getFrames()) if (panel.isFrameDrawnOnThisCanvas(frame) || frame == panel.getFocusedFrame()) frames.add(frame);
        return render(panel, "network.png", "active-canvas", included, frames, options);
    }

    private RenderedImage render(PetriNetsPanel panel, String file, String scope,
            Set<GraphElement> includedNodes, Set<GraphObjectFrame> includedFrames, Options options) throws Exception {
        if (!SwingUtilities.isEventDispatchThread()) throw new IllegalStateException("Capture the canvas on the Swing event thread");
        List<ElementBounds> elements = new ArrayList<>();
        Rectangle2D extent = null;
        List<GraphElement> nodes = new ArrayList<>();
        nodes.addAll(panel.getGraphNet().getGraphPetriPlaceList());
        nodes.addAll(panel.getGraphNet().getGraphPetriTransitionList());
        for (GraphElement node : nodes) {
            if (includedNodes != null && !includedNodes.contains(node)) continue;
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
            int sourceArcIndex = arcIndex++;
            if (includedNodes != null && (!includedNodes.contains(arc.getBeginElement()) || !includedNodes.contains(arc.getEndElement()))) continue;
            if (arc.getGraphElement() == null) continue;
            Rectangle2D bounds = arc.getGraphElement().getBounds2D();
            elements.add(new ElementBounds("canvas-arc-" + sourceArcIndex, "arc", bounds));
            Rectangle padded = bounds.getBounds();
            padded.grow(96, 96);
            extent = union(extent, padded);
        }
        for (GraphObjectFrame frame : panel.getCanvasModel().getFrames()) {
            if (includedFrames != null && !includedFrames.contains(frame)) continue;
            Rectangle bounds = frame.getBounds();
            if (frame != panel.getFocusedFrame()) elements.add(new ElementBounds("canvas-object-" + panel.getCanvasModel().getFrames().indexOf(frame), "object", new Rectangle(bounds)));
            Rectangle padded = new Rectangle(bounds);
            padded.grow(Math.max(64, length(frame.getName()) * 10), 96);
            extent = union(extent, padded);
        }
        if (extent == null) throw new IllegalArgumentException("The active canvas has no network to export");
        var parameters = ReviewParameters.entries(panel.getCanvasModel()).stream()
                .filter(entry -> elements.stream().anyMatch(element -> element.id().equals(entry.id()))).toList();
        List<String> parameterLines = new ArrayList<>();
        if (options.includeParameters()) {
            parameterLines.add("Model parameters");
            for (var entry : parameters) {
                for (String line : entry.text().split("\\n")) {
                    // Wrap by code point without losing long names or symbolic parameter values.
                    int[] points = line.codePoints().toArray();
                    for (int offset = 0; offset < points.length; offset += 100)
                        parameterLines.add(new String(points, offset, Math.min(100, points.length - offset)));
                }
                parameterLines.add("");
            }
        }
        double tableY = extent.getMaxY();
        if (!parameterLines.isEmpty()) extent = new Rectangle2D.Double(extent.getX(), extent.getY(), Math.max(1000, extent.getWidth()), extent.getHeight() + 32 + parameterLines.size() * 20);
        double scale = Math.min(options.scale(), Math.sqrt(MAX_PIXELS / Math.max(1.0, extent.getWidth() * extent.getHeight())));
        scale = Math.min(scale, options.maxDimension() / Math.max(extent.getWidth(), extent.getHeight()));
        int width = Math.max(1, (int) Math.ceil(extent.getWidth() * scale));
        int height = Math.max(1, (int) Math.ceil(extent.getHeight() * scale));
        BufferedImage image = new BufferedImage(width, height, options.transparentBackground() ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        try {
            if (!options.transparentBackground()) {
                graphics.setColor(CanvasPalette.current().get(CanvasColor.CANVAS_BACKGROUND));
                graphics.fillRect(0, 0, width, height);
            }
            graphics.setFont(panel.getFont());
            graphics.translate(-extent.getX() * scale, -extent.getY() * scale);
            graphics.scale(scale / panel.getScale(), scale / panel.getScale());
            boolean opaque = panel.isOpaque();
            try {
                if (options.transparentBackground()) panel.setOpaque(false);
                panel.paintComponent(graphics);
                if (!parameterLines.isEmpty()) {
                    graphics.setTransform(new java.awt.geom.AffineTransform());
                    graphics.scale(scale, scale);
                    graphics.setColor(CanvasPalette.current().get(CanvasColor.ELEMENT_STROKE));
                    graphics.setFont(new java.awt.Font(java.awt.Font.SANS_SERIF, java.awt.Font.PLAIN, 16));
                    double y = tableY - extent.getY() + 24;
                    for (String line : parameterLines) { graphics.drawString(line, 16, (float) y); y += 20; }
                }
            } finally { panel.setOpaque(opaque); }
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
        Map<String, Object> geometry = Map.of("parameters", parameters, "parameter_table_visible", options.includeParameters(), "coordinate_system", "image-pixels", "scope", scope,
                "image", Map.of("file", file, "width", width, "height", height),
                "transform", Map.of("origin_x", extent.getX(), "origin_y", extent.getY(), "scale", scale),
                "label_status", "unverified", "elements", labels);
        return new RenderedImage(encode(image, geometry), geometry);
    }

    public void write(Path output, RenderedImage image) throws Exception {
        write(output, image, false);
    }

    public void writeImages(Path output, Map<String, RenderedImage> images, boolean overwrite) throws Exception {
        String name = output.getFileName().toString().toLowerCase(java.util.Locale.ROOT);
        if (name.endsWith(".png")) {
            if (images.size() != 1) throw new IllegalArgumentException("Use a ZIP output for multiple images");
            write(output, images.values().iterator().next(), overwrite);
            return;
        }
        if (!name.endsWith(".zip")) throw new IllegalArgumentException("Image output must end with .png or .zip");
        Path target = output.toAbsolutePath().normalize();
        Files.createDirectories(target.getParent());
        Path temporary = Files.createTempFile(target.getParent(), ".petri-images-", ".zip");
        try {
            try (var zip = new java.util.zip.ZipOutputStream(Files.newOutputStream(temporary))) {
                Map<String, Object> geometry = new java.util.LinkedHashMap<>();
                for (var entry : images.entrySet()) {
                    zip.putNextEntry(new java.util.zip.ZipEntry(entry.getKey())); zip.write(entry.getValue().png()); zip.closeEntry();
                    geometry.put(entry.getKey(), entry.getValue().geometry());
                }
                zip.putNextEntry(new java.util.zip.ZipEntry("images.json"));
                zip.write(new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsBytes(geometry)); zip.closeEntry();
            }
            if (overwrite) Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            else Files.move(temporary, target);
        } finally { Files.deleteIfExists(temporary); }
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
                    "width", image.getWidth(), "height", image.getHeight(), "scope", geometry.get("scope"),
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

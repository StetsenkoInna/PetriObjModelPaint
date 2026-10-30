package ua.stetsenkoinna.graphpresentation.training;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.awt.geom.Point2D;
import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import javax.imageio.ImageIO;
import javax.swing.SwingUtilities;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import ua.stetsenkoinna.graphnet.GraphPetriPlace;
import ua.stetsenkoinna.graphnet.GraphPetriTransition;
import ua.stetsenkoinna.graphnet.GraphObjectFrame;
import ua.stetsenkoinna.graphpresentation.PetriNetsPanel;
import ua.stetsenkoinna.petriobj.PetriP;
import ua.stetsenkoinna.petriobj.PetriT;
import static org.junit.Assert.*;

public class NetworkImageExporterTest {
    @Rule public TemporaryFolder folder = new TemporaryFolder();

    @Test public void trainingImageIncludesAllInputParametersAndAnOptionalVisibleTable() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var panel = new PetriNetsPanel(null, false);
            try {
                var t = new PetriT("Обслуговування", 3.0); t.setPriority(4); t.setProbability(0.75); t.setParamDeviation(0.5);
                var node = new GraphPetriTransition(t, 10); node.setNewCoordinates(new java.awt.Point(300, 300));
                panel.getGraphNet().getGraphPetriTransitionList().add(node);
                var renderer = new NetworkImageExporter();
                var plain = renderer.render(panel);
                var training = renderer.renderTrainingImages(panel).overview();
                assertTrue(((Number)((Map<?,?>)training.geometry().get("image")).get("height")).intValue() > ((Number)((Map<?,?>)plain.geometry().get("image")).get("height")).intValue());
                var values = new ObjectMapper().valueToTree(training.geometry()).path("parameters").get(0).path("values");
                assertEquals("3.0", values.path("Delay").asText());
                assertEquals("4", values.path("Priority").asText());
                assertEquals("0.75", values.path("Probability").asText());
                assertEquals("0.5", values.path("Deviation").asText());
                assertEquals("Constant", values.path("Distribution").asText());
                var image = ImageIO.read(new ByteArrayInputStream(training.png()));
                String output = System.getProperty("training.qa.output");
                if (output != null) { Files.createDirectories(Path.of(output)); Files.write(Path.of(output).resolve("review-parameter-table.png"), training.png()); }
                int tablePixels = 0;
                for (int y = 220; y < image.getHeight(); y++) for (int x = 0; x < image.getWidth(); x++) if (image.getRGB(x,y) != image.getRGB(image.getWidth()-1,y)) tablePixels++;
                assertTrue("Parameter table must be painted, not only stored as metadata", tablePixels > 100);
            } catch (Exception failure) { throw new RuntimeException(failure); }
            finally { panel.setArcFrame.dispose(); panel.setPositionFrame.dispose(); panel.setTransitionFrame.dispose(); }
        });
    }

    @Test public void everyNestedObjectGetsAnExpandedScopedImageWithoutChangingTheEditor() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            PetriNetsPanel panel = new PetriNetsPanel(null, false);
            try {
                var canvas = panel.getCanvasModel();
                GraphObjectFrame parent = new GraphObjectFrame("Same / name", new java.awt.Rectangle(100, 100, 800, 600));
                GraphObjectFrame child = new GraphObjectFrame("Same / name", new java.awt.Rectangle(200, 200, 400, 300));
                GraphObjectFrame grandchild = new GraphObjectFrame("Deep", new java.awt.Rectangle(250, 250, 200, 150));
                GraphObjectFrame distant = new GraphObjectFrame("Distant", new java.awt.Rectangle(5000, 100, 300, 300));
                canvas.getFrames().addAll(java.util.List.of(parent, child, grandchild, distant));
                canvas.nest(child, parent); canvas.nest(grandchild, child);
                java.util.List<GraphPetriPlace> places = new java.util.ArrayList<>();
                int x = 180;
                for (GraphObjectFrame frame : canvas.getFrames()) {
                    var place = new GraphPetriPlace(new PetriP("Inside " + frame.getName(), 3), x);
                    place.setNewCoordinates(new Point2D.Double(frame == distant ? 5100 : x, 350));
                    x += 80; places.add(place); canvas.getNet().getGraphPetriPlaceList().add(place); canvas.claim(frame, place);
                }
                panel.openObjectCanvas(child);
                parent.setCollapsed(true); child.setContentVisible(false); grandchild.setCollapsed(true);
                panel.setSelectedFrame(distant);
                var beforeParent = new java.awt.Rectangle(parent.getBounds());
                var beforeChild = new java.awt.Rectangle(child.getBounds());
                var result = new NetworkImageExporter().renderTrainingImages(panel);
                assertEquals(4, result.objects().size());
                assertEquals("whole-document-expanded", result.overview().geometry().get("scope"));
                assertEquals(4, ((java.util.List<?>) result.overview().geometry().get("elements")).stream()
                        .filter(item -> "place".equals(((Map<?, ?>) item).get("kind"))).count());
                var identifiers = ((java.util.List<?>) result.overview().geometry().get("elements")).stream()
                        .map(item -> ((Map<?, ?>) item).get("id")).toList();
                for (var place : places) assertTrue(identifiers.contains(place.getType() + ":" + place.getId()));
                assertEquals("main", result.objects().get(0).parentId());
                assertEquals("object-0000", result.objects().get(1).parentId());
                assertEquals("object-0001", result.objects().get(2).parentId());
                var childImage = result.objects().get(1).image();
                assertEquals("networks/object-0001.png", ((Map<?, ?>) childImage.geometry().get("image")).get("file"));
                assertTrue(((Number) ((Map<?, ?>) childImage.geometry().get("image")).get("width")).intValue() < 2000);
                assertEquals(2, ((java.util.List<?>) childImage.geometry().get("elements")).stream()
                        .filter(item -> "place".equals(((Map<?, ?>) item).get("kind"))).count());
                assertSame(child, panel.getFocusedFrame()); assertSame(distant, panel.getSelectedFrame());
                assertTrue(parent.isCollapsed()); assertTrue(grandchild.isCollapsed()); assertFalse(child.isContentVisible());
                assertEquals(beforeParent, parent.getBounds()); assertEquals(beforeChild, child.getBounds());
                assertSame(places.get(0), canvas.getNet().getGraphPetriPlaceList().getFirst());
                var exporter = new NetworkImageExporter();
                var chosen = exporter.renderConfigured(panel, "objects", java.util.List.of(1, 2), false, NetworkImageExporter.Options.defaults());
                assertEquals(java.util.Set.of("networks/object-0001.png", "networks/object-0002.png"), chosen.keySet());
                Path selectedZip = folder.getRoot().toPath().resolve("chosen-objects.zip");
                exporter.writeImages(selectedZip, chosen, false);
                try (var zip = new java.util.zip.ZipFile(selectedZip.toFile())) { assertEquals(3, zip.size()); }
                var active = exporter.render(panel);
                assertTrue(((Number) ((Map<?, ?>) active.geometry().get("image")).get("width")).intValue() < 2000);
                assertEquals(1, ((java.util.List<?>) active.geometry().get("elements")).stream().filter(item -> "place".equals(((Map<?, ?>) item).get("kind"))).count());
            } catch (Exception failure) { throw new RuntimeException(failure); }
            finally { panel.setArcFrame.dispose(); panel.setPositionFrame.dispose(); panel.setTransitionFrame.dispose(); }
        });
    }

    @Test public void selectionExportsOnlyPickedNodesAndTheirInternalArcs() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            PetriNetsPanel panel = new PetriNetsPanel(null, false);
            try {
                var first = new GraphPetriPlace(new PetriP("Selected", 2), 401);
                first.setNewCoordinates(new Point2D.Double(100, 100));
                var last = new GraphPetriPlace(new PetriP("Outside selection", 0), 402);
                last.setNewCoordinates(new Point2D.Double(1500, 100));
                var transition = new GraphPetriTransition(new PetriT("Transfer", 1), 403);
                transition.setNewCoordinates(new Point2D.Double(300, 100));
                panel.getGraphNet().getGraphPetriPlaceList().addAll(java.util.List.of(first, last));
                panel.getGraphNet().getGraphPetriTransitionList().add(transition);
                var input = new ua.stetsenkoinna.graphnet.GraphArcIn();
                input.setEndElement(transition); input.settingNewArc(first); input.setPetriElements(); input.changeBorder(); input.updateCoordinates();
                panel.getGraphNet().getGraphArcInList().add(input);
                var output = new ua.stetsenkoinna.graphnet.GraphArcOut();
                output.settingNewArc(transition); output.setEndElement(last); output.setPetriElements(); output.changeBorder(); output.updateCoordinates();
                panel.getGraphNet().getGraphArcOutList().add(output);
                panel.getSelection().add(first); panel.getSelection().add(transition);
                var image = new NetworkImageExporter().renderConfigured(panel, "selection", null, false, NetworkImageExporter.Options.defaults()).get("selection.png");
                var elements = (java.util.List<?>) image.geometry().get("elements");
                assertEquals(3, elements.size());
                assertFalse(elements.stream().anyMatch(element -> (last.getType() + ":" + last.getId()).equals(((Map<?, ?>) element).get("id"))));
                assertTrue(((Number) ((Map<?, ?>) image.geometry().get("image")).get("width")).intValue() < 1200);
                assertEquals(2, panel.getGraphNet().getGraphPetriPlaceList().size());
                assertEquals(1, panel.getGraphNet().getGraphArcOutList().size());
                assertEquals(2, panel.getSelection().size());
            } catch (Exception failure) { throw new RuntimeException(failure); }
            finally { panel.setArcFrame.dispose(); panel.setPositionFrame.dispose(); panel.setTransitionFrame.dispose(); }
        });
    }

    @Test public void configurablePngSupportsTransparencyScaleAndBoundedDimensions() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            PetriNetsPanel panel = new PetriNetsPanel(null, false);
            try {
                var place = new GraphPetriPlace(new PetriP("Large", 1), 1);
                place.setNewCoordinates(new Point2D.Double(200, 200));
                panel.getGraphNet().getGraphPetriPlaceList().add(place);
                var exporter = new NetworkImageExporter();
                var normal = exporter.renderConfigured(panel, "document", null, true, new NetworkImageExporter.Options(1, false, 8192)).get("network.png");
                var larger = exporter.renderConfigured(panel, "document", null, true, new NetworkImageExporter.Options(2, true, 8192)).get("network.png");
                var original = ImageIO.read(new ByteArrayInputStream(normal.png()));
                var doubled = ImageIO.read(new ByteArrayInputStream(larger.png()));
                assertEquals(original.getWidth() * 2, doubled.getWidth());
                assertEquals(0, doubled.getRGB(0, 0) >>> 24);
                var capped = exporter.renderConfigured(panel, "document", null, true, new NetworkImageExporter.Options(4, false, 256)).get("network.png");
                var cappedImage = ImageIO.read(new ByteArrayInputStream(capped.png()));
                assertTrue(cappedImage.getWidth() <= 256); assertTrue(cappedImage.getHeight() <= 256);
                Path zip = folder.getRoot().toPath().resolve("images.zip");
                exporter.writeImages(zip, Map.of("network.png", larger), false);
                try (var archive = new java.util.zip.ZipFile(zip.toFile())) {
                    assertNotNull(archive.getEntry("network.png")); assertNotNull(archive.getEntry("images.json"));
                }
                assertTrue(panel.isOpaque());
            } catch (Exception failure) { throw new RuntimeException(failure); }
            finally { panel.setArcFrame.dispose(); panel.setPositionFrame.dispose(); panel.setTransitionFrame.dispose(); }
        });
    }

    @Test public void pngAndGeometryUseTheSameCanvasTransformWithoutMovingTheSource() throws Exception {
        NetworkImageExporter.RenderedImage[] result = new NetworkImageExporter.RenderedImage[1];
        SwingUtilities.invokeAndWait(() -> {
            try {
                PetriNetsPanel panel = new PetriNetsPanel(null, true);
                GraphPetriPlace place = new GraphPetriPlace(new PetriP("Source with a visible label", 6), 11);
                place.setNewCoordinates(new Point2D.Double(100, 100));
                GraphPetriTransition transition = new GraphPetriTransition(new PetriT("Transfer", 1), 12);
                transition.setNewCoordinates(new Point2D.Double(350, 100));
                panel.getGraphNet().getGraphPetriPlaceList().add(place);
                panel.getGraphNet().getGraphPetriTransitionList().add(transition);
                Point2D before = place.getGraphElementCenter();
                result[0] = new NetworkImageExporter().render(panel);
                assertEquals(before, place.getGraphElementCenter());
                assertEquals(1.0, panel.getScale(), 0.0);
                assertSame(place, panel.getGraphNet().getGraphPetriPlaceList().getFirst());
                assertEquals("unverified", result[0].geometry().get("label_status"));
                assertEquals(2, ((java.util.List<?>) result[0].geometry().get("elements")).size());
            } catch (Exception failure) {
                throw new RuntimeException(failure);
            }
        });
        var image = ImageIO.read(new ByteArrayInputStream(result[0].png()));
        Map<?, ?> dimensions = (Map<?, ?>) result[0].geometry().get("image");
        assertEquals(dimensions.get("width"), image.getWidth());
        assertEquals(dimensions.get("height"), image.getHeight());
        Map<?, ?> transformValues = (Map<?, ?>) result[0].geometry().get("transform");
        double scale = ((Number) transformValues.get("scale")).doubleValue();
        int sourceX = (int) Math.round((120 - ((Number) transformValues.get("origin_x")).doubleValue()) * scale);
        int sourceY = (int) Math.round((100 - ((Number) transformValues.get("origin_y")).doubleValue()) * scale);
        assertNotEquals("The image transform should locate the rendered source place", image.getRGB(0, 0), image.getRGB(sourceX, sourceY));
        var reader = ImageIO.getImageReadersByFormatName("png").next();
        try (var input = ImageIO.createImageInputStream(new ByteArrayInputStream(result[0].png()))) {
            reader.setInput(input);
            var metadata = reader.getImageMetadata(0).getAsTree("javax_imageio_png_1.0");
            var nodes = ((org.w3c.dom.Element) metadata).getElementsByTagName("tEXtEntry");
            assertEquals(1, nodes.getLength());
            var entry = (org.w3c.dom.Element) nodes.item(0);
            assertEquals("petri-modeling-transform", entry.getAttribute("keyword"));
            var transform = new ObjectMapper().readTree(entry.getAttribute("value"));
            assertEquals(image.getWidth(), transform.path("width").asInt());
            assertEquals("active-canvas", transform.path("scope").asText());
        } finally {
            reader.dispose();
        }
        Path target = folder.getRoot().toPath().resolve("network.png");
        NetworkImageExporter exporter = new NetworkImageExporter();
        exporter.write(target, result[0]);
        assertArrayEquals(result[0].png(), Files.readAllBytes(target));
        try {
            exporter.write(target, result[0]);
            fail("Existing image was overwritten without explicit permission");
        } catch (java.nio.file.FileAlreadyExistsException expected) {
            assertArrayEquals(result[0].png(), Files.readAllBytes(target));
        }
    }
}

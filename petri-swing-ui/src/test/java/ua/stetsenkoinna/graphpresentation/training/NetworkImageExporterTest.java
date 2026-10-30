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
import ua.stetsenkoinna.graphpresentation.PetriNetsPanel;
import ua.stetsenkoinna.petriobj.PetriP;
import ua.stetsenkoinna.petriobj.PetriT;
import static org.junit.Assert.*;

public class NetworkImageExporterTest {
    @Rule public TemporaryFolder folder = new TemporaryFolder();

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

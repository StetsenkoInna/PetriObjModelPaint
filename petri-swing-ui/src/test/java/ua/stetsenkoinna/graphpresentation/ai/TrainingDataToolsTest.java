package ua.stetsenkoinna.graphpresentation.ai;

import com.fasterxml.jackson.databind.JsonNode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipFile;
import javax.swing.SwingUtilities;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import ua.stetsenkoinna.graphnet.GraphPetriPlace;
import ua.stetsenkoinna.graphpresentation.PetriNetsPanel;
import ua.stetsenkoinna.graphpresentation.training.TrainingDataExporter;
import ua.stetsenkoinna.graphpresentation.training.TrainingSnapshot;
import ua.stetsenkoinna.petriobj.PetriP;
import static org.junit.Assert.*;

public class TrainingDataToolsTest {
    @Rule public TemporaryFolder folder = new TemporaryFolder();

    @Test public void registryAcceptsTypedConversationAndWritesTheSuppliedMessages() throws Exception {
        String pnml;
        try (var input = getClass().getResourceAsStream("/pnml/samples/easy/this-easy.pnml")) {
            pnml = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
        TrainingDataExporter exporter = new TrainingDataExporter(includeScreenshot ->
                new TrainingSnapshot(pnml, Map.of(), null, null, List.of(), "No tool recorder"));
        McpToolRegistry registry = new McpToolRegistry(new TrainingDataTools(exporter, () -> null));
        JsonNode arguments = DesktopMcpServer.JSON.valueToTree(Map.of("title", "Typed context", "task", "Inspect this model",
                "output_directory", folder.getRoot().toString(), "include_screenshot", false,
                "clarifications", List.of("One time unit is a second"),
                "conversation", List.of(Map.of("role", "user", "content", "Inspect the current model"))));
        JsonNode reply = DesktopMcpServer.JSON.valueToTree(registry.call("export_training_data", arguments));
        JsonNode result = DesktopMcpServer.JSON.readTree(reply.at("/content/0/text").asText());
        Path archive = Path.of(result.path("path").asText());
        assertTrue(Files.isRegularFile(archive));
        assertEquals("unreviewed", result.path("status").asText());
        try (ZipFile zip = new ZipFile(archive.toFile())) {
            String line = new String(zip.getInputStream(zip.getEntry("conversation.jsonl")).readAllBytes(), StandardCharsets.UTF_8);
            assertEquals("Inspect the current model", DesktopMcpServer.JSON.readTree(line).path("content").asText());
        }
        try {
            registry.call("export_training_data", DesktopMcpServer.JSON.valueToTree(Map.of("title", "Invalid",
                    "output_directory", folder.getRoot().toString(), "conversation", List.of(Map.of("role", "user", "content", 42)))));
            fail("Non-text conversation content was accepted");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("content"));
        }
    }

    @Test public void imageToolUsesExistingRendererAndRequiresExplicitOverwrite() throws Exception {
        PetriNetsPanel[] panel = new PetriNetsPanel[1];
        SwingUtilities.invokeAndWait(() -> {
            panel[0] = new PetriNetsPanel(null, true);
            GraphPetriPlace place = new GraphPetriPlace(new PetriP("Input", 1), 25);
            place.setNewCoordinates(new java.awt.geom.Point2D.Double(100, 100));
            panel[0].getGraphNet().getGraphPetriPlaceList().add(place);
        });
        TrainingDataTools tools = new TrainingDataTools(null, () -> panel[0]);
        Path image = folder.getRoot().toPath().resolve("network.png");
        JsonNode args = DesktopMcpServer.JSON.valueToTree(Map.of("output_path", image.toString()));
        tools.exportNetworkImage(args);
        byte[] original = Files.readAllBytes(image);
        assertNotNull(javax.imageio.ImageIO.read(image.toFile()));
        try {
            tools.exportNetworkImage(args);
            fail("Image overwritten without an explicit flag");
        } catch (java.nio.file.FileAlreadyExistsException expected) {
            assertArrayEquals(original, Files.readAllBytes(image));
        }
    }
}

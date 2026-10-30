package ua.stetsenkoinna.graphpresentation.training;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipFile;
import javax.swing.SwingUtilities;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import ua.stetsenkoinna.pnml.PnmlModelGenerator;
import ua.stetsenkoinna.pnml.PnmlModelParser;
import static org.junit.Assert.*;

public class TrainingDataExporterTest {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-30T09:15:00Z"), ZoneOffset.UTC);
    @Rule public TemporaryFolder folder = new TemporaryFolder();

    private String model() throws Exception {
        try (var input = getClass().getResourceAsStream("/pnml/samples/easy/this-easy.pnml")) {
            assertNotNull(input);
            var source = new PnmlModelParser().parseXml(new String(input.readAllBytes(), StandardCharsets.UTF_8));
            return new PnmlModelGenerator().generateXml(source);
        }
    }

    private TrainingDataExporter exporter(String pnml) {
        return new TrainingDataExporter(includeScreenshot -> {
            assertTrue(SwingUtilities.isEventDispatchThread());
            return new TrainingSnapshot(pnml, Map.of("protocol_text", "Observed text only"), null, null, List.of(), "No tool recorder was active");
        }, CLOCK);
    }

    private Map<String, byte[]> unpack(Path path) throws Exception {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        try (ZipFile zip = new ZipFile(path.toFile())) {
            var enumeration = zip.entries();
            while (enumeration.hasMoreElements()) {
                var entry = enumeration.nextElement();
                assertFalse(entry.getName().contains(".."));
                try (var input = zip.getInputStream(entry)) { entries.put(entry.getName(), input.readAllBytes()); }
            }
        }
        return entries;
    }

    @Test public void archiveContainsExactModelRealMessagesAndMatchingDigests() throws Exception {
        String pnml = model();
        List<Map<String, Object>> messages = List.of(Map.of("role", "user", "content", "Build a queue\nwith two servers"),
                Map.of("role", "assistant", "content", "The model is ready", "timestamp", "2026-10-30T09:14:00Z"));
        TrainingExportRequest request = new TrainingExportRequest("Queue study", "Two servers from one queue",
                List.of("Arrivals are exponential"), List.of("One time unit is a second"), List.of("No lost resource tokens"), null, messages, false);
        var result = exporter(pnml).export(folder.getRoot().toPath(), request);
        Map<String, byte[]> entries = unpack(result.path());
        assertArrayEquals(pnml.getBytes(StandardCharsets.UTF_8), entries.get("model.pnml"));
        assertEquals("unreviewed", result.status());
        assertTrue(result.path().getFileName().toString().contains("20261030T091500000Z"));
        JsonNode manifest = JSON.readTree(entries.get("manifest.json"));
        assertEquals("2026-10-30T09:15:00Z", manifest.path("created_at").asText());
        assertEquals("client-provided", manifest.at("/capture_scope/conversation").asText());
        assertEquals("unavailable", manifest.at("/capture_scope/tool_calls").asText());
        assertEquals(0, entries.get("tool-calls.jsonl").length);
        assertEquals(entries.size() - 1, manifest.path("files").size());
        for (JsonNode file : manifest.path("files")) {
            String name = file.path("name").asText();
            assertNotEquals("manifest.json", name);
            assertEquals(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(entries.get(name))), file.path("sha256").asText());
        }
        String[] lines = new String(entries.get("conversation.jsonl"), StandardCharsets.UTF_8).strip().split("\n");
        assertEquals(2, lines.length);
        assertEquals(messages.getFirst().get("content"), JSON.readTree(lines[0]).path("content").asText());
        JsonNode verification = JSON.readTree(entries.get("verification.json"));
        assertEquals("unknown", verification.path("status").asText());
        assertTrue(verification.get("simulation").isNull());
        assertEquals(0, verification.path("checks").size());
        var original = new PnmlModelParser().parseXml(pnml);
        var exported = new PnmlModelParser().parseXml(new String(entries.get("model.pnml"), StandardCharsets.UTF_8));
        assertEquals(original.getObject(0).getGraphNet().getPetriPList().getFirst().getId(),
                exported.getObject(0).getGraphNet().getPetriPList().getFirst().getId());
    }

    @Test public void missingTaskAndTranscriptStayExplicitAndFileNamesCannotEscapeTheFolder() throws Exception {
        TrainingExportRequest request = new TrainingExportRequest("../../CON:demo", null, null, null, null, null, null, false);
        var result = exporter(model()).export(folder.getRoot().toPath(), request);
        assertEquals(folder.getRoot().toPath().toAbsolutePath(), result.path().getParent());
        Map<String, byte[]> entries = unpack(result.path());
        assertTrue(JSON.readTree(entries.get("task.json")).get("task").isNull());
        assertEquals(0, entries.get("conversation.jsonl").length);
        JsonNode manifest = JSON.readTree(entries.get("manifest.json"));
        assertEquals("unreviewed", manifest.path("status").asText());
        assertTrue(manifest.path("shared_skills").get("revision").isNull());
        assertTrue(manifest.path("missing_data").toString().contains("conversation.jsonl"));
        assertTrue(manifest.path("missing_data").toString().contains("tool-calls.jsonl"));
    }

    @Test public void repeatedExportsNeverOverwriteEarlierArchive() throws Exception {
        TrainingDataExporter exporter = exporter(model());
        TrainingExportRequest request = new TrainingExportRequest("Same title", "Task", null, null, null, null, null, false);
        var first = exporter.export(folder.getRoot().toPath(), request);
        byte[] original = Files.readAllBytes(first.path());
        var second = exporter.export(folder.getRoot().toPath(), request);
        assertNotEquals(first.path(), second.path());
        assertArrayEquals(original, Files.readAllBytes(first.path()));
    }

    @Test public void humanAcceptanceDoesNotInventPassedChecks() throws Exception {
        var request = new TrainingExportRequest("Reviewed", "Task", List.of(), List.of(), List.of("Human review"), "accepted", List.of(), false);
        var entries = unpack(exporter(model()).export(folder.getRoot().toPath(), request).path());
        assertEquals("accepted", JSON.readTree(entries.get("manifest.json")).path("status").asText());
        assertEquals("unknown", JSON.readTree(entries.get("verification.json")).path("status").asText());
    }

    @Test(expected = IllegalArgumentException.class)
    public void unsupportedConversationRolesAreRejected() {
        new TrainingExportRequest("Title", "Task", null, null, null, null,
                List.of(Map.of("role", "system", "content", "Not a supplied user conversation message")), false);
    }
}

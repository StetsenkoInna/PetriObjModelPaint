package ua.stetsenkoinna.graphpresentation.training;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;

public class TrainingBundleWorkspaceTest {
    @Rule public TemporaryFolder folder = new TemporaryFolder();
    private final ObjectMapper json = new ObjectMapper();

    private TrainingDataExporter exporter(TrainingBundleWorkspace workspace, String pnml, int minute) {
        return new TrainingDataExporter(screenshot -> new TrainingSnapshot(pnml, Map.of(), null, null, List.of(), "Not observed"),
                Clock.fixed(Instant.parse("2026-10-30T10:" + String.format("%02d", minute) + ":00Z"), ZoneOffset.UTC), workspace);
    }

    private TrainingExportRequest request(String task) {
        return new TrainingExportRequest("Queue", task, List.of("FIFO"), List.of(), List.of("No lost tokens"), null,
                List.of(Map.of("role", "user", "content", "Build a queue")), false);
    }

    @Test public void threePeopleExchangeHistoryWithoutReplacingAnyEarlierRevisionOrReview() throws Exception {
        var firstWorkspace = new TrainingBundleWorkspace();
        var first = exporter(firstWorkspace, "<pnml>first</pnml>", 1).export(folder.getRoot().toPath(), request("Queue task"));
        byte[] original = Files.readAllBytes(first.path());
        var originalBundle = TrainingBundleWorkspace.read(first.path());
        String bundleId = originalBundle.history().path("bundle_id").asText();
        String firstRevision = originalBundle.history().path("head_revision_id").asText();

        var secondWorkspace = new TrainingBundleWorkspace();
        secondWorkspace.adopt(originalBundle);
        var review = new TrainingBundleWorkspace.Review("Reviewer A", "human", "changes_requested", "Fix capacity", List.of());
        var second = exporter(secondWorkspace, "<pnml>first</pnml>", 2).export(folder.getRoot().toPath(), secondWorkspace.exportRequest(null, List.of()), review);
        var secondBundle = TrainingBundleWorkspace.read(second.path());
        assertEquals(bundleId, secondBundle.history().path("bundle_id").asText());
        assertEquals(2, secondBundle.history().path("revisions").size());
        assertEquals(1, secondBundle.history().path("reviews").size());
        String reviewId = secondBundle.history().path("reviews").get(0).path("id").asText();
        String reviewPath = "history/reviews/" + reviewId + ".json";
        String reviewedRevision = json.readTree(secondBundle.files().get(reviewPath)).path("revision_id").asText();
        assertEquals(firstRevision, reviewedRevision);

        var thirdWorkspace = new TrainingBundleWorkspace();
        thirdWorkspace.adopt(secondBundle);
        var acceptance = new TrainingBundleWorkspace.Review("Reviewer B", "agent", "accepted", "Capacity corrected", List.of());
        var third = exporter(thirdWorkspace, "<pnml>corrected</pnml>", 3).export(folder.getRoot().toPath(), thirdWorkspace.exportRequest(null,
                List.of(Map.of("role", "user", "content", "Capacity is two"))), acceptance);
        var finalBundle = TrainingBundleWorkspace.read(third.path());
        assertEquals(3, finalBundle.history().path("revisions").size());
        assertEquals(2, finalBundle.history().path("reviews").size());
        assertArrayEquals(original, Files.readAllBytes(first.path()));
        assertArrayEquals(secondBundle.files().get(reviewPath), finalBundle.files().get(reviewPath));
        assertEquals("<pnml>first</pnml>", new String(finalBundle.files().get("history/revisions/" + firstRevision + "/model.pnml"), StandardCharsets.UTF_8));
        assertEquals("<pnml>corrected</pnml>", finalBundle.pnml());
        assertEquals("Queue task", json.readTree(finalBundle.files().get("task.json")).path("task").asText());
        assertTrue(third.path().getFileName().toString().contains("r003-review002"));
        assertEquals("unknown", json.readTree(finalBundle.files().get("verification.json")).path("status").asText());
        assertEquals("unreviewed", finalBundle.manifest().path("status").asText());
    }

    @Test public void tamperingWithAnImageOrHistoryFailsChecksumValidation() throws Exception {
        var workspace = new TrainingBundleWorkspace();
        var result = exporter(workspace, "<pnml/>", 1).export(folder.getRoot().toPath(), request("Task"));
        Map<String, byte[]> entries = new java.util.LinkedHashMap<>(TrainingBundleWorkspace.read(result.path()).files());
        String revision = workspace.active().history().path("head_revision_id").asText();
        entries.put("history/revisions/" + revision + "/model.pnml", "changed".getBytes(StandardCharsets.UTF_8));
        Path modified = folder.getRoot().toPath().resolve("tampered.zip"); write(modified, entries);
        try { TrainingBundleWorkspace.read(modified); fail("A modified historical model was accepted"); }
        catch (IllegalArgumentException expected) { assertTrue(expected.getMessage().contains("checksum")); }
    }

    @Test public void unsafePathsAndUnlistedPayloadsAreRejected() throws Exception {
        for (String name : List.of("../outside", "C:/outside", "a\\outside", "/outside", "a/../outside")) {
            Path path = folder.getRoot().toPath().resolve(java.util.UUID.randomUUID() + ".zip");
            write(path, Map.of(name, new byte[]{1}));
            try { TrainingBundleWorkspace.read(path); fail(name); } catch (IllegalArgumentException expected) { assertTrue(expected.getMessage().contains("Unsafe")); }
        }
        var workspace = new TrainingBundleWorkspace();
        var result = exporter(workspace, "<pnml/>", 1).export(folder.getRoot().toPath(), request("Task"));
        var entries = new java.util.LinkedHashMap<>(TrainingBundleWorkspace.read(result.path()).files());
        entries.put("unlisted.json", new byte[]{1});
        Path path = folder.getRoot().toPath().resolve("unlisted.zip"); write(path, entries);
        try { TrainingBundleWorkspace.read(path); fail("Unlisted data was accepted"); } catch (IllegalArgumentException expected) { assertTrue(expected.getMessage().contains("unlisted")); }
    }

    @Test public void unknownFindingTargetsDoNotCommitAnArchiveOrAdvanceHistory() throws Exception {
        var workspace = new TrainingBundleWorkspace();
        var exporter = exporter(workspace, "<pnml/>", 1);
        var review = new TrainingBundleWorkspace.Review("A", "human", "rejected", "Wrong node", List.of(new TrainingBundleWorkspace.Finding("error", "Missing tokens", "unknown", "main")));
        try { exporter.export(folder.getRoot().toPath(), request("Task"), review); fail(); }
        catch (IllegalArgumentException expected) { assertTrue(expected.getMessage().contains("Unknown canvas element")); }
        assertNull(workspace.active());
        assertEquals(0, folder.getRoot().list().length);
    }

    @Test public void regionCommentsRoundTripButWrongImageHashesAndOutsideBoundsAreRejected() throws Exception {
        var workspace = new TrainingBundleWorkspace();
        var png = new java.io.ByteArrayOutputStream();
        javax.imageio.ImageIO.write(new java.awt.image.BufferedImage(100, 80, java.awt.image.BufferedImage.TYPE_INT_RGB), "png", png);
        var image = new NetworkImageExporter.RenderedImage(png.toByteArray(), Map.of("elements", List.of(), "image", Map.of("file", "network.png", "width", 100, "height", 80)));
        var exporter = new TrainingDataExporter(screenshot -> new TrainingSnapshot("<pnml/>", Map.of(), image, null, List.of(), "Not observed"),
                Clock.fixed(Instant.parse("2026-10-30T10:00:00Z"), ZoneOffset.UTC), workspace, Map::of);
        exporter.export(folder.getRoot().toPath(), request("Task"));
        var valid = new TrainingBundleWorkspace.Region(10, 15, 30, 20, TrainingBundleWorkspace.hash(png.toByteArray()));
        var review = new TrainingBundleWorkspace.Review("A", "agent", "changes_requested", "Inspect this region",
                List.of(new TrainingBundleWorkspace.Finding("warning", "Marking concern", null, "main", valid)));
        var result = exporter.export(folder.getRoot().toPath(), request("Task"), review);
        var reloaded = TrainingBundleWorkspace.read(result.path());
        String reviewId = reloaded.history().path("reviews").get(0).path("id").asText();
        assertEquals(30, json.readTree(reloaded.files().get("history/reviews/" + reviewId + ".json")).at("/findings/0/region/width").asInt());
        int before = reloaded.history().path("reviews").size();
        for (var bad : List.of(new TrainingBundleWorkspace.Region(90, 10, 30, 20, valid.imageSha256()), new TrainingBundleWorkspace.Region(1, 1, 10, 10, "a".repeat(64)))) {
            try {
                exporter.export(folder.getRoot().toPath(), request("Task"), new TrainingBundleWorkspace.Review("A", "agent", "rejected", "Invalid region", List.of(new TrainingBundleWorkspace.Finding("error", "Invalid", null, "main", bad))));
                fail("Invalid region was exported");
            } catch (IllegalArgumentException expected) { assertTrue(expected.getMessage().contains("region")); }
            assertEquals(before, workspace.active().history().path("reviews").size());
        }
    }

    private void write(Path path, Map<String, byte[]> entries) throws Exception {
        try (var zip = new ZipOutputStream(Files.newOutputStream(path))) {
            for (var entry : entries.entrySet()) { zip.putNextEntry(new ZipEntry(entry.getKey())); zip.write(entry.getValue()); zip.closeEntry(); }
        }
    }
}

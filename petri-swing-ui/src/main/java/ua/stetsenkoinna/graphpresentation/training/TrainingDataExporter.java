package ua.stetsenkoinna.graphpresentation.training;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.text.Normalizer;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** Writes observed evidence and supplied context, without certifying model correctness. */
public final class TrainingDataExporter {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final DateTimeFormatter FILE_TIME = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmssSSS'Z'").withZone(ZoneOffset.UTC);
    @FunctionalInterface public interface SnapshotProvider { TrainingSnapshot capture(boolean includeScreenshot) throws Exception; }
    public record ExportResult(Path path, String sha256, String status, int artifactCount) { }
    private record Artifact(String name, String mediaType, String purpose, byte[] content) { }
    private final SnapshotProvider snapshots;
    private final Clock clock;

    public TrainingDataExporter(SnapshotProvider snapshots) { this(snapshots, Clock.systemUTC()); }
    public TrainingDataExporter(SnapshotProvider snapshots, Clock clock) {
        this.snapshots = snapshots;
        this.clock = clock;
    }

    public ExportResult export(Path directory, TrainingExportRequest request) throws Exception {
        TrainingSnapshot snapshot = capture(request.includeScreenshot());
        Instant createdAt = clock.instant();
        String title = TrainingMetadataRedactor.redactText(request.title());
        List<Artifact> artifacts = new ArrayList<>();
        List<Map<String, String>> missing = new ArrayList<>();
        Map<String, Object> task = new LinkedHashMap<>();
        task.put("task", request.task());
        task.put("clarifications", request.clarifications());
        task.put("assumptions", request.assumptions());
        task.put("acceptance", request.acceptance());
        artifacts.add(json("task.json", "User-supplied task, clarifications, assumptions and acceptance criteria", TrainingMetadataRedactor.redact(task)));
        artifacts.add(new Artifact("model.pnml", "application/xml", "Current document serialized by the editor's PNML writer", snapshot.modelPnml().getBytes(StandardCharsets.UTF_8)));
        artifacts.add(new Artifact("conversation.jsonl", "application/x-ndjson", "Actual messages supplied by the external client; no inferred private reasoning", jsonLines(request.conversation())));
        if (request.task() == null) missing.add(missing("task.json.task", "No task description was supplied"));
        if (request.conversation().isEmpty()) missing.add(missing("conversation.jsonl", "The editor cannot observe the external client's conversation; no messages were supplied"));
        List<Map<String, Object>> calls = validatedCalls(snapshot.toolCalls());
        artifacts.add(new Artifact("tool-calls.jsonl", "application/x-ndjson", "Recorded tool calls only; snapshots containing connection credentials are excluded", jsonLines(calls)));
        if (calls.isEmpty()) missing.add(missing("tool-calls.jsonl", snapshot.toolCaptureNote()));
        else missing.add(missing("complete-tool-history", snapshot.toolCaptureNote()));
        Map<String, Object> verification = new LinkedHashMap<>();
        verification.put("status", "unknown");
        verification.put("checks", List.of());
        verification.put("simulation", null);
        artifacts.add(json("verification.json", "No model acceptance or simulation success is inferred from an export", verification));
        missing.add(missing("verification.json.checks", "No structured correctness checks were recorded for this snapshot"));
        missing.add(missing("verification.json.simulation", "No structured simulation run tied to this exact snapshot was recorded; editor panels are raw observations only"));
        if (!snapshot.observations().isEmpty()) {
            artifacts.add(json("editor-observations.json", "Raw editor controls, potentially stale output and viewport metadata; not verification results", TrainingMetadataRedactor.redact(snapshot.observations())));
        }
        if (snapshot.networkImage() != null) {
            artifacts.add(new Artifact("network.png", "image/png", "Existing active-canvas rendering without window chrome; current collapsed/focused visibility is preserved", snapshot.networkImage().png()));
            artifacts.add(json("model-geometry.json", "Source canvas shape coordinates and image transform; unverified geometry is not accepted detector labeling", snapshot.networkImage().geometry()));
        }
        if (snapshot.screenshot() != null) {
            artifacts.add(new Artifact("screenshot.png", "image/png", "Main-window QA view only, excluding owned connection dialogs; not a detector image", snapshot.screenshot()));
        } else if (request.includeScreenshot()) {
            missing.add(missing("screenshot.png", "The main window was not visible or was too large for the requested QA screenshot"));
        }
        Map<String, Object> manifest = new LinkedHashMap<>();
        manifest.put("schema_version", "1.0");
        manifest.put("title", title);
        manifest.put("created_at", createdAt.toString());
        manifest.put("app", Map.of("name", "PetriObjModelPaint", "version", appVersion()));
        manifest.put("shared_skills", sharedSkills());
        manifest.put("status", request.status());
        manifest.put("capture_scope", Map.of("conversation", request.conversation().isEmpty() ? "unavailable" : "client-provided",
                "tool_calls", calls.isEmpty() ? "unavailable"
                        : calls.stream().allMatch(call -> "application".equals(call.get("origin"))) ? "application" : "client-provided"));
        List<Map<String, Object>> files = new ArrayList<>();
        for (Artifact artifact : artifacts) {
            files.add(Map.of("name", artifact.name(), "media_type", artifact.mediaType(),
                    "sha256", hash(artifact.content()), "purpose", artifact.purpose()));
        }
        manifest.put("files", files);
        manifest.put("missing_data", missing);

        Path destination = directory.toAbsolutePath().normalize();
        Files.createDirectories(destination);
        String baseName = "training-" + slug(title) + "-" + FILE_TIME.format(createdAt);
        Path target = destination.resolve(baseName + ".zip");
        if (Files.exists(target)) target = destination.resolve(baseName + "-" + UUID.randomUUID().toString().substring(0, 8) + ".zip");
        Path temporary = Files.createTempFile(destination, ".petri-training-", ".tmp");
        try {
            try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(temporary), StandardCharsets.UTF_8)) {
                write(zip, "manifest.json", JSON.writerWithDefaultPrettyPrinter().writeValueAsBytes(TrainingMetadataRedactor.redact(manifest)), createdAt);
                for (Artifact artifact : artifacts) write(zip, artifact.name(), artifact.content(), createdAt);
            }
            Files.move(temporary, target);
            return new ExportResult(target, hash(Files.readAllBytes(target)), request.status(), artifacts.size());
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private TrainingSnapshot capture(boolean includeScreenshot) throws Exception {
        return SwingCapture.call(() -> snapshots.capture(includeScreenshot));
    }

    private static List<Map<String, Object>> validatedCalls(List<Map<String, Object>> calls) {
        for (Map<String, Object> call : calls) {
            if (!(call.get("tool") instanceof String name) || name.isBlank() || !(call.get("arguments") instanceof Map)
                    || !(call.get("origin") instanceof String origin) || !Set.of("application", "client-provided").contains(origin)) {
                throw new IllegalArgumentException("Invalid recorded tool call");
            }
            if (!Set.of("tool", "arguments", "timestamp", "origin", "result", "error").containsAll(call.keySet())) {
                throw new IllegalArgumentException("Unsupported recorded tool-call field");
            }
            TrainingExportRequest.validateTimestamp(call.get("timestamp"));
        }
        return calls;
    }

    private static Artifact json(String name, String purpose, Object value) throws Exception {
        return new Artifact(name, "application/json", purpose, JSON.writerWithDefaultPrettyPrinter().writeValueAsBytes(value));
    }

    private static byte[] jsonLines(List<Map<String, Object>> records) throws Exception {
        StringBuilder output = new StringBuilder();
        for (Map<String, Object> record : records) output.append(JSON.writeValueAsString(TrainingMetadataRedactor.redact(record))).append('\n');
        return output.toString().getBytes(StandardCharsets.UTF_8);
    }

    private static Map<String, String> missing(String artifact, String reason) {
        return Map.of("artifact", artifact, "reason", reason);
    }

    private static Map<String, Object> sharedSkills() throws Exception {
        Map<String, Object> result = new LinkedHashMap<>();
        try (InputStream input = TrainingDataExporter.class.getResourceAsStream("/petri-modeling-skills/manifest.json")) {
            JsonNode manifest = input == null ? null : JSON.readTree(input);
            result.put("version", manifest == null ? "unknown" : manifest.path("version").asText("unknown"));
        }
        result.put("revision", null);
        return result;
    }

    private static String appVersion() throws Exception {
        Properties properties = new Properties();
        try (InputStream input = TrainingDataExporter.class.getResourceAsStream("/ua/stetsenkoinna/graphpresentation/training/build.properties")) {
            if (input != null) properties.load(input);
        }
        String version = properties.getProperty("app.version", "unknown");
        return version.startsWith("${") ? "unknown" : version;
    }

    private static String slug(String title) {
        String value = Normalizer.normalize(title, Normalizer.Form.NFKC)
                .replaceAll("[^\\p{L}\\p{N}-]+", "-").replaceAll("^-+|-+$", "");
        if (value.isBlank()) return "session";
        return value.codePointCount(0, value.length()) > 60 ? value.substring(0, value.offsetByCodePoints(0, 60)) : value;
    }

    private static String hash(byte[] content) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
    }

    private static void write(ZipOutputStream zip, String name, byte[] content, Instant createdAt) throws Exception {
        ZipEntry entry = new ZipEntry(name);
        entry.setTime(createdAt.toEpochMilli());
        zip.putNextEntry(entry);
        zip.write(content);
        zip.closeEntry();
    }
}

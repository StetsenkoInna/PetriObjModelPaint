package ua.stetsenkoinna.graphpresentation.training;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

/** Portable, append-only revision/review history. Hashes detect corruption, not reviewer identity. */
public final class TrainingBundleWorkspace {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final int MAX_FILE = 64 * 1024 * 1024;
    private static final long MAX_TOTAL = 256L * 1024 * 1024;
    private static final String INDEX = "dataset-history.json";
    private Bundle active;

    public record Region(double x, double y, double width, double height, String imageSha256) {
        public Region {
            if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(width) || !Double.isFinite(height)
                    || x < 0 || y < 0 || width <= 0 || height <= 0 || imageSha256 == null || !imageSha256.matches("[a-f0-9]{64}")) throw new IllegalArgumentException("A review region needs positive pixel bounds and the exact image SHA-256");
        }
        public Map<String, Object> fields() { return Map.of("coordinate_system", "image-pixels", "image_sha256", imageSha256, "x", x, "y", y, "width", width, "height", height); }
        @Override public String toString() { return String.format(java.util.Locale.ROOT, "%.0f, %.0f · %.0f × %.0f px", x, y, width, height); }
    }

    public record Finding(String severity, String message, String elementId, String viewId, Region region) {
        public Finding {
            if (!Set.of("error", "warning", "suggestion").contains(severity)) throw new IllegalArgumentException("Invalid finding severity");
            if (message == null || message.isBlank()) throw new IllegalArgumentException("A finding needs a message");
            if (region != null && (viewId == null || viewId.isBlank())) throw new IllegalArgumentException("A region must identify its image view");
        }
        public Finding(String severity, String message, String elementId, String viewId) { this(severity, message, elementId, viewId, null); }
    }

    public static Region region(JsonNode value) {
        if (value == null || value.isNull() || value.isMissingNode()) return null;
        if (!"image-pixels".equals(value.path("coordinate_system").asText())) throw new IllegalArgumentException("Review region coordinates must be image pixels");
        for (String key : List.of("x", "y", "width", "height")) if (!value.path(key).isNumber()) throw new IllegalArgumentException("Review region bounds must be numbers");
        return new Region(value.path("x").asDouble(), value.path("y").asDouble(), value.path("width").asDouble(), value.path("height").asDouble(), value.path("image_sha256").asText());
    }

    public record Review(String reviewer, String reviewerKind, String decision, String comment, List<Finding> findings, String revisionId, SimulationPlan simulationPlan) {
        public Review {
            if (reviewer == null || reviewer.isBlank()) throw new IllegalArgumentException("A reviewer name is required");
            if (!Set.of("human", "agent").contains(reviewerKind)) throw new IllegalArgumentException("Reviewer kind must be human or agent");
            if (!Set.of("accepted", "changes_requested", "rejected").contains(decision)) throw new IllegalArgumentException("Invalid review decision");
            if (comment == null || comment.isBlank()) throw new IllegalArgumentException("A review comment is required");
            findings = findings == null ? List.of() : List.copyOf(findings);
            simulationPlan = simulationPlan == null ? SimulationPlan.defaults() : simulationPlan;
        }
        public Review(String reviewer, String reviewerKind, String decision, String comment, List<Finding> findings, String revisionId) {
            this(reviewer, reviewerKind, decision, comment, findings, revisionId, SimulationPlan.defaults());
        }
        public Review(String reviewer, String reviewerKind, String decision, String comment, List<Finding> findings) {
            this(reviewer, reviewerKind, decision, comment, findings, null);
        }
    }

    public record Bundle(Path path, Map<String, byte[]> files) {
        public Bundle { files = Map.copyOf(files); }
        public String pnml() { return new String(files.get("model.pnml"), StandardCharsets.UTF_8); }
        public JsonNode manifest() throws Exception { return JSON.readTree(files.get("manifest.json")); }
        public JsonNode history() throws Exception { return files.containsKey(INDEX) ? JSON.readTree(files.get(INDEX)) : JSON.createObjectNode(); }
    }

    public synchronized void clear() { active = null; }
    public synchronized Bundle active() { return active; }
    public synchronized void adopt(Bundle bundle) { active = bundle; }

    public synchronized TrainingExportRequest exportRequest(String title, List<Map<String, Object>> newMessages) throws Exception {
        String task = null;
        List<String> clarifications = List.of(), assumptions = List.of(), acceptance = List.of();
        List<Map<String, Object>> conversation = new ArrayList<>();
        if (active != null) {
            if (title == null || title.isBlank()) title = active.manifest().path("title").asText("Reviewed model");
            if (active.files().containsKey("task.json")) {
                JsonNode context = JSON.readTree(active.files().get("task.json"));
                task = context.path("task").isTextual() ? context.path("task").asText() : null;
                clarifications = strings(context.path("clarifications")); assumptions = strings(context.path("assumptions")); acceptance = strings(context.path("acceptance"));
            }
            if (active.files().containsKey("conversation.jsonl")) {
                for (String line : new String(active.files().get("conversation.jsonl"), StandardCharsets.UTF_8).split("\\R")) {
                    if (!line.isBlank()) conversation.add(JSON.readValue(line, new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() { }));
                }
            }
        }
        if (newMessages != null) conversation.addAll(newMessages);
        return new TrainingExportRequest(title == null || title.isBlank() ? "Reviewed model" : title,
                task, clarifications, assumptions, acceptance, "unreviewed", conversation, false);
    }

    private static List<String> strings(JsonNode array) {
        List<String> values = new ArrayList<>();
        for (JsonNode item : array) values.add(item.asText());
        return values;
    }

    public synchronized Map<String, Object> summary() throws Exception {
        if (active == null) return Map.of("loaded", false);
        List<JsonNode> reviews = new ArrayList<>();
        for (JsonNode reference : active.history().path("reviews")) reviews.add(JSON.readTree(active.files().get("history/reviews/" + reference.path("id").asText() + ".json")));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("loaded", true); result.put("path", active.path().toString()); result.put("manifest", active.manifest());
        result.put("history", active.history()); result.put("reviews", reviews);
        if (active.files().containsKey("task.json")) result.put("task", JSON.readTree(active.files().get("task.json")));
        if (active.files().containsKey("network-views.json")) result.put("network_views", JSON.readTree(active.files().get("network-views.json")));
        return result;
    }

    public synchronized Map<String, byte[]> revisionFiles(String id) throws Exception {
        if (active == null) throw new IllegalArgumentException("Open a training ZIP first");
        if ("head".equals(id) || "loaded".equals(id)) return snapshot(active.files());
        if (id == null || !id.matches("[a-f0-9]{64}")) throw new IllegalArgumentException("Unknown revision");
        Map<String, byte[]> result = new LinkedHashMap<>();
        String prefix = "history/revisions/" + id + "/";
        for (var file : active.files().entrySet()) if (file.getKey().startsWith(prefix)) result.put(file.getKey().substring(prefix.length()), file.getValue());
        if (result.isEmpty()) throw new IllegalArgumentException("Unknown revision");
        return result;
    }

    public synchronized List<TrainingRevisionDiff.Change> compare(String left, String right, boolean includeLayout) throws Exception {
        return TrainingRevisionDiff.compare(revisionFiles(left).get("model.pnml"), revisionFiles(right).get("model.pnml"), includeLayout);
    }

    public synchronized String fileSuffix(boolean reviewing) throws Exception {
        int revisions = active == null ? 0 : active.history().path("revisions").size();
        if (active != null && revisions == 0) revisions = 1;
        int reviews = active == null ? 0 : active.history().path("reviews").size();
        return String.format(java.util.Locale.ROOT, "-r%03d-review%03d", revisions + 1, reviews + (reviewing ? 1 : 0));
    }

    /** Reads entries into memory, never extracts paths from an untrusted ZIP onto disk. */
    public static Bundle read(Path path) throws Exception {
        Map<String, byte[]> files = new LinkedHashMap<>();
        long total = 0;
        int count = 0;
        try (ZipFile zip = new ZipFile(path.toFile(), StandardCharsets.UTF_8)) {
            var entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                if (++count > 10000) throw new IllegalArgumentException("Training ZIP contains too many files");
                String name = entry.getName();
                if (entry.isDirectory()) name = name.substring(0, name.length() - 1);
                safeName(name);
                if (entry.isDirectory()) continue;
                if (files.containsKey(name)) throw new IllegalArgumentException("Duplicate ZIP entry: " + name);
                if (entry.getSize() > MAX_FILE) throw new IllegalArgumentException("Training ZIP entry exceeds 64 MiB: " + name);
                byte[] data;
                try (var input = zip.getInputStream(entry)) { data = input.readNBytes(MAX_FILE + 1); }
                total += data.length;
                if (data.length > MAX_FILE || total > MAX_TOTAL) throw new IllegalArgumentException("Training ZIP exceeds the size limit (64 MiB per file, 256 MiB total)");
                files.put(name, data);
            }
        }
        validate(files);
        return new Bundle(path.toAbsolutePath().normalize(), files);
    }

    private static void safeName(String name) {
        if (name.isBlank() || name.startsWith("/") || name.contains("\\") || name.contains(":")
                || name.chars().anyMatch(c -> c < 32)) throw new IllegalArgumentException("Unsafe ZIP path");
        for (String part : name.split("/", -1)) {
            if (part.isEmpty() || part.equals(".") || part.equals("..")) throw new IllegalArgumentException("Unsafe ZIP path: " + name);
        }
    }

    private static void validate(Map<String, byte[]> files) throws Exception {
        if (!files.containsKey("manifest.json") || !files.containsKey("model.pnml")) throw new IllegalArgumentException("A training ZIP requires manifest.json and model.pnml");
        JsonNode manifest = JSON.readTree(files.get("manifest.json"));
        if (!Set.of("1.0", "1.1").contains(manifest.path("schema_version").asText()) || !manifest.path("files").isArray()) throw new IllegalArgumentException("Unsupported training manifest");
        Set<String> listed = new java.util.HashSet<>();
        for (JsonNode file : manifest.path("files")) {
            String name = file.path("name").asText();
            if (!listed.add(name) || name.equals("manifest.json") || !files.containsKey(name)
                    || !hash(files.get(name)).equals(file.path("sha256").asText())) throw new IllegalArgumentException("Training ZIP checksum mismatch: " + name);
        }
        if (listed.size() != files.size() - 1) throw new IllegalArgumentException("Training ZIP contains unlisted files");
        for (var file : files.entrySet()) if (file.getKey().endsWith(".png")) checkImage(file.getValue(), false);
        if (!files.containsKey(INDEX)) {
            if (files.keySet().stream().anyMatch(name -> name.startsWith("history/"))) throw new IllegalArgumentException("History index is missing");
            return;
        }
        JsonNode index = JSON.readTree(files.get(INDEX));
        if (!"1.0".equals(index.path("schema_version").asText()) || index.path("bundle_id").asText().isBlank()
                || !index.path("revisions").isArray() || !index.path("reviews").isArray()) throw new IllegalArgumentException("Unsupported dataset history");
        Set<String> revisions = new java.util.HashSet<>();
        Set<String> modelHashes = new java.util.HashSet<>();
        String previous = "";
        for (JsonNode revision : index.path("revisions")) {
            String id = revision.path("id").asText();
            if (!id.matches("[a-f0-9]{64}") || !revisions.add(id) || !previous.equals(revision.path("parent_revision_id").asText())) throw new IllegalArgumentException("Invalid revision chain");
            String prefix = "history/revisions/" + id + "/";
            byte[] snapshot = files.get(prefix + "manifest.json");
            if (snapshot == null || !hash(snapshot).equals(id)) throw new IllegalArgumentException("Revision manifest mismatch");
            Map<String, byte[]> payload = new LinkedHashMap<>();
            for (var entry : files.entrySet()) if (entry.getKey().startsWith(prefix)) payload.put(entry.getKey().substring(prefix.length()), entry.getValue());
            if (payload.containsKey(INDEX) || payload.keySet().stream().anyMatch(n -> n.startsWith("history/"))) throw new IllegalArgumentException("Recursive history is not supported");
            validate(payload);
            if (!hash(payload.get("model.pnml")).equals(revision.path("model_sha256").asText())) throw new IllegalArgumentException("Revision model mismatch");
            modelHashes.add(revision.path("model_sha256").asText());
            previous = id;
        }
        if (!previous.equals(index.path("head_revision_id").asText())) throw new IllegalArgumentException("Invalid history head");
        Set<String> reviews = new java.util.HashSet<>();
        previous = "";
        for (JsonNode reference : index.path("reviews")) {
            String id = reference.path("id").asText();
            if (!id.matches("[a-f0-9]{64}") || !reviews.add(id)) throw new IllegalArgumentException("Invalid review identifier");
            byte[] data = files.get("history/reviews/" + id + ".json");
            if (data == null || !hash(data).equals(id)) throw new IllegalArgumentException("Review content mismatch");
            JsonNode review = JSON.readTree(data);
            if (!revisions.contains(review.path("revision_id").asText()) || !revisions.contains(review.path("result_revision_id").asText())
                    || !previous.equals(review.path("previous_review_id").asText())) throw new IllegalArgumentException("Invalid review chain");
            List<Finding> findings = new ArrayList<>();
            for (JsonNode finding : review.path("findings")) findings.add(new Finding(finding.path("severity").asText(), finding.path("message").asText(),
                    finding.path("element_id").asText(null), finding.path("view_id").asText(null), region(finding.path("region"))));
            Map<String, byte[]> reviewed = new LinkedHashMap<>();
            String revisionPrefix = "history/revisions/" + review.path("revision_id").asText() + "/";
            for (var file : files.entrySet()) if (file.getKey().startsWith(revisionPrefix)) reviewed.put(file.getKey().substring(revisionPrefix.length()), file.getValue());
            validateTargets(new Review(review.path("reviewer").asText(), review.path("reviewer_kind").asText(), review.path("decision").asText(), review.path("comment").asText(), findings), reviewed);
            if (review.has("simulation")) validateSimulation(review.path("simulation"), hash(reviewed.get("model.pnml")), modelHashes);
            previous = id;
        }
        Map<String, byte[]> root = snapshot(files);
        if (!hash(root.get("manifest.json")).equals(index.path("head_revision_id").asText())) throw new IllegalArgumentException("Current files do not match the history head");
    }

    private static void validateSimulation(JsonNode evidence, String targetHash, Set<String> knownModels) {
        if (!"attempt-or-reason".equals(evidence.path("policy").asText()) || !targetHash.equals(evidence.path("source_model_sha256").asText())
                || !evidence.path("automatic_runs").isArray() || !evidence.path("interactive_runs").isArray()
                || (evidence.path("automatic_runs").isEmpty() && evidence.path("skip_reason").asText().isBlank())) throw new IllegalArgumentException("Invalid review simulation evidence");
        for (String group : List.of("automatic_runs", "interactive_runs")) for (JsonNode run : evidence.path(group)) {
            String source = run.path("source_model_sha256").asText();
            if (!knownModels.contains(source) || (group.equals("automatic_runs") && !targetHash.equals(source))
                    || !Set.of("completed", "failed", "limit_reached", "stopped").contains(run.path("status").asText())) throw new IllegalArgumentException("Simulation targets an unknown model or status");
            JsonNode series = run.path("trajectory");
            if (!series.path("samples").isArray() || !series.path("columns").isArray() || series.path("samples").size() > 20001 || series.path("effective_interval").asDouble() <= 0) throw new IllegalArgumentException("Invalid simulation trajectory");
            double previous = -1;
            for (JsonNode row : series.path("samples")) {
                if (!row.isArray() || row.size() != series.path("columns").size()+1) throw new IllegalArgumentException("Simulation sample columns do not match");
                for (JsonNode value : row) if (!value.isNumber() || !Double.isFinite(value.asDouble())) throw new IllegalArgumentException("Non-finite simulation sample");
                double time = row.get(0).asDouble(); if (time < 0 || time <= previous) throw new IllegalArgumentException("Simulation sample times are not increasing"); previous = time;
            }
        }
    }

    public static java.awt.image.BufferedImage readImage(byte[] bytes) throws Exception { return checkImage(bytes, true); }

    private static java.awt.image.BufferedImage checkImage(byte[] bytes, boolean decode) throws Exception {
        try (var input = new javax.imageio.stream.MemoryCacheImageInputStream(new java.io.ByteArrayInputStream(bytes))) {
            var readers = javax.imageio.ImageIO.getImageReaders(input);
            if (!readers.hasNext()) throw new IllegalArgumentException("Invalid training PNG");
            var reader = readers.next();
            try {
                if (!"png".equalsIgnoreCase(reader.getFormatName())) throw new IllegalArgumentException("Training image must be a PNG");
                reader.setInput(input);
                int width = reader.getWidth(0), height = reader.getHeight(0);
                if (width <= 0 || height <= 0 || width > 8192 || height > 8192 || (long) width * height > 24_020_000) throw new IllegalArgumentException("Training image exceeds the supported pixel limit");
                return decode ? reader.read(0) : null;
            } finally { reader.dispose(); }
        }
    }

    /** Decorate a fresh snapshot, preserving every older snapshot and review byte for byte. */
    public synchronized void write(Path freshZip, Path destination, Review review, Bundle expectedBundle) throws Exception {
        if (active != expectedBundle) throw new IllegalStateException("The training document changed while exporting; retry for the current document");
        Bundle fresh = read(freshZip);
        Map<String, byte[]> output = new LinkedHashMap<>(fresh.files());
        ObjectNode index;
        if (active == null) {
            index = JSON.createObjectNode();
            index.put("schema_version", "1.0");
            index.put("bundle_id", UUID.randomUUID().toString());
            index.putArray("revisions");
            index.putArray("reviews");
            index.put("head_revision_id", "");
        } else {
            index = active.files().containsKey(INDEX) ? (ObjectNode) active.history().deepCopy() : JSON.createObjectNode();
            if (!index.has("bundle_id")) {
                index.put("schema_version", "1.0");
                index.put("bundle_id", UUID.randomUUID().toString());
                index.putArray("revisions"); index.putArray("reviews"); index.put("head_revision_id", "");
            }
            for (var entry : active.files().entrySet()) if (entry.getKey().startsWith("history/")) output.put(entry.getKey(), entry.getValue());
            appendRevision(output, index, snapshot(active.files()));
        }
        appendRevision(output, index, snapshot(fresh.files()));
        if (review != null) {
            String targetId = review.revisionId();
            if ("current".equals(targetId) || active == null) targetId = index.path("head_revision_id").asText();
            else if (targetId == null || targetId.isBlank() || "loaded".equals(targetId)) targetId = hash(snapshot(active.files()).get("manifest.json"));
            Map<String, byte[]> reviewedFiles = new LinkedHashMap<>();
            String prefix = "history/revisions/" + targetId + "/";
            for (var entry : output.entrySet()) if (entry.getKey().startsWith(prefix)) reviewedFiles.put(entry.getKey().substring(prefix.length()), entry.getValue());
            if (reviewedFiles.isEmpty()) throw new IllegalArgumentException("Unknown reviewed revision");
            validateTargets(review, reviewedFiles);
            ObjectNode record = JSON.valueToTree(TrainingMetadataRedactor.redact(Map.of("reviewer", review.reviewer(),
                    "reviewer_kind", review.reviewerKind(), "decision", review.decision(), "comment", review.comment())));
            record.set("findings", JSON.valueToTree(review.findings().stream().map(finding -> {
                Map<String, Object> value = new LinkedHashMap<>();
                value.put("severity", finding.severity()); value.put("message", finding.message());
                if (finding.elementId() != null && !finding.elementId().isBlank()) value.put("element_id", finding.elementId());
                if (finding.viewId() != null && !finding.viewId().isBlank()) value.put("view_id", finding.viewId());
                if (finding.region() != null) value.put("region", finding.region().fields());
                return TrainingMetadataRedactor.redact(value);
            }).toList()));
            record.set("simulation", JSON.valueToTree(TrainingMetadataRedactor.redact(SimulationEvidence.automatic(reviewedFiles.get("model.pnml"), review.simulationPlan()))));
            record.put("revision_id", targetId);
            record.put("result_revision_id", index.path("head_revision_id").asText());
            record.put("created_at", Instant.now().toString());
            var reviews = index.withArray("reviews");
            record.put("previous_review_id", reviews.isEmpty() ? "" : reviews.get(reviews.size() - 1).path("id").asText());
            record.put("verification_status", "unknown");
            if (fresh.manifest().has("contributor")) record.set("submitted_by", fresh.manifest().get("contributor"));
            byte[] bytes = JSON.writeValueAsBytes(record);
            String id = hash(bytes);
            output.put("history/reviews/" + id + ".json", bytes);
            reviews.addObject().put("id", id);
        }
        output.put(INDEX, JSON.writeValueAsBytes(index));
        ObjectNode manifest = (ObjectNode) fresh.manifest();
        var listing = manifest.putArray("files");
        for (var entry : output.entrySet()) if (!entry.getKey().equals("manifest.json")) {
            listing.addObject().put("name", entry.getKey()).put("sha256", hash(entry.getValue()))
                    .put("media_type", mediaType(entry.getKey())).put("purpose", "Training evidence and preserved revision history");
        }
        output.put("manifest.json", JSON.writeValueAsBytes(manifest));
        // Root manifest metadata stays stable when history is filtered back out.
        validate(output);
        long total = output.values().stream().mapToLong(bytes -> bytes.length).sum();
        if (total > MAX_TOTAL || output.size() > 10000) throw new IllegalArgumentException("Dataset history exceeds the ZIP size/file limit; start a separate bundle");
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(freshZip), StandardCharsets.UTF_8)) {
            for (var entry : output.entrySet()) {
                zip.putNextEntry(new ZipEntry(entry.getKey())); zip.write(entry.getValue()); zip.closeEntry();
            }
        }
        Files.move(freshZip, destination);
        active = new Bundle(destination.toAbsolutePath().normalize(), output);
    }

    private static void appendRevision(Map<String, byte[]> output, ObjectNode index, Map<String, byte[]> snapshot) throws Exception {
        String id = hash(snapshot.get("manifest.json"));
        if (id.equals(index.path("head_revision_id").asText())) return;
        if (index.withArray("revisions").findValuesAsText("id").contains(id)) throw new IllegalArgumentException("Cannot append a previous revision as a new head");
        for (var entry : snapshot.entrySet()) output.put("history/revisions/" + id + "/" + entry.getKey(), entry.getValue());
        index.withArray("revisions").addObject().put("id", id).put("parent_revision_id", index.path("head_revision_id").asText())
                .put("model_sha256", hash(snapshot.get("model.pnml")));
        index.put("head_revision_id", id);
    }

    private static Map<String, byte[]> snapshot(Map<String, byte[]> files) throws Exception {
        Map<String, byte[]> result = new LinkedHashMap<>();
        for (var entry : new java.util.TreeMap<>(files).entrySet()) if (!entry.getKey().equals(INDEX) && !entry.getKey().startsWith("history/")) result.put(entry.getKey(), entry.getValue());
        ObjectNode manifest = (ObjectNode) JSON.readTree(files.get("manifest.json"));
        var listing = manifest.putArray("files");
        for (var entry : result.entrySet()) if (!entry.getKey().equals("manifest.json")) {
            listing.addObject().put("name", entry.getKey()).put("sha256", hash(entry.getValue()))
                    .put("media_type", mediaType(entry.getKey())).put("purpose", "Training evidence and preserved revision history");
        }
        result.put("manifest.json", JSON.writeValueAsBytes(manifest));
        return result;
    }

    private static void validateTargets(Review review, Map<String, byte[]> files) throws Exception {
        Set<String> views = new java.util.HashSet<>(Set.of("main"));
        if (files.containsKey("network-views.json")) for (JsonNode view : JSON.readTree(files.get("network-views.json")).path("views")) views.add(view.path("id").asText());
        Set<String> elements = new java.util.HashSet<>();
        if (files.containsKey("model-geometry.json")) for (JsonNode element : JSON.readTree(files.get("model-geometry.json")).path("elements")) elements.add(element.path("id").asText());
        for (Finding finding : review.findings()) {
            if (finding.viewId() != null && !finding.viewId().isBlank() && !views.contains(finding.viewId())) throw new IllegalArgumentException("Unknown image view: " + finding.viewId());
            if (finding.elementId() != null && !finding.elementId().isBlank() && !elements.contains(finding.elementId())) throw new IllegalArgumentException("Unknown canvas element: " + finding.elementId());
            if (finding.region() != null) {
                String imagePath = finding.viewId().equals("main") ? "network.png" : null;
                if (files.containsKey("network-views.json")) for (JsonNode view : JSON.readTree(files.get("network-views.json")).path("views")) if (finding.viewId().equals(view.path("id").asText())) imagePath = view.path("image").asText();
                if (imagePath == null || !files.containsKey(imagePath) || !hash(files.get(imagePath)).equals(finding.region().imageSha256())) throw new IllegalArgumentException("Review region image does not match the reviewed revision");
                checkImage(files.get(imagePath), false);
                var header = java.nio.ByteBuffer.wrap(files.get(imagePath), 16, 8);
                int width = header.getInt(), height = header.getInt();
                if (finding.region().x() + finding.region().width() > width + 0.001 || finding.region().y() + finding.region().height() > height + 0.001) throw new IllegalArgumentException("Review region extends outside its image");
            }
            if (finding.viewId() != null && !finding.viewId().isBlank() && finding.elementId() != null && !finding.elementId().isBlank()
                    && files.containsKey("network-views.json")) {
                for (JsonNode view : JSON.readTree(files.get("network-views.json")).path("views")) {
                    if (!finding.viewId().equals(view.path("id").asText())) continue;
                    String geometry = view.path("geometry").asText();
                    Set<String> visible = new java.util.HashSet<>();
                    if (files.containsKey(geometry)) for (JsonNode element : JSON.readTree(files.get(geometry)).path("elements")) visible.add(element.path("id").asText());
                    if (!visible.contains(finding.elementId())) throw new IllegalArgumentException("The finding's element is not present in that image view");
                }
            }
        }
    }

    private static String mediaType(String name) {
        return name.endsWith(".png") ? "image/png" : name.endsWith(".pnml") ? "application/xml"
                : name.endsWith(".jsonl") ? "application/x-ndjson" : name.endsWith(".json") ? "application/json" : "application/octet-stream";
    }

    public static String hash(byte[] bytes) throws Exception { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
}

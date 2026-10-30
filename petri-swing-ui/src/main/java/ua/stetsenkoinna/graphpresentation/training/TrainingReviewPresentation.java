package ua.stetsenkoinna.graphpresentation.training;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Human-facing labels only. Archive paths, hashes and machine records stay in the data layer. */
public final class TrainingReviewPresentation {
    private static final ObjectMapper JSON = new ObjectMapper();
    private TrainingReviewPresentation() { }
    public record ImageChoice(String path, String label) { @Override public String toString() { return label; } }

    public static String label(String code) {
        return switch (code) {
            case "human" -> "Person";
            case "agent" -> "AI assistant";
            case "changes_requested" -> "Needs changes";
            case "accepted" -> "Accepted";
            case "rejected" -> "Rejected";
            case "error" -> "Error";
            case "warning" -> "Warning";
            case "suggestion" -> "Suggestion";
            default -> code;
        };
    }

    public static Map<String, String> versions(TrainingBundleWorkspace.Bundle bundle) throws Exception {
        Map<String, String> result = new LinkedHashMap<>();
        int number = 0;
        for (JsonNode revision : bundle.history().path("revisions")) result.put(revision.path("id").asText(), "Version " + ++number);
        return result;
    }

    public static List<ImageChoice> images(TrainingBundleWorkspace.Bundle bundle) throws Exception {
        List<ImageChoice> choices = new ArrayList<>();
        var versions = versions(bundle);
        String head = bundle.history().path("head_revision_id").asText();
        addImages(choices, bundle, "", versions.getOrDefault(head, "Saved version"));
        for (var entry : versions.entrySet()) if (!entry.getKey().equals(head)) addImages(choices, bundle, "history/revisions/" + entry.getKey() + "/", entry.getValue());
        return choices;
    }

    private static void addImages(List<ImageChoice> choices, TrainingBundleWorkspace.Bundle bundle, String prefix, String version) throws Exception {
        java.util.Set<String> added = new java.util.HashSet<>();
        String index = prefix + "network-views.json";
        if (bundle.files().containsKey(index)) for (JsonNode view : JSON.readTree(bundle.files().get(index)).path("views")) {
            String path = prefix + view.path("image").asText();
            if (!bundle.files().containsKey(path)) continue;
            String name = view.path("id").asText().equals("main") ? "Complete network" : view.path("name").asText("Nested network");
            choices.add(new ImageChoice(path, version + " - " + name)); added.add(path);
        }
        if (bundle.files().containsKey(prefix + "network.png") && added.add(prefix + "network.png")) choices.add(new ImageChoice(prefix + "network.png", version + " - Complete network"));
        if (bundle.files().containsKey(prefix + "screenshot.png")) choices.add(new ImageChoice(prefix + "screenshot.png", version + " - Application view"));
    }

    public static String context(TrainingBundleWorkspace.Bundle bundle) throws Exception {
        if (bundle == null) return "No archive is open. Saving a review will start a new history from the current network.";
        StringBuilder text = new StringBuilder();
        text.append(bundle.manifest().path("title").asText("Network review")).append("\n\n");
        if (bundle.files().containsKey("task.json")) {
            JsonNode task = JSON.readTree(bundle.files().get("task.json"));
            text.append("Task\n").append(task.path("task").isTextual() ? task.path("task").asText() : "No task was supplied.").append("\n\n");
            section(text, "Clarifications", task.path("clarifications")); section(text, "Assumptions", task.path("assumptions")); section(text, "Acceptance criteria", task.path("acceptance"));
        }
        var versions = versions(bundle);
        text.append("History\n").append(Math.max(1, versions.size())).append(" saved versions; ").append(bundle.history().path("reviews").size()).append(" reviews.\n\n");
        for (JsonNode reference : bundle.history().path("reviews")) {
            JsonNode review = JSON.readTree(bundle.files().get("history/reviews/" + reference.path("id").asText() + ".json"));
            text.append(versions.getOrDefault(review.path("revision_id").asText(), "Saved version")).append(" · ")
                    .append(review.path("reviewer").asText("Reviewer")).append(" · ").append(label(review.path("reviewer_kind").asText()))
                    .append(" · ").append(label(review.path("decision").asText())).append('\n');
            text.append(review.path("comment").asText()).append('\n');
            var simulation = review.path("simulation");
            if (!simulation.path("skip_reason").asText().isBlank()) text.append("Simulation skipped: ").append(simulation.path("skip_reason").asText()).append('\n');
            for (var run : simulation.path("automatic_runs")) text.append("Simulation to ").append(run.path("horizon").asText()).append(": ").append(run.path("status").asText()).append(run.path("error").asText().isBlank() ? "" : " - " + run.path("error").asText()).append("; samples: ").append(run.path("trajectory").path("samples").size()).append('\n');
            for (JsonNode finding : review.path("findings")) {
                text.append("• ").append(label(finding.path("severity").asText())).append(": ").append(finding.path("message").asText());
                if (finding.has("region")) text.append(" (marked image area)");
                text.append('\n');
            }
            text.append('\n');
        }
        if (bundle.files().containsKey("conversation.jsonl")) {
            boolean heading = false;
            for (String line : new String(bundle.files().get("conversation.jsonl"), StandardCharsets.UTF_8).split("\\R")) if (!line.isBlank()) {
                JsonNode message = JSON.readTree(line);
                if (!heading) { text.append("Provided conversation\n"); heading = true; }
                text.append(message.path("role").asText().equals("user") ? "User: " : "Assistant: ").append(message.path("content").asText()).append("\n\n");
            }
        }
        return text.toString();
    }

    private static void section(StringBuilder text, String title, JsonNode values) {
        if (values.isEmpty()) return;
        text.append(title).append('\n'); for (JsonNode value : values) text.append("• ").append(value.asText()).append('\n'); text.append('\n');
    }

    public static String differences(List<TrainingRevisionDiff.Change> changes) {
        if (changes.isEmpty()) return "The saved networks are the same. Comments or images may still differ.";
        StringBuilder text = new StringBuilder();
        java.util.Set<String> lines = new java.util.LinkedHashSet<>();
        for (var change : changes) {
            String subject = change.elementName() == null ? "Network" : "«" + change.elementName() + "»";
            String path = change.path().toLowerCase(java.util.Locale.ROOT);
            String property = switch (change.category()) {
                case "marking" -> "Initial tokens";
                case "label" -> "Name";
                case "layout" -> path.endsWith("/@x") ? "Horizontal position" : path.endsWith("/@y") ? "Vertical position" : path.endsWith("/@width") ? "Width" : path.endsWith("/@height") ? "Height" : "Layout";
                default -> path.contains("delay") || path.contains("timeserv") ? "Service delay" : path.contains("priority") ? "Priority" : path.contains("probability") ? "Probability" : path.contains("distribution") ? "Distribution" : path.contains("inscription") || path.contains("quantity") ? "Arc weight" : "";
            };
            if (!property.isEmpty()) lines.add(subject + " - " + property + ": " + value(change.before()) + " → " + value(change.after()));
            else if (path.endsWith("/@element") && java.util.Set.of("place", "transition", "arc", "page").contains(change.after() == null ? change.before() : change.after())) {
                lines.add(subject + (change.before() == null ? " - added" : " - removed"));
            } else lines.add(subject + " - network properties or connections updated");
        }
        for (String line : lines) text.append("• ").append(line).append("\n\n");
        return text.toString();
    }

    private static String value(String value) {
        if (value == null) return "not set";
        return switch (value) { case "true" -> "Yes"; case "false" -> "No"; case "exp" -> "Exponential"; case "norm" -> "Normal"; case "unif" -> "Uniform"; default -> value; };
    }
}

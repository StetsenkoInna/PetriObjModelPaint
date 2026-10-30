package ua.stetsenkoinna.graphpresentation.training;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** User-supplied context, kept separate from evidence the editor can observe. */
public record TrainingExportRequest(String title, String task, List<String> clarifications,
        List<String> assumptions, List<String> acceptance, String status,
        List<Map<String, Object>> conversation, boolean includeScreenshot) {

    public TrainingExportRequest {
        if (title == null || title.isBlank()) throw new IllegalArgumentException("A title is required");
        title = title.strip();
        status = status == null ? "unreviewed" : status;
        if (!Set.of("unreviewed", "accepted", "rejected").contains(status)) {
            throw new IllegalArgumentException("Invalid review status");
        }
        task = task == null || task.isBlank() ? null : task;
        clarifications = copyStrings(clarifications);
        assumptions = copyStrings(assumptions);
        acceptance = copyStrings(acceptance);
        List<Map<String, Object>> messages = new ArrayList<>();
        for (Map<String, Object> message : conversation == null ? List.<Map<String, Object>>of() : conversation) {
            if (!(message.get("role") instanceof String role) || !Set.of("user", "assistant").contains(role)
                    || !(message.get("content") instanceof String)) {
                throw new IllegalArgumentException("Conversation messages require a user/assistant role and text content");
            }
            if (!Set.of("role", "content", "timestamp").containsAll(message.keySet())) {
                throw new IllegalArgumentException("Unsupported conversation message field");
            }
            Map<String, Object> copy = new LinkedHashMap<>(message);
            if (copy.get("timestamp") == null) copy.remove("timestamp");
            else validateTimestamp(copy.get("timestamp"));
            messages.add(Map.copyOf(copy));
        }
        conversation = List.copyOf(messages);
    }

    private static List<String> copyStrings(List<String> source) {
        if (source == null) return List.of();
        if (source.stream().anyMatch(value -> value == null || value.isBlank())) {
            throw new IllegalArgumentException("Context lists must contain nonempty strings");
        }
        return List.copyOf(source);
    }

    static void validateTimestamp(Object value) {
        if (!(value instanceof String timestamp) || !(timestamp.endsWith("Z") || timestamp.endsWith("+00:00"))) {
            throw new IllegalArgumentException("Timestamps must use UTC");
        }
        Instant.parse(timestamp);
    }
}

package ua.stetsenkoinna.graphpresentation.ai;

import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Reads the pinned shared skills from the application JAR, without a runtime network dependency. */
final class ModelingSkills {
    private static final String ROOT = "/petri-modeling-skills/";
    private static final String URI_PREFIX = "petri://skills/";
    private record Guide(String id, String name, String description, String text) { }
    private Map<String, Guide> guides;

    synchronized List<Map<String, Object>> resources() {
        load();
        List<Map<String, Object>> result = new ArrayList<>();
        guides.values().forEach(guide -> result.add(Map.of("uri", URI_PREFIX + guide.id(),
                "name", guide.name(), "description", guide.description(), "mimeType", "text/markdown")));
        return result;
    }

    synchronized Map<String, Object> read(String uri) {
        load();
        if (!uri.startsWith(URI_PREFIX)) throw new IllegalArgumentException("Unknown skill resource");
        Guide guide = guides.get(uri.substring(URI_PREFIX.length()));
        if (guide == null) throw new IllegalArgumentException("Unknown skill resource");
        return Map.of("contents", List.of(Map.of("uri", uri, "mimeType", "text/markdown", "text", guide.text())));
    }

    List<Map<String, Object>> prompts() {
        return List.of(Map.of("name", "build_petri_model",
                "description", "Plan and build a Petri model using the shared modeling skills and this editor's controls.",
                "arguments", List.of(Map.of("name", "description", "description", "System to model or change requested", "required", true))));
    }

    synchronized Map<String, Object> prompt(String name, JsonNode arguments) {
        if (!"build_petri_model".equals(name)) throw new IllegalArgumentException("Unknown modeling prompt");
        if (!arguments.path("description").isTextual() || arguments.path("description").asText().isBlank()) {
            throw new IllegalArgumentException("description is required");
        }
        load();
        StringBuilder text = new StringBuilder("Modeling request:\n").append(arguments.get("description").asText()).append("\n\n");
        guides.values().forEach(guide -> text.append(guide.text()).append("\n\n"));
        text.append(readText("/ua/stetsenkoinna/graphpresentation/ai/swing-modeling.md"));
        return Map.of("description", "Build a Petri model in the running Swing editor", "messages", List.of(
                Map.of("role", "user", "content", Map.of("type", "text", "text", text.toString()))));
    }

    private void load() {
        if (guides != null) return;
        try {
            JsonNode manifest = DesktopMcpServer.JSON.readTree(readText(ROOT + "manifest.json"));
            Map<String, Guide> loaded = new LinkedHashMap<>();
            for (JsonNode entry : manifest.path("resources")) {
                String id = entry.path("id").asText();
                String path = entry.path("path").asText();
                if (!id.matches("[a-z0-9-]+") || path.contains("..") || path.startsWith("/")) {
                    throw new IllegalStateException("Invalid shared skills manifest");
                }
                String document = readText(ROOT + path).replace("\r\n", "\n");
                String name = entry.path("name").asText(id);
                String description = entry.path("description").asText("Shared Petri modeling knowledge: " + id);
                if (document.startsWith("---\n")) {
                    int end = document.indexOf("\n---\n", 4);
                    if (end < 0) throw new IllegalStateException("Invalid skill frontmatter: " + id);
                    for (String line : document.substring(4, end).split("\n")) {
                        if (line.startsWith("name: ")) name = line.substring(6).trim();
                        if (line.startsWith("description: ")) description = line.substring(13).trim();
                    }
                    document = document.substring(end + 5).strip();
                }
                if (loaded.putIfAbsent(id, new Guide(id, name, description, document)) != null) {
                    throw new IllegalStateException("Duplicate skill ID: " + id);
                }
            }
            if (loaded.isEmpty()) throw new IllegalStateException("Shared skills manifest is empty");
            guides = loaded;
        } catch (IOException ex) {
            throw new IllegalStateException("Cannot read shared modeling skills", ex);
        }
    }

    private static String readText(String resource) {
        try (InputStream input = ModelingSkills.class.getResourceAsStream(resource)) {
            if (input == null) throw new IllegalStateException("Missing modeling resource " + resource
                    + "; rebuild the application to download the pinned shared skills release");
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new IllegalStateException("Cannot read modeling resource " + resource, ex);
        }
    }
}

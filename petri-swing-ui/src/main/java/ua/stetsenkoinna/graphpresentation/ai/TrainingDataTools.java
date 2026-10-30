package ua.stetsenkoinna.graphpresentation.ai;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import ua.stetsenkoinna.graphpresentation.PetriNetsPanel;
import ua.stetsenkoinna.graphpresentation.training.NetworkImageExporter;
import ua.stetsenkoinna.graphpresentation.training.SwingCapture;
import ua.stetsenkoinna.graphpresentation.training.TrainingDataExporter;
import ua.stetsenkoinna.graphpresentation.training.TrainingExportRequest;

public final class TrainingDataTools {
    private final TrainingDataExporter exporter;
    private final Supplier<PetriNetsPanel> canvas;

    public TrainingDataTools(TrainingDataExporter exporter, Supplier<PetriNetsPanel> canvas) {
        this.exporter = exporter;
        this.canvas = canvas;
    }

    @McpTool(name = "export_training_data", description = "Save the current PNML model, network image, geometry and supplied task context as a local training ZIP. "
            + "All nested objects get separate expanded images. Loaded revision/review history and task context are preserved. "
            + "Supply actual new user/assistant conversation messages when available; they append to the loaded transcript. The editor cannot see the external client's private conversation. "
            + "Review status defaults to unreviewed; exporting never marks correctness checks as passed. Screenshot is an optional main-window QA image only.",
            inputSchema = """
                    {"type":"object","properties":{
                      "output_directory":{"type":"string"},"title":{"type":"string"},"task":{"type":"string"},
                      "clarifications":{"type":"array","items":{"type":"string"}},
                      "assumptions":{"type":"array","items":{"type":"string"}},
                      "acceptance":{"type":"array","items":{"type":"string"}},
                      "status":{"type":"string","enum":["unreviewed","accepted","rejected"]},
                      "include_screenshot":{"type":"boolean"},
                      "conversation":{"type":"array","items":{"type":"object","properties":{
                        "role":{"type":"string","enum":["user","assistant"]},"content":{"type":"string"},"timestamp":{"type":"string"}
                      },"required":["role","content"],"additionalProperties":false}}
                    },"required":["output_directory","title"],"additionalProperties":false}
                    """)
    public Object exportTrainingData(JsonNode args) throws Exception {
        Path directory = Path.of(required(args, "output_directory"));
        TrainingExportRequest inherited = exporter.workspace().exportRequest(required(args, "title"), messages(args));
        TrainingExportRequest request = new TrainingExportRequest(required(args, "title"),
                args.has("task") ? args.get("task").asText() : inherited.task(),
                args.has("clarifications") ? strings(args, "clarifications") : inherited.clarifications(),
                args.has("assumptions") ? strings(args, "assumptions") : inherited.assumptions(),
                args.has("acceptance") ? strings(args, "acceptance") : inherited.acceptance(),
                args.path("status").asText("unreviewed"), inherited.conversation(), args.path("include_screenshot").asBoolean(false));
        TrainingDataExporter.ExportResult result = exporter.export(directory, request);
        return text(Map.of("path", result.path().toString(), "sha256", result.sha256(),
                "status", result.status(), "artifact_count", result.artifactCount(), "verification_status", "unknown"));
    }

    @McpTool(name = "export_network_image", description = "Export the active view, complete expanded document, or selected expanded object subtrees as PNG or an image ZIP. "
            + "scope=active preserves visibility; document expands everything; selection exports selected places/transitions/objects with arcs between them; objects uses zero-based object_indices (omitted: all) and optional include_overview. "
            + "Configure include_parameters (append a complete model input table), scale, transparent_background and max_dimension; the renderer caps images at 24 megapixels. Geometry remains unverified. "
            + "Existing files are preserved unless overwrite is explicitly true.", inputSchema = """
                    {"type":"object","properties":{"output_path":{"type":"string"},"overwrite":{"type":"boolean"},
                    "scope":{"type":"string","enum":["active","document","objects","selection"]},"object_indices":{"type":"array","items":{"type":"integer"}},
                    "include_parameters":{"type":"boolean"},"include_overview":{"type":"boolean"},"scale":{"type":"number"},"transparent_background":{"type":"boolean"},"max_dimension":{"type":"integer"}},
                    "required":["output_path"],"additionalProperties":false}
                    """)
    public Object exportNetworkImage(JsonNode args) throws Exception {
        Path target = Path.of(required(args, "output_path")).toAbsolutePath().normalize();
        NetworkImageExporter images = new NetworkImageExporter();
        var options = new NetworkImageExporter.Options(args.path("scale").asDouble(1), args.path("transparent_background").asBoolean(false), args.path("max_dimension").asInt(8192), args.path("include_parameters").asBoolean(false));
        List<Integer> selected = args.has("object_indices") ? DesktopMcpServer.JSON.convertValue(args.get("object_indices"), new TypeReference<List<Integer>>() { }) : null;
        var captured = SwingCapture.call(() -> images.renderConfigured(canvas.get(), args.path("scope").asText("active"), selected, args.path("include_overview").asBoolean(true), options));
        images.writeImages(target, captured, args.path("overwrite").asBoolean(false));
        Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("path", target.toString()); result.put("image_count", captured.size()); result.put("label_status", "unverified");
        if (captured.size() == 1) {
            var image = captured.values().iterator().next();
            Map<?, ?> dimensions = (Map<?, ?>) image.geometry().get("image");
            result.put("width", dimensions.get("width")); result.put("height", dimensions.get("height")); result.put("transform", image.geometry().get("transform"));
        }
        return text(result);
    }

    private static String required(JsonNode args, String key) {
        if (!args.path(key).isTextual() || args.path(key).asText().isBlank()) throw new IllegalArgumentException(key + " is required");
        return args.path(key).asText();
    }

    private static List<String> strings(JsonNode args, String key) {
        return args.has(key) ? DesktopMcpServer.JSON.convertValue(args.get(key), new TypeReference<List<String>>() { }) : List.of();
    }

    private static List<Map<String, Object>> messages(JsonNode args) {
        return args.has("conversation") ? DesktopMcpServer.JSON.convertValue(args.get("conversation"),
                new TypeReference<List<Map<String, Object>>>() { }) : List.of();
    }

    private static Object text(Object value) throws Exception {
        return Map.of("content", List.of(Map.of("type", "text", "text", DesktopMcpServer.JSON.writeValueAsString(value))));
    }
}

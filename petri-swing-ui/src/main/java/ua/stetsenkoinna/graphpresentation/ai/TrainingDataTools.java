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
            + "Supply actual user/assistant conversation messages when available; the editor cannot see the external client's private conversation. "
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
        TrainingExportRequest request = new TrainingExportRequest(required(args, "title"),
                args.has("task") ? args.get("task").asText() : null,
                strings(args, "clarifications"), strings(args, "assumptions"), strings(args, "acceptance"),
                args.path("status").asText("unreviewed"), messages(args), args.path("include_screenshot").asBoolean(false));
        TrainingDataExporter.ExportResult result = exporter.export(directory, request);
        return text(Map.of("path", result.path().toString(), "sha256", result.sha256(),
                "status", result.status(), "artifact_count", result.artifactCount(), "verification_status", "unknown"));
    }

    @McpTool(name = "export_network_image", description = "Save the active canvas as a standalone PNG using the same renderer as training ZIPs. "
            + "No window chrome is included. Existing collapsed/focused visibility is preserved; geometry metadata is unverified. "
            + "Existing files are preserved unless overwrite is explicitly true.", inputSchema = """
                    {"type":"object","properties":{"output_path":{"type":"string"},"overwrite":{"type":"boolean"}},
                    "required":["output_path"],"additionalProperties":false}
                    """)
    public Object exportNetworkImage(JsonNode args) throws Exception {
        Path target = Path.of(required(args, "output_path")).toAbsolutePath().normalize();
        if (!target.getFileName().toString().toLowerCase(java.util.Locale.ROOT).endsWith(".png")) {
            throw new IllegalArgumentException("The image output path must end with .png");
        }
        NetworkImageExporter images = new NetworkImageExporter();
        NetworkImageExporter.RenderedImage image = SwingCapture.call(() -> images.render(canvas.get()));
        images.write(target, image, args.path("overwrite").asBoolean(false));
        Map<?, ?> dimensions = (Map<?, ?>) image.geometry().get("image");
        return text(Map.of("path", target.toString(), "width", dimensions.get("width"), "height", dimensions.get("height"),
                "transform", image.geometry().get("transform"), "label_status", "unverified"));
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

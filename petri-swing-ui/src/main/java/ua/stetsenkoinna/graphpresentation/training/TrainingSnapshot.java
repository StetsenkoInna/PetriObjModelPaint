package ua.stetsenkoinna.graphpresentation.training;

import java.util.List;
import java.util.Map;

public record TrainingSnapshot(String modelPnml, Map<String, Object> observations,
        NetworkImageExporter.RenderedImage networkImage, byte[] screenshot,
        List<Map<String, Object>> toolCalls, String toolCaptureNote,
        List<NetworkImageExporter.NetworkView> networkViews) {
    public TrainingSnapshot {
        if (modelPnml == null || modelPnml.isBlank()) throw new IllegalArgumentException("A current PNML model is required");
        observations = observations == null ? Map.of() : Map.copyOf(observations);
        screenshot = screenshot == null ? null : screenshot.clone();
        toolCalls = toolCalls == null ? List.of() : List.copyOf(toolCalls);
        networkViews = networkViews == null ? List.of() : List.copyOf(networkViews);
        toolCaptureNote = toolCaptureNote == null ? "No tool-call recorder is available" : toolCaptureNote;
    }

    public TrainingSnapshot(String modelPnml, Map<String, Object> observations,
            NetworkImageExporter.RenderedImage networkImage, byte[] screenshot,
            List<Map<String, Object>> toolCalls, String toolCaptureNote) {
        this(modelPnml, observations, networkImage, screenshot, toolCalls, toolCaptureNote, List.of());
    }

    public TrainingSnapshot withToolCalls(List<Map<String, Object>> calls, String note) {
        return new TrainingSnapshot(modelPnml, observations, networkImage, screenshot, calls, note, networkViews);
    }
}

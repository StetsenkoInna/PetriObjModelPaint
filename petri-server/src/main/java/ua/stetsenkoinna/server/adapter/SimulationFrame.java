package ua.stetsenkoinna.server.adapter;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;

/**
 * Single SSE snapshot emitted by the streaming simulation endpoint.
 * Field names match the Python text2pnml SSE format exactly.
 */
public record SimulationFrame(
        @JsonProperty("current_time") double currentTime,
        @JsonProperty("step_number") int stepNumber,
        @JsonProperty("markings") Map<String, Integer> markings,
        @JsonProperty("buffers") Map<String, Integer> buffers,
        @JsonProperty("progress") double progress
) {}

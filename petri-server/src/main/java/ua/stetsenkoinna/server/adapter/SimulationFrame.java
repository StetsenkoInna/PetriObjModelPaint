package ua.stetsenkoinna.server.adapter;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;

/**
 * Single SSE snapshot emitted by the streaming simulation endpoint.
 * Field names follow the standard Petri net simulation SSE format.
 *
 * <p>{@code markings} and {@code buffers} are the state at the snapshot; the two firing fields
 * describe how the model got there from the previous snapshot. Both are always present and
 * never null — an empty list means nothing fired in that interval — so a client can read them
 * without a presence check, and one that ignores them sees the frame it always saw.
 *
 * @param currentTime simulation clock value at this snapshot
 * @param stepNumber count of flushed time steps so far
 * @param markings token count per place, keyed by element id
 * @param buffers active channel count per transition, keyed by element id
 * @param progress {@code currentTime / simulationTime}, clamped to 1
 * @param firedTransitions ids of the transitions that fired since the previous frame, in the
 *        order they first fired
 * @param firingSequence the atomic animation steps of those firings, in order
 */
public record SimulationFrame(
        @JsonProperty("current_time") double currentTime,
        @JsonProperty("step_number") int stepNumber,
        @JsonProperty("markings") Map<String, Integer> markings,
        @JsonProperty("buffers") Map<String, Integer> buffers,
        @JsonProperty("progress") double progress,
        @JsonProperty("fired_transitions") List<String> firedTransitions,
        @JsonProperty("firing_sequence") List<FiringStepDto> firingSequence
) {}

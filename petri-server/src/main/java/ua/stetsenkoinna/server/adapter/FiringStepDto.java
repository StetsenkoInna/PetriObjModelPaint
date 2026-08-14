package ua.stetsenkoinna.server.adapter;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;

/**
 * One atomic animation step of a firing, as it travels inside a {@link SimulationFrame}.
 *
 * <p>The markings and buffers are the whole model's, not the firing object's, and are keyed by
 * element id exactly as the frame's own maps are — a client replays the steps of a frame in
 * order and lands on the state the frame itself reports.
 *
 * @param transitionId id of the transition being fired
 * @param phase one of the four phase names of {@link ua.stetsenkoinna.petriobj.FiringPhase}
 * @param markings token count per place at that instant
 * @param buffers active channel count per transition at that instant
 * @param time simulation clock value at that instant
 */
public record FiringStepDto(
        @JsonProperty("transition_id") String transitionId,
        @JsonProperty("phase") String phase,
        @JsonProperty("markings") Map<String, Integer> markings,
        @JsonProperty("buffers") Map<String, Integer> buffers,
        @JsonProperty("time") double time
) {}

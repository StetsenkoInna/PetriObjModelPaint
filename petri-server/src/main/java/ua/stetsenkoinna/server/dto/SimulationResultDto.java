package ua.stetsenkoinna.server.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record SimulationResultDto(
        @JsonProperty("simulation_time") double simulationTime,
        @JsonProperty("final_time") double finalTime,
        @JsonProperty("total_steps") int totalSteps,
        List<PlaceResultDto> places,
        List<TransitionResultDto> transitions
) {
    public record PlaceResultDto(
            String id,
            String name,
            @JsonProperty("final_marking") int finalMarking,
            @JsonProperty("mean_marking") double meanMarking,
            @JsonProperty("observed_min") int observedMin,
            @JsonProperty("observed_max") int observedMax
    ) {}

    public record TransitionResultDto(
            String id,
            String name,
            @JsonProperty("final_buffer") int finalBuffer,
            @JsonProperty("mean_buffer") double meanBuffer,
            @JsonProperty("observed_min") double observedMin,
            @JsonProperty("observed_max") double observedMax
    ) {}
}

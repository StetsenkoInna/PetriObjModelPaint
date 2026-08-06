package ua.stetsenkoinna.server.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Aggregated statistics of a finished Petri-object model run, grouped per Petri-object.
 *
 * <p>A place shared by two objects through a place fusion is one instance, and it appears
 * under every object whose net reaches it — with the same numbers, since both read the same
 * marking.
 */
public record ObjectModelResultDto(
        @JsonProperty("simulation_time") double simulationTime,
        @JsonProperty("final_time") double finalTime,
        @JsonProperty("total_steps") int totalSteps,
        List<ObjectResultDto> objects
) {
    /**
     * @param index position of the object in the model
     */
    public record ObjectResultDto(
            int index,
            String name,
            int priority,
            List<SimulationResultDto.PlaceResultDto> places,
            List<SimulationResultDto.TransitionResultDto> transitions
    ) {}
}

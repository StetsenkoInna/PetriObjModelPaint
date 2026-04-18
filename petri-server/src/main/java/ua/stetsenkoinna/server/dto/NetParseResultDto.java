package ua.stetsenkoinna.server.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record NetParseResultDto(
        List<PlaceDto> places,
        List<TransitionDto> transitions,
        List<ArcDto> arcs
) {
    public record PlaceDto(
            String id,
            String name,
            @JsonProperty("initial_marking") int initialMarking,
            Double x,
            Double y
    ) {}

    public record TransitionDto(
            String id,
            String name,
            double mean,
            double deviation,
            String distribution,
            int priority,
            double probability,
            Double x,
            Double y
    ) {}

    /**
     * @param type "normal" for regular arcs, "inhibitor" for informational/inhibitor arcs
     */
    public record ArcDto(
            String id,
            String source,
            String target,
            int weight,
            String type
    ) {}
}

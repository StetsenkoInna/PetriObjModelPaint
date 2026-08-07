package ua.stetsenkoinna.server.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * A parsed Petri-object model: the objects it is composed of and the links between them.
 *
 * <p>Objects appear in model order, and so do the places and transitions inside each of
 * them. Those positions are exactly what a link addresses — {@code source_element} of a
 * link into object 2 is an index into that object's {@code places} or {@code transitions}
 * list, depending on the link type.
 */
public record ObjectModelParseResultDto(
        String name,
        List<ObjectDto> objects,
        List<LinkDto> links
) {
    /**
     * @param index position of the object in the model, used to address it from a link and
     *        from a statistic formula ({@code O0.P1})
     * @param template the net library method the object was instantiated from, or null
     */
    public record ObjectDto(
            int index,
            String name,
            int priority,
            Double x,
            Double y,
            String template,
            List<NetParseResultDto.PlaceDto> places,
            List<NetParseResultDto.TransitionDto> transitions,
            List<NetParseResultDto.ArcDto> arcs
    ) {}

    /**
     * @param type {@code placeFusion}, {@code transitionToPlace} or {@code placeToTransition}
     * @param quantity arc multiplicity; always 1 for a place fusion
     * @param informational true for a test arc that does not consume tokens; only meaningful
     *        for {@code placeToTransition}
     */
    public record LinkDto(
            String type,
            @JsonProperty("source_object") int sourceObject,
            @JsonProperty("source_element") int sourceElement,
            @JsonProperty("target_object") int targetObject,
            @JsonProperty("target_element") int targetElement,
            int quantity,
            boolean informational
    ) {}
}

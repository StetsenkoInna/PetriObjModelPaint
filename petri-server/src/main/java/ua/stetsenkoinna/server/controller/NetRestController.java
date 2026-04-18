package ua.stetsenkoinna.server.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ua.stetsenkoinna.PetriObj.ArcIn;
import ua.stetsenkoinna.PetriObj.ArcOut;
import ua.stetsenkoinna.PetriObj.PetriNet;
import ua.stetsenkoinna.PetriObj.PetriP;
import ua.stetsenkoinna.PetriObj.PetriT;
import ua.stetsenkoinna.pnml.ImportResult;
import ua.stetsenkoinna.pnml.PnmlParser;
import ua.stetsenkoinna.server.dto.NetParseResultDto;

import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping(ApiVersions.V1 + "/net")
public class NetRestController {

    /**
     * Parse a PNML document into a structured JSON representation.
     *
     * Returns places (id, name, initial_marking, x, y), transitions
     * (id, name, mean, deviation, distribution, priority, probability, x, y),
     * and arcs (id, source, target, weight, type).
     *
     * Arc types: "normal" for regular arcs, "inhibitor" for informational/inhibitor arcs.
     * Coordinates are null when absent from the PNML document.
     */
    @PostMapping("/parse")
    public ResponseEntity<?> parse(@Valid @RequestBody ParseRequest body) {
        PnmlParser parser = new PnmlParser();
        PetriNet net;
        try {
            net = parser.parseXml(body.netXml());
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
        ImportResult importResult = new ImportResult(net, parser);

        List<NetParseResultDto.PlaceDto> places = new ArrayList<>();
        for (PetriP p : net.getListP()) {
            Point2D.Double coords = importResult.getPlaceCoordinates(p.getNumber());
            places.add(new NetParseResultDto.PlaceDto(
                    p.getId(), p.getName(), p.getMark(),
                    coords != null ? coords.x : null,
                    coords != null ? coords.y : null
            ));
        }

        List<NetParseResultDto.TransitionDto> transitions = new ArrayList<>();
        for (PetriT t : net.getListT()) {
            Point2D.Double coords = importResult.getTransitionCoordinates(t.getNumber());
            transitions.add(new NetParseResultDto.TransitionDto(
                    t.getId(), t.getName(),
                    t.getParameter(), t.getParamDeviation(), t.getDistribution(),
                    t.getPriority(), t.getProbability(),
                    coords != null ? coords.x : null,
                    coords != null ? coords.y : null
            ));
        }

        List<NetParseResultDto.ArcDto> arcs = new ArrayList<>();
        for (ArcIn arc : net.getListIn()) {
            arcs.add(new NetParseResultDto.ArcDto(
                    arc.getId(), arc.getNameP(), arc.getNameT(),
                    arc.getQuantity(),
                    arc.getIsInf() ? "inhibitor" : "normal"
            ));
        }
        for (ArcOut arc : net.getListOut()) {
            arcs.add(new NetParseResultDto.ArcDto(
                    arc.getId(), arc.getNameT(), arc.getNameP(),
                    arc.getQuantity(), "normal"
            ));
        }

        return ResponseEntity.ok(new NetParseResultDto(places, transitions, arcs));
    }

    public record ParseRequest(@NotBlank String netXml) {}
}

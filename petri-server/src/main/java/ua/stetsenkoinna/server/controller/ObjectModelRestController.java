package ua.stetsenkoinna.server.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import ua.stetsenkoinna.api.simulation.SimulationStatus;
import ua.stetsenkoinna.server.adapter.ObjectModelDtos;
import ua.stetsenkoinna.server.adapter.PetriObjModelFactory;
import ua.stetsenkoinna.server.dto.ObjectModelParseResultDto;
import ua.stetsenkoinna.server.dto.ObjectModelResultDto;
import ua.stetsenkoinna.server.service.ObjectModelSimulationService;
import ua.stetsenkoinna.server.service.SimulationSession;
import ua.stetsenkoinna.server.service.SimulationSessionRegistry;
import ua.stetsenkoinna.server.service.SseSimulationService;

import java.util.Map;

/**
 * The Petri-object model API.
 *
 * <p>Where v1 runs a single Petri net, v2 runs a model composed of several Petri-objects
 * linked by shared places and by arcs that cross object boundaries. The document format is
 * the same PNML, with one {@code <page>} per Petri-object; a plain single-net document is
 * accepted too and runs as a model of one object.
 */
@RestController
@RequestMapping(ApiVersions.V2)
public class ObjectModelRestController {

    private final ObjectModelSimulationService simulationService;
    private final SseSimulationService sseSimulationService;
    private final SimulationSessionRegistry registry;

    public ObjectModelRestController(ObjectModelSimulationService simulationService,
                                     SseSimulationService sseSimulationService,
                                     SimulationSessionRegistry registry) {
        this.simulationService = simulationService;
        this.sseSimulationService = sseSimulationService;
        this.registry = registry;
    }

    // ------------------------------------------------------------------ Model inspection

    /**
     * Parses a Petri-object model document into JSON: the objects with their nets, and the
     * links between them.
     *
     * <p>Each object's places and transitions come back in the order that indexes them, so
     * a link's {@code source_element} / {@code target_element} can be resolved by position.
     * Element coordinates are the ones the object's own drawing uses; where the object sits
     * in the model is its own {@code x} / {@code y}.
     */
    @PostMapping("/model/parse")
    public ResponseEntity<?> parse(@Valid @RequestBody ModelRequest body) {
        try {
            return ResponseEntity.ok(ObjectModelDtos.of(PetriObjModelFactory.parse(body.modelXml())));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", String.valueOf(e.getMessage())));
        }
    }

    // ------------------------------------------------------------------ WebSocket flow

    /**
     * Starts a run and returns its session id.
     *
     * <p>Steps are published to {@code /topic/v2/sim/{id}/steps} and status changes to
     * {@code /topic/v2/sim/{id}/status}; control commands go to
     * {@code /app/v2/sim/{id}/control}.
     */
    @PostMapping("/simulation/start")
    public ResponseEntity<?> start(@Valid @RequestBody StartRequest body) {
        try {
            // Fail fast on a document the model reader cannot make sense of, so the caller
            // gets 400 instead of a session that dies on its own thread.
            PetriObjModelFactory.parse(body.modelXml());
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", String.valueOf(e.getMessage())));
        }
        String sessionId = simulationService.start(body.modelXml(), body.simulationTime());
        return ResponseEntity.ok(Map.of("sessionId", sessionId));
    }

    @PostMapping("/simulation/{id}/stop")
    public ResponseEntity<Void> stop(@PathVariable String id) {
        simulationService.stop(id);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/simulation/{id}/pause")
    public ResponseEntity<Void> pause(@PathVariable String id) {
        simulationService.pause(id);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/simulation/{id}/resume")
    public ResponseEntity<Void> resume(@PathVariable String id) {
        simulationService.resume(id);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/simulation/{id}/status")
    public ResponseEntity<Map<String, String>> status(@PathVariable String id) {
        SimulationStatus status = simulationService.getStatus(id);
        if (status == SimulationStatus.NOT_FOUND) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(Map.of("status", status.name()));
    }

    /**
     * Returns the run's statistics grouped per Petri-object.
     *
     * <p>404 — no such session. 202 — the session exists but has not finished yet.
     */
    @GetMapping("/simulation/{id}/result")
    public ResponseEntity<ObjectModelResultDto> result(@PathVariable String id) {
        SimulationSession session = registry.get(id);
        if (session == null) {
            return ResponseEntity.notFound().build();
        }
        ObjectModelResultDto result = session.getObjectModelResult();
        if (result == null) {
            return ResponseEntity.accepted().build();
        }
        return ResponseEntity.ok(result);
    }

    // ------------------------------------------------------------------ SSE streaming flow

    /**
     * Streams simulation snapshots of a Petri-object model as Server-Sent Events.
     *
     * <p>Frames have the same shape as in v1 — {@code markings} and {@code buffers} keyed by
     * element id, which stays unique across the objects of a model. Which object an element
     * belongs to comes from {@code /model/parse}; the aggregated per-object statistics come
     * from {@code /simulation/{id}/result} once the stream is done.
     *
     * @param simulationTime total simulation time units
     * @param timeStep time-based snapshot interval
     * @param snapshotInterval step-based snapshot interval, overrides {@code timeStep}
     * @param animationDelayMs real-time pause after each snapshot in ms (0 = full speed)
     * @param body PNML document of the model to simulate
     */
    @PostMapping(value = "/simulation/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(
            @RequestParam(defaultValue = "3600.0") double simulationTime,
            @RequestParam(defaultValue = "1.0") double timeStep,
            @RequestParam(required = false) Integer snapshotInterval,
            @RequestParam(defaultValue = "0") long animationDelayMs,
            @Valid @RequestBody ModelRequest body
    ) {
        SseSimulationService.StreamParams params = new SseSimulationService.StreamParams(
                simulationTime, timeStep, snapshotInterval, animationDelayMs
        );
        return sseSimulationService.streamObjectModel(body.modelXml(), params);
    }

    // ------------------------------------------------------------------ Request records

    public record ModelRequest(@NotBlank String modelXml) {}

    public record StartRequest(
            @NotBlank String modelXml,
            @Positive double simulationTime
    ) {}
}

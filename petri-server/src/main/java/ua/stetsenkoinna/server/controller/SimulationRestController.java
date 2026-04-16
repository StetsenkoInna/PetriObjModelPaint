package ua.stetsenkoinna.server.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import ua.stetsenkoinna.api.simulation.SimulationRequest;
import ua.stetsenkoinna.api.simulation.SimulationService;
import ua.stetsenkoinna.api.simulation.SimulationStatus;
import ua.stetsenkoinna.server.service.SseSimulationService;

import java.util.Map;

@RestController
@RequestMapping(ApiVersions.V1 + "/simulation")
public class SimulationRestController {

    private final SimulationService simulationService;
    private final SseSimulationService sseSimulationService;

    public SimulationRestController(SimulationService simulationService,
                                    SseSimulationService sseSimulationService) {
        this.simulationService = simulationService;
        this.sseSimulationService = sseSimulationService;
    }

    // ------------------------------------------------------------------ WebSocket flow

    @PostMapping("/start")
    public ResponseEntity<Map<String, String>> start(@Valid @RequestBody StartRequest body) {
        String sessionId = simulationService.startSimulation(
                new SimulationRequest(body.netXml(), body.simulationTime(), 1)
        );
        return ResponseEntity.ok(Map.of("sessionId", sessionId));
    }

    @PostMapping("/{id}/stop")
    public ResponseEntity<Void> stop(@PathVariable String id) {
        simulationService.stop(id);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{id}/pause")
    public ResponseEntity<Void> pause(@PathVariable String id) {
        simulationService.pause(id);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{id}/resume")
    public ResponseEntity<Void> resume(@PathVariable String id) {
        simulationService.resume(id);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/{id}/status")
    public ResponseEntity<Map<String, String>> status(@PathVariable String id) {
        SimulationStatus status = simulationService.getStatus(id);
        if (status == SimulationStatus.NOT_FOUND) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(Map.of("status", status.name()));
    }

    // ------------------------------------------------------------------ SSE streaming flow

    /**
     * Stream simulation snapshots as Server-Sent Events.
     *
     * <p>Events shape: {@code {"current_time":…, "step_number":…, "markings":{id:tokens},
     * "buffers":{id:count}, "progress":…}}. Stream terminates with {@code data: [DONE]}.
     *
     * <p>First event (name=session) carries the session ID so the client can call
     * pause/resume/stop via the REST endpoints above.
     *
     * <p><b>Snapshot modes</b>
     * <ul>
     *   <li>Time-based (default): one snapshot every {@code timeStep} simulation units.
     *   <li>Step-based: one snapshot every {@code snapshotInterval} transition firings
     *       (overrides {@code timeStep} when set).
     * </ul>
     *
     * @param simulationTime   total simulation time units
     * @param timeStep         time-based snapshot interval (default 1.0)
     * @param snapshotInterval step-based snapshot interval (optional, overrides timeStep)
     * @param animationDelayMs real-time pause after each snapshot in ms (0 = full speed)
     * @param body             PNML XML of the net to simulate
     */
    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(
            @RequestParam(defaultValue = "3600.0") double simulationTime,
            @RequestParam(defaultValue = "1.0") double timeStep,
            @RequestParam(required = false) Integer snapshotInterval,
            @RequestParam(defaultValue = "0") long animationDelayMs,
            @Valid @RequestBody StreamRequest body
    ) {
        SseSimulationService.StreamParams params = new SseSimulationService.StreamParams(
                simulationTime, timeStep, snapshotInterval, animationDelayMs
        );
        return sseSimulationService.stream(body.netXml(), params);
    }

    // ------------------------------------------------------------------ Request records

    public record StartRequest(
            @NotBlank String netXml,
            @Positive double simulationTime
    ) {}

    public record StreamRequest(
            @NotBlank String netXml
    ) {}
}

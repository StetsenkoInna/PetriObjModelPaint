package ua.stetsenkoinna.server.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ua.stetsenkoinna.api.simulation.SimulationRequest;
import ua.stetsenkoinna.api.simulation.SimulationService;
import ua.stetsenkoinna.api.simulation.SimulationStatus;

import java.util.Map;

@RestController
@RequestMapping("/api/simulation")
public class SimulationRestController {

    private final SimulationService simulationService;

    public SimulationRestController(SimulationService simulationService) {
        this.simulationService = simulationService;
    }

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

    public record StartRequest(
            @NotBlank String netXml,
            @Positive double simulationTime
    ) {}
}

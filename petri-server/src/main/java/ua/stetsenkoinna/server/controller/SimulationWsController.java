package ua.stetsenkoinna.server.controller;

import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.stereotype.Controller;
import ua.stetsenkoinna.api.simulation.SimulationService;

import static ua.stetsenkoinna.server.controller.ApiVersions.WS_V1;

@Controller
public class SimulationWsController {

    private final SimulationService simulationService;

    public SimulationWsController(SimulationService simulationService) {
        this.simulationService = simulationService;
    }

    @MessageMapping(WS_V1 + "/sim/{id}/control")
    public void control(@DestinationVariable String id, String command) {
        switch (command.trim().toUpperCase()) {
            case "PAUSE"  -> simulationService.pause(id);
            case "RESUME" -> simulationService.resume(id);
            case "STOP"   -> simulationService.stop(id);
        }
    }
}

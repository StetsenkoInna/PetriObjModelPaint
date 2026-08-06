package ua.stetsenkoinna.server.controller;

import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.stereotype.Controller;
import ua.stetsenkoinna.server.service.ObjectModelSimulationService;

import static ua.stetsenkoinna.server.controller.ApiVersions.WS_V2;

/**
 * Control commands for a running Petri-object model simulation, sent to
 * {@code /app/v2/sim/{id}/control} as one of {@code PAUSE}, {@code RESUME}, {@code STOP}.
 */
@Controller
public class ObjectModelWsController {

    private final ObjectModelSimulationService simulationService;

    public ObjectModelWsController(ObjectModelSimulationService simulationService) {
        this.simulationService = simulationService;
    }

    @MessageMapping(WS_V2 + "/sim/{id}/control")
    public void control(@DestinationVariable String id, String command) {
        switch (command.trim().toUpperCase()) {
            case "PAUSE"  -> simulationService.pause(id);
            case "RESUME" -> simulationService.resume(id);
            case "STOP"   -> simulationService.stop(id);
        }
    }
}

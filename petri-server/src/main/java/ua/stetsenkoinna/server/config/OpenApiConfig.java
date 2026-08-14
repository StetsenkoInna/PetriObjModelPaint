package ua.stetsenkoinna.server.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI petriOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("petri-net-sim API")
                        .version("2.0.0")
                        .description("""
                                REST API for Petri net simulation. \
                                /api/v1 runs a single Petri net; /api/v2 runs a Petri-object \
                                model composed of several linked Petri-objects and reports \
                                statistics per object. WebSocket (STOMP/SockJS) endpoint: \
                                ws://localhost:8080/ws"""));
    }
}

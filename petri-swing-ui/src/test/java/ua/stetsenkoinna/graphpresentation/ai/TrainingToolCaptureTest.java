package ua.stetsenkoinna.graphpresentation.ai;

import com.fasterxml.jackson.databind.JsonNode;
import java.net.URI;
import java.net.http.*;
import java.util.Map;
import org.junit.Test;
import static org.junit.Assert.*;

public class TrainingToolCaptureTest {
    public static final class Actions {
        @McpTool(name="ui_action", description="Queue a test action", inputSchema="""
                {"type":"object","properties":{"text":{"type":"string"}},"required":["text"]}
                """)
        public Object action(JsonNode args) { return Map.of("status", "queued", "text", args.path("text").asText()); }
    }

    @Test public void capturesRealRequestsWithBoundedHistoryAndRedactedConnectionToken() throws Exception {
        try (DesktopMcpServer server = new DesktopMcpServer(new Actions()); HttpClient client = HttpClient.newHttpClient()) {
            server.start(0);
            for (int i = 0; i < 201; i++) {
                String payload = DesktopMcpServer.JSON.writeValueAsString(Map.of("jsonrpc", "2.0", "id", i,
                        "method", "tools/call", "params", Map.of("name", "ui_action",
                                "arguments", Map.of("text", server.token()))));
                HttpRequest request = HttpRequest.newBuilder(URI.create(server.endpoint()))
                        .header("Authorization", "Bearer " + server.token())
                        .POST(HttpRequest.BodyPublishers.ofString(payload)).build();
                assertEquals(200, client.send(request, HttpResponse.BodyHandlers.discarding()).statusCode());
            }
            assertEquals(200, server.trainingToolCalls().size());
            String history = DesktopMcpServer.JSON.writeValueAsString(server.trainingToolCalls());
            assertFalse(history.contains(server.token()));
            assertTrue(history.contains("[REDACTED]"));
            assertTrue(server.trainingToolCaptureNote().contains("Older calls were discarded"));
            server.trainingToolCalls().clear();
            assertEquals(200, server.trainingToolCalls().size());
        }
    }
}

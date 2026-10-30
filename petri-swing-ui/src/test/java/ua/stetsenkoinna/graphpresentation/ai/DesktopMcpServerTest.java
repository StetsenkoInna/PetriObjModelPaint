package ua.stetsenkoinna.graphpresentation.ai;

import com.fasterxml.jackson.databind.JsonNode;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

public class DesktopMcpServerTest {
    public static class Counter {
        final AtomicInteger value = new AtomicInteger();

        @McpTool(name = "increment", description = "Increment the service counter", inputSchema = """
                {"type":"object","properties":{"amount":{"type":"integer"}},"required":["amount"],"additionalProperties":false}
                """)
        public Object increment(JsonNode args) {
            return Map.of("content", List.of(Map.of("type", "text", "text", "" + value.addAndGet(args.get("amount").asInt()))));
        }
    }

    private final Counter counter = new Counter();
    private final DesktopMcpServer server = new DesktopMcpServer(counter);
    private final HttpClient client = HttpClient.newHttpClient();

    @Before public void start() throws Exception { server.start(0); }
    @After public void stop() { server.close(); client.close(); }

    private HttpResponse<String> post(String body, String token, String origin) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(server.endpoint()))
                .header("Content-Type", "application/json").header("Accept", "application/json, text/event-stream");
        if (token != null) request.header("Authorization", "Bearer " + token);
        if (origin != null) request.header("Origin", origin);
        return client.send(request.POST(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
    }

    private JsonNode rpc(String method, Object params) throws Exception {
        String body = DesktopMcpServer.JSON.writeValueAsString(Map.of("jsonrpc", "2.0", "id", 7, "method", method, "params", params));
        HttpResponse<String> response = post(body, server.token(), null);
        assertEquals(200, response.statusCode());
        return DesktopMcpServer.JSON.readTree(response.body());
    }

    @Test public void initializeAndNotificationUseMcpProtocol() throws Exception {
        JsonNode reply = rpc("initialize", Map.of("protocolVersion", "2025-03-26"));
        assertEquals("2025-03-26", reply.at("/result/protocolVersion").asText());
        assertTrue(reply.at("/result/capabilities").has("tools"));
        assertEquals(7, reply.get("id").asInt());
        assertEquals(202, post("{\"jsonrpc\":\"2.0\",\"method\":\"notifications/initialized\"}", server.token(), null).statusCode());
    }

    @Test public void annotationDrivesDiscoveryAndExecutionOfSameService() throws Exception {
        JsonNode tool = rpc("tools/list", Map.of()).at("/result/tools/0");
        assertEquals("increment", tool.get("name").asText());
        assertEquals("amount", tool.at("/inputSchema/required/0").asText());
        JsonNode reply = rpc("tools/call", Map.of("name", "increment", "arguments", Map.of("amount", 3)));
        assertEquals("3", reply.at("/result/content/0/text").asText());
        assertEquals(3, counter.value.get());
    }

    @Test public void schemaRejectsBadArgumentsBeforeCallingService() throws Exception {
        for (Object args : List.of(Map.of(), Map.of("amount", "3"), Map.of("amount", 1, "extra", 2))) {
            JsonNode reply = rpc("tools/call", Map.of("name", "increment", "arguments", args));
            assertTrue(reply.at("/result/isError").asBoolean());
        }
        assertEquals(0, counter.value.get());
    }

    @Test public void unauthenticatedAndBrowserRequestsCannotInvokeTools() throws Exception {
        assertEquals(401, post("{}", null, null).statusCode());
        assertEquals(401, post("{}", "wrong", null).statusCode());
        assertEquals(403, post("{}", server.token(), "https://example.org").statusCode());
        assertEquals(0, counter.value.get());
    }

    @Test public void parseAndMethodErrorsAreJsonRpcErrors() throws Exception {
        assertEquals(-32700, DesktopMcpServer.JSON.readTree(post("{", server.token(), null).body()).at("/error/code").asInt());
        assertEquals(-32600, DesktopMcpServer.JSON.readTree(post("[]", server.token(), null).body()).at("/error/code").asInt());
        assertEquals(-32601, rpc("unknown", Map.of()).at("/error/code").asInt());
    }

    @Test public void restartingRotatesCredentialsAndStopsOldListener() throws Exception {
        String oldToken = server.token();
        server.close();
        assertFalse(server.isRunning());
        server.start(0);
        assertNotEquals(oldToken, server.token());
        assertEquals(401, post("{}", oldToken, null).statusCode());
    }

    @Test public void batchesReturnOnlyRequestResponses() throws Exception {
        HttpResponse<String> response = post("""
                [{"jsonrpc":"2.0","method":"notifications/initialized"},
                 {"jsonrpc":"2.0","id":1,"method":"ping"},
                 {"jsonrpc":"2.0","id":2,"method":"tools/list"}]
                """, server.token(), null);
        JsonNode replies = DesktopMcpServer.JSON.readTree(response.body());
        assertEquals(200, response.statusCode());
        assertEquals(2, replies.size());
        assertEquals(1, replies.get(0).get("id").asInt());
        assertEquals("increment", replies.get(1).at("/result/tools/0/name").asText());
    }

    @Test public void stdioBridgeForwardsRequestsWithoutExtraStdout() throws Exception {
        ProcessBuilder builder = new ProcessBuilder(
                java.nio.file.Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                "-cp", System.getProperty("java.class.path"), DesktopMcpStdioBridge.class.getName(), server.endpoint());
        builder.environment().put("PETRI_MCP_TOKEN", server.token());
        Process process = builder.start();
        try {
            process.getOutputStream().write(("{\"jsonrpc\":\"2.0\",\"method\":\"notifications/initialized\"}\n"
                    + "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/list\"}\n").getBytes(java.nio.charset.StandardCharsets.UTF_8));
            process.getOutputStream().close();
            assertTrue(process.waitFor(10, java.util.concurrent.TimeUnit.SECONDS));
            String output = new String(process.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            assertEquals(0, process.exitValue());
            assertEquals(1, output.lines().count());
            assertEquals("increment", DesktopMcpServer.JSON.readTree(output).at("/result/tools/0/name").asText());
        } finally {
            process.destroyForcibly();
        }
    }
}

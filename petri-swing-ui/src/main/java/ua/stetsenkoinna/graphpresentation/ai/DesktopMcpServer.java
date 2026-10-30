package ua.stetsenkoinna.graphpresentation.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** A loopback-only, stateless Streamable HTTP MCP endpoint for the running editor. */
public final class DesktopMcpServer implements AutoCloseable {
    static final ObjectMapper JSON = new ObjectMapper();
    private static final int MAX_BODY = 1_048_576;
    private final McpToolRegistry tools;
    private final ModelingSkills skills = new ModelingSkills();
    private HttpServer server;
    private ExecutorService executor;
    private String token;

    public DesktopMcpServer(Object... services) {
        this.tools = new McpToolRegistry(services);
    }

    public synchronized void start(int port) throws IOException {
        if (server != null) {
            return;
        }
        byte[] secret = new byte[32];
        new SecureRandom().nextBytes(secret);
        token = Base64.getUrlEncoder().withoutPadding().encodeToString(secret);
        HttpServer candidate = HttpServer.create(new InetSocketAddress("127.0.0.1", port), 0);
        executor = Executors.newFixedThreadPool(4, runnable -> {
            Thread thread = new Thread(runnable, "desktop-mcp");
            thread.setDaemon(true);
            return thread;
        });
        candidate.setExecutor(executor);
        candidate.createContext("/mcp", this::handle);
        candidate.start();
        server = candidate;
    }

    public synchronized boolean isRunning() {
        return server != null;
    }

    public synchronized String endpoint() {
        return server == null ? "" : "http://127.0.0.1:" + server.getAddress().getPort() + "/mcp";
    }

    public synchronized String token() {
        return token;
    }

    public List<String> toolNames() {
        return tools.list().stream().map(tool -> (String) tool.get("name")).toList();
    }

    @Override
    public synchronized void close() {
        if (server != null) {
            server.stop(0);
            server = null;
            executor.shutdownNow();
            executor = null;
            token = null;
        }
    }

    private void handle(HttpExchange exchange) throws IOException {
        try (exchange) {
            if (!"/mcp".equals(exchange.getRequestURI().getPath())) {
                send(exchange, 404, null);
                return;
            }
            // Native clients send no Origin. Browser-originated requests are not accepted.
            if (exchange.getRequestHeaders().getFirst("Origin") != null) {
                send(exchange, 403, null);
                return;
            }
            String authorization = exchange.getRequestHeaders().getFirst("Authorization");
            String expected = "Bearer " + token();
            if (authorization == null || !MessageDigest.isEqual(
                    expected.getBytes(StandardCharsets.UTF_8), authorization.getBytes(StandardCharsets.UTF_8))) {
                send(exchange, 401, null);
                return;
            }
            if (!"POST".equals(exchange.getRequestMethod())) {
                exchange.getResponseHeaders().set("Allow", "POST");
                send(exchange, 405, null);
                return;
            }
            byte[] bytes = exchange.getRequestBody().readNBytes(MAX_BODY + 1);
            if (bytes.length > MAX_BODY) {
                send(exchange, 413, null);
                return;
            }
            JsonNode request;
            try {
                request = JSON.readTree(bytes);
            } catch (IOException ex) {
                send(exchange, 200, error(null, -32700, "Parse error"));
                return;
            }
            if (request != null && request.isArray() && !request.isEmpty()) {
                java.util.ArrayList<Object> responses = new java.util.ArrayList<>();
                for (JsonNode message : request) {
                    Object response = dispatch(message);
                    if (response != null) responses.add(response);
                }
                send(exchange, responses.isEmpty() ? 202 : 200, responses.isEmpty() ? null : responses);
            } else {
                Object response = dispatch(request);
                send(exchange, response == null ? 202 : 200, response);
            }
        }
    }

    private Object dispatch(JsonNode request) {
        if (request == null || !request.isObject() || !"2.0".equals(request.path("jsonrpc").asText())
                || !request.path("method").isTextual()) {
            return error(null, -32600, "Invalid Request");
        }
        if (!request.has("id")) {
            return null;
        }
        Object id = JSON.convertValue(request.get("id"), Object.class);
        Object result;
        switch (request.path("method").asText()) {
            case "initialize" -> result = Map.of(
                    "protocolVersion", "2025-03-26",
                    "capabilities", Map.of("tools", Map.of(), "resources", Map.of(), "prompts", Map.of()),
                    "serverInfo", Map.of("name", "PetriObjModelPaint Desktop", "version", "1.0"),
                    "instructions", "Control the running Swing editor. For model design read the shared resources at "
                            + "petri://skills/petri-modeling, petri://skills/petri-patterns and petri://skills/pnml-standard, "
                            + "or use the build_petri_model prompt. Inspect UI before actions; use returned component IDs. "
                            + "Actions are queued on the UI thread; inspect again to observe completion and dialogs. "
                            + "Coordinates are relative to the chosen component. File choosers may be native OS windows.");
            case "ping" -> result = Map.of();
            case "tools/list" -> result = Map.of("tools", tools.list());
            case "resources/list" -> result = Map.of("resources", skills.resources());
            case "prompts/list" -> result = Map.of("prompts", skills.prompts());
            case "resources/read", "prompts/get" -> {
                JsonNode params = request.path("params");
                try {
                    result = "resources/read".equals(request.path("method").asText())
                            ? skills.read(params.path("uri").asText())
                            : skills.prompt(params.path("name").asText(), params.path("arguments"));
                } catch (IllegalArgumentException ex) {
                    return error(id, -32602, ex.getMessage());
                }
            }
            case "tools/call" -> {
                JsonNode params = request.path("params");
                try {
                    result = tools.call(params.path("name").asText(), params.path("arguments"));
                } catch (Exception ex) {
                    result = Map.of("isError", true, "content", List.of(Map.of(
                            "type", "text", "text", ex.getMessage() == null ? ex.toString() : ex.getMessage())));
                }
            }
            default -> {
                return error(id, -32601, "Method not found");
            }
        }
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("jsonrpc", "2.0");
        response.put("id", id);
        response.put("result", result);
        return response;
    }

    private static Map<String, Object> error(Object id, int code, String message) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("jsonrpc", "2.0");
        response.put("id", id);
        response.put("error", Map.of("code", code, "message", message));
        return response;
    }

    private static void send(HttpExchange exchange, int status, Object body) throws IOException {
        if (body == null) {
            exchange.sendResponseHeaders(status, -1);
            return;
        }
        byte[] bytes = JSON.writeValueAsBytes(body);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
    }
}

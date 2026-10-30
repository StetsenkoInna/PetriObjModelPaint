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
    private final java.util.ArrayDeque<Map<String, Object>> trainingCalls = new java.util.ArrayDeque<>();
    private int trainingCallBytes;
    private boolean trainingCallsTruncated;

    public DesktopMcpServer(Object... services) {
        this.tools = new McpToolRegistry(services);
    }

    public synchronized void start(int port) throws IOException {
        start(port, null, false);
    }

    public synchronized void start(int port, String savedToken) throws IOException {
        start(port, savedToken, true);
    }

    public synchronized void start(int port, String savedToken, boolean requireAuthentication) throws IOException {
        if (server != null) {
            return;
        }
        if (requireAuthentication && savedToken != null && !ua.stetsenkoinna.config.AppSettings.isValidMcpToken(savedToken)) {
            throw new IllegalArgumentException("Saved MCP token is invalid");
        }
        HttpServer candidate = HttpServer.create(new InetSocketAddress("127.0.0.1", port), 0);
        if (!requireAuthentication) {
            token = null;
        } else if (savedToken == null) {
            byte[] secret = new byte[32];
            new SecureRandom().nextBytes(secret);
            token = Base64.getUrlEncoder().withoutPadding().encodeToString(secret);
        } else {
            token = savedToken;
        }
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

    public synchronized boolean isAuthenticationRequired() {
        return token != null;
    }

    public List<String> toolNames() {
        return tools.list().stream().map(tool -> (String) tool.get("name")).toList();
    }

    public synchronized List<Map<String, Object>> trainingToolCalls() {
        return JSON.convertValue(List.copyOf(trainingCalls), new com.fasterxml.jackson.core.type.TypeReference<>() { });
    }

    public synchronized void clearTrainingToolCalls() {
        trainingCalls.clear(); trainingCallBytes = 0; trainingCallsTruncated = false;
    }

    public synchronized String trainingToolCaptureNote() {
        return "Actual ui_action and training-bundle open/review/image/comparison/export requests since this document was opened are recorded. "
                + "Queued results do not prove action completion. Image responses store hashes instead of image bytes; general UI inspection and screenshots are excluded. "
                + "A snapshot precedes its own export response; that call can appear in the next snapshot. Direct mouse/keyboard editing is not an action-by-action recorder. "
                + "The editor cannot observe the client's private conversation or complete session history. "
                + (trainingCallsTruncated ? "Older calls were discarded at the 200-call/1 MiB limit." : "No recorder limit was reached.");
    }

    private synchronized void recordTrainingCall(String tool, JsonNode arguments, Object result) {
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("tool", tool);
        String redacted = arguments.toString();
        if (token != null) redacted = redacted.replace(token, "[REDACTED]");
        try {
            entry.put("arguments", JSON.readValue(redacted, Map.class));
            Object recorded = result;
            if ("training_bundle_image".equals(tool)) {
                JsonNode payload = JSON.valueToTree(result);
                if ("image".equals(payload.at("/content/0/type").asText())) {
                    byte[] png = Base64.getDecoder().decode(payload.at("/content/0/data").asText());
                    recorded = Map.of("image_path", arguments.path("image_path").asText(), "mime_type", "image/png", "byte_count", png.length,
                            "sha256", java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(png)));
                }
            }
            String resultJson = JSON.writeValueAsString(recorded);
            if (token != null) resultJson = resultJson.replace(token, "[REDACTED]");
            entry.put("result", JSON.readValue(resultJson, Object.class));
            entry.put("timestamp", java.time.Instant.now().toString());
            entry.put("origin", "application");
            int bytes = JSON.writeValueAsBytes(entry).length;
            if (bytes > 1_048_576) { trainingCallsTruncated = true; return; }
            trainingCalls.addLast(entry);
            trainingCallBytes += bytes;
            while (trainingCalls.size() > 200 || trainingCallBytes > 1_048_576) {
                trainingCallBytes -= JSON.writeValueAsBytes(trainingCalls.removeFirst()).length;
                trainingCallsTruncated = true;
            }
        } catch (Exception ex) {
            trainingCallsTruncated = true;
        }
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
            String currentToken = token();
            String expected = "Bearer " + currentToken;
            if (currentToken != null && (authorization == null || !MessageDigest.isEqual(
                    expected.getBytes(StandardCharsets.UTF_8), authorization.getBytes(StandardCharsets.UTF_8)))) {
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
                            + "or use the build_petri_model prompt. For ZIP review use review_petri_model or read petri://desktop/training-review. Inspect UI before actions; use returned component IDs. "
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
                String tool = params.path("name").asText();
                if (java.util.Set.of("ui_action", "open_training_bundle", "inspect_training_bundle", "training_bundle_image", "compare_training_revisions", "export_training_review", "export_training_data", "export_network_image").contains(tool)
                        && params.path("arguments").isObject()) {
                    recordTrainingCall(tool, params.path("arguments"), result);
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

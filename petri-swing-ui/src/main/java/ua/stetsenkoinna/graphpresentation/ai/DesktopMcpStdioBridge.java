package ua.stetsenkoinna.graphpresentation.ai;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/** Connects stdio-only clients to the already running desktop server. Uses only JDK classes. */
public final class DesktopMcpStdioBridge {
    private DesktopMcpStdioBridge() { }

    public static void main(String[] args) throws Exception {
        if (args.length != 1) throw new IllegalArgumentException("Expected the desktop MCP URL");
        URI endpoint = URI.create(args[0]);
        if (!"http".equals(endpoint.getScheme()) || !"127.0.0.1".equals(endpoint.getHost())
                || !"/mcp".equals(endpoint.getPath())) throw new IllegalArgumentException("Expected a local desktop MCP URL");
        String token = System.getenv("PETRI_MCP_TOKEN");
        try (HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
                BufferedReader input = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = input.readLine()) != null) {
                if (line.isBlank()) continue;
                HttpRequest.Builder request = HttpRequest.newBuilder(endpoint)
                        .header("Content-Type", "application/json")
                        .header("Accept", "application/json, text/event-stream")
                        .timeout(Duration.ofSeconds(30))
                        .POST(HttpRequest.BodyPublishers.ofString(line, StandardCharsets.UTF_8));
                if (token != null && !token.isBlank()) request.header("Authorization", "Bearer " + token);
                HttpResponse<String> response = client.send(request.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
                if (response.statusCode() != 200 && response.statusCode() != 202) {
                    throw new IllegalStateException("Desktop MCP returned HTTP " + response.statusCode());
                }
                if (response.statusCode() == 200) {
                    System.out.println(response.body());
                    System.out.flush();
                }
            }
        }
    }
}

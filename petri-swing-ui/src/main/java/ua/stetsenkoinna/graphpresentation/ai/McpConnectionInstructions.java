package ua.stetsenkoinna.graphpresentation.ai;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

final class McpConnectionInstructions {
    private McpConnectionInstructions() { }

    static String codex(String url, String token) {
        return "[mcp_servers.petriDesktop]\nurl = \"" + url + "\"\n"
                + "http_headers = { Authorization = \"Bearer " + token + "\" }";
    }

    static String claudeCode(String url, String token) {
        return "claude mcp add --transport http petriDesktop " + url
                + " --header \"Authorization: Bearer " + token + "\"";
    }

    static String claudeDesktop(String url, String token) {
        try {
            String javaName = System.getProperty("os.name").toLowerCase(java.util.Locale.ROOT).contains("win") ? "java.exe" : "java";
            String java = Path.of(System.getProperty("java.home"), "bin", javaName).toString();
            String classPath = Path.of(DesktopMcpStdioBridge.class.getProtectionDomain().getCodeSource().getLocation().toURI()).toString();
            Object config = Map.of("mcpServers", Map.of("petriDesktop", Map.of(
                    "command", java,
                    "args", List.of("-cp", classPath, DesktopMcpStdioBridge.class.getName(), url),
                    "env", Map.of("PETRI_MCP_TOKEN", token))));
            return DesktopMcpServer.JSON.writerWithDefaultPrettyPrinter().writeValueAsString(config);
        } catch (Exception ex) {
            throw new IllegalStateException("Cannot create Claude Desktop configuration", ex);
        }
    }
}

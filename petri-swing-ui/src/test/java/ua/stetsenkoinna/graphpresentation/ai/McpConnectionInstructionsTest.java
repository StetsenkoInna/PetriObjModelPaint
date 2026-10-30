package ua.stetsenkoinna.graphpresentation.ai;

import org.junit.Test;
import static org.junit.Assert.*;

public class McpConnectionInstructionsTest {
    @Test public void setupsOmitCredentialsWhenAuthenticationIsOff() throws Exception {
        String url = "http://127.0.0.1:8765/mcp";
        assertFalse(McpConnectionInstructions.codex(url, null).contains("http_headers"));
        assertFalse(McpConnectionInstructions.claudeCode(url, null).contains("--header"));
        var desktop = DesktopMcpServer.JSON.readTree(McpConnectionInstructions.claudeDesktop(url, null));
        assertTrue(desktop.at("/mcpServers/petriDesktop/env").isEmpty());
        assertEquals(url, desktop.at("/mcpServers/petriDesktop/args/3").asText());
    }

    @Test public void setupsIncludeCredentialsWhenAuthenticationIsOn() throws Exception {
        String url = "http://127.0.0.1:8765/mcp";
        String token = "test-token";
        assertTrue(McpConnectionInstructions.codex(url, token).contains("Authorization = \"Bearer test-token\""));
        assertTrue(McpConnectionInstructions.claudeCode(url, token).contains("--header \"Authorization: Bearer test-token\""));
        var desktop = DesktopMcpServer.JSON.readTree(McpConnectionInstructions.claudeDesktop(url, token));
        assertEquals(token, desktop.at("/mcpServers/petriDesktop/env/PETRI_MCP_TOKEN").asText());
    }
}

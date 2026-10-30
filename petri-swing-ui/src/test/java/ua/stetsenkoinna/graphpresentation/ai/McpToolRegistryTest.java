package ua.stetsenkoinna.graphpresentation.ai;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.Test;
import static org.junit.Assert.*;

public class McpToolRegistryTest {
    public static final class ContextService {
        int calls;
        @McpTool(name = "capture", description = "Capture supplied context", inputSchema = """
                {"type":"object","properties":{
                  "enabled":{"type":"boolean"},
                  "messages":{"type":"array","items":{"type":"object","properties":{
                    "role":{"type":"string","enum":["user","assistant"]},
                    "content":{"type":"string"}
                  },"required":["role","content"],"additionalProperties":false}}
                },"required":["messages"],"additionalProperties":false}
                """)
        public Object capture(JsonNode args) { calls++; return args; }
    }

    @Test public void validatesNestedContextBeforeInvokingService() throws Exception {
        ContextService service = new ContextService();
        McpToolRegistry registry = new McpToolRegistry(service);
        String valid = """
                {"enabled":true,"messages":[{"role":"user","content":"Build a queue"}]}
                """;
        registry.call("capture", DesktopMcpServer.JSON.readTree(valid));
        assertEquals(1, service.calls);
        for (String invalid : new String[]{
                "{\"messages\":{},\"enabled\":true}",
                "{\"messages\":[],\"enabled\":\"true\"}",
                "{\"messages\":[{\"role\":\"system\",\"content\":\"x\"}]}",
                "{\"messages\":[{\"role\":\"user\"}]}",
                "{\"messages\":[{\"role\":\"user\",\"content\":\"x\",\"extra\":1}]}",
                "{\"messages\":[null]}"}) {
            assertThrows(IllegalArgumentException.class,
                    () -> registry.call("capture", DesktopMcpServer.JSON.readTree(invalid)));
        }
        assertEquals(1, service.calls);
    }
}

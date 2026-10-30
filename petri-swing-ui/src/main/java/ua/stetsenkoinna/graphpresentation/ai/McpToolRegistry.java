package ua.stetsenkoinna.graphpresentation.ai;

import com.fasterxml.jackson.databind.JsonNode;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class McpToolRegistry {
    private record Binding(Object target, Method method, McpTool declaration, JsonNode schema) { }
    private final Map<String, Binding> bindings = new LinkedHashMap<>();

    public McpToolRegistry(Object... services) {
        for (Object service : services) {
            for (Method method : service.getClass().getMethods()) {
                McpTool declaration = method.getAnnotation(McpTool.class);
                if (declaration == null) continue;
                if (method.getParameterCount() != 1 || method.getParameterTypes()[0] != JsonNode.class) {
                    throw new IllegalArgumentException("MCP tool must accept one JsonNode: " + method);
                }
                try {
                    JsonNode schema = DesktopMcpServer.JSON.readTree(declaration.inputSchema());
                    Binding previous = bindings.putIfAbsent(declaration.name(), new Binding(service, method, declaration, schema));
                    if (previous != null) throw new IllegalArgumentException("Duplicate MCP tool: " + declaration.name());
                } catch (java.io.IOException ex) {
                    throw new IllegalArgumentException("Invalid tool schema: " + declaration.name(), ex);
                }
            }
        }
    }

    public List<Map<String, Object>> list() {
        List<Map<String, Object>> tools = new ArrayList<>();
        bindings.values().stream().sorted(Comparator.comparing(binding -> binding.declaration().name()))
                .forEach(binding -> tools.add(Map.of("name", binding.declaration().name(),
                        "description", binding.declaration().description(), "inputSchema", binding.schema())));
        return tools;
    }

    public Object call(String name, JsonNode arguments) throws Exception {
        Binding binding = bindings.get(name);
        if (binding == null) throw new IllegalArgumentException("Unknown tool: " + name);
        JsonNode args = arguments == null || arguments.isMissingNode()
                ? DesktopMcpServer.JSON.createObjectNode() : arguments;
        validate(args, binding.schema());
        try {
            return binding.method().invoke(binding.target(), args);
        } catch (InvocationTargetException ex) {
            if (ex.getCause() instanceof Exception cause) throw cause;
            throw new IllegalStateException(ex.getCause());
        }
    }

    private static void validate(JsonNode args, JsonNode schema) {
        if (!args.isObject()) throw new IllegalArgumentException("Arguments must be an object");
        for (JsonNode required : schema.path("required")) {
            if (!args.has(required.asText())) throw new IllegalArgumentException("Missing argument: " + required.asText());
        }
        args.fields().forEachRemaining(field -> {
            JsonNode property = schema.path("properties").path(field.getKey());
            if (property.isMissingNode()) throw new IllegalArgumentException("Unknown argument: " + field.getKey());
            JsonNode value = field.getValue();
            boolean valid = switch (property.path("type").asText()) {
                case "string" -> value.isTextual();
                case "integer" -> value.isIntegralNumber() && value.canConvertToInt();
                case "number" -> value.isNumber();
                default -> false;
            };
            if (!valid) throw new IllegalArgumentException("Invalid type for: " + field.getKey());
            if (property.has("enum")) {
                boolean found = false;
                for (JsonNode allowed : property.get("enum")) if (allowed.equals(value)) found = true;
                if (!found) throw new IllegalArgumentException("Invalid value for: " + field.getKey());
            }
            if (value.isNumber() && ((property.has("minimum") && value.asDouble() < property.get("minimum").asDouble())
                    || (property.has("maximum") && value.asDouble() > property.get("maximum").asDouble()))) {
                throw new IllegalArgumentException("Out of range: " + field.getKey());
            }
        });
    }
}

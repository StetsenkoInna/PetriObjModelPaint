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

    static void validate(JsonNode args, JsonNode schema) {
        validate(args, schema, "arguments");
    }

    private static void validate(JsonNode value, JsonNode property, String path) {
        boolean valid = switch (property.path("type").asText()) {
            case "string" -> value.isTextual();
            case "integer" -> value.isIntegralNumber() && value.canConvertToInt();
            case "number" -> value.isNumber();
            case "boolean" -> value.isBoolean();
            case "array" -> value.isArray();
            case "object" -> value.isObject();
            default -> false;
        };
        if (!valid) throw new IllegalArgumentException("Invalid type for: " + path);
        if (property.has("enum")) {
            boolean found = false;
            for (JsonNode allowed : property.get("enum")) if (allowed.equals(value)) found = true;
            if (!found) throw new IllegalArgumentException("Invalid value for: " + path);
        }
        if (value.isNumber() && ((property.has("minimum") && value.asDouble() < property.get("minimum").asDouble())
                || (property.has("maximum") && value.asDouble() > property.get("maximum").asDouble()))) {
            throw new IllegalArgumentException("Out of range: " + path);
        }
        if (value.isNumber() && !Double.isFinite(value.asDouble())) throw new IllegalArgumentException("Non-finite number: " + path);
        if (value.isTextual() && ((property.has("minLength") && value.asText().length() < property.get("minLength").asInt())
                || (property.has("maxLength") && value.asText().length() > property.get("maxLength").asInt()))) throw new IllegalArgumentException("Invalid length: " + path);
        if (value.isArray()) {
            if ((property.has("maxItems") && value.size() > property.get("maxItems").asInt())
                    || (property.has("minItems") && value.size() < property.get("minItems").asInt())) throw new IllegalArgumentException("Invalid array size: " + path);
            for (int i = 0; i < value.size(); i++) {
                validate(value.get(i), property.path("items"), path + "[" + i + "]");
            }
        }
        if (value.isObject()) {
            for (JsonNode required : property.path("required")) {
                if (!value.has(required.asText())) throw new IllegalArgumentException("Missing argument: " + path + "." + required.asText());
            }
            value.fields().forEachRemaining(field -> {
                JsonNode child = property.path("properties").path(field.getKey());
                if (child.isMissingNode()) throw new IllegalArgumentException("Unknown argument: " + path + "." + field.getKey());
                validate(field.getValue(), child, path + "." + field.getKey());
            });
        }
    }
}

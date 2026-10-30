package ua.stetsenkoinna.graphpresentation.training;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import java.util.ArrayList;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/** Removes credential-shaped metadata without changing the exported model. */
public final class TrainingMetadataRedactor {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String MASK = "[REDACTED]";
    private static final Set<String> SECRET_KEYS = Set.of("authorization", "token", "mcptoken", "bearertoken",
            "accesstoken", "refreshtoken", "idtoken", "sessiontoken", "password", "passphrase",
            "secret", "secretkey", "privatekey", "clientsecret", "credential", "credentials");
    private static final String KEY_PATTERN = "(?:authorization|(?:[a-z0-9]+[_-])*api[_-]?key|token|mcp[_-]?token|bearer[_-]?token|access[_-]?token|refresh[_-]?token|id[_-]?token|session[_-]?token|password|passphrase|secret(?:[_-]?key)?|private[_-]?key|client[_-]?secret)";
    private static final Pattern LABELED = Pattern.compile("(?i)(\\b" + KEY_PATTERN
            + "[\\\"']?\\s*[:=]\\s*)(\\\"[^\\\"]*\\\"|'[^']*'|[^\\s,;]+)");
    private static final Pattern BEARER = Pattern.compile("(?i)\\bBearer\\s+[^\\s\\\"'<>]+");
    private static final Pattern PROVIDER_KEY = Pattern.compile("\\bsk-[A-Za-z0-9_-]{12,}");
    private static final Pattern JWT = Pattern.compile("\\beyJ[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+\\b");
    private static final Pattern PRIVATE_KEY = Pattern.compile("-----BEGIN [^-]*PRIVATE KEY-----.*?-----END [^-]*PRIVATE KEY-----", Pattern.DOTALL);

    private TrainingMetadataRedactor() { }

    public static JsonNode redact(Object value) {
        JsonNode copy = JSON.valueToTree(value);
        return redactNode(copy);
    }

    public static String redactText(String text) {
        if (text == null) return null;
        String value = PRIVATE_KEY.matcher(text).replaceAll(MASK);
        value = BEARER.matcher(value).replaceAll(MASK);
        value = PROVIDER_KEY.matcher(value).replaceAll(MASK);
        value = JWT.matcher(value).replaceAll(MASK);
        return LABELED.matcher(value).replaceAll("$1" + MASK);
    }

    private static JsonNode redactNode(JsonNode value) {
        if (value.isTextual()) return TextNode.valueOf(redactText(value.textValue()));
        if (value instanceof ObjectNode object) {
            ArrayList<String> names = new ArrayList<>();
            object.fieldNames().forEachRemaining(names::add);
            for (String name : names) {
                JsonNode child = object.get(name);
                String normalized = name.replace("_", "").replace("-", "").toLowerCase(Locale.ROOT);
                boolean credentialKey = SECRET_KEYS.contains(normalized) || normalized.endsWith("apikey");
                boolean domainCount = normalized.equals("token") && child.isNumber();
                object.set(name, credentialKey && !domainCount && !child.isNull()
                        ? TextNode.valueOf(MASK) : redactNode(child));
            }
        } else if (value instanceof ArrayNode array) {
            for (int i = 0; i < array.size(); i++) array.set(i, redactNode(array.get(i)));
        }
        return value;
    }
}

package ua.stetsenkoinna.graphpresentation.training;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.junit.Test;
import static org.junit.Assert.*;

public class TrainingMetadataRedactorTest {
    private static final ObjectMapper JSON = new ObjectMapper();

    @Test public void structuredCredentialsAreMaskedWithoutChangingDomainCountsOrSourceObjects() {
        Map<String, Object> arguments = Map.of("tokens", 6, "token", 3,
                "mcp_token", "opaque_mcp_credential", "bearer-token", "opaque_bearer_credential",
                "password", 123456, "OPENAI_API_KEY", "opaque_provider_credential");
        JsonNode source = JSON.valueToTree(Map.of("arguments", arguments, "result", List.of(Map.of("access_token", "opaque_access_credential"))));
        JsonNode redacted = TrainingMetadataRedactor.redact(source);
        assertEquals(6, redacted.at("/arguments/tokens").asInt());
        assertEquals(3, redacted.at("/arguments/token").asInt());
        for (String name : List.of("mcp_token", "bearer-token", "password", "OPENAI_API_KEY")) {
            assertEquals("[REDACTED]", redacted.at("/arguments/" + name).asText());
        }
        assertEquals("[REDACTED]", redacted.at("/result/0/access_token").asText());
        assertEquals("opaque_mcp_credential", source.at("/arguments/mcp_token").asText());
        assertEquals("opaque_mcp_credential", arguments.get("mcp_token"));
    }

    @Test public void textualCredentialsIncludingQuotedJsonAreMaskedAndModelDescriptionsRemain() {
        String text = "Process 6 tokens. api_key=opaque_api_value mcp_token: opaque_mcp_value "
                + "token=opaque_token_value Bearer opaque_bearer_value sk-test_1234567890abcdef "
                + "{\"mcp_token\":\"opaque_json_value\"} password='opaque password value'";
        String redacted = TrainingMetadataRedactor.redactText(text);
        assertTrue(redacted.contains("Process 6 tokens."));
        for (String secret : List.of("opaque_api_value", "opaque_mcp_value", "opaque_token_value", "opaque_bearer_value",
                "sk-test_1234567890abcdef", "opaque_json_value", "opaque password value")) {
            assertFalse(secret, redacted.contains(secret));
        }
        assertTrue(redacted.contains("[REDACTED]"));
    }
}

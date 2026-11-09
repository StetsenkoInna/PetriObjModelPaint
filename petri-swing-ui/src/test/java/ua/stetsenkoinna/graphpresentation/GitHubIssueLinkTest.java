package ua.stetsenkoinna.graphpresentation;

import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import java.io.IOException;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.Test;

public class GitHubIssueLinkTest {

    @Test
    public void everyLinkIsAValidUriOnTheRepository() {
        for (GitHubIssueLink.Kind kind : GitHubIssueLink.Kind.values()) {
            String link = GitHubIssueLink.forKind(kind);
            URI uri = URI.create(link);
            assertTrue(link, link.startsWith(GitHubIssueLink.NEW_ISSUE_URL + "?template=" + kind.template));
            assertTrue(link, !uri.getRawQuery().contains(" ") && !uri.getRawQuery().contains("+"));
        }
    }

    @Test
    public void bugLinkCarriesTheRuntime() {
        Map<String, String> fields = fields(GitHubIssueLink.forKind(GitHubIssueLink.Kind.BUG));
        assertTrue(fields.toString(), fields.get("java-version").startsWith(System.getProperty("java.version")));
        assertTrue(fields.toString(), fields.get("os").startsWith(System.getProperty("os.name")));
    }

    /**
     * A field the link fills must still exist in the template, or GitHub drops the value
     * without a word. Skipped when the tests run outside a full checkout.
     */
    @Test
    public void filledFieldsExistInTheirTemplates() throws IOException {
        Path templates = templatesDir();
        assumeTrue("no .github/ISSUE_TEMPLATE next to this module", templates != null);
        for (GitHubIssueLink.Kind kind : GitHubIssueLink.Kind.values()) {
            String yaml = Files.readString(templates.resolve(kind.template), StandardCharsets.UTF_8);
            for (Map.Entry<String, String> field : fields(GitHubIssueLink.forKind(kind)).entrySet()) {
                if (field.getKey().equals("template")) {
                    continue;
                }
                assertTrue(kind.template + " lacks id " + field.getKey(),
                        yaml.contains("id: " + field.getKey() + "\n") || yaml.contains("id: " + field.getKey() + "\r\n"));
                if (field.getKey().equals("module")) {
                    assertTrue(kind.template + " lacks option " + field.getValue(),
                            yaml.contains("- " + field.getValue()));
                }
            }
        }
    }

    private static Path templatesDir() {
        for (Path dir = Paths.get("").toAbsolutePath(); dir != null; dir = dir.getParent()) {
            Path candidate = dir.resolve(".github").resolve("ISSUE_TEMPLATE");
            if (Files.isDirectory(candidate)) {
                return candidate;
            }
        }
        return null;
    }

    private static Map<String, String> fields(String link) {
        Map<String, String> fields = new LinkedHashMap<>();
        for (String pair : URI.create(link).getRawQuery().split("&")) {
            String[] kv = pair.split("=", 2);
            fields.put(URLDecoder.decode(kv[0], StandardCharsets.UTF_8),
                    URLDecoder.decode(kv[1], StandardCharsets.UTF_8));
        }
        return fields;
    }
}

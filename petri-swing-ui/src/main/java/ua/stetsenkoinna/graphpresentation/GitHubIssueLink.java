package ua.stetsenkoinna.graphpresentation;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Builds "new issue" addresses that open one of the repository's issue forms with the
 * environment already filled in, so a report from the app needs no copying from About.
 *
 * <p>GitHub fills a form field from the query parameter named after the field's {@code id}
 * in {@code .github/ISSUE_TEMPLATE/*.yml}. Those ids are therefore a contract with every
 * released build: renaming one in a template silently stops older builds from filling it.
 * A dropdown is filled by the exact text of one of its options.
 *
 * <p>Reports go through the reporter's own GitHub account in the browser. Filing them from
 * the app itself, with a token, is a possible later option and would live beside this one.
 */
final class GitHubIssueLink {

    static final String NEW_ISSUE_URL = "https://github.com/StetsenkoInna/PetriObjModelPaint/issues/new";

    /** Option text of the "module" dropdown in bug_report.yml and feature_request.yml. */
    static final String DESKTOP_MODULE = "petri-swing-ui (desktop editor)";

    enum Kind {
        BUG("bug_report.yml"),
        FEATURE("feature_request.yml"),
        FEEDBACK("feedback.yml");

        final String template;

        Kind(String template) {
            this.template = template;
        }
    }

    private GitHubIssueLink() {
    }

    static String forKind(Kind kind) {
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("template", kind.template);
        String version = ua.stetsenkoinna.AppVersion.current();
        String versionText = version.isEmpty() ? "development build" : version;
        String javaText = System.getProperty("java.version") + " (" + System.getProperty("java.vendor") + ")";
        String osText = System.getProperty("os.name") + " " + System.getProperty("os.version")
                + " " + System.getProperty("os.arch");
        switch (kind) {
            case BUG:
                fields.put("module", DESKTOP_MODULE);
                fields.put("version", versionText);
                fields.put("java-version", javaText);
                fields.put("os", osText);
                break;
            case FEATURE:
                fields.put("module", DESKTOP_MODULE);
                break;
            case FEEDBACK:
                fields.put("version", versionText + ", Java " + javaText + ", " + osText);
                break;
        }
        return NEW_ISSUE_URL + "?" + query(fields);
    }

    private static String query(Map<String, String> fields) {
        StringBuilder out = new StringBuilder();
        for (Map.Entry<String, String> field : fields.entrySet()) {
            if (out.length() > 0) {
                out.append('&');
            }
            out.append(encode(field.getKey())).append('=').append(encode(field.getValue()));
        }
        return out.toString();
    }

    /** URLEncoder targets HTML forms and writes spaces as '+'; a URL wants %20. */
    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }
}

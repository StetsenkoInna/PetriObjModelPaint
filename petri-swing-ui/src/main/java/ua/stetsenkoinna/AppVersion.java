package ua.stetsenkoinna;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import java.util.regex.Pattern;

/**
 * The version this build was made from, as Maven stamped it into the JAR.
 *
 * <p>The number comes from the same packaged resource the updater reads, so the window
 * titles, the About box and the update check can never disagree about which release is
 * running. A run that skipped resource filtering (straight from an IDE) has no number; it
 * reads as empty and the labels fall back to the bare application name rather than
 * showing a placeholder such as {@code ${project.version}}.
 */
public final class AppVersion {
    /** The product name, as the Welcome window, the About box and the releases spell it. */
    public static final String PRODUCT = "PetriObjModelPaint";

    static final String RESOURCE = "/ua/stetsenkoinna/updates/build.properties";
    private static final Pattern RELEASE = Pattern.compile("\\d+\\.\\d+\\.\\d+(?:[-+][0-9A-Za-z.-]+)?");
    private static final String CURRENT = load();

    private AppVersion() {
    }

    /** {@code "2.8.1"}, or empty when this is not a packaged build. */
    public static String current() {
        return CURRENT;
    }

    /** {@code name + " " + version}, or just {@code name} when the version is unknown. */
    public static String label(String name) {
        return label(name, CURRENT);
    }

    static String label(String name, String version) {
        return version == null || version.isEmpty() ? name : name + " " + version;
    }

    /** The version a build resource states, or empty when it states none or a placeholder. */
    static String parse(Properties build) {
        String version = build.getProperty("version", "").strip();
        return RELEASE.matcher(version).matches() ? version : "";
    }

    private static String load() {
        Properties build = new Properties();
        try (InputStream input = AppVersion.class.getResourceAsStream(RESOURCE)) {
            if (input == null) {
                return "";
            }
            build.load(input);
        } catch (IOException unreadable) {
            return "";
        }
        return parse(build);
    }
}

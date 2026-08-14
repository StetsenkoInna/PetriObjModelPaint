package ua.stetsenkoinna.theme;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Asks the desktop whether it is currently in dark mode.
 *
 * <p>Java has no portable API for this, so each desktop is asked in the only way it answers: the
 * registry on Windows, the global defaults domain on macOS, and GSettings on the GNOME-derived
 * Linux desktops. All three are separate processes, which is why the answer is cached - see
 * {@link #detect()} for when that cache is dropped.
 *
 * <p>Every path here is allowed to fail. A machine with no {@code reg} on PATH, a Linux desktop
 * that is not GNOME, a locked-down sandbox that refuses to fork - all of them simply produce
 * {@link Optional#empty()}, and the caller falls back to light, which is the appearance the app
 * has always had. Being unable to detect the desktop's preference is not an error worth
 * interrupting a startup for.
 *
 * <p>The command output parsers are package-private and pure, so they can be tested without a
 * desktop of the relevant kind - which matters, since no single CI machine has all three.
 */
public final class SystemThemeDetector {

    private static final Logger LOGGER = LoggerFactory.getLogger(SystemThemeDetector.class);

    /**
     * How long a probe is given before it is killed. Generous next to what these commands
     * normally cost (single-digit milliseconds) and still short enough that the worst case is a
     * barely perceptible pause on the way to the first window rather than a hang.
     */
    private static final long PROBE_TIMEOUT_SECONDS = 2;

    private static final String WINDOWS_PERSONALIZE_KEY =
            "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Themes\\Personalize";

    /** Cached result of the last probe; {@code null} means "not probed since the last refresh". */
    private static volatile Optional<ThemeVariant> cached;

    private SystemThemeDetector() {
    }

    /**
     * @return the desktop's current appearance, or empty when this desktop cannot be asked
     *
     * <p>Cached after the first call, because the answer costs a process and is wanted on the
     * startup path. The cache is dropped by {@link #refresh()}, which the theme is re-applied
     * through - so a user who changes their desktop to dark and then reopens the theme menu
     * sees the new answer, while a repaint never pays for one.
     */
    public static Optional<ThemeVariant> detect() {
        Optional<ThemeVariant> local = cached;
        if (local == null) {
            local = probe();
            cached = local;
        }
        return local;
    }

    /**
     * Forgets the cached answer, so the next {@link #detect()} asks the desktop again.
     */
    public static void refresh() {
        cached = null;
    }

    private static Optional<ThemeVariant> probe() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        Optional<ThemeVariant> result;
        if (os.contains("win")) {
            result = parseWindowsRegistry(
                    run(List.of("reg", "query", WINDOWS_PERSONALIZE_KEY, "/v", "AppsUseLightTheme")));
        } else if (os.contains("mac") || os.contains("darwin")) {
            result = parseMacInterfaceStyle(
                    run(List.of("defaults", "read", "-g", "AppleInterfaceStyle")));
        } else {
            result = parseGnomeColorScheme(
                    run(List.of("gsettings", "get", "org.gnome.desktop.interface", "color-scheme")));
            if (result.isEmpty()) {
                // Desktops still on the pre-45 GNOME setting, and the several derivatives that
                // never adopted color-scheme, only ever say "dark" by naming a dark GTK theme.
                result = parseGtkThemeName(
                        run(List.of("gsettings", "get", "org.gnome.desktop.interface", "gtk-theme")));
            }
        }
        LOGGER.debug("System theme probe on '{}' returned {}", os, result);
        return result;
    }

    /**
     * Reads {@code AppsUseLightTheme}, the value Windows itself flips when "Choose your default
     * app mode" changes. Deliberately not {@code SystemUsesLightTheme}, which is the taskbar and
     * Start menu - those can be dark while applications are meant to stay light.
     *
     * @param output raw {@code reg query} output, or null if the command could not be run
     * @return dark when the DWORD is 0, light when it is anything else, empty when the value is
     *         absent - which is what a Windows old enough to have no app mode at all looks like
     */
    static Optional<ThemeVariant> parseWindowsRegistry(String output) {
        if (output == null) {
            return Optional.empty();
        }
        for (String line : output.split("\\R")) {
            String trimmed = line.trim();
            if (!trimmed.startsWith("AppsUseLightTheme")) {
                continue;
            }
            // "AppsUseLightTheme    REG_DWORD    0x0" - the value is the last whitespace-separated
            // token, hex-prefixed, and the separator is a run of spaces rather than a single one.
            String[] tokens = trimmed.split("\\s+");
            String value = tokens[tokens.length - 1];
            try {
                int light = value.toLowerCase(Locale.ROOT).startsWith("0x")
                        ? Integer.parseInt(value.substring(2), 16)
                        : Integer.parseInt(value);
                return Optional.of(light == 0 ? ThemeVariant.DARK : ThemeVariant.LIGHT);
            } catch (NumberFormatException ex) {
                LOGGER.debug("Unreadable AppsUseLightTheme value '{}'", value);
                return Optional.empty();
            }
        }
        return Optional.empty();
    }

    /**
     * macOS only defines {@code AppleInterfaceStyle} while dark mode is on; in light mode the key
     * does not exist and {@code defaults read} fails, which reaches here as null. That absence is
     * a real answer - light - rather than a failure to detect, so it is reported as one.
     *
     * @param output raw {@code defaults read} output, or null when the command failed
     */
    static Optional<ThemeVariant> parseMacInterfaceStyle(String output) {
        if (output == null) {
            return Optional.of(ThemeVariant.LIGHT);
        }
        return Optional.of(output.trim().equalsIgnoreCase("Dark")
                ? ThemeVariant.DARK
                : ThemeVariant.LIGHT);
    }

    /**
     * Reads GNOME's {@code color-scheme}, whose values are quoted: {@code 'prefer-dark'},
     * {@code 'prefer-light'} or {@code 'default'}. Only the first two are answers - {@code
     * 'default'} means the user never expressed a preference, so it is reported as empty and the
     * caller moves on to the GTK theme name.
     *
     * @param output raw {@code gsettings get} output, or null when the command failed
     */
    static Optional<ThemeVariant> parseGnomeColorScheme(String output) {
        if (output == null) {
            return Optional.empty();
        }
        String value = unquote(output);
        if (value.equalsIgnoreCase("prefer-dark")) {
            return Optional.of(ThemeVariant.DARK);
        }
        if (value.equalsIgnoreCase("prefer-light")) {
            return Optional.of(ThemeVariant.LIGHT);
        }
        return Optional.empty();
    }

    /**
     * Last resort on Linux: a GTK theme whose name ends in {@code -dark} is the long-standing
     * convention for the dark build of a theme ({@code Adwaita-dark}, {@code Yaru-dark}). A name
     * that does not say dark is treated as light rather than as unknown, because by this point
     * GSettings has answered - the desktop is GNOME-like and simply is not dark.
     *
     * @param output raw {@code gsettings get} output, or null when the command failed
     */
    static Optional<ThemeVariant> parseGtkThemeName(String output) {
        if (output == null) {
            return Optional.empty();
        }
        String value = unquote(output).toLowerCase(Locale.ROOT);
        if (value.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(value.endsWith("-dark") || value.endsWith("dark")
                ? ThemeVariant.DARK
                : ThemeVariant.LIGHT);
    }

    private static String unquote(String raw) {
        String value = raw.trim();
        if (value.length() >= 2 && value.startsWith("'") && value.endsWith("'")) {
            value = value.substring(1, value.length() - 1);
        }
        return value;
    }

    /**
     * Runs a probe command and returns its standard output.
     *
     * @return the command's output, or null if it could not be started, timed out, or exited
     *         non-zero - every one of which the callers treat the same way
     */
    private static String run(List<String> command) {
        Process process = null;
        try {
            process = new ProcessBuilder(command).redirectErrorStream(false).start();
            String output;
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), Charset.defaultCharset()))) {
                output = reader.lines().reduce("", (a, b) -> a.isEmpty() ? b : a + System.lineSeparator() + b);
            }
            if (!process.waitFor(PROBE_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                LOGGER.debug("System theme probe {} timed out", command);
                return null;
            }
            return process.exitValue() == 0 ? output : null;
        } catch (IOException ex) {
            LOGGER.debug("System theme probe {} could not be started: {}", command, ex.getMessage());
            return null;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return null;
        } finally {
            if (process != null) {
                process.destroy();
            }
        }
    }
}

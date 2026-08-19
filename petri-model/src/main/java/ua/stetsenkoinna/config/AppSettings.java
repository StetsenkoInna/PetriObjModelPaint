package ua.stetsenkoinna.config;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ua.stetsenkoinna.theme.ThemeMode;

/**
 * The user's application-wide preferences: {@code ~/.PetriObjModelPaint/configs/ui.properties},
 * next to the caches and logs the app already keeps in the user's home.
 *
 * <p>That is the file the Petri-object toolbar has always kept its pinned list in, and it stays
 * the only one - a second file for a second preference would mean two things to find, two things
 * to copy between machines, and two copies of the same load-and-store code.
 *
 * <p>A plain properties file rather than {@link java.util.prefs.Preferences}: the preferences API
 * would put this in the Windows registry or an opaque per-user store, whereas everything else the
 * app persists is a file the user can find, read, copy between machines, and delete to start
 * over. Being able to delete it is what makes the first-run dialog testable by hand.
 *
 * <p>Every failure to read or write is logged and swallowed. A preferences file is not worth
 * refusing to start over: an unreadable one means defaults, an unwritable one means the choice
 * lasts for this session only.
 */
public class AppSettings {

    private static final Logger LOGGER = LoggerFactory.getLogger(AppSettings.class);

    private static final String FILE_NAME = "ui.properties";

    /** Stores a {@link ThemeMode} constant name. */
    private static final String KEY_THEME = "ui.theme";

    /**
     * Set once the first-start dialog has been answered. Deliberately separate from {@link
     * #KEY_THEME}: a user who accepts the default theme still has to count as having been asked,
     * and a missing theme key alone could not tell the two apart.
     */
    private static final String KEY_SETUP_COMPLETED = "setup.completed";

    /** Stores whether the last active project should be reopened automatically on startup. */
    private static final String KEY_REOPEN_LAST_PROJECT = "reopen.lastProject";

    private static volatile AppSettings shared;

    /** Where the settings live, or null when there is nowhere to keep them - see {@link #shared()}. */
    private final Path file;

    private Properties properties = new Properties();

    /**
     * @param file the properties file to read now and write back on every change; it does not
     *        have to exist, and its directory is created on the first save. Null for settings
     *        that live for this session only.
     */
    public AppSettings(Path file) {
        this.file = file;
        properties = readFile();
    }

    /**
     * @param directories the application's user directory, or null when there is none
     * @return settings kept in that directory, or session-only settings if it is null
     */
    public static AppSettings in(UserDirectoryManager directories) {
        return new AppSettings(directories == null
                ? null
                : directories.getFilePath(FILE_NAME, AppDirectoryType.CONFIGS));
    }

    /**
     * @return the settings of the running application, loaded on first use from the standard
     *         location under the user's home directory - or session-only settings if that
     *         directory cannot be used at all, since a home directory nobody can write to is
     *         not a reason to refuse to open the editor
     */
    public static AppSettings shared() {
        AppSettings local = shared;
        if (local == null) {
            synchronized (AppSettings.class) {
                local = shared;
                if (local == null) {
                    UserDirectoryManager directories = null;
                    try {
                        directories = new UserDirectoryManager();
                    } catch (RuntimeException unavailable) {
                        LOGGER.warn("User directory unavailable; settings will not persist",
                                unavailable);
                    }
                    local = in(directories);
                    shared = local;
                }
            }
        }
        return local;
    }

    /**
     * @return where these settings are stored, for the settings dialog to show the user
     */
    public Path getFile() {
        return file;
    }

    /**
     * @return the theme the user chose, or {@link ThemeMode#DEFAULT} if they never have
     */
    public ThemeMode getThemeMode() {
        return ThemeMode.fromName(properties.getProperty(KEY_THEME));
    }

    /**
     * Records the chosen theme and writes it out immediately, so a crash before the next clean
     * exit does not lose it.
     */
    public void setThemeMode(ThemeMode mode) {
        properties.setProperty(KEY_THEME, mode.name());
        save();
    }

    /**
     * @return false only on a genuinely first start - or after the user deleted the settings
     *         file - which is when the setup dialog is shown
     */
    public boolean isInitialSetupCompleted() {
        return Boolean.parseBoolean(properties.getProperty(KEY_SETUP_COMPLETED));
    }

    /**
     * Marks the first-start dialog as answered, so it is not shown again.
     */
    public void markInitialSetupCompleted() {
        properties.setProperty(KEY_SETUP_COMPLETED, Boolean.TRUE.toString());
        save();
    }

    /**
     * @return true unless the user has explicitly turned this off; a user who has never touched
     *         the setting gets their last project reopened automatically
     */
    public boolean isReopenLastProjectOnStartup() {
        String value = properties.getProperty(KEY_REOPEN_LAST_PROJECT);
        return value == null || Boolean.parseBoolean(value);
    }

    /**
     * Records whether the last active project should be reopened on startup and writes it out
     * immediately.
     */
    public void setReopenLastProjectOnStartup(boolean value) {
        properties.setProperty(KEY_REOPEN_LAST_PROJECT, Boolean.toString(value));
        save();
    }

    /**
     * @param key a setting this class has no typed accessor for
     * @param fallback what to answer when the setting has never been written
     * @return the stored value, or {@code fallback}
     *
     * <p>The escape hatch for preferences whose meaning belongs to their own component rather
     * than here - the Petri-object toolbar's pinned list, for one. What is stored is that
     * component's business; where it is stored is this class's.
     */
    public String getString(String key, String fallback) {
        return properties.getProperty(key, fallback);
    }

    /**
     * Stores {@code value} under {@code key} and writes the file out.
     */
    public void setString(String key, String value) {
        properties.setProperty(key, value);
        save();
    }

    private Properties readFile() {
        Properties loaded = new Properties();
        if (file == null || !Files.exists(file)) {
            LOGGER.debug("No settings file at {} yet; using defaults", file);
            return loaded;
        }
        try (InputStream in = Files.newInputStream(file)) {
            loaded.load(in);
        } catch (IOException ex) {
            LOGGER.warn("Could not read settings from {}; using defaults", file, ex);
        }
        return loaded;
    }

    /**
     * Writes every setting back out, after re-reading what is on disk and layering this
     * instance's values over it.
     *
     * <p>The re-read is what keeps two settings objects on one file from erasing each other:
     * the theme is written through the shared instance and the Petri-object toolbar keeps its
     * own, so a plain whole-file store from whichever loaded first would silently drop
     * everything the other has written since.
     */
    private void save() {
        if (file == null) {
            return;
        }
        Properties merged = readFile();
        merged.putAll(properties);
        properties = merged;
        try {
            Path parent = file.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            try (OutputStream out = Files.newOutputStream(file)) {
                merged.store(out, "PetriObjModelPaint user interface settings");
            }
        } catch (IOException ex) {
            LOGGER.warn("Could not write settings to {}; this session's changes will not persist",
                    file, ex);
        }
    }
}

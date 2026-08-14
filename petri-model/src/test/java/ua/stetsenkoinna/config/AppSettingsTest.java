package ua.stetsenkoinna.config;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import ua.stetsenkoinna.theme.ThemeMode;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * How the preferences file behaves, including on the machines where it is missing, unreadable
 * or has been edited by hand - which is a thing users do to a plain properties file in their own
 * home directory, and the reason it is one.
 */
public class AppSettingsTest {

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    private Path settingsFile() {
        return folder.getRoot().toPath().resolve("configs").resolve("ui.properties");
    }

    @Test
    public void aMissingFileMeansDefaultsAndAFirstRun() {
        AppSettings settings = new AppSettings(settingsFile());
        assertEquals(ThemeMode.DEFAULT, settings.getThemeMode());
        assertFalse("a fresh install has not been through setup", settings.isInitialSetupCompleted());
    }

    @Test
    public void aChosenThemeSurvivesARestart() {
        new AppSettings(settingsFile()).setThemeMode(ThemeMode.DARK);
        assertEquals(ThemeMode.DARK, new AppSettings(settingsFile()).getThemeMode());
    }

    /**
     * The directory the file lives in is created on demand: the settings can be written before
     * anything else has had a reason to make the application's config folder.
     */
    @Test
    public void savingCreatesTheDirectoryItNeeds() {
        new AppSettings(settingsFile()).setThemeMode(ThemeMode.LIGHT);
        assertTrue(Files.exists(settingsFile()));
    }

    /**
     * Accepting the default in the setup dialog still counts as having been asked. Recorded
     * separately from the theme for exactly this case - a missing theme key on its own could not
     * tell "never asked" from "asked, and kept the default".
     */
    @Test
    public void completingSetupIsRecordedEvenWhenNothingWasChanged() {
        AppSettings settings = new AppSettings(settingsFile());
        settings.markInitialSetupCompleted();

        AppSettings reloaded = new AppSettings(settingsFile());
        assertTrue(reloaded.isInitialSetupCompleted());
        assertEquals(ThemeMode.DEFAULT, reloaded.getThemeMode());
    }

    /**
     * The file is plain text in the user's home directory, so it will be hand-edited, and a
     * typo in it is not a reason to refuse to start.
     */
    @Test
    public void anUnrecognisedThemeNameFallsBackToTheDefault() throws IOException {
        Files.createDirectories(settingsFile().getParent());
        Files.writeString(settingsFile(), "ui.theme=Solarized\n");
        assertEquals(ThemeMode.DEFAULT, new AppSettings(settingsFile()).getThemeMode());
    }

    @Test
    public void aThemeNameIsReadRegardlessOfCase() throws IOException {
        Files.createDirectories(settingsFile().getParent());
        Files.writeString(settingsFile(), "ui.theme=dark\n");
        assertEquals(ThemeMode.DARK, new AppSettings(settingsFile()).getThemeMode());
    }

    /**
     * This file holds more than the theme - the Petri-object toolbar keeps its pinned list in it
     * through its own instance - so writing through one must not erase what another has written
     * since. A plain whole-file store from whichever loaded first would do exactly that.
     */
    @Test
    public void oneWriterDoesNotEraseAnother() {
        AppSettings theme = new AppSettings(settingsFile());
        AppSettings toolbar = new AppSettings(settingsFile());

        theme.setThemeMode(ThemeMode.DARK);
        toolbar.setString("toolbar.petriObjects", "builtin:CreateNetGenerator");

        AppSettings reloaded = new AppSettings(settingsFile());
        assertEquals(ThemeMode.DARK, reloaded.getThemeMode());
        assertEquals("builtin:CreateNetGenerator",
                reloaded.getString("toolbar.petriObjects", null));
    }

    /**
     * A home directory that cannot be used is not a reason to refuse to open the editor: the
     * settings simply stop persisting, and everything still reads back within the session.
     */
    @Test
    public void settingsWithNowhereToLiveStillWorkForThisSession() {
        AppSettings sessionOnly = new AppSettings(null);

        sessionOnly.setThemeMode(ThemeMode.DARK);
        sessionOnly.markInitialSetupCompleted();

        assertEquals(ThemeMode.DARK, sessionOnly.getThemeMode());
        assertTrue(sessionOnly.isInitialSetupCompleted());
    }
}

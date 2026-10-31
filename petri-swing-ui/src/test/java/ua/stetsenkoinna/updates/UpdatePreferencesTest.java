package ua.stetsenkoinna.updates;

import java.time.*;
import org.junit.*;
import org.junit.rules.TemporaryFolder;
import ua.stetsenkoinna.config.AppSettings;
import static org.junit.Assert.*;

public class UpdatePreferencesTest {
    @Rule public TemporaryFolder folder = new TemporaryFolder();
    private final Instant now = Instant.parse("2026-10-31T12:00:00Z");
    @Test public void tomorrowPersistsAndNewBuildsAreNotSilenced() {
        var file=folder.getRoot().toPath().resolve("ui.properties");
        var prefs=new UpdatePreferences(new AppSettings(file));
        prefs.remindTomorrow("2.8.0:first",now);
        var reloaded=new UpdatePreferences(new AppSettings(file));
        assertFalse(reloaded.shouldNotify("2.8.0:first",now.plus(Duration.ofHours(23))));
        assertTrue(reloaded.shouldNotify("2.8.0:first",now.plus(Duration.ofDays(1))));
        assertTrue(reloaded.shouldNotify("2.8.0:replacement",now));
        assertTrue(reloaded.shouldNotify("2.9.0:new",now));
    }
    @Test public void skipIsSpecificToBuildAndDisableAppliesToAllChecks() {
        var prefs=new UpdatePreferences(new AppSettings(null));
        assertTrue(prefs.enabled()); prefs.skip("2.8.0:first");
        assertFalse(prefs.shouldNotify("2.8.0:first",now.plus(Duration.ofDays(400))));
        assertTrue(prefs.shouldNotify("2.8.0:replacement",now));
        prefs.setEnabled(false);
        assertFalse(prefs.enabled()); assertFalse(prefs.shouldNotify("2.9.0:new",now));
    }
}

package ua.stetsenkoinna.updates;

import java.time.Duration;
import java.time.Instant;
import ua.stetsenkoinna.config.AppSettings;

public final class UpdatePreferences {
    private final AppSettings settings;
    public UpdatePreferences(AppSettings settings) { this.settings = settings; }
    public boolean enabled() { return Boolean.parseBoolean(settings.getString("updates.onStartup", "true")); }
    public void setEnabled(boolean value) { settings.setString("updates.onStartup", Boolean.toString(value)); }
    public void remindTomorrow(String identity, Instant now) {
        settings.setString("updates.snoozedBuild", identity);
        settings.setString("updates.remindAfter", now.plus(Duration.ofDays(1)).toString());
    }
    public void skip(String identity) { settings.setString("updates.skippedBuild", identity); }
    public boolean shouldNotify(String identity, Instant now) {
        if (!enabled() || identity.equals(settings.getString("updates.skippedBuild", ""))) return false;
        if (identity.equals(settings.getString("updates.snoozedBuild", ""))) {
            try { return !now.isBefore(Instant.parse(settings.getString("updates.remindAfter", ""))); }
            catch (java.time.format.DateTimeParseException ignored) { return true; }
        }
        return true;
    }
}

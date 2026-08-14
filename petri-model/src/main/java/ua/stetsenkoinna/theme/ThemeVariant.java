package ua.stetsenkoinna.theme;

/**
 * An appearance that can actually be painted, as opposed to {@link ThemeMode}, which is what the
 * user picked and may still be a deferral. Everything downstream of the resolution step - the
 * look and feel palette, the canvas palette - is chosen by one of these two values, so there is
 * never a third case for a painter to wonder about.
 */
public enum ThemeVariant {

    LIGHT,

    DARK;

    public boolean isDark() {
        return this == DARK;
    }
}

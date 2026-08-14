package ua.stetsenkoinna.theme;

/**
 * The appearance setting as the user chooses it, which is not the same thing as the appearance
 * that ends up on screen: {@link #SYSTEM} has no look of its own, it defers to the desktop and
 * can therefore mean either of the other two depending on the machine the app is started on.
 * {@link ThemeVariant} is the resolved answer, and {@link #resolve()} is the step between.
 *
 * <p>Stored by name in the settings file, so the constant names are part of that file's format -
 * renaming one silently resets the preference of everyone who had it selected.
 *
 * @see ThemeVariant
 * @see SystemThemeDetector
 */
public enum ThemeMode {

    /** Follow whatever the desktop is set to, re-checked every time the theme is applied. */
    SYSTEM("Use system setting"),

    LIGHT("Light"),

    DARK("Dark");

    /** The default for a fresh installation: matching the desktop is the least surprising start. */
    public static final ThemeMode DEFAULT = SYSTEM;

    private final String displayName;

    ThemeMode(String displayName) {
        this.displayName = displayName;
    }

    /**
     * @return the label to show in menus and settings, which is not the constant name because
     *         {@code SYSTEM} needs to read as an instruction rather than as a third colour
     */
    public String getDisplayName() {
        return displayName;
    }

    /**
     * @return the appearance this mode actually asks for right now; for {@link #SYSTEM} that
     *         means asking the desktop, and falling back to {@link ThemeVariant#LIGHT} when the
     *         desktop cannot be read, which is the appearance the app had before it had any
     */
    public ThemeVariant resolve() {
        return switch (this) {
            case LIGHT -> ThemeVariant.LIGHT;
            case DARK -> ThemeVariant.DARK;
            case SYSTEM -> SystemThemeDetector.detect().orElse(ThemeVariant.LIGHT);
        };
    }

    /**
     * Reads a mode back from its stored name, tolerating anything a hand-edited settings file
     * might contain rather than failing the whole startup over one bad line.
     *
     * @param name a stored constant name, possibly null, blank, differently cased or unknown
     * @return the matching mode, or {@link #DEFAULT} if the name means nothing here
     */
    public static ThemeMode fromName(String name) {
        if (name == null) {
            return DEFAULT;
        }
        for (ThemeMode mode : values()) {
            if (mode.name().equalsIgnoreCase(name.trim())) {
                return mode;
            }
        }
        return DEFAULT;
    }
}

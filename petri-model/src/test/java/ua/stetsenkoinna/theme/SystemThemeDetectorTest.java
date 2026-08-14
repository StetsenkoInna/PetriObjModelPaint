package ua.stetsenkoinna.theme;

import org.junit.Test;

import java.util.Optional;

import static org.junit.Assert.assertEquals;

/**
 * The three desktops' answers, parsed.
 *
 * <p>Only the parsing is covered, which is the point of it being a separate step: no CI machine
 * is a Windows, a Mac and a GNOME desktop at once, so the part that actually runs the commands
 * can only ever be exercised on one of them. What can be pinned everywhere is that each
 * desktop's output means what we think it means - including the two cases that are easy to get
 * backwards, since both are stated in the negative.
 */
public class SystemThemeDetectorTest {

    private static final String WINDOWS_DARK = """

            HKEY_CURRENT_USER\\Software\\Microsoft\\Windows\\CurrentVersion\\Themes\\Personalize
                AppsUseLightTheme    REG_DWORD    0x0
            """;

    private static final String WINDOWS_LIGHT = """

            HKEY_CURRENT_USER\\Software\\Microsoft\\Windows\\CurrentVersion\\Themes\\Personalize
                AppsUseLightTheme    REG_DWORD    0x1
            """;

    /**
     * The value is named for light, so 0 is dark - the one place in all of this where reading
     * the constant name and believing it gets the answer exactly backwards.
     */
    @Test
    public void windowsZeroMeansDark() {
        assertEquals(Optional.of(ThemeVariant.DARK),
                SystemThemeDetector.parseWindowsRegistry(WINDOWS_DARK));
        assertEquals(Optional.of(ThemeVariant.LIGHT),
                SystemThemeDetector.parseWindowsRegistry(WINDOWS_LIGHT));
    }

    /**
     * Windows writes several values into that key; only ours decides anything. A parser that
     * took the first REG_DWORD it saw would answer from SystemUsesLightTheme, which is the
     * taskbar's setting and can differ from the one applications are meant to follow.
     */
    @Test
    public void windowsReadsTheAppValueNotItsNeighbours() {
        String bothValues = """

                HKEY_CURRENT_USER\\Software\\Microsoft\\Windows\\CurrentVersion\\Themes\\Personalize
                    SystemUsesLightTheme    REG_DWORD    0x0
                    AppsUseLightTheme    REG_DWORD    0x1
                """;
        assertEquals(Optional.of(ThemeVariant.LIGHT),
                SystemThemeDetector.parseWindowsRegistry(bothValues));
    }

    @Test
    public void windowsWithoutTheValueIsUnknown() {
        assertEquals(Optional.empty(), SystemThemeDetector.parseWindowsRegistry(null));
        assertEquals(Optional.empty(), SystemThemeDetector.parseWindowsRegistry(
                "ERROR: The system was unable to find the specified registry key or value."));
    }

    /**
     * macOS defines AppleInterfaceStyle only while dark mode is on, so the command failing is
     * how light mode reports itself - an answer, not a failure to get one.
     */
    @Test
    public void macAbsentInterfaceStyleMeansLight() {
        assertEquals(Optional.of(ThemeVariant.DARK),
                SystemThemeDetector.parseMacInterfaceStyle("Dark\n"));
        assertEquals(Optional.of(ThemeVariant.LIGHT),
                SystemThemeDetector.parseMacInterfaceStyle(null));
    }

    @Test
    public void gnomeColorSchemeIsQuoted() {
        assertEquals(Optional.of(ThemeVariant.DARK),
                SystemThemeDetector.parseGnomeColorScheme("'prefer-dark'\n"));
        assertEquals(Optional.of(ThemeVariant.LIGHT),
                SystemThemeDetector.parseGnomeColorScheme("'prefer-light'\n"));
    }

    /**
     * 'default' is GNOME for "the user never said", which has to stay distinguishable from
     * "the user said light" - it is what sends the detector on to the GTK theme name.
     */
    @Test
    public void gnomeDefaultIsNotAnAnswer() {
        assertEquals(Optional.empty(), SystemThemeDetector.parseGnomeColorScheme("'default'\n"));
        assertEquals(Optional.empty(), SystemThemeDetector.parseGnomeColorScheme(null));
    }

    @Test
    public void gtkThemeNameEndingInDarkIsDark() {
        assertEquals(Optional.of(ThemeVariant.DARK),
                SystemThemeDetector.parseGtkThemeName("'Adwaita-dark'\n"));
        assertEquals(Optional.of(ThemeVariant.DARK),
                SystemThemeDetector.parseGtkThemeName("'Yaru-dark'\n"));
        assertEquals(Optional.of(ThemeVariant.LIGHT),
                SystemThemeDetector.parseGtkThemeName("'Adwaita'\n"));
        assertEquals(Optional.empty(), SystemThemeDetector.parseGtkThemeName(null));
    }
}

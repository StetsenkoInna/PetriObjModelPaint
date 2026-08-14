package ua.stetsenkoinna.graphpresentation.theme;

import java.awt.Color;
import java.awt.Window;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import javax.swing.SwingUtilities;
import javax.swing.UIDefaults;
import javax.swing.UIManager;
import javax.swing.plaf.ColorUIResource;
import javax.swing.plaf.nimbus.NimbusLookAndFeel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ua.stetsenkoinna.config.AppSettings;
import ua.stetsenkoinna.theme.CanvasPalette;
import ua.stetsenkoinna.theme.SystemThemeDetector;
import ua.stetsenkoinna.theme.ThemeMode;
import ua.stetsenkoinna.theme.ThemeVariant;

/**
 * Installs the application's appearance and switches it at runtime.
 *
 * <p>Three things have to move together for a theme change to look like one change rather than
 * three: the Swing look and feel, which paints the window chrome; {@link UiPalette}, for the
 * handful of chrome colours the app sets by hand and the look and feel therefore cannot touch;
 * and {@link CanvasPalette}, for the net drawing, which is not Swing components at all. This
 * class owns all three and applies them in one step.
 *
 * <h3>Why Nimbus for both</h3>
 * The editor has always run on Nimbus, and Nimbus derives nearly every colour it paints from a
 * dozen or so named base colours in its {@code UIDefaults}. That makes a dark Nimbus a matter of
 * replacing those bases rather than of adopting a second look and feel - so the light appearance
 * stays exactly what it has always been, byte for byte, and dark is genuinely an addition rather
 * than a restyling of the whole application.
 *
 * <h3>Switching at runtime</h3>
 * Nimbus computes its derived colours when it is installed, so a change of base colours is
 * applied by installing a fresh {@link NimbusLookAndFeel} rather than by editing the live one.
 * Every open window is then walked with {@link SwingUtilities#updateComponentTreeUI(java.awt.Component)},
 * and every registered listener is told, which is how the parts Swing cannot reach on its own -
 * hand-set backgrounds, the canvas - come along.
 */
public final class ThemeManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(ThemeManager.class);

    private static final List<ThemeChangeListener> LISTENERS = new CopyOnWriteArrayList<>();

    private static volatile ThemeMode currentMode = ThemeMode.DEFAULT;
    private static volatile ThemeVariant currentVariant = ThemeVariant.LIGHT;
    private static volatile UiPalette currentPalette = UiPalette.light();

    private ThemeManager() {
    }

    /**
     * Notified after the appearance has changed, on the event dispatch thread, so that a window
     * can re-apply whatever it colours itself and repaint.
     */
    public interface ThemeChangeListener {
        void themeChanged(ThemeVariant variant, UiPalette palette);
    }

    /**
     * Applies the theme the user last chose. Called once during startup, before the first window
     * exists.
     */
    public static void applySavedMode() {
        applyMode(AppSettings.shared().getThemeMode());
    }

    /**
     * Applies {@code mode} and remembers it, which is what a choice made in the menu or in the
     * settings dialog does.
     */
    public static void selectMode(ThemeMode mode) {
        AppSettings.shared().setThemeMode(mode);
        applyMode(mode);
    }

    /**
     * Applies {@code mode} without persisting it - used by the settings dialog to preview a
     * choice the user has not confirmed yet, and by tests.
     *
     * <p>Safe to call before any window exists, which is the startup case, and from the event
     * dispatch thread, which is every other case. Called from a background thread while windows
     * are showing, the Swing part is posted to the event dispatch thread rather than done here.
     */
    public static void applyMode(ThemeMode mode) {
        if (mode == ThemeMode.SYSTEM) {
            // Re-asked rather than answered once at startup, so a user who changes their desktop
            // to dark and then picks "Use system setting" again gets the answer they expect.
            // Only for SYSTEM: the other two need no probe, and probing costs a process.
            SystemThemeDetector.refresh();
        }
        ThemeVariant variant = mode.resolve();
        currentMode = mode;
        currentVariant = variant;
        currentPalette = UiPalette.of(variant);
        CanvasPalette.install(CanvasPalette.of(variant));
        LOGGER.info("Applying theme mode {} (resolved to {})", mode, variant);

        if (SwingUtilities.isEventDispatchThread() || Window.getWindows().length == 0) {
            installLookAndFeel(variant);
            refreshOpenWindows(variant);
        } else {
            SwingUtilities.invokeLater(() -> {
                installLookAndFeel(variant);
                refreshOpenWindows(variant);
            });
        }
    }

    /**
     * @return the mode the user chose, which may still be {@link ThemeMode#SYSTEM}
     */
    public static ThemeMode currentMode() {
        return currentMode;
    }

    /**
     * @return the appearance actually in force
     */
    public static ThemeVariant currentVariant() {
        return currentVariant;
    }

    /**
     * @return the chrome colours for the appearance in force
     */
    public static UiPalette palette() {
        return currentPalette;
    }

    /**
     * Registers {@code listener} and immediately calls it with the current theme, so a window
     * built during startup does not have to colour itself once in its constructor and again on
     * the first change.
     */
    public static void addListener(ThemeChangeListener listener) {
        LISTENERS.add(listener);
        listener.themeChanged(currentVariant, currentPalette);
    }

    public static void removeListener(ThemeChangeListener listener) {
        LISTENERS.remove(listener);
    }

    /**
     * Installs Nimbus with the palette for {@code variant}.
     *
     * <p>The colours go in twice, on purpose, because Nimbus reads them at two different moments.
     * Some of what it paints resolves its colours on every paint, and picks up a change made to
     * the live defaults table; the rest - text field and button backgrounds among them - is baked
     * into painters when the look and feel is constructed, and never looks again. Setting the
     * overrides only afterwards leaves exactly those white in a dark window, which is a strange
     * half-applied theme rather than an obvious failure. So: into {@link UIManager} first, where
     * a freshly constructed Nimbus will read them, then into the installed table, for everything
     * that resolves later.
     *
     * <p>The first pass clears every key the dark palette defines before writing, so switching
     * back to light genuinely returns to stock Nimbus instead of to light-with-dark-leftovers.
     */
    private static void installLookAndFeel(ThemeVariant variant) {
        try {
            Map<String, Object> darkPalette = darkDefaults();
            Map<String, Object> overrides = variant.isDark() ? darkPalette : Map.of();
            for (String key : darkPalette.keySet()) {
                UIManager.put(key, null);
            }
            overrides.forEach(UIManager::put);

            // A fresh instance every time: Nimbus derives its colours from the defaults it is
            // constructed with, so reusing the live one would keep the previous theme's.
            UIManager.setLookAndFeel(new NimbusLookAndFeel());

            UIDefaults defaults = UIManager.getLookAndFeelDefaults();
            overrides.forEach(defaults::put);
        } catch (Exception ex) {
            // Nimbus ships with the JDK, so this is close to unreachable - but an appearance is
            // not worth failing a startup over, and the platform default still draws a usable
            // window.
            LOGGER.error("Could not install the {} look and feel; keeping the current one",
                    variant, ex);
        }
    }

    private static void refreshOpenWindows(ThemeVariant variant) {
        for (Window window : Window.getWindows()) {
            SwingUtilities.updateComponentTreeUI(window);
        }
        for (ThemeChangeListener listener : LISTENERS) {
            listener.themeChanged(variant, currentPalette);
        }
        for (Window window : Window.getWindows()) {
            window.invalidate();
            window.validate();
            window.repaint();
        }
    }

    /**
     * The dark Nimbus palette.
     *
     * <p>The first block is Nimbus's own base colours: everything Nimbus paints is derived from
     * these, so replacing them is what actually turns the look and feel dark. The second block
     * names individual components, and exists because Nimbus paints most component backgrounds
     * through painters rather than through the {@code X.background} keys - the keys still matter
     * for the components that read them directly (text components, lists, tables) and for the
     * ones the app queries through {@link UIManager}, so both are set, to the same values, and
     * nothing is left to whichever mechanism happens to win.
     */
    private static Map<String, Object> darkDefaults() {
        Color chrome = new Color(0x3C, 0x3F, 0x41);
        Color chromeDark = new Color(0x33, 0x36, 0x3A);
        Color field = new Color(0x2B, 0x2E, 0x30);
        Color text = new Color(0xE0, 0xE3, 0xE6);
        // Lighter than a straight inversion of the light theme's disabled grey would give. On a
        // dark chrome the usual value lands around 2:1 against its background, which is legible
        // as "greyed out" and barely legible as text - and the settings dialog uses disabled
        // labels for the line of explanation under each choice, which is meant to be read.
        Color disabledText = new Color(0x97, 0x9C, 0x9F);
        Color selection = new Color(0x3A, 0x6E, 0xA5);
        Color border = new Color(0x56, 0x5A, 0x5D);

        Map<String, Object> defaults = new LinkedHashMap<>();

        // Nimbus base colours - the ones every derived colour hangs off.
        defaults.put("control", chrome);
        defaults.put("info", chrome);
        defaults.put("nimbusBase", new Color(0x2B, 0x3A, 0x4A));
        defaults.put("nimbusBlueGrey", new Color(0x4A, 0x4E, 0x51));
        defaults.put("nimbusBorder", border);
        defaults.put("nimbusAlertYellow", new Color(0xF8, 0xBB, 0x00));
        defaults.put("nimbusDisabledText", disabledText);
        defaults.put("nimbusFocus", new Color(0x4A, 0x90, 0xD9));
        defaults.put("nimbusGreen", new Color(0x5A, 0x9E, 0x48));
        defaults.put("nimbusInfoBlue", new Color(0x3E, 0x86, 0xC7));
        defaults.put("nimbusLightBackground", field);
        defaults.put("nimbusOrange", new Color(0xC9, 0x7B, 0x22));
        defaults.put("nimbusRed", new Color(0xC7, 0x54, 0x50));
        defaults.put("nimbusSelectedText", Color.WHITE);
        defaults.put("nimbusSelectionBackground", selection);
        defaults.put("text", text);
        defaults.put("menu", chrome);
        defaults.put("menuText", text);
        defaults.put("scrollbar", new Color(0x45, 0x49, 0x4B));
        defaults.put("controlText", text);
        defaults.put("controlHighlight", new Color(0x4A, 0x4E, 0x51));
        defaults.put("controlLHighlight", new Color(0x4A, 0x4E, 0x51));
        defaults.put("controlShadow", field);
        defaults.put("controlDkShadow", new Color(0x1E, 0x20, 0x22));
        defaults.put("textForeground", text);
        defaults.put("textBackground", field);
        defaults.put("textHighlight", selection);
        defaults.put("textHighlightText", Color.WHITE);
        defaults.put("textInactiveText", disabledText);
        defaults.put("activeCaption", chrome);
        defaults.put("inactiveCaption", chromeDark);
        defaults.put("desktop", new Color(0x23, 0x26, 0x28));

        // Per-component keys, for the components that read them instead of the painters.
        for (String textComponent : List.of("TextField", "FormattedTextField", "PasswordField",
                "TextArea", "TextPane", "EditorPane")) {
            defaults.put(textComponent + ".background", field);
            defaults.put(textComponent + ".foreground", text);
            defaults.put(textComponent + ".caretForeground", text);
            defaults.put(textComponent + ".inactiveBackground", chromeDark);
            defaults.put(textComponent + ".inactiveForeground", disabledText);
            defaults.put(textComponent + ".selectionBackground", selection);
            defaults.put(textComponent + ".selectionForeground", Color.WHITE);
        }
        defaults.put("List.background", field);
        defaults.put("List.foreground", text);
        defaults.put("Table.background", field);
        defaults.put("Table.foreground", text);
        defaults.put("Table.alternateRowColor", new Color(0x31, 0x34, 0x36));
        defaults.put("Table.gridColor", border);
        defaults.put("TableHeader.background", chrome);
        defaults.put("TableHeader.foreground", text);
        defaults.put("Tree.background", field);
        defaults.put("Tree.textBackground", field);
        defaults.put("Tree.textForeground", text);
        defaults.put("Viewport.background", field);
        defaults.put("ScrollPane.background", chrome);
        defaults.put("Panel.background", chrome);
        defaults.put("SplitPane.background", chrome);
        defaults.put("TabbedPane.background", chrome);
        defaults.put("TabbedPane.foreground", text);
        defaults.put("ToolBar.background", chrome);
        defaults.put("OptionPane.background", chrome);
        defaults.put("OptionPane.messageForeground", text);
        defaults.put("ToolTip.background", chromeDark);
        defaults.put("ToolTip.foreground", text);
        defaults.put("Separator.foreground", border);
        defaults.put("ComboBox.background", field);
        defaults.put("ComboBox.foreground", text);
        defaults.put("Label.foreground", text);
        defaults.put("Button.foreground", text);
        defaults.put("ToggleButton.foreground", text);
        defaults.put("CheckBox.foreground", text);
        defaults.put("RadioButton.foreground", text);
        defaults.put("TitledBorder.titleColor", text);
        defaults.put("MenuBar.background", chrome);
        defaults.put("MenuBar.foreground", text);
        for (String menuComponent : List.of("Menu", "MenuItem", "PopupMenu", "CheckBoxMenuItem",
                "RadioButtonMenuItem")) {
            defaults.put(menuComponent + ".background", chrome);
            defaults.put(menuComponent + ".foreground", text);
            // Nimbus reads a menu's label colour from its own per-state keys and ignores the
            // plain ".foreground" above, which is why the titles in the menu bar stayed dark
            // while the popups underneath them had already turned light.
            defaults.put(menuComponent + "[Enabled].textForeground", text);
            defaults.put(menuComponent + "[Disabled].textForeground", disabledText);
            defaults.put(menuComponent + "[Enabled+Selected].textForeground", Color.WHITE);
            defaults.put(menuComponent + "[MouseOver].textForeground", Color.WHITE);
        }
        defaults.put("MenuItem:MenuItemAccelerator[Enabled].textForeground", disabledText);
        defaults.put("MenuItem:MenuItemAccelerator[MouseOver].textForeground", Color.WHITE);
        // A menu sitting in the menu bar is a different Nimbus region from the same menu nested
        // in a popup, and takes its label colour from its own prefixed keys. Without these the
        // titles across the top stay near-black while every popup under them is already light -
        // which is the one part of the window a user cannot avoid looking at.
        defaults.put("MenuBar:Menu[Enabled].textForeground", text);
        defaults.put("MenuBar:Menu[Disabled].textForeground", disabledText);
        defaults.put("MenuBar:Menu[Selected].textForeground", Color.WHITE);
        defaults.put("MenuBar:Menu[Enabled+Selected].textForeground", Color.WHITE);
        // JFileChooser reads a few of its own, and is one of the few dialogs the app opens that
        // it does not build itself.
        defaults.put("FileChooser.background", chrome);
        defaults.put("FileChooser.foreground", text);

        return wrapAsResources(defaults);
    }

    /**
     * Wraps each colour as a {@link ColorUIResource}, which is how Swing tells a colour that came
     * from the look and feel - and may therefore be replaced on the next theme change - from one
     * the application set deliberately, which must not be.
     */
    private static Map<String, Object> wrapAsResources(Map<String, Object> raw) {
        Map<String, Object> wrapped = new LinkedHashMap<>();
        raw.forEach((key, color) -> wrapped.put(key, new ColorUIResource((Color) color)));
        return wrapped;
    }
}

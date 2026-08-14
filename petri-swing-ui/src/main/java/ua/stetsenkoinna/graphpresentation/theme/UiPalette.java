package ua.stetsenkoinna.graphpresentation.theme;

import java.awt.Color;
import java.util.Objects;
import ua.stetsenkoinna.theme.ThemeVariant;

/**
 * The handful of window-chrome colours the editor sets by hand instead of leaving to the look
 * and feel - the menu bar's blue, the grey behind the toolbars, the chart panel's near-white.
 *
 * <p>These are the ones a look and feel swap cannot reach: a component whose background was set
 * explicitly keeps it forever, because {@code setBackground} marks the value as the
 * application's rather than the theme's and {@code updateComponentTreeUI} will not overwrite it.
 * So every such site reads its colour from here and is re-applied when the theme changes.
 *
 * <p>Distinct from {@link ua.stetsenkoinna.theme.CanvasPalette}, which covers the net drawing.
 * That one lives in the model module because the painters do; this one is only ever needed
 * where Swing components are, so it stays here.
 *
 * @see ThemeManager#addListener(ThemeManager.ThemeChangeListener)
 */
public final class UiPalette {

    private final ThemeVariant variant;
    private final Color menuBarBackground;
    private final Color menuBarForeground;
    private final Color chrome;
    private final Color chromeAlt;
    private final Color chromeText;
    private final Color surface;
    private final Color activeControl;
    private final Color divider;

    private UiPalette(ThemeVariant variant, Color menuBarBackground, Color menuBarForeground,
                      Color chrome, Color chromeAlt, Color chromeText, Color surface,
                      Color activeControl, Color divider) {
        this.variant = variant;
        this.menuBarBackground = menuBarBackground;
        this.menuBarForeground = menuBarForeground;
        this.chrome = chrome;
        this.chromeAlt = chromeAlt;
        this.chromeText = chromeText;
        this.surface = surface;
        this.activeControl = activeControl;
        this.divider = divider;
    }

    /**
     * @return the palette for {@code variant}; light reproduces the literals that were spelled
     *         out at each call site before this class existed
     */
    public static UiPalette of(ThemeVariant variant) {
        return Objects.requireNonNull(variant).isDark() ? dark() : light();
    }

    public static UiPalette light() {
        return new UiPalette(
                ThemeVariant.LIGHT,
                new Color(186, 213, 241),
                new Color(98, 147, 167),
                new Color(238, 238, 238),
                new Color(229, 229, 229),
                Color.BLACK,
                new Color(0xF4, 0xF4, 0xF4),
                Color.LIGHT_GRAY,
                new Color(200, 200, 200));
    }

    public static UiPalette dark() {
        return new UiPalette(
                ThemeVariant.DARK,
                new Color(0x2F, 0x3B, 0x47),
                new Color(0xB8, 0xC7, 0xD6),
                new Color(0x3C, 0x3F, 0x41),
                new Color(0x33, 0x36, 0x3A),
                new Color(0xE0, 0xE3, 0xE6),
                new Color(0x2B, 0x2E, 0x30),
                new Color(0x4A, 0x4F, 0x52),
                new Color(0x4A, 0x4E, 0x51));
    }

    public ThemeVariant getVariant() {
        return variant;
    }

    public Color getMenuBarBackground() {
        return menuBarBackground;
    }

    public Color getMenuBarForeground() {
        return menuBarForeground;
    }

    /** Background of the toolbars and the parameter panels around the canvas. */
    public Color getChrome() {
        return chrome;
    }

    /** A half-step darker than {@link #getChrome()}, for panels that sit inside another one. */
    public Color getChromeAlt() {
        return chromeAlt;
    }

    /** Text drawn on {@link #getChrome()} and {@link #getChromeAlt()}. */
    public Color getChromeText() {
        return chromeText;
    }

    /** Background of content areas that are not the canvas: charts, the error report. */
    public Color getSurface() {
        return surface;
    }

    /** Background a toggle-style tool button takes while its tool is the active one. */
    public Color getActiveControl() {
        return activeControl;
    }

    /** The hairline rule between the panels around the canvas. */
    public Color getDivider() {
        return divider;
    }
}

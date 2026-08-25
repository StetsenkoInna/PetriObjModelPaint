package ua.stetsenkoinna.theme;

import static ua.stetsenkoinna.theme.CanvasColor.ACCENT;
import static ua.stetsenkoinna.theme.CanvasColor.ANIMATION_ACTIVE;
import static ua.stetsenkoinna.theme.CanvasColor.ANIMATION_CROSSING;
import static ua.stetsenkoinna.theme.CanvasColor.CANVAS_BACKGROUND;
import static ua.stetsenkoinna.theme.CanvasColor.COLLAPSED_FRAME_FILL;
import static ua.stetsenkoinna.theme.CanvasColor.ELEMENT_FILL;
import static ua.stetsenkoinna.theme.CanvasColor.ELEMENT_STROKE;
import static ua.stetsenkoinna.theme.CanvasColor.FRAME_BODY;
import static ua.stetsenkoinna.theme.CanvasColor.FRAME_BODY_SELECTED;
import static ua.stetsenkoinna.theme.CanvasColor.FRAME_BORDER;
import static ua.stetsenkoinna.theme.CanvasColor.FRAME_BORDER_SELECTED;
import static ua.stetsenkoinna.theme.CanvasColor.FRAME_HEADER;
import static ua.stetsenkoinna.theme.CanvasColor.FRAME_HEADER_SELECTED;
import static ua.stetsenkoinna.theme.CanvasColor.FRAME_TEXT;
import static ua.stetsenkoinna.theme.CanvasColor.LINKED_PLACE_FILL;
import static ua.stetsenkoinna.theme.CanvasColor.CONNECTOR_STRAND;
import static ua.stetsenkoinna.theme.CanvasColor.FUSION_RING_SELECTED;
import static ua.stetsenkoinna.theme.CanvasColor.GUIDE;
import static ua.stetsenkoinna.theme.CanvasColor.PORT_BORDER;
import static ua.stetsenkoinna.theme.CanvasColor.PORT_FILL_PLACE;
import static ua.stetsenkoinna.theme.CanvasColor.PORT_FILL_TRANSITION;
import static ua.stetsenkoinna.theme.CanvasColor.PORT_HIGHLIGHT;
import static ua.stetsenkoinna.theme.CanvasColor.PORT_LABEL_BACKDROP;
import static ua.stetsenkoinna.theme.CanvasColor.SELECTION;

import java.awt.Color;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

/**
 * Every colour the net drawing uses, in one place, so that switching the application between
 * light and dark is a matter of swapping one object rather than hunting literals through the
 * painters.
 *
 * <p>The look and feel handles the window chrome; it has no say over a {@code Graphics2D} the
 * canvas paints into by hand, which is what all of these are for. A palette is immutable; the
 * theme changes by installing a different one through {@link #install(CanvasPalette)}, which is
 * the only mutable state here and is read on the event dispatch thread during paint.
 *
 * <h3>Why the light palette repeats the old literals exactly</h3>
 * The light values are the colours the editor has always drawn with, character for character.
 * A theme feature that quietly restyles the existing appearance is a different, larger change
 * than adding a second appearance next to it, so light is pinned rather than "improved".
 *
 * @see #strokeFor(Color) for how colours already stored on saved elements are handled
 */
public final class CanvasPalette {

    /**
     * The palette in force. Written when the theme is applied, read from every paint - hence
     * volatile, and hence never null: the field starts on light so a canvas painted before any
     * theme has been applied (a unit test, a tool that never calls the theme manager) still has
     * the appearance the editor had before this class existed.
     */
    private static volatile CanvasPalette current = light();

    private final ThemeVariant variant;
    private final Map<CanvasColor, Color> colors;

    private CanvasPalette(ThemeVariant variant, Map<CanvasColor, Color> colors) {
        this.variant = variant;
        // Every role, or none: a palette missing one is a palette that paints something in null,
        // and the theme it belongs to is exactly the one nobody was looking at when it was added.
        for (CanvasColor role : CanvasColor.values()) {
            if (colors.get(role) == null) {
                throw new IllegalArgumentException(
                        "The " + variant + " canvas palette has no colour for " + role);
            }
        }
        this.colors = new EnumMap<>(colors);
    }

    /**
     * @return the palette every painter should draw with, never null
     */
    public static CanvasPalette current() {
        return current;
    }

    /**
     * Makes {@code palette} the one {@link #current()} returns. Called by the theme manager; a
     * repaint of every canvas has to follow, which is the caller's job.
     */
    public static void install(CanvasPalette palette) {
        current = Objects.requireNonNull(palette, "palette");
    }

    /**
     * @return the palette for {@code variant}, the only two that exist
     */
    public static CanvasPalette of(ThemeVariant variant) {
        return variant.isDark() ? dark() : light();
    }

    /**
     * The appearance the editor has always had, unchanged.
     */
    public static CanvasPalette light() {
        Map<CanvasColor, Color> c = new EnumMap<>(CanvasColor.class);
        c.put(CANVAS_BACKGROUND, Color.WHITE);
        c.put(ELEMENT_FILL, Color.WHITE);
        c.put(ELEMENT_STROKE, Color.BLACK);
        c.put(SELECTION, Color.BLUE);
        c.put(GUIDE, Color.GRAY);
        c.put(ACCENT, new Color(0x33, 0x5A, 0x8A));
        c.put(FRAME_BORDER, new Color(0x33, 0x5A, 0x8A));
        c.put(FRAME_BORDER_SELECTED, new Color(0x1E, 0x8E, 0x3E));
        c.put(FRAME_BODY, new Color(0xF8, 0xFA, 0xFD, 0x80));
        c.put(FRAME_BODY_SELECTED, new Color(0x1E, 0x8E, 0x3E, 0x1F));
        c.put(FRAME_HEADER, new Color(0xE4, 0xEC, 0xF7));
        c.put(FRAME_HEADER_SELECTED, new Color(0xD8, 0xEF, 0xDC));
        c.put(FRAME_TEXT, new Color(0x1C, 0x2B, 0x3A));
        c.put(COLLAPSED_FRAME_FILL, Color.WHITE);
        c.put(PORT_FILL_PLACE, new Color(0xFF, 0xFF, 0xFF));
        c.put(PORT_FILL_TRANSITION, new Color(0x33, 0x5A, 0x8A));
        c.put(PORT_BORDER, new Color(0x1C, 0x2B, 0x3A));
        c.put(PORT_HIGHLIGHT, new Color(0xD9, 0x7A, 0x00));
        c.put(PORT_LABEL_BACKDROP, new Color(255, 255, 255, 210));
        c.put(LINKED_PLACE_FILL, new Color(0xD6, 0xD8, 0xDB));
        c.put(FUSION_RING_SELECTED, new Color(0xD9, 0x7A, 0x00));
        // The same hue as the picked strand, held well back from it.
        c.put(CONNECTOR_STRAND, new Color(0xE8, 0xB0, 0x66));
        c.put(ANIMATION_ACTIVE, new Color(255, 77, 77));
        c.put(ANIMATION_CROSSING, new Color(60, 120, 220));
        return new CanvasPalette(ThemeVariant.LIGHT, c);
    }

    /**
     * The dark counterpart. Chosen so that each role keeps the reading it has in light rather
     * than merely being inverted: outlines stay the highest-contrast thing on the canvas, the
     * selection stays blue, a fused place stays green, an active animation stays red, and the
     * fill still matches the background so a shape still occludes what runs beneath it.
     */
    public static CanvasPalette dark() {
        Color canvas = new Color(0x23, 0x26, 0x28);
        Map<CanvasColor, Color> c = new EnumMap<>(CanvasColor.class);
        c.put(CANVAS_BACKGROUND, canvas);
        c.put(ELEMENT_FILL, canvas);
        c.put(ELEMENT_STROKE, new Color(0xE8, 0xEA, 0xED));
        c.put(SELECTION, new Color(0x6E, 0xA8, 0xFE));
        c.put(GUIDE, new Color(0x8A, 0x90, 0x99));
        c.put(ACCENT, new Color(0x5C, 0x93, 0xC8));
        c.put(FRAME_BORDER, new Color(0x5C, 0x93, 0xC8));
        c.put(FRAME_BORDER_SELECTED, new Color(0x53, 0xC0, 0x71));
        c.put(FRAME_BODY, new Color(0x2C, 0x31, 0x38, 0x80));
        c.put(FRAME_BODY_SELECTED, new Color(0x53, 0xC0, 0x71, 0x24));
        c.put(FRAME_HEADER, new Color(0x2F, 0x39, 0x44));
        c.put(FRAME_HEADER_SELECTED, new Color(0x27, 0x39, 0x2C));
        c.put(FRAME_TEXT, new Color(0xD7, 0xDE, 0xE7));
        c.put(COLLAPSED_FRAME_FILL, new Color(0x2A, 0x2E, 0x31));
        c.put(PORT_FILL_PLACE, new Color(0x2A, 0x2E, 0x31));
        c.put(PORT_FILL_TRANSITION, new Color(0x5C, 0x93, 0xC8));
        c.put(PORT_BORDER, new Color(0xC7, 0xCE, 0xD6));
        c.put(PORT_HIGHLIGHT, new Color(0xE5, 0x9A, 0x2E));
        c.put(PORT_LABEL_BACKDROP, new Color(0x23, 0x26, 0x28, 210));
        c.put(LINKED_PLACE_FILL, new Color(0x3C, 0x41, 0x48));
        c.put(FUSION_RING_SELECTED, new Color(0xE5, 0x9A, 0x2E));
        c.put(CONNECTOR_STRAND, new Color(0x9A, 0x6B, 0x2A));
        c.put(ANIMATION_ACTIVE, new Color(0xFF, 0x6B, 0x6B));
        c.put(ANIMATION_CROSSING, new Color(0x5C, 0x93, 0xE8));
        return new CanvasPalette(ThemeVariant.DARK, c);
    }

    /**
     * @param role which part of the drawing is being painted
     * @return the colour to paint it in, never null
     */
    public Color get(CanvasColor role) {
        return colors.get(role);
    }

    /**
     * Translates a colour that is stored on an element into the colour to draw it with.
     *
     * <p>Elements carry their own {@link java.awt.Color}, and two values of it are not colours at
     * all but states the editor writes and reads back: black means "not selected" and blue means
     * "selected". Those values are also inside every {@code .pns} on disk, since the graph model
     * is Java-serialized - so a file saved years ago arrives with a literal black in it, and no
     * amount of changing the code that assigns them can reach it. Resolving at paint time is the
     * only place that covers both.
     *
     * <p>Anything else passes through untouched, which is what keeps a running animation's red
     * red while it is being painted over an element that has been recoloured for it.
     *
     * @param stored the colour on the element, possibly null on an element never assigned one
     * @return the colour to hand to {@code Graphics2D}
     */
    public Color strokeFor(Color stored) {
        if (stored == null || Color.BLACK.equals(stored)) {
            return colors.get(ELEMENT_STROKE);
        }
        if (Color.BLUE.equals(stored)) {
            return colors.get(SELECTION);
        }
        return stored;
    }

    public ThemeVariant getVariant() {
        return variant;
    }

    public boolean isDark() {
        return variant.isDark();
    }
}

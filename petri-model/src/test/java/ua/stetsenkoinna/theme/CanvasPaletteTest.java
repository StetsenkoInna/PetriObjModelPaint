package ua.stetsenkoinna.theme;

import org.junit.Test;

import java.awt.Color;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/**
 * What both palettes have to keep true regardless of what anyone retunes later.
 */
public class CanvasPaletteTest {

    /**
     * The light palette is the appearance the editor shipped with, and this feature adds a
     * second appearance rather than restyling the first. These are the exact literals that were
     * spelled out at the call sites before the palette existed - if a change to them is ever
     * deliberate, this test is the place that says so out loud.
     */
    @Test
    public void lightPaletteStillDrawsTheOriginalColours() {
        CanvasPalette light = CanvasPalette.light();
        assertEquals(Color.WHITE, light.get(CanvasColor.CANVAS_BACKGROUND));
        assertEquals(Color.WHITE, light.get(CanvasColor.ELEMENT_FILL));
        assertEquals(Color.BLACK, light.get(CanvasColor.ELEMENT_STROKE));
        assertEquals(Color.BLUE, light.get(CanvasColor.SELECTION));
        assertEquals(Color.GRAY, light.get(CanvasColor.GUIDE));
        assertEquals(new Color(0x33, 0x5A, 0x8A), light.get(CanvasColor.FRAME_BORDER));
        assertEquals(new Color(0x1E, 0x8E, 0x3E), light.get(CanvasColor.FRAME_BORDER_SELECTED));
        assertEquals(new Color(0xE4, 0xEC, 0xF7), light.get(CanvasColor.FRAME_HEADER));
        assertEquals(new Color(0x1C, 0x2B, 0x3A), light.get(CanvasColor.FRAME_TEXT));
        assertEquals(new Color(0x1B, 0x7F, 0x3B), light.get(CanvasColor.FUSION_RING));
        assertEquals(new Color(0xD9, 0x7A, 0x00), light.get(CanvasColor.PORT_HIGHLIGHT));
        assertEquals(new Color(255, 77, 77), light.get(CanvasColor.ANIMATION_ACTIVE));
        assertEquals(new Color(60, 120, 220), light.get(CanvasColor.ANIMATION_CROSSING));
    }

    /**
     * A role added to the enum but given a value in only one theme would otherwise be a null
     * handed to {@code Graphics2D}, in whichever theme the author was not looking at.
     */
    @Test
    public void bothPalettesDefineEveryRole() {
        for (ThemeVariant variant : ThemeVariant.values()) {
            CanvasPalette palette = CanvasPalette.of(variant);
            for (CanvasColor role : CanvasColor.values()) {
                assertNotNull(variant + " palette has no colour for " + role, palette.get(role));
            }
        }
    }

    /**
     * A shape's fill is what hides whatever passes beneath it, so it has to be the background's
     * exact colour and fully opaque - a fill a shade off reads as a halo around every place.
     */
    @Test
    public void elementFillMatchesTheCanvasItSitsOn() {
        for (ThemeVariant variant : ThemeVariant.values()) {
            CanvasPalette palette = CanvasPalette.of(variant);
            assertEquals(variant + " element fill must match the canvas",
                    palette.get(CanvasColor.CANVAS_BACKGROUND), palette.get(CanvasColor.ELEMENT_FILL));
            assertEquals(variant + " element fill must be opaque",
                    255, palette.get(CanvasColor.ELEMENT_FILL).getAlpha());
        }
    }

    /**
     * Black and blue are not colours on a saved element, they are "not selected" and "selected" -
     * and every {@code .pns} written before this feature existed has literal black inside it, so
     * the translation has to happen when the element is painted rather than when it is assigned.
     */
    @Test
    public void storedSelectionStatesResolveToTheThemesColours() {
        CanvasPalette dark = CanvasPalette.dark();
        assertEquals(dark.get(CanvasColor.ELEMENT_STROKE), dark.strokeFor(Color.BLACK));
        assertEquals(dark.get(CanvasColor.ELEMENT_STROKE), dark.strokeFor(null));
        assertEquals(dark.get(CanvasColor.SELECTION), dark.strokeFor(Color.BLUE));
    }

    /**
     * An animation recolours elements outright, and those colours are the point - passing them
     * through the same translation would repaint a firing transition as an idle one.
     */
    @Test
    public void anyOtherStoredColourIsLeftAlone() {
        CanvasPalette dark = CanvasPalette.dark();
        Color animating = dark.get(CanvasColor.ANIMATION_ACTIVE);
        assertSame(animating, dark.strokeFor(animating));
        assertEquals(Color.MAGENTA, dark.strokeFor(Color.MAGENTA));
    }

    /**
     * The outline is the highest-contrast thing on the canvas in both themes; a dark palette
     * that kept black outlines would be unreadable, which is the whole failure mode this
     * feature exists to avoid.
     */
    @Test
    public void darkPaletteInvertsTheContrast() {
        CanvasPalette dark = CanvasPalette.dark();
        Color background = dark.get(CanvasColor.CANVAS_BACKGROUND);
        Color stroke = dark.get(CanvasColor.ELEMENT_STROKE);
        assertTrue("dark canvas should be dark", luminance(background) < 0.2);
        assertTrue("dark outlines should be light", luminance(stroke) > 0.7);
        assertNotEquals(CanvasPalette.light().get(CanvasColor.CANVAS_BACKGROUND), background);
    }

    /** Rough perceived brightness, 0 (black) to 1 (white). */
    private static double luminance(Color color) {
        return (0.2126 * color.getRed() + 0.7152 * color.getGreen() + 0.0722 * color.getBlue()) / 255d;
    }
}

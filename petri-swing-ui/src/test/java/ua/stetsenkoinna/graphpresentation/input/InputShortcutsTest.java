package ua.stetsenkoinna.graphpresentation.input;

import org.junit.Test;

import java.awt.event.InputEvent;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * That the shortcut modifier really does change with the platform.
 *
 * <p>These go through the injectable overloads on purpose. The macOS branch is the one that was
 * broken and the one nobody developing on Windows can otherwise execute, so it has to be
 * reachable without actually being on a Mac - otherwise this is precisely the code that gets
 * "fixed" and then ships still broken, which is how it got here in the first place.
 */
public class InputShortcutsTest {

    @Test
    public void macUsesCommandAndEverythingElseUsesControl() {
        assertEquals(InputEvent.META_DOWN_MASK, InputShortcuts.menuMask(true));
        assertEquals(InputEvent.CTRL_DOWN_MASK, InputShortcuts.menuMask(false));
    }

    @Test
    public void recognisesTheNamesMacOsActuallyReportsItselfBy() {
        assertTrue(InputShortcuts.isMac("Mac OS X"));
        assertTrue(InputShortcuts.isMac("macOS"));
        assertTrue(InputShortcuts.isMac("Darwin"));
        assertFalse(InputShortcuts.isMac("Windows 11"));
        assertFalse(InputShortcuts.isMac("Linux"));
        assertFalse(InputShortcuts.isMac(""));
        assertFalse(InputShortcuts.isMac(null));
    }

    /**
     * The exact confusion the old {@code event.isControlDown()} made: on a Mac the user holds
     * Command, so Control is down in neither event, and Copy simply never fired.
     */
    @Test
    public void commandCountsOnMacAndControlDoesNot() {
        assertTrue(InputShortcuts.hasMenuMask(InputEvent.META_DOWN_MASK, true));
        assertFalse(InputShortcuts.hasMenuMask(InputEvent.CTRL_DOWN_MASK, true));

        assertTrue(InputShortcuts.hasMenuMask(InputEvent.CTRL_DOWN_MASK, false));
        assertFalse(InputShortcuts.hasMenuMask(InputEvent.META_DOWN_MASK, false));
    }

    /**
     * Holding Shift as well does not stop a command being that command - a menu accelerator
     * behaves the same way, and the two routes to Copy should not disagree.
     */
    @Test
    public void otherModifiersDoNotCancelTheShortcut() {
        int windowsCopyWithShift = InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK;
        assertTrue(InputShortcuts.hasMenuMask(windowsCopyWithShift, false));

        int macCopyWithShift = InputEvent.META_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK;
        assertTrue(InputShortcuts.hasMenuMask(macCopyWithShift, true));
    }

    @Test
    public void noModifierIsNeverAShortcut() {
        assertFalse(InputShortcuts.hasMenuMask(0, true));
        assertFalse(InputShortcuts.hasMenuMask(0, false));
    }

    @Test
    public void shiftMenuMaskCombinesBoth() {
        int combined = InputShortcuts.shiftMenuMask();
        assertTrue((combined & InputEvent.SHIFT_DOWN_MASK) != 0);
        assertTrue((combined & InputShortcuts.menuMask()) != 0);
    }
}

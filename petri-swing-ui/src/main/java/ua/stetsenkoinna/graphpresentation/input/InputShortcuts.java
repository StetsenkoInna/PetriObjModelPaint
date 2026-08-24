package ua.stetsenkoinna.graphpresentation.input;

import java.awt.event.InputEvent;
import java.util.Locale;

/**
 * Where the editor decides what "the shortcut key" means on the machine it is running on.
 *
 * <p>Every desktop platform has one modifier that prefixes application commands, and it is not
 * the same key: Control on Windows and Linux, Command on macOS. Swing will not pick for you -
 * {@code KeyStroke.getKeyStroke(VK_Z, CTRL_DOWN_MASK)} means the literal Control key everywhere,
 * so a menu built that way shows {@code ^Z} to a Mac user and ignores the ⌘Z they actually press.
 * Routing every binding through {@link #menuMask()} is what makes one source of shortcuts behave
 * natively on both.
 *
 * <p>The platform is read from {@code os.name} rather than from
 * {@code Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx()}, which would be the obvious
 * choice but throws {@code HeadlessException} without a display. Reading the property keeps this
 * usable from a headless build - and, through {@link #isMac(String)}, lets the macOS branch be
 * tested on a machine that is not a Mac, which is the only way this class gets covered at all
 * given where it is developed.
 */
public final class InputShortcuts {

    private InputShortcuts() {
    }

    /**
     * @return true when running on macOS, where the shortcut modifier is Command
     */
    public static boolean isMac() {
        return isMac(System.getProperty("os.name", ""));
    }

    /**
     * @param osName the value of the {@code os.name} system property
     * @return true when that names a version of macOS
     */
    public static boolean isMac(String osName) {
        String normalized = osName == null ? "" : osName.toLowerCase(Locale.ROOT);
        // "darwin" alongside "mac" because that is what os.name reads as under some JVM
        // builds and CI images, where "Mac OS X" alone would miss.
        return normalized.contains("mac") || normalized.contains("darwin");
    }

    /**
     * The modifier that prefixes application commands on this platform: Command on macOS,
     * Control everywhere else. Use it for anything the user thinks of as "the shortcut key" -
     * Save, Undo, Copy, Select All.
     *
     * @return an {@link InputEvent} {@code _DOWN_MASK} constant
     */
    public static int menuMask() {
        return menuMask(isMac());
    }

    /**
     * @param mac whether to answer for macOS
     * @return the shortcut modifier mask for that platform
     */
    public static int menuMask(boolean mac) {
        return mac ? InputEvent.META_DOWN_MASK : InputEvent.CTRL_DOWN_MASK;
    }

    /**
     * The shortcut modifier plus Shift, for the handful of commands that are the "other half" of
     * a plain one - Redo against Undo, Export against Save.
     *
     * @return a combined {@link InputEvent} mask
     */
    public static int shiftMenuMask() {
        return menuMask() | InputEvent.SHIFT_DOWN_MASK;
    }

    /**
     * Whether an event carries the platform's shortcut modifier - the test to use in a
     * {@code KeyListener}, where there is no {@code KeyStroke} to compare against and the
     * temptation is to write {@code event.isControlDown()} and quietly exclude every Mac user.
     *
     * <p>Deliberately exact about the other modifiers only insofar as it ignores them: Copy is
     * still Copy whether or not Shift happens to be held, matching how the same command bound
     * through a menu accelerator behaves.
     *
     * @param modifiersEx the event's {@code getModifiersEx()} value
     * @return true if the shortcut modifier for this platform is down
     */
    public static boolean hasMenuMask(int modifiersEx) {
        return hasMenuMask(modifiersEx, isMac());
    }

    /**
     * @param modifiersEx the event's {@code getModifiersEx()} value
     * @param mac whether to answer for macOS
     * @return true if that platform's shortcut modifier is down
     */
    public static boolean hasMenuMask(int modifiersEx, boolean mac) {
        return (modifiersEx & menuMask(mac)) != 0;
    }

    /**
     * Whether Redo should also answer to the shortcut modifier plus Y.
     *
     * <p>Ctrl+Y is a Windows convention and nothing else; on macOS ⌘Y belongs to the system and
     * to applications' own View menus, and binding Redo to it would be both surprising and a
     * collision. ⌘⇧Z, which Redo keeps everywhere, is the Mac spelling.
     *
     * @return true on platforms where Redo conventionally answers to the modifier plus Y
     */
    public static boolean bindsRedoToY() {
        return !isMac();
    }
}

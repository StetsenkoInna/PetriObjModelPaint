package ua.stetsenkoinna.graphpresentation.theme;

import java.awt.Graphics;
import javax.swing.JMenuBar;

/**
 * A menu bar that fills its own background while the dark theme is in force.
 *
 * <p>Nimbus paints the menu bar with a hard-coded light gradient. It is not derived from any of
 * the base colours, so replacing those does not reach it, and it is not the component's
 * background either, so {@code setBackground} is quietly ignored - leaving a bright silver strip
 * across the top of an otherwise dark window.
 *
 * <p>Overriding Nimbus's {@code MenuBar[Enabled].backgroundPainter} does work, but only on a cold
 * start: after a theme change on a window that already exists, the menu bar comes back with the
 * stock gradient regardless of when the replacement painter is installed relative to the look and
 * feel. Painting it here instead does not depend on when anything was installed, so the switch
 * from the menu and the appearance at startup cannot disagree - which they did.
 *
 * <p>Light is left to Nimbus, untouched: the gradient is what the editor has always had, and this
 * class exists to add a dark appearance rather than to restyle the existing one.
 */
public class ThemedMenuBar extends JMenuBar {

    @Override
    protected void paintComponent(Graphics g) {
        if (!ThemeManager.currentVariant().isDark()) {
            super.paintComponent(g);
            return;
        }
        // Deliberately not calling super: its whole job here is to run the Nimbus painter that
        // this class exists to avoid. The menus themselves are children and paint themselves.
        g.setColor(getBackground());
        g.fillRect(0, 0, getWidth(), getHeight());
    }
}

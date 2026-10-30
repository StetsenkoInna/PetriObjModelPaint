package ua.stetsenkoinna.graphpresentation.theme;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.image.BufferedImage;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import org.junit.Assume;
import org.junit.Test;
import ua.stetsenkoinna.theme.ThemeMode;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class MenuThemeContrastTest {
    @Test
    public void popupAndHighlightKeepTheirDarkColorsAfterThemeChanges() throws Exception {
        Assume.assumeFalse(GraphicsEnvironment.isHeadless());
        SwingUtilities.invokeAndWait(() -> {
            ThemeMode original = ThemeManager.currentMode();
            JFrame frame = null;
            try {
                ThemeManager.applyMode(ThemeMode.LIGHT);
                JMenu cachedMenu = new JMenu("Cached menu");
                cachedMenu.add(new JMenuItem("Cached item"));
                render(cachedMenu.getPopupMenu());
                ThemeManager.applyMode(ThemeMode.DARK);
                frame = new JFrame();
                ThemedMenuBar bar = new ThemedMenuBar();
                JMenu menu = new JMenu("Edit");
                JMenuItem undo = new JMenuItem("Undo");
                undo.setEnabled(false);
                menu.add(undo);
                JMenuItem parameters = new JMenuItem("Edit net parameters");
                menu.add(parameters);
                bar.add(menu);
                frame.setJMenuBar(bar);
                frame.pack();
                assertDarkMenu(bar, menu, parameters);

                ThemeManager.applyMode(ThemeMode.LIGHT);
                JPopupMenu popup = menu.getPopupMenu();
                parameters.setArmed(false);
                int lightBackground = render(popup).getRGB(parameters.getX() + 3,
                        parameters.getY() + parameters.getHeight() / 2) & 0xffffff;
                assertTrue("Light theme should restore its light popup background", lightBackground != 0x3c3f41);

                ThemeManager.applyMode(ThemeMode.DARK);
                assertDarkMenu(bar, menu, parameters);
            } finally {
                if (frame != null) frame.dispose();
                ThemeManager.applyMode(original);
            }
        });
    }

    private static void assertDarkMenu(ThemedMenuBar bar, JMenu menu, JMenuItem parameters) {
        menu.setSelected(false);
        menu.setArmed(true);
        BufferedImage hover = render(bar);
        Color selection = UIManager.getColor("nimbusSelectionBackground");
        assertEquals("Top menu hover", selection.getRGB(), hover.getRGB(menu.getX() + 3, bar.getHeight() / 2));
        menu.setArmed(false);
        menu.setSelected(true);
        BufferedImage selected = render(bar);
        assertEquals("Top selected menu", selection.getRGB(), selected.getRGB(menu.getX() + 3, bar.getHeight() / 2));

        JPopupMenu popup = menu.getPopupMenu();
        parameters.setArmed(false);
        BufferedImage normal = render(popup);
        int x = parameters.getX() + 3;
        int y = parameters.getY() + parameters.getHeight() / 2;
        assertEquals("Full popup background", ThemeManager.palette().getChrome().getRGB(), normal.getRGB(x, y));
        parameters.setArmed(true);
        BufferedImage armed = render(popup);
        assertEquals("Popup item hover", selection.getRGB(), armed.getRGB(x, y));
    }

    private static BufferedImage render(JComponent component) {
        component.setSize(component.getPreferredSize());
        component.doLayout();
        BufferedImage image = new BufferedImage(component.getWidth(), component.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        try {
            component.printAll(graphics);
        } finally {
            graphics.dispose();
        }
        return image;
    }
}

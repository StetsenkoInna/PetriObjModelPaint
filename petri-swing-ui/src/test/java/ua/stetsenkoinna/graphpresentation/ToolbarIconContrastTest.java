package ua.stetsenkoinna.graphpresentation;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JToggleButton;
import javax.swing.SwingUtilities;
import org.junit.Test;
import ua.stetsenkoinna.config.ResourcePathConfig;
import ua.stetsenkoinna.graphpresentation.theme.ThemeManager;
import ua.stetsenkoinna.theme.ThemeMode;
import static org.junit.Assert.*;

public class ToolbarIconContrastTest {
    private static final String[] RASTERS = {ResourcePathConfig.HAND_ICON, ResourcePathConfig.ERASER_ICON,
            ResourcePathConfig.PLACE_ICON, ResourcePathConfig.TRANSITION_ICON, ResourcePathConfig.ARC_ICON};

    private static Icon original(String name) {
        ImageIcon loaded = new ImageIcon(ResourcePathConfig.getResource(ToolbarIconContrastTest.class, ResourcePathConfig.getIconPath(name)));
        return new ImageIcon(loaded.getImage().getScaledInstance(20, 20, java.awt.Image.SCALE_SMOOTH));
    }

    private static JToggleButton button(Icon icon) {
        JToggleButton button = new JToggleButton(icon);
        button.setForeground(Color.BLACK);
        button.setBackground(ThemeManager.palette().getChrome());
        CanvasToolIcons.installToolbarDisabledIcon(button);
        return button;
    }

    private static BufferedImage render(JToggleButton button, Icon icon) {
        BufferedImage image = new BufferedImage(28, 28, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setColor(button.getBackground());
        graphics.fillRect(0, 0, 28, 28);
        icon.paintIcon(button, graphics, 4, 4);
        graphics.dispose();
        return image;
    }

    @Test public void lightRasterArtworkIsPreservedExactly() throws Exception {
        ThemeMode previous = ThemeManager.currentMode();
        try {
            SwingUtilities.invokeAndWait(() -> {
                ThemeManager.applyMode(ThemeMode.LIGHT);
                for (String name : RASTERS) {
                    Icon original = original(name);
                    JToggleButton button = button(original);
                    BufferedImage expected = render(button, original);
                    BufferedImage actual = render(button, CanvasToolIcons.themedToolbarIcon(original, name));
                    assertArrayEquals(name, expected.getRGB(0, 0, 28, 28, null, 0, 28), actual.getRGB(0, 0, 28, 28, null, 0, 28));
                }
            });
        } finally {
            SwingUtilities.invokeAndWait(() -> ThemeManager.applyMode(previous));
        }
    }

    @Test public void darkIconsRemainVisibleAndDisabledIconsAreDistinct() throws Exception {
        ThemeMode previous = ThemeManager.currentMode();
        try {
            SwingUtilities.invokeAndWait(() -> {
                ThemeManager.applyMode(ThemeMode.DARK);
                java.util.List<Icon> icons = new java.util.ArrayList<>();
                icons.add(CanvasToolIcons.pointer(20));
                icons.add(CanvasToolIcons.marquee(20));
                icons.add(CanvasToolIcons.objectBand(20));
                icons.add(CanvasToolIcons.chevron(20, true));
                icons.add(CanvasToolIcons.letter("G", 20));
                for (String name : RASTERS) icons.add(CanvasToolIcons.themedToolbarIcon(original(name), name));
                for (Icon icon : icons) {
                    JToggleButton button = button(icon);
                    BufferedImage enabled = render(button, icon);
                    button.setEnabled(false);
                    BufferedImage disabled = render(button, button.getDisabledIcon());
                    double enabledContrast = maximumContrast(enabled, button.getBackground());
                    double disabledContrast = maximumContrast(disabled, button.getBackground());
                    assertTrue("Enabled icon should contrast with dark toolbar", enabledContrast >= 5.0);
                    assertTrue("Disabled icon should still be recognizable", disabledContrast >= 2.5);
                    assertTrue("Disabled state should be visibly muted", disabledContrast < enabledContrast * 0.7);
                }
            });
        } finally {
            SwingUtilities.invokeAndWait(() -> ThemeManager.applyMode(previous));
        }
    }

    private static double maximumContrast(BufferedImage image, Color background) {
        double result = 1;
        double backgroundLuminance = luminance(background);
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                double foreground = luminance(new Color(image.getRGB(x, y)));
                result = Math.max(result, (Math.max(foreground, backgroundLuminance) + 0.05)
                        / (Math.min(foreground, backgroundLuminance) + 0.05));
            }
        }
        return result;
    }

    private static double luminance(Color color) {
        return 0.2126 * linear(color.getRed()) + 0.7152 * linear(color.getGreen()) + 0.0722 * linear(color.getBlue());
    }

    private static double linear(int channel) {
        double normalized = channel / 255.0;
        return normalized <= 0.04045 ? normalized / 12.92 : Math.pow((normalized + 0.055) / 1.055, 2.4);
    }

    public static void main(String[] args) throws Exception {
        Path output = Path.of(args[0]);
        Files.createDirectories(output);
        SwingUtilities.invokeAndWait(() -> {
            for (ThemeMode mode : new ThemeMode[]{ThemeMode.LIGHT, ThemeMode.DARK}) {
                ThemeManager.applyMode(mode);
                BufferedImage sheet = new BufferedImage(480, 132, BufferedImage.TYPE_INT_RGB);
                Graphics2D graphics = sheet.createGraphics();
                graphics.setColor(ThemeManager.palette().getChrome());
                graphics.fillRect(0, 0, sheet.getWidth(), sheet.getHeight());
                graphics.setColor(ThemeManager.palette().getChromeText());
                graphics.drawString("Enabled", 8, 18);
                graphics.drawString("Disabled", 8, 80);
                java.util.List<Icon> icons = new java.util.ArrayList<>();
                icons.add(CanvasToolIcons.pointer(20));
                icons.add(CanvasToolIcons.marquee(20));
                for (String name : RASTERS) icons.add(CanvasToolIcons.themedToolbarIcon(original(name), name));
                icons.add(CanvasToolIcons.objectBand(20));
                for (int i = 0; i < icons.size(); i++) {
                    Icon icon = icons.get(i);
                    JToggleButton button = button(icon);
                    graphics.drawImage(render(button, icon), 9 + i * 58, 23, 42, 42, null);
                    button.setEnabled(false);
                    graphics.drawImage(render(button, button.getDisabledIcon()), 9 + i * 58, 84, 42, 42, null);
                }
                graphics.dispose();
                try {
                    ImageIO.write(sheet, "png", output.resolve("toolbar-icons-" + mode.name().toLowerCase(java.util.Locale.ROOT) + ".png").toFile());
                } catch (java.io.IOException ex) {
                    throw new java.io.UncheckedIOException(ex);
                }
            }
        });
    }
}

package ua.stetsenkoinna;

import java.awt.AWTEvent;
import java.awt.Image;
import java.awt.Taskbar;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.event.WindowEvent;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import javax.swing.ImageIcon;

import ua.stetsenkoinna.config.ResourcePathConfig;

/**
 * The application's own icon - a place with a token, an arc and a transition - in place of
 * the default Java coffee cup on every window, the taskbar and the macOS Dock.
 *
 * <p>Shipped at several sizes rather than one large image left to the system to scale down:
 * at 16 and 24 pixels the drawing is a separate, bolder variant without the arrowhead, which a
 * scaled-down 256 pixel image turns into a grey smudge.
 */
public final class AppIcon {

    private static final int[] SIZES = {16, 20, 24, 32, 40, 48, 64, 128, 256, 512};

    private static List<Image> images;

    private AppIcon() {
    }

    /**
     * @return the icon at every size it ships in, smallest first; empty if none could be read
     */
    public static synchronized List<Image> images() {
        if (images == null) {
            List<Image> loaded = new ArrayList<>();
            for (int size : SIZES) {
                URL url = AppIcon.class.getResource(ResourcePathConfig.getIconPath("app-icon-" + size + ".png"));
                if (url != null) {
                    loaded.add(new ImageIcon(url).getImage());
                }
            }
            images = List.copyOf(loaded);
        }
        return images;
    }

    /**
     * Puts the icon on the taskbar or Dock and on every window the app opens from now on.
     *
     * <p>A listener rather than a call in each window's constructor: the editor opens a good
     * number of ownerless frames of its own (the arc, place and transition property windows, the
     * statistics windows, the welcome screen), and any one that was missed would show the coffee
     * cup again. A window that already set an icon of its own keeps it.
     */
    public static void install() {
        List<Image> icons = images();
        if (icons.isEmpty()) {
            return;
        }
        if (Taskbar.isTaskbarSupported() && Taskbar.getTaskbar().isSupported(Taskbar.Feature.ICON_IMAGE)) {
            Taskbar.getTaskbar().setIconImage(icons.get(icons.size() - 1));
        }
        Toolkit.getDefaultToolkit().addAWTEventListener(event -> {
            if (event.getID() == WindowEvent.WINDOW_OPENED
                    && event.getSource() instanceof Window window
                    && window.getIconImages().isEmpty()) {
                window.setIconImages(icons);
            }
        }, AWTEvent.WINDOW_EVENT_MASK);
    }
}

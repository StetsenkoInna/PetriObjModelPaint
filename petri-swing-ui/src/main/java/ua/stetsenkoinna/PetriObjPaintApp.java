package ua.stetsenkoinna;

import ua.stetsenkoinna.config.AppSettings;
import ua.stetsenkoinna.graphpresentation.PetriNetsFrame;
import ua.stetsenkoinna.graphpresentation.settings.SettingsDialog;
import ua.stetsenkoinna.graphpresentation.theme.ThemeManager;
import ua.stetsenkoinna.utils.MessageHelper;

import javax.swing.JFrame;

/**
 * Entry point of the petri-net-sim desktop editor (Swing UI).
 * Configured as the executable JAR's {@code Main-Class} (see petri-swing-ui/pom.xml).
 */
public class PetriObjPaintApp {

    public static void main(String[] args) {
        // Everything, including the look and feel, on the event dispatch thread: the first-run
        // dialog is a real window, and putting up a window from the main thread and then handing
        // the rest to the event queue is the kind of split that works until it does not.
        java.awt.EventQueue.invokeLater(() -> {
            AppSettings settings = AppSettings.shared();

            // Before any window is built, so the first frame the user sees is already in the
            // right theme rather than repainting itself into it a moment later.
            ThemeManager.applySavedMode();
            SettingsDialog.showIfFirstRun(settings);

            PetriNetsFrame frame = new PetriNetsFrame();
            frame.setExtendedState(JFrame.MAXIMIZED_BOTH);

            // Set the main frame as default parent for MessageHelper dialogs
            MessageHelper.setDefaultParent(frame);

            frame.setVisible(true);
        });
    }
}

package ua.stetsenkoinna;

import java.io.File;
import java.util.Optional;

import ua.stetsenkoinna.config.AppSettings;
import ua.stetsenkoinna.graphpresentation.PetriNetsFrame;
import ua.stetsenkoinna.graphpresentation.settings.SettingsDialog;
import ua.stetsenkoinna.graphpresentation.theme.ThemeManager;
import ua.stetsenkoinna.graphpresentation.welcome.WelcomeFrame;
import ua.stetsenkoinna.recentprojects.RecentProjectEntry;
import ua.stetsenkoinna.recentprojects.RecentProjectsStore;
import ua.stetsenkoinna.utils.MessageHelper;

import javax.swing.JFrame;

/**
 * Entry point of the PetriObjModelPaint desktop editor (Swing UI).
 * Configured as the executable JAR's {@code Main-Class} (see petri-swing-ui/pom.xml).
 */
public class PetriObjPaintApp {

    public static void main(String[] args) {
        // Everything, including the look and feel, on the event dispatch thread: the first-run
        // dialog is a real window, and putting up a window from the main thread and then handing
        // the rest to the event queue is the kind of split that works until it does not.
        java.awt.EventQueue.invokeLater(() -> {
            AppSettings settings = AppSettings.shared();
            RecentProjectsStore store = RecentProjectsStore.shared();

            // Before any window is built, so the first frame the user sees is already in the
            // right theme rather than repainting itself into it a moment later.
            ThemeManager.applySavedMode();
            SettingsDialog.showIfFirstRun(settings);

            // Built once and reused for the whole process: the undo manager and undo support
            // behind it are static, shared across every instance, so there must only ever be
            // one. Not shown yet - whichever startup path below applies decides when.
            PetriNetsFrame frame = new PetriNetsFrame();
            MessageHelper.setDefaultParent(frame);

            File autoReopenFile = resolveAutoReopenFile(settings, store);
            if (autoReopenFile != null) {
                // Shown before loading, not after: if the file fails to parse, the error
                // dialog MessageHelper puts up needs a visible parent to attach to.
                frame.setExtendedState(JFrame.MAXIMIZED_BOTH);
                frame.setVisible(true);
                frame.openProjectFile(autoReopenFile);
            } else {
                WelcomeFrame.show(settings, store,
                        f -> {
                            // WelcomeFrame.show() pointed MessageHelper at itself; it is about
                            // to be disposed, so the default parent has to move back to this
                            // frame before anything - including a load-failure dialog - can use it.
                            MessageHelper.setDefaultParent(frame);
                            frame.setExtendedState(JFrame.MAXIMIZED_BOTH);
                            frame.setVisible(true);
                            frame.openProjectFile(f);
                            return true; // nothing was ever open yet, so there is nothing to decline
                        },
                        () -> {
                            // The frame is already blank from construction - nothing to load.
                            MessageHelper.setDefaultParent(frame);
                            frame.setExtendedState(JFrame.MAXIMIZED_BOTH);
                            frame.setVisible(true);
                            return true;
                        },
                        () -> { }, // onDismiss - unreachable: exitAppIfDismissed=true below exits directly instead
                        true // exitAppIfDismissed - this is the startup call, nothing else has been shown yet
                );
            }
        });
    }

    /**
     * @return the file to auto-reopen on startup, or null if reopening is off, there is no
     *         active project remembered, or the remembered project can no longer be found on
     *         disk
     */
    private static File resolveAutoReopenFile(AppSettings settings, RecentProjectsStore store) {
        if (!settings.isReopenLastProjectOnStartup()) {
            return null;
        }
        String activeId = store.getActiveProjectId();
        if (activeId == null) {
            return null;
        }
        Optional<RecentProjectEntry> entry = store.findById(activeId);
        if (entry.isEmpty()) {
            return null;
        }
        File file = new File(entry.get().getPath());
        return file.isFile() ? file : null;
    }
}

package ua.stetsenkoinna.uidriver;

import ua.stetsenkoinna.graphnet.GraphCanvasModel;
import ua.stetsenkoinna.graphnet.GraphPetriObjModel;
import ua.stetsenkoinna.graphpresentation.CanvasTool;
import ua.stetsenkoinna.graphpresentation.PetriNetsFrame;
import ua.stetsenkoinna.graphpresentation.PetriNetsPanel;
import ua.stetsenkoinna.graphpresentation.statistic.StatisticMonitorDialog;
import ua.stetsenkoinna.pnml.PnmlModelParser;

import javax.imageio.ImageIO;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JPopupMenu;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dialog;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.HeadlessException;
import java.awt.IllegalComponentStateException;
import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.Window;
import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Boots a real {@link PetriNetsFrame} and dumps PNG screenshots of its main window and key
 * dialogs, so a human can visually inspect the desktop editor without running it by hand.
 *
 * <p>This is a throwaway test-tree tool, not a JUnit test — it is named without a {@code Test}
 * suffix on purpose so surefire never picks it up. Run it via {@code java ...
 * ua.stetsenkoinna.uidriver.ScreenshotHarness --out <dir> [--theme <system|light|dark>]
 * [--shots <comma,separated,list>] [--capture <printall|robot>]}. See
 * {@code .uiharness/run-harness.ps1} for a script that assembles the classpath and runs it.
 *
 * <h3>Two capture paths</h3>
 * {@code --capture printall} (the default, unchanged since this harness's first version) never
 * shows anything on screen: it renders each component's own {@code printAll(Graphics)} into an
 * off-screen {@link BufferedImage}. That works even on a window that is not on top or not
 * visible at all, but it is not real screen output — a component can be measured, laid out and
 * printed slightly differently than it would actually paint once shown, which matters exactly
 * when what is being investigated is a rendering/layout bug.
 *
 * <p>{@code --capture robot} instead shows each window for real and grabs actual on-screen
 * pixels with {@link Robot#createScreenCapture}, at the cost of needing a real, unobstructed
 * display and briefly stealing the foreground while it runs. Use it to get ground truth when a
 * printAll shot looks suspicious.
 *
 * <p>Every shot is independent and best-effort: one failing or unavailable shot does not stop
 * the others. Each attempt logs exactly one line, prefixed {@code SHOT ok:}, {@code SHOT skip:}
 * or {@code SHOT fail:}, so a human (or a script) can tell at a glance what was produced.
 */
public final class ScreenshotHarness {

    private static final String SAMPLE_NET_PREFERRED =
            "petri-model/src/test/resources/pnml/composed_conformant_v21.pnml";
    private static final String SAMPLE_NET_FALLBACK =
            "petri-swing-ui/src/main/resources/pnml/samples/easy/this-easy.pnml";

    /** Reproducible main-frame size, clamped down if the screen is smaller (see {@link
     *  #sizeMainFrame}). */
    private static final int MAIN_FRAME_WIDTH = 1600;
    private static final int MAIN_FRAME_HEIGHT = 1000;

    private static final AtomicInteger OK_COUNT = new AtomicInteger();
    private static final AtomicInteger SKIP_COUNT = new AtomicInteger();
    private static final AtomicInteger FAIL_COUNT = new AtomicInteger();

    private ScreenshotHarness() {
    }

    public static void main(String[] args) throws Exception {
        Options options = Options.parse(args);
        Files.createDirectories(options.outDir);

        applyTheme(options.theme);

        PetriNetsFrame[] frameHolder = new PetriNetsFrame[1];
        SwingUtilities.invokeAndWait(() -> frameHolder[0] = createFrame());
        PetriNetsFrame frame = frameHolder[0];

        runShot(options, "main-empty", () -> shotMainEmpty(frame, options.outDir, options.capture));
        runShot(options, "main-net", () -> shotMainNet(frame, options.outDir, options.capture));
        runShot(options, "main-selection", () -> shotMainSelection(frame, options.outDir, options.capture));
        runShot(options, "menus", () -> shotMenus(frame, options.outDir, options.capture));
        runShot(options, "dialog-settings", () -> shotDialogSettings(frame, options.outDir, options.capture));
        runShot(options, "dialog-statistics", () -> shotDialogStatistics(frame, options.outDir, options.capture));

        System.out.println("SUMMARY: ok=" + OK_COUNT.get() + " skip=" + SKIP_COUNT.get()
                + " fail=" + FAIL_COUNT.get());

        // Every shot that ran either failed or nothing ran at all: that is the only case worth
        // a non-zero exit. A skip (missing class, flaky JavaFX chart, ...) is not a failure.
        boolean everythingFailed = FAIL_COUNT.get() > 0 && OK_COUNT.get() == 0 && SKIP_COUNT.get() == 0;
        System.out.flush();
        // Swing/JavaFX leave non-daemon threads (EDT, FX toolkit) running - the JVM would
        // otherwise hang here forever instead of returning to whoever launched the harness.
        System.exit(everythingFailed ? 1 : 0);
    }

    private static PetriNetsFrame createFrame() {
        PetriNetsFrame frame = new PetriNetsFrame();
        // The constructor itself maximizes the frame; undo that so every screenshot is taken
        // at the same reproducible size regardless of the screen the harness runs on.
        frame.setExtendedState(JFrame.NORMAL);
        sizeMainFrame(frame);
        try {
            frame.setVisible(true);
        } catch (HeadlessException e) {
            // No real display: fall back to a displayable-but-invisible frame, per the
            // known-good recipe. printAll still renders it either way. (Robot capture will
            // fail its own way further down, since there is nothing to grab pixels from.)
            frame.addNotify();
            frame.validate();
        }
        frame.validate();
        return frame;
    }

    /**
     * Sizes the main frame to {@link #MAIN_FRAME_WIDTH}x{@link #MAIN_FRAME_HEIGHT} for
     * reproducible screenshots, clamped down to the actual screen when it is smaller than that.
     * printAll does not care - it never touches real screen pixels - but robot capture needs
     * the whole frame to actually fit on screen, and failing the shot over a frame that is
     * merely bigger than the screen would be worse than shrinking it and saying so.
     */
    private static void sizeMainFrame(JFrame frame) {
        Dimension desired = new Dimension(MAIN_FRAME_WIDTH, MAIN_FRAME_HEIGHT);
        Dimension actual = clampToScreen(desired);
        if (!actual.equals(desired)) {
            System.out.println("NOTE: screen is smaller than " + desired.width + "x" + desired.height
                    + " - clamping the main frame to " + actual.width + "x" + actual.height);
        }
        frame.setSize(actual);
    }

    private static Dimension clampToScreen(Dimension desired) {
        try {
            Rectangle screen = GraphicsEnvironment.getLocalGraphicsEnvironment()
                    .getDefaultScreenDevice().getDefaultConfiguration().getBounds();
            return new Dimension(Math.min(desired.width, screen.width), Math.min(desired.height, screen.height));
        } catch (HeadlessException e) {
            // No real screen to measure against - leave the desired size as-is.
            return desired;
        }
    }

    // ------------------------------------------------------------------------------------
    // Shots
    // ------------------------------------------------------------------------------------

    private static void shotMainEmpty(PetriNetsFrame frame, Path outDir, CaptureMode capture) throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            frame.setExtendedState(JFrame.NORMAL);
            sizeMainFrame(frame);
            frame.validate();
        });
        captureMainFrame("main-empty", outDir, frame, capture);
    }

    private static void shotMainNet(PetriNetsFrame frame, Path outDir, CaptureMode capture) throws Exception {
        String[] sampleUsed = new String[1];
        Exception[] failure = new Exception[1];
        SwingUtilities.invokeAndWait(() -> {
            try {
                sampleUsed[0] = loadSampleNet(frame).toString();
            } catch (Exception e) {
                failure[0] = e;
            }
        });
        if (failure[0] != null) {
            throw failure[0];
        }
        System.out.println("  (main-net loaded " + sampleUsed[0] + ")");
        captureMainFrame("main-net", outDir, frame, capture);
    }

    private static void shotMainSelection(PetriNetsFrame frame, Path outDir, CaptureMode capture) throws Exception {
        Exception[] failure = new Exception[1];
        SwingUtilities.invokeAndWait(() -> {
            try {
                loadSampleNet(frame);
                PetriNetsPanel panel = resolvePetriNetsPanel(frame);
                panel.setTool(CanvasTool.MARQUEE);
                dispatchMarqueeDrag(panel);
            } catch (Exception e) {
                failure[0] = e;
            }
        });
        if (failure[0] != null) {
            throw failure[0];
        }
        captureMainFrame("main-selection", outDir, frame, capture);
    }

    /**
     * Renders the main frame's current state with whichever capture path was requested. Used
     * by every main-window shot (empty, with a net, with a selection) so the wait/settle/robot
     * logic lives in one place.
     */
    private static void captureMainFrame(String name, Path outDir, PetriNetsFrame frame, CaptureMode capture)
            throws Exception {
        if (capture != CaptureMode.ROBOT) {
            captureAndSave(name, outDir, frame::getRootPane);
            return;
        }
        if (!waitUntilShowing(frame, 5000)) {
            System.out.println("SHOT skip: " + name + " - main frame never became showing for robot capture");
            SKIP_COUNT.incrementAndGet();
            return;
        }
        // Robot grabs raw screen pixels regardless of z-order - on a shared desktop, anything
        // that happens to be on top of the frame's bounds at the moment of capture (another
        // application, a browser window, ...) gets captured instead of the frame. toFront()
        // does not guarantee foreground (the OS can ignore it for a background process), but
        // it measurably reduces the chance, seen in testing, of an unrelated window winning
        // the race during the settle below.
        SwingUtilities.invokeAndWait(frame::toFront);
        // isShowing() flips true as soon as the peer is realized, which can still race the
        // first real paint by a frame or two - a short settle avoids grabbing stale pixels.
        Thread.sleep(300);
        captureRegionRobot(name, outDir, boundsOnEdt(frame));
    }

    private static void shotMenus(PetriNetsFrame frame, Path outDir, CaptureMode capture) throws Exception {
        JMenuBar[] barHolder = new JMenuBar[1];
        SwingUtilities.invokeAndWait(() -> barHolder[0] = frame.getJMenuBar());
        JMenuBar menuBar = barHolder[0];
        if (menuBar == null) {
            System.out.println("SHOT skip: menus - frame has no menu bar");
            SKIP_COUNT.incrementAndGet();
            return;
        }
        if (capture == CaptureMode.ROBOT) {
            // Each popup inherits the frame's own z-order; raising the frame once before
            // opening any of them (rather than per-menu) is enough and cheaper. See the
            // toFront() comment on captureMainFrame's robot branch for why this only narrows,
            // rather than eliminates, the chance of an unrelated window being captured instead.
            SwingUtilities.invokeAndWait(frame::toFront);
        }

        int[] countHolder = new int[1];
        SwingUtilities.invokeAndWait(() -> countHolder[0] = menuBar.getMenuCount());
        int menuCount = countHolder[0];

        boolean anyMenu = false;
        for (int i = 0; i < menuCount; i++) {
            final int index = i;
            JMenu[] menuHolder = new JMenu[1];
            SwingUtilities.invokeAndWait(() -> menuHolder[0] = menuBar.getMenu(index));
            JMenu menu = menuHolder[0];
            // getMenu(int) returns null for a bare JMenuItem in the bar (e.g. "Nets",
            // "PObjects") - those have no popup to screenshot, so they are simply skipped.
            if (menu == null) {
                continue;
            }
            anyMenu = true;
            captureOneMenu(menu, outDir, capture);
        }
        if (!anyMenu) {
            System.out.println("SHOT skip: menus - no JMenu with a popup found in the menu bar");
            SKIP_COUNT.incrementAndGet();
        }
    }

    private static void captureOneMenu(JMenu menu, Path outDir, CaptureMode capture) {
        String[] textHolder = new String[1];
        try {
            SwingUtilities.invokeAndWait(() -> textHolder[0] = menu.getText());
        } catch (Exception e) {
            logFail("menu-?", e);
            return;
        }
        String label = textHolder[0] == null || textHolder[0].isBlank() ? "unnamed" : textHolder[0];
        String shotName = "menu-" + label.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-");

        JComponent[] popupHolder = new JComponent[1];
        Rectangle[] screenBoundsHolder = new Rectangle[1];
        try {
            SwingUtilities.invokeAndWait(() -> {
                menu.setPopupMenuVisible(true);
                JPopupMenu popup = menu.getPopupMenu();
                if (popup != null && popup.isShowing() && popup.getWidth() > 0 && popup.getHeight() > 0) {
                    popupHolder[0] = popup;
                    try {
                        screenBoundsHolder[0] = new Rectangle(popup.getLocationOnScreen(), popup.getSize());
                    } catch (IllegalComponentStateException notOnScreen) {
                        screenBoundsHolder[0] = null;
                    }
                }
            });
        } catch (Exception e) {
            logFail(shotName, e);
            closeMenuQuietly(menu);
            return;
        }

        if (popupHolder[0] == null) {
            System.out.println("SHOT skip: " + shotName + " - popup did not open (no display / empty menu)");
            SKIP_COUNT.incrementAndGet();
        } else if (capture == CaptureMode.ROBOT) {
            try {
                // The popup just opened synchronously above (setPopupMenuVisible(true) had
                // already returned) - a short pause gives the OS a moment to actually flip the
                // pixels before they are grabbed off the real screen.
                Thread.sleep(200);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
            captureRegionRobot(shotName, outDir, screenBoundsHolder[0]);
        } else {
            captureAndSave(shotName, outDir, () -> popupHolder[0]);
        }
        closeMenuQuietly(menu);
    }

    private static void closeMenuQuietly(JMenu menu) {
        try {
            SwingUtilities.invokeAndWait(() -> menu.setPopupMenuVisible(false));
        } catch (Exception ignored) {
            // Closing the popup is just tidiness before the next menu opens - not worth
            // failing the shot over.
        }
    }

    /**
     * Screenshots {@code ua.stetsenkoinna.graphpresentation.settings.SettingsDialog} via
     * whichever capture path was requested. The class is looked up reflectively either way
     * (skip cleanly if it is ever absent again), but the two paths differ sharply in how they
     * handle its modality - see {@link #shotDialogSettingsPrintAll} and
     * {@link #shotDialogSettingsRobot}.
     */
    private static void shotDialogSettings(PetriNetsFrame frame, Path outDir, CaptureMode capture)
            throws Exception {
        if (capture == CaptureMode.ROBOT) {
            shotDialogSettingsRobot(frame, outDir);
        } else {
            shotDialogSettingsPrintAll(frame, outDir);
        }
    }

    /**
     * The original printAll path, unchanged: forces the dialog non-modal before showing it, so
     * {@code setVisible(true)} - called directly on the EDT, inside {@code invokeAndWait} -
     * returns immediately instead of pumping a nested modal loop that nothing would ever end.
     */
    private static void shotDialogSettingsPrintAll(PetriNetsFrame frame, Path outDir) throws Exception {
        String name = "dialog-settings";
        Class<?> dialogClass;
        try {
            dialogClass = Class.forName("ua.stetsenkoinna.graphpresentation.settings.SettingsDialog");
        } catch (ClassNotFoundException e) {
            System.out.println("SHOT skip: " + name + " - SettingsDialog class does not exist yet");
            SKIP_COUNT.incrementAndGet();
            return;
        }

        final Class<?> resolvedClass = dialogClass;
        Object[] dialogHolder = new Object[1];
        Exception[] failure = new Exception[1];
        SwingUtilities.invokeAndWait(() -> {
            try {
                Object dialog = instantiateSpeculatively(resolvedClass, frame);
                if (dialog instanceof java.awt.Dialog awtDialog) {
                    // A speculative dialog must never be allowed to block the harness in a
                    // modal event loop - force it non-modal regardless of how it was built.
                    awtDialog.setModal(false);
                }
                if (dialog instanceof Component component) {
                    Dimension pref = component.getPreferredSize();
                    component.setSize(pref.width > 0 && pref.height > 0 ? pref : new Dimension(640, 480));
                }
                Method setVisible = resolvedClass.getMethod("setVisible", boolean.class);
                setVisible.invoke(dialog, true);
                dialogHolder[0] = dialog;
            } catch (Exception e) {
                failure[0] = e;
            }
        });

        if (failure[0] != null) {
            throw failure[0];
        }
        if (dialogHolder[0] instanceof JDialog jDialog) {
            captureAndSave(name, outDir, jDialog::getRootPane);
            SwingUtilities.invokeAndWait(jDialog::dispose);
        } else {
            System.out.println("SHOT skip: " + name
                    + " - SettingsDialog exists but could not be constructed/shown reflectively");
            SKIP_COUNT.incrementAndGet();
        }
    }

    /**
     * The robot path: shows the dialog with whatever modality it actually has (never forced
     * non-modal), because the whole point of this path is to see the dialog exactly as a user
     * would. That makes showing it more delicate.
     *
     * <p>An {@code APPLICATION_MODAL} dialog's own {@code setVisible(true)} does not return
     * until the dialog is closed. Calling it through {@code invokeAndWait} would therefore
     * block the calling thread forever - nothing would be left running to close the dialog, since
     * the very call that was supposed to show it never returns. Posting it with
     * {@code invokeLater} instead only schedules the call: the EDT runs it, enters the dialog's
     * own nested modal event pump, and - critically - that nested pump keeps dispatching
     * events, including further {@code invokeLater}/{@code invokeAndWait} runnables, the whole
     * time it is spinning. That is what lets the {@code dispose()} posted in the {@code finally}
     * block still get through and end the modal loop.
     *
     * <p>Showing-state is polled directly on this (non-EDT) thread rather than by hopping back
     * onto the EDT via {@code invokeAndWait} for every check - there is no need to, reading one
     * boolean does not require it, and it keeps the polling loop simple.
     */
    private static void shotDialogSettingsRobot(PetriNetsFrame frame, Path outDir) throws Exception {
        String name = "dialog-settings";
        Class<?> dialogClass;
        try {
            dialogClass = Class.forName("ua.stetsenkoinna.graphpresentation.settings.SettingsDialog");
        } catch (ClassNotFoundException e) {
            System.out.println("SHOT skip: " + name + " - SettingsDialog class does not exist yet");
            SKIP_COUNT.incrementAndGet();
            return;
        }

        final Class<?> resolvedClass = dialogClass;
        Object[] dialogHolder = new Object[1];
        Exception[] failure = new Exception[1];
        // Construction alone is safe via invokeAndWait: nothing blocks until setVisible(true)
        // is actually called, further down.
        SwingUtilities.invokeAndWait(() -> {
            try {
                dialogHolder[0] = instantiateSpeculatively(resolvedClass, frame);
            } catch (Exception e) {
                failure[0] = e;
            }
        });
        if (failure[0] != null) {
            throw failure[0];
        }
        if (!(dialogHolder[0] instanceof Window window)) {
            System.out.println("SHOT skip: " + name
                    + " - SettingsDialog exists but could not be constructed reflectively");
            SKIP_COUNT.incrementAndGet();
            return;
        }

        SwingUtilities.invokeLater(() -> {
            if (window instanceof Dialog dialog) {
                dialog.setVisible(true);
            } else {
                window.setVisible(true);
            }
        });
        // Queued separately, after the show above: by the time this runs the dialog is
        // already visible and there is something for toFront() to raise. The nested modal
        // pump keeps servicing the queue while setVisible(true) is blocking its own runnable,
        // which is what lets this (and the dispose() in the finally block) still get through.
        SwingUtilities.invokeLater(window::toFront);

        try {
            long deadline = System.currentTimeMillis() + 5000;
            while (!window.isShowing() && System.currentTimeMillis() < deadline) {
                Thread.sleep(50);
            }
            if (!window.isShowing()) {
                System.out.println("SHOT skip: " + name
                        + " - modal dialog never became showing for robot capture");
                SKIP_COUNT.incrementAndGet();
                return;
            }
            // Give the first real paint a moment to happen after the peer is realized.
            Thread.sleep(300);
            // Robot grabs raw screen pixels regardless of z-order - if anything else on a
            // shared desktop is on top of the dialog's bounds right now, it gets captured
            // instead of the dialog. Confirmed to actually happen in testing (an unrelated
            // window won the race once in several runs); one more toFront() immediately before
            // the grab narrows that window further, though it cannot close it completely - the
            // OS can decline to raise a background process's window at all.
            SwingUtilities.invokeLater(window::toFront);
            Thread.sleep(100);
            captureRegionRobot(name, outDir, window.getBounds());
        } finally {
            SwingUtilities.invokeLater(window::dispose);
            // Best-effort: give it a moment to actually close before the next shot runs,
            // but never block indefinitely on it - the harness has to move on regardless.
            long deadline = System.currentTimeMillis() + 2000;
            while (window.isShowing() && System.currentTimeMillis() < deadline) {
                Thread.sleep(50);
            }
        }
    }

    private static Object instantiateSpeculatively(Class<?> dialogClass, PetriNetsFrame frame)
            throws ReflectiveOperationException {
        for (Constructor<?> ctor : dialogClass.getDeclaredConstructors()) {
            Class<?>[] paramTypes = ctor.getParameterTypes();
            ctor.setAccessible(true);
            if (paramTypes.length == 0) {
                return ctor.newInstance();
            }
            if (paramTypes[0].isAssignableFrom(PetriNetsFrame.class)) {
                Object[] args = new Object[paramTypes.length];
                args[0] = frame;
                for (int i = 1; i < paramTypes.length; i++) {
                    args[i] = defaultValueFor(paramTypes[i]);
                }
                return ctor.newInstance(args);
            }
        }
        throw new NoSuchMethodException("No constructor of " + dialogClass
                + " takes a PetriNetsFrame (or nothing) as its first argument");
    }

    private static Object defaultValueFor(Class<?> type) {
        if (type == boolean.class) {
            return Boolean.FALSE;
        }
        if (type.isPrimitive()) {
            return 0;
        }
        return null;
    }

    /**
     * Screenshots {@link StatisticMonitorDialog}. It embeds a JavaFX {@code JFXPanel} chart
     * built asynchronously via {@code Platform.runLater}, so the dialog can be showing on screen
     * before the chart itself has actually been laid out - this polls briefly to give the FX
     * toolkit a chance to start and the chart to appear before giving up and rendering whatever
     * is there. The dialog is always opened non-modal here ({@code modal=false} at construction),
     * so {@code setVisible(true)} never blocks and both capture paths can safely use
     * {@code invokeAndWait} around it.
     */
    private static void shotDialogStatistics(PetriNetsFrame frame, Path outDir, CaptureMode capture) {
        String name = "dialog-statistics";
        StatisticMonitorDialog[] dialogHolder = new StatisticMonitorDialog[1];
        try {
            SwingUtilities.invokeAndWait(() -> {
                try {
                    // A loaded net is not required, but gives the monitor something plausible
                    // to look at instead of a totally bare window.
                    loadSampleNet(frame);
                } catch (Exception ignored) {
                    // Best-effort: the dialog itself is what is being screenshotted here.
                }
                StatisticMonitorDialog dialog = new StatisticMonitorDialog(frame, false);
                dialog.setSize(900, 650);
                dialog.setLocationRelativeTo(frame);
                dialog.setVisible(true);
                dialogHolder[0] = dialog;
            });
        } catch (Exception e) {
            logFail(name, e);
            return;
        }

        try {
            // Up to ~3 seconds for the FX toolkit to start and Platform.runLater to build the
            // chart. There is no public "is the chart ready" signal to poll, so this is a
            // fixed grace period rather than a real readiness check.
            Thread.sleep(3000);
            if (capture == CaptureMode.ROBOT) {
                // See the toFront() comment in shotMainFrame's robot branch: Robot grabs raw
                // screen pixels regardless of z-order, so this narrows (without eliminating)
                // the chance that something else on a shared desktop covers the dialog.
                SwingUtilities.invokeAndWait(dialogHolder[0]::toFront);
                Thread.sleep(100);
                captureRegionRobot(name, outDir, boundsOnEdt(dialogHolder[0]));
            } else {
                captureAndSave(name, outDir, dialogHolder[0]::getRootPane);
            }
        } catch (Exception e) {
            logFail(name, e);
        } finally {
            try {
                SwingUtilities.invokeAndWait(dialogHolder[0]::dispose);
            } catch (Exception ignored) {
                // Nothing more to do - the process is about to move on to the next shot.
            }
        }
    }

    // ------------------------------------------------------------------------------------
    // Net loading (mirrors PetriNetsFrame#importPnmlMenuItemActionPerformed without a
    // JFileChooser) and the marquee-selection gesture
    // ------------------------------------------------------------------------------------

    /**
     * Loads a sample PNML document onto the canvas, the same way File &gt; Open... does
     * ({@code PnmlModelParser} then {@code GraphCanvasModel.fromObjModel}), but pointed at a
     * fixed sample file instead of driving a {@code JFileChooser}. Must run on the EDT.
     *
     * @return the sample file that was loaded
     */
    private static Path loadSampleNet(PetriNetsFrame frame) throws Exception {
        Path sample = resolveSampleFile();
        GraphPetriObjModel objModel = new PnmlModelParser().parse(sample.toFile());
        GraphCanvasModel canvas = GraphCanvasModel.fromObjModel(objModel);
        PetriNetsPanel panel = resolvePetriNetsPanel(frame);
        panel.setCanvasModel(canvas);
        trySetNetNameField(frame, objModel.getName());
        panel.revalidate();
        frame.validate();
        panel.repaint();
        return sample;
    }

    /**
     * @return the richest available sample: the two-object composed PNML (two colour-coded
     *         Petri-object frames, a place fusion, cross-object arcs) if it can be found on
     *         disk, or the single-page "easy" sample that ships as an application resource
     *         otherwise.
     */
    private static Path resolveSampleFile() {
        Path root = findRepoRoot();
        Path preferred = root.resolve(SAMPLE_NET_PREFERRED);
        if (Files.isRegularFile(preferred)) {
            return preferred;
        }
        return root.resolve(SAMPLE_NET_FALLBACK);
    }

    private static Path findRepoRoot() {
        Path dir = Paths.get("").toAbsolutePath();
        for (int i = 0; i < 8 && dir != null; i++) {
            if (Files.isDirectory(dir.resolve("petri-model")) && Files.isDirectory(dir.resolve("petri-swing-ui"))) {
                return dir;
            }
            dir = dir.getParent();
        }
        // Nothing recognisable above the working directory - fall back to it as-is and let
        // the caller's own isRegularFile/fallback logic take it from there.
        return Paths.get("").toAbsolutePath();
    }

    /** Cosmetic only: sets the net-name text field via reflection so the header matches the
     *  loaded document. A failure here never fails the shot - the field just keeps whatever
     *  text it already had. */
    private static void trySetNetNameField(PetriNetsFrame frame, String name) {
        if (name == null) {
            return;
        }
        try {
            Field field = PetriNetsFrame.class.getDeclaredField("netNameTextField");
            field.setAccessible(true);
            Object value = field.get(frame);
            if (value instanceof JTextField textField) {
                textField.setText(name);
            }
        } catch (ReflectiveOperationException ignored) {
            // Cosmetic-only, see method comment.
        }
    }

    private static PetriNetsPanel resolvePetriNetsPanel(PetriNetsFrame frame) {
        PetriNetsPanel panel = frame.getPetriNetsPanel();
        if (panel != null) {
            return panel;
        }
        // Fallback for a hypothetical future PetriNetsFrame that no longer exposes the panel
        // directly: walk the component tree looking for one.
        PetriNetsPanel found = findComponentOfType(frame, PetriNetsPanel.class);
        if (found == null) {
            throw new IllegalStateException("No PetriNetsPanel found on the frame");
        }
        return found;
    }

    private static <T extends Component> T findComponentOfType(Container root, Class<T> type) {
        for (Component child : root.getComponents()) {
            if (type.isInstance(child)) {
                return type.cast(child);
            }
            if (child instanceof Container innerContainer) {
                T found = findComponentOfType(innerContainer, type);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    /**
     * Presses, drags across three intermediate points, then releases - all comfortably inside
     * the 1600x1000 canvas so the band encloses both Petri-object frames of the composed
     * sample. The release repeats the final drag point on purpose: the marquee band is computed
     * from the press point to the last {@code MOUSE_DRAGGED} point, so a release anywhere else
     * would move that corner and could collapse or shrink the band.
     */
    private static void dispatchMarqueeDrag(PetriNetsPanel panel) {
        java.awt.Point start = new java.awt.Point(15, 15);
        java.awt.Point[] dragPoints = {
                new java.awt.Point(250, 160),
                new java.awt.Point(500, 300),
                new java.awt.Point(760, 430),
        };
        java.awt.Point end = dragPoints[dragPoints.length - 1];

        postMouseEvent(panel, MouseEvent.MOUSE_PRESSED, start, 1);
        for (java.awt.Point point : dragPoints) {
            postMouseEvent(panel, MouseEvent.MOUSE_DRAGGED, point, 0);
        }
        postMouseEvent(panel, MouseEvent.MOUSE_RELEASED, end, 1);
    }

    private static void postMouseEvent(Component target, int id, java.awt.Point point, int clickCount) {
        MouseEvent event = new MouseEvent(
                target, id, System.currentTimeMillis(), InputEvent.BUTTON1_DOWN_MASK,
                point.x, point.y, clickCount, false, MouseEvent.BUTTON1);
        target.dispatchEvent(event);
    }

    // ------------------------------------------------------------------------------------
    // Theme (applied reflectively - ThemeManager may not exist yet in the checked-out code)
    // ------------------------------------------------------------------------------------

    private static void applyTheme(String themeArg) {
        if (themeArg == null) {
            return;
        }
        String upper = themeArg.toUpperCase(Locale.ROOT);
        try {
            Class<?> themeModeClass = Class.forName("ua.stetsenkoinna.theme.ThemeMode");
            Class<?> themeManagerClass = Class.forName("ua.stetsenkoinna.graphpresentation.theme.ThemeManager");
            Object mode = themeModeClass.getMethod("valueOf", String.class).invoke(null, upper);
            Method applyMode = themeManagerClass.getMethod("applyMode", themeModeClass);
            applyMode.invoke(null, mode);
            System.out.println("THEME applied: " + upper);
        } catch (ClassNotFoundException e) {
            System.out.println("THEME skip: " + e.getMessage()
                    + " does not exist yet - continuing without applying a theme");
        } catch (ReflectiveOperationException | RuntimeException e) {
            System.out.println("THEME fail: could not apply theme '" + themeArg + "' - " + e);
        }
    }

    // ------------------------------------------------------------------------------------
    // Rendering / bookkeeping plumbing
    // ------------------------------------------------------------------------------------

    private interface ComponentSupplier {
        JComponent get();
    }

    private interface ShotTask {
        void run() throws Exception;
    }

    private static void runShot(Options options, String name, ShotTask task) {
        if (options.shots != null && !options.shots.contains(name)) {
            return;
        }
        try {
            task.run();
        } catch (Throwable t) {
            logFail(name, t);
        }
    }

    /**
     * Waits for {@code window} to become {@link Window#isShowing()}, polling through
     * {@code invokeAndWait} - safe here because every caller of this method shows the window
     * either non-modally or via {@code invokeLater} beforehand, so the EDT is never stuck
     * waiting on something only this poll could unblock.
     */
    private static boolean waitUntilShowing(Window window, long timeoutMs) throws Exception {
        long deadline = System.currentTimeMillis() + timeoutMs;
        boolean[] showing = new boolean[1];
        do {
            SwingUtilities.invokeAndWait(() -> showing[0] = window.isShowing());
            if (showing[0]) {
                return true;
            }
            Thread.sleep(50);
        } while (System.currentTimeMillis() < deadline);
        return showing[0];
    }

    private static Rectangle boundsOnEdt(Window window) throws Exception {
        Rectangle[] holder = new Rectangle[1];
        SwingUtilities.invokeAndWait(() -> holder[0] = window.getBounds());
        return holder[0];
    }

    /** Grabs real on-screen pixels from {@code screenBounds} with {@link Robot} and saves them -
     *  the {@code --capture robot} counterpart of {@link #captureAndSave}. */
    private static void captureRegionRobot(String shotName, Path outDir, Rectangle screenBounds) {
        try {
            if (screenBounds == null || screenBounds.width <= 0 || screenBounds.height <= 0) {
                System.out.println("SHOT skip: " + shotName
                        + " - nothing showing on screen to capture (degenerate bounds)");
                SKIP_COUNT.incrementAndGet();
                return;
            }
            Robot robot = new Robot();
            BufferedImage image = robot.createScreenCapture(screenBounds);
            saveImage(shotName, outDir, image, "robot");
        } catch (Throwable t) {
            logFail(shotName, t);
        }
    }

    private static void captureAndSave(String shotName, Path outDir, ComponentSupplier supplier) {
        try {
            BufferedImage[] holder = new BufferedImage[1];
            Exception[] failure = new Exception[1];
            SwingUtilities.invokeAndWait(() -> {
                try {
                    holder[0] = renderComponent(supplier.get());
                } catch (Exception e) {
                    failure[0] = e;
                }
            });
            if (failure[0] != null) {
                throw failure[0];
            }
            saveImage(shotName, outDir, holder[0], null);
        } catch (Throwable t) {
            logFail(shotName, t);
        }
    }

    private static BufferedImage renderComponent(JComponent component) {
        Dimension size = component.getSize();
        if (size.width <= 0 || size.height <= 0) {
            size = component.getPreferredSize();
        }
        if (size.width <= 0 || size.height <= 0) {
            size = new Dimension(800, 600);
        }
        BufferedImage image = new BufferedImage(size.width, size.height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        Color background = component.getBackground();
        g.setColor(background != null ? background : Color.WHITE);
        g.fillRect(0, 0, size.width, size.height);
        component.printAll(g);
        g.dispose();
        return image;
    }

    /**
     * Writes {@code image} as a PNG and logs the {@code SHOT ok:} line both capture paths share.
     *
     * @param tag {@code null} for the original printall path (keeps its log line exactly as it
     *            always was), or a short marker like {@code "robot"} appended in brackets
     */
    private static void saveImage(String shotName, Path outDir, BufferedImage image, String tag)
            throws IOException {
        Path file = outDir.resolve(shotName + ".png");
        ImageIO.write(image, "png", file.toFile());
        long bytes = Files.size(file);
        String suffix = tag == null ? "" : " [" + tag + "]";
        System.out.println("SHOT ok: " + shotName + " -> " + file + " (" + bytes + " bytes)" + suffix);
        OK_COUNT.incrementAndGet();
    }

    private static void logFail(String shotName, Throwable t) {
        System.out.println("SHOT fail: " + shotName + " - " + t);
        FAIL_COUNT.incrementAndGet();
    }

    // ------------------------------------------------------------------------------------
    // CLI parsing
    // ------------------------------------------------------------------------------------

    private enum CaptureMode {
        /** Off-screen {@code printAll(Graphics)} into a {@link BufferedImage}. The default -
         *  unchanged from this harness's first version. */
        PRINTALL,
        /** Real {@code setVisible(true)} plus {@link Robot#createScreenCapture}. */
        ROBOT;

        static CaptureMode parse(String value) {
            try {
                return CaptureMode.valueOf(value.toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException(
                        "--capture must be 'printall' or 'robot', got: " + value);
            }
        }
    }

    private static final class Options {
        Path outDir;
        String theme;
        /** {@code null} means "every shot" - the CLI contract's default. */
        Set<String> shots;
        CaptureMode capture = CaptureMode.PRINTALL;

        static Options parse(String[] args) {
            Options options = new Options();
            for (int i = 0; i < args.length; i++) {
                String arg = args[i];
                switch (arg) {
                    case "--out" -> options.outDir = Paths.get(requireValue(args, ++i, "--out"));
                    case "--theme" -> options.theme = requireValue(args, ++i, "--theme");
                    case "--shots" -> options.shots = new LinkedHashSet<>(
                            Arrays.asList(requireValue(args, ++i, "--shots").split(",")));
                    case "--capture" -> options.capture = CaptureMode.parse(requireValue(args, ++i, "--capture"));
                    default -> System.out.println("Ignoring unrecognised argument: " + arg);
                }
            }
            if (options.outDir == null) {
                throw new IllegalArgumentException(
                        "Usage: ScreenshotHarness --out <dir> [--theme <system|light|dark>] "
                                + "[--shots <comma,separated,list>] [--capture <printall|robot>]");
            }
            return options;
        }

        private static String requireValue(String[] args, int index, String flag) {
            if (index >= args.length) {
                throw new IllegalArgumentException(flag + " requires a value");
            }
            return args[index];
        }
    }
}

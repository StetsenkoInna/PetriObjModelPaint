package ua.stetsenkoinna.updates;

import java.awt.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.swing.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ua.stetsenkoinna.config.AppSettings;
import ua.stetsenkoinna.graphpresentation.PetriNetsFrame;

public final class UpdateController {
    private static final Logger LOG = LoggerFactory.getLogger(UpdateController.class);
    private static final AtomicBoolean CHECKED = new AtomicBoolean();
    private UpdateController() { }
    public static void checkOnStartup(PetriNetsFrame frame) {
        // One call per application process. No timer, menu action, polling or retry loop.
        if (!CHECKED.compareAndSet(false,true)) return;
        UpdatePreferences preferences = new UpdatePreferences(AppSettings.shared());
        if (!preferences.enabled()) return;
        new SwingWorker<Optional<UpdateRelease>,Void>() {
            Path jar;
            @Override protected Optional<UpdateRelease> doInBackground() throws Exception {
                jar = installedJar();
                if (jar == null) return Optional.empty();
                Properties build = new Properties();
                try (var input = UpdateController.class.getResourceAsStream("/ua/stetsenkoinna/updates/build.properties")) {
                    if (input == null) return Optional.empty(); build.load(input);
                }
                String version = build.getProperty("version","");
                if (!version.matches("[0-9]+\\.[0-9]+\\.[0-9]+")) return Optional.empty();
                return new UpdateClient().check(jar,version,UpdateRelease.platform(System.getProperty("os.name")),System.getProperty("os.arch"));
            }
            @Override protected void done() {
                try {
                    var release = get();
                    if (release.isPresent() && preferences.shouldNotify(release.get().identity(),java.time.Instant.now()) && visibleOwner(frame)!=null) {
                        new UpdateDialog(visibleOwner(frame),release.get(),preferences,() -> install(frame,jar,release.get())).setVisible(true);
                    }
                } catch (Exception failure) { LOG.debug("Startup update check unavailable",failure); }
            }
        }.execute();
    }
    public static Path installedJar() throws Exception {
        Path path = Path.of(UpdateController.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        return Files.isRegularFile(path) && path.toString().endsWith(".jar") ? path.toRealPath() : null;
    }
    private static Window visibleOwner(PetriNetsFrame frame) {
        Window active = KeyboardFocusManager.getCurrentKeyboardFocusManager().getActiveWindow();
        if (active!=null && active.isShowing()) return active;
        if (frame.isShowing()) return frame;
        return Arrays.stream(Window.getWindows()).filter(Window::isShowing).findFirst().orElse(null);
    }
    private static void install(PetriNetsFrame frame, Path jar, UpdateRelease release) {
        Window owner = visibleOwner(frame);
        JDialog progress = new JDialog(owner,"Downloading update",Dialog.ModalityType.MODELESS);
        JPanel content = new JPanel(new BorderLayout(8,8)); content.setBorder(BorderFactory.createEmptyBorder(18,18,18,18));
        content.add(new JLabel("Downloading and checking PetriObjModelPaint " + release.version() + "..."),BorderLayout.NORTH);
        JProgressBar bar = new JProgressBar(); bar.setIndeterminate(true); content.add(bar,BorderLayout.CENTER);
        progress.setContentPane(content); progress.pack(); progress.setLocationRelativeTo(owner);
        progress.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        var ready = new java.util.concurrent.atomic.AtomicReference<UpdateClient.Prepared>();
        SwingWorker<UpdateClient.Prepared,Void> worker = new SwingWorker<>() {
            @Override protected UpdateClient.Prepared doInBackground() throws Exception {
                var staged = new UpdateClient().prepare(release,jar);
                ready.set(staged);
                if (isCancelled()) { var cleanup=ready.getAndSet(null); if (cleanup!=null) cleanup.close(); }
                return staged;
            }
            @Override protected void done() {
                progress.dispose();
                UpdateClient.Prepared prepared = null;
                try {
                    if (isCancelled()) { var cleanup=ready.getAndSet(null); if (cleanup!=null) cleanup.close(); return; }
                    prepared = get();
                    PetriNetsFrame.UpdateRestart decision = frame.prepareForUpdate();
                    if (!decision.proceed()) { prepared.close(); return; }
                    UpdateInstaller.handoff(prepared,release,jar,decision.reopen());
                    System.exit(0);
                } catch (Exception failure) {
                    if (prepared!=null) try { prepared.close(); } catch (Exception cleanup) { failure.addSuppressed(cleanup); }
                    LOG.warn("Update could not be prepared",failure);
                    Throwable cause = failure.getCause()==null ? failure : failure.getCause();
                    JOptionPane.showMessageDialog(owner,"The update was not installed. You can keep using this version.\n" + cause.getMessage(),"Update failed",JOptionPane.ERROR_MESSAGE);
                }
            }
        };
        JButton cancel = new JButton("Cancel"); cancel.addActionListener(e -> { worker.cancel(true); progress.dispose(); });
        content.add(cancel,BorderLayout.SOUTH); progress.pack(); progress.setVisible(true); worker.execute();
    }
    public static void cleanupPreviousInstaller(String[] args) {
        String directory = argument(args,"--cleanup-update"), parent = argument(args,"--updater-pid");
        if (directory == null || parent == null) return;
        Thread cleanup = new Thread(() -> {
            try {
                Path jar = installedJar(), staging = Path.of(directory).toAbsolutePath().normalize();
                if (jar==null || !staging.getParent().equals(jar.getParent()) || !staging.getFileName().toString().startsWith(".petri-update-")) return;
                var helper = ProcessHandle.of(Long.parseLong(parent));
                if (helper.isPresent()) helper.get().onExit().get(60,java.util.concurrent.TimeUnit.SECONDS);
                for (String name:java.util.List.of("helper.jar","new.jar","download.zip","install.log")) Files.deleteIfExists(staging.resolve(name));
                Files.deleteIfExists(staging);
            } catch (Exception failure) { LOG.debug("Update staging cleanup unavailable",failure); }
        },"update-cleanup");
        cleanup.setDaemon(true); cleanup.start();
    }
    public static String argument(String[] args,String key) {
        for (int i=0;i+1<args.length;i++) if (key.equals(args[i])) return args[i+1];
        return null;
    }
}

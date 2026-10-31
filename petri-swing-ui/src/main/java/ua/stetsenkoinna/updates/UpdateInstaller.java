package ua.stetsenkoinna.updates;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.TimeUnit;
import javax.swing.JOptionPane;

/** Runs from a copy of the old JAR after the editor has exited, including on Windows. */
public final class UpdateInstaller {
    private UpdateInstaller() { }
    public static Process handoff(UpdateClient.Prepared prepared, UpdateRelease release, Path target, Path reopen) throws IOException {
        return new ProcessBuilder(javaExecutable(), "-Duser.home=" + System.getProperty("user.home"),
                "-cp", prepared.helper().toString(), UpdateInstaller.class.getName(),
                Long.toString(ProcessHandle.current().pid()), prepared.jar().toString(), target.toString(),
                release.jarSha256(), System.getProperty("user.home"), reopen == null ? "" : reopen.toString())
                .redirectError(ProcessBuilder.Redirect.appendTo(prepared.directory().resolve("install.log").toFile()))
                .redirectOutput(ProcessBuilder.Redirect.appendTo(prepared.directory().resolve("install.log").toFile())).start();
    }
    public static void main(String[] args) throws Exception {
        if (args.length != 6) throw new IllegalArgumentException("Invalid update handoff");
        long parent = Long.parseLong(args[0]);
        Path source = Path.of(args[1]).toAbsolutePath().normalize();
        Path target = Path.of(args[2]).toAbsolutePath().normalize();
        Path directory = source.getParent();
        if (!directory.getParent().equals(target.getParent()) || !directory.getFileName().toString().startsWith(".petri-update-")) throw new IOException("Invalid update staging directory");
        ProcessHandle handle = ProcessHandle.of(parent).orElse(null);
        if (handle != null) handle.onExit().get(120, TimeUnit.SECONDS);
        Path backup = backup(target);
        boolean replaced = false;
        try (var channel = FileChannel.open(target.resolveSibling(".petri-update.lock"), StandardOpenOption.CREATE, StandardOpenOption.WRITE);
             var lock = channel.tryLock()) {
            if (lock == null) throw new IOException("Another update is already being installed");
            // Other processes may still be releasing handles on Windows.
            long until = System.nanoTime() + Duration.ofSeconds(20).toNanos();
            while (true) {
                try { replace(source, target, args[3]); replaced = true; break; }
                catch (java.nio.file.FileSystemException busy) {
                    if (System.nanoTime() >= until) throw busy;
                    Thread.sleep(250);
                }
            }
            Process launched = restart(target, args[4], args[5], directory);
            if (launched.waitFor(3, TimeUnit.SECONDS) && launched.exitValue() != 0) throw new IOException("The new version could not start");
        } catch (Exception failure) {
            if ((replaced || !Files.exists(target)) && Files.exists(backup)) Files.move(backup, target, StandardCopyOption.REPLACE_EXISTING);
            if (Files.isRegularFile(target)) restart(target, args[4], args[5], directory);
            JOptionPane.showMessageDialog(null, "The update was not installed. The previous version has been kept.\n" + failure.getMessage(), "Update failed", JOptionPane.ERROR_MESSAGE);
        }
    }
    interface Mover { void move(Path from, Path to) throws IOException; }
    static void replace(Path source, Path target, String expected) throws Exception {
        replace(source, target, expected, (from,to) -> Files.move(from,to,StandardCopyOption.REPLACE_EXISTING));
    }
    static void replace(Path source, Path target, String expected, Mover mover) throws Exception {
        if (!Files.isRegularFile(target) || source.equals(target)) throw new IOException("The installed application is missing");
        if (!UpdateClient.sha256(source).equals(expected)) throw new IOException("Replacement checksum changed before installation");
        Path backup = backup(target);
        mover.move(target, backup);
        try { mover.move(source, target); }
        catch (Exception failure) {
            try { mover.move(backup, target); } catch (Exception rollback) { failure.addSuppressed(rollback); }
            throw failure;
        }
    }
    private static Path backup(Path target) { return target.resolveSibling(target.getFileName() + ".previous"); }
    private static Process restart(Path jar, String profile, String reopen, Path cleanup) throws IOException {
        List<String> command = new ArrayList<>(List.of(javaExecutable(), "-Duser.home=" + profile, "-jar", jar.toString(),
                "--cleanup-update", cleanup.toString(), "--updater-pid", Long.toString(ProcessHandle.current().pid())));
        if (!reopen.isBlank()) command.addAll(List.of("--open", reopen));
        return new ProcessBuilder(command).directory(jar.getParent().toFile()).start();
    }
    static String javaExecutable() {
        return Path.of(System.getProperty("java.home"), "bin", System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("win") ? "javaw.exe" : "java").toString();
    }
}

package ua.stetsenkoinna.updates;

import java.nio.file.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.jar.*;
import org.junit.*;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;

public class UpdateInstallerTest {
    @Rule public TemporaryFolder folder=new TemporaryFolder();
    @Test public void swapsJarAndKeepsPreviousVersion() throws Exception {
        Path target=folder.getRoot().toPath().resolve("app.jar"),source=folder.getRoot().toPath().resolve("new.jar");
        Files.writeString(target,"old"); Files.writeString(source,"new");
        UpdateInstaller.replace(source,target,UpdateClient.sha256(source));
        assertEquals("new",Files.readString(target));
        assertEquals("old",Files.readString(target.resolveSibling("app.jar.previous")));
    }
    @Test public void restoresOldJarWhenReplacementMoveFails() throws Exception {
        Path target=folder.getRoot().toPath().resolve("app.jar"),source=folder.getRoot().toPath().resolve("new.jar");
        Files.writeString(target,"old"); Files.writeString(source,"new");
        assertThrows(IOException.class,() -> UpdateInstaller.replace(source,target,UpdateClient.sha256(source),(from,to) -> {
            if(from.equals(source)) throw new IOException("Disk failure");
            Files.move(from,to,StandardCopyOption.REPLACE_EXISTING);
        }));
        assertEquals("old",Files.readString(target));
    }
    @Test public void refusesChangedReplacementBeforeTouchingTarget() throws Exception {
        Path target=folder.getRoot().toPath().resolve("app.jar"),source=folder.getRoot().toPath().resolve("new.jar");
        Files.writeString(target,"old"); Files.writeString(source,"tampered");
        assertThrows(IOException.class,() -> UpdateInstaller.replace(source,target,"0".repeat(64)));
        assertEquals("old",Files.readString(target));
        assertFalse(Files.exists(target.resolveSibling("app.jar.previous")));
    }
    public static class RecordingApp {
        public static void main(String[] args) throws Exception {
            String output=null;
            for(int i=0;i+1<args.length;i++) if(args[i].equals("--open")) output=args[i+1];
            if(output!=null) Files.writeString(Path.of(output),"restarted with saved model");
        }
    }
    @Test public void helperProcessWaitsForParentReplacesAndRelaunchesWithSavedModel() throws Exception {
        Path root=folder.getRoot().toPath(),target=root.resolve("installed app.jar");
        Path staging=Files.createDirectory(root.resolve(".petri-update-test")),source=staging.resolve("new.jar"),marker=root.resolve("saved model.pnml");
        Files.writeString(target,"old");
        var manifest=new Manifest(); manifest.getMainAttributes().putValue("Manifest-Version","1.0"); manifest.getMainAttributes().putValue("Main-Class",RecordingApp.class.getName());
        try(var jar=new JarOutputStream(Files.newOutputStream(source),manifest)) {
            for(Class<?> clazz:List.of(RecordingApp.class)) {
                String name=clazz.getName().replace('.','/')+".class";
                jar.putNextEntry(new JarEntry(name));
                try(var input=clazz.getResourceAsStream("/"+name)) { input.transferTo(jar); }
                jar.closeEntry();
            }
        }
        // RecordingApp intentionally avoids loading the desktop and its dependencies.
        String java=Path.of(System.getProperty("java.home"),"bin","java.exe").toString();
        if(!Files.isRegularFile(Path.of(java))) java=Path.of(System.getProperty("java.home"),"bin","java").toString();
        Process parent=new ProcessBuilder(java,"-version").redirectErrorStream(true).start(); parent.waitFor();
        Path log=root.resolve("helper.log");
        Process helper=new ProcessBuilder(java,"-cp",System.getProperty("java.class.path"),UpdateInstaller.class.getName(),Long.toString(parent.pid()),source.toString(),target.toString(),UpdateClient.sha256(source),root.toString(),marker.toString()).redirectErrorStream(true).redirectOutput(log.toFile()).start();
        assertTrue("Helper did not finish: "+Files.readString(log),helper.waitFor(15,TimeUnit.SECONDS));
        assertEquals(Files.readString(log),0,helper.exitValue());
        assertEquals("restarted with saved model",Files.readString(marker));
        assertEquals("old",Files.readString(target.resolveSibling("installed app.jar.previous")));
    }
}

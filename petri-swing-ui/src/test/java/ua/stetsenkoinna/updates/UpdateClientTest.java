package ua.stetsenkoinna.updates;

import java.io.*;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.jar.*;
import java.util.zip.*;
import org.junit.*;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;

public class UpdateClientTest {
    @Rule public TemporaryFolder folder=new TemporaryFolder();
    private final Map<URI,byte[]> responses=new HashMap<>();
    private Path installed;
    private UpdateRelease release;
    private UpdateClient client;
    @Before public void setup() throws Exception {
        installed=folder.getRoot().toPath().resolve("app.jar"); Files.writeString(installed,"previous application");
        configure("2.8.0","package/petri-swing-ui-windows.jar");
        client=new UpdateClient((uri,limit) -> { if (!responses.containsKey(uri)) throw new IOException("Unexpected request: "+uri); return responses.get(uri); });
    }
    private void configure(String version,String jarPath) throws Exception {
        byte[] jar=jar(version);
        var bytes=new ByteArrayOutputStream();
        try(var zip=new ZipOutputStream(bytes)) { zip.putNextEntry(new ZipEntry(jarPath)); zip.write(jar); zip.closeEntry(); }
        byte[] archive=bytes.toByteArray();
        URI url=URI.create("https://github.com/"+UpdateClient.REPOSITORY+"/releases/download/v"+version+"/petri-swing-ui-"+version+"-windows.zip");
        release=new UpdateRelease(version,url,UpdateClient.sha256(archive),UpdateClient.sha256(jar),archive.length,"windows","amd64");
        URI manifest=URI.create("https://github.com/"+UpdateClient.REPOSITORY+"/releases/download/v"+version+"/desktop-update.json");
        responses.put(UpdateClient.LATEST,UpdateClient.JSON.writeValueAsBytes(Map.of("tag_name","v"+version,"assets",List.of(Map.of("name","desktop-update.json","browser_download_url",manifest.toString())))));
        responses.put(manifest,UpdateClient.JSON.writeValueAsBytes(Map.of("schema_version",1,"version",version,"minimum_java",23,"platforms",Map.of("windows",Map.of("url",url.toString(),"sha256",release.archiveSha256(),"jar_sha256",release.jarSha256(),"size",archive.length,"arch","amd64")))));
        responses.put(url,archive);
    }
    private byte[] jar(String version) throws Exception {
        var manifest=new Manifest(); manifest.getMainAttributes().putValue("Manifest-Version","1.0"); manifest.getMainAttributes().putValue("Main-Class","ua.stetsenkoinna.PetriObjPaintApp");
        var bytes=new ByteArrayOutputStream();
        try(var jar=new JarOutputStream(bytes,manifest)) {
            for(var entry:Map.of("ua/stetsenkoinna/PetriObjPaintApp.class",new byte[]{1},"ua/stetsenkoinna/updates/build.properties",("version="+version+"\narch=amd64\n").getBytes(StandardCharsets.UTF_8)).entrySet()) {
                jar.putNextEntry(new JarEntry(entry.getKey())); jar.write(entry.getValue()); jar.closeEntry();
            }
        }
        return bytes.toByteArray();
    }
    @Test public void preparesVerifiedJarWithoutChangingInstalledApplication() throws Exception {
        var found=client.check(installed,"2.7.0","windows","x86_64"); assertTrue(found.isPresent());
        try(var staged=client.prepare(found.get(),installed)) {
            assertEquals(release.jarSha256(),UpdateClient.sha256(staged.jar()));
            assertEquals("previous application",Files.readString(staged.helper()));
            assertEquals("previous application",Files.readString(installed));
        }
        try(var children=Files.list(installed.getParent())) { assertEquals(1,children.count()); }
    }
    @Test public void recognizesRebuiltSameTagAndDoesNotOfferIdenticalBuild() throws Exception {
        configure("2.7.0","petri-swing-ui-windows.jar");
        assertTrue(client.check(installed,"2.7.0","windows","amd64").isPresent());
        Files.write(installed,jar("2.7.0"));
        // The JAR fixture can contain ZIP timestamps; use the exact bytes from the archive.
        try(var zip=new ZipInputStream(new ByteArrayInputStream(responses.get(release.archive())))) { zip.getNextEntry(); Files.write(installed,zip.readAllBytes()); }
        assertTrue(client.check(installed,"2.7.0","windows","amd64").isEmpty());
    }
    @Test public void ignoresDowngradesUnsupportedArchitecturesAndIncompleteReleases() throws Exception {
        assertTrue(client.check(installed,"2.9.0","windows","amd64").isEmpty());
        assertTrue(client.check(installed,"2.7.0","windows","arm64").isEmpty());
        responses.put(UpdateClient.LATEST,UpdateClient.JSON.writeValueAsBytes(Map.of("tag_name","v2.8.0","assets",List.of())));
        assertTrue(client.check(installed,"2.7.0","windows","amd64").isEmpty());
    }
    @Test public void rejectsCorruptionAndRemovesStagingWithoutChangingInstallation() throws Exception {
        responses.put(release.archive(),new byte[]{9,9});
        assertThrows(IOException.class,() -> client.prepare(release,installed));
        assertEquals("previous application",Files.readString(installed));
        try(var children=Files.list(installed.getParent())) { assertEquals(1,children.count()); }
    }
    @Test public void rejectsTraversalAndForeignDownloadUrls() throws Exception {
        configure("2.8.0","../petri-swing-ui-windows.jar");
        assertThrows(IOException.class,() -> client.prepare(release,installed));
        assertFalse(Files.exists(installed.getParent().getParent().resolve("petri-swing-ui-windows.jar")));
        assertThrows(IOException.class,() -> UpdateClient.officialAsset("https://example.com/a.zip","v2.8.0","a.zip"));
    }
    @Test public void comparesNumericVersionsAndBoundsResponses() throws Exception {
        assertTrue(UpdateRelease.compareVersions("2.10.0","2.9.9")>0);
        assertThrows(IOException.class,() -> UpdateClient.limited(new ByteArrayInputStream(new byte[20]),10));
    }
}

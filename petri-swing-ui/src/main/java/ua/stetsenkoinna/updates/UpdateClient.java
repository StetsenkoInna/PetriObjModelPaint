package ua.stetsenkoinna.updates;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.*;
import java.net.URI;
import java.net.http.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.*;
import java.util.jar.JarFile;
import java.util.zip.ZipFile;

/** Reads only the official repository and prepares a verified replacement without changing the installation. */
public final class UpdateClient {
    static final String REPOSITORY = "StetsenkoInna/PetriObjModelPaint";
    static final URI LATEST = URI.create("https://api.github.com/repos/" + REPOSITORY + "/releases/latest");
    static final ObjectMapper JSON = new ObjectMapper();
    static final int MAX_ARCHIVE = 128 * 1024 * 1024;
    public interface Transport { byte[] get(URI uri, int limit) throws Exception; }
    private final Transport transport;
    public UpdateClient() { this(UpdateClient::fetch); }
    UpdateClient(Transport transport) { this.transport = transport; }

    public Optional<UpdateRelease> check(Path installedJar, String currentVersion, String platform, String arch) throws Exception {
        JsonNode latest = JSON.readTree(transport.get(LATEST, 1_048_576));
        if (latest.path("draft").asBoolean() || latest.path("prerelease").asBoolean()) return Optional.empty();
        String tag = latest.path("tag_name").asText();
        if (!tag.matches("v[0-9]+\\.[0-9]+\\.[0-9]+")) return Optional.empty();
        String version = tag.substring(1);
        if (UpdateRelease.compareVersions(version, currentVersion) < 0) return Optional.empty();
        JsonNode manifestAsset = null;
        for (JsonNode asset : latest.path("assets")) if ("desktop-update.json".equals(asset.path("name").asText())) manifestAsset = asset;
        // A release still being assembled is checked again on the next application launch.
        if (manifestAsset == null) return Optional.empty();
        URI manifestUrl = officialAsset(manifestAsset.path("browser_download_url").asText(), tag, "desktop-update.json");
        JsonNode manifest = JSON.readTree(transport.get(manifestUrl, 65_536));
        if (manifest.path("schema_version").asInt() != 1 || !version.equals(manifest.path("version").asText())) throw new IOException("Invalid update manifest");
        if (manifest.path("minimum_java").asInt(23) > Runtime.version().feature()) throw new IOException("The update needs a newer Java runtime");
        JsonNode asset = manifest.path("platforms").path(platform);
        if (asset.isMissingNode() || !UpdateRelease.architecture(arch).equals(UpdateRelease.architecture(asset.path("arch").asText()))) return Optional.empty();
        String name = "petri-swing-ui-" + version + "-" + platform + ".zip";
        URI url = officialAsset(asset.path("url").asText(), tag, name);
        String archiveHash = hashValue(asset.path("sha256").asText()), jarHash = hashValue(asset.path("jar_sha256").asText());
        long size = asset.path("size").asLong();
        if (size < 1 || size > MAX_ARCHIVE) throw new IOException("Invalid update archive size");
        if (UpdateRelease.compareVersions(version, currentVersion) == 0 && sha256(installedJar).equals(jarHash)) return Optional.empty();
        return Optional.of(new UpdateRelease(version, url, archiveHash, jarHash, size, platform, asset.path("arch").asText()));
    }

    public Prepared prepare(UpdateRelease release, Path installedJar) throws Exception {
        Path target = installedJar.toRealPath();
        Path directory = Files.createTempDirectory(target.getParent(), ".petri-update-");
        Prepared prepared = new Prepared(directory, directory.resolve("new.jar"), directory.resolve("helper.jar"));
        boolean success = false;
        try {
            byte[] data = transport.get(release.archive(), MAX_ARCHIVE);
            if (data.length != release.size() || !sha256(data).equals(release.archiveSha256())) throw new IOException("The downloaded archive failed SHA-256 verification");
            Path archive = directory.resolve("download.zip");
            Files.write(archive, data);
            String expected = "petri-swing-ui-" + release.platform() + ".jar";
            try (ZipFile zip = new ZipFile(archive.toFile())) {
                var jars = zip.stream().filter(e -> !e.isDirectory() && e.getName().endsWith(".jar")).toList();
                if (jars.size() != 1) throw new IOException("The update must contain one application JAR");
                var entry = jars.getFirst();
                String path = entry.getName();
                if (path.contains("\\") || path.startsWith("/") || Arrays.asList(path.split("/")).contains("..") || !Path.of(path).getFileName().toString().equals(expected)) throw new IOException("Invalid application path in update archive");
                try (InputStream input = zip.getInputStream(entry)) { Files.write(prepared.jar(), limited(input, MAX_ARCHIVE)); }
            }
            if (!sha256(prepared.jar()).equals(release.jarSha256())) throw new IOException("The replacement JAR failed SHA-256 verification");
            try (JarFile jar = new JarFile(prepared.jar().toFile())) {
                if (jar.getManifest() == null || !"ua.stetsenkoinna.PetriObjPaintApp".equals(jar.getManifest().getMainAttributes().getValue("Main-Class"))
                        || jar.getEntry("ua/stetsenkoinna/PetriObjPaintApp.class") == null) throw new IOException("The downloaded file is not the desktop application");
                var build = jar.getEntry("ua/stetsenkoinna/updates/build.properties");
                if (build == null) throw new IOException("The update has no build metadata");
                Properties properties = new Properties();
                try (var input = jar.getInputStream(build)) { properties.load(input); }
                if (!release.version().equals(properties.getProperty("version")) || !UpdateRelease.architecture(release.arch()).equals(UpdateRelease.architecture(properties.getProperty("arch", "")))) throw new IOException("Replacement build metadata differs from the release manifest");
            }
            Files.copy(target, prepared.helper());
            Files.delete(archive);
            success = true;
            return prepared;
        } finally { if (!success) prepared.close(); }
    }

    public record Prepared(Path directory, Path jar, Path helper) implements AutoCloseable {
        @Override public void close() throws IOException {
            for (String name : List.of("download.zip", "new.jar", "helper.jar", "install.log")) Files.deleteIfExists(directory.resolve(name));
            Files.deleteIfExists(directory);
        }
    }
    static URI officialAsset(String value, String tag, String name) throws IOException {
        URI expected = URI.create("https://github.com/" + REPOSITORY + "/releases/download/" + tag + "/" + name);
        if (!expected.toString().equals(value)) throw new IOException("Update URL is outside the official release");
        return expected;
    }
    private static String hashValue(String value) throws IOException {
        if (!value.matches("[a-f0-9]{64}")) throw new IOException("Missing or invalid SHA-256 in update manifest");
        return value;
    }
    public static String sha256(Path path) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream input = Files.newInputStream(path)) { byte[] buffer = new byte[65_536]; for (int count; (count=input.read(buffer))!=-1;) digest.update(buffer,0,count); }
        return HexFormat.of().formatHex(digest.digest());
    }
    static String sha256(byte[] bytes) throws Exception { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
    static byte[] limited(InputStream input, int limit) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream(); byte[] buffer = new byte[65_536];
        for (int count; (count=input.read(buffer))!=-1;) {
            if (Thread.currentThread().isInterrupted()) throw new java.io.InterruptedIOException("Update cancelled");
            if (bytes.size() > limit-count) throw new IOException("Update response exceeds its size limit");
            bytes.write(buffer,0,count);
        }
        return bytes.toByteArray();
    }
    private static byte[] fetch(URI uri, int limit) throws Exception {
        try (HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).followRedirects(HttpClient.Redirect.NORMAL).build()) {
            HttpRequest request = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(60)).header("User-Agent", "PetriObjModelPaint-updater").header("Accept", "application/vnd.github+json").GET().build();
            var response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
            try (InputStream body = response.body()) {
                if (response.statusCode() != 200) throw new IOException("GitHub returned HTTP " + response.statusCode());
                return limited(body, limit);
            }
        }
    }
}

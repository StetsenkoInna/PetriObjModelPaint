package ua.stetsenkoinna.updates;

import java.net.URI;
import java.util.Locale;

public record UpdateRelease(String version, URI archive, String archiveSha256, String jarSha256,
                            long size, String platform, String arch) {
    public String identity() { return version + ":" + jarSha256; }
    public static String platform(String os) {
        String name = os.toLowerCase(Locale.ROOT);
        if (name.contains("win")) return "windows";
        if (name.contains("mac")) return "macos";
        if (name.contains("linux")) return "linux";
        throw new IllegalArgumentException("Automatic updates are unavailable on this operating system");
    }
    public static String architecture(String arch) {
        return switch (arch.toLowerCase(Locale.ROOT)) {
            case "amd64", "x86_64", "x64" -> "x64";
            case "aarch64", "arm64" -> "arm64";
            default -> arch.toLowerCase(Locale.ROOT);
        };
    }
    public static int compareVersions(String left, String right) {
        if (!left.matches("[0-9]+\\.[0-9]+\\.[0-9]+") || !right.matches("[0-9]+\\.[0-9]+\\.[0-9]+")) throw new IllegalArgumentException("Invalid release version");
        String[] a = left.split("\\."), b = right.split("\\.");
        for (int i=0; i<3; i++) {
            int order = new java.math.BigInteger(a[i]).compareTo(new java.math.BigInteger(b[i]));
            if (order != 0) return order;
        }
        return 0;
    }
}

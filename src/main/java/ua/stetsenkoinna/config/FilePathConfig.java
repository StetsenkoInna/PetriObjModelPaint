package ua.stetsenkoinna.config;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Centralized file path configuration for the application.
 * Provides cross-platform file path resolution for NetLibrary and other external files.
 *
 * @author Serhii Rybak
 */
public class FilePathConfig {

    private static final Logger LOGGER = Logger.getLogger(FilePathConfig.class.getName());
    private static final Properties properties = new Properties();

    // Default path configurations (fallback if properties file is not found)
    private static final String[] DEFAULT_NET_LIBRARY_PATHS = {
        "src/main/java/ua/stetsenkoinna/LibNet/NetLibrary.java",
        "src/LibNet/NetLibrary.java"
    };

    static {
        loadProperties();
    }

    /**
     * Load properties from application.properties file
     */
    private static void loadProperties() {
        try (InputStream input = FilePathConfig.class.getClassLoader()
                .getResourceAsStream("application.properties")) {
            if (input != null) {
                properties.load(input);
            } else {
                LOGGER.log(Level.WARNING, "application.properties not found, using default paths");
            }
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Failed to load application.properties, using default paths", e);
        }
    }

    /**
     * Get the path to NetLibrary.java file, searching through configured paths
     * until a valid file is found.
     *
     * @return Path to NetLibrary.java or null if not found
     */
    public static Path getNetLibraryPath() {
        String userDir = System.getProperty("user.dir");

        // Try custom path from properties first
        String customPath = properties.getProperty("netlibrary.path");
        if (customPath != null && !customPath.isEmpty()) {
            Path path = Paths.get(userDir, customPath);
            if (Files.exists(path)) {
                LOGGER.log(Level.INFO, "Found NetLibrary.java at custom path: {0}", path);
                return path;
            }
        }

        // Try default paths
        for (String pathStr : DEFAULT_NET_LIBRARY_PATHS) {
            Path path = Paths.get(userDir, pathStr);
            if (Files.exists(path)) {
                LOGGER.log(Level.INFO, "Found NetLibrary.java at: {0}", path);
                return path;
            }
        }

        LOGGER.log(Level.SEVERE, "NetLibrary.java not found in any configured path. Working directory: {0}", userDir);
        return null;
    }

    /**
     * Get the path to NetLibrary.java file as a string.
     *
     * @return Path string to NetLibrary.java or null if not found
     */
    public static String getNetLibraryPathString() {
        Path path = getNetLibraryPath();
        return path != null ? path.toString() : null;
    }

    /**
     * Check if NetLibrary.java exists at any configured location.
     *
     * @return true if NetLibrary.java is found, false otherwise
     */
    public static boolean netLibraryExists() {
        return getNetLibraryPath() != null;
    }

    /**
     * Get all configured NetLibrary search paths for debugging purposes.
     *
     * @return Array of search path strings
     */
    public static String[] getNetLibrarySearchPaths() {
        String userDir = System.getProperty("user.dir");
        String customPath = properties.getProperty("netlibrary.path");

        if (customPath != null && !customPath.isEmpty()) {
            String[] paths = new String[DEFAULT_NET_LIBRARY_PATHS.length + 1];
            paths[0] = Paths.get(userDir, customPath).toString();
            for (int i = 0; i < DEFAULT_NET_LIBRARY_PATHS.length; i++) {
                paths[i + 1] = Paths.get(userDir, DEFAULT_NET_LIBRARY_PATHS[i]).toString();
            }
            return paths;
        }

        String[] paths = new String[DEFAULT_NET_LIBRARY_PATHS.length];
        for (int i = 0; i < DEFAULT_NET_LIBRARY_PATHS.length; i++) {
            paths[i] = Paths.get(userDir, DEFAULT_NET_LIBRARY_PATHS[i]).toString();
        }
        return paths;
    }
}
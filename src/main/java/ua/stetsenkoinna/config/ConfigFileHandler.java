package ua.stetsenkoinna.config;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ConfigFileHandler {
    private static final Logger LOGGER = Logger.getLogger(ConfigFileHandler.class.getName());
    private static final Properties properties = new Properties();
    private static Path settingsFile;
    private static Path settingsDir;

    private static final String appFolderName = ".PetriObjModelPaint";
    private static final String appPropertiesFileName = "application.properties";

    static {
        loadProperties();
    }

    private static void loadProperties() {
        String userHome = System.getProperty("user.home");
        settingsDir = Paths.get(userHome, appFolderName);
        settingsFile = settingsDir.resolve(appPropertiesFileName);

        try {
            if (!Files.exists(settingsDir)) {
                Files.createDirectories(settingsDir);
            }
            if (!Files.exists(settingsFile)) {
                Files.createFile(settingsFile);
            } else {
                try (InputStream in = Files.newInputStream(settingsFile)) {
                    properties.load(in);
                }
            }
        } catch (IOException ex) {
            LOGGER.log(Level.WARNING, "Could not create settings directory", ex);
        }
    }

    public static String getProperty(String key) {
        return properties.getProperty(key);
    }

    public static void setProperty(String key, String value) {
        properties.setProperty(key, value);
        saveProperties();
    }

    private static void saveProperties() {
        try (OutputStream out = Files.newOutputStream(settingsFile)) {
            properties.store(out, "Application properties");
        } catch (IOException ex) {
            LOGGER.log(Level.WARNING, "Could not save settings file", ex);
        }
    }
}

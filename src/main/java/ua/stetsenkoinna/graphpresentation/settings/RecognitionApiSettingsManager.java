package ua.stetsenkoinna.graphpresentation.settings;

import ua.stetsenkoinna.config.AppDirectoryType;
import ua.stetsenkoinna.config.UserDirectoryManager;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Manages loading and saving of Recognition API settings, including the API URL.
 * <p>
 * This class stores settings in a user-specific configuration directory using a properties file.
 * It is responsible for reading settings on startup, making them available to the application,
 * and persisting updates when changed.
 *
 * <h2>Features</h2>
 * <ul>
 *     <li>Loads settings from a properties file located in the user's configuration directory.</li>
 *     <li>Automatically creates the properties file if it is missing.</li>
 *     <li>Saves updated API URL to properties file.</li>
 *     <li>Provides getters and setters for API-related values.</li>
 * </ul>
 *
 * <h2>Properties Used</h2>
 * <ul>
 *     <li><b>PETRI_RECOGNITION_API_URL</b> – URL of the recognition API endpoint</li>
 * </ul>
 *
 * <h2>Typical Usage</h2>
 * <pre>
 * UserDirectoryManager udm = new UserDirectoryManager();
 * RecognitionApiSettingsManager settings = new RecognitionApiSettingsManager(udm);
 *
 * String apiUrl = settings.getApiUrl();
 *
 * // Modify and save:
 * settings.setApiUrl("https://api.example.com");
 * settings.save();
 * </pre>
 *
 * @author  Bohdan Hrontkovskyi
 * @since   14.11.2025
 * @see     UserDirectoryManager
 */
public class RecognitionApiSettingsManager {

    private static final Logger LOGGER = Logger.getLogger(RecognitionApiSettingsManager.class.getName());

    /** Key for storing the API URL in the properties file. */
    private static final String RECOGNITION_API_URL = "PETRI_RECOGNITION_API_URL";

    /** Name of the properties file storing API settings. */
    private static final String PROPERTIES_FILENAME = "api.properties";

    private final UserDirectoryManager userDirectoryManager;
    private final Path propertiesFilePath;
    private final Properties properties = new Properties();

    private String apiUrl;

    /**
     * Creates a manager responsible for loading and saving Recognition API settings.
     *
     * @param userDirectoryManager manager used to resolve the properties file location
     */
    public RecognitionApiSettingsManager(UserDirectoryManager userDirectoryManager) {
        this.userDirectoryManager = userDirectoryManager;
        this.propertiesFilePath = userDirectoryManager.getFilePath(PROPERTIES_FILENAME, AppDirectoryType.CONFIGS);
        loadSettings();
    }

    /**
     * Loads settings from the properties file. If the file does not exist, it is created.
     * Default values are applied if properties are missing:
     * <ul>
     *     <li>API URL defaults to <code>http://localhost:8000</code></li>
     * </ul>
     */
    private void loadSettings() {
        try {
            if (!Files.exists(propertiesFilePath)) {
                userDirectoryManager.createFileIfMissing(PROPERTIES_FILENAME, AppDirectoryType.CONFIGS);
                LOGGER.log(Level.INFO, "Created missing properties file: " + propertiesFilePath);
            }

            try (InputStream input = Files.newInputStream(propertiesFilePath)) {
                properties.load(input);
            }

            this.apiUrl = properties.getProperty(RECOGNITION_API_URL, "http://localhost:8000");
        } catch (IOException ex) {
            LOGGER.log(Level.WARNING, "Failed to load Recognition API settings", ex);
        }
    }

    /**
     * Saves current settings into the properties file. If an error occurs, it is logged
     * but does not propagate further.
     */
    public void save() {
        try {
            properties.setProperty(RECOGNITION_API_URL, getApiUrl());

            try (OutputStream output = Files.newOutputStream(propertiesFilePath)) {
                properties.store(output, "Recognition API settings");
            }

            LOGGER.log(Level.INFO, "Recognition API settings saved to " + propertiesFilePath);
        } catch (IOException ex) {
            LOGGER.log(Level.SEVERE, "Failed to save Recognition API settings", ex);
        }
    }

    /**
     * Returns the stored API URL.
     *
     * @return the recognition API endpoint URL (never {@code null})
     */
    public String getApiUrl() {
        return apiUrl;
    }

    /**
     * Sets the API URL (must be saved using {@link #save()} to persist).
     *
     * @param apiUrl new API endpoint URL
     */
    public void setApiUrl(String apiUrl) {
        this.apiUrl = apiUrl;
    }
}

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

public class RecognitionApiSettingsManager {

    private static final Logger LOGGER = Logger.getLogger(RecognitionApiSettingsManager.class.getName());

    private static final String RECOGNITION_API_URL = "PETRI_RECOGNITION_API_URL";
    private static final String ROBOFLOW_API_KEY = "ROBOFLOW_API_KEY";
    private static final String PROPERTIES_FILENAME = "api.properties";

    private final UserDirectoryManager userDirectoryManager;
    private final Path propertiesFilePath;
    private final Properties properties = new Properties();

    private String apiUrl;
    private String apiKey;

    public RecognitionApiSettingsManager(UserDirectoryManager userDirectoryManager) {
        this.userDirectoryManager = userDirectoryManager;
        this.propertiesFilePath = userDirectoryManager.getFilePath(PROPERTIES_FILENAME, AppDirectoryType.CONFIGS);
        loadSettings();
    }

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
            this.apiKey = properties.getProperty(ROBOFLOW_API_KEY, "");
        } catch (IOException ex) {
            LOGGER.log(Level.WARNING, "Failed to load Recognition API settings", ex);
        }
    }

    public void save() {
        try {
            properties.setProperty(RECOGNITION_API_URL, getApiUrl());
            properties.setProperty(ROBOFLOW_API_KEY, getApiKey());

            try (OutputStream output = Files.newOutputStream(propertiesFilePath)) {
                properties.store(output, "Recognition API settings");
            }

            LOGGER.log(Level.INFO, "Recognition API settings saved to " + propertiesFilePath);
        } catch (IOException ex) {
            LOGGER.log(Level.SEVERE, "Failed to save Recognition API settings", ex);
        }
    }

    public String getApiUrl() {
        return apiUrl;
    }

    public void setApiUrl(String apiUrl) {
        this.apiUrl = apiUrl;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    private boolean isNullOrEmpty(String value) {
        return value == null || value.trim().isEmpty();
    }
}

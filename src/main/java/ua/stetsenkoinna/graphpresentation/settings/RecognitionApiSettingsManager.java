package ua.stetsenkoinna.graphpresentation.settings;

import ua.stetsenkoinna.config.ConfigFileHandler;

import java.io.IOException;

public class RecognitionApiSettingsManager {
    private static final String RECOGNITION_API_URL = "PETRI_RECOGNITION_API_URL";
    private static final String ROBOFLOW_API_KEY = "ROBOFLOW_API_KEY";

    private String apiUrl;
    private String apiKey;

    public RecognitionApiSettingsManager() {
        loadSettings();
    }

    private void loadSettings() {
        this.apiUrl = ConfigFileHandler.getProperty(RECOGNITION_API_URL);
        this.apiKey = ConfigFileHandler.getProperty(ROBOFLOW_API_KEY);

        // Set to default if there is no API settings
        if (isNullOrEmpty(apiUrl) && isNullOrEmpty(apiKey)) {
            this.apiUrl = "http://localhost:8000";
            this.apiKey = "";
            ConfigFileHandler.setProperty(RECOGNITION_API_URL, this.apiUrl);
            ConfigFileHandler.setProperty(ROBOFLOW_API_KEY, this.apiKey);
        }
    }

    public void save() {
        ConfigFileHandler.setProperty(RECOGNITION_API_URL, this.getApiUrl());
        ConfigFileHandler.setProperty(ROBOFLOW_API_KEY, this.getApiKey());
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

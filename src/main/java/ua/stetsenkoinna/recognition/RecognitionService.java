package ua.stetsenkoinna.recognition;

import ua.stetsenkoinna.config.AppDirectoryType;
import ua.stetsenkoinna.config.UserDirectoryManager;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.UUID;
import java.util.logging.Logger;

public class RecognitionService {

    private static final Logger LOGGER = Logger.getLogger(RecognitionService.class.getName());

    private final RecognitionApiClient apiClient;
    private final UserDirectoryManager userDirectoryManager;

    public RecognitionService(RecognitionApiClient apiClient, UserDirectoryManager userDirectoryManager) {
        this.apiClient = apiClient;
        this.userDirectoryManager = userDirectoryManager;
    }

    public File recognize(File imageFile, File configFile, String requestedFileType) throws IOException {
        try (InputStream response = apiClient.recognize(imageFile, configFile, requestedFileType)) {
            String filename = "recognized_model" + UUID.randomUUID() + ".".concat(requestedFileType);
            if (!userDirectoryManager.fileExists(filename, AppDirectoryType.TMP)) {
                userDirectoryManager.createFileIfMissing(filename, AppDirectoryType.TMP);
            }

            Path output = userDirectoryManager.getFilePath(filename, AppDirectoryType.TMP);
            Files.copy(response, output, StandardCopyOption.REPLACE_EXISTING);
            return output.toFile();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IOException("Recognition process interrupted", ex);
        }
    }
}
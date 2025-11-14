package ua.stetsenkoinna.recognition;

import ua.stetsenkoinna.config.AppDirectoryType;
import ua.stetsenkoinna.config.UserDirectoryManager;
import ua.stetsenkoinna.utils.MessageHelper;

import java.io.*;
import java.net.ConnectException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

/**
 * Provides high-level functionality for invoking the external Petri Net Recognition API
 * and storing the resulting recognized model file.
 * <p>
 * This service acts as an abstraction layer over {@link RecognitionApiClient},
 * handling:
 * <ul>
 *     <li>API communication</li>
 *     <li>Temporary file generation</li>
 *     <li>Error handling and user notifications</li>
 *     <li>Saving results into the application's temporary directory</li>
 * </ul>
 *
 * <h2>Main Responsibilities</h2>
 * <ul>
 *     <li>Send an image and configuration file to the Recognition API.</li>
 *     <li>Receive the recognition result as an InputStream.</li>
 *     <li>Store the returned file (Java code, Petri Net format etc.).</li>
 *     <li>Ensure safe handling of interruptions and network errors.</li>
 * </ul>
 *
 * <h2>Usage Example</h2>
 * <pre>
 * RecognitionService service = new RecognitionService(apiClient, userDirectoryManager);
 * File result = service.recognize(imageFile, configFile, "pnml");
 *
 * if (result != null) {
 *     System.out.println("Recognition saved at: " + result.getAbsolutePath());
 * }
 * </pre>
 *
 * <h2>Error Handling</h2>
 * <ul>
 *     <li><b>InterruptedException</b> — The thread interruption flag is restored and wrapped as an IOException.</li>
 *     <li><b>ConnectException</b> — A user-facing popup is shown, and the method returns {@code null}.</li>
 * </ul>
 *
 *  @author  Bohdan Hrontkovskyi
 *  @since   14.11.2025
 */
public class RecognitionService {

    /** The HTTP client used to communicate with the Recognition API. */
    private final RecognitionApiClient apiClient;

    /** Manager for accessing and storing files in application-specific user directories. */
    private final UserDirectoryManager userDirectoryManager;

    /**
     * Constructs a new recognition service.
     *
     * @param apiClient the API client responsible for sending recognition requests
     * @param userDirectoryManager the manager providing file storage utilities
     */
    public RecognitionService(RecognitionApiClient apiClient, UserDirectoryManager userDirectoryManager) {
        this.apiClient = apiClient;
        this.userDirectoryManager = userDirectoryManager;
    }

    /**
     * Sends an image file to the Recognition API and stores the result in the temporary directory.
     *
     * @param imageFile the image file containing the Petri Net sketch
     * @param configFile the configuration file with detection settings
     * @param requestedFileType expected file type returned by the API ("pnml", "petriobj")
     *
     * @return the generated result file, or {@code null} if a connection error occurred
     *
     * @throws IOException if a file I/O error occurs or the recognition process is interrupted
     */
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
        } catch (ConnectException ex) {
            MessageHelper.showError("Cannot connect to recognition API. Check your Recognition API credentials.");
            return null;
        }
    }
}
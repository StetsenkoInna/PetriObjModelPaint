package ua.stetsenkoinna.recognition;

import java.io.*;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A low-level HTTP client responsible for communicating with the external
 * Petri Net Recognition API. This class sends image + YAML configuration files,
 * receives the recognized model, and returns the raw InputStream for further processing.
 *
 * <p>The client builds a multipart/form-data HTTP POST request using the
 * standard {@link java.net.http.HttpClient} and expects the API to return a file
 * containing the recognized Petri Net model.
 *
 * <h2>Main Responsibilities</h2>
 * <ul>
 *     <li>Create multipart/form-data requests</li>
 *     <li>Attach image and config files</li>
 *     <li>Send request to the Recognition API endpoint</li>
 *     <li>Return response InputStream for later saving</li>
 *     <li>Handle API errors and propagate IO issues</li>
 * </ul>
 *
 * <h2>Typical Usage</h2>
 * <pre>
 * RecognitionApiClient client = new RecognitionApiClient(apiUrl, apiKey);
 * InputStream result = client.recognize(imageFile, configFile, "pnml");
 * </pre>
 *
 * <h2>Error Handling</h2>
 * <ul>
 *     <li><b>IOException</b> — invalid files, failed request creation, or API error response.</li>
 *     <li><b>InterruptedException</b> — HTTP requests are interruptible; interruptions must be propagated.</li>
 * </ul>
 *
 * @author  Bohdan Hrontkovskyi
 * @since   14.11.2025
 */
public class RecognitionApiClient {

    private final static Logger LOGGER = Logger.getLogger(RecognitionApiClient.class.getName());

    /** HTTP client used for sending multipart requests. */
    private final HttpClient httpClient;

    /** Base URL of the Recognition API (http://localhost:8000). */
    private final String apiUrl;

    /** API key used for authentication (X-Roboflow-API-Key header). */
    private final String apiKey;

    /**
     * Creates a new Recognition API client using the provided API URL and API key.
     *
     * @param apiUrl the target API root URL
     * @param apiKey the Roboflow API authentication key
     */
    public RecognitionApiClient(String apiUrl, String apiKey) {
        this.httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .build();
        this.apiUrl = apiUrl;
        this.apiKey = apiKey;
    }

    /**
     * Sends image and YAML config files to the Recognition API and returns the raw
     * InputStream containing the resulting recognized model.
     *
     * <p>The API returns different formats depending on {@code requestedFileType}
     * (e.g., "pnml", "petriobj").
     *
     * @param imageFile the input image file (.png, .jpg)
     * @param configFile YAML configuration file (.yml or .yaml)
     * @param requestedFileType the desired output format ("petriobj", "pnml")
     *
     * @return InputStream containing the recognized Petri Net model
     *
     * @throws IOException if an I/O or API error occurs
     * @throws InterruptedException if the HTTP request is interrupted
     */
    public InputStream recognize(File imageFile, File configFile, String requestedFileType) throws IOException, InterruptedException {
        String boundary = "Boundary-" + UUID.randomUUID();
        String requestUrl = String.format("%s/api/recognize?requested_file_type=%s", apiUrl, requestedFileType);

        LOGGER.log(Level.INFO, "Sending recognition request to: {0}", requestUrl);

        byte[] multipartBody = buildMultipartBody(boundary, imageFile, configFile);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(requestUrl))
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .header("X-Roboflow-API-Key", apiKey)
                .POST(HttpRequest.BodyPublishers.ofByteArray(multipartBody))
                .build();

        HttpResponse<InputStream> response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());

        if (response.statusCode() != 200) {
            String error = new String(response.body().readAllBytes());
            throw new IOException("Recognition API error: " + response.statusCode() + " - " + error);
        }

        return response.body();
    }

    /**
     * Builds a multipart/form-data body containing:
     * <ul>
     *     <li>“image” - the image file</li>
     *     <li>“config” - the YAML settings file</li>
     * </ul>
     *
     * @param boundary the multipart boundary string
     * @param imageFile the image file to attach
     * @param configFile the config file to attach
     * @return the multipart body as byte[]
     *
     * @throws IOException if reading file data fails
     */
    private byte[] buildMultipartBody(String boundary, File imageFile, File configFile) throws IOException {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream(); DataOutputStream out = new DataOutputStream(baos)) {
            writeFilePart(out, "image", imageFile, boundary);
            writeFilePart(out, "config", configFile, boundary);

            out.writeBytes("--" + boundary + "--\r\n");
            out.flush();

            return baos.toByteArray();
        }
    }

    /**
     * Writes a single part of a multipart/form-data request, including:
     * <ul>
     *     <li>Content-Disposition</li>
     *     <li>Content-Type</li>
     *     <li>File binary contents</li>
     * </ul>
     *
     * @param out stream to write into
     * @param fieldName form field name
     * @param file file to send
     * @param boundary multipart boundary
     *
     * @throws IOException if reading from the file fails
     */
    private void writeFilePart(DataOutputStream out, String fieldName, File file, String boundary) throws IOException {
        String fileName = file.getName();
        String mimeType = Files.probeContentType(file.toPath());
        if (mimeType == null) {
            mimeType = "application/octet-stream";
        }

        out.writeBytes("--" + boundary + "\r\n");
        out.writeBytes("Content-Disposition: form-data; name=\"" + fieldName + "\"; filename=\"" + fileName + "\"\r\n");
        out.writeBytes("Content-Type: " + mimeType + "\r\n\r\n");

        Files.copy(file.toPath(), out);
        out.writeBytes("\r\n");
    }
}

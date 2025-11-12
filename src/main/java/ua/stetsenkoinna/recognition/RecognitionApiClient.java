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

public class RecognitionApiClient {

    private final static Logger LOGGER = Logger.getLogger(RecognitionApiClient.class.getName());

    private final HttpClient httpClient;
    private final String apiUrl;
    private final String apiKey;

    public RecognitionApiClient(String apiUrl, String apiKey) {
        this.httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .build();
        this.apiUrl = apiUrl;
        this.apiKey = apiKey;
    }

    /**
     * Sends image and YAML config files to the recognition API.
     *
     * @param imageFile          the image file (.png or .jpg)
     * @param configFile         the configuration file (.yml or .yaml)
     * @param requestedFileType  output file type ("pnml" or "petriobj")
     * @return InputStream containing the recognized model data
     * @throws IOException          if a network or IO error occurs
     * @throws InterruptedException if the request is interrupted
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
     * Builds a multipart/form-data request body.
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

package ua.stetsenkoinna.config;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The {@code UserDirectoryManager} class is responsible for managing the local
 * directory structure used by the {@code PetriObjModelPaint} application.
 * <p>
 * This class automatically creates a hidden application directory under the user's
 * home folder (typically {@code ~/.PetriObjModelPaint}) and ensures that all required
 * subdirectories — such as {@code configs}, {@code cache}, {@code logs},
 * {@code data}, and {@code tmp} — are initialized and available.
 * <p>
 * It also provides utility methods for performing file operations (create, delete,
 * check existence, resolve absolute paths, clear contents, etc.) within those directories.
 * <p>
 *
 * <h3>Example usage:</h3>
 * <pre>{@code
 * UserDirectoryManager manager = new UserDirectoryManager();
 * Path configPath = manager.getFilePath("application.properties", AppDirectoryType.CONFIGS);
 *
 * if (!manager.fileExists("application.properties", AppDirectoryType.CONFIGS)) {
 *     manager.createFileIfMissing("application.properties", AppDirectoryType.CONFIGS);
 * }
 * }</pre>
 *
 * @author  Bohdan Hrontkovskyi
 * @since   11.11.2025
 * @see     AppDirectoryType
 */
public class UserDirectoryManager {

    /** Logger for tracking directory operations and errors. */
    private static final Logger LOGGER = Logger.getLogger(UserDirectoryManager.class.getName());

    /** The name of the hidden application directory within the user's home folder. */
    private static final String APP_FOLDER_NAME = ".PetriObjModelPaint";

    /** Path to the user's home directory. */
    private final Path userHomeDir;

    /** Path to the application's root directory (~/.PetriObjModelPaint). */
    private final Path appRootDir;

    /**
     * Constructs a new {@code UserDirectoryManager}, automatically creating
     * the application root directory and all predefined subdirectories.
     */
    public UserDirectoryManager() {
        this.userHomeDir = Path.of(System.getProperty("user.home"));
        this.appRootDir = userHomeDir.resolve(APP_FOLDER_NAME);
        ensureAppDirectoryExists();
        ensureSubDirectoriesExists();
    }

    /**
     * Ensures that the root application directory exists.
     * Creates it if it does not already exist.
     */
    private void ensureAppDirectoryExists() {
        createDirIfMissing(appRootDir);
    }

    /**
     * Ensures that all subdirectories defined in {@link AppDirectoryType}
     * exist within the application root directory.
     */
    private void ensureSubDirectoriesExists() {
        for (AppDirectoryType type: AppDirectoryType.values()) {
            createDirIfMissing(getDirectoryPath(type));
        }
    }

    /**
     * Creates a directory if it does not exist.
     *
     * @param dir the directory path to check or create
     */
    private void createDirIfMissing(Path dir) {
        try {
            if (!Files.exists(dir)) {
                Files.createDirectories(dir);
                LOGGER.log(Level.INFO, "Created directory: " + dir);
            }
        } catch (IOException ex) {
            LOGGER.log(Level.SEVERE, "Failed to create directory: " + dir, ex);
        }
    }

    /**
     * Returns the path to a specific application subdirectory.
     *
     * @param type the {@link AppDirectoryType} representing the subdirectory
     * @return the resolved {@link Path} of the requested directory
     */
    public Path getDirectoryPath(AppDirectoryType type) {
        return appRootDir.resolve(type.getFolderName());
    }

    /**
     * Returns the path to a file within a specific subdirectory.
     *
     * @param filename the name of the file
     * @param type     the {@link AppDirectoryType} where the file is located
     * @return the full {@link Path} to the file
     */
    public Path getFilePath(String filename, AppDirectoryType type) {
        return getDirectoryPath(type).resolve(filename);
    }

    /**
     * Checks whether a file exists in a given subdirectory.
     *
     * @param filename the name of the file to check
     * @param type     the {@link AppDirectoryType} of the directory to search in
     * @return {@code true} if the file exists; {@code false} otherwise
     */
    public boolean fileExists(String filename, AppDirectoryType type) {
        return Files.exists(getFilePath(filename, type));
    }

    /**
     * Creates a file if it does not already exist in the specified subdirectory.
     *
     * @param filename the name of the file to create
     * @param type     the {@link AppDirectoryType} where the file should be created
     * @throws IOException if the file cannot be created
     */
    public void createFileIfMissing(String filename, AppDirectoryType type) throws IOException {
        Path file = getFilePath(filename, type);
        if (!Files.exists(file)) {
            Files.createFile(file);
            LOGGER.log(Level.INFO, "Created " + type.getFolderName() + " file: " + file);
        }
    }

    /**
     * Deletes a file if it exists in the specified subdirectory.
     *
     * @param filename the name of the file to delete
     * @param type     the {@link AppDirectoryType} of the file's directory
     * @throws IOException if the deletion fails
     */
    public void deleteFile(String filename, AppDirectoryType type) throws IOException {
        Files.deleteIfExists(getFilePath(filename, type));
        LOGGER.log(Level.INFO, "Deleted file: " + filename + " in " + type.getFolderName() + " directory");
    }

    /**
     * Returns the absolute path to a file in the specified subdirectory.
     *
     * @param filename the name of the file
     * @param type     the {@link AppDirectoryType} of the directory
     * @return the absolute path as a {@link String}
     */
    public String getFileAbsolutePath(String filename, AppDirectoryType type) {
        return getFilePath(filename, type).toAbsolutePath().toString();
    }

    /**
     * Deletes all files inside the specified subdirectory.
     *
     * @param type the {@link AppDirectoryType} representing the directory to clear
     */
    public void clearDirectory(AppDirectoryType type) {
        Path dir = getDirectoryPath(type);
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir)) {
            for (Path file: stream) {
                Files.deleteIfExists(file);
            }
            LOGGER.log(Level.INFO, "Directory cleared: " + dir);
        } catch (IOException ex) {
            LOGGER.log(Level.SEVERE, "Failed to clear directory: " + dir, ex);
        }
    }

    /**
     * Returns the path to the application's root directory.
     *
     * @return the application's root {@link Path}
     */
    public Path getAppRootDir() {
        return appRootDir;
    }
}

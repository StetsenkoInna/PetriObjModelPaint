package ua.stetsenkoinna.config;

/**
 * Enum {@code AppDirectoryType} defines the types of subdirectories
 * used by the {@code PetriObjModelPaint} application within the user's
 * home directory (typically located at {@code ~/.PetriObjModelPaint}).
 * <p>
 * Each enum constant corresponds to a specific subdirectory that stores
 * dedicated types of application data — such as configuration files,
 * cache data, logs, or temporary runtime files.
 * <p>
 * This enum is primarily used by the {@link UserDirectoryManager}
 * class to manage directory creation, path resolution, and file organization.
 *
 * <h3>Example usage:</h3>
 * <pre>{@code
 * Path configDir = userDirectoryManager.getDirectoryPath(AppDirectoryType.CONFIGS);
 * Path logDir = userDirectoryManager.getDirectoryPath(AppDirectoryType.LOGS);
 * }</pre>
 *
 * @author  Bohdan Hrontkovskyi
 * @since   11.11.2025
 */
public enum AppDirectoryType {
    CONFIGS("configs"),
    CACHE("cache"),
    LOGS("logs"),
    DATA("data"),
    TMP("tmp");

    private final String folderName;

    AppDirectoryType(String folderName) {
        this.folderName = folderName;
    }

    public String getFolderName() {
        return folderName;
    }
}
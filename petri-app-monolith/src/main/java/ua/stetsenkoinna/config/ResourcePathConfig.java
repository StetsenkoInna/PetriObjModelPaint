package ua.stetsenkoinna.config;

import java.io.InputStream;
import java.net.URL;

/**
 * Centralized resource path configuration for the application.
 * This class provides methods to access various resources consistently.
 *
 * @author Serhii Rybak
 */
public class ResourcePathConfig {

    // Base paths for different resource types
    private static final String ICONS_BASE_PATH = "/ua/stetsenkoinna/img/";
    private static final String PNS_FILES_BASE_PATH = "/ua/stetsenkoinna/pns/";
    private static final String STYLES_BASE_PATH = "/ua/stetsenkoinna/graphpresentation/statistic/styles/";

    // Icon file names
    public static final String PLACE_ICON = "pl.png";
    public static final String ARC_ICON = "Arc.PNG";
    public static final String TRANSITION_ICON = "trans.png";
    public static final String RESET_ICON = "reset_icon.png";
    public static final String FX_ICON = "fx_icon.png";
    public static final String HAND_ICON = "hand_icon.png";
    public static final String SCALE_ICON = "scale_icon.png";
    public static final String DRAW_VERTICAL_ICON = "draw_vertical_icon.png";
    public static final String DRAW_HORIZONTAL_ICON = "draw_horizontal_icon.png";
    public static final String ERASER_ICON = "eraser_icon.png";
    public static final String ERASER_CURSOR = "eraser_cusrsor.png";
    public static final String TEXT_ICON = "text_icon.png";
    public static final String DOWNLOAD_ICON = "download_icon.png";
    public static final String EXPORT_CSV_ICON = "export_csv_icon.png";
    public static final String SETTINGS_ICON = "settings_icon.png";
    public static final String PLAY_ICON = "play.png";
    public static final String PAUSE_ICON = "pause.png";

    // Style files
    public static final String LINE_CHART_CSS = "line-chart.css";

    /**
     * Get the full resource path for an icon file
     * @param iconFileName the icon file name
     * @return the full resource path
     */
    public static String getIconPath(String iconFileName) {
        return ICONS_BASE_PATH + iconFileName;
    }

    /**
     * Get the full resource path for a PNS file
     * @param fileName the PNS file name (without extension)
     * @return the full resource path
     */
    public static String getPnsFilePath(String fileName) {
        return PNS_FILES_BASE_PATH + fileName + ".pns";
    }

    /**
     * Get the full resource path for a style file
     * @param styleFileName the style file name
     * @return the full resource path
     */
    public static String getStylePath(String styleFileName) {
        return STYLES_BASE_PATH + styleFileName;
    }

    /**
     * Get an InputStream for a resource
     * @param clazz the class to use for loading the resource
     * @param resourcePath the resource path
     * @return InputStream for the resource, or null if not found
     */
    public static InputStream getResourceAsStream(Class<?> clazz, String resourcePath) {
        return clazz.getResourceAsStream(resourcePath);
    }

    /**
     * Get a URL for a resource
     * @param clazz the class to use for loading the resource
     * @param resourcePath the resource path
     * @return URL for the resource, or null if not found
     */
    public static URL getResource(Class<?> clazz, String resourcePath) {
        return clazz.getResource(resourcePath);
    }

    /**
     * Get the CSS file URL as string for JavaFX stylesheets
     * @param clazz the class to use for loading the resource
     * @param styleFileName the style file name
     * @return the CSS file URL as string, or null if not found
     */
    public static String getCssUrl(Class<?> clazz, String styleFileName) {
        URL resource = getResource(clazz, getStylePath(styleFileName));
        return resource != null ? resource.toExternalForm() : null;
    }
}
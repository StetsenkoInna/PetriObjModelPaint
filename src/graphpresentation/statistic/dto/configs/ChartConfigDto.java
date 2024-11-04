package graphpresentation.statistic.dto.configs;

import java.util.Objects;

public class ChartConfigDto {
    private String title;
    private String xAxisTitle;
    private String yAxisTitle;
    private Boolean displayDataMarkers;
    private Boolean isDrawHorizontalLineEnabled = false;
    private Boolean isDrawVerticalLineEnabled = false;
    private Boolean isDragEnabled = false;
    private Boolean clearLineEnabled = false;
    private Boolean createLabelEnabled = false;
    private ChartDataCollectionConfigDto dataCollectionConfig;

    public ChartConfigDto(String title, String xAxisTitle, String yAxisTitle, Boolean displayDataMarkers) {
        this.title = title;
        this.xAxisTitle = xAxisTitle;
        this.yAxisTitle = yAxisTitle;
        this.displayDataMarkers = displayDataMarkers;
        this.dataCollectionConfig = new ChartDataCollectionConfigDto();
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getxAxisTitle() {
        return xAxisTitle;
    }

    public void setxAxisTitle(String xAxisTitle) {
        this.xAxisTitle = xAxisTitle;
    }

    public String getyAxisTitle() {
        return yAxisTitle;
    }

    public void setyAxisTitle(String yAxisTitle) {
        this.yAxisTitle = yAxisTitle;
    }

    public Boolean getDisplayDataMarkers() {
        return displayDataMarkers;
    }

    public void setDisplayDataMarkers(Boolean displayDataMarkers) {
        this.displayDataMarkers = displayDataMarkers;
    }

    public Boolean getDrawHorizontalLineEnabled() {
        return isDrawHorizontalLineEnabled;
    }

    public void toggleDrawHorizontalLineEnabled() {
        isDrawHorizontalLineEnabled = !isDrawHorizontalLineEnabled;
    }

    public Boolean getDrawVerticalLineEnabled() {
        return isDrawVerticalLineEnabled;
    }

    public void toggleDrawVerticalLineEnabled() {
        isDrawVerticalLineEnabled = !isDrawVerticalLineEnabled;
    }

    public Boolean getDragAndZoomEnabled() {
        return isDragEnabled;
    }

    public void toggleDragAndZoomEnabled() {
        isDragEnabled = !isDragEnabled;
    }

    public Boolean getClearLineEnabled() {
        return clearLineEnabled;
    }

    public void toggleClearLineEnabled() {
        clearLineEnabled = !clearLineEnabled;
    }

    public Boolean getCreateLabelEnabled() {
        return createLabelEnabled;
    }

    public void toggleCreateLabelEnabled() {
        createLabelEnabled = !createLabelEnabled;
    }

    public void setDrawHorizontalLineEnabled(Boolean drawHorizontalLineEnabled) {
        isDrawHorizontalLineEnabled = drawHorizontalLineEnabled;
    }

    public void setDrawVerticalLineEnabled(Boolean drawVerticalLineEnabled) {
        isDrawVerticalLineEnabled = drawVerticalLineEnabled;
    }

    public Boolean getDragEnabled() {
        return isDragEnabled;
    }

    public void setDragEnabled(Boolean dragEnabled) {
        isDragEnabled = dragEnabled;
    }

    public void setClearLineEnabled(Boolean clearLineEnabled) {
        this.clearLineEnabled = clearLineEnabled;
    }

    public void setCreateLabelEnabled(Boolean createLabelEnabled) {
        this.createLabelEnabled = createLabelEnabled;
    }

    public ChartDataCollectionConfigDto getDataCollectionConfig() {
        return dataCollectionConfig;
    }

    public void setDataCollectionConfig(ChartDataCollectionConfigDto dataCollectionConfig) {
        this.dataCollectionConfig = dataCollectionConfig;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ChartConfigDto that = (ChartConfigDto) o;
        return Objects.equals(title, that.title) && Objects.equals(xAxisTitle, that.xAxisTitle) && Objects.equals(yAxisTitle, that.yAxisTitle) && Objects.equals(displayDataMarkers, that.displayDataMarkers) && Objects.equals(isDrawHorizontalLineEnabled, that.isDrawHorizontalLineEnabled) && Objects.equals(isDrawVerticalLineEnabled, that.isDrawVerticalLineEnabled) && Objects.equals(isDragEnabled, that.isDragEnabled) && Objects.equals(clearLineEnabled, that.clearLineEnabled) && Objects.equals(createLabelEnabled, that.createLabelEnabled) && Objects.equals(dataCollectionConfig, that.dataCollectionConfig);
    }

    @Override
    public int hashCode() {
        return Objects.hash(title, xAxisTitle, yAxisTitle, displayDataMarkers, isDrawHorizontalLineEnabled, isDrawVerticalLineEnabled, isDragEnabled, clearLineEnabled, createLabelEnabled, dataCollectionConfig);
    }

    @Override
    public String toString() {
        return "ChartConfigDto{" +
                "title='" + title + '\'' +
                ", xAxisTitle='" + xAxisTitle + '\'' +
                ", yAxisTitle='" + yAxisTitle + '\'' +
                ", displayDataMarkers=" + displayDataMarkers +
                ", isDrawHorizontalLineEnabled=" + isDrawHorizontalLineEnabled +
                ", isDrawVerticalLineEnabled=" + isDrawVerticalLineEnabled +
                ", isDragEnabled=" + isDragEnabled +
                ", clearLineEnabled=" + clearLineEnabled +
                ", createLabelEnabled=" + createLabelEnabled +
                ", dataCollectionConfig=" + dataCollectionConfig +
                '}';
    }
}

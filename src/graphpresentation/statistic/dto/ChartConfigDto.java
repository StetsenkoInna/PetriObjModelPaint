package graphpresentation.statistic.dto;

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

    public ChartConfigDto(String title, String xAxisTitle, String yAxisTitle, Boolean displayDataMarkers) {
        this.title = title;
        this.xAxisTitle = xAxisTitle;
        this.yAxisTitle = yAxisTitle;
        this.displayDataMarkers = displayDataMarkers;
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

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ChartConfigDto configDto = (ChartConfigDto) o;
        return Objects.equals(title, configDto.title) && Objects.equals(xAxisTitle, configDto.xAxisTitle) && Objects.equals(yAxisTitle, configDto.yAxisTitle) && Objects.equals(displayDataMarkers, configDto.displayDataMarkers) && Objects.equals(isDrawHorizontalLineEnabled, configDto.isDrawHorizontalLineEnabled) && Objects.equals(isDrawVerticalLineEnabled, configDto.isDrawVerticalLineEnabled) && Objects.equals(isDragEnabled, configDto.isDragEnabled) && Objects.equals(clearLineEnabled, configDto.clearLineEnabled);
    }

    @Override
    public int hashCode() {
        return Objects.hash(title, xAxisTitle, yAxisTitle, displayDataMarkers, isDrawHorizontalLineEnabled, isDrawVerticalLineEnabled, isDragEnabled, clearLineEnabled);
    }
}

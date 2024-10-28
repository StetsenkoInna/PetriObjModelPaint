package graphpresentation.statistic.dto;

public class ChartConfigDto {
    private String title;
    private String xAxisTitle;
    private String yAxisTitle;
    private Boolean displayDataMarkers;

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
}

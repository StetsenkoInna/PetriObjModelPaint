package graphpresentation.statistic.dto;

public class ChartConfigDto {
    private String title;
    private String xAxisTitle;
    private String yAxisTitle;
    private String seriesName;

    public ChartConfigDto(String title, String xAxisTitle, String yAxisTitle, String seriesName) {
        this.title = title;
        this.xAxisTitle = xAxisTitle;
        this.yAxisTitle = yAxisTitle;
        this.seriesName = seriesName;
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

    public String getSeriesName() {
        return seriesName;
    }

    public void setSeriesName(String seriesName) {
        this.seriesName = seriesName;
    }
}

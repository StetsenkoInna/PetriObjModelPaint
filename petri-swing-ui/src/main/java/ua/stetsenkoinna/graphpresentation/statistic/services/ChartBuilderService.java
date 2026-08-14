package ua.stetsenkoinna.graphpresentation.statistic.services;

import ua.stetsenkoinna.api.dto.ChartConfigDto;
import javafx.embed.swing.JFXPanel;
import javafx.scene.chart.XYChart;

public interface ChartBuilderService {
    void createChart(JFXPanel jfxPanel, ChartConfigDto configDto);

    /**
     * Re-reads the stylesheet for the theme now in force, for a chart that already exists.
     *
     * <p>A chart picks its stylesheet when it is built, and the monitor holding it outlives any
     * number of theme changes - so without this, switching to dark with the monitor open leaves
     * a white chart with black axis labels sitting inside a dark dialog.
     */
    void applyTheme();
    void clearChart();
    void clearDrawings();
    void appendData(XYChart.Data<Number, Number> data);
    void changeSeriesName(Integer seriesId, String name);
    void updateChartConfig(ChartConfigDto chartConfigDto);
    void exportChartAsImage(String directory);
    void exportChartAsTable(String directory);
    void autoSizeChart();
    boolean isChartEmpty();
    int getCurrentSeriesId();
    void createSeries(String name);
}

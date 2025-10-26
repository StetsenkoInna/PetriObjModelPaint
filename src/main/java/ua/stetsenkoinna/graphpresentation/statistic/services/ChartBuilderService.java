package ua.stetsenkoinna.graphpresentation.statistic.services;

import ua.stetsenkoinna.graphpresentation.statistic.dto.configs.ChartConfigDto;
import javafx.embed.swing.JFXPanel;
import javafx.scene.chart.XYChart;

public interface ChartBuilderService {
    void createChart(JFXPanel jfxPanel, ChartConfigDto configDto);
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

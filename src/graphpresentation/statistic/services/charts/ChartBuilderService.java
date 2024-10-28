package graphpresentation.statistic.services.charts;

import graphpresentation.statistic.dto.ChartConfigDto;
import javafx.embed.swing.JFXPanel;
import javafx.scene.chart.XYChart;

public interface ChartBuilderService {
    void createChart(JFXPanel jfxPanel, ChartConfigDto configDto);
    void clearChart();
    void appendData(XYChart.Data<Number, Number> data);
    void changeSeriesName(String name);
    void updateChartConfig(ChartConfigDto chartConfigDto);
    void downloadChart(String fileName);
    boolean isChartEmpty();
}

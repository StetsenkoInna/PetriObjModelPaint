package graphpresentation.statistic.charts;

import graphpresentation.statistic.dto.ChartConfigDto;
import javafx.embed.swing.JFXPanel;
import javafx.scene.chart.XYChart;

public interface ChartBuilderService {
    void createChart(JFXPanel jfxPanel, ChartConfigDto configDto);
    void appendData(XYChart.Data<Number, Number> data);

    boolean isChartEmpty();
}

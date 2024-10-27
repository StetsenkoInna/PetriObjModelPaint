package graphpresentation.statistic.charts;

import graphpresentation.statistic.dto.ChartConfigDto;
import javafx.application.Platform;
import javafx.embed.swing.JFXPanel;
import javafx.scene.Scene;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;

public class LineChartBuilderService implements ChartBuilderService {
    private final XYChart.Series<Number, Number> series;
    private LineChart<Number, Number> lineChart;

    public LineChartBuilderService() {
        series = new XYChart.Series<>();
        Platform.setImplicitExit(false);
    }

    @Override
    public void createChart(JFXPanel jfxPanel, ChartConfigDto configDto) {
        Platform.runLater(() -> {
            NumberAxis xAxis = new NumberAxis();
            NumberAxis yAxis = new NumberAxis();
            xAxis.setLabel(configDto.getxAxisTitle());
            yAxis.setLabel(configDto.getyAxisTitle());

            lineChart = new LineChart<>(xAxis, yAxis);
            lineChart.setTitle(configDto.getTitle());

            series.setName(configDto.getSeriesName());

            lineChart.getData().add(series);
            jfxPanel.setScene(new Scene(lineChart, 800, 600));
        });
    }

    @Override
    public void clearChart() {
        Platform.runLater(() -> series.getData().clear());
    }

    @Override
    public void appendData(XYChart.Data<Number, Number> data) {
        Platform.runLater(() -> {
            series.getData().add(data);
        });
    }

    @Override
    public void changeSeriesName(String name) {
        Platform.runLater(() -> {
            series.setName(name);
            series.getData().clear();
        });
    }

    @Override
    public boolean isChartEmpty() {
        return lineChart == null || lineChart.getData().isEmpty();
    }
}

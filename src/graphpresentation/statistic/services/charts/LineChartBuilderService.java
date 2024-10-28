package graphpresentation.statistic.services.charts;

import graphpresentation.statistic.dto.ChartConfigDto;
import javafx.application.Platform;
import javafx.embed.swing.JFXPanel;
import javafx.embed.swing.SwingFXUtils;
import javafx.scene.Scene;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Tooltip;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.StackPane;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.io.File;
import java.io.IOException;

import static javax.swing.JOptionPane.showMessageDialog;

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

            String cssFile = new File("src/graphpresentation/statistic/styles/line-chart.css").toURI().toString();
            lineChart.getStylesheets().add(cssFile);

            StackPane root = new StackPane(lineChart);
            jfxPanel.setScene(new Scene(root, 800, 600));
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
            XYChart.Data<Number, Number> dataPoint = series.getData().get(series.getData().size() - 1);
            Tooltip tooltip = new Tooltip("Modelling time: " + dataPoint.getXValue() + "\n" + "Observed value: " + dataPoint.getYValue());
            Tooltip.install(dataPoint.getNode(), tooltip);
            dataPoint.getNode().setOnMouseEntered(event -> dataPoint.getNode().getStyleClass().add("onDataPointHover"));
            dataPoint.getNode().setOnMouseExited(event -> dataPoint.getNode().getStyleClass().remove("onDataPointHover"));
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
    public void updateChartConfig(ChartConfigDto chartConfigDto) {
        Platform.runLater(() -> {
            lineChart.setTitle(chartConfigDto.getTitle());
            lineChart.getXAxis().setLabel(chartConfigDto.getxAxisTitle());
            lineChart.getYAxis().setLabel(chartConfigDto.getyAxisTitle());
        });
    }

    @Override
    public void downloadChart(String fileName) {
        Platform.runLater(() -> {
            WritableImage image = lineChart.snapshot(null, null);
            File file = new File(fileName);
            try {
                ImageIO.write(SwingFXUtils.fromFXImage(image, null), "png", file);
                showMessageDialog(null, "Chart image successfully saved", "Chart image export", JOptionPane.PLAIN_MESSAGE);
            } catch (IOException e) {
                showMessageDialog(null, "Failed to save chart image", "Chart image export", JOptionPane.ERROR_MESSAGE);
            }
        });
    }

    @Override
    public boolean isChartEmpty() {
        return lineChart == null || lineChart.getData().isEmpty();
    }
}

package graphpresentation.statistic.serviceImpl;

import graphpresentation.statistic.dto.ChartConfigDto;
import graphpresentation.statistic.services.ChartBuilderService;
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
import java.io.FileWriter;
import java.io.IOException;

import static javax.swing.JOptionPane.showMessageDialog;

public class LineChartBuilderService implements ChartBuilderService {
    private Integer currentSeriesId;
    private LineChart<Number, Number> lineChart;
    private ChartConfigDto chartConfigDto;

    public LineChartBuilderService() {
        this.currentSeriesId = -1;
        Platform.setImplicitExit(false);
    }

    @Override
    public void createChart(JFXPanel jfxPanel, ChartConfigDto configDto) {
        this.chartConfigDto = configDto;
        Platform.runLater(() -> {
            NumberAxis xAxis = new NumberAxis();
            NumberAxis yAxis = new NumberAxis();
            xAxis.setLabel(configDto.getxAxisTitle());
            yAxis.setLabel(configDto.getyAxisTitle());

            lineChart = new LineChart<>(xAxis, yAxis);
            lineChart.setTitle(configDto.getTitle());

            String cssFile = new File("src/graphpresentation/statistic/styles/line-chart.css").toURI().toString();
            lineChart.getStylesheets().add(cssFile);

            StackPane root = new StackPane(lineChart);
            jfxPanel.setScene(new Scene(root, 800, 600));
        });
    }

    @Override
    public void clearChart() {
        currentSeriesId = -1;
        Platform.runLater(() -> lineChart.getData().clear());
    }

    @Override
    public void appendData(Integer seriesId, XYChart.Data<Number, Number> data) {
        Platform.runLater(() -> {
            XYChart.Series<Number, Number> series = lineChart.getData().get(seriesId);
            series.getData().add(data);
            XYChart.Data<Number, Number> dataPoint = series.getData().get(series.getData().size() - 1);
            String tooltipMessage = chartConfigDto.getxAxisTitle() + ":" + dataPoint.getXValue() + "\n" +
                    chartConfigDto.getyAxisTitle() + ":" + dataPoint.getYValue();
            Tooltip tooltip = new Tooltip(tooltipMessage);
            Tooltip.install(dataPoint.getNode(), tooltip);
            dataPoint.getNode().setOnMouseEntered(event -> dataPoint.getNode().getStyleClass().add("onDataPointHover"));
            dataPoint.getNode().setOnMouseExited(event -> dataPoint.getNode().getStyleClass().remove("onDataPointHover"));
        });
    }

    @Override
    public void changeSeriesName(Integer seriesId, String name) {
        Platform.runLater(() -> {
            XYChart.Series<Number, Number> series = lineChart.getData().get(seriesId);
            series.setName(name);
            series.getData().clear();
        });
    }

    @Override
    public void updateChartConfig(ChartConfigDto chartConfigDto) {
        this.chartConfigDto = chartConfigDto;
        Platform.runLater(() -> {
            lineChart.setTitle(chartConfigDto.getTitle());
            lineChart.getXAxis().setLabel(chartConfigDto.getxAxisTitle());
            lineChart.getYAxis().setLabel(chartConfigDto.getyAxisTitle());
        });
    }

    @Override
    public void exportChartAsImage(String directory) {
        Platform.runLater(() -> {
            WritableImage image = lineChart.snapshot(null, null);
            File file = new File(directory + "/" + chartConfigDto.getTitle() + ".png");
            try {
                ImageIO.write(SwingFXUtils.fromFXImage(image, null), "png", file);
                showMessageDialog(null, "Chart image successfully saved", "Chart image export", JOptionPane.PLAIN_MESSAGE);
            } catch (IOException e) {
                e.printStackTrace();
                showMessageDialog(null, "Failed to save chart image", "Chart image export", JOptionPane.ERROR_MESSAGE);
            }
        });
    }

    @Override
    public void exportChartAsTable(String directory) {
        for (XYChart.Series<Number, Number> series : lineChart.getData()) {
            String filePath = directory + "/" + chartConfigDto.getTitle() + "_" + series.getName() + ".csv";
            try (FileWriter writer = new FileWriter(filePath)) {
                writer.append(chartConfigDto.getxAxisTitle()).append(",")
                        .append(chartConfigDto.getyAxisTitle()).append("\n");
                for (XYChart.Data<Number, Number> data : series.getData()) {
                    writer.append(data.getXValue().toString()).append(",")
                            .append(data.getYValue().toString()).append("\n");
                }
            } catch (IOException e) {
                e.printStackTrace();
                showMessageDialog(null, "Failed to export chart data", "Chart data export", JOptionPane.ERROR_MESSAGE);
            }
        }
        showMessageDialog(null, "Chart data successfully exported", "Chart data export", JOptionPane.PLAIN_MESSAGE);
    }


    @Override
    public boolean isChartEmpty() {
        return lineChart == null || lineChart.getData().isEmpty();
    }

    @Override
    public int getCurrentSeriesId() {
        return currentSeriesId;
    }

    @Override
    public void createSeries(String name) {
        Platform.runLater(() -> {
            XYChart.Series<Number, Number> series = new XYChart.Series<>();
            series.setName(name);
            lineChart.getData().add(series);
            currentSeriesId++;
        });
    }
}

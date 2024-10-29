package graphpresentation.statistic.serviceImpl;

import com.sun.javafx.charts.Legend;
import graphpresentation.statistic.dto.ChartConfigDto;
import graphpresentation.statistic.dto.ChartDrawingConfig;
import graphpresentation.statistic.dto.ChartLineData;
import graphpresentation.statistic.services.ChartBuilderService;
import javafx.application.Platform;
import javafx.embed.swing.JFXPanel;
import javafx.embed.swing.SwingFXUtils;
import javafx.scene.Cursor;
import javafx.scene.ImageCursor;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.image.WritableImage;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.AnchorPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Line;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static javax.swing.JOptionPane.showMessageDialog;

public class LineChartBuilderService implements ChartBuilderService {
    private ChartConfigDto chartConfigDto;
    private ChartDrawingConfig chartDrawing;

    private Integer currentSeriesId;
    private LineChart<Number, Number> lineChart;
    private AnchorPane root;
    private Line horizontalTooltipLine;
    private Line verticalTooltipLine;

    public LineChartBuilderService() {
        this.currentSeriesId = -1;
        this.chartDrawing = new ChartDrawingConfig();
        Platform.setImplicitExit(false);
    }

    @Override
    public void createChart(JFXPanel jfxPanel, ChartConfigDto configDto) {
        this.chartConfigDto = configDto;
        Platform.runLater(() -> {
            NumberAxis xAxis = new NumberAxis();
            NumberAxis yAxis = new NumberAxis();
            xAxis.setAutoRanging(true);
            yAxis.setAutoRanging(true);
            xAxis.setLabel(configDto.getxAxisTitle());
            yAxis.setLabel(configDto.getyAxisTitle());

            lineChart = new LineChart<>(xAxis, yAxis);
            lineChart.setTitle(configDto.getTitle());

            String cssFile = new File("src/graphpresentation/statistic/styles/line-chart.css").toURI().toString();
            lineChart.getStylesheets().add(cssFile);

            root = new AnchorPane(lineChart);
            AnchorPane.setTopAnchor(lineChart, 0.0);
            AnchorPane.setBottomAnchor(lineChart, 0.0);
            AnchorPane.setLeftAnchor(lineChart, 0.0);
            AnchorPane.setRightAnchor(lineChart, 0.0);

            createOnChartScrollHandler(xAxis, yAxis);
            createOnChartMouseHandlers(xAxis, yAxis);

            jfxPanel.setScene(new Scene(root, 800, 600));
        });
    }

    private void createOnChartMouseHandlers(NumberAxis xAxis, NumberAxis yAxis) {
        final double[] dragAnchorX = new double[1];
        final double[] dragAnchorY = new double[1];

        createLineDrawTooltip();

        lineChart.setOnMousePressed(event -> {
            if (event.isSecondaryButtonDown() && chartConfigDto.getDragAndZoomEnabled()) {
                lineChart.setCursor(Cursor.MOVE);
                dragAnchorX[0] = event.getX();
                dragAnchorY[0] = event.getY();
            }
            if (event.isPrimaryButtonDown()) {
                double xData = (double) lineChart.getXAxis().getValueForDisplay(event.getX());
                double yData = (double) lineChart.getYAxis().getValueForDisplay(event.getY());

                if (chartConfigDto.getClearLineEnabled()) {
                    clearLinesOnClicked(xData, yData);
                } else {
                    drawLinesOnClicked(xData, yData);
                }
                updateDrawings();
            }
        });

        if (chartConfigDto.getDragAndZoomEnabled() && !chartConfigDto.getClearLineEnabled()) {
            lineChart.setOnMouseReleased(event -> lineChart.setCursor(Cursor.DEFAULT));
        }

        lineChart.setOnMouseDragged(event -> {
            if (event.isSecondaryButtonDown() && chartConfigDto.getDragAndZoomEnabled()) {
                double deltaX = (dragAnchorX[0] - event.getX()) * (xAxis.getUpperBound() - xAxis.getLowerBound()) / lineChart.getWidth();
                double deltaY = (event.getY() - dragAnchorY[0]) * (yAxis.getUpperBound() - yAxis.getLowerBound()) / lineChart.getHeight(); // Invert Y movement

                xAxis.setLowerBound(xAxis.getLowerBound() + deltaX);
                xAxis.setUpperBound(xAxis.getUpperBound() + deltaX);
                yAxis.setLowerBound(yAxis.getLowerBound() + deltaY);
                yAxis.setUpperBound(yAxis.getUpperBound() + deltaY);

                dragAnchorX[0] = event.getX();
                dragAnchorY[0] = event.getY();
                updateDrawings();
            }
        });
    }

    private void createLineDrawTooltip() {
        horizontalTooltipLine = new Line();
        horizontalTooltipLine.setStroke(Color.DARKGRAY);
        horizontalTooltipLine.setStrokeWidth(1);
        horizontalTooltipLine.getStrokeDashArray().addAll(10.0, 5.0);
        horizontalTooltipLine.setVisible(false);
        horizontalTooltipLine.setMouseTransparent(true);

        verticalTooltipLine = new Line();
        verticalTooltipLine.setStroke(Color.DARKGRAY);
        verticalTooltipLine.setStrokeWidth(1);
        verticalTooltipLine.getStrokeDashArray().addAll(10.0, 5.0);
        verticalTooltipLine.setVisible(false);
        verticalTooltipLine.setMouseTransparent(true);

        root.getChildren().addAll(verticalTooltipLine, horizontalTooltipLine);

        lineChart.setOnMouseMoved(event -> {
            if (chartConfigDto.getDrawVerticalLineEnabled()) {
                verticalTooltipLine.setStartX(event.getX());
                verticalTooltipLine.setStartY(0);
                verticalTooltipLine.setEndX(event.getX());
                verticalTooltipLine.setEndY(lineChart.getHeight());
                verticalTooltipLine.setVisible(true);
            }
            if (chartConfigDto.getDrawHorizontalLineEnabled()) {
                horizontalTooltipLine.setStartX(0);
                horizontalTooltipLine.setStartY(event.getY());
                horizontalTooltipLine.setEndX(lineChart.getWidth());
                horizontalTooltipLine.setEndY(event.getY());
                horizontalTooltipLine.setVisible(true);
            }
        });
    }

    private void drawLinesOnClicked(double xData, double yData) {
        if (chartConfigDto.getDrawVerticalLineEnabled()) {
            Line line = new Line();
            line.setVisible(false);
            root.getChildren().add(line);
            chartDrawing.getVerticalLines().add(new ChartLineData(line, xData, true));
        }
        if (chartConfigDto.getDrawHorizontalLineEnabled()) {
            Line line = new Line();
            line.setVisible(false);
            root.getChildren().add(line);
            chartDrawing.getHorizontalLines().add(new ChartLineData(line, yData, false));
        }
    }

    private void clearLinesOnClicked(double xData, double yData) {
        List<ChartLineData> verticalLinesToRemove = chartDrawing.getVerticalLines().stream()
                .filter(chartLineData -> Math.abs(chartLineData.getX() - xData) <= 1)
                .collect(Collectors.toList());
        List<ChartLineData> horizontalLinesToRemove = chartDrawing.getHorizontalLines().stream()
                .filter(chartLineData -> Math.abs(chartLineData.getY() - yData) <= 1)
                .collect(Collectors.toList());
        chartDrawing.getVerticalLines().removeAll(verticalLinesToRemove);
        chartDrawing.getHorizontalLines().removeAll(horizontalLinesToRemove);
        List<Line> removeLines = new ArrayList<>();
        removeLines.addAll(verticalLinesToRemove.stream()
                .map(ChartLineData::getLine)
                .collect(Collectors.toList()));
        removeLines.addAll(horizontalLinesToRemove.stream()
                .map(ChartLineData::getLine)
                .collect(Collectors.toList()));
        root.getChildren().removeAll(removeLines);
    }

    private void createOnChartScrollHandler(NumberAxis xAxis, NumberAxis yAxis) {
        lineChart.setOnScroll(event -> {
            lineChart.getXAxis().setAutoRanging(false);
            lineChart.getYAxis().setAutoRanging(false);

            double zoomFactor = 0.1;
            double xRange = xAxis.getUpperBound() - xAxis.getLowerBound();
            double yRange = yAxis.getUpperBound() - yAxis.getLowerBound();
            double deltaX = xRange * zoomFactor;
            double deltaY = yRange * zoomFactor;

            if (event.getDeltaY() > 0) {
                xAxis.setLowerBound(xAxis.getLowerBound() + deltaX);
                xAxis.setUpperBound(xAxis.getUpperBound() - deltaX);
                yAxis.setLowerBound(yAxis.getLowerBound() + deltaY);
                yAxis.setUpperBound(yAxis.getUpperBound() - deltaY);
            } else if (event.getDeltaY() < 0) {
                xAxis.setLowerBound(xAxis.getLowerBound() - deltaX);
                xAxis.setUpperBound(xAxis.getUpperBound() + deltaX);
                yAxis.setLowerBound(yAxis.getLowerBound() - deltaY);
                yAxis.setUpperBound(yAxis.getUpperBound() + deltaY);
            }
            updateDrawings();
        });
    }


    private void updateDrawings() {
        double chartHeight = lineChart.getHeight();
        double chartWidth = lineChart.getWidth();

        for (ChartLineData lineData : chartDrawing.getVerticalLines()) {
            double xPos = lineChart.getXAxis().getDisplayPosition(lineData.getX());
            Line verticalLine = lineData.getLine();
            verticalLine.setStartX(xPos);
            verticalLine.setStartY(0);
            verticalLine.setEndX(xPos);
            verticalLine.setEndY(chartHeight);
            verticalLine.setStroke(Color.DARKGRAY);
            verticalLine.setStrokeWidth(1);
            verticalLine.getStrokeDashArray().addAll(10.0, 5.0);
            verticalLine.setId(lineData.getId());
            verticalLine.setVisible(true);
        }
        for (ChartLineData lineData : chartDrawing.getHorizontalLines()) {
            double yPos = lineChart.getYAxis().getDisplayPosition(lineData.getY());
            Line horizontalLine = lineData.getLine();
            horizontalLine.setStartX(0);
            horizontalLine.setStartY(yPos);
            horizontalLine.setEndX(chartWidth);
            horizontalLine.setEndY(yPos);
            horizontalLine.setStroke(Color.DARKGRAY);
            horizontalLine.setStrokeWidth(1);
            horizontalLine.getStrokeDashArray().addAll(10.0, 5.0);
            horizontalLine.setId(lineData.getId());
            horizontalLine.setVisible(true);
        }
    }

    @Override
    public void clearChart() {
        currentSeriesId = -1;
        Platform.runLater(() -> lineChart.getData().clear());
    }

    @Override
    public void appendData(XYChart.Data<Number, Number> data) {
        Platform.runLater(() -> {
            XYChart.Series<Number, Number> series = lineChart.getData().get(currentSeriesId);
            series.getData().add(data);
            if (chartConfigDto.getDisplayDataMarkers()) {
                XYChart.Data<Number, Number> dataPoint = series.getData().get(series.getData().size() - 1);
                String tooltipMessage = chartConfigDto.getxAxisTitle() + ":" + dataPoint.getXValue() + "\n" +
                        chartConfigDto.getyAxisTitle() + ":" + dataPoint.getYValue();
                Tooltip tooltip = new Tooltip(tooltipMessage);
                Tooltip.install(dataPoint.getNode(), tooltip);
                dataPoint.getNode().setOnMouseEntered(event -> dataPoint.getNode().getStyleClass().add("onDataPointHover"));
                dataPoint.getNode().setOnMouseExited(event -> dataPoint.getNode().getStyleClass().remove("onDataPointHover"));
            }
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
            lineChart.setCreateSymbols(chartConfigDto.getDisplayDataMarkers());
            if (chartConfigDto.getClearLineEnabled()) {
                Image image = new Image(getClass().getResourceAsStream("/utils/eraser_cusrsor.png"));
                ImageCursor imageCursor = new ImageCursor(image);
                ImageCursor.getBestSize(20, 20);
                lineChart.setCursor(imageCursor);
            } else {
                lineChart.setCursor(Cursor.DEFAULT);
            }

            verticalTooltipLine.setVisible(chartConfigDto.getDrawVerticalLineEnabled());
            horizontalTooltipLine.setVisible(chartConfigDto.getDrawHorizontalLineEnabled());
        });
    }

    @Override
    public void exportChartAsImage(String directory) {
        Platform.runLater(() -> {
            WritableImage image = root.snapshot(null, null);
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
        File documentsFolder = new File(directory, chartConfigDto.getTitle());
        if (!documentsFolder.exists()) {
            documentsFolder.mkdirs();
        }
        for (XYChart.Series<Number, Number> series : lineChart.getData()) {
            String filePath = documentsFolder.getPath() + "/" + series.getName() + ".csv";
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
    public void autoSizeChart() {
        Platform.runLater(() -> {
            lineChart.getXAxis().setAutoRanging(true);
            lineChart.getYAxis().setAutoRanging(true);
            lineChart.autosize();
            updateDrawings();
        });
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
            currentSeriesId++;
            XYChart.Series<Number, Number> series = new XYChart.Series<>();
            series.setName(name + "_" + currentSeriesId);
            lineChart.getData().add(series);

            Node legendNode = lineChart.getChildrenUnmodifiable()
                    .stream()
                    .filter(node -> node instanceof Legend)
                    .findFirst()
                    .orElse(null);
            if (legendNode == null) {
                return;
            }
            Legend legend = (Legend) legendNode;
            for (Legend.LegendItem legendItem : legend.getItems()) {
                XYChart.Series<Number, Number> dataSeries = lineChart.getData()
                        .stream().filter(ser -> ser.getName().equals(legendItem.getText()))
                        .findFirst()
                        .orElse(null);
                if (dataSeries == null) {
                    continue;
                }
                legendItem.getSymbol().setCursor(Cursor.HAND);
                legendItem.getSymbol().setOnMouseClicked(event -> {
                    if (event.getButton() == MouseButton.PRIMARY) {
                        dataSeries.getNode().setVisible(!dataSeries.getNode().isVisible());
                        if (chartConfigDto.getDisplayDataMarkers()) {
                            dataSeries.getData().forEach(dataPoint -> dataPoint.getNode().setVisible(dataSeries.getNode().isVisible()));
                        }
                    }
                });
            }
        });
    }
}

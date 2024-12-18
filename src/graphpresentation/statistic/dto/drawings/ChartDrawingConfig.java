package graphpresentation.statistic.dto.drawings;

import javafx.scene.shape.Line;
import javafx.scene.shape.Rectangle;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class ChartDrawingConfig {
    private final List<ChartAnnotationData> annotations = new ArrayList<>();
    private final List<ChartLineData> verticalLines = new ArrayList<>();
    private final List<ChartLineData> horizontalLines = new ArrayList<>();
    private Line horizontalTooltipLine;
    private Line verticalTooltipLine;
    private Rectangle verticalLineBox;
    private Rectangle horizontalLineBox;

    public List<ChartLineData> getHorizontalLines() {
        return horizontalLines;
    }

    public List<ChartLineData> getVerticalLines() {
        return verticalLines;
    }

    public List<ChartAnnotationData> getAnnotations() {
        return annotations;
    }

    public Line getHorizontalTooltipLine() {
        return horizontalTooltipLine;
    }

    public Line getVerticalTooltipLine() {
        return verticalTooltipLine;
    }

    public void setHorizontalTooltipLine(Line horizontalTooltipLine) {
        this.horizontalTooltipLine = horizontalTooltipLine;
    }

    public void setVerticalTooltipLine(Line verticalTooltipLine) {
        this.verticalTooltipLine = verticalTooltipLine;
    }

    public Rectangle getVerticalLineBox() {
        return verticalLineBox;
    }

    public void setVerticalLineBox(Rectangle verticalLineBox) {
        this.verticalLineBox = verticalLineBox;
    }

    public Rectangle getHorizontalLineBox() {
        return horizontalLineBox;
    }

    public void setHorizontalLineBox(Rectangle horizontalLineBox) {
        this.horizontalLineBox = horizontalLineBox;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ChartDrawingConfig that = (ChartDrawingConfig) o;
        return Objects.equals(annotations, that.annotations) && Objects.equals(verticalLines, that.verticalLines) && Objects.equals(horizontalLines, that.horizontalLines) && Objects.equals(horizontalTooltipLine, that.horizontalTooltipLine) && Objects.equals(verticalTooltipLine, that.verticalTooltipLine) && Objects.equals(verticalLineBox, that.verticalLineBox) && Objects.equals(horizontalLineBox, that.horizontalLineBox);
    }

    @Override
    public int hashCode() {
        return Objects.hash(annotations, verticalLines, horizontalLines, horizontalTooltipLine, verticalTooltipLine, verticalLineBox, horizontalLineBox);
    }

    @Override
    public String toString() {
        return "ChartDrawingConfig{" +
                "annotations=" + annotations +
                ", verticalLines=" + verticalLines +
                ", horizontalLines=" + horizontalLines +
                ", horizontalTooltipLine=" + horizontalTooltipLine +
                ", verticalTooltipLine=" + verticalTooltipLine +
                ", verticalLineBox=" + verticalLineBox +
                ", horizontalLineBox=" + horizontalLineBox +
                '}';
    }
}

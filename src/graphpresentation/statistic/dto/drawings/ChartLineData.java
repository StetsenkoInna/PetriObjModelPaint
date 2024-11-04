package graphpresentation.statistic.dto.drawings;

import javafx.scene.shape.Line;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

public class ChartLineData {
    private final String id;
    private final Line line;
    private double x;
    private double y;
    private boolean isVertical;

    public ChartLineData(Line line, double position, boolean isVertical) {
        this.id = UUID.randomUUID().toString();
        this.line = line;
        this.isVertical = isVertical;
        if (isVertical) {
            this.x = position;
        } else {
            this.y = position;
        }
    }

    public double getX() {
        return x;
    }

    public void setX(double x) {
        this.x = x;
    }

    public double getY() {
        return y;
    }

    public void setY(double y) {
        this.y = y;
    }

    public boolean isVertical() {
        return isVertical;
    }

    public void setVertical(boolean vertical) {
        isVertical = vertical;
    }

    public String getId() {
        return id;
    }

    public Line getLine() {
        return line;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ChartLineData that = (ChartLineData) o;
        return Double.compare(x, that.x) == 0 && Double.compare(y, that.y) == 0 && isVertical == that.isVertical && Objects.equals(id, that.id) && Objects.equals(line, that.line);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, line, x, y, isVertical);
    }

    @Override
    public String toString() {
        return "ChartLineData{" +
                "id='" + id + '\'' +
                ", line=" + line +
                ", x=" + x +
                ", y=" + y +
                ", isVertical=" + isVertical +
                '}';
    }
}

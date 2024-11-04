package graphpresentation.statistic.dto.drawings;

import javafx.scene.layout.Pane;

import java.util.Objects;
import java.util.UUID;

public class ChartAnnotationData {
    private final String id;
    private double x;
    private double y;
    private String text;
    private Pane node;

    public ChartAnnotationData(String text, double x, double y) {
        this.id = UUID.randomUUID().toString();
        this.text = text;
        this.x = x;
        this.y = y;
    }

    public String getId() {
        return id;
    }

    public String getText() {
        return text;
    }

    public double getX() {
        return x;
    }

    public double getY() {
        return y;
    }

    public Pane getNode() {
        return node;
    }

    public void setText(String text) {
        this.text = text;
    }

    public void setNode(Pane node) {
        this.node = node;
    }

    public void setX(double x) {
        this.x = x;
    }

    public void setY(double y) {
        this.y = y;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ChartAnnotationData that = (ChartAnnotationData) o;
        return Double.compare(x, that.x) == 0 && Double.compare(y, that.y) == 0 && Objects.equals(id, that.id) && Objects.equals(text, that.text);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, text, x, y);
    }

    @Override
    public String toString() {
        return "ChartAnnotationData{" +
                "id='" + id + '\'' +
                ", x=" + x +
                ", y=" + y +
                ", text='" + text + '\'' +
                ", node=" + node +
                '}';
    }
}

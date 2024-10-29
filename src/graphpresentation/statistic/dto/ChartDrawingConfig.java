package graphpresentation.statistic.dto;

import java.util.ArrayList;
import java.util.List;

public class ChartDrawingConfig {
    private final List<ChartLineData> verticalLines = new ArrayList<>();
    private final List<ChartLineData> horizontalLines = new ArrayList<>();

    public List<ChartLineData> getHorizontalLines() {
        return horizontalLines;
    }

    public List<ChartLineData> getVerticalLines() {
        return verticalLines;
    }
}

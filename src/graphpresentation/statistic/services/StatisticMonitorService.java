package graphpresentation.statistic.services;

import graphpresentation.statistic.dto.PetriElementStatisticDto;
import javafx.scene.chart.XYChart;

import java.util.List;

public interface StatisticMonitorService {
    void sendStatistic(double currentTime, List<PetriElementStatisticDto> statistics);

    List<String> getSelectedElementNames();

    boolean getIsFormulaValid();
}

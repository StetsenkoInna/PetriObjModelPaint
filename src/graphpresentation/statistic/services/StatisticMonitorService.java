package graphpresentation.statistic.services;

import graphpresentation.statistic.dto.configs.ChartDataCollectionConfigDto;
import graphpresentation.statistic.dto.data.PetriElementStatisticDto;

import java.util.List;

public interface StatisticMonitorService {
    void appendChartStatistic(double currentTime, List<PetriElementStatisticDto> statistics);

    List<String> getSelectedElementNames();

    boolean getIsFormulaValid();

    ChartDataCollectionConfigDto getChartDataCollectionConfig();
}

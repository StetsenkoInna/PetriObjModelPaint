package graphpresentation.statistic.services;

import graphpresentation.statistic.dto.configs.DataCollectionConfigDto;
import graphpresentation.statistic.dto.data.PetriElementStatisticDto;

import java.util.List;
import java.util.Map;

public interface StatisticMonitorService {
    void appendChartStatistic(double currentTime, List<PetriElementStatisticDto> statistics);

    Map<Integer, List<String>> getElementsWatchMap();

    boolean getIsFormulaValid();

    DataCollectionConfigDto getChartDataCollectionConfig();
}

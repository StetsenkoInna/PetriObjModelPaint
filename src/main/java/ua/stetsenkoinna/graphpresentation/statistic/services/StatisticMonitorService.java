package ua.stetsenkoinna.graphpresentation.statistic.services;

import ua.stetsenkoinna.graphpresentation.statistic.dto.configs.DataCollectionConfigDto;
import ua.stetsenkoinna.graphpresentation.statistic.dto.data.PetriElementStatisticDto;

import java.util.List;
import java.util.Map;

public interface StatisticMonitorService {
    void appendChartStatistic(double currentTime, List<PetriElementStatisticDto> statistics);

    Map<Integer, List<String>> getElementsWatchMap();

    boolean getIsFormulaValid();

    DataCollectionConfigDto getChartDataCollectionConfig();
}

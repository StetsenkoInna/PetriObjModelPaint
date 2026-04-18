package ua.stetsenkoinna.api.statistic;

import ua.stetsenkoinna.api.dto.DataCollectionConfigDto;
import ua.stetsenkoinna.api.dto.PetriElementStatisticDto;

import java.util.List;
import java.util.Map;

public interface StatisticMonitorService {
    void appendChartStatistic(double currentTime, List<PetriElementStatisticDto> statistics);

    Map<Integer, List<String>> getElementsWatchMap();

    boolean getIsFormulaValid();

    DataCollectionConfigDto getChartDataCollectionConfig();
}

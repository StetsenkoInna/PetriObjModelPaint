package ua.stetsenkoinna.api.statistic;

import ua.stetsenkoinna.api.dto.PetriElementStatisticDto;

import java.util.List;

public interface StatisticSink {
    void onStatistic(double time, List<PetriElementStatisticDto> data);
}

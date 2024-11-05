package graphpresentation.statistic.dto.data;

import PetriObj.PetriNet;
import graphpresentation.statistic.dto.configs.ChartDataCollectionConfigDto;
import graphpresentation.statistic.events.StatisticUpdateWorker;
import graphpresentation.statistic.services.StatisticMonitorService;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class StatisticMonitor {
    private final List<String> watchList;
    private final ChartDataCollectionConfigDto dataCollectionConfig;
    private Double lastStatisticCollectionTime;

    public StatisticMonitor(List<String> watchList, ChartDataCollectionConfigDto dataCollectionConfig) {
        this.watchList = watchList;
        this.dataCollectionConfig = dataCollectionConfig;
        this.lastStatisticCollectionTime = dataCollectionConfig.getDataCollectionStartTime() - dataCollectionConfig.getDataCollectionStep();

    }

    public List<String> getWatchList() {
        return watchList;
    }

    public boolean isValidWatchList() {
        return watchList != null && !watchList.isEmpty();
    }

    public Double getDataCollectionStartTime() {
        return dataCollectionConfig.getDataCollectionStartTime();
    }

    public Double getDataCollectionStep() {
        return dataCollectionConfig.getDataCollectionStep();
    }

    public Double getLastStatisticCollectionTime() {
        return lastStatisticCollectionTime;
    }

    public void setLastStatisticCollectionTime(Double lastStatisticCollectionTime) {
        this.lastStatisticCollectionTime = lastStatisticCollectionTime;
    }

    public List<PetriElementStatisticDto> getNetWatchListStatistic(PetriNet petriNet) {
        List<PetriElementStatisticDto> petriStat = new ArrayList<>();
        petriStat.addAll(Arrays.stream(petriNet.getListP())
                .filter(petriP -> watchList.contains(petriP.getName()))
                .map(petriP -> new PetriElementStatisticDto(petriP.getName(), petriP.getObservedMin(), petriP.getObservedMax(), petriP.getMean()))
                .collect(Collectors.toList()));
        petriStat.addAll(Arrays.stream(petriNet.getListT())
                .filter(petriT -> watchList.contains(petriT.getName()))
                .map(petriT -> new PetriElementStatisticDto(petriT.getName(), petriT.getObservedMin(), petriT.getObservedMax(), petriT.getMean()))
                .collect(Collectors.toList()));
        return petriStat;
    }
}

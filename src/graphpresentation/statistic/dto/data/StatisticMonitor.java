package graphpresentation.statistic.dto.data;

import PetriObj.PetriNet;
import graphpresentation.statistic.dto.configs.DataCollectionConfigDto;

import java.util.*;
import java.util.stream.Collectors;

public class StatisticMonitor {
    private Map<Integer, List<String>> watchMap;
    private DataCollectionConfigDto dataCollectionConfig;
    private Double lastStatisticCollectionTime;

    public StatisticMonitor() {
        this.watchMap = new HashMap<>();
        this.dataCollectionConfig = new DataCollectionConfigDto();
        this.lastStatisticCollectionTime = dataCollectionConfig.getDataCollectionStartTime() - dataCollectionConfig.getDataCollectionStep();
    }
    public StatisticMonitor(Map<Integer, List<String>> watchMap, DataCollectionConfigDto dataCollectionConfig) {
        this.watchMap = watchMap;
        this.dataCollectionConfig = dataCollectionConfig;
        this.lastStatisticCollectionTime = dataCollectionConfig.getDataCollectionStartTime() - dataCollectionConfig.getDataCollectionStep();
    }

    public StatisticMonitor(List<String> watchList, DataCollectionConfigDto dataCollectionConfig) {
        this.watchMap = new HashMap<>();
        this.watchMap.put(0, watchList);
        this.dataCollectionConfig = dataCollectionConfig;
        this.lastStatisticCollectionTime = dataCollectionConfig.getDataCollectionStartTime() - dataCollectionConfig.getDataCollectionStep();
    }


    public void setWatchMap(Map<Integer, List<String>> watchMap) {
        System.out.println("watchMap:" + watchMap);
        this.watchMap = watchMap;
    }

    public void setDataCollectionConfig(DataCollectionConfigDto dataCollectionConfig) {
        this.dataCollectionConfig = dataCollectionConfig;
        this.lastStatisticCollectionTime = dataCollectionConfig.getDataCollectionStartTime() - dataCollectionConfig.getDataCollectionStep();
    }

    public Map<Integer, List<String>> getWatchMap() {
        return watchMap;
    }

    public boolean isValidWatchList() {
        return watchMap != null && !watchMap.isEmpty();
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

    public List<PetriElementStatisticDto> getNetWatchListStatistic(Integer petriObjId, PetriNet petriNet) {
        if (!watchMap.containsKey(petriObjId)) {
            return new ArrayList<>();
        }
        List<String> watchList = watchMap.get(petriObjId);
        List<PetriElementStatisticDto> petriStat = new ArrayList<>();
        petriStat.addAll(Arrays.stream(petriNet.getListP())
                .filter(petriP -> watchList.contains(petriP.getName()))
                .map(petriP -> new PetriElementStatisticDto(petriObjId, petriP.getName(), petriP.getObservedMin(), petriP.getObservedMax(), petriP.getMean()))
                .collect(Collectors.toList()));
        petriStat.addAll(Arrays.stream(petriNet.getListT())
                .filter(petriT -> watchList.contains(petriT.getName()))
                .map(petriT -> new PetriElementStatisticDto(petriObjId, petriT.getName(), petriT.getObservedMin(), petriT.getObservedMax(), petriT.getMean()))
                .collect(Collectors.toList()));
        return petriStat;
    }
}

package graphpresentation.statistic.dto.data;


import graphpresentation.statistic.events.StatisticGraphUpdateWorker;
import graphpresentation.statistic.services.StatisticMonitorService;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

public final class StatisticGraphMonitor extends StatisticMonitor {
    private final StatisticGraphUpdateWorker statisticGraphUpdateWorker;
    private final StatisticMonitorService monitorService;

    public StatisticGraphMonitor(StatisticMonitorService monitorService) {
        super(monitorService.getSelectedElementNames(), monitorService.getChartDataCollectionConfig());
        this.monitorService = monitorService;
        this.statisticGraphUpdateWorker = new StatisticGraphUpdateWorker(monitorService);
        this.statisticGraphUpdateWorker.execute();
    }

    public StatisticMonitorService getMonitorService() {
        return monitorService;
    }

    public boolean isValidMonitor() {
        return monitorService != null && monitorService.getIsFormulaValid() && super.isValidWatchList();
    }

    public void instantStatisticSend(double currentTime, List<PetriElementStatisticDto> statistic) {
        monitorService.appendChartStatistic(currentTime, statistic);
    }

    public void asyncStatisticSend(double currentTime, List<PetriElementStatisticDto> statistic) {
        statisticGraphUpdateWorker.publishEvent(currentTime, statistic);
    }

    public void shutdownStatisticUpdate() {
        statisticGraphUpdateWorker.publishTerminationEvent();
    }
}

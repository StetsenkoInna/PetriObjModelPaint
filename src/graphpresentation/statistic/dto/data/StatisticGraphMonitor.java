package graphpresentation.statistic.dto.data;


import graphpresentation.statistic.events.StatisticUpdateWorker;
import graphpresentation.statistic.services.StatisticMonitorService;

import java.util.List;

public final class StatisticGraphMonitor extends StatisticMonitor {
    private final StatisticUpdateWorker statisticUpdateWorker;
    private final StatisticMonitorService monitorService;

    public StatisticGraphMonitor(StatisticMonitorService monitorService) {
        super(monitorService.getSelectedElementNames(), monitorService.getChartDataCollectionConfig());
        this.monitorService = monitorService;
        this.statisticUpdateWorker = new StatisticUpdateWorker(monitorService);
        this.statisticUpdateWorker.execute();
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
        statisticUpdateWorker.publishEvent(currentTime, statistic);
    }

    public void shutdownStatisticUpdate() {
        statisticUpdateWorker.publishTerminationEvent();
    }
}

package graphpresentation.statistic.dto.data;


import graphpresentation.statistic.events.StatisticGraphUpdateWorker;
import graphpresentation.statistic.services.StatisticMonitorService;

import java.util.List;
import java.util.concurrent.CountDownLatch;

public final class StatisticGraphMonitor extends StatisticMonitor {
    private final StatisticGraphUpdateWorker statisticGraphUpdateWorker;
    private final StatisticMonitorService monitorService;
    private final CountDownLatch workerStateLatch;

    public StatisticGraphMonitor(StatisticMonitorService monitorService) {
        super(monitorService.getElementsWatchMap(), monitorService.getChartDataCollectionConfig());
        this.monitorService = monitorService;
        this.workerStateLatch = null;
        this.statisticGraphUpdateWorker = new StatisticGraphUpdateWorker(monitorService);
        this.statisticGraphUpdateWorker.execute();
    }

    public StatisticGraphMonitor(StatisticMonitorService monitorService, CountDownLatch workerStateLatch) {
        super(monitorService.getElementsWatchMap(), monitorService.getChartDataCollectionConfig());
        this.monitorService = monitorService;
        this.workerStateLatch = workerStateLatch;
        this.statisticGraphUpdateWorker = new StatisticGraphUpdateWorker(monitorService, workerStateLatch);
        this.statisticGraphUpdateWorker.execute();
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

    public CountDownLatch getWorkerStateLatch() {
        return workerStateLatch;
    }

    public void shutdownStatisticUpdate() {
        statisticGraphUpdateWorker.publishTerminationEvent();
    }
}

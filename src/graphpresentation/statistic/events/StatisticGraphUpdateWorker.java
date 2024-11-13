package graphpresentation.statistic.events;

import graphpresentation.statistic.dto.data.PetriElementStatisticDto;
import graphpresentation.statistic.services.StatisticMonitorService;

import javax.swing.*;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.*;

/**
 * Worker thread for processing statistic update requests
 * Receives update events and pushes them to monitor dialog chart
 *
 * @author Andrii Kachmar
 */
public class StatisticGraphUpdateWorker extends SwingWorker<Boolean, StatisticUpdateEvent> {
    private final BlockingQueue<StatisticUpdateEvent> eventsQueue;
    private final StatisticMonitorService monitorService;
    private final CountDownLatch workerStateLatch;
    private Boolean isWorking;

    public StatisticGraphUpdateWorker(StatisticMonitorService monitorService, CountDownLatch workerStateLatch) {
        this.eventsQueue = new SynchronousQueue<>(true);
        this.monitorService = monitorService;
        this.workerStateLatch = workerStateLatch;
    }

    public StatisticGraphUpdateWorker(StatisticMonitorService monitorService) {
        this.eventsQueue = new SynchronousQueue<>(true);
        this.monitorService = monitorService;
        this.workerStateLatch = null;
    }

    @Override
    protected Boolean doInBackground() throws Exception {
        try {
            this.isWorking = true;
            while (!isCancelled()) {
                StatisticUpdateEvent event = eventsQueue.take();
                if (event.isTermination()) {
                    break;
                }
                publish(event);
            }
        } finally {
            this.isWorking = false;
        }
        return true;
    }

    @Override
    protected void process(List<StatisticUpdateEvent> chunks) {
        chunks.sort(Comparator.comparing(StatisticUpdateEvent::getCurrentTime));
        for (StatisticUpdateEvent event : chunks) {
            monitorService.appendChartStatistic(event.getCurrentTime(), event.getStatistic());
        }
        if (workerStateLatch != null && !isWorking) {
            workerStateLatch.countDown();
        }
    }

    public void publishEvent(double currentTime, List<PetriElementStatisticDto> statistics) {
        try {
            eventsQueue.put(new StatisticUpdateEvent(currentTime, statistics));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public void publishTerminationEvent() {
        try {
            eventsQueue.put(new StatisticUpdateEvent(true));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}

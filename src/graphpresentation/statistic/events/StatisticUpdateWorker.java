package graphpresentation.statistic.events;

import graphpresentation.statistic.dto.data.PetriElementStatisticDto;
import graphpresentation.statistic.services.StatisticMonitorService;

import javax.swing.*;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public class StatisticUpdateWorker extends SwingWorker<Boolean, StatisticUpdateEvent> {
    private final BlockingQueue<StatisticUpdateEvent> eventsQueue;
    private final StatisticMonitorService monitorService;

    public StatisticUpdateWorker(StatisticMonitorService monitorService) {
        this.eventsQueue = new LinkedBlockingQueue<>();
        this.monitorService = monitorService;
    }

    @Override
    protected Boolean doInBackground() throws Exception {
        while (!isCancelled()) {
            StatisticUpdateEvent event = eventsQueue.take();
            if (event.isTermination()) {
                break;
            }
            publish(event);
        }
        return true;
    }

    @Override
    protected void process(List<StatisticUpdateEvent> chunks) {
        for (StatisticUpdateEvent event : chunks) {
            monitorService.appendChartStatistic(event.getCurrentTime(), event.getStatistic());
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

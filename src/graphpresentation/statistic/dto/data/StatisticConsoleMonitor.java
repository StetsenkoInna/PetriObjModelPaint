package graphpresentation.statistic.dto.data;

import graphpresentation.statistic.dto.configs.DataCollectionConfigDto;
import graphpresentation.statistic.events.StatisticConsoleUpdateWorker;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class StatisticConsoleMonitor extends StatisticMonitor {
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();
    private final String formula;

    public StatisticConsoleMonitor(String formula, Map<Integer, List<String>> watchMap) {
        super(watchMap, new DataCollectionConfigDto());
        this.formula = formula;
    }

    public StatisticConsoleMonitor(String formula, Map<Integer, List<String>> watchMap, DataCollectionConfigDto configDto) {
        super(watchMap, configDto);
        this.formula = formula;
        printStatisticHeader();
    }

    public boolean isValidMonitor() {
        return isValidWatchList();
    }

    public void sendStatistic(double currentTime, List<PetriElementStatisticDto> statistic) {
        executorService.submit(new StatisticConsoleUpdateWorker(formula, currentTime, statistic));
    }

    public void shutdownStatisticUpdate() {
        try {
            System.out.println("Shutdown statistic monitoring...");
            executorService.awaitTermination(2L, TimeUnit.SECONDS);
            executorService.shutdown();
        } catch (InterruptedException e) {
            e.printStackTrace();
            executorService.shutdownNow();
        }
    }

    private void printStatisticHeader() {
        System.out.printf("--------------------------------%n");
        System.out.printf(" Calculated values              %n");
        System.out.printf("--------------------------------%n");
        System.out.printf("| %-10s | %-8s |%n", "TIME", "VALUE");
        System.out.printf("--------------------------------%n");
    }
}

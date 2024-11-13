package graphpresentation.statistic.dto.data;

import graphpresentation.statistic.dto.configs.DataCollectionConfigDto;
import graphpresentation.statistic.events.StatisticConsoleUpdateWorker;
import graphpresentation.statistic.services.FormulaBuilderService;
import graphpresentation.statistic.services.FormulaBuilderServiceImpl;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class StatisticConsoleMonitor extends StatisticMonitor {
    private final String formula;
    private Boolean isMonitoringEnabled;
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();
    private final FormulaBuilderService formulaBuilderService = new FormulaBuilderServiceImpl();

    public StatisticConsoleMonitor(String formula) {
        Map<Integer, List<String>> watchMap = formulaBuilderService.getFormulaElementsWatchMap(formula);
        setWatchMap(watchMap);
        setDataCollectionConfig(new DataCollectionConfigDto());

        this.formula = formula;
        this.isMonitoringEnabled = true;
        if (!formulaBuilderService.isFormulaValid(formula)) {
            throw new ArithmeticException("Entered statistic formula is not valid");
        }

        printHeader();
    }

    public StatisticConsoleMonitor(String formula, DataCollectionConfigDto configDto) {
        Map<Integer, List<String>> watchMap = formulaBuilderService.getFormulaElementsWatchMap(formula);
        setWatchMap(watchMap);
        setDataCollectionConfig(configDto);

        this.formula = formula;
        this.isMonitoringEnabled = true;
        if (!formulaBuilderService.isFormulaValid(formula)) {
            throw new ArithmeticException("Entered statistic formula=[" + formula + "] is not valid");
        }

        printHeader();
    }

    public boolean isValidMonitor() {
        return isValidWatchList();
    }

    public void sendStatistic(double currentTime, List<PetriElementStatisticDto> statistic) {
        if (isMonitoringEnabled) {
            executorService.submit(new StatisticConsoleUpdateWorker(formula, currentTime, statistic));
        }
    }

    public void shutdownStatisticUpdate() {
        if (isMonitoringEnabled) {
            executorService.shutdown();
            System.out.println("Shutdown statistic monitoring...\n");
            try {
                if (!executorService.awaitTermination(10, TimeUnit.SECONDS)) {
                    executorService.shutdownNow();
                }
            } catch (InterruptedException e) {
                executorService.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }

    public Boolean getMonitoringEnabled() {
        return isMonitoringEnabled;
    }

    public void setIsMonitoringEnabled(Boolean displayValuesLive) {
        isMonitoringEnabled = displayValuesLive;
    }

    public void printHeader() {
        if (isMonitoringEnabled) {
            System.out.println("-----------------------------------------------");
            System.out.println(String.format("%-20s | %-20s", "Petri Object Index", "Watch Elements"));
            System.out.println("-----------------------------------------------");
            for (Map.Entry<Integer, List<String>> entry : getWatchMap().entrySet()) {
                Integer index = entry.getKey();
                List<String> watchElements = entry.getValue();
                System.out.printf("%-20d | %-20s%n", index, watchElements);
            }
            System.out.println("-----------------------------------------------");
            System.out.println();
            System.out.println("-----------------------------------------------");
            System.out.println(String.format("%-20s | %-20s", "Time", "Formula Value"));
            System.out.println("-----------------------------------------------");
        }
    }
}

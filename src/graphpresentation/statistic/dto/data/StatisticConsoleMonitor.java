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
    private Boolean isDisplayValuesLive;
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();
    private final FormulaBuilderService formulaBuilderService = new FormulaBuilderServiceImpl();

    public StatisticConsoleMonitor(String formula) {
        Map<Integer, List<String>> watchMap = formulaBuilderService.getFormulaElementsWatchMap(formula);
        setWatchMap(watchMap);
        setDataCollectionConfig(new DataCollectionConfigDto());

        this.formula = formula;
        this.isDisplayValuesLive = true;
        if (!formulaBuilderService.isFormulaValid(formula)) {
            throw new ArithmeticException("Entered statistic formula is not valid");
        }
    }

    public StatisticConsoleMonitor(String formula, DataCollectionConfigDto configDto) {
        Map<Integer, List<String>> watchMap = formulaBuilderService.getFormulaElementsWatchMap(formula);
        setWatchMap(watchMap);
        setDataCollectionConfig(configDto);

        this.formula = formula;
        this.isDisplayValuesLive = true;
        if (!formulaBuilderService.isFormulaValid(formula)) {
            throw new ArithmeticException("Entered statistic formula=[" + formula + "] is not valid");
        }
    }

    public boolean isValidMonitor() {
        return isValidWatchList();
    }

    public void sendStatistic(double currentTime, List<PetriElementStatisticDto> statistic) {
        if (isDisplayValuesLive) {
            executorService.submit(new StatisticConsoleUpdateWorker(formula, currentTime, statistic));
        }
    }

    public void shutdownStatisticUpdate() {
        if (isDisplayValuesLive) {
            try {
                System.out.println("Shutdown statistic monitoring...");
                executorService.awaitTermination(2L, TimeUnit.SECONDS);
                executorService.shutdown();
            } catch (InterruptedException e) {
                e.printStackTrace();
                executorService.shutdownNow();
            }
        }
    }

    public Boolean getDisplayValuesLive() {
        return isDisplayValuesLive;
    }

    public void setDisplayValuesLive(Boolean displayValuesLive) {
        isDisplayValuesLive = displayValuesLive;
    }
}

package ua.stetsenkoinna.graphpresentation.statistic.dto.data;

import ua.stetsenkoinna.PetriObj.PetriNet;
import ua.stetsenkoinna.PetriObj.PetriSim;
import ua.stetsenkoinna.PetriObj.SimulationStatisticCollector;
import ua.stetsenkoinna.api.dto.DataCollectionConfigDto;
import ua.stetsenkoinna.api.dto.PetriElementStatisticDto;
import ua.stetsenkoinna.api.statistic.StatisticMonitor;
import ua.stetsenkoinna.graphpresentation.statistic.events.StatisticConsoleUpdateWorker;
import ua.stetsenkoinna.graphpresentation.statistic.services.FormulaBuilderService;
import ua.stetsenkoinna.graphpresentation.statistic.services.FormulaBuilderServiceImpl;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class StatisticConsoleMonitor extends StatisticMonitor implements SimulationStatisticCollector {

    /** Accumulator for the current time-step (flushed in flush()). */
    private final List<PetriElementStatisticDto> stepAccumulator = new ArrayList<>();
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

    // ── SimulationStatisticCollector interface ──────────────────────────────

    @Override
    public boolean shouldCollect(double currentTime) {
        return isValidMonitor()
                && Boolean.TRUE.equals(isMonitoringEnabled)
                && currentTime >= getDataCollectionStartTime()
                && currentTime - getLastStatisticCollectionTime() >= getDataCollectionStep();
    }

    @Override
    public void onTimeStep(double currentTime, PetriNet net, int petriObjId) {
        stepAccumulator.addAll(getNetWatchListStatistic(petriObjId, net));
    }

    @Override
    public void flush(double currentTime) {
        if (!stepAccumulator.isEmpty()) {
            setLastStatisticCollectionTime(currentTime);
            sendStatistic(currentTime, new ArrayList<>(stepAccumulator));
            stepAccumulator.clear();
        }
    }

    @Override
    public void onSimulationEnd(double simulationEndTime, Iterable<PetriSim> objects) {
        if (!isValidMonitor() || !Boolean.TRUE.equals(isMonitoringEnabled)) return;
        List<PetriElementStatisticDto> statistic = new ArrayList<>();
        for (PetriSim e : objects) {
            statistic.addAll(getNetWatchListStatistic(e.getNumObj(), e.getNet()));
        }
        if (!statistic.isEmpty()) {
            sendStatistic(simulationEndTime, statistic);
        }
    }

    @Override
    public void shutdown() {
        shutdownStatisticUpdate();
    }

    // ────────────────────────────────────────────────────────────────────────

    public void printHeader() {
        if (isMonitoringEnabled) {
            System.out.println("-----------------------------------------------");
            System.out.printf("%-20s | %-20s%n", "Petri Object Index", "Watch Elements");
            System.out.println("-----------------------------------------------");
            for (Map.Entry<Integer, List<String>> entry : getWatchMap().entrySet()) {
                Integer index = entry.getKey();
                List<String> watchElements = entry.getValue();
                System.out.printf("%-20d | %-20s%n", index, watchElements);
            }
            System.out.println("-----------------------------------------------");
            System.out.println();
            System.out.println("-----------------------------------------------");
            System.out.printf("%-20s | %-20s%n", "Time", "Formula Value");
            System.out.println("-----------------------------------------------");
        }
    }
}

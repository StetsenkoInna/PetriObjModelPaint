package ua.stetsenkoinna.graphpresentation.statistic.events;

import ua.stetsenkoinna.api.dto.PetriElementStatisticDto;
import ua.stetsenkoinna.graphpresentation.statistic.services.FormulaBuilderService;
import ua.stetsenkoinna.graphpresentation.statistic.services.FormulaBuilderServiceImpl;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class StatisticConsoleUpdateWorker implements Runnable {

    private static final Logger log = LoggerFactory.getLogger(StatisticConsoleUpdateWorker.class);

    private final String formula;
    private final double currentTime;
    private final List<PetriElementStatisticDto> statisticDtos;
    private final FormulaBuilderService formulaBuilderService;

    public StatisticConsoleUpdateWorker(String formula, double currentTime, List<PetriElementStatisticDto> statisticDtos) {
        this.formula = formula;
        this.currentTime = currentTime;
        this.statisticDtos = statisticDtos;
        this.formulaBuilderService = new FormulaBuilderServiceImpl();
    }

    @Override
    public void run() {
        double result = (double) formulaBuilderService.calculateFormula(formula, statisticDtos);
        log.info(String.format("%-20s | %-20s", currentTime, result));
    }
}

package ua.stetsenkoinna.graphpresentation.statistic.events;

import ua.stetsenkoinna.graphpresentation.statistic.dto.data.PetriElementStatisticDto;
import ua.stetsenkoinna.graphpresentation.statistic.services.FormulaBuilderService;
import ua.stetsenkoinna.graphpresentation.statistic.services.FormulaBuilderServiceImpl;

import java.util.List;

public class StatisticConsoleUpdateWorker implements Runnable {
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
        System.out.printf("%-20s | %-20s%n", currentTime, result);
    }
}

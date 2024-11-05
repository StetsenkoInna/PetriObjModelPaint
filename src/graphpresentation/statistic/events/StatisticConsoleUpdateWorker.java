package graphpresentation.statistic.events;

import graphpresentation.statistic.dto.data.PetriElementStatisticDto;
import graphpresentation.statistic.services.FormulaBuilderService;
import graphpresentation.statistic.services.FormulaBuilderServiceImpl;

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
        Number result = formulaBuilderService.calculateFormula(formula, statisticDtos);
        System.out.println(currentTime + ";" + result);
    }
}

package graphpresentation.statistic.formula;

import graphpresentation.statistic.dto.PetriElementStatisticDto;
import javafx.scene.chart.XYChart;

import java.util.List;

public interface FormulaBuilderService {
    String updateFormula(String formula, String input);
    List<String> getSuggestions(String input);
    List<String> getSelectedElements(String formula);
    Number calculateFormula(String formula, List<PetriElementStatisticDto> statistics);
}

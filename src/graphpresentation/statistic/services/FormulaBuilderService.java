package graphpresentation.statistic.services;

import graphpresentation.statistic.dto.data.PetriElementStatisticDto;

import javax.swing.*;
import java.util.List;
import java.util.Map;

public interface FormulaBuilderService {
    String updateFormula(String formula, String input);
    List<String> getFormulaSuggestions(String input);
    Map<Integer, List<String>> getFormulaElementsWatchMap(String formula);
    boolean isFormulaValid(String formula);
    Number calculateFormula(String formula, List<PetriElementStatisticDto> statistics);
}

package graphpresentation.statistic.formula;

import graphpresentation.statistic.dto.PetriElementStatisticDto;

import java.util.List;

public interface FormulaBuilderService {
    String updateFormula(String formula, String input);
    List<String> getFormulaSuggestions(String input);
    List<String> getSelectedPetriElementNames(String formula);
    boolean isFormulaValid(String formula);
    Number calculateFormula(String formula, List<PetriElementStatisticDto> statistics);
}

package graphpresentation.statistic.formula;

import java.util.List;

public interface FormulaBuilderService {
    String updateFormula(String formula, String input);
    List<String> getSuggestions(String input);
}

package graphpresentation.statistic.formula;

import graphnet.GraphPetriNet;
import graphnet.GraphPetriPlace;
import graphpresentation.GraphTransition;
import graphpresentation.statistic.enums.FunctionType;
import graphpresentation.statistic.enums.PetriStatFunction;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class FormulaBuilderServiceImpl implements FormulaBuilderService {
    private final GraphPetriNet graphPetriNet;

    public FormulaBuilderServiceImpl(GraphPetriNet graphPetriNet) {
        this.graphPetriNet = graphPetriNet;
    }

    @Override
    public String updateFormula(String formula, String input) {
        int lastOperatorIndex = findLastOperatorIndex(formula);
        String lastPart = lastOperatorIndex == -1 ? formula : formula.substring(lastOperatorIndex + 1).trim();
        if (lastPart.contains("(")) {
            int lastOpenParenIndex = lastPart.lastIndexOf("(");
            int closingParenIndex = lastPart.indexOf(")", lastOpenParenIndex);
            if (closingParenIndex != -1) {
                String prefix = formula.substring(0, lastOperatorIndex + 1).trim();
                String updatedFunctionCall = lastPart.substring(0, lastOpenParenIndex + 1) + input + ")";
                return prefix + updatedFunctionCall;
            }
        }

        return formula + input;
    }

    @Override
    public List<String> getSuggestions(String input) {
        List<String> suggestions = new ArrayList<>();
        String trimmedInput = input.trim();
        if (trimmedInput.isEmpty()) {
            return suggestions;
        }


        int lastOperatorIndex = findLastOperatorIndex(trimmedInput);
        String lastPart = lastOperatorIndex == -1 ? trimmedInput : trimmedInput.substring(lastOperatorIndex + 1).trim();

        if (lastPart.contains("(")) {
            String functionName = lastPart.substring(0, lastPart.indexOf("(")).trim();
            PetriStatFunction currentFunction = PetriStatFunction.findFunctionByName(functionName);
            if (currentFunction != null) {
                String argumentName = lastPart.substring(lastPart.indexOf("(") + 1).trim();
                suggestions.addAll(getElementSuggestions(currentFunction, argumentName));
            }
        } else {
            if (lastPart.isEmpty()) {
                suggestions.addAll(getOperatorSuggestions());
            } else {
                suggestions.addAll(PetriStatFunction.filterFunctionsByName(input));
            }
        }

        return suggestions;
    }


    private int findLastOperatorIndex(String input) {
        int lastPlus = input.lastIndexOf("+");
        int lastMinus = input.lastIndexOf("-");
        int lastMultiply = input.lastIndexOf("*");
        int lastDivide = input.lastIndexOf("/");
        return Math.max(Math.max(lastPlus, lastMinus), Math.max(lastMultiply, lastDivide));
    }

    private List<String> getElementSuggestions(PetriStatFunction petriStatFunction, String input) {
        List<String> elements = new ArrayList<>();
        if (petriStatFunction.getFunctionType().equals(FunctionType.POSITION_BASED)) {
            List<String> petriPlaceSuggestions = graphPetriNet.getGraphPetriPlaceList().stream()
                    .map(GraphPetriPlace::getName)
                    .filter(place -> place.toUpperCase().startsWith(input))
                    .collect(Collectors.toList());
            elements.addAll(petriPlaceSuggestions);
        } else {
            List<String> petriPlaceSuggestions = graphPetriNet.getGraphPetriTransitionList().stream()
                    .map(GraphTransition::getName)
                    .filter(place -> place.toUpperCase().startsWith(input))
                    .collect(Collectors.toList());
            elements.addAll(petriPlaceSuggestions);
        }
        return elements;
    }

    private List<String> getOperatorSuggestions() {
        return Arrays.asList("+", "-", "*", "/");
    }
}

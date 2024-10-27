package graphpresentation.statistic.formula;

import graphnet.GraphPetriNet;
import graphnet.GraphPetriPlace;
import graphpresentation.GraphTransition;
import graphpresentation.statistic.dto.PetriElementStatisticDto;
import graphpresentation.statistic.enums.FunctionType;
import graphpresentation.statistic.enums.PetriStatFunction;

import java.util.*;
import java.util.stream.Collectors;


public class FormulaBuilderServiceImpl implements FormulaBuilderService {
    private final GraphPetriNet graphPetriNet;
    private static final List<PetriStatFunction> PETRI_STAT_FUNCTIONS = Arrays.asList(PetriStatFunction.values());
    private static final List<String> OPERATORS = Arrays.asList("+", "-", "*", "/");

    public FormulaBuilderServiceImpl(GraphPetriNet graphPetriNet) {
        this.graphPetriNet = graphPetriNet;
    }

    @Override
    public String updateFormula(String formula, String input) {
        return formula + input;
    }

    @Override
    public List<String> getFormulaSuggestions(String input) {
        if (input == null || input.isEmpty()) {
            return PetriStatFunction.getFunctionNames();
        }
        String lastOperation = getLastOperation(input);

        if (lastOperation.isEmpty()) {
            return PetriStatFunction.filterFunctionsByName(input);
        } else if (lastOperation.endsWith("(")) {
            String functionName = lastOperation.substring(0, lastOperation.length() - 1);
            String functionArgumentName = getFunctionArgumentName(lastOperation);
            PetriStatFunction function = PetriStatFunction.findFunctionByName(functionName);
            return function != null
                    ? getElementSuggestions(function, functionArgumentName)
                    : Collections.emptyList();
        } else if (OPERATORS.contains(lastOperation)) {
            return PetriStatFunction.getFunctionNames();
        } else {
            return PetriStatFunction.getFunctionNames().stream()
                    .filter(key -> key.toUpperCase().startsWith(lastOperation.toUpperCase()))
                    .collect(Collectors.toList());
        }
    }

    private String getLastOperation(String input) {
        String trimmedInput = input.trim();
        int lastOperatorIndex = Math.max(
                trimmedInput.lastIndexOf('+'),
                Math.max(trimmedInput.lastIndexOf('-'),
                        Math.max(trimmedInput.lastIndexOf('*'),
                                trimmedInput.lastIndexOf('/')))
        );
        return lastOperatorIndex >= 0
                ? trimmedInput.substring(lastOperatorIndex + 1).trim()
                : trimmedInput;
    }

    @Override
    public List<String> getSelectedPetriElementNames(String formula) {
        List<String> contents = new ArrayList<>();
        int openParenIndex = 0;
        while ((openParenIndex = formula.indexOf("(", openParenIndex)) != -1) {
            int closeParenIndex = formula.indexOf(")", openParenIndex);
            if (closeParenIndex == -1) {
                break;
            }
            String contentInsideParens = formula.substring(openParenIndex + 1, closeParenIndex).trim();
            contents.add(contentInsideParens);
            openParenIndex = closeParenIndex + 1;
        }
        return contents;
    }

    @Override
    public Number calculateFormula(String formula, List<PetriElementStatisticDto> statistics) {
        double result = 0.0;
        String[] parts = formula.split("\\+");

        for (String part : parts) {
            part = part.trim();
            String elementName = getFunctionArgumentName(part);
            PetriElementStatisticDto statistic = statistics.stream()
                    .filter(petriElementStatistic -> petriElementStatistic.getElementName().equals(elementName))
                    .findFirst().orElse(null);
            if (statistic != null) {
                if (part.startsWith(PetriStatFunction.P_MIN.getFunctionName())) {
                    result += (int) statistic.getMin();
                } else if (part.startsWith(PetriStatFunction.P_MAX.getFunctionName())) {
                    result += (int) statistic.getMax();
                } else if (part.startsWith(PetriStatFunction.P_AVG.getFunctionName())) {
                    result += statistic.getAvg();
                } else if (part.startsWith(PetriStatFunction.T_MIN.getFunctionName())) {
                    result += (double) statistic.getMin();
                } else if (part.startsWith(PetriStatFunction.T_MAX.getFunctionName())) {
                    result += (double) statistic.getMax();
                } else if (part.startsWith(PetriStatFunction.T_AVG.getFunctionName())) {
                    result += statistic.getAvg();
                }
            }
        }
        return result;
    }

    private List<String> getElementSuggestions(PetriStatFunction petriStatFunction, String input) {
        if (petriStatFunction == null) {
            return new ArrayList<>();
        }

        List<String> elements = new ArrayList<>();
        List<String> placeNames = graphPetriNet.getGraphPetriPlaceList().stream()
                .map(GraphPetriPlace::getName)
                .collect(Collectors.toList());
        List<String> transitionNames = graphPetriNet.getGraphPetriTransitionList().stream()
                .map(GraphTransition::getName)
                .collect(Collectors.toList());
        if (petriStatFunction.getFunctionType().equals(FunctionType.POSITION_BASED)) {
            if (input != null) {
                placeNames = placeNames.stream()
                        .filter(name -> name.toUpperCase().startsWith(input.toUpperCase()))
                        .collect(Collectors.toList());
            }
            elements.addAll(placeNames);
        } else if (petriStatFunction.getFunctionType().equals(FunctionType.TRANSITION_BASED)) {
            if (input != null) {
                transitionNames = transitionNames.stream()
                        .filter(name -> name.toUpperCase().startsWith(input.toUpperCase()))
                        .collect(Collectors.toList());
            }
            elements.addAll(transitionNames);
        } else {
            elements.addAll(placeNames);
            elements.addAll(transitionNames);
        }
        return elements;
    }

    private String getFunctionArgumentName(String input) {
        int openParenIndex = input.indexOf('(');
        int closeParenIndex = input.lastIndexOf(')');
        if (openParenIndex > 0 && closeParenIndex > openParenIndex) {
            return input.substring(openParenIndex + 1, closeParenIndex).trim().toUpperCase();
        }
        return "";
    }
}

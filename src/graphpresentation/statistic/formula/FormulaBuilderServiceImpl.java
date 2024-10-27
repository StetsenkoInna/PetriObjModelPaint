package graphpresentation.statistic.formula;

import graphnet.GraphPetriNet;
import graphnet.GraphPetriPlace;
import graphpresentation.GraphTransition;
import graphpresentation.PetriNetsFrame;
import graphpresentation.statistic.dto.PetriElementStatisticDto;
import graphpresentation.statistic.enums.FunctionType;
import graphpresentation.statistic.enums.PetriStatFunction;

import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;


public class FormulaBuilderServiceImpl implements FormulaBuilderService {
    private final PetriNetsFrame petriNetParent;
    private static final List<String> OPERATORS = Arrays.asList("+", "-", "*", "/");
    private static final Pattern VALID_CHARACTERS_PATTERN = Pattern.compile("^[A-Za-z0-9_() +\\-*/\\u0400-\\u04FF]*$");

    public FormulaBuilderServiceImpl(PetriNetsFrame parent) {
        this.petriNetParent = parent;
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
    public boolean isFormulaValid(String formula) {
        if (formula == null || formula.isEmpty()) {
            return false;
        }

        if (!VALID_CHARACTERS_PATTERN.matcher(formula).matches() || !hasBalancedParentheses(formula)) {
            return false;
        }

        String[] operations = formula.split("(?=[+\\-*/])|(?<=[+\\-*/])");
        boolean isPreviousMathOperator = true;

        for (String operation : operations) {
            operation = operation.trim();
            if (operation.isEmpty()) {
                continue;
            }

            if (OPERATORS.contains(operation)) {
                isPreviousMathOperator = true;
                continue;
            }

            if (!operation.endsWith("()") && !operation.contains("(")) {
                return false;
            }

            if (operation.indexOf(")") != operation.length() - 1) {
                return false;
            }

            String lastFunctionName = getLastFunctionName(operation);
            PetriStatFunction function = PetriStatFunction.findFunctionByName(lastFunctionName);
            if (function != null) {
                String argument = getFunctionArgumentName(operation);
                if (argument == null || argument.isEmpty() || !isValidArgument(function, argument)) {
                    return false;
                }
                if (!isPreviousMathOperator) {
                    return false;
                }
                isPreviousMathOperator = false;
            } else {
                return false;
            }
        }

        return !isPreviousMathOperator;
    }

    @Override
    public Number calculateFormula(String formula, List<PetriElementStatisticDto> statistics) { //TODO REVIEW
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
        List<String> placeNames = getGraphNet().getGraphPetriPlaceList().stream()
                .map(GraphPetriPlace::getName)
                .collect(Collectors.toList());
        List<String> transitionNames = getGraphNet().getGraphPetriTransitionList().stream()
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
        return null;
    }

    private String getLastFunctionName(String token) {
        if (token.endsWith("(")) {
            return token.substring(0, token.length() - 1).trim();
        }
        int openParenIndex = token.indexOf('(');
        if (openParenIndex > -1) {
            return token.substring(0, openParenIndex).trim();
        }
        return null;
    }

    private boolean hasBalancedParentheses(String formula) {
        Stack<Character> stack = new Stack<>();
        for (char c : formula.toCharArray()) {
            if (c == '(') {
                stack.push(c);
            } else if (c == ')') {
                if (stack.isEmpty()) {
                    return false;
                }
                stack.pop();
            }
        }
        return stack.isEmpty();
    }

    private boolean isValidArgument(PetriStatFunction function, String argument) {
        List<String> elements;
        if (function.getFunctionType() == FunctionType.POSITION_BASED) {
            elements = getGraphNet().getGraphPetriPlaceList().stream()
                    .map(place -> place.getName().toUpperCase())
                    .collect(Collectors.toList());
        } else {
            elements = getGraphNet().getGraphPetriTransitionList().stream()
                    .map(transition -> transition.getName().toUpperCase())
                    .collect(Collectors.toList());
        }
        return elements.contains(argument.toUpperCase());
    }

    private GraphPetriNet getGraphNet() {
        return petriNetParent.getPetriNetsPanel().getGraphNet();
    }
}

package graphpresentation.statistic.serviceImpl;

import graphnet.GraphPetriNet;
import graphnet.GraphPetriPlace;
import graphpresentation.GraphTransition;
import graphpresentation.PetriNetsFrame;
import graphpresentation.statistic.dto.PetriElementStatisticDto;
import graphpresentation.statistic.enums.FunctionType;
import graphpresentation.statistic.enums.PetriStatFunction;
import graphpresentation.statistic.services.FormulaBuilderService;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;


public class FormulaBuilderServiceImpl implements FormulaBuilderService {
    private final PetriNetsFrame petriNetParent;
    private static final List<String> OPERATORS = Arrays.asList("+", "-", "*", "/");
    private static final Pattern VALID_CHARACTERS = Pattern.compile("^[A-Za-z0-9_.() +\\-*/\\u0400-\\u04FF]*$");
    private static final Pattern FUNCTION_CALL_PATTERN = Pattern.compile("([A-Z_0-9]+)\\(([^()]*)\\)");
    private static final Pattern DIVISION_BY_ZERO = Pattern.compile("/\\s*0(?!\\.[0-9])");


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
            String functionArgumentName = getFunctionArgument(lastOperation);
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
        if (!VALID_CHARACTERS.matcher(formula).matches()) {
            return false;
        }
        if (!hasBalancedParentheses(formula)) {
            return false;
        }

        char lastElement = formula.charAt(formula.length() - 1);
        if (OPERATORS.contains(String.valueOf(lastElement))) {
            return false;
        }

        if (DIVISION_BY_ZERO.matcher(formula).find()) {
            return false;
        }

        return validateOperations(splitFormula(formula));
    }

    private boolean validateOperations(List<String> operations) {
        if (OPERATORS.contains(operations.get(operations.size() - 1))) {
            return false;
        }

        for (String operation : operations) {
            operation = operation.trim();
            if (operation.isEmpty()) {
                continue;
            }

            if (OPERATORS.contains(operation)) {
                continue;
            }

            if (operation.matches("^[0-9]+(\\.[0-9]+)?$")) {
                continue;
            }

            if (operation.startsWith("(") && operation.endsWith(")")) {
                String nestedFormula = removeOuterParentheses(operation);
                List<String> nestedOperations = splitFormula(nestedFormula);
                return validateOperations(nestedOperations);
            }

            if (!operation.endsWith("()") && !operation.contains("(")) {
                return false;
            }

            if (operation.indexOf(")") != operation.length() - 1) {
                return false;
            }

            String lastFunctionName = getOperationFunctionName(operation);
            PetriStatFunction function = PetriStatFunction.findFunctionByName(lastFunctionName);
            if (function == null) {
                return false;
            }

            String argument = getFunctionArgument(operation);
            if (argument == null || argument.isEmpty() || !isValidArgumentElement(function, argument)) {
                return false;
            }
        }

        return true;
    }

    private List<String> splitFormula(String formula) {
        List<String> operations = new ArrayList<>();
        StringBuilder currentOperation = new StringBuilder();
        int parenthesisDepth = 0;

        for (char c : formula.toCharArray()) {
            if (c == '(') {
                parenthesisDepth++;
            } else if (c == ')') {
                parenthesisDepth--;
            }

            if ((OPERATORS.contains(String.valueOf(c)) && parenthesisDepth == 0) && currentOperation.length() > 0) {
                operations.add(currentOperation.toString());
                currentOperation = new StringBuilder();
                operations.add(String.valueOf(c));
            } else {
                currentOperation.append(c);
            }
        }
        if (currentOperation.length() > 0) {
            operations.add(currentOperation.toString());
        }
        return operations;
    }

    private String removeOuterParentheses(String expression) {
        while (expression.startsWith("(") && expression.endsWith(")") &&
                hasBalancedParentheses(expression.substring(1, expression.length() - 1))) {
            expression = expression.substring(1, expression.length() - 1);
        }
        return expression;
    }

    @Override
    public Number calculateFormula(String formula, List<PetriElementStatisticDto> statistics) {
        Number result = null;
        try {
            Matcher functionMatcher = FUNCTION_CALL_PATTERN.matcher(formula);
            StringBuffer numericExpression = new StringBuffer();
            while (functionMatcher.find()) {
                String functionName = functionMatcher.group(1);
                String argument = functionMatcher.group(2);
                Number functionValue = getFunctionValue(PetriStatFunction.findFunctionByName(functionName), argument, statistics);
                if (functionValue != null) {
                    functionMatcher.appendReplacement(numericExpression, functionValue.toString());
                }
            }
            functionMatcher.appendTail(numericExpression);
            result = ExpressionEvaluateUtil.evaluateExpression(numericExpression.toString());

            System.out.println("IN FORMULA:" + formula);
            System.out.println("OUT FORMULA:" + numericExpression);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return result;
    }

    private Number getFunctionValue(PetriStatFunction function, String argumentName, List<PetriElementStatisticDto> statistics) {
        if (function == null || argumentName == null || statistics == null || statistics.isEmpty()) {
            return null;
        }
        PetriElementStatisticDto argumentStatistic = statistics.stream()
                .filter(petriElementStatistic -> petriElementStatistic.getElementName().equals(argumentName))
                .findFirst()
                .orElse(null);
        if (argumentStatistic == null) {
            return null;
        }
        switch (function) {
            case P_MIN:
            case T_MIN: {
                return argumentStatistic.getMin();
            }
            case P_MAX:
            case T_MAX: {
                return argumentStatistic.getMax();
            }
            case P_AVG:
            case T_AVG: {
                return argumentStatistic.getAvg();
            }
        }
        return null;
    }


    private List<String> getElementSuggestions(PetriStatFunction petriStatFunction, String input) {
        if (petriStatFunction == null) {
            return new ArrayList<>();
        }

        List<String> elements = new ArrayList<>();
        List<String> placeNames = getCurrentGraphNet().getGraphPetriPlaceList().stream()
                .map(GraphPetriPlace::getName)
                .collect(Collectors.toList());
        List<String> transitionNames = getCurrentGraphNet().getGraphPetriTransitionList().stream()
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

    private String getFunctionArgument(String input) {
        int openParenIndex = input.indexOf('(');
        int closeParenIndex = input.lastIndexOf(')');
        if (openParenIndex > 0 && closeParenIndex > openParenIndex) {
            return input.substring(openParenIndex + 1, closeParenIndex).trim().toUpperCase();
        }
        return null;
    }

    private String getOperationFunctionName(String operation) {
        if (operation.endsWith("(")) {
            return operation.substring(0, operation.length() - 1).trim();
        }
        int openParenIndex = operation.indexOf('(');
        if (openParenIndex > -1) {
            return operation.substring(0, openParenIndex).trim();
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

    private boolean isValidArgumentElement(PetriStatFunction function, String argument) {
        GraphPetriNet net = getCurrentGraphNet();
        List<String> elements = new ArrayList<>();
        if (function.getFunctionType() == FunctionType.POSITION_BASED) {
            elements = net.getGraphPetriPlaceList().stream()
                    .map(place -> place.getName().toUpperCase())
                    .collect(Collectors.toList());
        } else if (function.getFunctionType() == FunctionType.TRANSITION_BASED) {
            elements = net.getGraphPetriTransitionList().stream()
                    .map(transition -> transition.getName().toUpperCase())
                    .collect(Collectors.toList());
        }
        return elements.contains(argument.toUpperCase());
    }

    private GraphPetriNet getCurrentGraphNet() {
        return petriNetParent.getPetriNetsPanel().getGraphNet();
    }
}

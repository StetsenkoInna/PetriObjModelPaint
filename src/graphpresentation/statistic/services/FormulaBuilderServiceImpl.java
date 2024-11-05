package graphpresentation.statistic.services;

import graphnet.GraphPetriNet;
import graphnet.GraphPetriPlace;
import graphpresentation.GraphTransition;
import graphpresentation.PetriNetsFrame;
import graphpresentation.statistic.dto.data.PetriElementStatisticDto;
import graphpresentation.statistic.enums.PetriStatisticFunction;

import javax.swing.*;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Petri net formula builder service
 *
 * @author Andrii Kachmar
 */
public class FormulaBuilderServiceImpl implements FormulaBuilderService {
    private PetriNetsFrame petriNetParent;
    private static final List<String> OPERATORS = Arrays.asList("+", "-", "*", "/");
    private static final Pattern VALID_CHARACTERS = Pattern.compile("^[A-Za-z0-9_.;() +\\-*/\\u0400-\\u04FF]*$");
    private static final Pattern FUNCTION_CALL_PATTERN = Pattern.compile("([A-Z_0-9]+)\\(([^()]*?(?:;[^()]*?)*)\\)");
    private static final Pattern DIVISION_BY_ZERO = Pattern.compile("/\\s*0(?!\\.[0-9])");


    public FormulaBuilderServiceImpl(PetriNetsFrame parent) {
        this.petriNetParent = parent;
    }

    public FormulaBuilderServiceImpl() {

    }

    @Override
    public String updateFormula(JTextArea formulaField, String input) {
        String currentText = formulaField.getText();
        int caretPosition = formulaField.getCaretPosition();

        String textBeforeCaret = currentText.substring(0, caretPosition);
        String textAfterCaret = currentText.substring(caretPosition);

        int lastSpaceIndex = textBeforeCaret.lastIndexOf(' ');
        int lastOpenParenIndex = textBeforeCaret.lastIndexOf('(');
        int lastCloseParenIndex = textBeforeCaret.lastIndexOf(')');

        String newFormula;
        if (lastOpenParenIndex > lastSpaceIndex) {
            if (lastCloseParenIndex > lastOpenParenIndex) {
                newFormula = textBeforeCaret.substring(0, lastOpenParenIndex + 1) + input + textAfterCaret;
            } else {
                newFormula = textBeforeCaret + input + textAfterCaret;
            }
        } else {
            newFormula = textBeforeCaret.substring(0, lastSpaceIndex + 1) + input + textAfterCaret;
        }

        formulaField.setText(newFormula);

        if (lastOpenParenIndex > lastSpaceIndex) {
            formulaField.setCaretPosition(lastOpenParenIndex + 1 + input.length());
        } else {
            formulaField.setCaretPosition(lastSpaceIndex + 1 + input.length());
        }

        return newFormula;
    }

    @Override
    public List<String> getFormulaSuggestions(String formula) {
        if (formula == null || formula.isEmpty()) {
            return PetriStatisticFunction.getFunctionNames();
        }
        List<String> operators = splitFormula(formula);
        String operation = operators.get(operators.size() - 1);

        String functionName = getOperationFunctionName(operation);
        String functionArgumentName = getFunctionArgument(operation);
        if (operation.isEmpty()) {
            return PetriStatisticFunction.filterFunctionsByName(formula);
        } else if (functionName != null) {
            PetriStatisticFunction function = PetriStatisticFunction.findFunctionByName(functionName);
            return function != null
                    ? getElementSuggestions(function, functionArgumentName)
                    : Collections.emptyList();
        } else if (OPERATORS.contains(operation)) {
            return PetriStatisticFunction.getFunctionNames();
        } else {
            return PetriStatisticFunction.filterFunctionsByName(operation);
        }
    }

    @Override
    public List<String> getSelectedPetriElementNames(String formula) {
        List<String> elements = new ArrayList<>();
        Matcher functionMatcher = FUNCTION_CALL_PATTERN.matcher(formula);
        while (functionMatcher.find()) {
            String argument = functionMatcher.group(2).trim();
            if (!argument.isEmpty()) {
                elements.addAll(Arrays.asList(argument.split(";")));
            }
        }
        return elements;
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
        if (operations.isEmpty() || OPERATORS.contains(operations.get(operations.size() - 1))) {
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
            PetriStatisticFunction function = PetriStatisticFunction.findFunctionByName(lastFunctionName);
            if (function == null) {
                return false;
            }

            String argument = getFunctionArgument(operation);
            if (argument == null || argument.isEmpty() || (function.hasSeparator() && argument.endsWith(function.getArgumentType().getSeparator()))) {
                return false;
            }

            if (!isValidFunctionArgument(function, argument)) {
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
                Number functionValue = getFunctionValue(PetriStatisticFunction.findFunctionByName(functionName), argument, statistics);
                if (functionValue != null) {
                    functionMatcher.appendReplacement(numericExpression, functionValue.toString());
                }
            }
            functionMatcher.appendTail(numericExpression);
            if (numericExpression.toString().matches("\\d+(\\.\\d+)?")) {
                result = Double.parseDouble(numericExpression.toString());
            } else if (!numericExpression.toString().matches(".*[a-zA-Z].*")) {
                result = ExpressionEvaluateUtil.evaluateExpression(numericExpression.toString());
            } else {
                result = null;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return result;
    }

    private Number getFunctionValue(PetriStatisticFunction function, String argument, List<PetriElementStatisticDto> statistics) {
        if (function == null || argument == null || statistics == null || statistics.isEmpty()) {
            return null;
        }

        if (function.getArgumentType() == PetriStatisticFunction.FunctionArgumentType.SINGLE_ELEMENT) {
            Map.Entry<Integer, String> argumentEntry = getArgumentIdName(argument);
            PetriElementStatisticDto argumentStatistic = statistics.stream()
                    .filter(petriElementStatistic -> petriElementStatistic.getElementName().equals(argumentEntry.getValue()) &&
                            petriElementStatistic.getPetriObjId().equals(argumentEntry.getKey()))
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
        } else if (function.getArgumentType() == PetriStatisticFunction.FunctionArgumentType.MULTIPLE_ELEMENT) {
            List<Map.Entry<Integer, String>> entries = Arrays.asList(argument.split(function.getArgumentType().getSeparator())).stream()
                    .map(this::getArgumentIdName)
                    .collect(Collectors.toList());
            List<PetriElementStatisticDto> argumentsStatistic = statistics.stream()
                    .filter(petriElementStatistic -> entries.contains(new AbstractMap.SimpleEntry<>(petriElementStatistic.getPetriObjId(), petriElementStatistic.getElementName())))
                    .collect(Collectors.toList());
            switch (function) {
                case SUM_MIN: {
                    return argumentsStatistic.stream()
                            .mapToDouble(PetriElementStatisticDto::getMin)
                            .sum();
                }
                case SUM_MAX: {
                    return argumentsStatistic.stream()
                            .mapToDouble(PetriElementStatisticDto::getMax)
                            .sum();
                }
                case SUM_AVG: {
                    return argumentsStatistic.stream()
                            .mapToDouble(PetriElementStatisticDto::getAvg)
                            .sum();
                }
                case AVG_MIN: {
                    return argumentsStatistic.stream()
                            .mapToDouble(PetriElementStatisticDto::getMin)
                            .average().orElse(0.0);
                }
                case AVG_MAX: {
                    return argumentsStatistic.stream()
                            .mapToDouble(PetriElementStatisticDto::getMax)
                            .average().orElse(0.0);
                }
                case AVG: {
                    return argumentsStatistic.stream()
                            .mapToDouble(PetriElementStatisticDto::getAvg)
                            .average().orElse(0.0);
                }
            }
        } else if (function.getArgumentType() == PetriStatisticFunction.FunctionArgumentType.SINGLE_ELEMENT_AND_NUMBER) {
            String[] args = argument.split(PetriStatisticFunction.FunctionArgumentType.SINGLE_ELEMENT_AND_NUMBER.getSeparator());
            Map.Entry<Integer, String> argumentEntry = getArgumentIdName(args[0]);
            double functionArgument = Double.parseDouble(args[1]);
            PetriElementStatisticDto argumentStatistic = statistics.stream()
                    .filter(petriElementStatistic -> petriElementStatistic.getElementName().equals(argumentEntry.getValue()) &&
                            petriElementStatistic.getPetriObjId().equals(argumentEntry.getKey()))
                    .findFirst()
                    .orElse(null);
            if (argumentStatistic == null) {
                return null;
            }
            switch (function) {
                case POWER_MIN: {
                    return Math.pow(argumentStatistic.getMin(), functionArgument);
                }
                case POWER_MAX: {
                    return Math.pow(argumentStatistic.getMax(), functionArgument);
                }
                case POWER_AVG: {
                    return Math.pow(argumentStatistic.getAvg(), functionArgument);
                }
            }
        }
        return null;
    }


    private List<String> getElementSuggestions(PetriStatisticFunction petriStatisticFunction, String argument) {
        if (petriStatisticFunction == null) {
            return new ArrayList<>();
        }

        List<String> elements = new ArrayList<>();
        List<String> placeNames = getCurrentGraphNet().getGraphPetriPlaceList().stream()
                .map(GraphPetriPlace::getName)
                .collect(Collectors.toList());
        List<String> transitionNames = getCurrentGraphNet().getGraphPetriTransitionList().stream()
                .map(GraphTransition::getName)
                .collect(Collectors.toList());
        if (petriStatisticFunction.getFunctionType().equals(PetriStatisticFunction.FunctionArgumentElementType.PLACE)) {
            if (argument != null) {
                placeNames = placeNames.stream()
                        .filter(name -> name.toUpperCase().startsWith(argument.toUpperCase()))
                        .collect(Collectors.toList());
            }
            elements.addAll(placeNames);
        } else if (petriStatisticFunction.getFunctionType().equals(PetriStatisticFunction.FunctionArgumentElementType.TRANSITION)) {
            if (argument != null) {
                transitionNames = transitionNames.stream()
                        .filter(name -> name.toUpperCase().startsWith(argument.toUpperCase()))
                        .collect(Collectors.toList());
            }
            elements.addAll(transitionNames);
        } else {
            elements.addAll(placeNames);
            elements.addAll(transitionNames);
        }
        return elements;
    }

    private Map.Entry<Integer, String> getArgumentIdName(String argument) {
        String argumentName;
        int petriObjId;
        Pattern pattern = Pattern.compile("(\\w+)\\[(\\d+)\\]");
        Matcher matcher = pattern.matcher(argument);
        if (matcher.matches()) {
            argumentName = matcher.group(1);
            petriObjId = Integer.parseInt(matcher.group(2));
        } else {
            petriObjId = 0;
            argumentName = argument;
        }
        return new AbstractMap.SimpleEntry<>(petriObjId, argumentName);
    }

    private String getFunctionArgument(String input) {
        int openParenIndex = input.indexOf('(');
        int closeParenIndex = input.lastIndexOf(')');
        if (openParenIndex > 0 && closeParenIndex > openParenIndex) {
            return input.substring(openParenIndex + 1, closeParenIndex).trim().toUpperCase();
        } else if (openParenIndex > 0) {
            return input.substring(openParenIndex + 1).trim().toUpperCase();
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

    private boolean isValidArgumentsElement(PetriStatisticFunction function, String argument) {
        String[] args = argument.split(function.getArgumentType().getSeparator());
        return Arrays.stream(args).allMatch(param -> isValidArgumentElement(function, param));
    }

    private boolean isValidArgumentsElementAndNumber(PetriStatisticFunction function, String argument) {
        String[] args = argument.split(PetriStatisticFunction.FunctionArgumentType.SINGLE_ELEMENT_AND_NUMBER.getSeparator());
        if (args.length != 2) {
            return false;
        }
        String elementName = args[0];
        String functionArgument = args[1];
        return isValidArgumentElement(function, elementName) && functionArgument.matches("\\d+(\\.\\d+)?");
    }

    private boolean isValidFunctionArgument(PetriStatisticFunction function, String argument) {
        switch (function.getArgumentType()) {
            case SINGLE_ELEMENT: {
                return isValidArgumentElement(function, argument);
            }
            case MULTIPLE_ELEMENT: {
                return isValidArgumentsElement(function, argument);
            }
            case SINGLE_ELEMENT_AND_NUMBER: {
                return isValidArgumentsElementAndNumber(function, argument);
            }
        }
        return false;
    }

    private boolean isValidArgumentElement(PetriStatisticFunction function, String argument) {
        if (argument == null || argument.isEmpty()) {
            return false;
        }
        GraphPetriNet net = getCurrentGraphNet();

        List<String> elements = new ArrayList<>();
        List<String> places = net.getGraphPetriPlaceList().stream()
                .map(place -> place.getName().toUpperCase())
                .collect(Collectors.toList());
        List<String> transitions = net.getGraphPetriTransitionList().stream()
                .map(transition -> transition.getName().toUpperCase())
                .collect(Collectors.toList());
        if (function.getFunctionType() == PetriStatisticFunction.FunctionArgumentElementType.PLACE) {
            elements.addAll(places);
        } else if (function.getFunctionType() == PetriStatisticFunction.FunctionArgumentElementType.TRANSITION) {
            elements.addAll(transitions);
        } else if (function.getFunctionType() == PetriStatisticFunction.FunctionArgumentElementType.ANY) {
            elements.addAll(places);
            elements.addAll(transitions);
        }
        return elements.contains(argument.toUpperCase());
    }

    private GraphPetriNet getCurrentGraphNet() {
        return petriNetParent != null ? petriNetParent.getPetriNetsPanel().getGraphNet() : null;
    }
}

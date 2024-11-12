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
    private static final Pattern ARGUMENT_WITH_ID_PATTERN = Pattern.compile("O(\\d+)\\.([A-Za-z\\u0400-\\u04FF]\\w*)");

    public FormulaBuilderServiceImpl(PetriNetsFrame parent) {
        this.petriNetParent = parent;
    }

    public FormulaBuilderServiceImpl() {

    }

    @Override
    public String updateFormula(String formula, String input) {
        List<String> operators = splitFormula(formula);

        String lastOperator = operators.get(operators.size() - 1);

        if (formula.endsWith(")")) {
            operators.add(input);
            return String.join("", operators);
        }
        if (OPERATORS.contains(lastOperator)) {
            operators.add(input + "(");
            return String.join("", operators);
        }

        StringBuilder operator = new StringBuilder();
        String lastFunctionName = getOperationFunctionName(lastOperator);
        if (lastFunctionName == null) {
            String resultFunctionName = lastOperator + input.substring(lastOperator.length());
            operator.append(resultFunctionName).append("(");
        } else {
            PetriStatisticFunction func = PetriStatisticFunction.findFunctionByName(lastFunctionName);
            if (func.getArgumentType() == PetriStatisticFunction.FunctionArgumentType.SINGLE_ELEMENT) {
                String functionArgument = Optional.ofNullable(getFunctionArgument(lastOperator)).orElse("");
                String resultArgumentName = functionArgument + input.substring(functionArgument.length());
                operator.append(func.getFunctionName()).append("(").append(resultArgumentName).append(")");
            } else if (func.getArgumentType() == PetriStatisticFunction.FunctionArgumentType.SINGLE_ELEMENT_AND_NUMBER) {
                String functionArgument = Optional.ofNullable(getFunctionArgument(lastOperator)).orElse("");
                String resultArgumentName = functionArgument + input.substring(functionArgument.length());
                operator.append(func.getFunctionName()).append("(").append(resultArgumentName).append(";");
            } else if (func.getArgumentType() == PetriStatisticFunction.FunctionArgumentType.MULTIPLE_ELEMENT) {
                String functionArgument = Optional.ofNullable(getFunctionArgument(lastOperator)).orElse("");
                if (functionArgument.isEmpty()) {
                    operator.append(func.getFunctionName()).append("(").append(input).append(";");
                } else if (!functionArgument.contains(";")) {
                    String resultArgumentName = functionArgument + input.substring(functionArgument.length());
                    operator.append(func.getFunctionName()).append("(").append(resultArgumentName).append(";");
                } else {
                    List<String> args = new ArrayList<>(Arrays.asList(functionArgument.split(";")));
                    int separatorIndex = functionArgument.lastIndexOf(";");
                    String lastArgument = separatorIndex != -1 && separatorIndex + 1 < input.length() ?
                            functionArgument.substring(separatorIndex + 1).trim()
                            : null;
                    if (lastArgument == null) {
                        args.add(input);
                    } else {
                        String resultArgumentName = lastArgument + input.substring(lastArgument.length());
                        args.set(args.size() - 1, resultArgumentName);
                    }
                    String allArgs = String.join(";", args);
                    operator.append(func.getFunctionName()).append("(").append(allArgs).append(";");
                }
            }
        }


        operators.set(operators.size() - 1, operator.toString());
        return String.join("", operators).toUpperCase();
    }

    @Override
    public List<String> getFormulaSuggestions(String formula) {
        if (formula == null || formula.isEmpty()) {
            return PetriStatisticFunction.getFunctionNames();
        }
        if (formula.endsWith(")")) {
            return OPERATORS;
        }

        List<String> suggestions = new ArrayList<>();
        List<String> operators = splitFormula(formula);

        String lastOperator = operators.get(operators.size() - 1);
        if (OPERATORS.contains(lastOperator)) {
            return PetriStatisticFunction.getFunctionNames();
        }

        String lastFunctionName = getOperationFunctionName(lastOperator);
        if (lastFunctionName == null) {
            return PetriStatisticFunction.filterFunctionsByName(lastOperator);
        } else {
            PetriStatisticFunction func = PetriStatisticFunction.findFunctionByName(lastFunctionName);
            if (func.getArgumentType() == PetriStatisticFunction.FunctionArgumentType.SINGLE_ELEMENT) {
                String functionArgument = Optional.ofNullable(getFunctionArgument(lastOperator)).orElse("");
                return getElementSuggestions(func, functionArgument);
            } else if (func.getArgumentType() == PetriStatisticFunction.FunctionArgumentType.SINGLE_ELEMENT_AND_NUMBER) {
                String functionArgument = Optional.ofNullable(getFunctionArgument(lastOperator)).orElse("");
                if (functionArgument.contains(";")) {
                    return new ArrayList<>();
                }
                return getElementSuggestions(func, functionArgument);
            } else if (func.getArgumentType() == PetriStatisticFunction.FunctionArgumentType.MULTIPLE_ELEMENT) {
                String functionArgument = Optional.ofNullable(getFunctionArgument(lastOperator)).orElse("");
                if (!functionArgument.contains(";")) {
                    return getElementSuggestions(func, functionArgument);
                } else {
                    int separatorIndex = functionArgument.lastIndexOf(";");
                    String lastArgument = separatorIndex != -1 ?
                            functionArgument.substring(separatorIndex + 1).trim()
                            : null;
                    return getElementSuggestions(func, lastArgument);
                }
            }
        }
        return suggestions;
    }

    @Override
    public Map<Integer, List<String>> getFormulaElementsWatchMap(String formula) {
        Map<Integer, List<String>> watchMap = new HashMap<>();
        Matcher functionMatcher = FUNCTION_CALL_PATTERN.matcher(formula);
        while (functionMatcher.find()) {
            String argument = functionMatcher.group(2).trim();
            if (argument.isEmpty()) {
                continue;
            }
            for (String arg : argument.split(";")) {
                Map.Entry<Integer, String> entry = getArgumentIdNameEntry(arg);
                watchMap.computeIfAbsent(entry.getKey(), k -> new ArrayList<>()).add(entry.getValue());
            }
        }
        return watchMap;
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

            if (argument.contains(".") && !argument.matches("^O\\d+\\." + ".*")) {
                return false;
            }

            if (petriNetParent != null) {
                if (!isValidFunctionArgument(function, argument)) {
                    return false;
                }
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
            Map.Entry<Integer, String> argumentEntry = getArgumentIdNameEntry(argument);
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
                    .map(this::getArgumentIdNameEntry)
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
            Map.Entry<Integer, String> argumentEntry = getArgumentIdNameEntry(args[0]);
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
        GraphPetriNet petriNet = getCurrentGraphNet();
        if (petriStatisticFunction == null || petriNet == null) {
            return new ArrayList<>();
        }

        List<String> elements = new ArrayList<>();
        List<String> placeNames = petriNet.getGraphPetriPlaceList().stream()
                .map(GraphPetriPlace::getName)
                .collect(Collectors.toList());
        List<String> transitionNames = petriNet.getGraphPetriTransitionList().stream()
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

    private Map.Entry<Integer, String> getArgumentIdNameEntry(String argument) {
        String argumentName;
        int petriObjId;
        Matcher matcher = ARGUMENT_WITH_ID_PATTERN.matcher(argument);
        if (matcher.matches()) {
            petriObjId = Integer.parseInt(matcher.group(1));
            argumentName = matcher.group(2);
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
        if (net == null) {
            return false;
        }
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

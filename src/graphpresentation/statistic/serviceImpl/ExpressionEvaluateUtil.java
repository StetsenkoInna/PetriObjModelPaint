package graphpresentation.statistic.serviceImpl;

import java.util.Stack;

public class ExpressionEvaluateUtil {
    public static Number evaluateExpression(String expression) {
        char[] tokens = expression.toCharArray();
        Stack<Double> values = new Stack<>();
        Stack<Character> operators = new Stack<>();

        for (int i = 0; i < tokens.length; i++) {
            if (tokens[i] == ' ')
                continue;
            if ((tokens[i] >= '0' && tokens[i] <= '9')
                    || tokens[i] == '.') {
                StringBuilder sb = new StringBuilder();
                while (i < tokens.length && (Character.isDigit(tokens[i]) || tokens[i] == '.')) {
                    sb.append(tokens[i]);
                    i++;
                }
                values.push(Double.parseDouble(sb.toString()));
                i--;
            } else if (tokens[i] == '(') {
                operators.push(tokens[i]);
            } else if (tokens[i] == ')') {
                while (operators.peek() != '(') {
                    values.push(applyOperator(
                            operators.pop(), values.pop(),
                            values.pop()));
                }
                operators.pop();
            } else if (tokens[i] == '+'
                    || tokens[i] == '-'
                    || tokens[i] == '*'
                    || tokens[i] == '/') {
                while (!operators.isEmpty()
                        && hasPrecedence(tokens[i],
                        operators.peek())) {
                    values.push(applyOperator(
                            operators.pop(), values.pop(),
                            values.pop()));
                }
                operators.push(tokens[i]);
            }
        }
        while (!operators.isEmpty()) {
            values.push(applyOperator(operators.pop(),
                    values.pop(),
                    values.pop()));
        }
        return values.pop();
    }

    private static boolean hasPrecedence(char operator1,
                                         char operator2) {
        if (operator2 == '(' || operator2 == ')') {
            return false;
        }
        return (operator1 != '*' && operator1 != '/') || (operator2 != '+' && operator2 != '-');
    }

    private static double applyOperator(char operator, double b, double a) {
        switch (operator) {
            case '+': {
                return a + b;
            }
            case '-': {
                return a - b;
            }
            case '*': {
                return a * b;
            }
            case '/': {
                if (b == 0)
                    throw new ArithmeticException("Cannot divide by zero");
                return a / b;
            }
        }
        return 0;
    }
}

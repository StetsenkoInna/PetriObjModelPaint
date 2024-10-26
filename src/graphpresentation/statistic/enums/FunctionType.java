package graphpresentation.statistic.enums;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public enum FunctionType {
    MIN("O_MIN", "Observed min MIN('P1')", "Display observed minimum for selected element"),
    MAX("O_MAX", "Observed max MAX('P1')", "Display observed maximum for selected element"),
    AVG("AVG", "Observed average AVG('P1')", "Display observed average for selected element");

    FunctionType(String functionName, String title, String description) {
        this.functionName = functionName;
        this.title = title;
        this.description = description;
    }


    private final String functionName;
    private final String title;
    private final String description;

    public String getFunctionName() {
        return functionName;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public static List<String> getFunctionNames() {
        return Arrays.stream(values())
                .map(FunctionType::getFunctionName)
                .collect(Collectors.toList());
    }

    public static FunctionType getFunctionByIndex(int index) {
        return values()[index];
    }
}

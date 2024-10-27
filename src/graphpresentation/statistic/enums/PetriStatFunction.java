package graphpresentation.statistic.enums;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public enum PetriStatFunction {
    P_MIN(
            "P_MIN",
            "Observed position min MIN('P1')",
            "Returns observed minimum for selected position",
            FunctionType.POSITION_BASED
    ),
    P_MAX(
            "P_MAX",
            "Observed position max MAX('P1')",
            "Returns observed maximum for selected position",
            FunctionType.POSITION_BASED
    ),
    P_AVG(
            "P_AVG",
            "Observed position average AVG('P1')",
            "Returns observed average for selected position",
            FunctionType.POSITION_BASED
    ),
    T_MIN(
            "T_MIN",
            "Observed transition min MIN('T1')",
            "Returns observed minimum for selected transition",
            FunctionType.TRANSITION_BASED
    ),
    T_MAX(
            "T_MAX",
            "Observed transition max MAX('T1')",
            "Returns observed maximum for selected transition",
            FunctionType.TRANSITION_BASED
    ),
    T_AVG(
            "T_AVG",
            "Observed transition average AVG('T1')",
            "Returns observed average for selected transition",
            FunctionType.TRANSITION_BASED
    );

    PetriStatFunction(String functionName, String title, String description, FunctionType functionType) {
        this.functionName = functionName;
        this.title = title;
        this.description = description;
        this.functionType = functionType;
    }


    private final String functionName;
    private final String title;
    private final String description;

    private final FunctionType functionType;

    public String getFunctionName() {
        return functionName;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public FunctionType getFunctionType() {
        return functionType;
    }

    public static List<String> getFunctionNames() {
        return Arrays.stream(values())
                .map(PetriStatFunction::getFunctionName)
                .collect(Collectors.toList());
    }

    public static PetriStatFunction getFunctionByIndex(int index) {
        return values()[index];
    }

    public static List<String> filterFunctionsByName(String functionName) {
        return Arrays.stream(values())
                .map(PetriStatFunction::getFunctionName)
                .filter(name -> name.toUpperCase().startsWith(functionName.toUpperCase()))
                .collect(Collectors.toList());
    }

    public static PetriStatFunction findFunctionByName(String functionName) {
        return Arrays.stream(values())
                .filter(func -> func.getFunctionName().equalsIgnoreCase(functionName))
                .findFirst().orElse(null);
    }
}

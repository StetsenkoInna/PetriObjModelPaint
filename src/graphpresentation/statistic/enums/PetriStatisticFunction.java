package graphpresentation.statistic.enums;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Enum which describes function that we could apply for petri net element statistic
 *
 * @author Andrii Kachmar
 */
public enum PetriStatisticFunction {
    P_MIN(
            "P_MIN",
            "Place observed min P_MIN(P1)",
            "Returns observed minimum for specified place",
            FunctionArgumentElementType.PLACE,
            FunctionArgumentType.SINGLE_ELEMENT
    ),
    P_MAX(
            "P_MAX",
            "Place observed max P_MAX(P1)",
            "Returns observed maximum for specified place",
            FunctionArgumentElementType.PLACE,
            FunctionArgumentType.SINGLE_ELEMENT
    ),
    P_AVG(
            "P_AVG",
            "Place observed average P_AVG(P1)",
            "Returns observed average for specified place",
            FunctionArgumentElementType.PLACE,
            FunctionArgumentType.SINGLE_ELEMENT
    ),
    T_MIN(
            "T_MIN",
            "Transition observed min T_MIN(T1)",
            "Returns observed minimum for selected transition",
            FunctionArgumentElementType.TRANSITION,
            FunctionArgumentType.SINGLE_ELEMENT
    ),
    T_MAX(
            "T_MAX",
            "Transition observed max T_MAX(T1)",
            "Returns observed maximum for selected transition",
            FunctionArgumentElementType.TRANSITION,
            FunctionArgumentType.SINGLE_ELEMENT
    ),
    T_AVG(
            "T_AVG",
            "Transition observed average T_AVG(T1)",
            "Returns observed average for selected transition",
            FunctionArgumentElementType.TRANSITION,
            FunctionArgumentType.SINGLE_ELEMENT
    ),
    SUM_MIN(
            "SUM_MIN",
            "Summary of min values SUM_MIN(P1; T1; ...)",
            "Returns sum of min value for all specified elements",
            FunctionArgumentElementType.ANY,
            FunctionArgumentType.MULTIPLE_ELEMENT
    ),
    SUM_MAX(
            "SUM_MAX",
            "Summary of max values SUM_MAX(P1; T1; ...)",
            "Returns sum of max value for all specified elements",
            FunctionArgumentElementType.ANY,
            FunctionArgumentType.MULTIPLE_ELEMENT
    ),
    SUM_AVG(
            "SUM_AVG",
            "Summary of avg values SUM_AVG(P1; T1; ...)",
            "Returns sum of avg value for all specified elements",
            FunctionArgumentElementType.ANY,
            FunctionArgumentType.MULTIPLE_ELEMENT
    ),
    AVG_MIN(
            "AVG_MIN",
            "Average of min values AVG_MIN(P1; T1; ...)",
            "Returns average of min value for all specified elements",
            FunctionArgumentElementType.ANY,
            FunctionArgumentType.MULTIPLE_ELEMENT
    ),
    AVG_MAX(
            "AVG_MAX",
            "Average of max values AVG_MAX(P1; T1; ...)",
            "Returns average of max value for all specified elements",
            FunctionArgumentElementType.ANY,
            FunctionArgumentType.MULTIPLE_ELEMENT
    ),
    AVG(
            "AVG",
            "Average of avg values AVG(P1; T1; ...)",
            "Returns average of avg value for all specified elements",
            FunctionArgumentElementType.ANY,
            FunctionArgumentType.MULTIPLE_ELEMENT
    ),
    POWER_MIN(
            "POWER_MIN",
            "Power of element min POWER_MIN(P1; 2)",
            "Returns a power for specified element min",
            FunctionArgumentElementType.ANY,
            FunctionArgumentType.SINGLE_ELEMENT_AND_NUMBER
    ),
    POWER_MAX(
            "POWER_MAX",
            "Power of element max POWER_MAX(P1; 2)",
            "Returns a power for specified element max",
            FunctionArgumentElementType.ANY,
            FunctionArgumentType.SINGLE_ELEMENT_AND_NUMBER
    ),
    POWER_AVG(
            "POWER_AVG",
            "Power of element avg POWER_AVG(P1; 2)",
            "Returns a power for specified element avg",
            FunctionArgumentElementType.ANY,
            FunctionArgumentType.SINGLE_ELEMENT_AND_NUMBER
    );


    PetriStatisticFunction(String functionName, String title, String description,
                           FunctionArgumentElementType elementType, FunctionArgumentType argumentType) {
        this.functionName = functionName;
        this.title = title;
        this.description = description;
        this.elementType = elementType;
        this.argumentType = argumentType;
    }


    private final String functionName;
    private final String title;
    private final String description;
    private final FunctionArgumentElementType elementType;
    private final FunctionArgumentType argumentType;

    public String getFunctionName() {
        return functionName;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public FunctionArgumentElementType getFunctionType() {
        return elementType;
    }

    public static List<String> getFunctionNames() {
        return Arrays.stream(values())
                .map(PetriStatisticFunction::getFunctionName)
                .collect(Collectors.toList());
    }

    public static PetriStatisticFunction getFunctionByIndex(int index) {
        return values()[index];
    }

    public static List<String> filterFunctionsByName(String functionName) {
        return Arrays.stream(values())
                .map(PetriStatisticFunction::getFunctionName)
                .filter(name -> name.toUpperCase().startsWith(functionName.toUpperCase()))
                .collect(Collectors.toList());
    }

    public static PetriStatisticFunction findFunctionByName(String functionName) {
        return Arrays.stream(values())
                .filter(func -> func.getFunctionName().equalsIgnoreCase(functionName))
                .findFirst().orElse(null);
    }

    public FunctionArgumentElementType getElementType() {
        return elementType;
    }

    public FunctionArgumentType getArgumentType() {
        return argumentType;
    }

    public boolean hasSeparator() {
        return !argumentType.getSeparator().isEmpty();
    }

    public enum FunctionArgumentElementType {
        PLACE,
        TRANSITION,
        ANY
    }

    public enum FunctionArgumentType {
        SINGLE_ELEMENT(""),
        MULTIPLE_ELEMENT(";"),
        SINGLE_ELEMENT_AND_NUMBER(";");

        private final String separator;

        FunctionArgumentType(String separator) {
            this.separator = separator;
        }

        public String getSeparator() {
            return separator;
        }
    }
}

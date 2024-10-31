package graphpresentation.statistic.dto.data;

import PetriObj.PetriNet;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class PetriElementStatisticDto {
    private final String elementName;
    private final Number min;
    private final Number max;
    private final Double avg;

    public PetriElementStatisticDto(String elementName, Number min, Number max, Double avg) {
        this.elementName = elementName;
        this.min = min;
        this.max = max;
        this.avg = avg;
    }

    public String getElementName() {
        return elementName;
    }

    public Number getMin() {
        return min;
    }

    public Number getMax() {
        return max;
    }

    public Double getAvg() {
        return avg;
    }

    @Override
    public String toString() {
        return "PetriElementStatistic{" +
                "elementName='" + elementName + '\'' +
                ", min=" + min +
                ", max=" + max +
                ", avg=" + avg +
                '}';
    }
}

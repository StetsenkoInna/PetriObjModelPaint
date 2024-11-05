package graphpresentation.statistic.dto.data;

import PetriObj.PetriNet;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public class PetriElementStatisticDto {
    private final Integer petriObjId;
    private final String elementName;
    private final Number min;
    private final Number max;
    private final Double avg;

    public PetriElementStatisticDto(Integer petriObjId, String elementName, Number min, Number max, Double avg) {
        this.petriObjId = petriObjId;
        this.elementName = elementName;
        this.min = min;
        this.max = max;
        this.avg = avg;
    }

    public String getElementName() {
        return elementName;
    }

    public Double getMin() {
        return min.doubleValue();
    }

    public Double getMax() {
        return max.doubleValue();
    }

    public Double getAvg() {
        return avg;
    }

    public Integer getPetriObjId() {
        return petriObjId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PetriElementStatisticDto that = (PetriElementStatisticDto) o;
        return Objects.equals(petriObjId, that.petriObjId) && Objects.equals(elementName, that.elementName) && Objects.equals(min, that.min) && Objects.equals(max, that.max) && Objects.equals(avg, that.avg);
    }

    @Override
    public int hashCode() {
        return Objects.hash(petriObjId, elementName, min, max, avg);
    }

    @Override
    public String toString() {
        return "PetriElementStatisticDto{" +
                "petriObjId=" + petriObjId +
                ", elementName='" + elementName + '\'' +
                ", min=" + min +
                ", max=" + max +
                ", avg=" + avg +
                '}';
    }
}

package graphpresentation.statistic.dto;

public class PetriElementStatisticDto {
    private final String elementName;
    private final Double currentTime;
    private final Number min;
    private final Number max;
    private final Double avg;

    public PetriElementStatisticDto(Double currentTime, String elementName, Number min, Number max, Double avg) {
        this.currentTime = currentTime;
        this.elementName = elementName;
        this.min = min;
        this.max = max;
        this.avg = avg;
    }

    public String getElementName() {
        return elementName;
    }

    public Double getCurrentTime() {
        return currentTime;
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
                ", currentTime=" + currentTime +
                ", min=" + min +
                ", max=" + max +
                ", avg=" + avg +
                '}';
    }
}

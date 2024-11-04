package graphpresentation.statistic.dto.configs;

import java.util.Objects;

public class ChartDataCollectionConfigDto {
    private Double dataUpdateFrequency;
    private Double dataCollectionStartTime;
    private Double dataCollectionStep;

    public ChartDataCollectionConfigDto() {
        this.dataUpdateFrequency = 1.0;
        this.dataCollectionStartTime = 0.0;
        this.dataCollectionStep = 1.0;
    }

    public ChartDataCollectionConfigDto(Double dataUpdateFrequency, Double dataCollectionStartTime, Double dataCollectionStep) {
        this.dataUpdateFrequency = dataUpdateFrequency;
        this.dataCollectionStartTime = dataCollectionStartTime;
        this.dataCollectionStep = dataCollectionStep;
    }

    public Double getDataUpdateFrequency() {
        return dataUpdateFrequency != null ? dataUpdateFrequency : 1;
    }

    public void setDataUpdateFrequency(String dataUpdateFrequency) {
        this.dataUpdateFrequency = Double.valueOf(dataUpdateFrequency);
    }

    public Double getDataCollectionStartTime() {
        return dataCollectionStartTime != null ? dataCollectionStartTime : 0;
    }

    public void setDataCollectionStartTime(String dataCollectionStartTime) {
        this.dataCollectionStartTime = Double.valueOf(dataCollectionStartTime);
    }

    public Double getDataCollectionStep() {
        return dataCollectionStep != null ? dataCollectionStep : 1;
    }

    public void setDataCollectionStep(String dataCollectionStep) {
        this.dataCollectionStep = Double.valueOf(dataCollectionStep);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ChartDataCollectionConfigDto that = (ChartDataCollectionConfigDto) o;
        return Objects.equals(dataUpdateFrequency, that.dataUpdateFrequency) && Objects.equals(dataCollectionStartTime, that.dataCollectionStartTime) && Objects.equals(dataCollectionStep, that.dataCollectionStep);
    }

    @Override
    public int hashCode() {
        return Objects.hash(dataUpdateFrequency, dataCollectionStartTime, dataCollectionStep);
    }

    @Override
    public String toString() {
        return "ChartDataCollectionConfigDto{" +
                "dataUpdateFrequency=" + dataUpdateFrequency +
                ", dataCollectionStartTime=" + dataCollectionStartTime +
                ", dataCollectionStep=" + dataCollectionStep +
                '}';
    }
}

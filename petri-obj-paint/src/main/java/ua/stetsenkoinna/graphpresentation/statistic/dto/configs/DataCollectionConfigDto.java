package ua.stetsenkoinna.graphpresentation.statistic.dto.configs;

import java.util.Objects;

public class DataCollectionConfigDto {
    private Double dataUpdateFrequency;
    private Double dataCollectionStartTime;
    private Double dataCollectionStep;
    private Integer numberOfRuns;

    public DataCollectionConfigDto() {
        this.dataUpdateFrequency = 1.0;
        this.dataCollectionStartTime = 0.0;
        this.dataCollectionStep = 1.0;
        this.numberOfRuns = 1;
    }

    public DataCollectionConfigDto(Double dataUpdateFrequency, Double dataCollectionStartTime,
                                   Double dataCollectionStep, Integer numberOfRuns) {
        this.dataUpdateFrequency = dataUpdateFrequency;
        this.dataCollectionStartTime = dataCollectionStartTime;
        this.dataCollectionStep = dataCollectionStep;
        this.numberOfRuns = numberOfRuns;
    }

    public Double getDataUpdateFrequency() {
        return dataUpdateFrequency != null ? dataUpdateFrequency : 1;
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

    public void setDataCollectionStartTime(Double dataCollectionStartTime) {
        this.dataCollectionStartTime = dataCollectionStartTime;
    }

    public void setDataCollectionStep(Double dataCollectionStep) {
        this.dataCollectionStep = dataCollectionStep;
    }

    public void setDataUpdateFrequency(Double dataUpdateFrequency) {
        this.dataUpdateFrequency = dataUpdateFrequency;
    }

    public Integer getNumberOfRuns() {
        return numberOfRuns != null ? numberOfRuns : 1;
    }

    public void setNumberOfRuns(String numberOfRuns) {
        this.numberOfRuns = Integer.valueOf(numberOfRuns);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        DataCollectionConfigDto that = (DataCollectionConfigDto) o;
        return Objects.equals(dataUpdateFrequency, that.dataUpdateFrequency) && Objects.equals(dataCollectionStartTime, that.dataCollectionStartTime) && Objects.equals(dataCollectionStep, that.dataCollectionStep) && Objects.equals(numberOfRuns, that.numberOfRuns);
    }

    @Override
    public int hashCode() {
        return Objects.hash(dataUpdateFrequency, dataCollectionStartTime, dataCollectionStep, numberOfRuns);
    }

    @Override
    public String toString() {
        return "DataCollectionConfigDto{" +
                "dataUpdateFrequency=" + dataUpdateFrequency +
                ", dataCollectionStartTime=" + dataCollectionStartTime +
                ", dataCollectionStep=" + dataCollectionStep +
                ", numberOfRuns=" + numberOfRuns +
                '}';
    }
}

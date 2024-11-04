package graphpresentation.statistic.events;

import graphpresentation.statistic.dto.data.PetriElementStatisticDto;

import java.util.List;
import java.util.Objects;

public class StatisticUpdateEvent {
    private double currentTime;
    private List<PetriElementStatisticDto> statistic;
    private boolean isTermination = false;

    public StatisticUpdateEvent(double currentTime, List<PetriElementStatisticDto> statistic) {
        this.currentTime = currentTime;
        this.statistic = statistic;
    }

    public StatisticUpdateEvent(boolean isTermination) {
        this.isTermination = isTermination;
    }

    public double getCurrentTime() {
        return currentTime;
    }

    public void setCurrentTime(double currentTime) {
        this.currentTime = currentTime;
    }

    public List<PetriElementStatisticDto> getStatistic() {
        return statistic;
    }

    public void setStatistic(List<PetriElementStatisticDto> statistic) {
        this.statistic = statistic;
    }

    public boolean isTermination() {
        return isTermination;
    }

    public void setTermination(boolean termination) {
        isTermination = termination;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        StatisticUpdateEvent event = (StatisticUpdateEvent) o;
        return Double.compare(currentTime, event.currentTime) == 0 && isTermination == event.isTermination && Objects.equals(statistic, event.statistic);
    }

    @Override
    public int hashCode() {
        return Objects.hash(currentTime, statistic, isTermination);
    }

    @Override
    public String toString() {
        return "StatisticUpdateEvent{" +
                "currentTime=" + currentTime +
                ", statistic=" + statistic +
                ", isTermination=" + isTermination +
                '}';
    }
}

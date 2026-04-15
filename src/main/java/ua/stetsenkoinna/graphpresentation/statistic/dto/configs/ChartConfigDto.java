package ua.stetsenkoinna.graphpresentation.statistic.dto.configs;

/** @deprecated Use {@link ua.stetsenkoinna.api.dto.ChartConfigDto} */
@Deprecated
public class ChartConfigDto extends ua.stetsenkoinna.api.dto.ChartConfigDto {
    public ChartConfigDto(String title, String xAxisTitle, String yAxisTitle, Boolean displayDataMarkers) {
        super(title, xAxisTitle, yAxisTitle, displayDataMarkers);
    }
}

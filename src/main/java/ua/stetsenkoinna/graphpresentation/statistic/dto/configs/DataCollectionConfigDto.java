package ua.stetsenkoinna.graphpresentation.statistic.dto.configs;

/** @deprecated Use {@link ua.stetsenkoinna.api.dto.DataCollectionConfigDto} */
@Deprecated
public class DataCollectionConfigDto extends ua.stetsenkoinna.api.dto.DataCollectionConfigDto {
    public DataCollectionConfigDto() { super(); }
    public DataCollectionConfigDto(Double freq, Double start, Double step, Integer runs) {
        super(freq, start, step, runs);
    }
}

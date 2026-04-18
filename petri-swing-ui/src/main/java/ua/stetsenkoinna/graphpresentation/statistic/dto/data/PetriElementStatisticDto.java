package ua.stetsenkoinna.graphpresentation.statistic.dto.data;

/** @deprecated Use {@link ua.stetsenkoinna.api.dto.PetriElementStatisticDto} */
@Deprecated
public class PetriElementStatisticDto extends ua.stetsenkoinna.api.dto.PetriElementStatisticDto {
    public PetriElementStatisticDto(Integer petriObjId, String elementName, Number min, Number max, Double avg) {
        super(petriObjId, elementName, min, max, avg);
    }
}

package textrepresentation;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import ua.stetsenkoinna.graphnet.GraphPetriNet;
import ua.stetsenkoinna.graphnet.GraphPetriPlace;
import ua.stetsenkoinna.graphnet.GraphPetriTransition;
import ua.stetsenkoinna.petritextrepr.dto.ArcImpl.ArcIn;
import ua.stetsenkoinna.petritextrepr.dto.ArcImpl.ArcOut;
import ua.stetsenkoinna.petritextrepr.dto.PetriNetDTO;
import ua.stetsenkoinna.petritextrepr.dto.Place;
import ua.stetsenkoinna.petritextrepr.dto.Transition;
import ua.stetsenkoinna.petritextrepr.pnmltransform.PetriNetEstablisher;

import static org.junit.jupiter.api.Assertions.*;


class PetriNetEstablisherTest {

    private PetriNetDTO sampleDto;

    @BeforeEach
    void setUp() {
        sampleDto = new PetriNetDTO();

        Place p1 = new Place();
        p1.setName("P1");
        p1.setTokens(1);

        Place p2 = new Place();
        p2.setName("P2");
        p2.setTokens(0);

        sampleDto.addPlace(p1);
        sampleDto.addPlace(p2);

        Transition t1 = new Transition(1);
        t1.setName("T1");
        t1.setTimeDelay(5.0);
        t1.setPriority(2);
        sampleDto.addTransition(t1);

        ArcIn arcIn = new ArcIn(1);
        arcIn.setArcIn(p1, t1);
        arcIn.setMultiplicity(1);
        sampleDto.addInputArc(arcIn);

        ArcOut arcOut = new ArcOut(2);
        arcOut.setArcOut(p2, t1);
        arcOut.setMultiplicity(1);
        sampleDto.addOutputArc(arcOut);
    }

    @Test
    @DisplayName("Тест: метод повертає не-null результат для коректного DTO")
    void testEstablishPetriNet_NonNullResult() {
        GraphPetriNet result = PetriNetEstablisher.establishPetriNetFromDto(sampleDto);
        assertNotNull(result, "Результат не повинен бути null");
        assertNotNull(result.getPetriNet(), "Внутрішня логічна мережа не повинна бути null");
    }

    @Test
    @DisplayName("Тест: коректна кількість елементів у створеній мережі")
    void testEstablishPetriNet_CorrectCounts() {
        GraphPetriNet result = PetriNetEstablisher.establishPetriNetFromDto(sampleDto);

        assertEquals(2, result.getGraphPetriPlaceList().size(), "Має бути 2 графічні позиції");
        assertEquals(1, result.getGraphPetriTransitionList().size(), "Має бути 1 графічний перехід");
        assertEquals(1, result.getGraphArcInList().size(), "Має бути 1 вхідна дуга");
        assertEquals(1, result.getGraphArcOutList().size(), "Має бути 1 вихідна дуга");
    }

    @Test
    @DisplayName("Тест: дані з DTO коректно перенесені в логічні об'єкти")
    void testEstablishPetriNet_DataTransferCorrectness() {
        GraphPetriNet result = PetriNetEstablisher.establishPetriNetFromDto(sampleDto);

        GraphPetriPlace gp1 = result.getGraphPetriPlaceList().stream()
                .filter(p -> "P1".equals(p.getPetriPlace().getName()))
                .findFirst()
                .orElse(null);
        assertNotNull(gp1);
        assertEquals(1, gp1.getPetriPlace().getMark(), "Кількість маркерів для P1 має бути 1");

        GraphPetriTransition gt1 = result.getGraphPetriTransitionList().get(0);
        assertEquals("T1", gt1.getPetriTransition().getName());
        assertEquals(5.0, gt1.getPetriTransition().getParameter());
        assertEquals(2, gt1.getPetriTransition().getPriority());
    }

    @Test
    @DisplayName("Тест: структурна цілісність дуг збережена")
    void testEstablishPetriNet_StructuralIntegrity() {
        GraphPetriNet result = PetriNetEstablisher.establishPetriNetFromDto(sampleDto);

        assertEquals(1, result.getGraphArcInList().size());
        var arcIn = result.getGraphArcInList().get(0);
        assertEquals("P1", ((GraphPetriPlace) arcIn.getBeginElement()).getPetriPlace().getName());
        assertEquals("T1", ((GraphPetriTransition) arcIn.getEndElement()).getPetriTransition().getName());
        assertEquals(1, arcIn.getArcIn().getQuantity());

        assertEquals(1, result.getGraphArcOutList().size());
        var arcOut = result.getGraphArcOutList().get(0);
        assertEquals("T1", ((GraphPetriTransition) arcOut.getBeginElement()).getPetriTransition().getName());
        assertEquals("P2", ((GraphPetriPlace) arcOut.getEndElement()).getPetriPlace().getName());
        assertEquals(1, arcOut.getArcOut().getQuantity());
    }

    @Test
    @DisplayName("Тест: координати графічних елементів були призначені")
    void testEstablishPetriNet_CoordinatesAreAssigned() {
        GraphPetriNet result = PetriNetEstablisher.establishPetriNetFromDto(sampleDto);

        GraphPetriPlace gp1 = result.getGraphPetriPlaceList().get(0);
        GraphPetriTransition gt1 = result.getGraphPetriTransitionList().get(0);

        boolean p1CoordsAssigned = gp1.getGraphElement().getX() != 0.0 || gp1.getGraphElement().getY() != 0.0;
        boolean t1CoordsAssigned = gt1.getGraphElement().getX() != 0.0 || gt1.getGraphElement().getY() != 0.0;

        assertTrue(p1CoordsAssigned, "Координати для GraphPetriPlace мають бути встановлені");
        assertTrue(t1CoordsAssigned, "Координати для GraphPetriTransition мають бути встановлені");
    }

    @Test
    @DisplayName("Тест: обробка порожнього DTO не викликає помилок")
    void testEstablishPetriNet_EmptyDto() {
        PetriNetDTO emptyDto = new PetriNetDTO();

        GraphPetriNet result = assertDoesNotThrow(() -> PetriNetEstablisher.establishPetriNetFromDto(emptyDto));

        assertNotNull(result);
        assertTrue(result.getGraphPetriPlaceList().isEmpty());
        assertTrue(result.getGraphPetriTransitionList().isEmpty());
        assertTrue(result.getGraphArcInList().isEmpty());
        assertTrue(result.getGraphArcOutList().isEmpty());
    }
}
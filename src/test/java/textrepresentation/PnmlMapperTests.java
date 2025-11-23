package textrepresentation;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Element;
import org.xml.sax.InputSource;
import ua.stetsenkoinna.petritextrepr.dto.Place;
import ua.stetsenkoinna.petritextrepr.dto.Transition;
import ua.stetsenkoinna.petritextrepr.dto.mathstats.DistributionLaws;
import ua.stetsenkoinna.petritextrepr.pnmltransform.PnmlMapper;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.StringReader;
import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;

public class PnmlMapperTests {

    private DocumentBuilder builder;

    private Element createElementFromString(String xml) throws Exception {
        InputSource is = new InputSource(new StringReader(xml));
        return builder.parse(is).getDocumentElement();
    }

    private <T> T invokePrivateStaticMethod(String methodName, Class<?>[] parameterTypes, Object[] args) throws Exception {
        Method method = PnmlMapper.class.getDeclaredMethod(methodName, parameterTypes);
        method.setAccessible(true);
        return (T) method.invoke(null, args);
    }

    @BeforeEach
    void setUp() throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        builder = factory.newDocumentBuilder();
    }

    @Test
    @DisplayName("Тест створення Place з коректним ім'ям та кількістю маркерів")
    void testBuildPlace_withNameAndMarking() throws Exception {
        String xml = "<place id='p1'>" +
                "  <name><text>Buffer</text></name>" +
                "  <initialMarking><text>5</text></initialMarking>" +
                "</place>";
        Element placeElement = createElementFromString(xml);

        Place result = invokePrivateStaticMethod("buildPlace", new Class[]{Element.class}, new Object[]{placeElement});

        assertNotNull(result);
        assertEquals("Buffer", result.getName());
        assertEquals(5, result.getTokens());
    }

    @Test
    @DisplayName("Тест створення Place без початкової розмітки (має бути 0)")
    void testBuildPlace_withNoMarking() throws Exception {
        String xml = "<place id='p2'>" +
                "  <name><text>Storage</text></name>" +
                "</place>";
        Element placeElement = createElementFromString(xml);

        Place result = invokePrivateStaticMethod("buildPlace", new Class[]{Element.class}, new Object[]{placeElement});

        assertNotNull(result);
        assertEquals("Storage", result.getName());
        assertEquals(0, result.getTokens(), "Кількість маркерів за замовчуванням має бути 0");
    }

    @Test
    @DisplayName("Тест створення Transition з усіма властивостями")
    void testBuildTransition_withAllProperties() throws Exception {
        String xml = "<transition id='t1'>" +
                "  <name><text>Process</text></name>" +
                "  <timeDelay><text>15.5</text></timeDelay>" +
                "  <priority><text>2</text></priority>" +
                "  <probability><text>0.8</text></probability>" +
                "  <distributionLaw><text>NORM</text></distributionLaw>" +
                "</transition>";
        Element transElement = createElementFromString(xml);

        Transition result = invokePrivateStaticMethod("buildTransition", new Class[]{Element.class, int.class}, new Object[]{transElement, 1});

        assertNotNull(result);
        assertEquals("Process", result.getName());
        assertEquals(1, result.getId());
        assertEquals(15.5, result.getTimeDelay());
        assertEquals(2, result.getPriority());
        assertEquals(0.8, result.getProbability());
        assertEquals(DistributionLaws.NORM, result.getDistributionLaw());
    }

    @Test
    @DisplayName("Тест створення Transition з некоректними числовими властивостями")
    void testBuildTransition_withInvalidProperties() throws Exception {
        String xml = "<transition id='t3'>" +
                "  <name><text>Failure</text></name>" +
                "  <timeDelay><text>invalid_delay</text></timeDelay>" +
                "  <priority><text>high</text></priority>" +
                "</transition>";
        Element transElement = createElementFromString(xml);

        Transition result = invokePrivateStaticMethod("buildTransition", new Class[]{Element.class, int.class}, new Object[]{transElement, 3});

        assertNotNull(result);
        assertEquals("Failure", result.getName());
        assertEquals(0.0, result.getTimeDelay(), "Часова затримка має бути 0.0 при помилці парсингу");
        assertEquals(1, result.getPriority(), "Пріоритет має бути 1 при помилці парсингу");
    }

    @Test
    @DisplayName("Тест парсингу кратності дуги зі значенням")
    void testParseArcMultiplicity_withValue() throws Exception {
        String xml = "<arc id='a1' source='p1' target='t1'>" +
                "  <inscription><text>3</text></inscription>" +
                "</arc>";
        Element arcElement = createElementFromString(xml);

        int result = invokePrivateStaticMethod("parseArcMultiplicity", new Class[]{Element.class}, new Object[]{arcElement});

        assertEquals(3, result);
    }

    @Test
    @DisplayName("Тест парсингу кратності дуги без значення (має бути 1)")
    void testParseArcMultiplicity_withNoValue() throws Exception {
        String xml = "<arc id='a2' source='p1' target='t1'></arc>";
        Element arcElement = createElementFromString(xml);

        int result = invokePrivateStaticMethod("parseArcMultiplicity", new Class[]{Element.class}, new Object[]{arcElement});

        assertEquals(1, result, "Кратність за замовчуванням має бути 1");
    }

    @Test
    @DisplayName("Тест визначення інгібіторної дуги за атрибутом 'type'")
    void testParseArcInhibitor_isTrueByType() throws Exception {
        String xml = "<arc id='a3' source='p1' target='t1' type='inhibitor'></arc>";
        Element arcElement = createElementFromString(xml);

        boolean result = invokePrivateStaticMethod("parseArcInhibitor", new Class[]{Element.class}, new Object[]{arcElement});

        assertTrue(result, "Дуга має бути визначена як інгібіторна");
    }

    @Test
    @DisplayName("Тест звичайної дуги (не інгібіторної)")
    void testParseArcInhibitor_isFalse() throws Exception {
        String xml = "<arc id='a4' source='p1' target='t1' type='normal'></arc>";
        Element arcElement = createElementFromString(xml);

        boolean result = invokePrivateStaticMethod("parseArcInhibitor", new Class[]{Element.class}, new Object[]{arcElement});

        assertFalse(result, "Дуга не має бути визначена як інгібіторна");
    }
}
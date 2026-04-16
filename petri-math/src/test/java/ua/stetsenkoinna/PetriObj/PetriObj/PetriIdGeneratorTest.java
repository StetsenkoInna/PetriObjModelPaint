package ua.stetsenkoinna.PetriObj;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Test for PetriIdGenerator
 */
public class PetriIdGeneratorTest {

    @Before
    public void setUp() {
        PetriIdGenerator.reset();
    }

    @After
    public void tearDown() {
        PetriIdGenerator.reset();
    }

    @Test
    public void testGeneratePlaceId() {
        String id1 = PetriIdGenerator.generatePlaceId("Buffer");
        String id2 = PetriIdGenerator.generatePlaceId("Buffer");

        // Should start with prefix
        assertTrue(id1.startsWith("p-"));

        // Should contain sanitized name
        assertTrue(id1.contains("buffer"));

        // Should be unique
        assertNotEquals(id1, id2);
    }

    @Test
    public void testGenerateTransitionId() {
        String id1 = PetriIdGenerator.generateTransitionId("Process Request");
        String id2 = PetriIdGenerator.generateTransitionId("Process Request");

        // Should start with prefix
        assertTrue(id1.startsWith("t-"));

        // Should contain sanitized name (spaces replaced with hyphens)
        assertTrue(id1.contains("process-request"));

        // Should be unique
        assertNotEquals(id1, id2);
    }

    @Test
    public void testGenerateArcId() {
        String id1 = PetriIdGenerator.generateArcId();
        String id2 = PetriIdGenerator.generateArcId();

        // Should start with prefix
        assertTrue(id1.startsWith("arc-"));

        // Should be unique
        assertNotEquals(id1, id2);
    }

    @Test
    public void testRegisterExistingId() {
        String existingId = "p-custom-12345678";

        // Should register successfully
        assertTrue(PetriIdGenerator.registerExistingId(existingId));

        // Should be marked as used
        assertTrue(PetriIdGenerator.isIdUsed(existingId));

        // Should not register twice
        assertFalse(PetriIdGenerator.registerExistingId(existingId));
    }

    @Test
    public void testSanitizeName() {
        // Special characters removed, spaces to hyphens
        String id1 = PetriIdGenerator.generatePlaceId("Test@Place#123");
        assertTrue(id1.contains("testplace123"));

        // Multiple spaces collapsed
        String id2 = PetriIdGenerator.generatePlaceId("Test   Space");
        assertTrue(id2.contains("test-space"));

        // Long names truncated
        String longName = "VeryLongPlaceNameThatExceedsTheLimit";
        String id3 = PetriIdGenerator.generatePlaceId(longName);
        assertTrue(id3.length() < longName.length() + 20); // prefix + uuid
    }

    @Test
    public void testDomainModelIntegration() {
        // Test that domain models use ID generator
        PetriP place = new PetriP("TestPlace", 5);
        assertNotNull(place.getId());
        assertTrue(place.getId().startsWith("p-"));
        assertTrue(place.getId().contains("testplace"));

        PetriT transition = new PetriT("TestTransition", 1.0);
        assertNotNull(transition.getId());
        assertTrue(transition.getId().startsWith("t-"));
        assertTrue(transition.getId().contains("testtransition"));

        ArcIn arcIn = new ArcIn(place, transition);
        assertNotNull(arcIn.getId());
        assertTrue(arcIn.getId().startsWith("arc-"));

        ArcOut arcOut = new ArcOut(transition, place, 1);
        assertNotNull(arcOut.getId());
        assertTrue(arcOut.getId().startsWith("arc-"));
    }

    @Test
    public void testIdUniqueness() {
        // Generate many IDs and ensure all are unique
        int count = 100;
        java.util.Set<String> ids = new java.util.HashSet<>();

        for (int i = 0; i < count; i++) {
            ids.add(PetriIdGenerator.generatePlaceId("Place"));
            ids.add(PetriIdGenerator.generateTransitionId("Transition"));
            ids.add(PetriIdGenerator.generateArcId());
        }

        // All IDs should be unique
        assertEquals(count * 3, ids.size());
    }

    @Test
    public void testReset() {
        String id1 = PetriIdGenerator.generatePlaceId("Test");
        assertEquals(1, PetriIdGenerator.getUsedIdCount());

        PetriIdGenerator.reset();
        assertEquals(0, PetriIdGenerator.getUsedIdCount());

        // After reset, same ID could be generated again
        assertFalse(PetriIdGenerator.isIdUsed(id1));
    }
}

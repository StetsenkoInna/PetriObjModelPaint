package ua.stetsenkoinna.PetriObj;

import java.io.Serializable;
import java.util.Objects;

/**
 * Wrapper class for Petri net element IDs.
 * Provides type safety and ensures all IDs are properly generated and validated.
 *
 * @author PetriObjPaint Team
 */
public class PetriElementId implements Serializable {

    private final String value;

    /**
     * Private constructor - use factory methods
     */
    private PetriElementId(String value) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException("ID cannot be null or empty");
        }
        this.value = value;
    }

    /**
     * Create ID for a place (auto-generated)
     *
     * @param name Place name (optional)
     * @return New place ID
     */
    public static PetriElementId forPlace(String name) {
        return new PetriElementId(PetriIdGenerator.generatePlaceId(name));
    }

    /**
     * Create ID for a transition (auto-generated)
     *
     * @param name Transition name (optional)
     * @return New transition ID
     */
    public static PetriElementId forTransition(String name) {
        return new PetriElementId(PetriIdGenerator.generateTransitionId(name));
    }

    /**
     * Create ID for an arc (auto-generated)
     *
     * @return New arc ID
     */
    public static PetriElementId forArc() {
        return new PetriElementId(PetriIdGenerator.generateArcId());
    }

    /**
     * Create ID from existing string (e.g., when loading from file)
     *
     * @param id Existing ID string
     * @return ID wrapper
     */
    public static PetriElementId fromString(String id) {
        if (id != null && !id.trim().isEmpty()) {
            PetriIdGenerator.registerExistingId(id);
        }
        return new PetriElementId(id);
    }

    /**
     * Get the string value of this ID
     *
     * @return ID string
     */
    public String getValue() {
        return value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PetriElementId that = (PetriElementId) o;
        return Objects.equals(value, that.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(value);
    }

    @Override
    public String toString() {
        return value;
    }
}

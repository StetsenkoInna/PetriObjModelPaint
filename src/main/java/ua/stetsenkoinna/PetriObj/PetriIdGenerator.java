package ua.stetsenkoinna.PetriObj;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Generator for unique human-readable IDs for Petri net elements.
 * Follows PNML standards (ISO/IEC 15909) and XML NCName rules.
 * ID format: {prefix}-{sanitized_name}-{short_uuid}
 * Example: p-buffer-a3f2, t-process-b9e1, arc-4d7c
 *
 * @author PetriObjPaint Team
 */
public class PetriIdGenerator {

    private static final Set<String> usedIds = new HashSet<>();

    // Prefixes for different element types
    private static final String PLACE_PREFIX = "p";
    private static final String TRANSITION_PREFIX = "t";
    private static final String ARC_PREFIX = "arc";

    /**
     * Generate unique ID for a place
     *
     * @param name Place name (optional, can be null)
     * @return Unique ID in format: p-{name}-{uuid} or p-{uuid}
     */
    public static String generatePlaceId(String name) {
        return generateId(PLACE_PREFIX, name);
    }

    /**
     * Generate unique ID for a transition
     *
     * @param name Transition name (optional, can be null)
     * @return Unique ID in format: t-{name}-{uuid} or t-{uuid}
     */
    public static String generateTransitionId(String name) {
        return generateId(TRANSITION_PREFIX, name);
    }

    /**
     * Generate unique ID for an arc
     *
     * @return Unique ID in format: arc-{uuid}
     */
    public static String generateArcId() {
        return generateId(ARC_PREFIX, null);
    }

    /**
     * Generate unique ID with prefix and optional name
     *
     * @param prefix Element type prefix (p, t, arc)
     * @param name Element name (optional)
     * @return Unique human-readable ID
     */
    private static String generateId(String prefix, String name) {
        String id;
        int maxAttempts = 100;
        int attempt = 0;

        do {
            StringBuilder sb = new StringBuilder(prefix);

            // Add sanitized name if provided
            if (name != null && !name.trim().isEmpty()) {
                String sanitized = sanitizeName(name);
                if (!sanitized.isEmpty()) {
                    sb.append("-").append(sanitized);
                }
            }

            // Add short UUID suffix for uniqueness
            sb.append("-").append(generateShortUuid());

            id = sb.toString();
            attempt++;

            // Safety check to prevent infinite loop
            if (attempt >= maxAttempts) {
                throw new RuntimeException("Failed to generate unique ID after " + maxAttempts + " attempts");
            }

        } while (usedIds.contains(id));

        usedIds.add(id);
        return id;
    }

    /**
     * Sanitize element name for use in ID
     * - Convert to lowercase
     * - Replace spaces and special chars with hyphens
     * - Remove invalid XML NCName characters
     * - Limit length to 20 characters
     *
     * @param name Original name
     * @return Sanitized name safe for XML IDs
     */
    private static String sanitizeName(String name) {
        if (name == null) {
            return "";
        }

        String sanitized = name.toLowerCase()
            .replaceAll("[\\s_]+", "-")  // spaces/underscores to hyphens
            .replaceAll("[^a-z0-9-]", "") // remove invalid chars
            .replaceAll("-+", "-")        // collapse multiple hyphens
            .replaceAll("^-|-$", "");     // trim hyphens

        // Limit length
        if (sanitized.length() > 20) {
            sanitized = sanitized.substring(0, 20);
        }

        return sanitized;
    }

    /**
     * Generate short UUID (8 characters) for ID suffix
     *
     * @return 8-character hex string
     */
    private static String generateShortUuid() {
        String uuid = UUID.randomUUID().toString().replace("-", "");
        return uuid.substring(0, 8);
    }

    /**
     * Check if an ID is already registered
     *
     * @param id ID to check
     * @return true if ID exists
     */
    public static boolean isIdUsed(String id) {
        return usedIds.contains(id);
    }

    /**
     * Register an existing ID (e.g., when loading from file)
     *
     * @param id ID to register
     * @return true if registered successfully, false if already exists
     */
    public static boolean registerExistingId(String id) {
        if (id == null || id.isEmpty()) {
            return false;
        }
        return usedIds.add(id);
    }

    /**
     * Clear all registered IDs (useful for testing or when starting fresh)
     */
    public static void reset() {
        usedIds.clear();
    }

    /**
     * Get count of registered IDs
     *
     * @return Number of IDs in use
     */
    public static int getUsedIdCount() {
        return usedIds.size();
    }
}

package ua.stetsenkoinna.graphnet;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Central counter for unique graph element IDs.
 * Extracted from PetriNetsPanel so the graphnet model layer
 * does not depend on the UI presentation layer.
 */
public final class GraphElementIdGenerator {

    private static final AtomicInteger counter = new AtomicInteger(0);

    private GraphElementIdGenerator() {}

    /** Returns the next unique element ID and increments the counter. */
    public static int next() {
        return counter.getAndIncrement();
    }

    /** Resets the counter (e.g. when loading a new net). */
    public static void reset() {
        counter.set(0);
    }

    /**
     * Ensures the counter is at least {@code minValue}.
     * Used after loading a saved net so new IDs don't collide with existing ones.
     */
    public static void ensureAtLeast(int minValue) {
        counter.updateAndGet(current -> Math.max(current, minValue));
    }
}

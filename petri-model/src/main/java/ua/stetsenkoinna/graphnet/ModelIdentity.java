package ua.stetsenkoinna.graphnet;

import java.util.UUID;

/** Persistent identifiers for object metadata, separate from PNML element numbering. */
public final class ModelIdentity {
    private ModelIdentity() { }

    public static String create(String prefix) {
        return prefix + UUID.randomUUID().toString().replace("-", "");
    }

    public static boolean isValid(String value) {
        return value != null && value.matches("[A-Za-z_][A-Za-z0-9_.-]*");
    }

    public static String requireValid(String value) {
        if (!isValid(value)) throw new IllegalArgumentException("Invalid model identifier: " + value);
        return value;
    }
}

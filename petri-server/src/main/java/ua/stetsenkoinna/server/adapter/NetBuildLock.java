package ua.stetsenkoinna.server.adapter;

/** Shared mutex for PetriP/PetriT static counter reset + net construction. */
public final class NetBuildLock {
    public static final Object LOCK = new Object();
    private NetBuildLock() {}
}

package ua.stetsenkoinna.server.controller;

/**
 * Builds the STOMP destinations a simulation publishes to, so the broker-prefixed
 * topic strings are defined in one place instead of being concatenated inline.
 */
public final class WsDestinations {

    private static final String BROKER_PREFIX = "/topic";

    private WsDestinations() {}

    /** Per-step statistics topic for a session: {@code /topic/v1/sim/{id}/steps}. */
    public static String steps(String sessionId) {
        return steps(ApiVersions.WS_V1, sessionId);
    }

    /** Status-change topic for a session: {@code /topic/v1/sim/{id}/status}. */
    public static String status(String sessionId) {
        return status(ApiVersions.WS_V1, sessionId);
    }

    /**
     * Per-step statistics topic of a given API version.
     *
     * @param apiVersion one of {@link ApiVersions#WS_V1}, {@link ApiVersions#WS_V2}
     * @param sessionId the simulation session
     */
    public static String steps(String apiVersion, String sessionId) {
        return BROKER_PREFIX + apiVersion + "/sim/" + sessionId + "/steps";
    }

    /**
     * Status-change topic of a given API version.
     *
     * @param apiVersion one of {@link ApiVersions#WS_V1}, {@link ApiVersions#WS_V2}
     * @param sessionId the simulation session
     */
    public static String status(String apiVersion, String sessionId) {
        return BROKER_PREFIX + apiVersion + "/sim/" + sessionId + "/status";
    }
}

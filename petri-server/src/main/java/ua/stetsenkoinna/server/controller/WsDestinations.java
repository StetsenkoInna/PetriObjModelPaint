package ua.stetsenkoinna.server.controller;

/**
 * Builds the STOMP destinations a simulation publishes to, so the broker-prefixed
 * topic strings are defined in one place instead of being concatenated inline.
 */
public final class WsDestinations {

    private static final String TOPIC_PREFIX = "/topic" + ApiVersions.WS_V1 + "/sim/";

    private WsDestinations() {}

    /** Per-step statistics topic for a session: {@code /topic/v1/sim/{id}/steps}. */
    public static String steps(String sessionId) {
        return TOPIC_PREFIX + sessionId + "/steps";
    }

    /** Status-change topic for a session: {@code /topic/v1/sim/{id}/status}. */
    public static String status(String sessionId) {
        return TOPIC_PREFIX + sessionId + "/status";
    }
}

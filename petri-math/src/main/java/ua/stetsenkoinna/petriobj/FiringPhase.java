package ua.stetsenkoinna.petriobj;

/**
 * One atomic animation step of a transition firing.
 *
 * <p>A firing is not instantaneous for anyone watching it: tokens leave the input places,
 * the transition holds them for its service time, and only then do they appear in the output
 * places. Splitting the firing into these four phases is what lets a client highlight each
 * part of the net at the moment it actually takes part, instead of jumping straight from one
 * marking to the next.
 *
 * <ul>
 *   <li>{@link #BEFORE_ACT_IN} — input places and input arcs are the ones highlighted;
 *       the marking is still the one from <em>before</em> the tokens are consumed</li>
 *   <li>{@link #AFTER_ACT_IN} — the transition itself is highlighted; the tokens are gone
 *       from the input places and the transition's buffer has grown by one</li>
 *   <li>{@link #BEFORE_ACT_OUT} — the transition and its output arcs are highlighted; the
 *       marking is still the one left by {@link #AFTER_ACT_IN}</li>
 *   <li>{@link #AFTER_ACT_OUT} — output places are highlighted; the buffer has shrunk by one
 *       and the produced tokens have arrived</li>
 * </ul>
 */
public enum FiringPhase {
    BEFORE_ACT_IN("before_act_in"),
    AFTER_ACT_IN("after_act_in"),
    BEFORE_ACT_OUT("before_act_out"),
    AFTER_ACT_OUT("after_act_out");

    private final String wireName;

    FiringPhase(String wireName) {
        this.wireName = wireName;
    }

    /**
     * The exact snake_case token the streaming contract carries. It lives here rather than in
     * the server module so no transport can invent a spelling of its own.
     *
     * @return the phase name as it appears in a streamed frame
     */
    public String wireName() {
        return wireName;
    }
}

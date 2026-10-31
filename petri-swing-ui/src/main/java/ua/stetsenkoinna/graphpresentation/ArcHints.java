package ua.stetsenkoinna.graphpresentation;

import ua.stetsenkoinna.graphnet.GraphArc;
import ua.stetsenkoinna.graphnet.GraphArcIn;

/**
 * The hover hints of the connections on the canvas: what kind of connection it is and, in one
 * line, what it does. The canvas is also a teaching tool, and the line styles alone do not
 * explain themselves to someone meeting Petri-objects for the first time.
 *
 * <p>The web editor shows the same hints; keep the two worded alike.
 */
final class ArcHints {

    static final String INPUT = hint("Input arc",
            "The transition needs tokens in this place to fire and takes them when it fires.");
    static final String OUTPUT = hint("Output arc",
            "When the transition fires, it puts tokens into this place.");
    static final String INFORMATIONAL = hint("Informational arc",
            "The transition needs tokens in this place to fire but does not take them.");
    static final String SHARED_PLACE = hint("Shared place link",
            "Both places are one place in different Petri-objects; the place at the arrowhead"
                    + " repeats the other one (In := Out).");

    private ArcHints() {
    }

    /**
     * @param arc an arc on the canvas
     * @return its hint, by direction and kind
     */
    static String forArc(GraphArc arc) {
        if (arc instanceof GraphArcIn) {
            return arc.getIsInf() ? INFORMATIONAL : INPUT;
        }
        return OUTPUT;
    }

    private static String hint(String kind, String purpose) {
        // HTML so the purpose wraps under a bold name instead of running off as one long line.
        return "<html><b>" + kind + "</b><br><div style='width:260px'>" + purpose + "</div></html>";
    }
}

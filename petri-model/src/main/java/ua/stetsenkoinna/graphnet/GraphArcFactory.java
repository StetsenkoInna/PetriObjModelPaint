package ua.stetsenkoinna.graphnet;

import ua.stetsenkoinna.petriobj.ArcIn;
import ua.stetsenkoinna.petriobj.ArcOut;

/**
 * Builds drawn arcs between two graph elements, wired and positioned.
 *
 * <p>Used where an arc is created by something other than the mouse — restoring a link of a
 * loaded Petri-object model, or duplicating an object together with its net.
 */
public final class GraphArcFactory {

    private GraphArcFactory() {}

    /**
     * @param place the place the arc starts at
     * @param transition the transition it ends at
     * @param quantity arc multiplicity
     * @param informational true for a test arc that does not consume tokens
     * @return the drawn arc, already attached to both elements
     */
    public static GraphArcIn inArc(GraphPetriPlace place, GraphPetriTransition transition,
                                   int quantity, boolean informational) {
        ArcIn arc = new ArcIn(place.getPetriPlace(), transition.getPetriTransition(),
                Math.max(1, quantity), informational);
        GraphArcIn graphArc = new GraphArcIn(arc);
        attach(graphArc, place, transition);
        return graphArc;
    }

    /**
     * @param transition the transition the arc starts at
     * @param place the place it ends at
     * @param quantity arc multiplicity
     * @return the drawn arc, already attached to both elements
     */
    public static GraphArcOut outArc(GraphPetriTransition transition, GraphPetriPlace place,
                                     int quantity) {
        ArcOut arc = new ArcOut(transition.getPetriTransition(), place.getPetriPlace(),
                Math.max(1, quantity));
        GraphArcOut graphArc = new GraphArcOut(arc);
        attach(graphArc, transition, place);
        return graphArc;
    }

    private static void attach(GraphArc arc, GraphElement begin, GraphElement end) {
        arc.settingNewArc(begin);
        arc.setEndElement(end);
        arc.setPetriElements();
        arc.changeBorder();
        arc.updateCoordinates();
    }
}

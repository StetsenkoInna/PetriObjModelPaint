package ua.stetsenkoinna.graphnet;

import ua.stetsenkoinna.petriobj.ArcIn;
import ua.stetsenkoinna.petriobj.ArcOut;
import ua.stetsenkoinna.petriobj.PetriNet;
import ua.stetsenkoinna.petriobj.PetriP;
import ua.stetsenkoinna.petriobj.PetriT;
import ua.stetsenkoinna.pnml.CoordinateNormalizer;

import java.awt.Point;
import java.awt.geom.Point2D;
import java.util.Collections;
import java.util.Map;

/**
 * Builds a drawable {@link GraphPetriNet} from a plain {@link PetriNet} and the coordinates
 * that came with it.
 *
 * <p>Used whenever a net arrives without graphics of its own — imported from PNML, dropped
 * on the canvas, or instantiated from a net library template.
 *
 * <p>Elements whose coordinates are missing are laid out on a fallback grid so that a net
 * is always visible and draggable rather than piled up in one point.
 */
public final class GraphNetBuilder {

    /** Horizontal step of the fallback layout, in canvas units. */
    private static final int FALLBACK_STEP = 100;
    /** Row the fallback layout puts places on. */
    private static final int FALLBACK_PLACE_ROW = 100;
    /** Row the fallback layout puts transitions on. */
    private static final int FALLBACK_TRANSITION_ROW = 200;

    private GraphNetBuilder() {}

    /**
     * Builds a graph net keeping the shape described by the given coordinates.
     *
     * @param net the net to draw
     * @param placeCoordinates place number to position, may be empty
     * @param transitionCoordinates transition number to position, may be empty
     * @param location where to move the finished net's centre, or {@code null} to leave it
     *        where the coordinates put it
     * @return a graph net whose elements and arcs are wired to {@code net}
     */
    public static GraphPetriNet build(PetriNet net,
                                      Map<Integer, Point2D.Double> placeCoordinates,
                                      Map<Integer, Point2D.Double> transitionCoordinates,
                                      Point location) {
        CoordinateNormalizer.NormalizationResult normalized = CoordinateNormalizer.normalize(
                placeCoordinates == null ? Collections.emptyMap() : placeCoordinates,
                transitionCoordinates == null ? Collections.emptyMap() : transitionCoordinates);

        GraphPetriNet graphNet = new GraphPetriNet();
        graphNet.setPetriNet(net);

        int fallbackIndex = 0;
        for (PetriP place : net.getListP()) {
            GraphPetriPlace graphPlace = new GraphPetriPlace(place, GraphElementIdGenerator.next());
            Point2D.Double position = normalized.normalizedPlaceCoordinates.get(place.getNumber());
            graphPlace.setNewCoordinates(position != null
                    ? new Point2D.Double(position.x, position.y)
                    : new Point2D.Double(FALLBACK_PLACE_ROW + fallbackIndex * FALLBACK_STEP, FALLBACK_PLACE_ROW));
            graphNet.getGraphPetriPlaceList().add(graphPlace);
            fallbackIndex++;
        }

        fallbackIndex = 0;
        for (PetriT transition : net.getListT()) {
            GraphPetriTransition graphTransition =
                    new GraphPetriTransition(transition, GraphElementIdGenerator.next());
            Point2D.Double position = normalized.normalizedTransitionCoordinates.get(transition.getNumber());
            graphTransition.setNewCoordinates(position != null
                    ? new Point2D.Double(position.x, position.y)
                    : new Point2D.Double(FALLBACK_PLACE_ROW + fallbackIndex * FALLBACK_STEP, FALLBACK_TRANSITION_ROW));
            graphNet.getGraphPetriTransitionList().add(graphTransition);
            fallbackIndex++;
        }

        for (ArcIn arcIn : net.getArcIn()) {
            GraphPetriPlace begin = findPlace(graphNet, arcIn.getNumP());
            GraphPetriTransition end = findTransition(graphNet, arcIn.getNumT());
            if (begin == null || end == null) {
                continue;
            }
            GraphArcIn graphArc = new GraphArcIn(arcIn);
            graphArc.settingNewArc(begin);
            graphArc.setEndElement(end);
            graphArc.setPetriElements();
            graphArc.updateCoordinates();
            graphNet.getGraphArcInList().add(graphArc);
        }

        for (ArcOut arcOut : net.getArcOut()) {
            GraphPetriTransition begin = findTransition(graphNet, arcOut.getNumT());
            GraphPetriPlace end = findPlace(graphNet, arcOut.getNumP());
            if (begin == null || end == null) {
                continue;
            }
            GraphArcOut graphArc = new GraphArcOut(arcOut);
            graphArc.settingNewArc(begin);
            graphArc.setEndElement(end);
            graphArc.setPetriElements();
            graphArc.updateCoordinates();
            graphNet.getGraphArcOutList().add(graphArc);
        }

        if (location != null) {
            graphNet.changeLocation(location);
        }
        graphNet.fixOverlappingArcs();
        return graphNet;
    }

    private static GraphPetriPlace findPlace(GraphPetriNet graphNet, int number) {
        for (GraphPetriPlace place : graphNet.getGraphPetriPlaceList()) {
            if (place.getPetriPlace().getNumber() == number) {
                return place;
            }
        }
        return null;
    }

    private static GraphPetriTransition findTransition(GraphPetriNet graphNet, int number) {
        for (GraphPetriTransition transition : graphNet.getGraphPetriTransitionList()) {
            if (transition.getPetriTransition().getNumber() == number) {
                return transition;
            }
        }
        return null;
    }
}

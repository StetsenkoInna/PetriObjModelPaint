package ua.stetsenkoinna.graphpresentation.undoable_edits;

import java.awt.Rectangle;
import java.awt.geom.Point2D;
import java.util.IdentityHashMap;
import java.util.Map;

import ua.stetsenkoinna.graphnet.GraphCanvasModel;
import ua.stetsenkoinna.graphnet.GraphElement;
import ua.stetsenkoinna.graphnet.GraphObjectFrame;
import ua.stetsenkoinna.graphnet.GraphPetriPlace;
import ua.stetsenkoinna.graphnet.GraphPetriTransition;

/**
 * Where everything on a canvas sits, and what holds it: each element's centre, each
 * Petri-object's rectangle, which object claims each element and which object encloses each
 * object.
 *
 * <p>Taken whole rather than per dragged item on purpose. A drag reaches much further than what
 * the pointer grabbed - moving an object carries its members and its nested objects, a drop can
 * change which object claims an element or which object encloses another, and an object that
 * grew to keep its margin around a child moved nothing the user touched. Recording the canvas
 * either side of the gesture captures all of that without {@link MoveCanvasItemsEdit} needing to
 * know which of those things happened, and it is the same handful of numbers per element either
 * way.
 *
 * <p>Positions only. What a drag never does is create or delete anything, so nothing here has to
 * account for an element that exists on one side of the gesture and not the other; an instance
 * that has since left the canvas is simply skipped when applying.
 */
public final class CanvasLayoutSnapshot {

    private final Map<GraphElement, Point2D> elementCentres = new IdentityHashMap<>();
    private final Map<GraphObjectFrame, Rectangle> frameBounds = new IdentityHashMap<>();
    private final Map<GraphElement, GraphObjectFrame> owners = new IdentityHashMap<>();
    private final Map<GraphObjectFrame, GraphObjectFrame> enclosing = new IdentityHashMap<>();

    public CanvasLayoutSnapshot(GraphCanvasModel canvas) {
        if (canvas == null || canvas.getNet() == null) {
            return;
        }
        for (GraphPetriPlace place : canvas.getNet().getGraphPetriPlaceList()) {
            record(canvas, place);
        }
        for (GraphPetriTransition transition : canvas.getNet().getGraphPetriTransitionList()) {
            record(canvas, transition);
        }
        for (GraphObjectFrame frame : canvas.getFrames()) {
            frameBounds.put(frame, new Rectangle(frame.getBounds()));
            enclosing.put(frame, frame.getEnclosing());
        }
    }

    private void record(GraphCanvasModel canvas, GraphElement element) {
        Point2D centre = element.getGraphElementCenter();
        if (centre != null) {
            elementCentres.put(element, new Point2D.Double(centre.getX(), centre.getY()));
        }
        owners.put(element, canvas.ownerOf(element));
    }

    /**
     * @param other the snapshot to compare against
     * @return whether anything at all differs - a drag that ended where it started, or one that
     *         only ever drew an arc, must not leave an undo step behind
     */
    public boolean differsFrom(CanvasLayoutSnapshot other) {
        if (other == null) {
            return true;
        }
        for (Map.Entry<GraphElement, Point2D> entry : elementCentres.entrySet()) {
            Point2D then = other.elementCentres.get(entry.getKey());
            if (then == null || then.distanceSq(entry.getValue()) > 0) {
                return true;
            }
        }
        for (Map.Entry<GraphObjectFrame, Rectangle> entry : frameBounds.entrySet()) {
            if (!entry.getValue().equals(other.frameBounds.get(entry.getKey()))) {
                return true;
            }
        }
        for (Map.Entry<GraphElement, GraphObjectFrame> entry : owners.entrySet()) {
            if (entry.getValue() != other.owners.get(entry.getKey())) {
                return true;
            }
        }
        for (Map.Entry<GraphObjectFrame, GraphObjectFrame> entry : enclosing.entrySet()) {
            if (entry.getValue() != other.enclosing.get(entry.getKey())) {
                return true;
            }
        }
        return elementCentres.size() != other.elementCentres.size()
                || frameBounds.size() != other.frameBounds.size();
    }

    /**
     * Puts the canvas back the way this snapshot found it.
     *
     * <p>Ownership before geometry: {@code claim} refreshes which object each half of a shared
     * place belongs to, so doing it second would leave those anchored to whoever held them
     * during the gesture being undone.
     *
     * @param canvas the canvas to restore
     */
    public void applyTo(GraphCanvasModel canvas) {
        if (canvas == null) {
            return;
        }
        // Everything is lifted to the top level before any of it is nested again. Restoring
        // each object's parent in place would refuse a gesture that swapped two objects round -
        // putting A back inside B while B still sits inside A is a cycle, which nest rightly
        // rejects - and the recorded arrangement is by construction not one, so rebuilding it
        // from a flat start can never hit that.
        for (GraphObjectFrame frame : enclosing.keySet()) {
            if (canvas.getFrames().contains(frame)) {
                canvas.nest(frame, null);
            }
        }
        for (Map.Entry<GraphObjectFrame, GraphObjectFrame> entry : enclosing.entrySet()) {
            if (entry.getValue() != null && canvas.getFrames().contains(entry.getKey())) {
                canvas.nest(entry.getKey(), entry.getValue());
            }
        }
        for (Map.Entry<GraphElement, GraphObjectFrame> entry : owners.entrySet()) {
            if (canvas.ownerOf(entry.getKey()) != entry.getValue()) {
                canvas.claim(entry.getValue(), entry.getKey());
            }
        }
        for (Map.Entry<GraphObjectFrame, Rectangle> entry : frameBounds.entrySet()) {
            Rectangle bounds = entry.getValue();
            entry.getKey().moveTo(bounds.x, bounds.y);
        }
        for (Map.Entry<GraphElement, Point2D> entry : elementCentres.entrySet()) {
            Point2D centre = entry.getValue();
            entry.getKey().setNewCoordinates(new Point2D.Double(centre.getX(), centre.getY()));
        }
        canvas.syncFusions();
    }
}

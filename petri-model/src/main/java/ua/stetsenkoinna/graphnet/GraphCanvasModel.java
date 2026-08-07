package ua.stetsenkoinna.graphnet;

import java.awt.Point;
import java.awt.Rectangle;
import java.awt.geom.Point2D;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * What the editor canvas holds: one drawing, plus the frames that mark out the Petri-objects
 * in it and the places that were joined across those frames.
 *
 * <p>The drawing stays a single net, the way it always was — elements, arcs, selection, undo
 * and copy all work on it unchanged. Which Petri-object an element belongs to is decided by
 * geometry: the frame it is drawn inside. An arc that crosses a frame border is therefore not
 * an arc of any net but a link between two objects, and {@link #toObjModel()} is where that
 * reading happens.
 *
 * <p>Anything drawn outside every frame still simulates: it is gathered into one last,
 * unnamed Petri-object, so a plain net drawn without any frame is a model of one object.
 */
public class GraphCanvasModel implements Serializable {

    /** Name given to the object that collects everything drawn outside every frame. */
    public static final String FREE_OBJECT_NAME = "Free elements";

    /** Gap left between a frame's border and the net laid out inside it. */
    private static final int FRAME_PADDING = 26;

    private String name;
    private GraphPetriNet net;
    private final List<GraphObjectFrame> frames = new ArrayList<>();
    private final List<GraphPlaceFusion> fusions = new ArrayList<>();

    public GraphCanvasModel() {
        this(GraphPetriObjModel.DEFAULT_NAME, new GraphPetriNet());
    }

    public GraphCanvasModel(String name, GraphPetriNet net) {
        this.name = name == null || name.isBlank() ? GraphPetriObjModel.DEFAULT_NAME : name;
        this.net = Objects.requireNonNull(net, "net");
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name == null || name.isBlank() ? GraphPetriObjModel.DEFAULT_NAME : name;
    }

    /**
     * @return the single drawing the canvas shows, holding the elements of every object
     */
    public GraphPetriNet getNet() {
        return net;
    }

    public void setNet(GraphPetriNet net) {
        this.net = Objects.requireNonNull(net, "net");
    }

    public List<GraphObjectFrame> getFrames() {
        return frames;
    }

    public List<GraphPlaceFusion> getFusions() {
        return fusions;
    }

    /**
     * @return true when the canvas carries no Petri-object frames, so it is an ordinary net
     */
    public boolean isPlainNet() {
        return frames.isEmpty();
    }

    /**
     * @param point a point on the canvas
     * @return the innermost frame containing the point, or {@code null} — later frames win,
     *         so a frame drawn on top of another one owns what is drawn in the overlap
     */
    public GraphObjectFrame frameAt(Point2D point) {
        for (int index = frames.size() - 1; index >= 0; index--) {
            if (frames.get(index).contains(point)) {
                return frames.get(index);
            }
        }
        return null;
    }

    /**
     * @param element an element of the drawing
     * @return the frame that owns it, or {@code null} when it is drawn outside every frame
     */
    public GraphObjectFrame ownerOf(GraphElement element) {
        if (element instanceof GraphPetriPlace place) {
            GraphPlaceFusion fusion = fusionOf(place);
            if (fusion != null) {
                // Both halves of a shared place are drawn in the same spot, so the frame
                // they sit in cannot tell them apart — the fusion remembers which is whose.
                return fusion.ownerOf(place);
            }
        }
        Point2D centre = element.getGraphElementCenter();
        return centre == null ? null : frameAt(centre);
    }

    /**
     * @param place a place of the drawing
     * @return the fusion that joins it to a place of another object, or {@code null}
     */
    public GraphPlaceFusion fusionOf(GraphPetriPlace place) {
        for (GraphPlaceFusion fusion : fusions) {
            if (fusion.involves(place)) {
                return fusion;
            }
        }
        return null;
    }

    // ------------------------------------------------------------------ ports

    /**
     * Computes the port markers a frame shows on its border: one per place and one per
     * transition it owns, evenly spaced around the perimeter below the header.
     *
     * <p>Ports are what a locked object is connected to other objects through, since its own
     * elements can no longer be dragged directly on the shared canvas. Nothing about a port
     * is stored — it is derived fresh from the frame's current bounds and its current
     * elements every time, so moving or resizing a frame, or editing its net, keeps the ports
     * in step without any bookkeeping of its own.
     *
     * @param frame the object to compute ports for
     * @return the frame's ports, places first then transitions, both in net order
     */
    public List<FramePort> portsOf(GraphObjectFrame frame) {
        List<GraphElement> owned = new ArrayList<>();
        for (GraphPetriPlace place : net.getGraphPetriPlaceList()) {
            if (ownerOf(place) == frame) {
                owned.add(place);
            }
        }
        for (GraphPetriTransition transition : net.getGraphPetriTransitionList()) {
            if (ownerOf(transition) == frame) {
                owned.add(transition);
            }
        }
        List<PerimeterPoint> positions = perimeterPositions(frame.getBounds(), owned.size());
        List<FramePort> ports = new ArrayList<>(owned.size());
        for (int i = 0; i < owned.size(); i++) {
            PerimeterPoint p = positions.get(i);
            ports.add(new FramePort(owned.get(i), p.point(), p.edge()));
        }
        return ports;
    }

    /**
     * @param point a point on the canvas
     * @return the port under that point, across every frame, or {@code null}
     */
    public FramePort portAt(Point2D point) {
        for (GraphObjectFrame frame : frames) {
            for (FramePort port : portsOf(frame)) {
                if (port.isNear(point)) {
                    return port;
                }
            }
        }
        return null;
    }

    private record PerimeterPoint(Point point, FramePort.Edge edge) {}

    /**
     * Spaces {@code count} points evenly around the perimeter of a frame's bounds, excluding
     * the header strip, starting just past the top-left corner and going clockwise.
     */
    private static List<PerimeterPoint> perimeterPositions(Rectangle bounds, int count) {
        List<PerimeterPoint> points = new ArrayList<>(count);
        if (count <= 0) {
            return points;
        }
        int left = bounds.x;
        int top = bounds.y + GraphObjectFrame.HEADER_HEIGHT;
        int width = bounds.width;
        int height = bounds.height - GraphObjectFrame.HEADER_HEIGHT;
        double perimeter = 2.0 * (width + height);
        for (int i = 0; i < count; i++) {
            // Offset by half a step so a port never lands exactly on a corner.
            double distance = perimeter * (i + 0.5) / count;
            points.add(pointAtDistance(left, top, width, height, distance));
        }
        return points;
    }

    private static PerimeterPoint pointAtDistance(int left, int top, int width, int height, double distance) {
        if (distance < width) {
            return new PerimeterPoint(new Point((int) (left + distance), top), FramePort.Edge.TOP);
        }
        distance -= width;
        if (distance < height) {
            return new PerimeterPoint(new Point(left + width, (int) (top + distance)), FramePort.Edge.RIGHT);
        }
        distance -= height;
        if (distance < width) {
            return new PerimeterPoint(new Point((int) (left + width - distance), top + height), FramePort.Edge.BOTTOM);
        }
        distance -= width;
        return new PerimeterPoint(new Point(left, (int) (top + height - distance)), FramePort.Edge.LEFT);
    }

    /**
     * Joins two places into one shared place.
     *
     * @param master the place the shared marking lives in
     * @param joined the place that becomes the same place
     * @return the new fusion
     * @throws IllegalArgumentException if either place is already shared, or both belong to
     *         the same Petri-object, where a shared place would mean nothing
     */
    public GraphPlaceFusion joinPlaces(GraphPetriPlace master, GraphPetriPlace joined) {
        if (fusionOf(master) != null || fusionOf(joined) != null) {
            throw new IllegalArgumentException("This place is already shared with another Petri-object");
        }
        GraphObjectFrame masterOwner = ownerOf(master);
        GraphObjectFrame joinedOwner = ownerOf(joined);
        if (masterOwner == joinedOwner) {
            throw new IllegalArgumentException(
                    "Both places belong to the same Petri-object — a shared place joins two different objects");
        }
        // Places keep whatever position they already have — the two owners are always
        // different by this point, so moving one onto the other would displace it out of its
        // own object's layout (or, for a free place, away from wherever it was drawn) for no
        // benefit: a framed fusion is drawn as a port-to-port line, which does not depend on
        // where either place actually sits.
        GraphPlaceFusion fusion = new GraphPlaceFusion(master, joined, masterOwner, joinedOwner);
        fusions.add(fusion);
        return fusion;
    }

    /**
     * Drops fusions and frames that no longer refer to anything on the canvas — called after
     * elements were deleted.
     */
    public void removeDanglingFusions() {
        List<GraphPetriPlace> places = net.getGraphPetriPlaceList();
        fusions.removeIf(fusion -> !places.contains(fusion.getMaster()) || !places.contains(fusion.getJoined()));
    }

    /**
     * Keeps every shared place drawn as one circle after elements were moved.
     */
    public void syncFusions() {
        for (GraphPlaceFusion fusion : fusions) {
            fusion.syncPosition();
        }
    }

    // ------------------------------------------------------------------ splitting

    /**
     * Reads the canvas as a Petri-object model.
     *
     * <p>Objects come out in frame order, with everything drawn outside every frame gathered
     * into a last unnamed object. An arc whose ends sit in different objects becomes a link
     * between them, and a shared place becomes a place fusion.
     *
     * @return the model the canvas describes
     * @throws IllegalStateException if the canvas has nothing on it
     */
    public GraphPetriObjModel toObjModel() {
        List<GraphElement> elements = new ArrayList<>();
        elements.addAll(net.getGraphPetriPlaceList());
        elements.addAll(net.getGraphPetriTransitionList());
        if (elements.isEmpty()) {
            throw new IllegalStateException("There is nothing on the canvas to simulate");
        }

        // Frames become objects in the order they appear; index -1 collects the free elements.
        Map<GraphElement, Integer> owners = new IdentityHashMap<>();
        boolean hasFree = false;
        for (GraphElement element : elements) {
            GraphObjectFrame frame = ownerOf(element);
            int owner = frame == null ? -1 : frames.indexOf(frame);
            owners.put(element, owner);
            hasFree |= owner < 0;
        }
        int freeIndex = hasFree ? frames.size() : -1;
        if (hasFree) {
            for (Map.Entry<GraphElement, Integer> entry : owners.entrySet()) {
                if (entry.getValue() < 0) {
                    entry.setValue(freeIndex);
                }
            }
        }

        int objectCount = frames.size() + (hasFree ? 1 : 0);
        List<List<GraphPetriPlace>> places = new ArrayList<>();
        List<List<GraphPetriTransition>> transitions = new ArrayList<>();
        for (int index = 0; index < objectCount; index++) {
            places.add(new ArrayList<>());
            transitions.add(new ArrayList<>());
        }
        for (GraphPetriPlace place : net.getGraphPetriPlaceList()) {
            places.get(owners.get(place)).add(place);
        }
        for (GraphPetriTransition transition : net.getGraphPetriTransitionList()) {
            transitions.get(owners.get(transition)).add(transition);
        }

        List<List<GraphArcIn>> arcsIn = new ArrayList<>();
        List<List<GraphArcOut>> arcsOut = new ArrayList<>();
        for (int index = 0; index < objectCount; index++) {
            arcsIn.add(new ArrayList<>());
            arcsOut.add(new ArrayList<>());
        }

        GraphPetriObjModel model = new GraphPetriObjModel(name);
        List<ua.stetsenkoinna.petriobj.PetriObjLink> links = new ArrayList<>();

        for (GraphArcIn arc : net.getGraphArcInList()) {
            GraphPetriPlace place = placeOf(arc.getBeginElement());
            GraphPetriTransition transition = transitionOf(arc.getEndElement());
            if (place == null || transition == null) {
                continue;
            }
            int placeOwner = owners.get(place);
            int transitionOwner = owners.get(transition);
            if (placeOwner == transitionOwner) {
                arcsIn.get(placeOwner).add(arc);
            } else {
                links.add(ua.stetsenkoinna.petriobj.PetriObjLink.placeToTransition(
                        placeOwner, places.get(placeOwner).indexOf(place),
                        transitionOwner, transitions.get(transitionOwner).indexOf(transition),
                        Math.max(1, arc.getArcIn().getQuantity()), arc.getArcIn().getIsInf()));
            }
        }

        for (GraphArcOut arc : net.getGraphArcOutList()) {
            GraphPetriTransition transition = transitionOf(arc.getBeginElement());
            GraphPetriPlace place = placeOf(arc.getEndElement());
            if (place == null || transition == null) {
                continue;
            }
            int placeOwner = owners.get(place);
            int transitionOwner = owners.get(transition);
            if (placeOwner == transitionOwner) {
                arcsOut.get(placeOwner).add(arc);
            } else {
                links.add(ua.stetsenkoinna.petriobj.PetriObjLink.transitionToPlace(
                        transitionOwner, transitions.get(transitionOwner).indexOf(transition),
                        placeOwner, places.get(placeOwner).indexOf(place),
                        Math.max(1, arc.getArcOut().getQuantity())));
            }
        }

        for (GraphPlaceFusion fusion : fusions) {
            Integer masterOwner = owners.get(fusion.getMaster());
            Integer joinedOwner = owners.get(fusion.getJoined());
            if (masterOwner == null || joinedOwner == null || masterOwner.equals(joinedOwner)) {
                continue;
            }
            links.add(ua.stetsenkoinna.petriobj.PetriObjLink.placeFusion(
                    joinedOwner, places.get(joinedOwner).indexOf(fusion.getJoined()),
                    masterOwner, places.get(masterOwner).indexOf(fusion.getMaster())));
        }

        for (int index = 0; index < objectCount; index++) {
            GraphPetriNet objectNet = new GraphPetriNet(null,
                    new ArrayList<>(places.get(index)),
                    new ArrayList<>(transitions.get(index)),
                    new ArrayList<>(arcsIn.get(index)),
                    new ArrayList<>(arcsOut.get(index)));
            boolean free = index == freeIndex;
            GraphObjectFrame frame = free ? null : frames.get(index);
            GraphPetriObject object = new GraphPetriObject(
                    free ? FREE_OBJECT_NAME : frame.getName(), objectNet);
            if (!free) {
                object.setPriority(frame.getPriority());
                object.setTemplate(frame.getTemplate());
                object.setPosition(new Point(frame.getBounds().x, frame.getBounds().y));
                object.setSize(frame.getBounds().width, frame.getBounds().height);
                object.setCollapsed(frame.isCollapsed());
            }
            model.addObject(object);
        }

        for (ua.stetsenkoinna.petriobj.PetriObjLink link : links) {
            model.addLink(link);
        }
        return model;
    }

    private static GraphPetriPlace placeOf(GraphElement element) {
        return element instanceof GraphPetriPlace place ? place : null;
    }

    private static GraphPetriTransition transitionOf(GraphElement element) {
        return element instanceof GraphPetriTransition transition ? transition : null;
    }

    // ------------------------------------------------------------------ assembling

    /**
     * Lays a Petri-object model out on one canvas: a frame per object, its net inside, and
     * the links restored as arcs crossing frame borders or as shared places.
     *
     * @param model the model to show
     * @return the canvas that draws it
     */
    public static GraphCanvasModel fromObjModel(GraphPetriObjModel model) {
        GraphCanvasModel canvas = new GraphCanvasModel(model.getName(), new GraphPetriNet());

        for (int index = 0; index < model.getObjectCount(); index++) {
            GraphPetriObject object = model.getObject(index);
            boolean free = FREE_OBJECT_NAME.equals(object.getName()) && object.getPosition().x == 0
                    && object.getPosition().y == 0 && object.getWidth() == 0;

            Rectangle bounds = new Rectangle(
                    object.getPosition().x, object.getPosition().y,
                    object.getWidth() > 0 ? object.getWidth() : defaultWidth(object),
                    object.getHeight() > 0 ? object.getHeight() : defaultHeight(object));
            if (!free) {
                GraphObjectFrame frame = new GraphObjectFrame(object.getName(), bounds);
                frame.setPriority(object.getPriority());
                frame.setTemplate(object.getTemplate());
                canvas.frames.add(frame);
                // Put the object's drawing in the middle of its own frame; the net keeps its
                // shape, only where it sits on the shared canvas is decided here.
                object.getGraphNet().changeLocation(new Point(
                        bounds.x + bounds.width / 2,
                        bounds.y + (bounds.height + GraphObjectFrame.HEADER_HEIGHT) / 2));
                if (object.isCollapsed()) {
                    frame.setCollapsed(true);
                }
            }
            canvas.absorb(object.getGraphNet());
        }

        canvas.restoreLinks(model);
        canvas.syncFusions();
        return canvas;
    }

    private static int defaultWidth(GraphPetriObject object) {
        return Math.max(GraphObjectFrame.MIN_WIDTH * 2,
                80 * Math.max(2, object.getTransitionCount() + object.getPlaceCount() / 2));
    }

    private static int defaultHeight(GraphPetriObject object) {
        return Math.max(GraphObjectFrame.MIN_HEIGHT * 2, 60 * Math.max(2, object.getPlaceCount()));
    }

    /**
     * Adds every element and arc of a net to the canvas drawing.
     */
    private void absorb(GraphPetriNet other) {
        net.getGraphPetriPlaceList().addAll(other.getGraphPetriPlaceList());
        net.getGraphPetriTransitionList().addAll(other.getGraphPetriTransitionList());
        net.getGraphArcInList().addAll(other.getGraphArcInList());
        net.getGraphArcOutList().addAll(other.getGraphArcOutList());
    }

    /**
     * Turns the model's link declarations back into things drawn on the canvas: an arc
     * between elements of two objects, or a shared place.
     */
    private void restoreLinks(GraphPetriObjModel model) {
        for (ua.stetsenkoinna.petriobj.PetriObjLink link : model.getLinks()) {
            GraphPetriObject source = model.getObject(link.getSourceObject());
            GraphPetriObject target = model.getObject(link.getTargetObject());
            switch (link.getType()) {
                case PLACE_FUSION -> {
                    GraphPetriPlace joined = placeAt(source, link.getSourceElement());
                    GraphPetriPlace master = placeAt(target, link.getTargetElement());
                    if (joined != null && master != null) {
                        fusions.add(new GraphPlaceFusion(master, joined,
                                frameOfObject(link.getTargetObject()),
                                frameOfObject(link.getSourceObject())));
                    }
                }
                case TRANSITION_TO_PLACE -> {
                    GraphPetriTransition transition = transitionAt(source, link.getSourceElement());
                    GraphPetriPlace place = placeAt(target, link.getTargetElement());
                    if (transition != null && place != null) {
                        net.getGraphArcOutList().add(
                                GraphArcFactory.outArc(transition, place, link.getQuantity()));
                    }
                }
                case PLACE_TO_TRANSITION -> {
                    GraphPetriPlace place = placeAt(source, link.getSourceElement());
                    GraphPetriTransition transition = transitionAt(target, link.getTargetElement());
                    if (place != null && transition != null) {
                        net.getGraphArcInList().add(GraphArcFactory.inArc(
                                place, transition, link.getQuantity(), link.isInformational()));
                    }
                }
            }
        }
    }

    /**
     * @param objectIndex position of an object in the model being laid out
     * @return the frame created for it, or null when it is the free-elements object
     */
    private GraphObjectFrame frameOfObject(int objectIndex) {
        return objectIndex >= 0 && objectIndex < frames.size() ? frames.get(objectIndex) : null;
    }

    private static GraphPetriPlace placeAt(GraphPetriObject object, int index) {
        List<GraphPetriPlace> places = object.getGraphNet().getGraphPetriPlaceList();
        return index >= 0 && index < places.size() ? places.get(index) : null;
    }

    private static GraphPetriTransition transitionAt(GraphPetriObject object, int index) {
        List<GraphPetriTransition> transitions = object.getGraphNet().getGraphPetriTransitionList();
        return index >= 0 && index < transitions.size() ? transitions.get(index) : null;
    }

    /**
     * @return the frames, in the order that indexes the objects they mark out
     */
    public List<GraphObjectFrame> framesInOrder() {
        return Collections.unmodifiableList(frames);
    }
}

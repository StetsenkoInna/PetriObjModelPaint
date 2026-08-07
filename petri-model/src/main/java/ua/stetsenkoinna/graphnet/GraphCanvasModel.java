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
 * and copy all work on it unchanged. Which Petri-object an element belongs to is what its
 * frame explicitly claims ({@link GraphObjectFrame#addMember}), not where it happens to sit —
 * so dragging a frame across the canvas can never quietly pick up something it passes over. An
 * arc that crosses between two objects' elements is therefore not an arc of any net but a link
 * between them, and {@link #toObjModel()} is where that reading happens.
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
     * @return the innermost frame whose rectangle contains the point, or {@code null} — later
     *         frames win when two overlap. A hit test for clicks and menus; it says nothing
     *         about which object an element belongs to, see {@link #ownerOf}.
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
     * @return the frame that claims it ({@link GraphObjectFrame#hasMember}), or {@code null}
     *         when no frame does, which makes it one of the free elements
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
        for (GraphObjectFrame frame : frames) {
            if (frame.hasMember(element)) {
                return frame;
            }
        }
        return null;
    }

    /**
     * Called when a frame is removed: what it claimed does not vanish along with it, it just
     * stops being explicitly claimed — falling to whichever other frame's rectangle happens to
     * cover it now, or to the free elements when none does. This is the one place a claim is
     * still decided by geometry, since there is no more explicit frame left to have decided it.
     *
     * @param removed the frame that was just taken off the canvas
     */
    public void releaseMembers(GraphObjectFrame removed) {
        for (GraphElement element : new ArrayList<>(removed.getMembers())) {
            removed.removeMember(element);
            Point2D centre = element.getGraphElementCenter();
            GraphObjectFrame covering = centre == null ? null : frameAt(centre);
            if (covering != null) {
                covering.addMember(element);
            }
        }
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

    /** Smallest gap kept between two ports on the same edge, so their circles never touch. */
    private static final double MIN_PORT_GAP = FramePort.RADIUS * 3.0;

    /**
     * Computes the port markers a frame shows on its border: one per place and one per
     * transition it owns, each sitting on whichever side of the frame — left, right or bottom,
     * never the header side on top — is closest to where the element itself actually is, so a
     * port reads as "this element, right about here" rather than an arbitrary slot.
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
        List<PositionedPort> positions = perimeterPositionsNear(frame.getBounds(), owned);
        List<FramePort> ports = new ArrayList<>(owned.size());
        for (int i = 0; i < owned.size(); i++) {
            ports.add(new FramePort(owned.get(i), positions.get(i).point(), positions.get(i).edge()));
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

    /** One element's port, still being placed: which edge, and how far along it. */
    private static final class PendingPort {
        final int index;
        final FramePort.Edge edge;
        double along;

        PendingPort(int index, FramePort.Edge edge, double along) {
            this.index = index;
            this.edge = edge;
            this.along = along;
        }
    }

    private record PositionedPort(Point point, FramePort.Edge edge) {}

    /**
     * Places one port per element of {@code owned}, on whichever of the frame's left, right or
     * bottom side sits closest to that element's own position — the top side, under the
     * header, is never used. Elements that land close together on the same side are then
     * nudged apart just enough that their circles do not overlap.
     *
     * @param bounds the frame's current rectangle
     * @param owned the elements to place a port for, in the order ports are returned
     * @return one position per element of {@code owned}, same order
     */
    private static List<PositionedPort> perimeterPositionsNear(Rectangle bounds, List<GraphElement> owned) {
        List<PositionedPort> result = new ArrayList<>(Collections.nCopies(owned.size(), null));
        if (owned.isEmpty()) {
            return result;
        }
        int left = bounds.x;
        int right = bounds.x + bounds.width;
        int top = bounds.y + GraphObjectFrame.HEADER_HEIGHT;
        int bottom = bounds.y + bounds.height;

        List<PendingPort> onLeft = new ArrayList<>();
        List<PendingPort> onRight = new ArrayList<>();
        List<PendingPort> onBottom = new ArrayList<>();

        for (int i = 0; i < owned.size(); i++) {
            Point2D centre = owned.get(i).getGraphElementCenter();
            double ex = centre == null ? (left + right) / 2.0 : clamp(centre.getX(), left, right);
            double ey = centre == null ? bottom : clamp(centre.getY(), top, bottom);
            double distLeft = ex - left;
            double distRight = right - ex;
            double distBottom = bottom - ey;
            double nearest = Math.min(distLeft, Math.min(distRight, distBottom));
            if (nearest == distLeft) {
                onLeft.add(new PendingPort(i, FramePort.Edge.LEFT, clamp(ey, top, bottom)));
            } else if (nearest == distRight) {
                onRight.add(new PendingPort(i, FramePort.Edge.RIGHT, clamp(ey, top, bottom)));
            } else {
                onBottom.add(new PendingPort(i, FramePort.Edge.BOTTOM, clamp(ex, left, right)));
            }
        }

        spaceOut(onLeft, top, bottom);
        spaceOut(onRight, top, bottom);
        spaceOut(onBottom, left, right);

        for (PendingPort p : onLeft) {
            result.set(p.index, new PositionedPort(new Point(left, (int) Math.round(p.along)), p.edge));
        }
        for (PendingPort p : onRight) {
            result.set(p.index, new PositionedPort(new Point(right, (int) Math.round(p.along)), p.edge));
        }
        for (PendingPort p : onBottom) {
            result.set(p.index, new PositionedPort(new Point((int) Math.round(p.along), bottom), p.edge));
        }
        return result;
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    /**
     * Enforces a minimum gap between ports sharing one edge, moving each only as far as it
     * takes to stop overlapping its neighbour — a port that already has room to itself does
     * not move at all.
     */
    private static void spaceOut(List<PendingPort> group, double min, double max) {
        if (group.size() < 2) {
            return;
        }
        group.sort((a, b) -> Double.compare(a.along, b.along));
        for (int i = 1; i < group.size(); i++) {
            double wanted = group.get(i - 1).along + MIN_PORT_GAP;
            if (group.get(i).along < wanted) {
                group.get(i).along = wanted;
            }
        }
        // The forward pass alone can push the last ports past the edge's end; pull the whole
        // run back from that end, which keeps every gap intact.
        int last = group.size() - 1;
        if (group.get(last).along > max) {
            group.get(last).along = max;
            for (int i = last - 1; i >= 0; i--) {
                double limit = group.get(i + 1).along - MIN_PORT_GAP;
                if (group.get(i).along > limit) {
                    group.get(i).along = limit;
                }
            }
        }
        for (PendingPort p : group) {
            p.along = clamp(p.along, min, max);
        }
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
                // The object's own places and transitions are exactly what this frame claims —
                // known outright here, from the document, rather than left to be re-derived
                // from wherever changeLocation below happens to put them.
                for (GraphPetriPlace place : object.getGraphNet().getGraphPetriPlaceList()) {
                    frame.addMember(place);
                }
                for (GraphPetriTransition transition : object.getGraphNet().getGraphPetriTransitionList()) {
                    frame.addMember(transition);
                }
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

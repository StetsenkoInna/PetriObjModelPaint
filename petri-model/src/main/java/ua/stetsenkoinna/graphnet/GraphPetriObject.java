package ua.stetsenkoinna.graphnet;

import java.awt.Point;
import java.io.Serializable;
import java.util.Objects;

/**
 * One Petri-object of a {@link GraphPetriObjModel}: a Petri net plus the properties that
 * only make sense once the net takes part in a composition.
 *
 * <p>The net is the object's behaviour, edited on the net layer of the editor. The name,
 * the priority used to resolve conflicts between objects, and the position on the structure
 * layer belong to the object, not to the net — two objects instantiated from the same
 * template differ exactly in those.
 */
public class GraphPetriObject implements Serializable {

    /** Where a newly created object is put when the caller has no position in mind. */
    private static final Point DEFAULT_POSITION = new Point(0, 0);

    private String name;
    private GraphPetriNet graphNet;
    private int priority;
    private Point position;
    private NetTemplateRef template;
    private int width;
    private int height;
    private boolean collapsed;

    /**
     * @param name display name of the Petri-object
     * @param graphNet the net that describes its behaviour
     */
    public GraphPetriObject(String name, GraphPetriNet graphNet) {
        this(name, graphNet, 0, new Point(DEFAULT_POSITION), null);
    }

    /**
     * @param name display name of the Petri-object
     * @param graphNet the net that describes its behaviour
     * @param priority conflict-resolution priority among the objects of a model
     * @param position position of the object's node on the structure layer
     * @param template the library method this object was instantiated from, or {@code null}
     *        for a net drawn by hand
     */
    public GraphPetriObject(String name, GraphPetriNet graphNet, int priority,
                            Point position, NetTemplateRef template) {
        this.name = Objects.requireNonNull(name, "name");
        this.graphNet = Objects.requireNonNull(graphNet, "graphNet");
        this.priority = priority;
        this.position = position == null ? new Point(DEFAULT_POSITION) : position;
        this.template = template;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = Objects.requireNonNull(name, "name");
    }

    public GraphPetriNet getGraphNet() {
        return graphNet;
    }

    public void setGraphNet(GraphPetriNet graphNet) {
        this.graphNet = Objects.requireNonNull(graphNet, "graphNet");
    }

    /**
     * @return the priority this object has when several objects want to act at the same
     *         simulation moment; higher wins
     */
    public int getPriority() {
        return priority;
    }

    public void setPriority(int priority) {
        this.priority = priority;
    }

    /**
     * @return position of this object's node on the structure layer of the editor
     */
    public Point getPosition() {
        return position;
    }

    public void setPosition(Point position) {
        this.position = position == null ? new Point(DEFAULT_POSITION) : position;
    }

    /**
     * Size of the object's frame on the canvas. Zero means the frame was never sized — a
     * reader then picks a size that fits the net.
     *
     * @return width of the frame in canvas units, or 0
     */
    public int getWidth() {
        return width;
    }

    /**
     * @return height of the frame in canvas units, or 0
     */
    public int getHeight() {
        return height;
    }

    public void setSize(int width, int height) {
        this.width = Math.max(0, width);
        this.height = Math.max(0, height);
    }

    /**
     * @return true if the object's frame is shown collapsed to a single node
     */
    public boolean isCollapsed() {
        return collapsed;
    }

    public void setCollapsed(boolean collapsed) {
        this.collapsed = collapsed;
    }

    /**
     * @return the net library method this object was instantiated from, or {@code null}
     *         when its net was drawn by hand
     */
    public NetTemplateRef getTemplate() {
        return template;
    }

    public void setTemplate(NetTemplateRef template) {
        this.template = template;
    }

    /**
     * @return the number of places of this object's net, i.e. the range of place indices a
     *         link may address
     */
    public int getPlaceCount() {
        return graphNet.getGraphPetriPlaceList().size();
    }

    /**
     * @return the number of transitions of this object's net
     */
    public int getTransitionCount() {
        return graphNet.getGraphPetriTransitionList().size();
    }

    /**
     * @param index position of the place in the object's net
     * @return the place's display name, or {@code P<index>} when the net has no such place
     */
    public String getPlaceName(int index) {
        if (index < 0 || index >= graphNet.getGraphPetriPlaceList().size()) {
            return "P" + index;
        }
        return graphNet.getGraphPetriPlaceList().get(index).getName();
    }

    /**
     * @param index position of the transition in the object's net
     * @return the transition's display name, or {@code T<index>} when the net has no such
     *         transition
     */
    public String getTransitionName(int index) {
        if (index < 0 || index >= graphNet.getGraphPetriTransitionList().size()) {
            return "T" + index;
        }
        return graphNet.getGraphPetriTransitionList().get(index).getName();
    }

    @Override
    public String toString() {
        return name;
    }
}

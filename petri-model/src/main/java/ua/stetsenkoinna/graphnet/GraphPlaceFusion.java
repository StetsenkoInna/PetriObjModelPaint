package ua.stetsenkoinna.graphnet;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Stroke;
import java.awt.geom.Point2D;
import java.io.Serializable;
import java.util.Objects;

/**
 * Two places of different Petri-objects drawn, and simulated, as one place.
 *
 * <p>A shared place is the classic way two Petri-objects are composed, and it is not an arc:
 * nothing flows along it, the two places simply are the same place.
 *
 * <p>How that is shown depends on whether the two places are locked inside a Petri-object.
 * Between two free places — not yet grouped into any object — it is shown literally: the
 * joined place is kept on top of the place it was joined to, so the arcs of both meet at one
 * circle, drawn with a second ring to say it is shared. Between two framed objects the places
 * themselves are locked and not necessarily anywhere near each other, so there the fusion is
 * shown as a line between the two objects' ports instead ({@link #drawBetweenPorts}), and the
 * places keep whatever position they already had inside their own object.
 *
 * <p>Which of the two is the {@code master} decides nothing about the semantics; it only
 * fixes which object keeps the place instance when the model is built, and, for a free-place
 * fusion, which one the other is moved onto.
 */
public class GraphPlaceFusion implements Serializable {

    /** Radius added around the place to mark it as shared. */
    private static final int RING_MARGIN = 5;
    private static final Color RING = new Color(0x1B, 0x7F, 0x3B);
    private static final Color RING_SELECTED = new Color(0xD9, 0x7A, 0x00);

    private final GraphPetriPlace master;
    private final GraphPetriPlace joined;

    /**
     * Which Petri-object each half belongs to, remembered when the two were joined.
     *
     * <p>Everywhere else on the canvas the frame an element is drawn in decides which object
     * owns it. A shared place is the one case where that cannot work: the two halves sit on
     * top of each other, so they are drawn in the same frame while belonging to two objects.
     * Null means the half belongs to no frame — the free elements.
     */
    private final GraphObjectFrame masterOwner;
    private final GraphObjectFrame joinedOwner;

    /**
     * @param master the place the shared marking lives in
     * @param joined the place that becomes the same place
     * @param masterOwner frame the master is drawn in, or null
     * @param joinedOwner frame the joined place is drawn in, or null
     */
    public GraphPlaceFusion(GraphPetriPlace master, GraphPetriPlace joined,
                            GraphObjectFrame masterOwner, GraphObjectFrame joinedOwner) {
        this.master = Objects.requireNonNull(master, "master");
        this.joined = Objects.requireNonNull(joined, "joined");
        if (master == joined) {
            throw new IllegalArgumentException("A place cannot be joined with itself");
        }
        this.masterOwner = masterOwner;
        this.joinedOwner = joinedOwner;
        // Positioning is the caller's call: a free-place fusion wants the classic coincident
        // circles (see syncPosition()), a port-to-port one must leave each place exactly
        // where it already sits inside its own locked object.
    }

    /**
     * @return true if both halves belong to a Petri-object frame, which is when the fusion is
     *         drawn as a port-to-port line rather than a coincident ring
     */
    public boolean isBetweenFrames() {
        return masterOwner != null && joinedOwner != null;
    }

    /**
     * @return the frame that owns the master half, or null when it belongs to no frame
     */
    public GraphObjectFrame getMasterOwner() {
        return masterOwner;
    }

    /**
     * @return the frame that owns the joined half, or null when it belongs to no frame
     */
    public GraphObjectFrame getJoinedOwner() {
        return joinedOwner;
    }

    /**
     * @param place one of the joined places
     * @return the frame that owns that half of the shared place
     */
    public GraphObjectFrame ownerOf(GraphPetriPlace place) {
        return place == master ? masterOwner : joinedOwner;
    }

    public GraphPetriPlace getMaster() {
        return master;
    }

    public GraphPetriPlace getJoined() {
        return joined;
    }

    /**
     * @param place a place on the canvas
     * @return true if this fusion joins that place
     */
    public boolean involves(GraphPetriPlace place) {
        return master == place || joined == place;
    }

    /**
     * Puts the joined place back on top of the master, which is what makes the two read as
     * one place. Called after either of them was dragged.
     */
    public void syncPosition() {
        Point2D centre = master.getGraphElementCenter();
        if (centre != null) {
            joined.setNewCoordinates(new Point2D.Double(centre.getX(), centre.getY()));
        }
    }

    /**
     * Moves the shared place, keeping both halves together.
     */
    public void moveTo(Point2D centre) {
        master.setNewCoordinates(new Point2D.Double(centre.getX(), centre.getY()));
        syncPosition();
    }

    /**
     * Draws the ring that marks two free places as shared. Does nothing for a fusion between
     * two framed objects — draw {@link #drawBetweenPorts} for that one instead.
     *
     * @param g2 canvas graphics
     * @param selected whether the fusion is the current selection
     */
    public void draw(Graphics2D g2, boolean selected) {
        if (isBetweenFrames()) {
            return;
        }
        Point2D centre = master.getGraphElementCenter();
        if (centre == null) {
            return;
        }
        Stroke previousStroke = g2.getStroke();
        Color previousColor = g2.getColor();

        int radius = master.getBorder() + RING_MARGIN;
        g2.setColor(selected ? RING_SELECTED : RING);
        g2.setStroke(new BasicStroke(selected ? 2.4f : 1.6f));
        g2.drawOval((int) centre.getX() - radius, (int) centre.getY() - radius, radius * 2, radius * 2);

        g2.setStroke(previousStroke);
        g2.setColor(previousColor);
    }

    /**
     * Draws a fusion between two framed objects as a line between their ports, since the
     * places themselves are locked wherever they sit inside their own object and are not
     * expected to be anywhere near each other.
     *
     * @param g2 canvas graphics
     * @param masterPort the master's port position on its frame's border
     * @param joinedPort the joined place's port position on its frame's border
     * @param selected whether the fusion is the current selection
     */
    public void drawBetweenPorts(Graphics2D g2, Point masterPort, Point joinedPort, boolean selected) {
        Stroke previousStroke = g2.getStroke();
        Color previousColor = g2.getColor();

        g2.setColor(selected ? RING_SELECTED : RING);
        g2.setStroke(new BasicStroke(selected ? 2.4f : 1.6f));
        g2.drawLine(masterPort.x, masterPort.y, joinedPort.x, joinedPort.y);

        g2.setStroke(previousStroke);
        g2.setColor(previousColor);
    }

    /**
     * @param point a point on the canvas
     * @return true if the point is on the shared-place ring, so a click there selects the
     *         fusion rather than the place
     */
    public boolean isOnRing(Point2D point) {
        if (isBetweenFrames()) {
            return false;
        }
        Point2D centre = master.getGraphElementCenter();
        if (centre == null) {
            return false;
        }
        double distance = centre.distance(point);
        int radius = master.getBorder() + RING_MARGIN;
        return distance <= radius + 3 && distance >= radius - 3;
    }

    @Override
    public String toString() {
        return master.getName() + " = " + joined.getName();
    }
}

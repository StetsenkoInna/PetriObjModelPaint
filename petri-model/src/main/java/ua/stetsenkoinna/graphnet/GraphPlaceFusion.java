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
 * <p>How that is shown depends on whether either place is locked inside a Petri-object.
 * Between two free places — not yet grouped into any object — it is shown literally: the
 * joined place is kept on top of the place it was joined to, so the arcs of both meet at one
 * circle, drawn with a second ring to say it is shared. The moment either half is framed that
 * stops being possible — a locked place cannot be moved onto anything, and moving a free one
 * onto a place buried inside someone else's object would corrupt that object's own layout — so
 * there the fusion is shown as a line instead ({@link #drawBetweenPorts}), one end anchored to
 * whichever port stands for a framed half, the other to a free half's own position, and both
 * places keep whatever position they already had.
 *
 * <p>Which of the two is the {@code master} decides nothing about the semantics; it only
 * fixes which object keeps the place instance when the model is built, and, for a free-place
 * fusion, which one the other is moved onto.
 */
public class GraphPlaceFusion implements Serializable {
    /**
     * Pinned before this class ever reached a saved file, which it does from now on.
     * Left to the compiler it would be recomputed from the class shape, and the next
     * field added here would make every file written before that unreadable.
     */
    private static final long serialVersionUID = 1L;


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
     *
     * <p>Not final: ownership changes after the join (the owning object is ungrouped, a half
     * is dragged into another object), and a snapshot frozen at join time kept the fusion
     * anchored to a frame that was no longer on the canvas. {@link GraphCanvasModel} refreshes
     * these from the claims whenever ownership moves.
     */
    private GraphObjectFrame masterOwner;
    private GraphObjectFrame joinedOwner;

    /**
     * Re-reads which frame owns each half; see {@link GraphCanvasModel#refreshFusionOwners}.
     */
    void refreshOwners(GraphObjectFrame masterOwner, GraphObjectFrame joinedOwner) {
        this.masterOwner = masterOwner;
        this.joinedOwner = joinedOwner;
    }

    /**
     * Mirrors the master's marking onto the joined half. A shared place is one place with
     * one marking - a PNML reference place carries no marking of its own, and the built
     * simulation replaces the joined half's instance with the master's outright - but the
     * editor used to keep showing each half's own token count, so the drawing displayed two
     * different numbers for what the model runs as one.
     */
    public void syncMarking() {
        copyMarking(master, joined);
    }

    /**
     * Takes the given half's marking as the shared one, mirroring it onto the other half -
     * what an edit through either half's properties dialog means.
     *
     * @param half the half whose marking the user just set
     */
    public void adoptMarkingFrom(GraphPetriPlace half) {
        copyMarking(half, half == master ? joined : master);
    }

    private static void copyMarking(GraphPetriPlace from, GraphPetriPlace to) {
        ua.stetsenkoinna.petriobj.PetriP source = from.getPetriPlace();
        ua.stetsenkoinna.petriobj.PetriP target = to.getPetriPlace();
        if (source.markIsParam()) {
            target.setMarkParam(source.getMarkParamName());
        } else {
            target.setMark(source.getMark());
            target.setMarkParam(null);
        }
    }

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
     * @return true if either half belongs to a Petri-object frame, which is when the fusion is
     *         drawn as a line — to the other half's port if it too is framed, to its own
     *         position if it is free — rather than a coincident ring
     */
    public boolean isAnchoredToAFrame() {
        return masterOwner != null || joinedOwner != null;
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
     *
     * <p>Does nothing once either half is framed - see the class doc: a locked place cannot be
     * moved onto anything, and moving a free one onto a place buried inside someone else's
     * object would corrupt that object's own layout, so both keep whatever position they
     * already had and only the drawn line ({@link #drawBetweenPorts}) shows the fusion. Left
     * unguarded here, {@link GraphCanvasModel#syncFusions()} - called after every frame drag,
     * to keep a free-free fusion's ring coincident - pulled a framed half's counterpart on top
     * of wherever the frame had just moved it, so the two places visibly merged the moment
     * either object was dragged.
     */
    public void syncPosition() {
        if (isAnchoredToAFrame()) {
            return;
        }
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
     * Draws the ring that marks two free places as shared. Does nothing once either half is
     * framed — draw {@link #drawBetweenPorts} for that one instead.
     *
     * @param g2 canvas graphics
     * @param selected whether the fusion is the current selection
     */
    public void draw(Graphics2D g2, boolean selected) {
        if (isAnchoredToAFrame()) {
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
     * Draws a fusion anchored to at least one frame as a line between the two halves' drawn
     * positions — a framed half's port, a free half's own position — since a locked place
     * cannot be moved to sit on top of the other half the way two free places can.
     *
     * <p>The line is the UML dependency notation: black, dashed, with an open arrowhead at
     * the master end - the reference relation is stored directed, the joined half points at
     * the real place it stands for, exactly the way a PNML {@code referencePlace} points at
     * the node it references. It used to be a plain solid green line, which read as some
     * special kind of arc rather than as a "this IS that one" reference.
     *
     * @param g2 canvas graphics
     * @param masterPoint where the master half is drawn: its port if framed, its own position
     *        if free
     * @param joinedPoint the same for the joined half
     * @param selected whether the fusion is the current selection or lit by the animation
     */
    public void drawBetweenPorts(Graphics2D g2, Point masterPoint, Point joinedPoint, boolean selected) {
        Stroke previousStroke = g2.getStroke();
        Color previousColor = g2.getColor();

        g2.setColor(selected ? RING_SELECTED : Color.BLACK);
        g2.setStroke(new BasicStroke(selected ? 2.2f : 1.4f, BasicStroke.CAP_BUTT,
                BasicStroke.JOIN_MITER, 10f, new float[] {6f, 6f}, 0f));
        g2.drawLine(masterPoint.x, masterPoint.y, joinedPoint.x, joinedPoint.y);

        // Open (unfilled) arrowhead at the master end, the UML dependency style.
        double angle = Math.atan2(masterPoint.y - joinedPoint.y, masterPoint.x - joinedPoint.x);
        int length = 11;
        double spread = Math.toRadians(24);
        g2.setStroke(new BasicStroke(selected ? 2.2f : 1.4f));
        g2.drawLine(masterPoint.x, masterPoint.y,
                (int) (masterPoint.x - length * Math.cos(angle - spread)),
                (int) (masterPoint.y - length * Math.sin(angle - spread)));
        g2.drawLine(masterPoint.x, masterPoint.y,
                (int) (masterPoint.x - length * Math.cos(angle + spread)),
                (int) (masterPoint.y - length * Math.sin(angle + spread)));

        g2.setStroke(previousStroke);
        g2.setColor(previousColor);
    }

    /**
     * Whether a running animation currently lights this shared place - a token just landed
     * in (or left) one of its halves, so the line and both halves pulse together. Transient:
     * a saved file never contains a mid-animation state.
     */
    private transient boolean animationLit;

    public boolean isAnimationLit() {
        return animationLit;
    }

    public void setAnimationLit(boolean animationLit) {
        this.animationLit = animationLit;
    }

    /**
     * @param point a point on the canvas
     * @return true if the point is on the shared-place ring, so a click there selects the
     *         fusion rather than the place
     */
    public boolean isOnRing(Point2D point) {
        if (isAnchoredToAFrame()) {
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

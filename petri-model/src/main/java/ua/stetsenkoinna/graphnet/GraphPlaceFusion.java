package ua.stetsenkoinna.graphnet;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Stroke;
import java.awt.geom.Point2D;
import java.io.Serializable;
import java.util.Objects;
import ua.stetsenkoinna.theme.CanvasColor;
import ua.stetsenkoinna.theme.CanvasPalette;

/**
 * Two places of different Petri-objects drawn, and simulated, as one place.
 *
 * <p>A shared place is the classic way two Petri-objects are composed, and it is not an arc:
 * nothing flows along it, the two places simply are the same place.
 *
 * <p>Always shown as a line ({@link #drawBetweenPorts}), one end anchored to whichever port
 * stands for a framed half, the other to a free half's own position, and never moving either
 * place. There used to be a second form for two free places, which stacked one on top of the
 * other and drew a ring round the pair. It was unreachable while two free places could not be
 * linked at all, and once they could it would have been wrong: a source repeated by several
 * places would have piled all of them onto one point, destroying the layout and showing a ring
 * that says nothing about what the place is linked to.
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


    // The ring's two colours come from the palette rather than from constants here, so that a
    // fused place is still legibly green in either theme; see CanvasPalette.


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
        drawBetweenPorts(g2, masterPoint, joinedPoint,
                selected ? CanvasPalette.current().get(CanvasColor.FUSION_RING_SELECTED) : null);
    }

    /**
     * The same line, told which colour to highlight in rather than only whether to - see
     * {@link #draw(Graphics2D, Color)} for why the two highlights are not the same colour.
     *
     * @param g2 canvas graphics
     * @param masterPoint where the master half is drawn
     * @param joinedPoint the same for the joined half
     * @param highlight the colour to draw it highlighted in, or {@code null} for its plain form
     */
    public void drawBetweenPorts(Graphics2D g2, Point masterPoint, Point joinedPoint, Color highlight) {
        Stroke previousStroke = g2.getStroke();
        Color previousColor = g2.getColor();

        CanvasPalette palette = CanvasPalette.current();
        boolean selected = highlight != null;
        float width = selected ? 2.2f : 1.4f;
        g2.setColor(selected ? highlight : palette.get(CanvasColor.ELEMENT_STROKE));
        // Dash-dot, where an informational arc is an even dash. The two used to be told apart
        // only by dash length and by whether the arrowhead was filled - differences of degree,
        // and those are the first thing to go when the canvas is zoomed out, printed or
        // screenshotted. A pattern of a different kind survives all three.
        g2.setStroke(new BasicStroke(width, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10f,
                new float[] {9f, 4f, 2f, 4f}, 0f));
        g2.drawLine(masterPoint.x, masterPoint.y, joinedPoint.x, joinedPoint.y);

        // A filled node on the source end, and no arrowhead at either. An arrowhead promises a
        // flow, and nothing flows along a reference link: the target simply repeats the source.
        // Direction still has to be legible - one end is the source and the other copies it -
        // and the node carries that, being on one end only. It just stops spelling the
        // relationship as movement. With one source repeated by several places the nodes
        // coincide, so the fan reads as one origin rather than as several separate links.
        g2.setStroke(new BasicStroke(width));
        int radius = selected ? 5 : 4;
        g2.fillOval(masterPoint.x - radius, masterPoint.y - radius, radius * 2, radius * 2);

        g2.setStroke(previousStroke);
        g2.setColor(previousColor);
    }

    /**
     * Where the user parked this shared place's boundary stub on an object's own canvas,
     * as an offset from the drawn half's centre - {@code null} until the user drags it,
     * which means "derived: a short stub pointing toward the off-canvas half".
     */
    private java.awt.geom.Point2D.Double boundaryStubOffset;

    public java.awt.geom.Point2D.Double getBoundaryStubOffset() {
        return boundaryStubOffset;
    }

    public void setBoundaryStubOffset(java.awt.geom.Point2D.Double boundaryStubOffset) {
        this.boundaryStubOffset = boundaryStubOffset;
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


    @Override
    public String toString() {
        return master.getName() + " = " + joined.getName();
    }
}

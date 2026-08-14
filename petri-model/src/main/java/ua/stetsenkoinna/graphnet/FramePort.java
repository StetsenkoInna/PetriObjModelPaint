package ua.stetsenkoinna.graphnet;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Stroke;
import java.io.Serializable;
import java.util.Objects;
import ua.stetsenkoinna.theme.CanvasColor;
import ua.stetsenkoinna.theme.CanvasPalette;

/**
 * A small connection point on a Petri-object frame's border, standing in for one of the
 * object's own places or transitions.
 *
 * <p>Once an element belongs to a frame it is locked on the shared canvas — it can only be
 * moved or rewired inside that object's own editor. A port is how the object still takes
 * part in the composition from the outside: it is what a cross-object link is drawn to and
 * from, labelled with the element's name so the connection is legible without opening the
 * object.
 *
 * @see GraphCanvasModel#portsOf(GraphObjectFrame)
 */
public final class FramePort implements Serializable {
    /**
     * Pinned before this class ever reached a saved file, which it does from now on.
     * Left to the compiler it would be recomputed from the class shape, and the next
     * field added here would make every file written before that unreadable.
     */
    private static final long serialVersionUID = 1L;


    /** Radius of the drawn circle and the hit-test tolerance around it, in canvas units. */
    public static final int RADIUS = 6;

    // A port's fill, border, highlight and label plate all come from the palette, so the circle
    // that stands in for a place still reads as a place-shaped hole in either theme.
    private static final Font LABEL_FONT = new Font(Font.SANS_SERIF, Font.PLAIN, 10);

    /** Which side of the frame's bounds the port sits on, used to place its label legibly. */
    public enum Edge { TOP, RIGHT, BOTTOM, LEFT }

    private final GraphElement element;
    private final Point position;
    private final Edge edge;

    public FramePort(GraphElement element, Point position, Edge edge) {
        this.element = Objects.requireNonNull(element, "element");
        this.position = Objects.requireNonNull(position, "position");
        this.edge = Objects.requireNonNull(edge, "edge");
    }

    /**
     * @return the place or transition this port stands in for
     */
    public GraphElement getElement() {
        return element;
    }

    /**
     * @return the port's centre, in canvas coordinates
     */
    public Point getPosition() {
        return position;
    }

    public Edge getEdge() {
        return edge;
    }

    public String getLabel() {
        return element.getName();
    }

    public boolean isPlace() {
        return element instanceof GraphPetriPlace;
    }

    /**
     * @param point a point on the canvas
     * @return true if the point is close enough to count as clicking this port
     */
    public boolean isNear(java.awt.geom.Point2D point) {
        return position.distance(point) <= RADIUS + 3;
    }

    /**
     * Draws the port's circle and its name, the label offset away from the frame on
     * whichever side the port sits on.
     *
     * @param g2 canvas graphics
     * @param highlighted whether the port is the current drag source or target
     */
    public void draw(Graphics2D g2, boolean highlighted) {
        Color previousColor = g2.getColor();
        Stroke previousStroke = g2.getStroke();
        Font previousFont = g2.getFont();

        CanvasPalette palette = CanvasPalette.current();
        g2.setColor(highlighted
                ? palette.get(CanvasColor.PORT_HIGHLIGHT)
                : (isPlace() ? palette.get(CanvasColor.PORT_FILL_PLACE) : palette.get(CanvasColor.PORT_FILL_TRANSITION)));
        g2.fillOval(position.x - RADIUS, position.y - RADIUS, RADIUS * 2, RADIUS * 2);
        g2.setColor(palette.get(CanvasColor.PORT_BORDER));
        g2.setStroke(new BasicStroke(highlighted ? 2f : 1.2f));
        g2.drawOval(position.x - RADIUS, position.y - RADIUS, RADIUS * 2, RADIUS * 2);

        g2.setFont(LABEL_FONT);
        String label = getLabel();
        int labelWidth = g2.getFontMetrics().stringWidth(label);
        int x = switch (edge) {
            case LEFT -> position.x - RADIUS - 4 - labelWidth;
            case RIGHT -> position.x + RADIUS + 4;
            case TOP, BOTTOM -> position.x - labelWidth / 2;
        };
        int y = switch (edge) {
            case TOP -> position.y - RADIUS - 4;
            case BOTTOM -> position.y + RADIUS + 12;
            case LEFT, RIGHT -> position.y + 4;
        };
        g2.setColor(palette.get(CanvasColor.PORT_LABEL_BACKDROP));
        g2.fillRect(x - 2, y - 10, labelWidth + 4, 13);
        g2.setColor(palette.get(CanvasColor.PORT_BORDER));
        g2.drawString(label, x, y);

        g2.setColor(previousColor);
        g2.setStroke(previousStroke);
        g2.setFont(previousFont);
    }
}

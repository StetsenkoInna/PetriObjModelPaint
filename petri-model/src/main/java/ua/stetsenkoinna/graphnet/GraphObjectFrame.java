package ua.stetsenkoinna.graphnet;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.Stroke;
import java.awt.geom.Point2D;
import java.io.Serializable;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Objects;
import java.util.Set;

/**
 * The frame that marks out one Petri-object on the canvas.
 *
 * <p>A Petri-object is not a separate drawing: it is a named region of the one canvas, and
 * whatever belongs to it is drawn inside it. That keeps the structure of a model and the
 * behaviour of its objects in a single picture — a token crossing a frame border is a token
 * crossing an object border.
 *
 * <p>Which elements those are is decided once — grouped into the frame, drawn in the object's
 * own editor, dragged in and confirmed, or loaded as part of it — and stays exactly that set of
 * elements afterward, independent of the frame's own position: {@link #addMember} is the only
 * thing that puts an element in this object, so moving the frame across the canvas can never by
 * itself hand it something it was never given.
 *
 * <p>Collapsing a frame hides the net inside it and leaves the object as a single node, for
 * when a model has grown past what fits on screen.
 */
public class GraphObjectFrame implements Serializable {

    /** Height of the header strip that carries the name, in canvas units. */
    public static final int HEADER_HEIGHT = 22;
    /** Side of the square handle in the bottom-right corner used for resizing. */
    public static final int RESIZE_HANDLE = 12;
    /** Smallest frame the user can resize to. */
    public static final int MIN_WIDTH = 120;
    public static final int MIN_HEIGHT = 80;
    /** Size of the node a collapsed frame shrinks to. */
    public static final int COLLAPSED_WIDTH = 170;
    public static final int COLLAPSED_HEIGHT = HEADER_HEIGHT + 34;

    private static final Color BORDER = new Color(0x33, 0x5A, 0x8A);
    private static final Color BORDER_SELECTED = new Color(0xD9, 0x7A, 0x00);
    private static final Color HEADER = new Color(0xE4, 0xEC, 0xF7);
    private static final Color BODY = new Color(0xF8, 0xFA, 0xFD, 0x80);
    private static final Color TEXT = new Color(0x1C, 0x2B, 0x3A);
    private static final Font NAME_FONT = new Font(Font.SANS_SERIF, Font.BOLD, 12);
    private static final Font DETAIL_FONT = new Font(Font.SANS_SERIF, Font.PLAIN, 11);

    private String name;
    private int priority;
    private Rectangle bounds;
    private boolean collapsed;
    private NetTemplateRef template;

    /** Bounds the frame had before it was collapsed, so expanding restores them. */
    private Rectangle expandedBounds;

    /** The places and transitions this object explicitly claims — see the class doc. */
    private final Set<GraphElement> members = Collections.newSetFromMap(new IdentityHashMap<>());

    /**
     * @param name display name of the Petri-object
     * @param bounds region of the canvas the object occupies
     */
    public GraphObjectFrame(String name, Rectangle bounds) {
        this.name = Objects.requireNonNull(name, "name");
        this.bounds = Objects.requireNonNull(bounds, "bounds");
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = Objects.requireNonNull(name, "name");
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

    public Rectangle getBounds() {
        return bounds;
    }

    public void setBounds(Rectangle bounds) {
        this.bounds = Objects.requireNonNull(bounds, "bounds");
    }

    /**
     * @return the net library method this object was instantiated from, or {@code null}
     */
    public NetTemplateRef getTemplate() {
        return template;
    }

    public void setTemplate(NetTemplateRef template) {
        this.template = template;
    }

    public boolean isCollapsed() {
        return collapsed;
    }

    /**
     * Collapses the frame to a node, or restores the size it had before.
     *
     * @param collapsed true to hide the net inside
     */
    public void setCollapsed(boolean collapsed) {
        if (collapsed == this.collapsed) {
            return;
        }
        if (collapsed) {
            expandedBounds = new Rectangle(bounds);
            bounds = new Rectangle(bounds.x, bounds.y, COLLAPSED_WIDTH, COLLAPSED_HEIGHT);
        } else if (expandedBounds != null) {
            bounds = new Rectangle(expandedBounds);
        } else {
            bounds = new Rectangle(bounds.x, bounds.y,
                    Math.max(bounds.width, MIN_WIDTH * 2), Math.max(bounds.height, MIN_HEIGHT * 2));
        }
        this.collapsed = collapsed;
    }

    /**
     * @param point a point on the canvas
     * @return true if the point falls within this frame's rectangle — a hit test for clicks
     *         and menus, unrelated to which elements this object actually claims
     */
    public boolean contains(Point2D point) {
        return bounds.contains(point.getX(), point.getY());
    }

    /**
     * @param element a place or transition on the canvas
     * @return true if this object claims the element
     */
    public boolean hasMember(GraphElement element) {
        return members.contains(element);
    }

    /**
     * Claims an element for this object. Idempotent.
     *
     * @param element the place or transition to claim
     */
    public void addMember(GraphElement element) {
        members.add(Objects.requireNonNull(element, "element"));
    }

    /**
     * Releases an element — it becomes free unless something else claims it.
     *
     * @param element the place or transition to release
     */
    public void removeMember(GraphElement element) {
        members.remove(element);
    }

    /**
     * @return every element this object currently claims
     */
    public Set<GraphElement> getMembers() {
        return Collections.unmodifiableSet(members);
    }

    /**
     * @return true if the point is on the header strip, which is what the user grabs to move
     *         the frame and double-clicks to rename the object
     */
    public boolean isOnHeader(Point2D point) {
        return point.getX() >= bounds.x && point.getX() <= bounds.x + bounds.width
                && point.getY() >= bounds.y && point.getY() <= bounds.y + HEADER_HEIGHT;
    }

    /**
     * @return true if the point is on the resize handle in the bottom-right corner
     */
    public boolean isOnResizeHandle(Point2D point) {
        if (collapsed) {
            return false;
        }
        return point.getX() >= bounds.x + bounds.width - RESIZE_HANDLE
                && point.getX() <= bounds.x + bounds.width
                && point.getY() >= bounds.y + bounds.height - RESIZE_HANDLE
                && point.getY() <= bounds.y + bounds.height;
    }

    /**
     * Moves the frame, keeping its size.
     */
    public void moveTo(int x, int y) {
        bounds = new Rectangle(Math.max(0, x), Math.max(0, y), bounds.width, bounds.height);
    }

    /**
     * Resizes the frame by its bottom-right corner, never below the minimum.
     */
    public void resizeTo(int right, int bottom) {
        int width = Math.max(MIN_WIDTH, right - bounds.x);
        int height = Math.max(MIN_HEIGHT, bottom - bounds.y);
        bounds = new Rectangle(bounds.x, bounds.y, width, height);
    }

    /**
     * Draws the frame under the net it holds.
     *
     * @param g2 canvas graphics
     * @param index position of the object in the model, shown as {@code O<index>}
     * @param selected whether the frame is the current selection
     * @param elementCount how many places and transitions the frame holds, shown when collapsed
     */
    public void draw(Graphics2D g2, int index, boolean selected, int elementCount) {
        Stroke previousStroke = g2.getStroke();
        Color previousColor = g2.getColor();
        Font previousFont = g2.getFont();

        g2.setColor(BODY);
        g2.fillRoundRect(bounds.x, bounds.y, bounds.width, bounds.height, 14, 14);
        g2.setColor(HEADER);
        g2.fillRoundRect(bounds.x, bounds.y, bounds.width, HEADER_HEIGHT + 8, 14, 14);
        g2.fillRect(bounds.x, bounds.y + HEADER_HEIGHT - 4, bounds.width, 8);

        g2.setColor(selected ? BORDER_SELECTED : BORDER);
        g2.setStroke(new BasicStroke(selected ? 2.4f : 1.4f));
        g2.drawRoundRect(bounds.x, bounds.y, bounds.width, bounds.height, 14, 14);
        g2.drawLine(bounds.x, bounds.y + HEADER_HEIGHT, bounds.x + bounds.width, bounds.y + HEADER_HEIGHT);

        g2.setColor(TEXT);
        g2.setFont(NAME_FONT);
        String title = "O" + index + "  " + name + (collapsed ? "  ▸" : "  ▾");
        g2.drawString(title, bounds.x + 8, bounds.y + 15);

        g2.setFont(DETAIL_FONT);
        String detail = "priority " + priority;
        if (template != null) {
            detail += "  ·  " + template.getMethodName();
        }
        int detailWidth = g2.getFontMetrics().stringWidth(detail);
        g2.drawString(detail, bounds.x + bounds.width - detailWidth - 8, bounds.y + 15);

        if (collapsed) {
            g2.setFont(DETAIL_FONT);
            g2.drawString(elementCount + " elements hidden", bounds.x + 8, bounds.y + HEADER_HEIGHT + 20);
        } else {
            g2.setColor(selected ? BORDER_SELECTED : BORDER);
            g2.fillRect(bounds.x + bounds.width - RESIZE_HANDLE, bounds.y + bounds.height - RESIZE_HANDLE,
                    RESIZE_HANDLE, RESIZE_HANDLE);
        }

        g2.setStroke(previousStroke);
        g2.setColor(previousColor);
        g2.setFont(previousFont);
    }
}

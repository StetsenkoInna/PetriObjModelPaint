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
import java.util.Map;
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
 * <p>Which elements those are is decided once - grouped into the frame, drawn on the object's
 * own canvas, dragged in and confirmed, or loaded as part of it - and stays exactly that set of
 * elements afterward, independent of the frame's own position: {@link GraphCanvasModel#claim}
 * is the only thing that puts an element in this object, so moving the frame across the canvas
 * can never by itself hand it something it was never given. The member set is deliberately
 * written only from {@link GraphCanvasModel}, which is what makes "exactly one frame claims an
 * element" a structural guarantee instead of a convention every caller has to remember.
 *
 * <p>A frame can also sit inside another frame, which is what nesting one Petri-object in
 * another means. That is a frame-to-frame relation ({@link #getEnclosing()}), never membership:
 * {@link #members} only ever holds places and transitions, so an enclosing object's own member
 * set says nothing about the objects nested in it.
 *
 * <p>Collapsing a frame shrinks it down to a small node, for when a model has grown past what
 * fits on screen — a distinct, coarser thing from the eye icon in the header, which only ever
 * hides the net's drawing without changing the frame's own size: what a collapsed frame does to
 * its footprint on the canvas, the eye does to what is painted inside that footprint. Either
 * way an object's own places and transitions still exist and still hold their marking; hiding
 * them is a drawing choice, not a structural one, which is also why a locked object still
 * reaches the rest of the model through its ports regardless of whether its content is shown.
 */
public class GraphObjectFrame implements Serializable, CanvasItem {
    /**
     * Pinned before this class ever reached a saved file, which it does from now on.
     * Left to the compiler it would be recomputed from the class shape, and the next
     * field added here would make every file written before that unreadable.
     */
    private static final long serialVersionUID = 1L;


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
    /** Side of the square eye icon in the header that toggles {@link #isContentVisible()}. */
    public static final int EYE_ICON_SIZE = 14;
    private static final int EYE_ICON_MARGIN = 4;

    private static final Color BORDER = new Color(0x33, 0x5A, 0x8A);
    /** Selected frames get the same green the canvas already uses for selected elements, so
     *  one selection reads as one selection whether it caught elements, frames or both. The
     *  previous dark grey sat too close to {@link #BORDER}'s blue to be noticeable at all. */
    private static final Color BORDER_SELECTED = new Color(0x1E, 0x8E, 0x3E);
    /** Wash over a selected frame's body — the cue that survives being read at a glance,
     *  where a border a fraction of a pixel thicker does not. */
    private static final Color BODY_SELECTED = new Color(0x1E, 0x8E, 0x3E, 0x1F);
    private static final Color HEADER = new Color(0xE4, 0xEC, 0xF7);
    private static final Color HEADER_SELECTED = new Color(0xD8, 0xEF, 0xDC);
    private static final Color BODY = new Color(0xF8, 0xFA, 0xFD, 0x80);
    private static final Color TEXT = new Color(0x1C, 0x2B, 0x3A);
    private static final Font NAME_FONT = new Font(Font.SANS_SERIF, Font.BOLD, 12);
    private static final Font DETAIL_FONT = new Font(Font.SANS_SERIF, Font.PLAIN, 11);

    private String name;
    private int priority;
    private Rectangle bounds;
    private boolean collapsed;
    private boolean contentVisible = true;
    private NetTemplateRef template;

    /** Bounds the frame had before it was collapsed, so expanding restores them. */
    private Rectangle expandedBounds;

    /** The places and transitions this object explicitly claims — see the class doc. */
    private final Set<GraphElement> members = Collections.newSetFromMap(new IdentityHashMap<>());

    /**
     * The object that encloses this one, or {@code null} when it sits directly on the net.
     *
     * <p>Kept on the frame rather than in a side table on {@link GraphCanvasModel} because
     * "what claims me" already lives here, so "what encloses me" belongs next to it, and
     * because the web editor models the same relation the same way (an object frame naming its
     * parent object). Written only by {@link GraphCanvasModel#nest}, which is the one place
     * that can check for a cycle.
     */
    private GraphObjectFrame enclosing;

    /**
     * Border colour a running animation is currently painting this frame with, or
     * {@code null} for none — {@code transient} because it is purely a live "this object is
     * doing something right now" indicator, never part of the model itself.
     */
    private transient Color highlightColor;

    /**
     * @param name display name of the Petri-object
     * @param bounds region of the canvas the object occupies
     */
    public GraphObjectFrame(String name, Rectangle bounds) {
        this.name = Objects.requireNonNull(name, "name");
        this.bounds = Objects.requireNonNull(bounds, "bounds");
    }

    /**
     * Copies another frame's own state — bounds, name, priority, template, collapsed and
     * content-visibility — and translates its membership through {@code oldToNew} so the copy
     * claims the corresponding NEW element instances rather than the ones {@code other}
     * claims. {@code collapsed}/{@code expandedBounds} are copied as plain field values rather
     * than replayed through {@link #setCollapsed}, which computes a derived rectangle and
     * would get the wrong answer fed a bounds/collapsed pair that do not match its own
     * transition history.
     *
     * @param other the frame to copy
     * <p>{@link #enclosing} is deliberately NOT translated here: the frame it would point at
     * may not have been copied yet, so {@link GraphCanvasModel}'s own copy constructor wires
     * every copy's parent in a second pass once all of them exist.
     *
     * @param other the frame to copy
     * @param oldToNew maps every element {@code other} might claim to its already-made copy;
     *        a member with no entry is dropped rather than left dangling on the original
     */
    public GraphObjectFrame(GraphObjectFrame other, Map<GraphElement, GraphElement> oldToNew) {
        this.name = other.name;
        this.priority = other.priority;
        this.bounds = new Rectangle(other.bounds);
        this.collapsed = other.collapsed;
        this.contentVisible = other.contentVisible;
        this.template = other.template;
        this.expandedBounds = other.expandedBounds == null ? null : new Rectangle(other.expandedBounds);
        for (GraphElement oldMember : other.members) {
            GraphElement newMember = oldToNew.get(oldMember);
            if (newMember != null) {
                members.add(newMember);
            }
        }
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
     * @return true if this object's own net is painted; false if only its header, border and
     *         ports are — the elements themselves still exist and still hold their marking
     */
    public boolean isContentVisible() {
        return contentVisible;
    }

    /**
     * Shows or hides this object's net, without touching the frame's size — see the class doc
     * for how this differs from {@link #setCollapsed}.
     *
     * @param contentVisible false to hide the net inside
     */
    public void setContentVisible(boolean contentVisible) {
        this.contentVisible = contentVisible;
    }

    /**
     * @return the colour a running animation currently wants this frame's border painted in,
     *         or {@code null} for its ordinary (selected or not) colour
     */
    public Color getHighlightColor() {
        return highlightColor;
    }

    /**
     * @param highlightColor the animation highlight colour, or {@code null} to clear it
     */
    public void setHighlightColor(Color highlightColor) {
        this.highlightColor = highlightColor;
    }

    /**
     * @return true if this object's own net is actually on screen right now — both the eye
     *         icon has it shown and the frame is not collapsed, since a collapsed frame hides
     *         everything regardless of what the eye says. The one thing to check before
     *         reaching for a place or transition directly instead of through its port.
     */
    public boolean isContentShown() {
        return !collapsed && contentVisible;
    }

    /**
     * @return the eye icon's clickable square in the header, in canvas coordinates
     */
    public Rectangle eyeIconBounds() {
        return new Rectangle(bounds.x + EYE_ICON_MARGIN, bounds.y + (HEADER_HEIGHT - EYE_ICON_SIZE) / 2,
                EYE_ICON_SIZE, EYE_ICON_SIZE);
    }

    /**
     * @param point a point on the canvas
     * @return true if the point is on the header's eye icon, which toggles
     *         {@link #isContentVisible()} — checked ahead of {@link #isOnHeader} so a click on
     *         the icon does not also start dragging the frame
     */
    public boolean isOnEyeIcon(Point2D point) {
        return eyeIconBounds().contains(point.getX(), point.getY());
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
     * @return {@link #contains(Point2D)} under the name every {@link CanvasItem} hit-tests by -
     *         kept as a separate method rather than a rename, since {@code contains} already had
     *         callers of its own before this interface existed
     */
    @Override
    public boolean containsPoint(Point2D point) {
        return contains(point);
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
     * <p>Package-private on purpose: {@link GraphCanvasModel#claim} is the only writer, and it
     * releases whatever claimed the element first. Two frames claiming one element used to be
     * reachable from six different callers, and {@link GraphCanvasModel#ownerOf} then answered
     * with whichever came first in canvas order - so a newly created object could hold nothing
     * as far as every reader was concerned.
     *
     * @param element the place or transition to claim
     */
    void addMember(GraphElement element) {
        members.add(Objects.requireNonNull(element, "element"));
    }

    /**
     * Releases an element — it becomes free unless something else claims it.
     *
     * @param element the place or transition to release
     * @see #addMember for why this is not public
     */
    void removeMember(GraphElement element) {
        members.remove(element);
    }

    /**
     * @return the object this one sits inside, or {@code null} when it sits directly on the net
     */
    public GraphObjectFrame getEnclosing() {
        return enclosing;
    }

    /**
     * @param enclosing the object this one sits inside, or {@code null} to lift it to the top
     *        level. Package-private: {@link GraphCanvasModel#nest} is the writer, since only the
     *        canvas can see whether a parent would close a cycle.
     */
    void setEnclosing(GraphObjectFrame enclosing) {
        this.enclosing = enclosing;
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
     * Moves the frame, keeping its size. The remembered expanded rectangle travels by the
     * same delta: it used to stay behind, so dragging a collapsed object and expanding it
     * snapped the frame back to where it was collapsed while its net, carried by the drag,
     * stayed at the drop point - frame and net permanently separated.
     */
    public void moveTo(int x, int y) {
        Rectangle moved = new Rectangle(Math.max(0, x), Math.max(0, y), bounds.width, bounds.height);
        if (expandedBounds != null) {
            expandedBounds = new Rectangle(
                    expandedBounds.x + moved.x - bounds.x,
                    expandedBounds.y + moved.y - bounds.y,
                    expandedBounds.width, expandedBounds.height);
        }
        bounds = moved;
    }

    /**
     * Moves this frame alone by a delta - never its nested subtree or the elements it claims, the
     * same restriction {@link CanvasItem#moveBy} documents. A drag that must cascade to those
     * still goes through {@code moveTo} directly the way it always did, one call per item in the
     * subtree, since only {@link GraphCanvasModel} knows what that subtree is.
     */
    @Override
    public void moveBy(int dx, int dy) {
        moveTo(bounds.x + dx, bounds.y + dy);
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
        if (selected) {
            g2.setColor(BODY_SELECTED);
            g2.fillRoundRect(bounds.x, bounds.y, bounds.width, bounds.height, 14, 14);
        }
        g2.setColor(selected ? HEADER_SELECTED : HEADER);
        g2.fillRoundRect(bounds.x, bounds.y, bounds.width, HEADER_HEIGHT + 8, 14, 14);
        g2.fillRect(bounds.x, bounds.y + HEADER_HEIGHT - 4, bounds.width, 8);

        g2.setColor(highlightColor != null ? highlightColor : (selected ? BORDER_SELECTED : BORDER));
        g2.setStroke(new BasicStroke(highlightColor != null ? 2.6f : (selected ? 2.4f : 1.4f)));
        g2.drawRoundRect(bounds.x, bounds.y, bounds.width, bounds.height, 14, 14);
        g2.drawLine(bounds.x, bounds.y + HEADER_HEIGHT, bounds.x + bounds.width, bounds.y + HEADER_HEIGHT);

        drawEyeIcon(g2);

        g2.setColor(TEXT);
        g2.setFont(NAME_FONT);
        String title = "O" + index + "  " + name;
        int titleX = bounds.x + EYE_ICON_MARGIN + EYE_ICON_SIZE + 6;
        g2.drawString(title, titleX, bounds.y + 15);

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

    /**
     * Draws the eye icon: an open eye when the content is visible, an eye with a line through
     * it when it is hidden — a plain vector glyph rather than a font character, so it renders
     * identically regardless of what fonts happen to be installed.
     */
    private void drawEyeIcon(Graphics2D g2) {
        Color previousColor = g2.getColor();
        Stroke previousStroke = g2.getStroke();

        Rectangle icon = eyeIconBounds();
        g2.setColor(TEXT);
        g2.setStroke(new BasicStroke(1.3f));
        g2.drawOval(icon.x, icon.y + icon.height / 4, icon.width, icon.height / 2);
        if (contentVisible) {
            int pupil = 4;
            g2.fillOval(icon.x + icon.width / 2 - pupil / 2, icon.y + icon.height / 2 - pupil / 2, pupil, pupil);
        } else {
            g2.drawLine(icon.x, icon.y, icon.x + icon.width, icon.y + icon.height);
        }

        g2.setColor(previousColor);
        g2.setStroke(previousStroke);
    }
}

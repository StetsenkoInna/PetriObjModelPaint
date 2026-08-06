package ua.stetsenkoinna.graphpresentation.objmodel;

import ua.stetsenkoinna.graphnet.GraphPetriObjModel;
import ua.stetsenkoinna.graphnet.GraphPetriObject;
import ua.stetsenkoinna.petriobj.PetriObjLink;

import javax.swing.JPanel;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Line2D;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.List;

/**
 * The structure layer of the editor: Petri-objects as nodes, the links between them as
 * edges.
 *
 * <p>This is where a model is composed. The behaviour of a single object is edited one
 * level down, on the net canvas, which a double click on a node opens.
 */
public class ObjectStructurePanel extends JPanel {

    /** Nothing is selected. */
    public static final int NONE = -1;

    private static final int NODE_WIDTH = 170;
    private static final int NODE_HEIGHT = 74;
    private static final int NODE_ARC = 16;
    private static final int CANVAS_MARGIN = 60;

    private static final Color NODE_FILL = new Color(0xF2, 0xF6, 0xFC);
    private static final Color NODE_BORDER = new Color(0x33, 0x5A, 0x8A);
    private static final Color NODE_SELECTED = new Color(0xFF, 0xE9, 0xA8);
    private static final Color FUSION_COLOR = new Color(0x1B, 0x7F, 0x3B);
    private static final Color OUTPUT_COLOR = new Color(0x1F, 0x4E, 0xA8);
    private static final Color INPUT_COLOR = new Color(0xA8, 0x3A, 0x1F);
    private static final Color LABEL_COLOR = new Color(0x33, 0x33, 0x33);

    private static final Font NAME_FONT = new Font(Font.SANS_SERIF, Font.BOLD, 13);
    private static final Font DETAIL_FONT = new Font(Font.SANS_SERIF, Font.PLAIN, 11);
    private static final Font LINK_FONT = new Font(Font.SANS_SERIF, Font.PLAIN, 10);

    /**
     * Reports what the user did on the structure layer.
     */
    public interface Listener {

        /** A node was double-clicked: its net should be opened for editing. */
        void objectOpened(int index);

        /** The selected object changed; {@link #NONE} when nothing is selected. */
        void selectionChanged(int index);

        /** The model was changed here, e.g. a node was dragged to a new position. */
        void modelChanged();
    }

    private GraphPetriObjModel model;
    private Listener listener;
    private int selected = NONE;

    private int dragged = NONE;
    private Point dragOffset;

    public ObjectStructurePanel() {
        setBackground(Color.WHITE);
        MouseHandler handler = new MouseHandler();
        addMouseListener(handler);
        addMouseMotionListener(handler);
        setPreferredSize(new Dimension(800, 500));
    }

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    public void setModel(GraphPetriObjModel model) {
        this.model = model;
        this.selected = NONE;
        refresh();
    }

    public GraphPetriObjModel getModel() {
        return model;
    }

    /**
     * @return index of the selected object, or {@link #NONE}
     */
    public int getSelected() {
        return selected;
    }

    public void setSelected(int index) {
        selected = index;
        if (listener != null) {
            listener.selectionChanged(selected);
        }
        repaint();
    }

    /**
     * Recomputes the canvas size and repaints — call after the model was changed elsewhere.
     */
    public void refresh() {
        if (model != null && selected >= model.getObjectCount()) {
            selected = NONE;
        }
        setPreferredSize(canvasSize());
        revalidate();
        repaint();
    }

    /**
     * @return a free position for a node that is about to be added, laid out in rows
     */
    public Point nextFreePosition() {
        int count = model == null ? 0 : model.getObjectCount();
        int columns = 3;
        int column = count % columns;
        int row = count / columns;
        return new Point(40 + column * (NODE_WIDTH + 70), 40 + row * (NODE_HEIGHT + 80));
    }

    private Dimension canvasSize() {
        int width = 400;
        int height = 300;
        if (model != null) {
            for (GraphPetriObject object : model.getObjects()) {
                width = Math.max(width, object.getPosition().x + NODE_WIDTH + CANVAS_MARGIN);
                height = Math.max(height, object.getPosition().y + NODE_HEIGHT + CANVAS_MARGIN);
            }
        }
        return new Dimension(width, height);
    }

    private Rectangle boundsOf(int index) {
        Point position = model.getObject(index).getPosition();
        return new Rectangle(position.x, position.y, NODE_WIDTH, NODE_HEIGHT);
    }

    private int objectAt(Point point) {
        if (model == null) {
            return NONE;
        }
        // Walk backwards so the node drawn on top is the one that is picked.
        for (int index = model.getObjectCount() - 1; index >= 0; index--) {
            if (boundsOf(index).contains(point)) {
                return index;
            }
        }
        return NONE;
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        if (model == null) {
            return;
        }
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        for (PetriObjLink link : model.getLinks()) {
            paintLink(g2, link);
        }
        for (int index = 0; index < model.getObjectCount(); index++) {
            paintObject(g2, index);
        }
        g2.dispose();
    }

    private void paintObject(Graphics2D g2, int index) {
        GraphPetriObject object = model.getObject(index);
        Rectangle bounds = boundsOf(index);

        g2.setColor(index == selected ? NODE_SELECTED : NODE_FILL);
        g2.fillRoundRect(bounds.x, bounds.y, bounds.width, bounds.height, NODE_ARC, NODE_ARC);
        g2.setColor(NODE_BORDER);
        g2.setStroke(new BasicStroke(index == selected ? 2.5f : 1.5f));
        g2.drawRoundRect(bounds.x, bounds.y, bounds.width, bounds.height, NODE_ARC, NODE_ARC);

        g2.setFont(NAME_FONT);
        g2.drawString("O" + index + "  " + object.getName(), bounds.x + 10, bounds.y + 20);

        g2.setFont(DETAIL_FONT);
        g2.setColor(LABEL_COLOR);
        g2.drawString(object.getPlaceCount() + " places, " + object.getTransitionCount() + " transitions",
                bounds.x + 10, bounds.y + 38);
        String detail = "priority " + object.getPriority();
        if (object.getTemplate() != null) {
            detail += "  ·  " + object.getTemplate().getMethodName();
        }
        g2.drawString(detail, bounds.x + 10, bounds.y + 54);
    }

    private void paintLink(Graphics2D g2, PetriObjLink link) {
        if (link.getSourceObject() >= model.getObjectCount()
                || link.getTargetObject() >= model.getObjectCount()) {
            return;
        }
        Color color = switch (link.getType()) {
            case PLACE_FUSION -> FUSION_COLOR;
            case TRANSITION_TO_PLACE -> OUTPUT_COLOR;
            case PLACE_TO_TRANSITION -> INPUT_COLOR;
        };
        g2.setColor(color);
        g2.setStroke(link.isInformational()
                ? new BasicStroke(1.6f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 1f,
                        new float[]{6f, 5f}, 0f)
                : new BasicStroke(1.6f));

        Rectangle from = boundsOf(link.getSourceObject());
        Rectangle to = boundsOf(link.getTargetObject());

        if (link.getSourceObject() == link.getTargetObject()) {
            g2.drawArc(from.x + from.width / 4, from.y - 26, from.width / 2, 34, 200, -220);
            drawLabel(g2, describe(link), from.x + from.width / 2, from.y - 30);
            return;
        }

        Point2D start = borderPoint(from, center(to));
        Point2D end = borderPoint(to, center(from));
        g2.draw(new Line2D.Double(start, end));
        drawArrowHead(g2, start, end);
        drawLabel(g2, describe(link),
                (int) ((start.getX() + end.getX()) / 2), (int) ((start.getY() + end.getY()) / 2) - 4);
    }

    private void drawLabel(Graphics2D g2, String text, int x, int y) {
        g2.setFont(LINK_FONT);
        int width = g2.getFontMetrics().stringWidth(text);
        Color stroke = g2.getColor();
        g2.setColor(new Color(255, 255, 255, 220));
        g2.fillRect(x - width / 2 - 3, y - 11, width + 6, 14);
        g2.setColor(stroke);
        g2.drawString(text, x - width / 2, y);
    }

    /**
     * @return a short description of the link, naming the elements it connects
     */
    private String describe(PetriObjLink link) {
        GraphPetriObject source = model.getObject(link.getSourceObject());
        GraphPetriObject target = model.getObject(link.getTargetObject());
        return switch (link.getType()) {
            case PLACE_FUSION -> source.getPlaceName(link.getSourceElement())
                    + " = " + target.getPlaceName(link.getTargetElement());
            case TRANSITION_TO_PLACE -> source.getTransitionName(link.getSourceElement())
                    + " → " + target.getPlaceName(link.getTargetElement())
                    + (link.getQuantity() > 1 ? " ×" + link.getQuantity() : "");
            case PLACE_TO_TRANSITION -> source.getPlaceName(link.getSourceElement())
                    + (link.isInformational() ? " ⇢ " : " → ")
                    + target.getTransitionName(link.getTargetElement())
                    + (link.getQuantity() > 1 ? " ×" + link.getQuantity() : "");
        };
    }

    private static Point2D center(Rectangle bounds) {
        return new Point2D.Double(bounds.getCenterX(), bounds.getCenterY());
    }

    /**
     * @return the point where the line towards {@code towards} leaves the node's rectangle
     */
    private static Point2D borderPoint(Rectangle bounds, Point2D towards) {
        Point2D from = center(bounds);
        double dx = towards.getX() - from.getX();
        double dy = towards.getY() - from.getY();
        if (dx == 0 && dy == 0) {
            return from;
        }
        double scaleX = dx == 0 ? Double.MAX_VALUE : bounds.getWidth() / 2 / Math.abs(dx);
        double scaleY = dy == 0 ? Double.MAX_VALUE : bounds.getHeight() / 2 / Math.abs(dy);
        double scale = Math.min(scaleX, scaleY);
        return new Point2D.Double(from.getX() + dx * scale, from.getY() + dy * scale);
    }

    private static void drawArrowHead(Graphics2D g2, Point2D from, Point2D to) {
        double angle = Math.atan2(to.getY() - from.getY(), to.getX() - from.getX());
        int size = 9;
        int[] xs = {
                (int) to.getX(),
                (int) (to.getX() - size * Math.cos(angle - Math.PI / 7)),
                (int) (to.getX() - size * Math.cos(angle + Math.PI / 7))
        };
        int[] ys = {
                (int) to.getY(),
                (int) (to.getY() - size * Math.sin(angle - Math.PI / 7)),
                (int) (to.getY() - size * Math.sin(angle + Math.PI / 7))
        };
        g2.fillPolygon(xs, ys, 3);
    }

    /**
     * @return the links attached to the given object, for a caller that wants to warn before
     *         removing it
     */
    public List<PetriObjLink> linksOf(int index) {
        List<PetriObjLink> attached = new ArrayList<>();
        if (model == null) {
            return attached;
        }
        for (PetriObjLink link : model.getLinks()) {
            if (link.getSourceObject() == index || link.getTargetObject() == index) {
                attached.add(link);
            }
        }
        return attached;
    }

    private class MouseHandler extends MouseAdapter {

        @Override
        public void mousePressed(MouseEvent e) {
            int index = objectAt(e.getPoint());
            setSelected(index);
            if (index != NONE) {
                dragged = index;
                Point position = model.getObject(index).getPosition();
                dragOffset = new Point(e.getX() - position.x, e.getY() - position.y);
            }
        }

        @Override
        public void mouseReleased(MouseEvent e) {
            if (dragged != NONE) {
                dragged = NONE;
                refresh();
                if (listener != null) {
                    listener.modelChanged();
                }
            }
        }

        @Override
        public void mouseDragged(MouseEvent e) {
            if (dragged == NONE) {
                return;
            }
            int x = Math.max(0, e.getX() - dragOffset.x);
            int y = Math.max(0, e.getY() - dragOffset.y);
            model.getObject(dragged).setPosition(new Point(x, y));
            repaint();
        }

        @Override
        public void mouseClicked(MouseEvent e) {
            if (e.getClickCount() != 2) {
                return;
            }
            int index = objectAt(e.getPoint());
            if (index != NONE && listener != null) {
                listener.objectOpened(index);
            }
        }
    }
}

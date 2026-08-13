package ua.stetsenkoinna.graphpresentation.objmodel;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Insets;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.JToggleButton;

import ua.stetsenkoinna.graphnet.GraphCanvasModel;
import ua.stetsenkoinna.graphnet.GraphObjectFrame;

/**
 * The strip of canvases along the bottom of the canvas area: one pill per open canvas, the
 * active one badged and bold.
 *
 * <p>It replaces the modal window a Petri-object's net used to be edited in. That window could
 * offer Save and Cancel because it knew when editing ended; a canvas the user leaves by clicking
 * another pill, closing this one, starting a simulation or importing a file has no such moment,
 * so there is nothing here that looks like it reverts. What keeps that from surprising anyone is
 * this strip itself: it is permanently on screen with the active level badged and named, so
 * "I am inside Machine" is never a guess.
 *
 * <p>A pill's badge is the object's abstraction level - 0 for the net, 1 for an object on it, 2
 * for one nested in that - and since a canvas is never opened without its enclosing canvases,
 * the pill order is the breadcrumb.
 */
public class CanvasTabsBar extends JPanel {

    /** Name shown for the canvas of the net itself, which is level 0 and never closes. */
    private static final String ROOT_NAME = "Net";

    private static final int CLOSE_BUTTON_SIZE = 12;
    private static final Color BADGE = new Color(0x5A, 0x6B, 0x7D);

    private final CanvasStack stack;
    private final GraphCanvasModel model;

    /** Called with the frame whose pill was clicked, {@code null} for the net's own pill. */
    private final Consumer<GraphObjectFrame> onActivate;

    /** Called with the frame whose close control was clicked. */
    private final Consumer<GraphObjectFrame> onClose;

    /**
     * @param stack the canvases to show; this bar rebuilds itself whenever the stack changes
     * @param model the canvas document, consulted for each object's nesting level
     * @param onActivate what to do when a pill is clicked
     * @param onClose what to do when a pill's close control is clicked
     */
    public CanvasTabsBar(CanvasStack stack, GraphCanvasModel model,
            Consumer<GraphObjectFrame> onActivate, Consumer<GraphObjectFrame> onClose) {
        this.stack = stack;
        this.model = model;
        this.onActivate = onActivate;
        this.onClose = onClose;

        setLayout(new FlowLayout(FlowLayout.LEFT, 4, 3));
        setBackground(new Color(0xEE, 0xEE, 0xEE));
        setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, new Color(0xC8, 0xC8, 0xC8)));
        stack.addChangeListener(this::rebuild);
        rebuild();
    }

    /**
     * Rebuilds the pills from the stack. Cheap enough to do wholesale: a model has a handful of
     * open canvases, and rebuilding avoids having to reconcile a live pill against a frame that
     * may since have been renamed, re-nested or removed.
     */
    public final void rebuild() {
        removeAll();
        ButtonGroup group = new ButtonGroup();
        List<GraphObjectFrame> open = new ArrayList<>(stack.getOpen());
        for (int index = 0; index < open.size(); index++) {
            GraphObjectFrame frame = open.get(index);
            add(pillFor(frame, index == stack.getActiveIndex(), group));
        }
        // Always shown, the net's own pill included. It used to hide itself while a document had
        // no objects, on the reasoning that a strip with one pill navigates nowhere. That reads as
        // a control appearing out of nowhere the first time an object is created, and it leaves
        // the user without the one landmark that says which canvas they are looking at. A strip
        // that is always there is one the user can rely on, and the net's pill is where they came
        // from.
        setVisible(true);
        revalidate();
        repaint();
    }

    private JPanel pillFor(GraphObjectFrame frame, boolean active, ButtonGroup group) {
        JPanel pill = new JPanel(new FlowLayout(FlowLayout.LEFT, 2, 0));
        pill.setOpaque(false);

        JToggleButton button = new JToggleButton(labelFor(frame));
        button.setFocusable(false);
        button.setSelected(active);
        button.setMargin(new Insets(1, 8, 1, 8));
        button.setFont(button.getFont().deriveFont(active ? Font.BOLD : Font.PLAIN));
        button.setToolTipText(frame == null
                ? "The whole net, with every Petri-object drawn on it"
                : "Edit '" + frame.getName() + "' in place");
        button.addActionListener(event -> onActivate.accept(frame));
        group.add(button);
        pill.add(button);

        if (frame != null) {
            JButton close = new JButton("x");
            close.setFocusable(false);
            close.setMargin(new Insets(0, 0, 0, 0));
            close.setBorder(BorderFactory.createEmptyBorder());
            close.setContentAreaFilled(false);
            close.setPreferredSize(new Dimension(CLOSE_BUTTON_SIZE, CLOSE_BUTTON_SIZE));
            close.setForeground(BADGE);
            close.setToolTipText("Close this canvas. Nothing is discarded: every edit is already "
                    + "in the model.");
            close.addActionListener(event -> onClose.accept(frame));
            pill.add(close);
        }
        return pill;
    }

    /**
     * @param frame the object a pill stands for, or {@code null} for the net
     * @return the pill's text: the level badge then the name, so {@code 0 Net},
     *         {@code 1 Machine}, {@code 2 Buffer}
     */
    String labelFor(GraphObjectFrame frame) {
        if (frame == null) {
            return "0  " + ROOT_NAME;
        }
        return model.levelOf(frame) + "  " + frame.getName();
    }
}

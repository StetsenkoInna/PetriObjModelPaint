package ua.stetsenkoinna.graphpresentation;

import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.RenderingHints;

import javax.swing.BorderFactory;
import javax.swing.JToggleButton;

import ua.stetsenkoinna.graphpresentation.theme.ThemeManager;
import ua.stetsenkoinna.graphpresentation.theme.UiPalette;
import ua.stetsenkoinna.theme.CanvasColor;
import ua.stetsenkoinna.theme.CanvasPalette;

/**
 * One choice out of a handful, drawn as a filled pill: the accent behind the chosen one, a muted
 * chip behind the rest.
 *
 * <p>Painted here rather than left to the look and feel, because a look and feel draws a selected
 * toggle as a pressed-in button, and a pressed-in button sitting in a row of four others reads as
 * a button someone left pressed rather than as one choice out of five. It also means the row
 * looks the same under every look and feel this application can be started with, which the
 * header's own colours already have to.
 */
class Chip extends JToggleButton {

    private static final int ARC = 8;

    /** Pixels of width added beyond what the label measures; see {@link #getPreferredSize()}. */
    private static final int SLACK = 6;

    private boolean hovered;

    Chip(String text) {
        super(text);
        setFocusable(false);
        setFocusPainted(false);
        setBorderPainted(false);
        setContentAreaFilled(false);
        setOpaque(false);
        setRolloverEnabled(true);
        setFont(new Font("Arial", Font.PLAIN, 11));
        setMargin(new Insets(3, 8, 3, 8));
        setBorder(BorderFactory.createEmptyBorder(3, 8, 3, 8));
        setAlignmentY(Component.CENTER_ALIGNMENT);
        addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseEntered(java.awt.event.MouseEvent e) {
                hovered = true;
                repaint();
            }

            @Override
            public void mouseExited(java.awt.event.MouseEvent e) {
                hovered = false;
                repaint();
            }
        });
    }

    /**
     * A few pixels wider than the label strictly needs.
     *
     * <p>A button asks for exactly the width its text measures, which leaves nothing in hand: a
     * measurement taken during layout and one taken while painting can differ by a pixel, and a
     * button one pixel short of its own label does not shrink the text, it replaces it with an
     * ellipsis. A row reading "0.5x ... ... ... Max" is what that looks like.
     */
    @Override
    public Dimension getPreferredSize() {
        Dimension size = super.getPreferredSize();
        return new Dimension(size.width + SLACK, size.height);
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        UiPalette palette = ThemeManager.palette();
        boolean chosen = isSelected();
        g2.setColor(chosen ? CanvasPalette.current().get(CanvasColor.ACCENT)
                : hovered ? palette.getDivider() : palette.getChromeAlt());
        g2.fillRoundRect(0, 0, getWidth(), getHeight(), ARC, ARC);
        g2.dispose();
        setForeground(chosen ? Color.WHITE : palette.getChromeText());
        super.paintComponent(g);
    }
}

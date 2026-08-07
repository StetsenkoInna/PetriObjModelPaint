package ua.stetsenkoinna.graphpresentation;

import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.RenderingHints;
import java.awt.geom.RoundRectangle2D;

import javax.swing.Icon;

/**
 * Small vector icons for the toolbar and header controls that no image asset in
 * {@code /ua/stetsenkoinna/img} already covers — the pointer, the marquee (rubber-band)
 * selector, the sidebar's collapse chevron, and the simulation transport buttons. Drawn
 * rather than shipped as files so they stay crisp at whatever size is asked for, never
 * fall out of sync with the UI's color scheme, and never depend on a Unicode glyph being
 * present in whatever font the system happens to have installed.
 */
final class CanvasToolIcons {

    private CanvasToolIcons() {
    }

    static Icon pointer(int size) {
        return new Icon() {
            @Override
            public void paintIcon(Component c, Graphics g, int x, int y) {
                Graphics2D g2 = prepare(g);
                int s = size;
                Polygon arrow = new Polygon(
                        new int[]{x + s * 2 / 10, x + s * 2 / 10, x + s * 8 / 10, x + s * 55 / 100, x + s * 62 / 100, x + s * 45 / 100},
                        new int[]{y + s * 1 / 10, y + s * 85 / 100, y + s * 60 / 100, y + s * 55 / 100, y + s * 90 / 100, y + s * 62 / 100},
                        6);
                g2.setColor(c.getForeground());
                g2.fillPolygon(arrow);
                g2.setColor(c.getBackground());
                g2.drawPolygon(arrow);
                g2.dispose();
            }

            @Override
            public int getIconWidth() {
                return size;
            }

            @Override
            public int getIconHeight() {
                return size;
            }
        };
    }

    static Icon marquee(int size) {
        return new Icon() {
            @Override
            public void paintIcon(Component c, Graphics g, int x, int y) {
                Graphics2D g2 = prepare(g);
                g2.setColor(c.getForeground());
                float inset = size * 0.12f;
                RoundRectangle2D rect = new RoundRectangle2D.Float(
                        x + inset, y + inset, size - 2 * inset, size - 2 * inset, 3, 3);
                g2.setStroke(new java.awt.BasicStroke(1.4f, java.awt.BasicStroke.CAP_ROUND,
                        java.awt.BasicStroke.JOIN_ROUND, 1f, new float[]{2.5f, 2.5f}, 0f));
                g2.draw(rect);
                g2.dispose();
            }

            @Override
            public int getIconWidth() {
                return size;
            }

            @Override
            public int getIconHeight() {
                return size;
            }
        };
    }

    /**
     * A simple "&lt;" / "&gt;" chevron, stroked rather than filled — the sidebar's
     * collapse/expand toggle. Drawn instead of relying on a Unicode triangle glyph
     * ({@code ◀}/{@code ▶}) since not every installed font actually carries those code
     * points, which on some systems silently fell back to a tofu box that reads as an
     * ellipsis at this size.
     *
     * @param size the icon's side length
     * @param pointingLeft true for {@code <} (sidebar collapsed, click to expand it
     *        leftward), false for {@code >} (expanded, click to collapse it rightward)
     */
    static Icon chevron(int size, boolean pointingLeft) {
        return new Icon() {
            @Override
            public void paintIcon(Component c, Graphics g, int x, int y) {
                Graphics2D g2 = prepare(g);
                g2.setColor(c.getForeground());
                g2.setStroke(new java.awt.BasicStroke(1.8f, java.awt.BasicStroke.CAP_ROUND,
                        java.awt.BasicStroke.JOIN_ROUND));
                int nearX = x + size * (pointingLeft ? 68 : 32) / 100;
                int farX = x + size * (pointingLeft ? 32 : 68) / 100;
                int topY = y + size * 22 / 100;
                int midY = y + size * 50 / 100;
                int bottomY = y + size * 78 / 100;
                java.awt.geom.Path2D.Float chevron = new java.awt.geom.Path2D.Float();
                chevron.moveTo(nearX, topY);
                chevron.lineTo(farX, midY);
                chevron.lineTo(nearX, bottomY);
                g2.draw(chevron);
                g2.dispose();
            }

            @Override
            public int getIconWidth() {
                return size;
            }

            @Override
            public int getIconHeight() {
                return size;
            }
        };
    }

    /** Filled square — stop, replacing the {@code ⏹} glyph. */
    static Icon stop(int size) {
        return new Icon() {
            @Override
            public void paintIcon(Component c, Graphics g, int x, int y) {
                Graphics2D g2 = prepare(g);
                g2.setColor(c.getForeground());
                float inset = size * 0.22f;
                g2.fill(new RoundRectangle2D.Float(x + inset, y + inset, size - 2 * inset, size - 2 * inset, 2, 2));
                g2.dispose();
            }

            @Override
            public int getIconWidth() {
                return size;
            }

            @Override
            public int getIconHeight() {
                return size;
            }
        };
    }

    /**
     * A single triangle pointing left — "step back" one event. Plain, no trailing bar, so it
     * doesn't read as "restart from the beginning" — this only ever undoes one event at a
     * time.
     */
    static Icon arrowLeft(int size) {
        return new Icon() {
            @Override
            public void paintIcon(Component c, Graphics g, int x, int y) {
                Graphics2D g2 = prepare(g);
                g2.setColor(c.getForeground());
                g2.fillPolygon(new Polygon(
                        new int[]{x + size * 78 / 100, x + size * 78 / 100, x + size * 22 / 100},
                        new int[]{y + size * 20 / 100, y + size * 80 / 100, y + size * 50 / 100},
                        3));
                g2.dispose();
            }

            @Override
            public int getIconWidth() {
                return size;
            }

            @Override
            public int getIconHeight() {
                return size;
            }
        };
    }

    /**
     * A single triangle pointing right — "step forward" one event. Plain, matching
     * {@link #arrowLeft}'s look now that the pair sits together in the header row.
     */
    static Icon arrowRight(int size) {
        return new Icon() {
            @Override
            public void paintIcon(Component c, Graphics g, int x, int y) {
                Graphics2D g2 = prepare(g);
                g2.setColor(c.getForeground());
                g2.fillPolygon(new Polygon(
                        new int[]{x + size * 22 / 100, x + size * 22 / 100, x + size * 78 / 100},
                        new int[]{y + size * 20 / 100, y + size * 80 / 100, y + size * 50 / 100},
                        3));
                g2.dispose();
            }

            @Override
            public int getIconWidth() {
                return size;
            }

            @Override
            public int getIconHeight() {
                return size;
            }
        };
    }

    /** Two triangles in a row — run to completion, replacing the {@code ⏭} glyph. */
    static Icon fastForward(int size) {
        return new Icon() {
            @Override
            public void paintIcon(Component c, Graphics g, int x, int y) {
                Graphics2D g2 = prepare(g);
                g2.setColor(c.getForeground());
                g2.fillPolygon(new Polygon(
                        new int[]{x + size * 12 / 100, x + size * 12 / 100, x + size * 50 / 100},
                        new int[]{y + size * 22 / 100, y + size * 78 / 100, y + size * 50 / 100},
                        3));
                g2.fillPolygon(new Polygon(
                        new int[]{x + size * 50 / 100, x + size * 50 / 100, x + size * 88 / 100},
                        new int[]{y + size * 22 / 100, y + size * 78 / 100, y + size * 50 / 100},
                        3));
                g2.dispose();
            }

            @Override
            public int getIconWidth() {
                return size;
            }

            @Override
            public int getIconHeight() {
                return size;
            }
        };
    }

    /**
     * One or two letters drawn as an icon — the toolbar button for a Petri-object template,
     * whose identity is its name rather than any shape worth drawing. Painted with the same
     * Java2D machinery as every other icon here rather than set as a button's text, so it
     * inherits the identical sizing, centering and {@link #dimmed} disabled treatment, and so
     * a template whose initial has no glyph in the UI font cannot render as a tofu box.
     *
     * @param letters the one or two characters to draw
     * @param size the icon's side length
     */
    static Icon letter(String letters, int size) {
        return new Icon() {
            @Override
            public void paintIcon(Component c, Graphics g, int x, int y) {
                Graphics2D g2 = prepare(g);
                g2.setColor(c.getForeground());
                // Two letters have to fit the same square one does, so the size is driven by
                // how much text there is rather than fixed.
                float pointSize = size * (letters.length() > 1 ? 0.62f : 0.86f);
                g2.setFont(new java.awt.Font(java.awt.Font.SANS_SERIF, java.awt.Font.BOLD,
                        Math.round(pointSize)));
                java.awt.FontMetrics metrics = g2.getFontMetrics();
                int textX = x + (size - metrics.stringWidth(letters)) / 2;
                // Centre on the cap height rather than the full line height: the descent below
                // the baseline is empty for capitals and would push the letter visibly high.
                int textY = y + (size - metrics.getHeight()) / 2 + metrics.getAscent();
                g2.drawString(letters, textX, textY);
                g2.dispose();
            }

            @Override
            public int getIconWidth() {
                return size;
            }

            @Override
            public int getIconHeight() {
                return size;
            }
        };
    }

    /**
     * Wraps another icon, painting it at reduced opacity. Used as a button's disabled-state
     * icon: Swing can auto-generate a grayscale variant for an {@code ImageIcon}, but not for
     * a plain-drawn {@link Icon} like the ones above, so without this a "disabled" button
     * looks pixel-identical to an enabled one — which reads as broken/unresponsive rather
     * than as blocked.
     */
    static Icon dimmed(Icon icon) {
        return new Icon() {
            @Override
            public void paintIcon(Component c, Graphics g, int x, int y) {
                Graphics2D g2 = prepare(g);
                g2.setComposite(java.awt.AlphaComposite.getInstance(java.awt.AlphaComposite.SRC_OVER, 0.35f));
                icon.paintIcon(c, g2, x, y);
                g2.dispose();
            }

            @Override
            public int getIconWidth() {
                return icon.getIconWidth();
            }

            @Override
            public int getIconHeight() {
                return icon.getIconHeight();
            }
        };
    }

    private static Graphics2D prepare(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        return g2;
    }
}

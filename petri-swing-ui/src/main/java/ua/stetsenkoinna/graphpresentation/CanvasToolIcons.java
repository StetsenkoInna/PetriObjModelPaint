package ua.stetsenkoinna.graphpresentation;

import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.RenderingHints;
import java.awt.geom.RoundRectangle2D;

import javax.swing.Icon;

/**
 * Small vector icons for the two tools the toolbar needs that no image asset in
 * {@code /ua/stetsenkoinna/img} already covers: the plain pointer and the marquee
 * (rubber-band) selector. Drawn rather than shipped as files so they stay crisp at
 * whatever size the toolbar asks for and never fall out of sync with its color scheme.
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

    private static Graphics2D prepare(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        return g2;
    }
}

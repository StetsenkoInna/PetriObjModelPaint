package ua.stetsenkoinna.graphpresentation.welcome;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Component;
import java.awt.Composite;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.io.File;
import java.text.DateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import javax.swing.Icon;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.ListCellRenderer;
import javax.swing.UIManager;
import ua.stetsenkoinna.graphpresentation.theme.ThemeManager;
import ua.stetsenkoinna.graphpresentation.theme.UiPalette;
import ua.stetsenkoinna.recentprojects.RecentProjectEntry;

/**
 * Draws one cell of the welcome screen's card grid: the two pinned action tiles, or a card
 * summarising a previously opened project.
 *
 * <p>A single reused instance, painted by hand in {@link #paintComponent(Graphics)} rather than
 * assembled from nested {@code JLabel}s - the card's text has to be truncated to a pixel budget
 * that only the renderer's own bounds can supply, and a hand-rolled paint keeps that measurement
 * and the drawing that depends on it in the same place instead of split across a layout pass and
 * a paint pass.
 *
 * <p>Colours are read from {@link ThemeManager#palette()} on every paint rather than cached, so a
 * theme change picked up by {@link WelcomeFrame}'s listener repaints this renderer correctly with
 * no extra wiring here.
 */
public class WelcomeCardRenderer extends JPanel implements ListCellRenderer<WelcomeItem> {

    private static final int AVATAR_SIZE = 56;
    private static final int PADDING = 12;

    private final DateFormat dateFormat = DateFormat.getDateInstance(DateFormat.MEDIUM);

    // A fresh renderer is built with every WelcomeFrame (see its class javadoc: "no shared
    // instance"), so these live exactly as long as one welcome-screen session - long enough to
    // avoid re-hashing/re-rendering an identicon and re-stat'ing a project's file on every
    // repaint (every scroll, hover, resize), short enough that a JList model rebuild always
    // starts a session with a clean cache rather than needing manual invalidation.
    private final Map<String, Icon> avatarCache = new HashMap<>();
    private final Map<String, Boolean> fileExistsCache = new HashMap<>();

    private WelcomeItem item;
    private boolean selected;

    public WelcomeCardRenderer() {
        setOpaque(true);
    }

    @Override
    public Component getListCellRendererComponent(JList<? extends WelcomeItem> list, WelcomeItem value,
            int index, boolean isSelected, boolean cellHasFocus) {
        this.item = value;
        this.selected = isSelected;
        setToolTipText(tooltipFor(value));
        return this;
    }

    private String tooltipFor(WelcomeItem value) {
        if (!(value instanceof RecentProjectItem recentItem)) {
            return value instanceof OpenProjectItem
                    ? "Open an existing project file"
                    : "Start a new project";
        }
        RecentProjectEntry entry = recentItem.entry();
        StringBuilder tip = new StringBuilder("<html>");
        tip.append(escape(entry.getName()));
        if (!isFile(entry)) {
            tip.append(" <b>(file not found)</b>");
        }
        tip.append("<br>").append(escape(entry.getPath()));
        if (!entry.getDescription().isBlank()) {
            tip.append("<br><br>").append(escape(entry.getDescription()));
        }
        if (!entry.getAuthors().isBlank()) {
            tip.append("<br><i>").append(escape(entry.getAuthors())).append("</i>");
        }
        tip.append("</html>");
        return tip.toString();
    }

    private static String escape(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private boolean isFile(RecentProjectEntry entry) {
        // Cached per session: a blocking stat on every repaint is a real freeze risk against a
        // disconnected network share or an ejected drive, not just wasted CPU.
        return fileExistsCache.computeIfAbsent(entry.getPath(), path -> new File(path).isFile());
    }

    private Icon avatarFor(RecentProjectEntry entry) {
        return avatarCache.computeIfAbsent(entry.getId(),
                id -> ProjectAvatarGenerator.generate(id, AVATAR_SIZE));
    }

    @Override
    protected void paintComponent(Graphics g) {
        UiPalette palette = ThemeManager.palette();
        int width = getWidth();
        int height = getHeight();

        Graphics2D g2 = (Graphics2D) g.create();
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                    RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

            Color background = selected ? palette.getActiveControl() : palette.getSurface();
            g2.setColor(background);
            g2.fillRoundRect(2, 2, width - 4, height - 4, 14, 14);
            g2.setColor(palette.getDivider());
            g2.drawRoundRect(2, 2, width - 5, height - 5, 14, 14);

            if (item instanceof NewProjectItem) {
                paintActionTile(g2, width, height, palette, true);
            } else if (item instanceof OpenProjectItem) {
                paintActionTile(g2, width, height, palette, false);
            } else if (item instanceof RecentProjectItem recentItem) {
                paintProjectCard(g2, width, height, palette, recentItem.entry());
            }
        } finally {
            g2.dispose();
        }
    }

    private void paintActionTile(Graphics2D g2, int width, int height, UiPalette palette,
            boolean isNew) {
        g2.setColor(palette.getChromeText());

        int glyphSize = 34;
        int cx = width / 2;
        int glyphY = height / 2 - 14;
        g2.setStroke(new java.awt.BasicStroke(3f));
        if (isNew) {
            g2.drawLine(cx - glyphSize / 2, glyphY, cx + glyphSize / 2, glyphY);
            g2.drawLine(cx, glyphY - glyphSize / 2, cx, glyphY + glyphSize / 2);
        } else {
            int fw = glyphSize + 4;
            int fh = glyphSize - 6;
            int fx = cx - fw / 2;
            int fy = glyphY - fh / 2;
            g2.drawRoundRect(fx, fy, fw, fh, 6, 6);
            g2.drawLine(fx + 4, fy, fx + fw / 3, fy - 8);
            g2.drawLine(fx + fw / 3, fy - 8, fx + fw / 2, fy);
        }

        String caption = isNew ? "New Project" : "Open Project";
        Font font = getFont().deriveFont(Font.BOLD, 13f);
        g2.setFont(font);
        FontMetrics fm = g2.getFontMetrics();
        int textWidth = fm.stringWidth(caption);
        g2.drawString(caption, cx - textWidth / 2, height / 2 + 28);
    }

    private void paintProjectCard(Graphics2D g2, int width, int height, UiPalette palette,
            RecentProjectEntry entry) {
        boolean missing = !isFile(entry);
        Color background = selected ? palette.getActiveControl() : palette.getSurface();
        Color textColor = palette.getChromeText();
        Color mutedColor = mix(textColor, background, 0.45f);

        Composite originalComposite = g2.getComposite();
        if (missing) {
            g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.5f));
        }

        int cx = width / 2;
        int y = PADDING;

        Icon avatar = avatarFor(entry);
        avatar.paintIcon(this, g2, cx - AVATAR_SIZE / 2, y);
        y += AVATAR_SIZE + 8;

        int textWidth = width - 2 * PADDING;

        Font nameFont = getFont().deriveFont(Font.BOLD, 13f);
        y = drawCenteredLine(g2, entry.getName(), nameFont, textColor, cx, y, textWidth);

        Font bodyFont = getFont().deriveFont(Font.PLAIN, 11.5f);
        if (!entry.getDescription().isBlank()) {
            y = drawCenteredLine(g2, entry.getDescription(), bodyFont, textColor, cx, y + 2,
                    textWidth);
        }
        if (!entry.getAuthors().isBlank()) {
            y = drawCenteredLine(g2, entry.getAuthors(), bodyFont, mutedColor, cx, y + 2,
                    textWidth);
        }

        Font dateFont = getFont().deriveFont(Font.PLAIN, 10.5f);
        y = drawCenteredLine(g2, "Edited " + dateFormat.format(new Date(entry.getLastEditedAt())),
                dateFont, mutedColor, cx, y + 4, textWidth);
        // Skipped when the file is missing: the "(file not found)" badge below is drawn at a
        // fixed offset from the bottom of the card, and a full five lines of text (name,
        // description, authors, edited, created) can otherwise run into it.
        if (!missing) {
            drawCenteredLine(g2, "Created " + dateFormat.format(new Date(entry.getCreatedAt())),
                    dateFont, mutedColor, cx, y, textWidth);
        }

        g2.setComposite(originalComposite);

        if (missing) {
            Color warn = UIManager.getColor("nimbusRed");
            if (warn == null) {
                warn = new Color(0xC7, 0x54, 0x50);
            }
            Font warnFont = getFont().deriveFont(Font.BOLD, 10.5f);
            drawCenteredLine(g2, "(file not found)", warnFont, warn, cx, height - PADDING - 12,
                    textWidth);
        }
    }

    /**
     * Draws {@code text} centred on {@code cx}, truncating with an ellipsis if it does not fit in
     * {@code maxWidth}.
     *
     * @return the y coordinate just below the line just drawn, for the caller to stack the next
     *         one under it
     */
    private int drawCenteredLine(Graphics2D g2, String text, Font font, Color color, int cx, int y,
            int maxWidth) {
        g2.setFont(font);
        g2.setColor(color);
        FontMetrics fm = g2.getFontMetrics();
        String toDraw = truncate(fm, text, maxWidth);
        int textWidth = fm.stringWidth(toDraw);
        int baseline = y + fm.getAscent();
        g2.drawString(toDraw, cx - textWidth / 2, baseline);
        return baseline + fm.getDescent() + fm.getLeading();
    }

    private static String truncate(FontMetrics fm, String text, int maxWidth) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        if (fm.stringWidth(text) <= maxWidth) {
            return text;
        }
        String ellipsis = "...";
        int budget = maxWidth - fm.stringWidth(ellipsis);
        if (budget <= 0) {
            return ellipsis;
        }
        StringBuilder kept = new StringBuilder();
        int width = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            width += fm.charWidth(c);
            if (width > budget) {
                break;
            }
            kept.append(c);
        }
        return kept + ellipsis;
    }

    private static Color mix(Color foreground, Color background, float towardBackground) {
        int r = (int) (foreground.getRed() * (1 - towardBackground)
                + background.getRed() * towardBackground);
        int g = (int) (foreground.getGreen() * (1 - towardBackground)
                + background.getGreen() * towardBackground);
        int b = (int) (foreground.getBlue() * (1 - towardBackground)
                + background.getBlue() * towardBackground);
        return new Color(r, g, b);
    }
}

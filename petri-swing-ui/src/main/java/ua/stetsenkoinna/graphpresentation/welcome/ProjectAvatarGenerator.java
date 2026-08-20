package ua.stetsenkoinna.graphpresentation.welcome;

import javax.swing.Icon;
import javax.swing.ImageIcon;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Deterministic, GitHub-identicon-style avatar generator for project cards.
 *
 * <p>The same seed always renders the same byte-identical image, on every call and every JVM
 * run: the seed is hashed with SHA-256, one byte of the hash picks a hue, and a run of
 * subsequent bits fills a 5x5 grid with left-right mirror symmetry (the classic identicon
 * shape). The background is left fully transparent so the mark can sit on any card
 * background.
 */
public final class ProjectAvatarGenerator {

    private static final int GRID_SIZE = 5;
    private static final int SYMMETRIC_COLUMNS = 3;
    private static final float SATURATION = 0.55f;
    private static final float BRIGHTNESS = 0.85f;

    private ProjectAvatarGenerator() {
    }

    /**
     * Renders a {@code size x size} ARGB image with a fully transparent background and a
     * deterministic symmetric pixel-grid pattern in the foreground.
     *
     * @param seed arbitrary text identifying the project; same seed -&gt; same image
     * @param size width and height, in pixels, of the square image to render
     * @return a new {@code TYPE_INT_ARGB} image
     */
    public static BufferedImage renderImage(String seed, int size) {
        byte[] hash = sha256(seed);

        float hue = (hash[0] & 0xFF) / 255f;
        int rgb = Color.HSBtoRGB(hue, SATURATION, BRIGHTNESS);
        Color foreground = new Color(rgb);

        boolean[][] activeByRowThenColumn = buildSymmetricGrid(hash);

        BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = image.createGraphics();
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(foreground);

            int cellSize = size / GRID_SIZE;
            int arc = Math.max(2, cellSize / 4);
            for (int row = 0; row < GRID_SIZE; row++) {
                for (int col = 0; col < GRID_SIZE; col++) {
                    if (!activeByRowThenColumn[row][col]) {
                        continue;
                    }
                    int x = col * cellSize;
                    int y = row * cellSize;
                    // Give the last row/column any leftover pixels so the pattern always
                    // reaches the far edge of the image regardless of rounding.
                    int width = (col == GRID_SIZE - 1) ? size - x : cellSize;
                    int height = (row == GRID_SIZE - 1) ? size - y : cellSize;
                    g2.fillRoundRect(x, y, width, height, arc, arc);
                }
            }
        } finally {
            g2.dispose();
        }
        return image;
    }

    /**
     * Convenience wrapper for Swing UI code that just wants an {@link Icon} (e.g.
     * {@code new JLabel(icon)}).
     */
    public static Icon generate(String seed, int size) {
        return new ImageIcon(renderImage(seed, size));
    }

    /**
     * Builds a {@code [row][col]} grid of active/inactive cells with left-right mirror
     * symmetry: columns 0-2 are decided from hash bits, column 3 mirrors column 1 and
     * column 4 mirrors column 0.
     */
    private static boolean[][] buildSymmetricGrid(byte[] hash) {
        boolean[][] grid = new boolean[GRID_SIZE][GRID_SIZE];
        int bitCursor = 0; // bit index into hash, starting at byte 1 (byte 0 fed the hue)
        for (int row = 0; row < GRID_SIZE; row++) {
            for (int col = 0; col < SYMMETRIC_COLUMNS; col++) {
                int byteIndex = 1 + (bitCursor / 8);
                int bitIndex = bitCursor % 8;
                boolean active = ((hash[byteIndex] >> bitIndex) & 1) == 1;
                grid[row][col] = active;
                bitCursor++;
            }
            grid[row][3] = grid[row][1];
            grid[row][4] = grid[row][0];
        }
        return grid;
    }

    private static byte[] sha256(String seed) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return digest.digest(seed.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is guaranteed to be available on every standard JRE.
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }
}

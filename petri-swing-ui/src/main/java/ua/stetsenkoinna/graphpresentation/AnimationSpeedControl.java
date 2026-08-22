package ua.stetsenkoinna.graphpresentation;

import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Insets;
import java.util.ArrayList;
import java.util.List;

import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;

import javax.swing.AbstractButton;
import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.JPanel;
import javax.swing.JToggleButton;

import ua.stetsenkoinna.graphpresentation.theme.ThemeManager;
import ua.stetsenkoinna.graphpresentation.theme.UiPalette;
import ua.stetsenkoinna.theme.CanvasColor;
import ua.stetsenkoinna.theme.CanvasPalette;

/**
 * How fast the animation plays, as the web editor states it: a mode, and a row of named speeds
 * to pick from.
 *
 * <p>This replaces a bare slider whose value was a sleep in milliseconds between one fired
 * transition and the next. A slider says nothing about what it is measuring - the two ends were
 * "slow" and "fast" and every position in between was a number the user could not name - and
 * milliseconds-per-event is not a speed anyone reasons about while watching a model of a
 * factory or a queue. Naming the speeds instead makes each one answerable: "20 events a second"
 * or "one simulated hour per second" is a thing you can want.
 *
 * <h3>The two modes</h3>
 * They differ in what the animation is paced by, which is the same split the web editor draws
 * between "Scientific" and "Visual":
 *
 * <ul>
 *   <li><b>Scientific</b> paces by <em>events</em>: every fired transition is held on screen for
 *       the same length of time, however much or little simulated time it took. This is what the
 *       slider always did, and it is the default here for that reason - it is the mode for
 *       watching the logic of a net, where a transition that fires in zero time matters exactly
 *       as much as one that takes an hour.</li>
 *   <li><b>Visual</b> paces by <em>simulated time</em>: a run plays back at a chosen ratio of
 *       simulated time to real time, so a delay of an hour actually takes longer on screen than
 *       one of a minute. This is the mode for watching a model behave the way the thing it
 *       models would.</li>
 * </ul>
 *
 * <p>Both are expressed to the animation through one question - {@link
 * #sleepMillisAfterStep(double)}, "given that the model just advanced this far, how long should
 * this step be held?" - so the simulator itself does not know which mode is in force.
 */
public class AnimationSpeedControl extends JPanel {

    /** What the animation is paced by. */
    public enum Mode {
        /** A fixed length of real time per fired transition. */
        SCIENTIFIC,
        /** A fixed ratio of simulated time to real time. */
        VISUAL
    }

    /**
     * One named speed on the row.
     *
     * @param label what the button says
     * @param tooltip the longer reading of it
     * @param value events per second in {@link Mode#SCIENTIFIC}, simulation units per second in
     *        {@link Mode#VISUAL}; zero means "as fast as the model runs"
     */
    private record Speed(String label, String tooltip, double value) {
    }

    /**
     * Events per real second. The slowest is one a second, which is exactly where the slider
     * used to start, so an existing habit lands on the same pace it always did.
     */
    private static final List<Speed> SCIENTIFIC_SPEEDS = List.of(
            new Speed("1/s", "1 event per second", 1),
            new Speed("5/s", "5 events per second", 5),
            new Speed("20/s", "20 events per second", 20),
            new Speed("100/s", "100 events per second", 100),
            new Speed("Max", "As fast as the model runs", 0));

    /**
     * Simulation units per real second, with one unit read as one second - the same five ratios
     * the web editor offers, and the same labels, so a model watched in one editor plays at a
     * recognisably equal speed in the other.
     */
    private static final List<Speed> VISUAL_SPEEDS = List.of(
            new Speed("1 s/s", "1 simulated second per second", 1),
            new Speed("10 s/s", "10 simulated seconds per second", 10),
            new Speed("1 min/s", "1 simulated minute per second", 60),
            new Speed("10 min/s", "10 simulated minutes per second", 600),
            new Speed("1 h/s", "1 simulated hour per second", 3600));

    /** Longest a single step is ever held, so a slow ratio cannot look like a hang. */
    private static final long MAX_STEP_SLEEP_MILLIS = 5_000;

    /** Bounds on how often the canvas is repainted while a run is in progress. */
    private static final int MIN_REPAINT_MILLIS = 30;
    private static final int MAX_REPAINT_MILLIS = 250;

    private final JPanel modeGroup = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
    private final JPanel speedGroup = new JPanel(new FlowLayout(FlowLayout.LEFT, 2, 0));
    private final Chip visualButton = new Chip("Visual");
    private final Chip scientificButton = new Chip("Scientific");

    /**
     * Read by the animation thread while the user clicks on the event dispatch thread, so both
     * are volatile rather than guarded: a speed change is meant to take effect on the next step
     * of a run already in progress, which is the whole point of choosing one mid-run.
     */
    private volatile Mode mode = Mode.SCIENTIFIC;
    private volatile double speed = SCIENTIFIC_SPEEDS.get(0).value();

    private final List<Runnable> listeners = new ArrayList<>();

    public AnimationSpeedControl() {
        super(new FlowLayout(FlowLayout.LEFT, 6, 0));
        setOpaque(false);

        modeGroup.setOpaque(false);
        speedGroup.setOpaque(false);

        ButtonGroup modes = new ButtonGroup();
        for (Chip button : new Chip[] {visualButton, scientificButton}) {
            modes.add(button);
            modeGroup.add(button);
        }
        visualButton.setToolTipText("Play back at a ratio of simulated time to real time");
        scientificButton.setToolTipText("Hold every event on screen for the same length of time");
        visualButton.addActionListener(e -> setMode(Mode.VISUAL));
        scientificButton.addActionListener(e -> setMode(Mode.SCIENTIFIC));

        add(modeGroup);
        add(speedGroup);

        applyMode(Mode.SCIENTIFIC);
        applyTheme();
    }

    /**
     * @return what the animation is paced by right now
     */
    public Mode getMode() {
        return mode;
    }

    /**
     * Switches mode, and with it the row of speeds. Each mode remembers nothing: it comes back
     * on its own default, because its numbers mean something different from the other's and
     * carrying a position across would be carrying a number that no longer applies.
     *
     * @param newMode the mode to switch to
     */
    public final void setMode(Mode newMode) {
        if (newMode == mode && speedGroup.getComponentCount() > 0) {
            return;
        }
        applyMode(newMode);
        notifyListeners();
    }

    private void applyMode(Mode newMode) {
        mode = newMode;
        (newMode == Mode.VISUAL ? visualButton : scientificButton).setSelected(true);

        speedGroup.removeAll();
        List<Speed> speeds = speedsFor(newMode);
        ButtonGroup group = new ButtonGroup();
        for (Speed option : speeds) {
            Chip button = new Chip(option.label());
            button.setToolTipText(option.tooltip());
            button.addActionListener(e -> {
                speed = option.value();
                applyTheme();
                notifyListeners();
            });
            group.add(button);
            speedGroup.add(button);
        }
        // Each mode's own starting speed: the slowest in Scientific, which is where the slider
        // used to start, and a simulated minute per second in Visual, which is the ratio the
        // web editor opens on.
        int initial = newMode == Mode.VISUAL ? 2 : 0;
        speed = speeds.get(initial).value();
        ((AbstractButton) speedGroup.getComponent(initial)).setSelected(true);

        applyTheme();
        revalidate();
        repaint();
    }

    private static List<Speed> speedsFor(Mode mode) {
        return mode == Mode.VISUAL ? VISUAL_SPEEDS : SCIENTIFIC_SPEEDS;
    }

    /**
     * How long the step that just finished should be held on screen before the next one runs.
     *
     * <p>This is the one question the animation asks, and both modes answer it - which is why
     * the simulator never learns which mode is in force.
     *
     * @param simTimeAdvanced how much simulated time that step took; ignored in {@link
     *        Mode#SCIENTIFIC}, where every event is held equally long however long it took
     * @return milliseconds to sleep, never negative and never long enough to read as a hang
     */
    public long sleepMillisAfterStep(double simTimeAdvanced) {
        double rate = speed;
        if (rate <= 0) {
            // "Max": no pacing at all, the model runs as fast as it can and the canvas keeps up
            // as best it can.
            return 0;
        }
        double millis = mode == Mode.VISUAL
                // A step that advanced no simulated time at all - an immediate transition -
                // takes no time to watch either, which is what pacing by simulated time means.
                ? Math.max(0, simTimeAdvanced) / rate * 1000.0
                : 1000.0 / rate;
        return (long) Math.min(MAX_STEP_SLEEP_MILLIS, Math.max(0, Math.round(millis)));
    }

    /**
     * @return how often the canvas should repaint itself while a run is in progress. Tied to the
     *         chosen speed for the same reason it was tied to the slider: a fast animation whose
     *         canvas repaints four times a second is a slideshow, and a slow one repainting
     *         sixty times a second is that much work for nothing to have changed.
     */
    public int repaintIntervalMillis() {
        if (speed <= 0) {
            return MIN_REPAINT_MILLIS;
        }
        long step = mode == Mode.VISUAL
                // A visual run's step length depends on the model, so its repaint interval is
                // pinned to the ratio instead: the faster simulated time runs, the more often
                // something on screen has moved.
                ? Math.round(1000.0 / Math.max(1, speed / 60.0))
                : sleepMillisAfterStep(0);
        return (int) Math.min(MAX_REPAINT_MILLIS, Math.max(MIN_REPAINT_MILLIS, step));
    }

    /**
     * @param listener run whenever the mode or the speed changes - what the frame uses to keep
     *        the canvas repaint timer in step with the chosen pace
     */
    public void addChangeListener(Runnable listener) {
        listeners.add(listener);
    }

    private void notifyListeners() {
        for (Runnable listener : listeners) {
            listener.run();
        }
    }

    /**
     * One speed, or one mode, drawn as a filled pill: the accent behind the chosen one, a muted
     * chip behind the rest.
     *
     * <p>Painted here rather than left to the look and feel, because a look and feel draws a
     * selected toggle as a pressed-in button, and a pressed-in button sitting in a row of four
     * others reads as a button someone left pressed rather than as one choice out of five. It
     * also means the row looks the same under every look and feel this application can be
     * started with, which the header's own colours already have to.
     */
    private static final class Chip extends JToggleButton {

        private static final int ARC = 8;

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

    /**
     * Re-reads the theme. The chips take their colours at paint time, so this only has to ask
     * for a repaint - and re-draw the hairline around the mode pair, which is a border rather
     * than something painted.
     */
    public final void applyTheme() {
        modeGroup.setBorder(BorderFactory.createLineBorder(ThemeManager.palette().getDivider()));
        repaint();
    }

    @Override
    public Dimension getMaximumSize() {
        return getPreferredSize();
    }
}

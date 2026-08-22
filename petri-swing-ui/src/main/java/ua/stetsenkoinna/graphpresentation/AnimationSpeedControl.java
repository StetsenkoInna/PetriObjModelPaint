package ua.stetsenkoinna.graphpresentation;

import java.awt.Dimension;
import java.awt.FlowLayout;
import java.util.ArrayList;
import java.util.List;

import javax.swing.AbstractButton;
import javax.swing.ButtonGroup;
import javax.swing.JPanel;

/**
 * How fast a run is played back, as a row of named speeds.
 *
 * <p>This replaces a slider whose value was a sleep in milliseconds between one fired transition
 * and the next. A slider says nothing about what it is measuring - its two ends were "slow" and
 * "fast" and every position between them was a number nobody could name - so the same pacing is
 * offered as a handful of speeds instead, each one a multiple of how the animation has always
 * played. 1x is exactly that: the frame delays the canvas was drawn with, and the pause between
 * events the slider opened on.
 *
 * <p>One kind of speed, not two. An earlier version of this offered a second mode that paced by
 * simulated time rather than by events, so an hour's delay took longer to watch than a minute's.
 * It is a real distinction and the web editor draws it, but it is not what this application's
 * animation has ever meant: here every fired transition is one thing to look at, however much of
 * the model's clock it consumed, and a second mode beside the first mostly raised the question of
 * which one you were in.
 *
 * <p>The speed governs the whole of a step, not just the pause at the end of it. Lighting one
 * firing up - its places, its arcs, the transition itself - is most of what a step costs, so a
 * speed that left that alone barely changed anything: see {@link #pulseFrameMillis(long)}.
 */
public class AnimationSpeedControl extends JPanel {

    /**
     * One named speed.
     *
     * @param label what the chip says
     * @param tooltip the longer reading of it
     * @param factor how many times faster than the animation's own pace; zero means no pacing
     *        at all
     */
    private record Speed(String label, String tooltip, double factor) {
    }

    /**
     * Multiples of the pace the animation was drawn to run at. Slower as well as faster: the
     * frame delays it was given are brisk, and a net being explained to somebody is worth
     * watching at half of them.
     */
    private static final List<Speed> SPEEDS = List.of(
            new Speed("0.5x", "Half speed", 0.5),
            new Speed("1x", "Normal speed", 1),
            new Speed("2x", "Twice as fast", 2),
            new Speed("5x", "Five times as fast", 5),
            new Speed("Max", "As fast as the model runs, with no pauses at all", 0));

    /** Which of them the editor opens on: the pace the animation has always played at. */
    private static final int NORMAL_INDEX = 1;

    /**
     * The pause held after each fired transition at 1x, in milliseconds. This is where the
     * slider's own default sat, so an editor opened and left alone animates exactly as it did
     * before there was anything to choose.
     */
    private static final long NORMAL_PAUSE_MILLIS = 1000;

    /** Bounds on how often the canvas is repainted while a run is in progress. */
    private static final int MIN_REPAINT_MILLIS = 30;
    private static final int MAX_REPAINT_MILLIS = 250;

    private final JPanel speedGroup = new JPanel(new FlowLayout(FlowLayout.LEFT, 2, 0));

    /**
     * Read by the animation thread while the user clicks on the event dispatch thread, so it is
     * volatile rather than guarded: a speed change is meant to take effect on the next step of a
     * run already in progress, which is the whole point of choosing one mid-run.
     */
    private volatile double factor = SPEEDS.get(NORMAL_INDEX).factor();

    private final List<Runnable> listeners = new ArrayList<>();

    public AnimationSpeedControl() {
        super(new FlowLayout(FlowLayout.LEFT, 0, 0));
        setOpaque(false);
        speedGroup.setOpaque(false);
        add(speedGroup);

        ButtonGroup group = new ButtonGroup();
        for (Speed option : SPEEDS) {
            Chip chip = new Chip(option.label());
            chip.setToolTipText(option.tooltip());
            chip.addActionListener(e -> {
                factor = option.factor();
                notifyListeners();
            });
            group.add(chip);
            speedGroup.add(chip);
        }
        ((AbstractButton) speedGroup.getComponent(NORMAL_INDEX)).setSelected(true);
    }

    /**
     * Scales one frame of the pulse that lights up a firing, so the highlight speeds up and slows
     * down with everything else.
     *
     * <p>Without this the speed row barely did anything. The pulse's frame delays are written
     * into the canvas's animation as fixed numbers, and lighting one firing through them costs
     * the better part of three seconds - so every speed played at about one event every three
     * seconds, the pause between them being the only thing a speed ever changed.
     *
     * @param nominalMillis the frame delay the animation asks for
     * @return what to actually wait: the same at 1x, twice as long at 0.5x, nothing at Max
     */
    public long pulseFrameMillis(long nominalMillis) {
        double rate = factor;
        return rate <= 0 ? 0 : Math.max(0, Math.round(nominalMillis / rate));
    }

    /**
     * @return how long to pause once a fired transition has finished being lit, in milliseconds
     */
    public long stepPauseMillis() {
        double rate = factor;
        return rate <= 0 ? 0 : Math.max(0, Math.round(NORMAL_PAUSE_MILLIS / rate));
    }

    /**
     * @return how often the canvas should repaint itself while a run is in progress. Tied to the
     *         chosen speed for the same reason it was tied to the slider: a fast animation whose
     *         canvas repaints four times a second is a slideshow, and a slow one repainting sixty
     *         times a second is that much work for nothing to have changed.
     */
    public int repaintIntervalMillis() {
        long pause = stepPauseMillis();
        return (int) Math.min(MAX_REPAINT_MILLIS, Math.max(MIN_REPAINT_MILLIS, pause / 4));
    }

    /**
     * Picks the fastest speed on the row, exactly as clicking its last chip would - so the row's
     * own highlight moves with it rather than saying one thing while the animation does another.
     */
    public void selectFastestSpeed() {
        ((AbstractButton) speedGroup.getComponent(SPEEDS.size() - 1)).doClick();
    }

    /**
     * Picks a speed by the label its chip carries, again by clicking it, so the row stays honest
     * about what is in force.
     *
     * @param label one of the labels on the row
     * @throws IllegalArgumentException if no chip carries that label
     */
    public void selectSpeed(String label) {
        for (java.awt.Component chip : speedGroup.getComponents()) {
            if (((AbstractButton) chip).getText().equals(label)) {
                ((AbstractButton) chip).doClick();
                return;
            }
        }
        throw new IllegalArgumentException("No such speed: " + label);
    }

    /**
     * @param listener run whenever the speed changes - what the frame uses to keep the canvas
     *        repaint timer in step with the chosen pace
     */
    public void addChangeListener(Runnable listener) {
        listeners.add(listener);
    }

    private void notifyListeners() {
        for (Runnable listener : listeners) {
            listener.run();
        }
    }

    /** Re-reads the theme. The chips take their colours at paint time, so a repaint is all. */
    public void applyTheme() {
        repaint();
    }

    @Override
    public Dimension getMaximumSize() {
        return getPreferredSize();
    }
}

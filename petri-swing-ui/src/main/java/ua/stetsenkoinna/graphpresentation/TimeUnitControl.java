package ua.stetsenkoinna.graphpresentation;

import java.awt.Dimension;
import java.awt.FlowLayout;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import javax.swing.AbstractButton;
import javax.swing.ButtonGroup;
import javax.swing.JPanel;

/**
 * What one tick of the model's clock stands for, as a row of chips.
 *
 * <p>A drop-down before, and it could not be used at all: opening the list repainted the canvas
 * behind it, the canvas took the keyboard focus back on every paint, and a list without focus
 * closes itself. That is fixed where it belonged, in the canvas, but the row it sits in is four
 * choices long and reads better beside the speeds as the same kind of thing - a small set, all
 * of it visible, one of them lit.
 */
public class TimeUnitControl extends JPanel {

    private final List<Consumer<TimeUnitScale>> listeners = new ArrayList<>();

    private TimeUnitScale scale = TimeUnitScale.SECONDS;

    public TimeUnitControl() {
        super(new FlowLayout(FlowLayout.LEFT, 2, 0));
        setOpaque(false);

        ButtonGroup group = new ButtonGroup();
        for (TimeUnitScale option : TimeUnitScale.values()) {
            Chip chip = new Chip(option.chipLabel());
            chip.setToolTipText(option.toString());
            chip.addActionListener(e -> {
                scale = option;
                for (Consumer<TimeUnitScale> listener : listeners) {
                    listener.accept(option);
                }
            });
            group.add(chip);
            add(chip);
            if (option == scale) {
                chip.setSelected(true);
            }
        }
    }

    /**
     * @return what one tick of the model's clock is currently taken to stand for
     */
    public TimeUnitScale getScale() {
        return scale;
    }

    /**
     * @param listener told whenever the choice changes
     */
    public void addChangeListener(Consumer<TimeUnitScale> listener) {
        listeners.add(listener);
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

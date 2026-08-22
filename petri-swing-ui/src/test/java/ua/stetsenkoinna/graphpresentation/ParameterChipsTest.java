package ua.stetsenkoinna.graphpresentation;

import org.junit.Test;

import javax.swing.AbstractButton;
import java.awt.Component;
import java.awt.Container;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;

/**
 * The two chip rows in the parameters bar explain themselves by being read, not by being
 * hovered over.
 *
 * <p>Each is a short row of short labels, sitting next to a reading that says what choosing one
 * does and a question mark that says why. A popup appearing over each chip on the way to
 * clicking the next one added nothing to that and got in the way of it.
 */
public class ParameterChipsTest {

    @Test
    public void noSpeedChipPopsUpAnExplanation() {
        for (AbstractButton chip : chipsOf(new AnimationSpeedControl())) {
            assertNull("'" + chip.getText() + "' must not pop up a tooltip", chip.getToolTipText());
        }
    }

    @Test
    public void noTimeUnitChipPopsUpAnExplanation() {
        for (AbstractButton chip : chipsOf(new TimeUnitControl())) {
            assertNull("'" + chip.getText() + "' must not pop up a tooltip", chip.getToolTipText());
        }
    }

    @Test
    public void everyChipStillCarriesALabelToReadInstead() {
        List<AbstractButton> chips = new ArrayList<>(chipsOf(new AnimationSpeedControl()));
        chips.addAll(chipsOf(new TimeUnitControl()));
        assertEquals("five speeds and four units", 9, chips.size());
        for (AbstractButton chip : chips) {
            assertFalse("a chip with no tooltip and no label would say nothing at all",
                    chip.getText() == null || chip.getText().isBlank());
        }
    }

    @Test
    public void theUnitRowOpensOnSeconds() {
        assertEquals(TimeUnitScale.SECONDS, new TimeUnitControl().getScale());
    }

    private static List<AbstractButton> chipsOf(Container root) {
        List<AbstractButton> chips = new ArrayList<>();
        for (Component child : root.getComponents()) {
            if (child instanceof AbstractButton button) {
                chips.add(button);
            } else if (child instanceof Container container) {
                chips.addAll(chipsOf(container));
            }
        }
        return chips;
    }
}

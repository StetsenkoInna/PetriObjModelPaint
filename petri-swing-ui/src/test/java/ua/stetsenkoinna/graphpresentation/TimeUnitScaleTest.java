package ua.stetsenkoinna.graphpresentation;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Naming a playback ratio in terms of the thing being modelled.
 *
 * <p>The ratio itself is model units per real second and never moves; what moves is what those
 * units are taken to stand for, and therefore what the ratio is called. Sixty units a second is
 * a simulated minute per second when a unit is a second, and a simulated hour per second when a
 * unit is a minute - the same ratio, the same amount of watching, described the way the person
 * watching thinks about it.
 */
public class TimeUnitScaleTest {

    @Test
    public void secondsNameARatioInWhicheverRealUnitItComesOutRoundIn() {
        assertEquals("1 s/s", TimeUnitScale.SECONDS.formatRate(1));
        assertEquals("10 s/s", TimeUnitScale.SECONDS.formatRate(10));
        assertEquals("1 min/s", TimeUnitScale.SECONDS.formatRate(60));
        assertEquals("10 min/s", TimeUnitScale.SECONDS.formatRate(600));
        assertEquals("1 h/s", TimeUnitScale.SECONDS.formatRate(3600));
    }

    @Test
    public void theSameRatiosClimbAScaleWhenAUnitIsWorthMore() {
        // Every one of them is sixty times the watching it was, because every unit is.
        assertEquals("1 min/s", TimeUnitScale.MINUTES.formatRate(1));
        assertEquals("1 h/s", TimeUnitScale.MINUTES.formatRate(60));
        assertEquals("1 h/s", TimeUnitScale.HOURS.formatRate(1));
        assertEquals("1 d/s", TimeUnitScale.HOURS.formatRate(24));
    }

    @Test
    public void anAbstractScaleNamesTheBareRatioBecauseThereIsNothingToConvertItInto() {
        assertFalse(TimeUnitScale.ABSTRACT.isConcrete());
        assertEquals("60×", TimeUnitScale.ABSTRACT.formatRate(60));
    }

    @Test
    public void aRatioThatIsNotWholeKeepsOneDecimalRatherThanReadingAsExact() {
        // 3600 units a second at a minute a unit is two and a half days of watching a second.
        assertEquals("2.5 d/s", TimeUnitScale.MINUTES.formatRate(3600));
    }

    @Test
    public void changingWhatAUnitMeansRenamesTheSpeedsWithoutChangingWhichIsChosen() {
        AnimationSpeedControl control = new AnimationSpeedControl();
        control.setMode(AnimationSpeedControl.Mode.VISUAL);
        // Opens on sixty units a second: a simulated minute per second, while a unit is a second.
        long budgetBefore = control.stepBudgetMillis(60);

        control.setTimeUnitScale(TimeUnitScale.MINUTES);

        assertEquals(TimeUnitScale.MINUTES, control.getTimeUnitScale());
        assertEquals("the ratio itself is untouched - only what it is called changed",
                budgetBefore, control.stepBudgetMillis(60));
    }

    @Test
    public void theScaleSaysWhatItMeansWhereverItIsShown() {
        assertEquals("1 unit = 1 s", TimeUnitScale.SECONDS.toString());
        assertEquals("1 unit = 1 min", TimeUnitScale.MINUTES.toString());
        assertEquals("Abstract", TimeUnitScale.ABSTRACT.toString());
        assertTrue(TimeUnitScale.HOURS.isConcrete());
    }
}

package ua.stetsenkoinna.graphpresentation;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * What each named speed actually means, in milliseconds a step is given.
 *
 * <p>The control replaced a slider whose value was fed straight to {@code Thread.sleep}, so the
 * numbers were their own specification and there was nothing to get wrong. Naming the speeds
 * puts a calculation between the label and the sleep, and these pin that calculation: a label
 * that says "20 events a second" has to hold each event for a twentieth of a second, and one
 * that says "1 min/s" has to take a second over a minute of simulated time however many events
 * that minute happens to contain.
 */
public class AnimationSpeedControlTest {

    @Test
    public void opensOnScientificAtOneEventASecond() {
        AnimationSpeedControl control = new AnimationSpeedControl();

        assertEquals(AnimationSpeedControl.Mode.SCIENTIFIC, control.getMode());
        assertEquals(1000, control.stepBudgetMillis(0));
    }

    @Test
    public void scientificHoldsEveryEventTheSameLengthOfTimeHoweverLongItTook() {
        AnimationSpeedControl control = new AnimationSpeedControl();

        // The point of the mode: an immediate transition and one that took an hour of simulated
        // time are both one event, and both are watched for just as long.
        assertEquals(control.stepBudgetMillis(0), control.stepBudgetMillis(3600));
    }

    @Test
    public void visualTakesASecondOverWhateverItsRatioSays() {
        AnimationSpeedControl control = new AnimationSpeedControl();
        control.setMode(AnimationSpeedControl.Mode.VISUAL);

        // Opens on 1 min/s: sixty simulated units are a second of watching, six are a tenth.
        assertEquals(1000, control.stepBudgetMillis(60));
        assertEquals(100, control.stepBudgetMillis(6));
    }

    @Test
    public void visualPassesStraightOverAStepThatTookNoSimulatedTime() {
        AnimationSpeedControl control = new AnimationSpeedControl();
        control.setMode(AnimationSpeedControl.Mode.VISUAL);

        // An immediate transition takes no time to happen, so there is no time to watch it for.
        // This is exactly what the mode is for, and exactly what Scientific refuses to do.
        assertEquals(0, control.stepBudgetMillis(0));
    }

    @Test
    public void noSingleStepIsEverHeldLongEnoughToLookLikeAHang() {
        AnimationSpeedControl control = new AnimationSpeedControl();
        control.setMode(AnimationSpeedControl.Mode.VISUAL);

        // A day of simulated time at a minute a second is a real day of waiting. The ratio is
        // still honoured everywhere it can be; it is only capped where honouring it would be
        // indistinguishable from the application having stopped responding.
        assertTrue(control.stepBudgetMillis(86_400) <= 5_000);
    }

    @Test
    public void switchingModeStartsThatModeOnItsOwnSpeedRatherThanCarryingANumberAcross() {
        AnimationSpeedControl control = new AnimationSpeedControl();

        control.setMode(AnimationSpeedControl.Mode.VISUAL);
        assertEquals(1000, control.stepBudgetMillis(60));

        control.setMode(AnimationSpeedControl.Mode.SCIENTIFIC);
        assertEquals(AnimationSpeedControl.Mode.SCIENTIFIC, control.getMode());
        assertEquals(1000, control.stepBudgetMillis(0));
    }

    @Test
    public void thePulseThatLightsAFiringShrinksWithTheChosenSpeed() {
        AnimationSpeedControl control = new AnimationSpeedControl();

        // At one event a second the pulse has to fit inside a second, so the frame delays the
        // canvas asks for are cut down; at a hundred a second there is no room for them at all
        // and they go to nothing, leaving the colour changes and the repaints without the waits.
        long atOneASecond = control.pulseFrameMillis(100);
        control.selectFastestSpeed();
        long atTopSpeed = control.pulseFrameMillis(100);

        assertTrue("scaled to fit the budget rather than left at its nominal delay",
                atOneASecond < 100);
        assertTrue(atTopSpeed < atOneASecond);
        assertEquals("nothing waits when there is no time to wait in", 0, atTopSpeed);
    }

    @Test
    public void thePulseIsNeverStretchedBeyondWhatTheAnimationAskedFor() {
        AnimationSpeedControl control = new AnimationSpeedControl();
        control.setMode(AnimationSpeedControl.Mode.VISUAL);
        // A step covering a long stretch of simulated time has more budget than the pulse could
        // ever use. It still looks the way it was drawn to look.
        control.stepBudgetMillis(86_400);

        assertEquals(100, control.pulseFrameMillis(100));
    }

    @Test
    public void theCanvasRepaintIntervalStaysWithinSightOfTheChosenSpeed() {
        AnimationSpeedControl control = new AnimationSpeedControl();

        int interval = control.repaintIntervalMillis();
        assertTrue("never so rare that the animation reads as a slideshow", interval <= 250);
        assertTrue("never so often that it is work for nothing having changed", interval >= 30);
    }

    @Test
    public void aModeChangeIsAnnouncedSoTheRepaintTimerCanFollowIt() {
        AnimationSpeedControl control = new AnimationSpeedControl();
        int[] changes = {0};
        control.addChangeListener(() -> changes[0]++);

        control.setMode(AnimationSpeedControl.Mode.VISUAL);

        assertEquals(1, changes[0]);
    }
}

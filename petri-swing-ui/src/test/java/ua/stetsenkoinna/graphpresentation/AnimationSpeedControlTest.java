package ua.stetsenkoinna.graphpresentation;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * What each named speed does to the two things that pace a run: the pulse that lights a firing
 * up, and the pause held after it.
 *
 * <p>Both, not just the pause. Lighting one firing costs the better part of three seconds at the
 * frame delays the canvas was drawn with, so a speed that governed only the pause left every
 * setting playing at about the same rate. And 1x has to be exactly what the animation always
 * did, or an editor opened and left alone would not behave the way it used to.
 */
public class AnimationSpeedControlTest {

    @Test
    public void opensOnTheSpeedTheAnimationHasAlwaysPlayedAt() {
        AnimationSpeedControl control = new AnimationSpeedControl();

        // The frame delays the canvas asks for, unchanged, and the pause the slider opened on.
        assertEquals(100, control.pulseFrameMillis(100));
        assertEquals(50, control.pulseFrameMillis(50));
        assertEquals(1000, control.stepPauseMillis());
    }

    @Test
    public void afasterSpeedShortensTheHighlightAndThePauseTogether() {
        AnimationSpeedControl control = new AnimationSpeedControl();

        control.selectSpeed("2x");

        assertEquals(50, control.pulseFrameMillis(100));
        assertEquals(500, control.stepPauseMillis());
    }

    @Test
    public void aslowerSpeedLengthensBothSoAFiringCanBeFollowed() {
        AnimationSpeedControl control = new AnimationSpeedControl();

        control.selectSpeed("0.5x");

        assertEquals("the highlight is what a slower setting is wanted for",
                200, control.pulseFrameMillis(100));
        assertEquals(2000, control.stepPauseMillis());
    }

    @Test
    public void theFastestSpeedWaitsForNothingAtAll() {
        AnimationSpeedControl control = new AnimationSpeedControl();

        control.selectFastestSpeed();

        assertEquals("no frame delay, so the highlight costs only what painting it costs",
                0, control.pulseFrameMillis(100));
        assertEquals(0, control.stepPauseMillis());
    }

    @Test
    public void theCanvasRepaintIntervalStaysWithinSightOfTheChosenSpeed() {
        AnimationSpeedControl control = new AnimationSpeedControl();
        int atNormal = control.repaintIntervalMillis();

        control.selectFastestSpeed();
        int atTopSpeed = control.repaintIntervalMillis();

        assertTrue("never so rare that the animation reads as a slideshow", atNormal <= 250);
        assertTrue("never so often that it is work for nothing having changed", atTopSpeed >= 30);
        assertTrue(atTopSpeed <= atNormal);
    }

    @Test
    public void aspeedChangeIsAnnouncedSoTheRepaintTimerCanFollowIt() {
        AnimationSpeedControl control = new AnimationSpeedControl();
        int[] changes = {0};
        control.addChangeListener(() -> changes[0]++);

        control.selectFastestSpeed();

        assertEquals(1, changes[0]);
    }
}

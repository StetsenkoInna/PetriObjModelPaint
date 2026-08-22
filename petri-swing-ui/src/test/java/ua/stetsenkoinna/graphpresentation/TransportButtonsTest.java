package ua.stetsenkoinna.graphpresentation;

import org.junit.Assume;
import org.junit.Test;
import ua.stetsenkoinna.graphpresentation.actions.StopSimulationAction;

import javax.swing.AbstractButton;
import javax.swing.Action;
import javax.swing.Icon;
import javax.swing.JFrame;
import javax.swing.SwingUtilities;
import java.awt.GraphicsEnvironment;
import java.lang.reflect.Field;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

/**
 * The five transport buttons: what they show, and what they do not.
 *
 * <p>They carry no tooltip. Five icon buttons eight pixels apart, hovered over constantly while
 * a run plays, meant five popups appearing over whichever button was about to be clicked next.
 *
 * <p>And the stop button is two buttons. While something is running it halts it; once a run has
 * finished on its own there is nothing left to halt and all it still does is put the net back
 * where it started, which a square cannot say. It wears a reset icon for exactly as long as that
 * is what it means.
 */
public class TransportButtonsTest {

    @Test
    public void noneOfThemExplainsItselfOnHover() throws Exception {
        withFrame(frame -> {
            for (String name : new String[] {"playPauseAnimationButton", "stopAnimationButton",
                    "stepBackButton", "runOneEventButton", "skipForwardAnimationButton"}) {
                AbstractButton button = buttonNamed(frame, name);
                assertNull(name + " must not pop up a tooltip", button.getToolTipText());
            }
        });
    }

    @Test
    public void everyTransportButtonStillShowsAnIcon() throws Exception {
        withFrame(frame -> {
            for (String name : new String[] {"playPauseAnimationButton", "stopAnimationButton",
                    "stepBackButton", "runOneEventButton", "skipForwardAnimationButton"}) {
                assertNotNull(name + " is an icon button and has to have one",
                        buttonNamed(frame, name).getIcon());
            }
        });
    }

    @Test
    public void stopBecomesResetOnceTheRunIsOverAndGoesBackAfterwards() throws Exception {
        withFrame(frame -> {
            StopSimulationAction stop = stopActionOf(frame);
            Icon whileRunning = iconOf(stop);
            assertNotNull(whileRunning);

            stop.switchToResetButton();
            Icon whenFinished = iconOf(stop);
            assertNotSame("a finished run needs a different button from a running one",
                    whileRunning, whenFinished);

            stop.switchToStopButton();
            assertNotSame("and it goes back the moment there is something to stop again",
                    whenFinished, iconOf(stop));
        });
    }

    @Test
    public void theButtonFollowsTheActionsIcon() throws Exception {
        withFrame(frame -> {
            AbstractButton button = buttonNamed(frame, "stopAnimationButton");
            StopSimulationAction stop = stopActionOf(frame);

            stop.switchToResetButton();
            assertSame("the button shows whatever the action currently means",
                    iconOf(stop), button.getIcon());
            // The greyed-out twin follows too, or a disabled button shows the icon it used to
            // have; see PetriNetsFrame.keepDisabledIconInStep.
            assertNotNull(button.getDisabledIcon());

            stop.switchToStopButton();
            assertSame(iconOf(stop), button.getIcon());
        });
    }

    private static Icon iconOf(Action action) {
        return (Icon) action.getValue(Action.LARGE_ICON_KEY);
    }

    private static AbstractButton buttonNamed(PetriNetsFrame frame, String field) {
        try {
            Field f = PetriNetsFrame.class.getDeclaredField(field);
            f.setAccessible(true);
            return (AbstractButton) f.get(frame);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("no such transport button: " + field, e);
        }
    }

    private static StopSimulationAction stopActionOf(PetriNetsFrame frame) {
        try {
            Field controls = PetriNetsFrame.class.getDeclaredField("animationControls");
            controls.setAccessible(true);
            AnimationControls animation = (AnimationControls) controls.get(frame);
            return animation.stopSimulationAction;
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("no animation controls on the frame", e);
        }
    }

    private interface FrameCheck {
        void run(PetriNetsFrame frame) throws Exception;
    }

    private static void withFrame(FrameCheck check) throws Exception {
        Assume.assumeFalse("needs a real display", GraphicsEnvironment.isHeadless());
        PetriNetsFrame[] holder = new PetriNetsFrame[1];
        SwingUtilities.invokeAndWait(() -> {
            PetriNetsFrame frame = new PetriNetsFrame();
            frame.setExtendedState(JFrame.NORMAL);
            frame.addNotify();
            holder[0] = frame;
        });
        try {
            check.run(holder[0]);
        } finally {
            SwingUtilities.invokeAndWait(holder[0]::dispose);
        }
    }
}

package ua.stetsenkoinna.petriobj;

import org.junit.Test;
import static org.junit.Assert.assertEquals;

public class HeadlessTransitionTest {
    @Test public void negativeDelayDoesNotRequireADesktopOrChangeTheSample() {
        assertEquals(-1.0, new PetriT("negative", -1.0).getTimeServ(), 0.0);
    }
}

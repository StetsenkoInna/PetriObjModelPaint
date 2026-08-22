package ua.stetsenkoinna.graphpresentation;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Saying what a stretch of the model's clock amounts to.
 *
 * <p>The simulator counts in units of nothing in particular, which is right for a simulator and
 * unreadable on a screen: a horizon of 1000 could be a quarter of an hour of a factory's day or
 * six weeks of it. Naming the unit once turns that number into something a person can check.
 */
public class TimeUnitScaleTest {

    @Test
    public void aHorizonIsSaidInTheLargestRealUnitItFills() {
        assertEquals("16 min 40 s", TimeUnitScale.SECONDS.formatDuration(1000));
        assertEquals("45 s", TimeUnitScale.SECONDS.formatDuration(45));
        assertEquals("2 h", TimeUnitScale.SECONDS.formatDuration(7200));
    }

    @Test
    public void theSameHorizonGrowsWhenAUnitIsWorthMore() {
        assertEquals("16 h 40 min", TimeUnitScale.MINUTES.formatDuration(1000));
        assertEquals("41 d 16 h", TimeUnitScale.HOURS.formatDuration(1000));
    }

    @Test
    public void aRoundHorizonDoesNotTrailAnEmptyRemainder() {
        assertEquals("1 min", TimeUnitScale.SECONDS.formatDuration(60));
        assertEquals("1 h", TimeUnitScale.MINUTES.formatDuration(60));
    }

    @Test
    public void unitsThatStandForNothingAreNotConvertedIntoAnything() {
        assertFalse(TimeUnitScale.ABSTRACT.isConcrete());
        assertEquals("", TimeUnitScale.ABSTRACT.formatDuration(1000));
    }

    @Test
    public void thereIsNothingToSayAboutAHorizonOfZero() {
        assertEquals("", TimeUnitScale.SECONDS.formatDuration(0));
    }

    @Test
    public void eachUnitCarriesTheShortLabelItsChipShows() {
        assertEquals("s", TimeUnitScale.SECONDS.chipLabel());
        assertEquals("min", TimeUnitScale.MINUTES.chipLabel());
        assertEquals("h", TimeUnitScale.HOURS.chipLabel());
        assertEquals("abstract", TimeUnitScale.ABSTRACT.chipLabel());
        assertTrue(TimeUnitScale.HOURS.isConcrete());
    }
}

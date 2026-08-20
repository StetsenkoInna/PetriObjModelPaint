package ua.stetsenkoinna.utils;

import org.junit.Test;

import java.util.Collections;

import static org.junit.Assert.assertEquals;

/**
 * {@link MessageHelper#showWarnings} and {@link MessageHelper#showImportWarnings} open a modal
 * {@code JOptionPane}, which cannot run in a test JVM: with no owner window and no user to
 * click it, the dialog would block the test run forever rather than fail outright (the same
 * reason {@code ObjectBandToolTest} leaves its own dialog untested). What is dialog-free and
 * therefore safe to pin directly: the empty-list guard, which shows nothing at all, and the
 * exact wording of the title a non-empty list would be shown under.
 */
public class MessageHelperTest {

    @Test
    public void showsNothingForAnEmptyWarningsList() {
        // No dialog opens for an empty list, so this is safe to call for real - if it ever
        // did open one, this test would hang instead of failing, which is the point of
        // exercising the guard directly rather than trusting it by inspection alone.
        MessageHelper.showImportWarnings(null, Collections.emptyList());
        MessageHelper.showImportWarnings(null, null);
    }

    @Test
    public void titlesANonEmptyListWithHowManyWarningsThereWere() {
        assertEquals("Imported with 1 warnings", MessageHelper.importWarningsTitle(1));
        assertEquals("Imported with 3 warnings", MessageHelper.importWarningsTitle(3));
    }
}

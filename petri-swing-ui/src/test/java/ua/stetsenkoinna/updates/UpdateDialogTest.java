package ua.stetsenkoinna.updates;

import java.awt.*;
import java.awt.event.WindowEvent;
import java.net.URI;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.swing.*;
import org.junit.*;
import ua.stetsenkoinna.config.AppSettings;
import ua.stetsenkoinna.graphpresentation.training.SwingCapture;
import static org.junit.Assert.*;

public class UpdateDialogTest {
    private final UpdateRelease release=new UpdateRelease("2.8.0",URI.create("https://example.com/update.zip"),"a".repeat(64),"b".repeat(64),10,"windows","amd64");
    private final UpdatePreferences preferences=new UpdatePreferences(new AppSettings(null));
    private final AtomicBoolean installed=new AtomicBoolean();
    private UpdateDialog dialog;
    @Before public void setup() throws Exception { dialog=SwingCapture.call(() -> new UpdateDialog(null,release,preferences,() -> installed.set(true))); }
    @After public void cleanup() throws Exception { SwingCapture.call(() -> { dialog.dispose(); return null; }); }
    private JButton button(Container root,String label) {
        for(Component c:root.getComponents()) {
            if(c instanceof JButton b && label.equals(b.getText())) return b;
            if(c instanceof Container child) { JButton found=button(child,label); if(found!=null) return found; }
        }
        return null;
    }
    @Test public void remindingDoesNotInstallAndPersistsSnooze() throws Exception {
        SwingCapture.call(() -> { button(dialog,"Remind me tomorrow").doClick(); return null; });
        assertFalse(installed.get()); assertFalse(preferences.shouldNotify(release.identity(),Instant.now()));
    }
    @Test public void skippingDoesNotInstallAndDoesNotHideNextBuild() throws Exception {
        SwingCapture.call(() -> { button(dialog,"Skip this build").doClick(); return null; });
        assertFalse(installed.get()); assertFalse(preferences.shouldNotify(release.identity(),Instant.now().plusSeconds(1000000)));
        assertTrue(preferences.shouldNotify("2.8.0:new-build",Instant.now()));
    }
    @Test public void closingIsEquivalentToRemindingTomorrow() throws Exception {
        SwingCapture.call(() -> { dialog.dispatchEvent(new WindowEvent(dialog,WindowEvent.WINDOW_CLOSING)); return null; });
        assertFalse(installed.get()); assertFalse(preferences.shouldNotify(release.identity(),Instant.now()));
    }
    @Test public void installationRequiresExplicitClick() throws Exception {
        assertFalse(installed.get());
        SwingCapture.call(() -> { button(dialog,"Install update").doClick(); return null; });
        assertTrue(installed.get());
    }
}

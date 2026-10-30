package ua.stetsenkoinna.graphpresentation;

import javax.swing.*;
import java.awt.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class ResultsSidebarLayoutTest {
    @Test public void collapsedSidebarKeepsOnlyItsStripAcrossResizeAndUiRelayout() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            PetriNetsFrame frame = new PetriNetsFrame();
            try {
                frame.setSize(1000, 700); frame.setVisible(true); frame.validate();
                JSplitPane split = find(frame);
                assertNotNull(split);
                frame.setSidebarCollapsed(true); split.doLayout();
                int strip = split.getRightComponent().getWidth();
                assertTrue(strip > 0 && strip < 60);
                for (int width : new int[]{1900, 850, 1600, 1000}) {
                    frame.setSize(width, 700); frame.validate(); split.doLayout();
                    assertEquals("Collapsed sidebar must not retain the old divider's position", strip, split.getRightComponent().getWidth());
                }
                // Reproduce a UI delegate or stale resize restoring an old divider location.
                split.setDividerLocation(split.getWidth() / 2); split.doLayout();
                assertEquals(strip, split.getRightComponent().getWidth());
                split.updateUI(); split.doLayout();
                assertEquals(strip, split.getRightComponent().getWidth());
                frame.setSidebarCollapsed(false); split.doLayout();
                assertTrue(split.getRightComponent().getWidth() > 250);
                frame.setSidebarCollapsed(true); split.doLayout();
                assertEquals(strip, split.getRightComponent().getWidth());
            } finally { for (Window window : Window.getWindows()) window.dispose(); }
        });
    }
    private static JSplitPane find(Container parent) {
        for (Component child : parent.getComponents()) {
            if (child instanceof JSplitPane split && "Canvas and results split".equals(split.getAccessibleContext().getAccessibleName())) return split;
            if (child instanceof Container nested) { JSplitPane result = find(nested); if (result != null) return result; }
        }
        return null;
    }
}

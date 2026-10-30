package ua.stetsenkoinna.graphpresentation.ai;

import java.awt.Component;
import java.awt.Container;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import ua.stetsenkoinna.config.AppSettings;
import static org.junit.Assert.*;

public class McpDialogTest {
    @Rule public TemporaryFolder folder = new TemporaryFolder();

    @Test public void dialogStartsWithoutAuthenticationAndCanEnableItAfterStopping() throws Exception {
        var settingsFile = folder.getRoot().toPath().resolve("ui.properties");
        var settings = new AppSettings(settingsFile);
        int port;
        try (var socket = new java.net.ServerSocket(0)) { port = socket.getLocalPort(); }
        settings.rememberMcpConnection(port, null, false);
        try (var server = new DesktopMcpServer()) {
            SwingUtilities.invokeAndWait(() -> {
                var dialog = new McpDialog(null, server, settings, null);
                try {
                    var controls = descendants(dialog);
                    var requireToken = controls.stream().filter(JCheckBox.class::isInstance)
                            .map(JCheckBox.class::cast).findFirst().orElseThrow();
                    assertFalse(requireToken.isSelected());
                    button(controls, "Start server").doClick();
                    assertTrue(server.isRunning());
                    assertFalse(server.isAuthenticationRequired());
                    assertFalse(button(controls, "Copy token").isEnabled());
                    assertFalse(requireToken.isEnabled());
                    assertTrue(controls.stream().filter(JTextArea.class::isInstance).map(JTextArea.class::cast)
                            .anyMatch(area -> area.getText().contains("Authentication: Off")));
                    button(controls, "Stop server").doClick();
                    requireToken.setSelected(true);
                    button(controls, "Start server").doClick();
                    assertTrue(server.isAuthenticationRequired());
                    assertTrue(button(controls, "Copy token").isEnabled());
                    assertTrue(new AppSettings(settingsFile).isMcpAuthenticationRequired());
                    assertEquals(server.token(), new AppSettings(settingsFile).getMcpToken());
                } finally {
                    dialog.dispose();
                }
            });
        }
    }

    private static JButton button(List<Component> controls, String text) {
        return controls.stream().filter(JButton.class::isInstance).map(JButton.class::cast)
                .filter(button -> text.equals(button.getText())).findFirst().orElseThrow();
    }

    private static List<Component> descendants(Container parent) {
        List<Component> result = new ArrayList<>();
        for (Component child : parent.getComponents()) {
            result.add(child);
            if (child instanceof Container container) result.addAll(descendants(container));
        }
        return result;
    }
}

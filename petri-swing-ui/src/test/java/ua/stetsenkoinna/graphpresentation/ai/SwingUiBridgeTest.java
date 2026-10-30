package ua.stetsenkoinna.graphpresentation.ai;

import com.fasterxml.jackson.databind.JsonNode;
import java.awt.GraphicsEnvironment;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.AbstractAction;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

public class SwingUiBridgeTest {
    private final SwingUiBridge bridge = new SwingUiBridge();
    private final McpToolRegistry tools = new McpToolRegistry(bridge);
    private JFrame frame;
    private JTextField field;
    private final AtomicInteger clicks = new AtomicInteger();
    private final java.util.concurrent.atomic.AtomicReference<java.io.File> chosenFile = new java.util.concurrent.atomic.AtomicReference<>();

    @Before public void createWindow() throws Exception {
        Assume.assumeFalse(GraphicsEnvironment.isHeadless());
        SwingUtilities.invokeAndWait(() -> {
            frame = new JFrame("MCP test");
            JPanel content = new JPanel();
            field = new JTextField("Before", 12);
            content.add(field);
            JButton button = new JButton("Run action");
            button.addActionListener(event -> {
                assertTrue(SwingUtilities.isEventDispatchThread());
                clicks.incrementAndGet();
            });
            content.add(button);
            JButton modal = new JButton("Open dialog");
            modal.addActionListener(event -> {
                JDialog dialog = new JDialog(frame, "Test modal", true);
                JButton close = new JButton("Close dialog");
                close.addActionListener(closeEvent -> dialog.dispose());
                JPanel buttons = new JPanel();
                buttons.add(close);
                JButton nested = new JButton("Open nested dialog");
                nested.addActionListener(nestedEvent -> {
                    JDialog child = new JDialog(dialog, "Nested modal", true);
                    JButton dismiss = new JButton("Close nested");
                    dismiss.addActionListener(dismissEvent -> child.dispose());
                    child.add(dismiss);
                    child.pack();
                    child.setVisible(true);
                });
                buttons.add(nested);
                dialog.add(buttons);
                dialog.pack();
                dialog.setVisible(true);
            });
            content.add(modal);
            JButton openFile = new JButton("Open native file");
            openFile.addActionListener(event -> chosenFile.set(
                    ua.stetsenkoinna.graphpresentation.io.FileDialogs.open(frame, "MCP test file",
                            ua.stetsenkoinna.graphpresentation.io.FileDialogs.ANY, null)));
            content.add(openFile);
            JButton saveFile = new JButton("Save native file");
            saveFile.addActionListener(event -> chosenFile.set(
                    ua.stetsenkoinna.graphpresentation.io.FileDialogs.save(frame, "MCP test save",
                            ua.stetsenkoinna.graphpresentation.io.FileDialogs.PNML, null, "model.pnml")));
            content.add(saveFile);
            field.getInputMap(JComponent.WHEN_FOCUSED).put(KeyStroke.getKeyStroke("ctrl K"), "test-key");
            field.getActionMap().put("test-key", new AbstractAction() {
                @Override public void actionPerformed(ActionEvent event) { clicks.incrementAndGet(); }
            });
            frame.add(content);
            javax.swing.JMenuBar bar = new javax.swing.JMenuBar();
            javax.swing.JMenu menu = new javax.swing.JMenu("Actions");
            javax.swing.JMenuItem item = new javax.swing.JMenuItem("Run menu action");
            item.addActionListener(event -> clicks.incrementAndGet());
            menu.add(item);
            bar.add(menu);
            frame.setJMenuBar(bar);
            frame.pack();
            frame.setVisible(true);
        });
    }

    @After public void closeWindow() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            for (Window window : Window.getWindows()) if (window == frame || window.getOwner() == frame) window.dispose();
        });
    }

    private JsonNode inspect() throws Exception {
        JsonNode result = DesktopMcpServer.JSON.valueToTree(tools.call("inspect_ui", null));
        return DesktopMcpServer.JSON.readTree(result.at("/content/0/text").asText());
    }

    private JsonNode find(JsonNode node, String key, String value) {
        if (node.isObject() && value.equals(node.path(key).asText())) return node;
        for (JsonNode child : node) {
            JsonNode found = find(child, key, value);
            if (found != null) return found;
        }
        return null;
    }

    private void action(JsonNode control, String action, Object... pairs) throws Exception {
        var args = DesktopMcpServer.JSON.createObjectNode().put("id", control.get("id").asText()).put("action", action);
        for (int i = 0; i < pairs.length; i += 2) args.set((String) pairs[i], DesktopMcpServer.JSON.valueToTree(pairs[i + 1]));
        tools.call("ui_action", args);
        SwingUtilities.invokeAndWait(() -> { });
    }

    @Test public void controlsUseExistingHandlersOnEdtAndKeyBindingsWork() throws Exception {
        JsonNode initial = inspect();
        action(find(initial, "text", "Before"), "text", "text", "After");
        assertEquals("After", field.getText());
        action(find(initial, "text", "Run action"), "click");
        assertEquals("Button handler", 1, clicks.get());
        action(find(initial, "text", "Before"), "key", "key", "ctrl K");
        assertEquals("Key handler: " + inspect().path("lastAction").asText(), 2, clicks.get());
        action(find(inspect(), "text", "Run menu action"), "click");
        assertEquals(3, clicks.get());
    }

    @Test public void staleIdsAreRejectedAndScreenshotsContainPngData() throws Exception {
        JsonNode initial = inspect();
        JsonNode window = find(initial, "title", "MCP test");
        JsonNode result = DesktopMcpServer.JSON.valueToTree(tools.call("screenshot",
                DesktopMcpServer.JSON.valueToTree(Map.of("id", window.get("id").asText()))));
        byte[] png = java.util.Base64.getDecoder().decode(result.at("/content/0/data").asText());
        assertEquals(0x89, png[0] & 0xff);
        assertEquals('P', png[1]);
        inspect();
        try {
            action(find(initial, "text", "Run action"), "click");
            fail("Stale ID accepted");
        } catch (Exception expected) {
            assertEquals(0, clicks.get());
        }
    }

    @Test public void modalDialogsDoNotBlockSubsequentMcpCalls() throws Exception {
        JsonNode initial = inspect();
        action(find(initial, "text", "Open dialog"), "click");
        JsonNode modal = inspect();
        assertNotNull(find(modal, "title", "Test modal"));
        action(find(modal, "text", "Open nested dialog"), "click");
        JsonNode nested = inspect();
        assertNotNull(find(nested, "title", "Nested modal"));
        action(find(nested, "text", "Close nested"), "click");
        action(find(inspect(), "text", "Close dialog"), "click");
        assertNull(find(inspect(), "title", "Test modal"));
    }

    @Test public void nativeFileSelectionReturnsThroughExistingFileDialogHandler() throws Exception {
        java.nio.file.Path file = java.nio.file.Files.createTempFile("petri-mcp-test-", ".pnml");
        try {
            action(find(inspect(), "text", "Open native file"), "click");
            JsonNode dialog = find(inspect(), "title", "MCP test file");
            assertNotNull(dialog);
            action(dialog, "file", "path", file.toString());
            long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(5);
            while (chosenFile.get() == null && System.nanoTime() < deadline) Thread.sleep(20);
            assertEquals(file.toFile(), chosenFile.get());
        } finally {
            java.nio.file.Files.deleteIfExists(file);
        }
    }

    @Test public void nativeSaveSelectionPreservesNewPathAndExtension() throws Exception {
        java.nio.file.Path directory = java.nio.file.Files.createTempDirectory("petri-mcp-save-");
        try {
            action(find(inspect(), "text", "Save native file"), "click");
            JsonNode dialog = find(inspect(), "title", "MCP test save");
            assertNotNull(dialog);
            java.nio.file.Path target = directory.resolve("new-model");
            action(dialog, "file", "path", target.toString());
            long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(5);
            while (chosenFile.get() == null && System.nanoTime() < deadline) Thread.sleep(20);
            assertEquals(directory.resolve("new-model.pnml").toFile(), chosenFile.get());
        } finally {
            java.nio.file.Files.deleteIfExists(directory);
        }
    }
}

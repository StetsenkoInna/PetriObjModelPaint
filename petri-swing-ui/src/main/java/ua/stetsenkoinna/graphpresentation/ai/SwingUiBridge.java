package ua.stetsenkoinna.graphpresentation.ai;

import com.fasterxml.jackson.databind.JsonNode;
import java.awt.Component;
import java.awt.Container;
import java.awt.Window;
import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import javax.imageio.ImageIO;
import javax.swing.AbstractButton;
import javax.swing.Action;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JMenu;
import javax.swing.JSpinner;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.text.JTextComponent;

/** Operates on application components only, through the same listeners as local input. */
public final class SwingUiBridge {
    private final Map<String, Component> components = new LinkedHashMap<>();
    private int generation;
    private volatile String lastAction = "No actions yet";
    private long actionSequence;

    @McpTool(name = "inspect_ui", description = "Read application windows, menus, controls, values and component IDs. "
            + "IDs are valid until the next inspect_ui. Reports the last queued action.")
    public Object inspectUi(JsonNode args) throws Exception {
        return onEdt(() -> text(inspect()));
    }

    @McpTool(name = "screenshot", description = "Capture a visible application window as PNG. Use a window ID from inspect_ui.",
            inputSchema = """
                    {"type":"object","properties":{"id":{"type":"string"}},"required":["id"],"additionalProperties":false}
                    """)
    public Object captureWindow(JsonNode args) throws Exception {
        return onEdt(() -> screenshot(component(args)));
    }

    @McpTool(name = "ui_action", description = "Operate a control or canvas through the editor's existing handlers. "
            + "click invokes buttons or menu items; text replaces an editable field; select uses index for lists, combos, tabs and tables "
            + "or value for spinners. key invokes a Swing binding (ctrl Z, DELETE). mouse, drag and scroll use component-relative coordinates. "
            + "button is 1 (left) or 3 (right). file chooses a path in a native file dialog. "
            + "Actions are queued so modal dialogs can be handled by later calls. Inspect again after each action to see completion or errors.",
            inputSchema = """
                    {"type":"object","properties":{
                      "id":{"type":"string"},
                      "action":{"type":"string","enum":["click","text","select","key","mouse","drag","scroll","file","close"]},
                      "text":{"type":"string"},"key":{"type":"string"},"path":{"type":"string"},
                      "index":{"type":"integer"},"value":{"type":"number"},
                      "x":{"type":"integer"},"y":{"type":"integer"},
                      "toX":{"type":"integer"},"toY":{"type":"integer"},
                      "button":{"type":"integer","enum":[1,3]},
                      "clickCount":{"type":"integer","minimum":1,"maximum":2},
                      "rotation":{"type":"integer"}
                    },"required":["id","action"],"additionalProperties":false}
                    """)
    public Object performAction(JsonNode args) throws Exception {
        return onEdt(() -> queueAction(component(args), args));
    }

    private Object onEdt(java.util.concurrent.Callable<Object> operation) throws Exception {
        if (SwingUtilities.isEventDispatchThread()) return operation.call();
        FutureTask<Object> task = new FutureTask<>(operation);
        SwingUtilities.invokeLater(task);
        try {
            return task.get(10, TimeUnit.SECONDS);
        } catch (java.util.concurrent.TimeoutException ex) {
            task.cancel(false);
            throw new IllegalStateException("UI did not respond in time", ex);
        }
    }

    private Object inspect() {
        components.clear();
        generation++;
        List<Object> windows = new ArrayList<>();
        for (Window window : Window.getWindows()) {
            if (window.isShowing()) {
                windows.add(describe(window));
            }
        }
        return Map.of("windows", windows, "lastAction", lastAction);
    }

    private Map<String, Object> describe(Component component) {
        String id = generation + ":" + components.size();
        components.put(id, component);
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("id", id);
        info.put("type", component.getClass().getSimpleName());
        info.put("enabled", component.isEnabled());
        info.put("showing", component.isShowing());
        info.put("width", component.getWidth());
        info.put("height", component.getHeight());
        info.put("x", component.getX());
        info.put("y", component.getY());
        if (component instanceof JComponent control) {
            java.awt.Rectangle visible = control.getVisibleRect();
            info.put("visibleRect", Map.of("x", visible.x, "y", visible.y, "width", visible.width, "height", visible.height));
        }
        if (component instanceof javax.accessibility.Accessible accessible && accessible.getAccessibleContext() != null) {
            String name = accessible.getAccessibleContext().getAccessibleName();
            if (name != null && !name.isBlank()) info.put("accessibleName", name);
        }
        if (component.getName() != null) info.put("name", component.getName());
        if (component instanceof java.awt.Frame frame) info.put("title", frame.getTitle());
        if (component instanceof java.awt.Dialog dialog) info.put("title", dialog.getTitle());
        if (component instanceof java.awt.FileDialog dialog) {
            info.put("mode", dialog.getMode() == java.awt.FileDialog.SAVE ? "save" : "open");
            info.put("directory", dialog.getDirectory() == null ? "" : dialog.getDirectory());
        }
        if (component instanceof AbstractButton button) {
            info.put("text", button.getText() == null ? "" : button.getText());
            info.put("selected", button.isSelected());
        }
        if (component instanceof JLabel label) info.put("text", label.getText() == null ? "" : label.getText());
        if (component instanceof JTextComponent field && !(field instanceof javax.swing.JPasswordField)) {
            info.put("text", field.getText());
            info.put("editable", field.isEditable());
        }
        if (component instanceof JComponent control && control.getToolTipText() != null) info.put("tooltip", control.getToolTipText());
        if (component instanceof JComboBox<?> combo) {
            List<String> items = new ArrayList<>();
            for (int i = 0; i < combo.getItemCount(); i++) items.add(String.valueOf(combo.getItemAt(i)));
            info.put("items", items);
            info.put("selectedIndex", combo.getSelectedIndex());
        }
        if (component instanceof JList<?> list) {
            List<String> items = new ArrayList<>();
            for (int i = 0; i < list.getModel().getSize(); i++) items.add(String.valueOf(list.getModel().getElementAt(i)));
            info.put("items", items);
            info.put("selectedIndex", list.getSelectedIndex());
        }
        if (component instanceof JSpinner spinner) info.put("value", String.valueOf(spinner.getValue()));
        if (component instanceof JTable table) {
            List<Object> rows = new ArrayList<>();
            for (int r = 0; r < table.getRowCount(); r++) {
                List<String> row = new ArrayList<>();
                for (int c = 0; c < table.getColumnCount(); c++) row.add(String.valueOf(table.getValueAt(r, c)));
                rows.add(row);
            }
            info.put("rows", rows);
        }
        List<Object> children = new ArrayList<>();
        if (component instanceof JMenu menu) {
            for (Component child : menu.getMenuComponents()) children.add(describe(child));
        } else if (component instanceof Container container) {
            for (Component child : container.getComponents()) {
                if (child.isVisible()) children.add(describe(child));
            }
        }
        if (!children.isEmpty()) info.put("children", children);
        return info;
    }

    private Component component(JsonNode args) {
        Component component = components.get(args.path("id").asText());
        if (component == null) throw new IllegalArgumentException("Unknown or stale component ID; call inspect_ui");
        return component;
    }

    private Object queueAction(Component component, JsonNode args) {
        if (!component.isEnabled()) throw new IllegalArgumentException("Control is disabled");
        Window window = owningWindow(component);
        if (window == null || !window.isShowing()) throw new IllegalArgumentException("Window is closed");
        for (Window candidate : Window.getWindows()) {
            if (candidate instanceof java.awt.Dialog dialog && dialog.isShowing() && dialog.isModal()
                    && !ownedBy(window, dialog)) throw new IllegalArgumentException("Handle the open modal dialog first");
        }
        long sequence = ++actionSequence;
        lastAction = "Queued " + args.path("action").asText();
        SwingUtilities.invokeLater(() -> {
            try {
                lastAction = "Running " + args.path("action").asText();
                act(component, args);
                if (actionSequence == sequence) lastAction = "Completed " + args.path("action").asText();
            } catch (Exception ex) {
                if (actionSequence == sequence) lastAction = "Failed: " + ex.getMessage();
            }
        });
        return text(Map.of("status", "queued"));
    }

    private static Window owningWindow(Component component) {
        for (Component current = component; current != null;) {
            if (current instanceof Window window) return window;
            current = current instanceof javax.swing.JPopupMenu popup ? popup.getInvoker() : current.getParent();
        }
        return null;
    }

    private static boolean ownedBy(Window window, Window owner) {
        for (Window current = window; current != null; current = current.getOwner()) {
            if (current == owner) return true;
        }
        return false;
    }

    private void act(Component component, JsonNode args) {
        switch (args.path("action").asText()) {
            case "file" -> {
                if (!(component instanceof java.awt.FileDialog dialog)) throw new IllegalArgumentException("Choose a native file dialog");
                if (!args.hasNonNull("path") || args.path("path").asText().isBlank()) throw new IllegalArgumentException("path is required");
                java.io.File file = new java.io.File(args.path("path").asText()).getAbsoluteFile();
                if (dialog.getMode() == java.awt.FileDialog.LOAD && !file.isFile()) throw new IllegalArgumentException("File does not exist");
                if (dialog.getMode() == java.awt.FileDialog.SAVE && file.exists()
                        && javax.swing.JOptionPane.showConfirmDialog(dialog, "Replace existing file?\n" + file,
                                "Confirm save", javax.swing.JOptionPane.YES_NO_OPTION) != javax.swing.JOptionPane.YES_OPTION) return;
                dialog.setDirectory(file.getParent());
                dialog.setFile(file.getName());
                dialog.dispose();
            }
            case "click" -> {
                if (!(component instanceof AbstractButton button)) throw new IllegalArgumentException("Use mouse for a canvas");
                button.doClick(0);
            }
            case "text" -> {
                if (!(component instanceof JTextComponent field) || !field.isEditable()) throw new IllegalArgumentException("Not an editable field");
                if (!args.has("text")) throw new IllegalArgumentException("text is required");
                field.setText(args.path("text").asText());
            }
            case "select" -> {
                int index = args.path("index").asInt(-1);
                if (component instanceof JComboBox<?> combo) combo.setSelectedIndex(index);
                else if (component instanceof JList<?> list) list.setSelectedIndex(index);
                else if (component instanceof JTabbedPane tabs) tabs.setSelectedIndex(index);
                else if (component instanceof JTable table) table.setRowSelectionInterval(index, index);
                else if (component instanceof JSpinner spinner) {
                    Object previous = spinner.getValue();
                    spinner.setValue(previous instanceof Integer ? (Object) args.path("value").asInt() : args.path("value").asDouble());
                } else throw new IllegalArgumentException("Control does not support selection");
            }
            case "key" -> {
                if (!(component instanceof JComponent control)) throw new IllegalArgumentException("Choose a Swing control");
                KeyStroke stroke = KeyStroke.getKeyStroke(args.path("key").asText());
                if (stroke == null) throw new IllegalArgumentException("Invalid Swing key stroke");
                boolean invoked = false;
                for (int condition : new int[]{JComponent.WHEN_FOCUSED, JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT, JComponent.WHEN_IN_FOCUSED_WINDOW}) {
                    Object binding = control.getInputMap(condition).get(stroke);
                    Action action = binding == null ? null : control.getActionMap().get(binding);
                    if (action != null && action.isEnabled()) {
                        java.awt.event.KeyEvent keyEvent = new java.awt.event.KeyEvent(control,
                                stroke.getKeyEventType(), System.currentTimeMillis(), stroke.getModifiers(),
                                stroke.getKeyCode(), stroke.getKeyChar());
                        SwingUtilities.notifyAction(action, stroke, keyEvent, control, stroke.getModifiers());
                        invoked = true;
                        break;
                    }
                }
                if (!invoked) throw new IllegalArgumentException("No enabled binding for this key on the control");
            }
            case "mouse", "drag" -> mouse(component, args);
            case "close" -> {
                if (!(component instanceof Window target)) throw new IllegalArgumentException("Choose a window ID");
                target.dispatchEvent(new java.awt.event.WindowEvent(target, java.awt.event.WindowEvent.WINDOW_CLOSING));
            }
            case "scroll" -> component.dispatchEvent(new MouseWheelEvent(component, MouseEvent.MOUSE_WHEEL,
                    System.currentTimeMillis(), 0, args.path("x").asInt(), args.path("y").asInt(), 0, false,
                    MouseWheelEvent.WHEEL_UNIT_SCROLL, 3, args.path("rotation").asInt()));
            default -> throw new IllegalArgumentException("Unknown action");
        }
    }

    private void mouse(Component component, JsonNode args) {
        if (!args.has("x") || !args.has("y")) throw new IllegalArgumentException("x and y are required");
        if ("drag".equals(args.path("action").asText()) && (!args.has("toX") || !args.has("toY"))) {
            throw new IllegalArgumentException("toX and toY are required for a drag");
        }
        int x = args.path("x").asInt(), y = args.path("y").asInt();
        if (x < 0 || y < 0 || x >= component.getWidth() || y >= component.getHeight()) throw new IllegalArgumentException("Coordinates outside control");
        int button = args.path("button").asInt(1);
        if (button != 1 && button != 3) throw new IllegalArgumentException("Unsupported mouse button");
        int count = args.path("clickCount").asInt(1);
        int mask = InputEvent.getMaskForButton(button);
        boolean drag = "drag".equals(args.path("action").asText());
        component.requestFocusInWindow();
        component.dispatchEvent(new MouseEvent(component, MouseEvent.MOUSE_PRESSED, System.currentTimeMillis(), mask, x, y, count, false, button));
        if (drag) {
            int toX = args.path("toX").asInt(), toY = args.path("toY").asInt();
            for (int step = 1; step <= 10; step++) {
                component.dispatchEvent(new MouseEvent(component, MouseEvent.MOUSE_DRAGGED, System.currentTimeMillis(), mask,
                        x + (toX - x) * step / 10, y + (toY - y) * step / 10, 0, false, MouseEvent.NOBUTTON));
            }
            x = toX;
            y = toY;
        }
        component.dispatchEvent(new MouseEvent(component, MouseEvent.MOUSE_RELEASED, System.currentTimeMillis(), 0, x, y, count, button == 3, button));
        if (!drag) component.dispatchEvent(new MouseEvent(component, MouseEvent.MOUSE_CLICKED, System.currentTimeMillis(), 0, x, y, count, false, button));
    }

    private Object screenshot(Component component) throws Exception {
        if (!(component instanceof Window window) || !window.isShowing()) throw new IllegalArgumentException("Choose a visible window ID");
        BufferedImage image = new BufferedImage(window.getWidth(), window.getHeight(), BufferedImage.TYPE_INT_RGB);
        java.awt.Graphics2D graphics = image.createGraphics();
        try {
            window.paintAll(graphics);
        } finally {
            graphics.dispose();
        }
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(image, "png", output);
        return Map.of("content", List.of(Map.of("type", "image", "mimeType", "image/png",
                "data", Base64.getEncoder().encodeToString(output.toByteArray()))));
    }

    private static Object text(Object value) {
        try {
            return Map.of("content", List.of(Map.of("type", "text", "text", DesktopMcpServer.JSON.writeValueAsString(value))));
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }
}

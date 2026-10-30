package ua.stetsenkoinna.graphpresentation.ai;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Window;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTextArea;
import javax.swing.SpinnerNumberModel;
import javax.swing.JTabbedPane;
import ua.stetsenkoinna.config.AppSettings;

public final class McpDialog extends JDialog {
    private final DesktopMcpServer server;
    private final AppSettings settings;
    private String startupFailure;
    private final JLabel status = new JLabel();
    private final JTextArea connection = new JTextArea(12, 68);
    private final JSpinner port = new JSpinner(new SpinnerNumberModel(8765, 1024, 65535, 1));
    private final JButton toggle = new JButton();
    private final JTabbedPane clients = new JTabbedPane();
    private final JTextArea codex = clientText();
    private final JTextArea claudeCode = clientText();
    private final JTextArea claudeDesktop = clientText();
    private final JButton copyUrl = new JButton("Copy URL");
    private final JButton copyToken = new JButton("Copy token");
    private final JButton copySetup = new JButton("Copy client setup");

    public McpDialog(Window owner, DesktopMcpServer server) {
        this(owner, server, AppSettings.shared(), null);
    }

    public McpDialog(Window owner, DesktopMcpServer server, AppSettings settings, String startupFailure) {
        super(owner, "AI - MCP", ModalityType.MODELESS);
        this.server = server;
        this.settings = settings;
        this.startupFailure = startupFailure;
        port.setValue(settings.getMcpPort());
        port.setEditor(new JSpinner.NumberEditor(port, "0"));
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        JPanel content = new JPanel(new BorderLayout(8, 8));
        content.setBorder(BorderFactory.createEmptyBorder(12, 14, 12, 14));
        content.add(new JLabel("Allow an AI client to control this editor through MCP."), BorderLayout.NORTH);
        connection.setEditable(false);
        connection.setLineWrap(true);
        connection.setWrapStyleWord(true);
        connection.setRows(5);
        clients.addTab("Codex", clientPanel("Merge into ~/.codex/config.toml, then reconnect or restart Codex.", codex));
        clients.addTab("Claude Code", clientPanel("Run this command once in a terminal, then check /mcp in Claude Code.", claudeCode));
        clients.addTab("Claude Desktop", clientPanel("Settings > Developer > Edit Config: merge this entry into claude_desktop_config.json, then restart Claude Desktop.", claudeDesktop));
        JPanel details = new JPanel(new BorderLayout(8, 8));
        details.add(new JScrollPane(connection), BorderLayout.NORTH);
        details.add(clients, BorderLayout.CENTER);
        content.add(details, BorderLayout.CENTER);
        JPanel controls = new JPanel(new FlowLayout(FlowLayout.LEFT));
        controls.add(status);
        controls.add(new JLabel("Port"));
        controls.add(port);
        toggle.addActionListener(event -> toggleServer());
        controls.add(toggle);
        copyUrl.addActionListener(event -> copy(server.endpoint()));
        controls.add(copyUrl);
        copyToken.addActionListener(event -> copy(server.token()));
        controls.add(copyToken);
        copySetup.addActionListener(event -> copy(switch (clients.getSelectedIndex()) {
            case 1 -> claudeCode.getText();
            case 2 -> claudeDesktop.getText();
            default -> codex.getText();
        }));
        controls.add(copySetup);
        JButton close = new JButton("Close");
        close.addActionListener(event -> dispose());
        controls.add(close);
        content.add(controls, BorderLayout.SOUTH);
        setContentPane(content);
        refresh();
        pack();
        setLocationRelativeTo(owner);
    }

    private void toggleServer() {
        try {
            if (server.isRunning()) {
                server.close();
                settings.disableMcp();
            } else {
                port.commitEdit();
                int selectedPort = (Integer) port.getValue();
                server.start(selectedPort, settings.getMcpToken());
                settings.rememberMcpConnection(selectedPort, server.token());
            }
            startupFailure = null;
            refresh();
        } catch (Exception ex) {
            startupFailure = ex.getMessage();
            refresh();
            JOptionPane.showMessageDialog(this, ex.getMessage(), "Cannot start MCP", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void refresh() {
        boolean running = server.isRunning();
        status.setText(running ? "Running on localhost" : "Stopped");
        status.setToolTipText(startupFailure);
        status.setPreferredSize(new java.awt.Dimension(155, status.getPreferredSize().height));
        if (running) port.setValue(java.net.URI.create(server.endpoint()).getPort());
        toggle.setText(running ? "Stop server" : "Start server");
        port.setEnabled(!running);
        copyUrl.setEnabled(running);
        copyToken.setEnabled(running);
        copySetup.setEnabled(running);
        connection.setText(running
                ? "Transport: Streamable HTTP\nURL: " + server.endpoint()
                    + "\nAvailable tools: " + String.join(", ", server.toolNames()) + ".\n"
                    + "The client can operate menus, fields, dialogs and the canvas. MCP starts automatically after application restart with the same connection settings. Stop server disables automatic startup."
                : (startupFailure == null ? "" : "MCP startup failed: " + startupFailure + "\n\n")
                    + "Start the server to connect an AI client to this running application.\n\n"
                    + "The client can read application windows and operate controls and the canvas, including opening, editing, running and saving models. "
                    + "Normal application dialogs and confirmations remain in place.\n\n"
                    + "The server accepts authenticated connections on 127.0.0.1 only. Its port and token are remembered so configured clients can reconnect after restart.");
        connection.setCaretPosition(0);
        String hint = "Start the server to see a ready-to-copy client setup.";
        codex.setText(running ? McpConnectionInstructions.codex(server.endpoint(), server.token()) : hint);
        claudeCode.setText(running ? McpConnectionInstructions.claudeCode(server.endpoint(), server.token()) : hint);
        claudeDesktop.setText(running ? McpConnectionInstructions.claudeDesktop(server.endpoint(), server.token()) : hint);
        for (JTextArea area : new JTextArea[]{codex, claudeCode, claudeDesktop}) area.setCaretPosition(0);
    }

    private static JTextArea clientText() {
        JTextArea area = new JTextArea(12, 68);
        area.setEditable(false);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        return area;
    }

    private static JPanel clientPanel(String instruction, JTextArea setup) {
        JPanel panel = new JPanel(new BorderLayout(6, 6));
        JTextArea hint = clientText();
        hint.setRows(2);
        hint.setText(instruction);
        panel.add(hint, BorderLayout.NORTH);
        panel.add(new JScrollPane(setup), BorderLayout.CENTER);
        return panel;
    }

    private static void copy(String text) {
        if (text != null) java.awt.Toolkit.getDefaultToolkit().getSystemClipboard()
                .setContents(new java.awt.datatransfer.StringSelection(text), null);
    }
}

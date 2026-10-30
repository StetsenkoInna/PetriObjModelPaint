package ua.stetsenkoinna.graphpresentation.training;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.util.ArrayList;
import java.util.List;
import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import ua.stetsenkoinna.graphpresentation.PetriNetsFrame;
import ua.stetsenkoinna.graphpresentation.io.FileDialogs;

/** Human review uses the same archive writer and revision format as the MCP tools. */
public final class TrainingReviewDialog extends JDialog {
    private final TrainingDataExporter exporter;
    private final JTextField reviewer = new JTextField(ua.stetsenkoinna.config.AppSettings.shared().getContributorProfile().nickname(), 24);
    private final JComboBox<String> decision = new JComboBox<>(new String[]{"changes_requested", "accepted", "rejected"});
    private final JComboBox<String> target = new JComboBox<>(new String[]{"Saved network from the archive", "Current working network"});
    private final JTextArea comment = new JTextArea(5, 65);
    private final DefaultTableModel findings = new DefaultTableModel(new String[]{"Importance", "Comment", "Element ID", "View ID", "Region", "Location"}, 0) {
        @Override public boolean isCellEditable(int row, int column) { return column < 2; }
    };
    private final List<String> targetIds = new ArrayList<>(List.of("loaded", "current"));
    private TrainingRevisionCanvasWindow reviewCanvas;
    private final TrainingHistoryPanel historyPanel;
    private final JButton save = new JButton("Save reviewed ZIP...");
    private final JCheckBox automaticSimulation = new JCheckBox("Automatically simulate the reviewed version before saving", true);
    private final JTextField simulationPeriods = new JTextField("100, 1000, 10000");
    private final JSpinner samplingInterval = new JSpinner(new SpinnerNumberModel(1.0, 0.001, 1000000.0, 1.0));
    private final JTextArea skipSimulationReason = new JTextArea(4, 55);
    private final List<java.util.Map<String, Object>> interactiveRuns = java.util.Collections.synchronizedList(new ArrayList<>());
    private final JTabbedPane tabs = new JTabbedPane();

    public TrainingReviewDialog(PetriNetsFrame owner, TrainingDataExporter exporter) {
        super(owner, "Review training bundle", true);
        this.exporter = exporter;
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        reviewer.getAccessibleContext().setAccessibleName("Reviewer name");
        decision.getAccessibleContext().setAccessibleName("Review decision");
        target.getAccessibleContext().setAccessibleName("Reviewed revision");
        if (exporter.workspace().active() == null) target.setSelectedIndex(1);
        comment.getAccessibleContext().setAccessibleName("Review comment");
        comment.setLineWrap(true); comment.setWrapStyleWord(true);
        decision.setRenderer(labels());
        JPanel form = new ReviewForm(); form.setLayout(new BoxLayout(form, BoxLayout.Y_AXIS));
        form.setBorder(BorderFactory.createEmptyBorder(10, 12, 10, 12));
        form.add(new JLabel("Reviewer")); form.add(reviewer);
        form.add(new JLabel("Network being reviewed")); form.add(target);
        JButton openCanvas = new JButton("Start review on live network");
        emphasize(openCanvas); openCanvas.addActionListener(event -> openReviewCanvas());
        JPanel start = new JPanel(new BorderLayout(8, 8));
        start.setBorder(BorderFactory.createEmptyBorder(12, 0, 12, 0));
        start.add(openCanvas, BorderLayout.WEST);
        start.add(new JLabel("Select an element or area on the network to add a finding."), BorderLayout.CENTER);
        start.setMaximumSize(new Dimension(Integer.MAX_VALUE, 80)); form.add(start);
        form.add(new JLabel("Review decision")); form.add(decision);
        form.add(new JLabel("Review comment"));
        JScrollPane commentScroll = new JScrollPane(comment); commentScroll.setMaximumSize(new Dimension(Integer.MAX_VALUE, 110));
        form.add(commentScroll);
        JTable table = new JTable(findings);
        table.setRowHeight(24);
        JComboBox<String> severity = new JComboBox<>(new String[]{"error", "warning", "suggestion"}); severity.setRenderer(labels());
        table.getColumnModel().getColumn(0).setCellEditor(new DefaultCellEditor(severity));
        table.getColumnModel().getColumn(0).setCellRenderer((grid, value, selected, focused, row, column) -> {
            var label = new javax.swing.table.DefaultTableCellRenderer();
            return label.getTableCellRendererComponent(grid, TrainingReviewPresentation.label(String.valueOf(value)), selected, focused, row, column);
        });
        // Keep source bindings in the model while exposing only meaningful columns to people.
        for (int column = 4; column >= 2; column--) table.removeColumn(table.getColumnModel().getColumn(column));
        JScrollPane findingScroll = new JScrollPane(table); findingScroll.setPreferredSize(new Dimension(800, 140));
        findingScroll.setMaximumSize(new Dimension(Integer.MAX_VALUE, 170));
        form.add(new JLabel("Findings added on the live network")); form.add(findingScroll);
        JPanel findingButtons = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton add = new JButton("Add comment"); add.addActionListener(event -> addFinding(this, null));
        JButton remove = new JButton("Remove comment"); remove.addActionListener(event -> { if (table.getSelectedRow() >= 0) findings.removeRow(table.getSelectedRow()); target.setEnabled(findings.getRowCount() == 0); });
        findingButtons.add(add); findingButtons.add(remove); form.add(findingButtons);
        form.add(new JLabel("Saving keeps the previous versions and adds your review."));
        for (var field : List.of(reviewer, target, decision)) field.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
        for (var component : form.getComponents()) if (component instanceof JComponent child) child.setAlignmentX(java.awt.Component.LEFT_ALIGNMENT);

        tabs.addTab("Review", new JScrollPane(form));
        JTextArea details = new JTextArea(); details.setEditable(false);
        var bundle = exporter.workspace().active();
        try {
            if (bundle != null) {
                for (var version : TrainingReviewPresentation.versions(bundle).entrySet()) {
                    targetIds.add(version.getKey()); target.addItem(version.getValue());
                }
            }
            details.setText(TrainingReviewPresentation.context(bundle)); details.setCaretPosition(0);
        } catch (Exception failure) { details.setText("Some task or review details could not be read."); }
        tabs.addTab("Simulation", simulationSettings());
        tabs.addTab("Task and history", new JScrollPane(details));
        historyPanel = new TrainingHistoryPanel(exporter.workspace()); historyPanel.setSimulationEvidenceListener(interactiveRuns::add);
        tabs.addTab("Versions and comparison", historyPanel);
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton close = new JButton("Close"); close.addActionListener(event -> dispose());
        save.addActionListener(event -> {
            if (table.isEditing() && !table.getCellEditor().stopCellEditing()) return;
            export();
        });
        emphasize(save); buttons.add(close); buttons.add(save);
        add(tabs, BorderLayout.CENTER); add(buttons, BorderLayout.SOUTH);
        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override public void windowClosed(java.awt.event.WindowEvent event) { if (reviewCanvas != null) reviewCanvas.dispose(); }
        });
        setSize(960, 700); setLocationRelativeTo(owner);
    }

    private void openReviewCanvas() {
        try {
            if ("current".equals(targetIds.get(target.getSelectedIndex()))) {
                JOptionPane.showMessageDialog(this, "Save the current network as a training archive first, then review its saved version."); return;
            }
            if ((reviewCanvas != null && reviewCanvas.isSimulationBusy()) || historyPanel.hasActiveSimulation()) { JOptionPane.showMessageDialog(this, "Stop or finish the live simulation first so its results can be preserved."); return; }
            if (reviewCanvas != null) reviewCanvas.dispose();
            String revision = targetIds.get(target.getSelectedIndex());
            reviewCanvas = new TrainingRevisionCanvasWindow(this, "Review - " + target.getSelectedItem(), exporter.workspace().revisionFiles(revision));
            final var window = reviewCanvas;
            window.setSimulationEvidenceListener(interactiveRuns::add);
            final int targetIndex = target.getSelectedIndex();
            window.enableFindings(selection -> {
                if (findings.getRowCount() > 0 && target.getSelectedIndex() != targetIndex) return;
                target.setSelectedIndex(targetIndex);
                addFinding(window, selection);
            });
            window.setLocationRelativeTo(this); window.setExtendedState(java.awt.Frame.MAXIMIZED_BOTH); window.setVisible(true);
        } catch (Exception failure) { JOptionPane.showMessageDialog(this, failure.getMessage(), "Cannot open review canvas", JOptionPane.ERROR_MESSAGE); }
    }

    private void addFinding(java.awt.Component owner, TrainingRevisionCanvasWindow.Selection selection) {
        JTextArea message = new JTextArea(6, 44); message.setLineWrap(true); message.setWrapStyleWord(true);
        message.getAccessibleContext().setAccessibleName("Finding comment");
        JComboBox<String> severity = new JComboBox<>(new String[]{"error", "warning", "suggestion"}); severity.setRenderer(labels());
        JPanel form = new JPanel(new BorderLayout(6, 6));
        form.add(severity, BorderLayout.NORTH); form.add(new JScrollPane(message), BorderLayout.CENTER);
        form.add(new JLabel(selection == null ? "Whole network" : selection.location()), BorderLayout.SOUTH);
        while (JOptionPane.showConfirmDialog(owner, form, "Add finding", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE) == JOptionPane.OK_OPTION) {
            if (message.getText().isBlank()) { JOptionPane.showMessageDialog(owner, "Write a comment for this finding."); continue; }
            findings.addRow(new Object[]{severity.getSelectedItem(), message.getText().strip(), selection == null ? "" : selection.elementId(),
                    selection == null ? "" : "main", selection == null ? null : selection.region(), selection == null ? "Whole network" : selection.location()});
            target.setEnabled(false); return;
        }
    }

    private void export() {
        if ((reviewCanvas != null && reviewCanvas.isSimulationBusy()) || historyPanel.hasActiveSimulation()) { JOptionPane.showMessageDialog(this, "Stop or finish the live simulation before saving so its results can be included."); return; }
        TrainingBundleWorkspace.Review review;
        TrainingExportRequest request;
        try {
            List<TrainingBundleWorkspace.Finding> rows = new ArrayList<>();
            for (int row = 0; row < findings.getRowCount(); row++) rows.add(new TrainingBundleWorkspace.Finding(
                    value(row, 0), value(row, 1), value(row, 2), value(row, 3), (TrainingBundleWorkspace.Region) findings.getValueAt(row, 4)));
            review = new TrainingBundleWorkspace.Review(reviewer.getText(), "human", (String) decision.getSelectedItem(), comment.getText(), rows, targetIds.get(target.getSelectedIndex()), simulationPlan());
            request = exporter.workspace().exportRequest(((PetriNetsFrame) getOwner()).getNameNet(), List.of());
        } catch (Exception failure) { JOptionPane.showMessageDialog(this, failure.getMessage(), "Review incomplete", JOptionPane.ERROR_MESSAGE); return; }
        var directory = FileDialogs.directory(this, "Save reviewed training ZIP", null);
        if (directory == null) return;
        save.setText("Running checks and saving..."); save.setEnabled(false); setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        new SwingWorker<TrainingDataExporter.ExportResult, Void>() {
            @Override protected TrainingDataExporter.ExportResult doInBackground() throws Exception { return exporter.export(directory.toPath(), request, review); }
            @Override protected void done() {
                try { var result = get(); dispose(); JOptionPane.showMessageDialog(getOwner(), "Saved review and revision history:\n" + result.path()); }
                catch (Exception failure) {
                    save.setText("Save reviewed ZIP..."); save.setEnabled(true); setDefaultCloseOperation(DISPOSE_ON_CLOSE);
                    JOptionPane.showMessageDialog(TrainingReviewDialog.this, (failure.getCause() == null ? failure : failure.getCause()).getMessage(), "Review export failed", JOptionPane.ERROR_MESSAGE);
                }
            }
        }.execute();
    }
    private JPanel simulationSettings() {
        JPanel panel = new JPanel(); panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS)); panel.setBorder(BorderFactory.createEmptyBorder(14,14,14,14));
        panel.add(automaticSimulation);
        panel.add(new JLabel("Simulation end times (each run starts at 0 on a fresh copy)")); panel.add(simulationPeriods);
        panel.add(new JLabel("Record tokens and active channels every N model-time units")); panel.add(samplingInterval);
        panel.add(new JLabel("Reason for skipping automatic simulation (required if disabled)")); panel.add(new JScrollPane(skipSimulationReason));
        panel.add(new JLabel("Runs, errors, time series and statistics are saved in the ZIP; execution does not certify task correctness."));
        skipSimulationReason.setLineWrap(true); skipSimulationReason.setWrapStyleWord(true); skipSimulationReason.setEnabled(false);
        automaticSimulation.addActionListener(event -> { simulationPeriods.setEnabled(automaticSimulation.isSelected()); samplingInterval.setEnabled(automaticSimulation.isSelected()); skipSimulationReason.setEnabled(!automaticSimulation.isSelected()); });
        automaticSimulation.getAccessibleContext().setAccessibleName("Automatic review simulation");
        skipSimulationReason.getAccessibleContext().setAccessibleName("Reason for skipping simulation");
        for (var field : List.of(simulationPeriods, samplingInterval)) field.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
        return panel;
    }
    private SimulationPlan simulationPlan() {
        List<Double> periods = automaticSimulation.isSelected() ? java.util.Arrays.stream(simulationPeriods.getText().split(",")).map(String::strip).map(Double::parseDouble).toList() : List.of();
        synchronized (interactiveRuns) { return new SimulationPlan(periods, ((Number) samplingInterval.getValue()).doubleValue(), automaticSimulation.isSelected() ? "" : skipSimulationReason.getText(), List.copyOf(interactiveRuns)); }
    }

    private String value(int row, int column) { Object value = findings.getValueAt(row, column); return value == null ? "" : value.toString(); }

    public void showVersionsAndComparison() {
        tabs.setSelectedIndex(tabs.indexOfTab("Versions and comparison"));
        setVisible(true);
    }

    private static void emphasize(AbstractButton button) {
        button.setFont(button.getFont().deriveFont(java.awt.Font.BOLD, 16f));
        button.setMargin(new java.awt.Insets(12, 20, 12, 20));
    }

    private static ListCellRenderer<? super String> labels() {
        return new DefaultListCellRenderer() {
            @Override public java.awt.Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean selected, boolean focused) {
                return super.getListCellRendererComponent(list, value == null ? "" : TrainingReviewPresentation.label(value.toString()), index, selected, focused);
            }
        };
    }

    private static final class ReviewForm extends JPanel implements Scrollable {
        @Override public Dimension getPreferredScrollableViewportSize() { return new Dimension(850, 560); }
        @Override public int getScrollableUnitIncrement(java.awt.Rectangle visible, int orientation, int direction) { return 24; }
        @Override public int getScrollableBlockIncrement(java.awt.Rectangle visible, int orientation, int direction) { return Math.max(24, visible.height - 24); }
        @Override public boolean getScrollableTracksViewportWidth() { return true; }
        @Override public boolean getScrollableTracksViewportHeight() { return false; }
    }
}

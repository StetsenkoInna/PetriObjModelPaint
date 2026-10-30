package ua.stetsenkoinna.graphpresentation.training;

import java.awt.BorderLayout;
import java.awt.GridLayout;
import javax.swing.*;

/** Browse saved revisions and open independent movable canvas windows. */
public final class TrainingHistoryPanel extends JPanel {
    private record Revision(String id, String label) { @Override public String toString() { return label; } }
    private final TrainingBundleWorkspace workspace;
    private final JComboBox<Revision> left = new JComboBox<>(), right = new JComboBox<>();
    private final JCheckBox layout = new JCheckBox("Include layout changes", true);
    private final JTextArea changes = new JTextArea();
    private TrainingRevisionCanvasWindow[] canvases;
    private java.util.function.Consumer<java.util.Map<String, Object>> simulationListener = record -> { };
    public void setSimulationEvidenceListener(java.util.function.Consumer<java.util.Map<String, Object>> listener) { simulationListener = listener; }

    public TrainingHistoryPanel(TrainingBundleWorkspace workspace) {
        super(new BorderLayout(6, 6));
        this.workspace = workspace;
        JPanel controls = new JPanel(new GridLayout(0, 2, 6, 4));
        controls.add(new JLabel("Earlier revision")); controls.add(new JLabel("Later revision"));
        controls.add(left); controls.add(right);
        left.getAccessibleContext().setAccessibleName("Earlier training revision");
        right.getAccessibleContext().setAccessibleName("Later training revision");
        JButton show = new JButton("Show on canvases");
        show.addActionListener(event -> openCanvases());
        controls.add(show); controls.add(layout);
        add(controls, BorderLayout.NORTH);
        changes.setEditable(false); add(new JScrollPane(changes), BorderLayout.CENTER);
        add(new JLabel("Open two movable read-only canvases; each supports pan, zoom and nested object navigation."), BorderLayout.SOUTH);
        try {
            var bundle = workspace.active();
            if (bundle != null) {
                for (var revision : TrainingReviewPresentation.versions(bundle).entrySet()) {
                    left.addItem(new Revision(revision.getKey(), revision.getValue())); right.addItem(new Revision(revision.getKey(), revision.getValue()));
                }
                if (left.getItemCount() == 0) { left.addItem(new Revision("head", "Saved version")); right.addItem(new Revision("head", "Saved version")); }
                right.setSelectedIndex(right.getItemCount() - 1);
                updateComparison();
            } else { show.setEnabled(false); changes.setText("Open a training ZIP to browse its saved versions."); }
        } catch (Exception failure) { changes.setText("Cannot read history: " + failure.getMessage()); }
        left.addActionListener(event -> updateComparison()); right.addActionListener(event -> updateComparison());
        layout.addActionListener(event -> updateComparison());
    }

    public boolean hasActiveSimulation() { return canvases != null && java.util.Arrays.stream(canvases).anyMatch(TrainingRevisionCanvasWindow::isSimulationBusy); }

    private void openCanvases() {
        try {
            if (hasActiveSimulation()) { JOptionPane.showMessageDialog(this, "Stop or finish the live comparison simulation first so its results can be preserved."); return; }
            if (left.getSelectedItem() == null || right.getSelectedItem() == null) return;
            var first = (Revision) left.getSelectedItem(); var second = (Revision) right.getSelectedItem();
            var opened = TrainingRevisionCanvasWindow.showPair(SwingUtilities.getWindowAncestor(this), workspace, first.id(), second.id(), first.label(), second.label());
            if (canvases != null) for (var canvas : canvases) canvas.dispose();
            canvases = opened;
            for (var canvas : canvases) canvas.setSimulationEvidenceListener(simulationListener);
        } catch (Exception failure) { JOptionPane.showMessageDialog(this, failure.getMessage(), "Cannot show revision canvases", JOptionPane.ERROR_MESSAGE); }
    }

    private void updateComparison() {
        try {
            if (left.getSelectedItem() == null || right.getSelectedItem() == null) return;
            var differences = workspace.compare(((Revision) left.getSelectedItem()).id(), ((Revision) right.getSelectedItem()).id(), layout.isSelected());
            changes.setText(TrainingReviewPresentation.differences(differences)); changes.setCaretPosition(0);
        } catch (Exception failure) { changes.setText("Cannot compare revisions: " + failure.getMessage()); }
    }
}

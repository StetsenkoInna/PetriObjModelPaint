package ua.stetsenkoinna.graphpresentation.training;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.io.File;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingWorker;
import ua.stetsenkoinna.graphpresentation.PetriNetsFrame;
import ua.stetsenkoinna.graphpresentation.io.FileDialogs;

public final class TrainingDataDialog extends JDialog {
    private final TrainingDataExporter exporter;
    private final JTextField title;
    private final JTextArea task = new JTextArea(5, 62);
    private final JComboBox<String> status = new JComboBox<>(new String[]{"unreviewed", "accepted", "rejected"});
    private final JCheckBox screenshot = new JCheckBox("Include a main-window screenshot for QA");
    private final JButton save = new JButton("Export ZIP...");
    private final JButton cancel = new JButton("Cancel");
    private final TrainingExportRequest inherited;

    public TrainingDataDialog(PetriNetsFrame owner, TrainingDataExporter exporter) {
        super(owner, "Export training data", true);
        this.exporter = exporter;
        try { inherited = exporter.workspace().exportRequest(owner.getNameNet(), List.of()); }
        catch (Exception failure) { throw new IllegalArgumentException("Cannot read training context", failure); }
        title = new JTextField(owner.getNameNet(), 38);
        title.getAccessibleContext().setAccessibleName("Training title");
        task.getAccessibleContext().setAccessibleName("Training task");
        status.getAccessibleContext().setAccessibleName("Human review status");
        task.setText(inherited.task() == null ? "" : inherited.task());
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        JPanel form = new JPanel();
        form.setLayout(new BoxLayout(form, BoxLayout.Y_AXIS));
        form.setBorder(BorderFactory.createEmptyBorder(12, 14, 8, 14));
        form.add(new JLabel("Title"));
        form.add(title);
        form.add(Box.createVerticalStrut(8));
        form.add(new JLabel("Task description"));
        task.setLineWrap(true);
        task.setWrapStyleWord(true);
        form.add(new JScrollPane(task));
        form.add(Box.createVerticalStrut(8));
        JPanel review = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        review.add(new JLabel("Human review status: "));
        review.add(status);
        form.add(review);
        form.add(screenshot);
        JTextArea note = new JTextArea("The bundle contains the complete model, expanded images of every object and preserved revision/review history. It is unreviewed by default. "
                + "Exporting does not verify correctness. External conversations are included only when supplied through MCP.", 3, 62);
        note.setEditable(false);
        note.setLineWrap(true);
        note.setWrapStyleWord(true);
        note.setOpaque(false);
        form.add(Box.createVerticalStrut(8));
        form.add(note);
        setLayout(new BorderLayout());
        add(form, BorderLayout.CENTER);
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        cancel.addActionListener(event -> dispose());
        save.addActionListener(event -> export());
        buttons.add(cancel);
        buttons.add(save);
        add(buttons, BorderLayout.SOUTH);
        getRootPane().setDefaultButton(save);
        pack();
        setLocationRelativeTo(owner);
    }

    private void export() {
        TrainingExportRequest request;
        try {
            request = new TrainingExportRequest(title.getText(), task.getText(), inherited.clarifications(), inherited.assumptions(), inherited.acceptance(),
                    (String) status.getSelectedItem(), inherited.conversation(), screenshot.isSelected());
        } catch (IllegalArgumentException failure) {
            JOptionPane.showMessageDialog(this, failure.getMessage(), "Export training data", JOptionPane.ERROR_MESSAGE);
            return;
        }
        File directory = FileDialogs.directory(this, "Export training data to folder", null);
        if (directory == null) return;
        save.setEnabled(false);
        cancel.setEnabled(false);
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        new SwingWorker<TrainingDataExporter.ExportResult, Void>() {
            @Override protected TrainingDataExporter.ExportResult doInBackground() throws Exception {
                return exporter.export(directory.toPath(), request);
            }
            @Override protected void done() {
                try {
                    TrainingDataExporter.ExportResult result = get();
                    dispose();
                    JOptionPane.showMessageDialog(getOwner(), "Saved " + result.status() + " training bundle:\n" + result.path());
                } catch (Exception failure) {
                    save.setEnabled(true);
                    cancel.setEnabled(true);
                    setDefaultCloseOperation(DISPOSE_ON_CLOSE);
                    Throwable cause = failure.getCause() == null ? failure : failure.getCause();
                    JOptionPane.showMessageDialog(TrainingDataDialog.this, cause.getMessage(), "Export failed", JOptionPane.ERROR_MESSAGE);
                }
            }
        }.execute();
    }
}

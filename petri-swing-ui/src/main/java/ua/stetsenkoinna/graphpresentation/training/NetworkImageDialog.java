package ua.stetsenkoinna.graphpresentation.training;

import java.io.File;
import javax.swing.JOptionPane;
import javax.swing.SwingWorker;
import javax.swing.*;
import java.util.List;
import java.util.Map;
import ua.stetsenkoinna.graphpresentation.PetriNetsFrame;
import ua.stetsenkoinna.graphpresentation.io.FileDialogs;

public final class NetworkImageDialog {
    private static final FileDialogs.FileKind PNG = new FileDialogs.FileKind("PNG image", "png");
    private NetworkImageDialog() { }

    public static void export(PetriNetsFrame owner) {
        JComboBox<String> scope = new JComboBox<>(new String[]{"Current canvas view", "Complete model, all objects expanded", "Selected object networks", "Selected places, transitions and objects"});
        JComboBox<String> format = new JComboBox<>(new String[]{"PNG image", "ZIP with images and geometry"});
        var frames = owner.getPetriNetsPanel().getCanvasModel().getFrames();
        String[] names = new String[frames.size()];
        for (int index = 0; index < frames.size(); index++) names[index] = "O" + index + "  " + frames.get(index).getName();
        JList<String> objects = new JList<>(names);
        objects.setVisibleRowCount(Math.min(7, Math.max(2, names.length)));
        objects.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        if (names.length > 0) objects.setSelectionInterval(0, names.length - 1);
        objects.setEnabled(false);
        JCheckBox overview = new JCheckBox("Also include the complete expanded model", true);
        overview.setEnabled(false);
        JSpinner scale = new JSpinner(new SpinnerNumberModel(1.0, 0.1, 4.0, 0.25));
        JSpinner maximum = new JSpinner(new SpinnerNumberModel(8192, 256, 8192, 256));
        JCheckBox parameters = new JCheckBox("Include model parameter table", true);
        JCheckBox transparent = new JCheckBox("Transparent background");
        scope.getAccessibleContext().setAccessibleName("Image scope");
        format.getAccessibleContext().setAccessibleName("Image file format");
        objects.getAccessibleContext().setAccessibleName("Object networks to export");
        scale.getAccessibleContext().setAccessibleName("Image scale");
        maximum.getAccessibleContext().setAccessibleName("Maximum image dimension");
        scope.addActionListener(event -> {
            boolean selecting = scope.getSelectedIndex() == 2;
            objects.setEnabled(selecting); overview.setEnabled(selecting);
            if (selecting) format.setSelectedIndex(1);
        });
        JPanel settings = new JPanel(); settings.setLayout(new BoxLayout(settings, BoxLayout.Y_AXIS));
        settings.add(new JLabel("What to export")); settings.add(scope);
        settings.add(new JScrollPane(objects)); settings.add(overview);
        settings.add(new JLabel("File format")); settings.add(format);
        settings.add(new JLabel("Scale (1 = original; capped by image limits)")); settings.add(scale);
        settings.add(new JLabel("Maximum width/height in pixels (up to 24 megapixels total)")); settings.add(maximum);
        settings.add(transparent); settings.add(parameters);
        if (JOptionPane.showConfirmDialog(owner, settings, "Export network images", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE) != JOptionPane.OK_OPTION) return;
        if (scope.getSelectedIndex() == 2 && objects.isSelectionEmpty() && !overview.isSelected()) {
            JOptionPane.showMessageDialog(owner, "Select at least one object or include the overview."); return;
        }
        boolean zip = format.getSelectedIndex() == 1;
        File target = FileDialogs.save(owner, "Export network images", zip ? new FileDialogs.FileKind("Image ZIP", "zip") : PNG,
                null, owner.getNameNet() + (zip ? ".zip" : ".png"));
        if (target == null) return;
        if (target.exists() && JOptionPane.showConfirmDialog(owner, "Replace existing image?\n" + target,
                "Export image", JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) return;
        NetworkImageExporter images = new NetworkImageExporter();
        Map<String, NetworkImageExporter.RenderedImage> captured;
        try {
            String mode = List.of("active", "document", "objects", "selection").get(scope.getSelectedIndex());
            List<Integer> selected = java.util.Arrays.stream(objects.getSelectedIndices()).boxed().toList();
            captured = images.renderConfigured(owner.getPetriNetsPanel(), mode, selected, overview.isSelected(),
                    new NetworkImageExporter.Options(((Number) scale.getValue()).doubleValue(), transparent.isSelected(), ((Number) maximum.getValue()).intValue(), parameters.isSelected()));
            if (!zip && captured.size() != 1) throw new IllegalArgumentException("Choose ZIP for multiple images, or select one object without the overview");
        } catch (Exception failure) {
            JOptionPane.showMessageDialog(owner, failure.getMessage(), "Cannot export image", JOptionPane.ERROR_MESSAGE);
            return;
        }
        new SwingWorker<Void, Void>() {
            @Override protected Void doInBackground() throws Exception {
                images.writeImages(target.toPath(), captured, true);
                return null;
            }
            @Override protected void done() {
                try {
                    get();
                    JOptionPane.showMessageDialog(owner, "Saved " + captured.size() + " image(s):\n" + target.getAbsolutePath());
                } catch (Exception failure) {
                    Throwable cause = failure.getCause() == null ? failure : failure.getCause();
                    JOptionPane.showMessageDialog(owner, cause.getMessage(), "Cannot export image", JOptionPane.ERROR_MESSAGE);
                }
            }
        }.execute();
    }
}

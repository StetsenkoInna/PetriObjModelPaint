package ua.stetsenkoinna.graphpresentation.training;

import java.io.File;
import javax.swing.JOptionPane;
import javax.swing.SwingWorker;
import ua.stetsenkoinna.graphpresentation.PetriNetsFrame;
import ua.stetsenkoinna.graphpresentation.io.FileDialogs;

public final class NetworkImageDialog {
    private static final FileDialogs.FileKind PNG = new FileDialogs.FileKind("PNG image", "png");
    private NetworkImageDialog() { }

    public static void export(PetriNetsFrame owner) {
        File target = FileDialogs.save(owner, "Export network image", PNG, null, owner.getNameNet() + ".png");
        if (target == null) return;
        if (target.exists() && JOptionPane.showConfirmDialog(owner, "Replace existing image?\n" + target,
                "Export image", JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) return;
        NetworkImageExporter images = new NetworkImageExporter();
        NetworkImageExporter.RenderedImage captured;
        try {
            captured = images.render(owner.getPetriNetsPanel());
        } catch (Exception failure) {
            JOptionPane.showMessageDialog(owner, failure.getMessage(), "Cannot export image", JOptionPane.ERROR_MESSAGE);
            return;
        }
        new SwingWorker<Void, Void>() {
            @Override protected Void doInBackground() throws Exception {
                images.write(target.toPath(), captured, true);
                return null;
            }
            @Override protected void done() {
                try {
                    get();
                    JOptionPane.showMessageDialog(owner, "Saved network image:\n" + target.getAbsolutePath());
                } catch (Exception failure) {
                    Throwable cause = failure.getCause() == null ? failure : failure.getCause();
                    JOptionPane.showMessageDialog(owner, cause.getMessage(), "Cannot export image", JOptionPane.ERROR_MESSAGE);
                }
            }
        }.execute();
    }
}

package ua.stetsenkoinna;

import ua.stetsenkoinna.graphpresentation.PetriNetsFrame;
import ua.stetsenkoinna.utils.MessageHelper;

import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.JFrame;
import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;

/**
 * Entry point of the PetriObjModelPaint desktop editor (Swing UI).
 * Configured as the executable JAR's {@code Main-Class} (see petri-swing-ui/pom.xml).
 */
public class PetriObjPaintApp {

    public static void main(String[] args) {
        applyNimbusLookAndFeel();

        java.awt.EventQueue.invokeLater(() -> {
            PetriNetsFrame frame = new PetriNetsFrame();
            frame.setExtendedState(JFrame.MAXIMIZED_BOTH);

            // Set the main frame as default parent for MessageHelper dialogs
            MessageHelper.setDefaultParent(frame);

            frame.setVisible(true);
        });
    }

    private static void applyNimbusLookAndFeel() {
        try {
            for (UIManager.LookAndFeelInfo info : UIManager.getInstalledLookAndFeels()) {
                if ("Nimbus".equals(info.getName())) {
                    UIManager.setLookAndFeel(info.getClassName());
                    break;
                }
            }
        } catch (ClassNotFoundException | InstantiationException | IllegalAccessException
                 | UnsupportedLookAndFeelException ex) {
            Logger.getLogger(PetriObjPaintApp.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
}

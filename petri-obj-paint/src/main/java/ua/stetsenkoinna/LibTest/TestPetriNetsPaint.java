/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package ua.stetsenkoinna.LibTest;

import ua.stetsenkoinna.graphpresentation.PetriNetsFrame;
import ua.stetsenkoinna.utils.MessageHelper;

/**
 *
 * @author innastetsenko
 */
public class TestPetriNetsPaint {
    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) {

        try {
            for (javax.swing.UIManager.LookAndFeelInfo info : javax.swing.UIManager
                    .getInstalledLookAndFeels()) {
                if ("Nimbus".equals(info.getName())) {
                    javax.swing.UIManager.setLookAndFeel(info.getClassName());
                    break;
                }
            }
        } catch (ClassNotFoundException | InstantiationException | IllegalAccessException | javax.swing.UnsupportedLookAndFeelException ex) {
            java.util.logging.Logger.getLogger(PetriNetsFrame.class.getName())
                    .log(java.util.logging.Level.SEVERE, null, ex);
        }
		/* Create and display the form */
        java.awt.EventQueue.invokeLater(() -> {
            PetriNetsFrame frame = new PetriNetsFrame();
            frame.setExtendedState(javax.swing.JFrame.MAXIMIZED_BOTH);

            // Set the main frame as default parent for MessageHelper dialogs
            MessageHelper.setDefaultParent(frame);

            frame.setVisible(true);
        });

    }
}

package ua.stetsenkoinna.pnml;

import ua.stetsenkoinna.petriobj.PetriNet;
import ua.stetsenkoinna.graphnet.GraphPetriNet;
import ua.stetsenkoinna.graphpresentation.io.FileDialogs;
import ua.stetsenkoinna.utils.MessageHelper;

import javax.swing.*;
import java.io.File;

/**
 * Utility class for PNML import/export operations
 *
 * @author Serhii Rybak
 */
public class PnmlUtils {

    /**
     * Import PetriNet from PNML file with file chooser dialog
     *
     * @param parent Parent component for dialog
     * @return ImportResult containing PetriNet and coordinate data or null if cancelled/failed
     */
    public static ImportResult importFromFile(java.awt.Component parent) {
        File selectedFile = FileDialogs.open(parent, "Import PNML", FileDialogs.PNML,
                new File(System.getProperty("user.home")));
        return selectedFile == null ? null : importFromFile(selectedFile, parent);
    }

    /**
     * Import PetriNet from PNML file with file chooser dialog (legacy method)
     *
     * @param parent Parent component for dialog
     * @return PetriNet object or null if cancelled/failed
     */
    public static PetriNet importPetriNetFromFile(java.awt.Component parent) {
        ImportResult result = importFromFile(parent);
        return result != null ? result.getPetriNet() : null;
    }

    /**
     * Import PetriNet from specific PNML file
     *
     * @param file   PNML file to import
     * @param parent Parent component for error dialogs
     * @return ImportResult containing PetriNet and coordinate data or null if failed
     */
    public static ImportResult importFromFile(File file, java.awt.Component parent) {
        try {
            PnmlParser parser = new PnmlParser();
            PetriNet petriNet = parser.parse(file);
            MessageHelper.showInfo(parent,
                    "PNML file imported successfully!\n" +
                            "Places: " + petriNet.getListP().length + "\n" +
                            "Transitions: " + petriNet.getListT().length + "\n" +
                            "Input Arcs: " + petriNet.getArcIn().length + "\n" +
                            "Output Arcs: " + petriNet.getArcOut().length);
            ImportResult result = new ImportResult(petriNet, parser);
            MessageHelper.showImportWarnings(parent, result.getWarnings());
            return result;
        } catch (Exception e) {
            MessageHelper.showException(parent, "Error importing PNML file: " + file.getName(), e);
            return null;
        }
    }

    /**
     * Export PetriNet to PNML file with file chooser dialog
     *
     * @param petriNet      PetriNet to export
     * @param parent        Parent component for dialog
     * @param graphPetriNet GraphPetriNet for coordinate information (optional)
     * @return true if export successful, false otherwise
     */
    public static boolean exportToFile(PetriNet petriNet, java.awt.Component parent, GraphPetriNet graphPetriNet) {
        if (petriNet == null) {
            MessageHelper.showError(parent, "No Petri net to export");
            return false;
        }

        String defaultName = petriNet.getName() != null && !petriNet.getName().isEmpty()
                ? petriNet.getName() + ".pnml"
                : "petri_net.pnml";

        // The extension is appended by the dialog helper now, for every command rather than the
        // ones whose author remembered to.
        File selectedFile = FileDialogs.save(parent, "Export PNML", FileDialogs.PNML,
                new File(System.getProperty("user.home")), defaultName);
        return selectedFile != null && exportToFile(petriNet, selectedFile, parent, graphPetriNet);
    }

    /**
     * Export PetriNet to PNML file with file chooser dialog (legacy method)
     *
     * @param petriNet PetriNet to export
     * @param parent   Parent component for dialog
     * @return true if export successful, false otherwise
     */
    public static boolean exportToFile(PetriNet petriNet, java.awt.Component parent) {
        return exportToFile(petriNet, parent, null);
    }

    /**
     * Export PetriNet to specific PNML file
     *
     * @param petriNet      PetriNet to export
     * @param file          Output file
     * @param parent        Parent component for error dialogs
     * @param graphPetriNet GraphPetriNet for coordinate information (optional)
     * @return true if export successful, false otherwise
     */
    public static boolean exportToFile(PetriNet petriNet, File file, java.awt.Component parent, GraphPetriNet graphPetriNet) {
        try {
            // Check if file exists and ask for confirmation
            if (file.exists()) {
                if (!MessageHelper.showConfirmation(parent,
                        "File already exists. Do you want to overwrite it?")) {
                    return false;
                }
            }

            PnmlGenerator generator = new PnmlGenerator();
            generator.generate(petriNet, file, graphPetriNet);

            MessageHelper.showInfo(parent,
                    "PNML file exported successfully to:\n" + file.getAbsolutePath());
            return true;
        } catch (Exception e) {
            MessageHelper.showException(parent, "Error exporting PNML file", e);
            return false;
        }
    }

    /**
     * Export PetriNet to specific PNML file (legacy method)
     *
     * @param petriNet PetriNet to export
     * @param file     Output file
     * @param parent   Parent component for error dialogs
     * @return true if export successful, false otherwise
     */
    public static boolean exportToFile(PetriNet petriNet, File file, java.awt.Component parent) {
        return exportToFile(petriNet, file, parent, null);
    }

    /**
     * Validate PNML file format
     *
     * @param file PNML file to validate
     * @return true if valid, false otherwise
     */
    public static boolean validatePnmlFile(File file) {
        try {
            PnmlParser parser = new PnmlParser();
            parser.parse(file);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
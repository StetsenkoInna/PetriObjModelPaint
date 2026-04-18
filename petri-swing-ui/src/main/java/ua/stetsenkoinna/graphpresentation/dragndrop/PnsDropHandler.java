package ua.stetsenkoinna.graphpresentation.dragndrop;

import ua.stetsenkoinna.graphnet.GraphPetriNet;
import ua.stetsenkoinna.graphnet.GraphPetriTransition;
import ua.stetsenkoinna.graphpresentation.FileUse;
import ua.stetsenkoinna.graphpresentation.PetriNetsPanel;
import ua.stetsenkoinna.PetriObj.PetriNet;
import ua.stetsenkoinna.utils.MessageHelper;

import javax.swing.*;
import java.awt.Point;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.dnd.*;
import java.io.File;
import java.io.FileInputStream;
import java.io.ObjectInputStream;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Handler for PNS (Petri Net Serialized) file drag and drop operations.
 * PNS is the native serialized format for saving GraphPetriNet objects.
 *
 * This handler implements the complete import logic for .pns files,
 * supporting both GraphPetriNet and legacy PetriNet formats.
 *
 * @author Serhii Rybak
 */
public class PnsDropHandler implements DropTargetListener {

    private static final Logger LOGGER = Logger.getLogger(PnsDropHandler.class.getName());
    private static final String FILE_EXTENSION = ".pns";
    private static final long MIN_FILE_SIZE = 50; // Minimum size for a valid serialized object

    private final PetriNetsPanel panel;
    private final JFrame parentFrame;
    private final FileUse fileUse;

    /**
     * Creates a new PNS drop handler.
     *
     * @param panel The PetriNetsPanel where imported nets will be displayed
     * @param parentFrame The parent frame for displaying dialogs
     */
    public PnsDropHandler(PetriNetsPanel panel, JFrame parentFrame) {
        if (panel == null) {
            throw new IllegalArgumentException("PetriNetsPanel cannot be null");
        }
        if (parentFrame == null) {
            throw new IllegalArgumentException("Parent frame cannot be null");
        }
        this.panel = panel;
        this.parentFrame = parentFrame;
        this.fileUse = new FileUse();
    }

    @Override
    public void dragEnter(DropTargetDragEvent dtde) {
        if (isDragAcceptable(dtde)) {
            dtde.acceptDrag(DnDConstants.ACTION_COPY);
        } else {
            dtde.rejectDrag();
        }
    }

    @Override
    public void dragOver(DropTargetDragEvent dtde) {
        // Visual feedback could be added here in future
    }

    @Override
    public void dropActionChanged(DropTargetDragEvent dtde) {
        if (isDragAcceptable(dtde)) {
            dtde.acceptDrag(DnDConstants.ACTION_COPY);
        } else {
            dtde.rejectDrag();
        }
    }

    @Override
    public void dragExit(DropTargetEvent dte) {
        // Clean up visual feedback if any
    }

    @Override
    public void drop(DropTargetDropEvent dtde) {
        try {
            Transferable transferable = dtde.getTransferable();

            if (!transferable.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
                dtde.rejectDrop();
                return;
            }

            dtde.acceptDrop(DnDConstants.ACTION_COPY);

            @SuppressWarnings("unchecked")
            List<File> droppedFiles = (List<File>) transferable.getTransferData(DataFlavor.javaFileListFlavor);

            Point dropLocation = dtde.getLocation();
            boolean atLeastOneSuccess = false;

            for (File file : droppedFiles) {
                if (isPnsFile(file)) {
                    if (importPnsFile(file, dropLocation)) {
                        atLeastOneSuccess = true;
                    }
                }
            }

            dtde.dropComplete(atLeastOneSuccess);

        } catch (Exception ex) {
            LOGGER.log(Level.SEVERE, "Error during file drop", ex);
            MessageHelper.showException(parentFrame, "Error during file drop", ex);
            dtde.dropComplete(false);
        }
    }

    /**
     * Checks if the drag operation contains acceptable data.
     *
     * @param dtde The drag event
     * @return true if the drag contains file list data flavor
     */
    private boolean isDragAcceptable(DropTargetDragEvent dtde) {
        return dtde.isDataFlavorSupported(DataFlavor.javaFileListFlavor);
    }

    /**
     * Checks if the file is a valid PNS file.
     *
     * @param file The file to check
     * @return true if the file exists, is readable, and has .pns extension
     */
    private boolean isPnsFile(File file) {
        if (file == null || !file.exists() || !file.isFile()) {
            return false;
        }
        String name = file.getName().toLowerCase();
        return name.endsWith(FILE_EXTENSION);
    }

    /**
     * Imports a PNS file and places the net on the workspace.
     * This method replicates the logic from FileUse.openFile() with additional
     * support for positioning the imported net at the drop location.
     *
     * @param file The PNS file to import
     * @param dropLocation The location where the file was dropped
     * @return true if import was successful, false otherwise
     */
    private boolean importPnsFile(File file, Point dropLocation) {
        // Validate file
        if (!validateFile(file)) {
            return false;
        }

        try (FileInputStream fis = new FileInputStream(file);
             ObjectInputStream ois = new ObjectInputStream(fis)) {

            // Deserialize object
            Object loadedObject = ois.readObject();
            GraphPetriNet net = processLoadedObject(loadedObject, file, dropLocation);

            if (net == null) {
                return false;
            }

            // Check for transitions with non-zero buffers and ask user if they want to clear them
            handleNonZeroBuffers(net);

            // Position the net at drop location if specified
            // (Note: for legacy format, the position is already applied during conversion)
            if (dropLocation != null && loadedObject instanceof GraphPetriNet) {
                net.changeLocation(dropLocation);
            }

            // Add the net to panel
            panel.addGraphNet(net);
            panel.repaint();

            LOGGER.log(Level.INFO, "Successfully imported PNS file: {0}", file.getName());
            return true;

        } catch (java.io.FileNotFoundException e) {
            MessageHelper.showError(parentFrame, "File not found: " + file.getAbsolutePath());
            LOGGER.log(Level.WARNING, "File not found: " + file.getAbsolutePath(), e);
            return false;

        } catch (ClassNotFoundException ex) {
            MessageHelper.showException(parentFrame,
                "Cannot open file: incompatible file format or missing classes\nFile: " + file.getName(), ex);
            LOGGER.log(Level.SEVERE, "Class not found while loading file: " + file.getName(), ex);
            return false;

        } catch (java.io.EOFException ex) {
            MessageHelper.showError(parentFrame,
                "Error reading file: The file appears to be corrupted or incomplete.\n\n" +
                "File: " + file.getName() + "\n\n" +
                "Possible causes:\n" +
                "• File was not saved properly\n" +
                "• File was created with a different version of the application\n" +
                "• File was damaged or truncated\n" +
                "• Network interruption during file transfer\n\n" +
                "Please try:\n" +
                "• Using a backup copy of the file\n" +
                "• Re-saving the file from the original source\n" +
                "• Importing from PNML format instead (File → Import PNML)");
            LOGGER.log(Level.SEVERE, "EOF error during file reading: " + file.getName(), ex);
            return false;

        } catch (java.io.IOException ex) {
            MessageHelper.showException(parentFrame, "Error reading file: " + file.getName(), ex);
            LOGGER.log(Level.SEVERE, "IO error while reading file: " + file.getName(), ex);
            return false;

        } catch (CloneNotSupportedException ex) {
            MessageHelper.showException(parentFrame, "Error processing file data: " + file.getName(), ex);
            LOGGER.log(Level.SEVERE, "Clone not supported error: " + file.getName(), ex);
            return false;

        } catch (ClassCastException ex) {
            MessageHelper.showException(parentFrame,
                "Unsupported file format: " + file.getName() + "\n\n" +
                "Expected GraphPetriNet or PetriNet, but found incompatible type.", ex);
            LOGGER.log(Level.SEVERE, "Unsupported file format: " + file.getName(), ex);
            return false;

        } catch (Exception ex) {
            MessageHelper.showException(parentFrame, "Unexpected error importing file: " + file.getName(), ex);
            LOGGER.log(Level.SEVERE, "Unexpected error during import: " + file.getName(), ex);
            return false;
        }
    }

    /**
     * Validates that a file is readable and has minimum required size.
     *
     * @param file The file to validate
     * @return true if file is valid, false otherwise
     */
    private boolean validateFile(File file) {
        if (!file.exists()) {
            MessageHelper.showError(parentFrame, "File does not exist: " + file.getAbsolutePath());
            return false;
        }

        if (!file.canRead()) {
            MessageHelper.showError(parentFrame, "Cannot read file: " + file.getAbsolutePath());
            return false;
        }

        if (file.length() == 0) {
            MessageHelper.showError(parentFrame, "File is empty: " + file.getName());
            return false;
        }

        if (file.length() < MIN_FILE_SIZE) {
            MessageHelper.showError(parentFrame,
                "File appears to be corrupted or incomplete (too small): " + file.getName() +
                "\nFile size: " + file.length() + " bytes");
            return false;
        }

        return true;
    }

    /**
     * Processes the loaded object, handling both GraphPetriNet and legacy PetriNet formats.
     *
     * @param loadedObject The deserialized object
     * @param file The source file (for error messages)
     * @param dropLocation The location where file was dropped (used for centering legacy format)
     * @return GraphPetriNet instance or null if processing failed
     * @throws CloneNotSupportedException if cloning fails
     */
    private GraphPetriNet processLoadedObject(Object loadedObject, File file, Point dropLocation)
            throws CloneNotSupportedException {
        if (loadedObject instanceof GraphPetriNet) {
            // Clone to avoid modifying the original loaded object
            return ((GraphPetriNet) loadedObject).clone();

        } else if (loadedObject instanceof PetriNet) {
            // Legacy format: convert PetriNet to GraphPetriNet
            // The layout will be auto-generated using the same algorithm as File → Open
            PetriNet petriNet = (PetriNet) loadedObject;

            LOGGER.log(Level.INFO, "Converting legacy PetriNet format for file: {0}", file.getName());

            // Use drop location as the center point for the generated layout
            Point centerPoint = dropLocation != null ? dropLocation : new Point(400, 300);

            // Convert using FileUse.generateGraphNetBySimpleNet
            GraphPetriNet graphNet = fileUse.generateGraphNetBySimpleNet(panel, petriNet, centerPoint);

            if (graphNet != null) {
                LOGGER.log(Level.INFO, "Successfully converted legacy format to GraphPetriNet");
            }

            return graphNet;

        } else {
            throw new ClassCastException("Unsupported file format. Expected GraphPetriNet or PetriNet, but found: "
                + loadedObject.getClass().getName());
        }
    }

    /**
     * Checks for transitions with non-zero buffers and prompts user to clear them if found.
     * This is important for ensuring clean simulation state.
     *
     * @param net The GraphPetriNet to check
     */
    private void handleNonZeroBuffers(GraphPetriNet net) {
        GraphPetriTransition[] transitionsWithNonZeroBuffers = net.getGraphPetriTransitionList().stream()
            .filter(transition -> transition.getPetriTransition().getBuffer() != 0)
            .toArray(GraphPetriTransition[]::new);

        if (transitionsWithNonZeroBuffers.length > 0) {
            boolean shouldClear = MessageHelper.showConfirmation(parentFrame,
                "Non-empty buffers detected in imported net.\n\n" +
                "Found " + transitionsWithNonZeroBuffers.length + " transition(s) with non-empty buffers.\n" +
                "Do you want to clear them to start with a clean state?");

            if (shouldClear) {
                for (GraphPetriTransition trans : transitionsWithNonZeroBuffers) {
                    // Clear all saved exit times
                    trans.getPetriTransition().getTimeOut().clear();
                    trans.getPetriTransition().getTimeOut().add(Double.MAX_VALUE);
                    trans.getPetriTransition().setBuffer(0);
                }
                LOGGER.log(Level.INFO, "Cleared buffers for {0} transitions", transitionsWithNonZeroBuffers.length);
            }
        }
    }
}
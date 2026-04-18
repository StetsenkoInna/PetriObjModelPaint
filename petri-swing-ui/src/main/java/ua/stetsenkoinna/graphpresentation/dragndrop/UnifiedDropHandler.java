package ua.stetsenkoinna.graphpresentation.dragndrop;

import ua.stetsenkoinna.graphpresentation.PetriNetsPanel;
import ua.stetsenkoinna.utils.MessageHelper;

import javax.swing.*;
import java.awt.Point;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.dnd.*;
import java.io.File;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Unified handler for multiple file format drag and drop operations.
 * Supports both PNML and PNS file formats by delegating to specialized handlers.
 *
 * This unified approach allows multiple file types to be handled by a single
 * DropTarget, avoiding conflicts when multiple handlers are registered.
 *
 * @author Serhii Rybak
 */
public class UnifiedDropHandler implements DropTargetListener {

    private static final Logger LOGGER = Logger.getLogger(UnifiedDropHandler.class.getName());

    private final PetriNetsPanel panel;
    private final JFrame parentFrame;
    private final PnmlDropHandler pnmlHandler;
    private final PnsDropHandler pnsHandler;

    /**
     * Creates a unified drop handler supporting multiple file formats.
     *
     * @param panel The PetriNetsPanel where imported nets will be displayed
     * @param parentFrame The parent frame for displaying dialogs
     */
    public UnifiedDropHandler(PetriNetsPanel panel, JFrame parentFrame) {
        if (panel == null) {
            throw new IllegalArgumentException("PetriNetsPanel cannot be null");
        }
        if (parentFrame == null) {
            throw new IllegalArgumentException("Parent frame cannot be null");
        }
        this.panel = panel;
        this.parentFrame = parentFrame;

        // Create specialized handlers for delegation
        this.pnmlHandler = new PnmlDropHandler(panel, parentFrame);
        this.pnsHandler = new PnsDropHandler(panel, parentFrame);
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

            // Process each dropped file
            for (File file : droppedFiles) {
                String fileName = file.getName().toLowerCase();

                if (fileName.endsWith(".pnml")) {
                    // Delegate to PNML handler
                    LOGGER.log(Level.INFO, "Processing PNML file: {0}", file.getName());
                    if (importPnmlFile(file, dropLocation)) {
                        atLeastOneSuccess = true;
                    }

                } else if (fileName.endsWith(".pns")) {
                    // Delegate to PNS handler
                    LOGGER.log(Level.INFO, "Processing PNS file: {0}", file.getName());
                    if (importPnsFile(file, dropLocation)) {
                        atLeastOneSuccess = true;
                    }

                } else {
                    // Unsupported file type
                    LOGGER.log(Level.WARNING, "Unsupported file type: {0}", file.getName());
                    MessageHelper.showError(parentFrame,
                        "Unsupported file type: " + file.getName() + "\n\n" +
                        "Supported formats:\n" +
                        "  • .pnml (Petri Net Markup Language)\n" +
                        "  • .pns (Petri Net Serialized)");
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
     * Import a PNML file using the specialized handler.
     *
     * @param file The PNML file to import
     * @param dropLocation The location where the file was dropped
     * @return true if import was successful, false otherwise
     */
    private boolean importPnmlFile(File file, Point dropLocation) {
        try {
            // Create a synthetic drop event for the PNML handler
            // Since we've already validated the file, we just need to trigger import
            return callPnmlHandler(file, dropLocation);
        } catch (Exception ex) {
            LOGGER.log(Level.SEVERE, "Error importing PNML file: " + file.getName(), ex);
            MessageHelper.showException(parentFrame, "Error importing PNML file: " + file.getName(), ex);
            return false;
        }
    }

    /**
     * Import a PNS file using the specialized handler.
     *
     * @param file The PNS file to import
     * @param dropLocation The location where the file was dropped
     * @return true if import was successful, false otherwise
     */
    private boolean importPnsFile(File file, Point dropLocation) {
        try {
            // Create a synthetic drop event for the PNS handler
            // Since we've already validated the file, we just need to trigger import
            return callPnsHandler(file, dropLocation);
        } catch (Exception ex) {
            LOGGER.log(Level.SEVERE, "Error importing PNS file: " + file.getName(), ex);
            MessageHelper.showException(parentFrame, "Error importing PNS file: " + file.getName(), ex);
            return false;
        }
    }

    /**
     * Delegate to PNML handler by calling its import method directly.
     * This uses reflection to access the private importPnmlFile method.
     */
    private boolean callPnmlHandler(File file, Point dropLocation) {
        try {
            var method = PnmlDropHandler.class.getDeclaredMethod("importPnmlFile", File.class, Point.class);
            method.setAccessible(true);
            return (boolean) method.invoke(pnmlHandler, file, dropLocation);
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error calling PNML handler", e);
            return false;
        }
    }

    /**
     * Delegate to PNS handler by calling its import method directly.
     * This uses reflection to access the private importPnsFile method.
     */
    private boolean callPnsHandler(File file, Point dropLocation) {
        try {
            var method = PnsDropHandler.class.getDeclaredMethod("importPnsFile", File.class, Point.class);
            method.setAccessible(true);
            return (boolean) method.invoke(pnsHandler, file, dropLocation);
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error calling PNS handler", e);
            return false;
        }
    }
}
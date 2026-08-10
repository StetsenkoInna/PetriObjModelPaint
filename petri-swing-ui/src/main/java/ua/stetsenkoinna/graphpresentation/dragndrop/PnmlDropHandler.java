package ua.stetsenkoinna.graphpresentation.dragndrop;

import ua.stetsenkoinna.petriobj.PetriNet;
import ua.stetsenkoinna.graphnet.GraphNetBuilder;
import ua.stetsenkoinna.graphnet.GraphPetriNet;
import ua.stetsenkoinna.graphpresentation.PetriNetsPanel;
import ua.stetsenkoinna.pnml.PnmlParser;
import ua.stetsenkoinna.utils.MessageHelper;

import javax.swing.*;
import java.awt.Point;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.dnd.*;
import java.io.File;
import java.util.List;

/**
 * Handler for PNML file drag and drop operations
 *
 * @author Serhii Rybak
 */
public class PnmlDropHandler implements DropTargetListener {

    private final PetriNetsPanel panel;
    private final JFrame parentFrame;

    public PnmlDropHandler(PetriNetsPanel panel, JFrame parentFrame) {
        this.panel = panel;
        this.parentFrame = parentFrame;
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
        // Visual feedback can be added here
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
        // Clean up visual feedback
    }

    @Override
    public void drop(DropTargetDropEvent dtde) {
        try {
            Transferable transferable = dtde.getTransferable();

            if (transferable.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
                dtde.acceptDrop(DnDConstants.ACTION_COPY);

                @SuppressWarnings("unchecked")
                List<File> droppedFiles = (List<File>) transferable.getTransferData(DataFlavor.javaFileListFlavor);

                Point dropLocation = dtde.getLocation();

                for (File file : droppedFiles) {
                    if (isPnmlFile(file)) {
                        importPnmlFile(file, dropLocation);
                    }
                }

                dtde.dropComplete(true);
            } else {
                dtde.rejectDrop();
            }
        } catch (Exception ex) {
            MessageHelper.showException(parentFrame, "Error during file drop", ex);
            dtde.dropComplete(false);
        }
    }

    /**
     * Check if drag operation is acceptable
     */
    private boolean isDragAcceptable(DropTargetDragEvent dtde) {
        return dtde.isDataFlavorSupported(DataFlavor.javaFileListFlavor);
    }

    /**
     * Check if file is PNML format
     */
    private boolean isPnmlFile(File file) {
        if (file == null || !file.exists() || !file.isFile()) {
            return false;
        }
        String name = file.getName().toLowerCase();
        return name.endsWith(".pnml") || name.endsWith(".xml");
    }

    /**
     * Import PNML file preserving original coordinates from PNML.
     * The network shape remains unchanged, only the overall position is adjusted to drop location.
     */
    private boolean importPnmlFile(File file, Point dropLocation) {
        try {
            PnmlParser parser = new PnmlParser();
            PetriNet petriNet = parser.parse(file);

            // Coordinates come from the document, so the net keeps the shape it was drawn
            // with; only its overall position follows the drop.
            GraphPetriNet graphNet = GraphNetBuilder.build(petriNet,
                    parser.getAllPlaceCoordinates(),
                    parser.getAllTransitionCoordinates(),
                    dropLocation);

            // Add the imported net to panel
            panel.addGraphNet(graphNet);
            panel.repaint();

            MessageHelper.showInfo(parentFrame,
                "PNML file imported successfully!\n" +
                "Places: " + petriNet.getListP().length +
                ", Transitions: " + petriNet.getListT().length +
                "\nInput arcs: " + petriNet.getArcIn().length +
                ", Output arcs: " + petriNet.getArcOut().length);

            return true;

        } catch (Exception ex) {
            MessageHelper.showException(parentFrame, "Error importing PNML file: " + file.getName(), ex);
            return false;
        }
    }
}

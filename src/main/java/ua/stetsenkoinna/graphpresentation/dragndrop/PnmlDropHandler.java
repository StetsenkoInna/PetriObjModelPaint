package ua.stetsenkoinna.graphpresentation.dragndrop;

import ua.stetsenkoinna.PetriObj.PetriNet;
import ua.stetsenkoinna.graphnet.GraphPetriNet;
import ua.stetsenkoinna.graphpresentation.PetriNetsPanel;
import ua.stetsenkoinna.graphpresentation.FileUse;
import ua.stetsenkoinna.pnml.PnmlParser;
import ua.stetsenkoinna.pnml.ImportResult;
import ua.stetsenkoinna.utils.MessageHelper;
import ua.stetsenkoinna.utils.NetworkLocationUtil;

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
    private final FileUse fileUse;

    public PnmlDropHandler(PetriNetsPanel panel, JFrame parentFrame) {
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
     * Import PNML file and place on workspace at drop location
     */
    private void importPnmlFile(File file, Point dropLocation) {
        try {
            // Parse PNML using existing parser
            PnmlParser parser = new PnmlParser();
            PetriNet petriNet = parser.parse(file);
            ImportResult importResult = new ImportResult(petriNet, parser);

            // Calculate free space on workspace using utility
            Point targetLocation = NetworkLocationUtil.findFreeSpaceNear(
                panel.getGraphNet(),
                dropLocation
            );

            // Use existing FileUse logic to convert PetriNet to GraphPetriNet
            GraphPetriNet graphNet = fileUse.generateGraphNetBySimpleNet(
                panel,
                petriNet,
                targetLocation
            );

            // Add network to panel
            panel.addGraphNet(graphNet);
            panel.repaint();

            MessageHelper.showInfo(parentFrame,
                "PNML file imported successfully!\n" +
                "File: " + file.getName() + "\n" +
                "Places: " + petriNet.getListP().length +
                ", Transitions: " + petriNet.getListT().length);

        } catch (Exception ex) {
            MessageHelper.showException(parentFrame, "Error importing PNML file: " + file.getName(), ex);
        }
    }
}

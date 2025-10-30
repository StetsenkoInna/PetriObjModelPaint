package ua.stetsenkoinna.graphpresentation.dragndrop;

import ua.stetsenkoinna.PetriObj.ArcIn;
import ua.stetsenkoinna.PetriObj.ArcOut;
import ua.stetsenkoinna.PetriObj.PetriNet;
import ua.stetsenkoinna.PetriObj.PetriP;
import ua.stetsenkoinna.PetriObj.PetriT;
import ua.stetsenkoinna.graphnet.GraphArcIn;
import ua.stetsenkoinna.graphnet.GraphArcOut;
import ua.stetsenkoinna.graphnet.GraphPetriNet;
import ua.stetsenkoinna.graphnet.GraphPetriPlace;
import ua.stetsenkoinna.graphnet.GraphPetriTransition;
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
            // Parse PNML file
            PnmlParser parser = new PnmlParser();
            PetriNet petriNet = parser.parse(file);

            // Create empty GraphPetriNet and manually add elements with original PNML coordinates
            GraphPetriNet graphNet = new GraphPetriNet();

            // Create GraphPetriPlace objects from PetriP objects with original PNML coordinates
            for (PetriP place : petriNet.getListP()) {
                GraphPetriPlace graphPlace = new GraphPetriPlace(place, PetriNetsPanel.getIdElement());

                // Get coordinates from PNML parser (preserves original position)
                java.awt.geom.Point2D.Double coords = parser.getPlaceCoordinates(place.getNumber());
                if (coords != null) {
                    graphPlace.setNewCoordinates(new java.awt.geom.Point2D.Double(coords.x, coords.y));
                } else {
                    // Fallback to default coordinates if PNML doesn't have position info
                    graphPlace.setNewCoordinates(new java.awt.geom.Point2D.Double(100 + place.getNumber() * 100, 100));
                }

                graphNet.getGraphPetriPlaceList().add(graphPlace);
            }

            // Create GraphPetriTransition objects from PetriT objects with original PNML coordinates
            for (PetriT transition : petriNet.getListT()) {
                GraphPetriTransition graphTransition = new GraphPetriTransition(transition, PetriNetsPanel.getIdElement());

                // Get coordinates from PNML parser (preserves original position)
                java.awt.geom.Point2D.Double coords = parser.getTransitionCoordinates(transition.getNumber());
                if (coords != null) {
                    graphTransition.setNewCoordinates(new java.awt.geom.Point2D.Double(coords.x, coords.y));
                } else {
                    // Fallback to default coordinates if PNML doesn't have position info
                    graphTransition.setNewCoordinates(new java.awt.geom.Point2D.Double(100 + transition.getNumber() * 100, 200));
                }

                graphNet.getGraphPetriTransitionList().add(graphTransition);
            }

            // Create GraphArcIn objects from ArcIn objects
            for (ArcIn arcIn : petriNet.getArcIn()) {
                GraphPetriPlace beginPlace = null;
                GraphPetriTransition endTransition = null;

                // Find corresponding graph elements
                for (GraphPetriPlace gp : graphNet.getGraphPetriPlaceList()) {
                    if (gp.getPetriPlace().getNumber() == arcIn.getNumP()) {
                        beginPlace = gp;
                        break;
                    }
                }

                for (GraphPetriTransition gt : graphNet.getGraphPetriTransitionList()) {
                    if (gt.getPetriTransition().getNumber() == arcIn.getNumT()) {
                        endTransition = gt;
                        break;
                    }
                }

                if (beginPlace != null && endTransition != null) {
                    GraphArcIn graphArcIn = new GraphArcIn(arcIn);
                    graphArcIn.settingNewArc(beginPlace);
                    graphArcIn.setEndElement(endTransition);
                    graphArcIn.setPetriElements();
                    graphArcIn.updateCoordinates();
                    graphNet.getGraphArcInList().add(graphArcIn);
                }
            }

            // Create GraphArcOut objects from ArcOut objects
            for (ArcOut arcOut : petriNet.getArcOut()) {
                GraphPetriTransition beginTransition = null;
                GraphPetriPlace endPlace = null;

                // Find corresponding graph elements
                for (GraphPetriTransition gt : graphNet.getGraphPetriTransitionList()) {
                    if (gt.getPetriTransition().getNumber() == arcOut.getNumT()) {
                        beginTransition = gt;
                        break;
                    }
                }

                for (GraphPetriPlace gp : graphNet.getGraphPetriPlaceList()) {
                    if (gp.getPetriPlace().getNumber() == arcOut.getNumP()) {
                        endPlace = gp;
                        break;
                    }
                }

                if (beginTransition != null && endPlace != null) {
                    GraphArcOut graphArcOut = new GraphArcOut(arcOut);
                    graphArcOut.settingNewArc(beginTransition);
                    graphArcOut.setEndElement(endPlace);
                    graphArcOut.setPetriElements();
                    graphArcOut.updateCoordinates();
                    graphNet.getGraphArcOutList().add(graphArcOut);
                }
            }

            // Adjust the entire network position to drop location if specified
            // This moves all elements together, preserving their relative positions
            if (dropLocation != null) {
                graphNet.changeLocation(dropLocation);
            }

            // Fix overlapping arcs (important for nets with bidirectional connections)
            graphNet.fixOverlappingArcs();

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

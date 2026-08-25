package ua.stetsenkoinna.graphpresentation.dragndrop;

import ua.stetsenkoinna.petriobj.PetriNet;
import ua.stetsenkoinna.graphnet.GraphCanvasModel;
import ua.stetsenkoinna.graphnet.GraphNetBuilder;
import ua.stetsenkoinna.graphnet.GraphPetriNet;
import ua.stetsenkoinna.graphnet.GraphPetriObjModel;
import ua.stetsenkoinna.graphpresentation.PetriNetsPanel;
import ua.stetsenkoinna.pnml.PnmlConstants;
import ua.stetsenkoinna.pnml.PnmlModelParser;
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
     *
     * <p>{@link PnmlParser} only reads a single-page document; a document that holds a whole
     * Petri-object model (a page per object) makes it fail with {@link
     * PnmlConstants#ERROR_OBJECT_MODEL_NOT_SUPPORTED}. Rather than reporting that failure to
     * the user, it is recognised here and the drop falls back to {@link #importComposedPnmlFile},
     * the composed reader File &gt; Open uses for the same documents.
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
            MessageHelper.showImportWarnings(parentFrame, parser.getWarnings());

            return true;

        } catch (Exception ex) {
            if (isComposedDocumentFailure(ex)) {
                return importComposedPnmlFile(file);
            }
            MessageHelper.showException(parentFrame, "Error importing PNML file: " + file.getName(), ex);
            return false;
        }
    }

    /**
     * Reads {@code file} as a whole Petri-object model and merges it into the panel: a page
     * per object, each becoming a frame, plus the links between them, and reports the outcome
     * the same way {@link #importPnmlFile} reports a single net.
     *
     * <p>The drop location is not used here: a composed document already carries a position for
     * every one of its objects, and {@link PetriNetsPanel#addCanvasModel} is the same merge a
     * loaded canvas document goes through elsewhere, which does not shift it either.
     */
    private boolean importComposedPnmlFile(File file) {
        try {
            ComposedImportResult result = mergeComposedDocument(file);
            MessageHelper.showInfo(parentFrame,
                    "PNML file imported successfully!\n" +
                    "Objects: " + result.model().getObjects().size());
            MessageHelper.showImportWarnings(parentFrame, result.warnings());
            return true;
        } catch (Exception ex) {
            MessageHelper.showException(parentFrame, "Error importing PNML file: " + file.getName(), ex);
            return false;
        }
    }

    /**
     * Reads {@code file} as a composed {@link GraphPetriObjModel} and merges it into the panel -
     * the dialog-free core of {@link #importComposedPnmlFile}, package-visible so a test can
     * drive it directly without any of the notification dialogs blocking the test JVM.
     */
    ComposedImportResult mergeComposedDocument(File file) throws Exception {
        PnmlModelParser parser = new PnmlModelParser();
        GraphPetriObjModel objModel = parser.parse(file);
        GraphCanvasModel canvas = GraphCanvasModel.fromObjModel(objModel);
        panel.addCanvasModel(canvas);
        panel.repaint();
        List<String> warnings = new java.util.ArrayList<>(parser.getWarnings());
        warnings.addAll(canvas.getLoadWarnings());
        return new ComposedImportResult(objModel, warnings);
    }

    /** What {@link #mergeComposedDocument} produced, for a caller to report or inspect. */
    record ComposedImportResult(GraphPetriObjModel model, List<String> warnings) {
    }

    /**
     * @return true when {@code ex} is the failure {@link PnmlParser} raises for a document that
     *         holds more than one page - the signal that this drop should be retried through
     *         the composed reader instead of being reported as an error.
     */
    static boolean isComposedDocumentFailure(Exception ex) {
        String message = ex.getMessage();
        if (message == null) {
            return false;
        }
        String template = PnmlConstants.ERROR_OBJECT_MODEL_NOT_SUPPORTED;
        String prefix = template.substring(0, template.indexOf("%d"));
        return message.startsWith(prefix);
    }
}

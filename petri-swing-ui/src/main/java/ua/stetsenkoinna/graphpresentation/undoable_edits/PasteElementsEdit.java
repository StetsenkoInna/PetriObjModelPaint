package ua.stetsenkoinna.graphpresentation.undoable_edits;

import ua.stetsenkoinna.petriobj.ExceptionInvalidNetStructure;
import ua.stetsenkoinna.graphnet.GraphElement;
import ua.stetsenkoinna.graphnet.GraphPetriNet;
import ua.stetsenkoinna.graphpresentation.PetriNetsPanel;
import javax.swing.undo.AbstractUndoableEdit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Leonid
 */
public class PasteElementsEdit extends AbstractUndoableEdit {

    private static final Logger log = LoggerFactory.getLogger(PasteElementsEdit.class);

    private final PetriNetsPanel panel;
    
    /**
     * Cloned elements and arcs that were pasted
     */
    private final GraphPetriNet.GraphNetFragment fragment;
    
    public PasteElementsEdit(PetriNetsPanel panel, GraphPetriNet.GraphNetFragment fragment) {
        this.panel = panel;
        this.fragment = fragment;
    }
    
    @Override
    public void undo() {
        super.undo(); // checking whether it can be undone and setting hasBeenDone = false

        for (GraphElement element : fragment.elements) {
            if (element == panel.getCurrent()) {
                panel.setCurrent(null);
            }
            panel.getChoosenElements().remove(element);
            // Whatever frame claims this clone must let go of it: deleting without
            // releasing left ghost membership behind, so a frame kept counting and
            // fitting itself around elements that no longer existed in the net.
            panel.getCanvasModel().release(element);
            try {
               panel.getGraphNet().delGraphElement(element);
            } catch (ExceptionInvalidNetStructure e) {
                log.error("Unexpected error while undoing paste", e);
                // theoretically this exception should never happen here
            }
        }
        panel.repaint();
    }
    
    @Override
    public void redo() {
        super.redo();
        panel.addNetFragment(fragment);
    }
    
}

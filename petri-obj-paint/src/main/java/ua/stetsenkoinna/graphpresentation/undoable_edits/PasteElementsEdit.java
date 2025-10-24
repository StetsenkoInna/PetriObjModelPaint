package ua.stetsenkoinna.graphpresentation.undoable_edits;

import ua.stetsenkoinna.PetriObj.ExceptionInvalidNetStructure;
import ua.stetsenkoinna.graphnet.GraphPetriNet;
import ua.stetsenkoinna.graphpresentation.GraphElement;
import ua.stetsenkoinna.graphpresentation.PetriNetsPanel;
import javax.swing.undo.AbstractUndoableEdit;

/**
 *
 * @author Leonid
 */
public class PasteElementsEdit extends AbstractUndoableEdit {
    
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
            try {
               panel.getGraphNet().delGraphElement(element);
            } catch (ExceptionInvalidNetStructure e) {
                e.printStackTrace();
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

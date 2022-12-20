package graphpresentation.undoable_edits;

import PetriObj.ExceptionInvalidNetStructure;
import graphnet.GraphArcIn;
import graphnet.GraphArcOut;
import graphnet.GraphPetriNet;
import graphnet.GraphPetriPlace;
import graphnet.GraphPetriTransition;
import graphpresentation.GraphElement;
import graphpresentation.PetriNetsPanel;
import java.awt.Color;
import java.util.List;
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
            if (element == panel.getChoosen()) {
               // panel.setChoosen(null);
            }
            if (panel.getChoosenElements().contains(element)) {
                panel.getChoosenElements().remove(element);
            }
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

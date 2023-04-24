package graphpresentation.undoable_edits;

import graphpresentation.PetriNetsPanel;
import graphnet.GraphPetriPlace;
import graphnet.GraphPetriTransition;
import graphpresentation.GraphElement;
import javax.swing.undo.AbstractUndoableEdit;

/**
 * Represents an action of adding elements to the net. Contains methods
 * to undo and redo adding element and is supposed to be used with an UndoManager.
 * @author Leonid
 */
public class AddGraphElementEdit extends AbstractUndoableEdit  {
    
    private final PetriNetsPanel panel;
    private final GraphElement element;

    public AddGraphElementEdit(PetriNetsPanel panel, GraphElement element) {
        this.panel = panel;
        this.element = element;
    }
    
    @Override
    public void redo() {
        super.redo(); // checking whether it can be redone and setting hasBeenDone = true
        doFirstTime();
        panel.setCurrent(null);
        panel.repaint();
    }
    
    /**
     * Adds the place to the net for the first time.
     * Called when this action is first done.
     */
    public void doFirstTime() {
        if (element instanceof GraphPetriPlace) {
            panel.getGraphNet().getGraphPetriPlaceList().add((GraphPetriPlace)element);
        } else if (element instanceof GraphPetriTransition) {
            panel.getGraphNet().getGraphPetriTransitionList().add((GraphPetriTransition)element);
        } else {
            throw new RuntimeException("AddPlaceEdit.doFirstTime(): unsupported element");
        }
        
        panel.setCurrent(element);
    }
    
    @Override
    public void undo() {
        super.undo(); // checking whether it can be undone and setting hasBeenDone = false
        if (element == panel.getCurrent()) {
                panel.setCurrent(null);
            }
            if (element == panel.getChoosen()) {
               panel.setChoosen(null);
            }
            if (panel.getChoosenElements().contains(element)) {
                panel.getChoosenElements().remove(element);
            }
            
            if (element instanceof GraphPetriPlace) {
                panel.getGraphNet().getGraphPetriPlaceList().remove((GraphPetriPlace)element);
            } else if (element instanceof GraphPetriTransition) {
                panel.getGraphNet().getGraphPetriTransitionList().remove((GraphPetriTransition)element);
            } else {
                throw new RuntimeException("AddGraphElementEdit.undo(): unsupported element");
            }
           
            panel.revalidate();
            panel.repaint();
    }
    
}

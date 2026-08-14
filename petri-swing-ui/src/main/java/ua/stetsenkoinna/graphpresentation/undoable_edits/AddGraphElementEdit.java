package ua.stetsenkoinna.graphpresentation.undoable_edits;

import ua.stetsenkoinna.graphpresentation.PetriNetsPanel;
import ua.stetsenkoinna.graphnet.GraphObjectFrame;
import ua.stetsenkoinna.graphnet.GraphPetriPlace;
import ua.stetsenkoinna.graphnet.GraphPetriTransition;
import ua.stetsenkoinna.graphnet.GraphElement;
import javax.swing.undo.AbstractUndoableEdit;

/**
 * Represents an action of adding elements to the net. Contains methods
 * to undo and redo adding element and is supposed to be used with an UndoManager.
 * @author Leonid
 */
public class AddGraphElementEdit extends AbstractUndoableEdit  {

    private final PetriNetsPanel panel;
    private final GraphElement element;

    /**
     * The Petri-object the element was drawn into, or {@code null} for the net's own canvas. An
     * element added on an object's canvas belongs to that object from the moment it appears, so
     * redoing the addition has to claim it again - otherwise the redone element sits inside the
     * object's frame while belonging to nothing.
     */
    private final GraphObjectFrame owner;

    public AddGraphElementEdit(PetriNetsPanel panel, GraphElement element) {
        this(panel, element, null);
    }

    /**
     * @param owner the Petri-object whose canvas the element was drawn on, or {@code null} for
     *        the net's own canvas
     */
    public AddGraphElementEdit(PetriNetsPanel panel, GraphElement element, GraphObjectFrame owner) {
        this.panel = panel;
        this.element = element;
        this.owner = owner;
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

        if (owner != null && panel.getCanvasModel().getFrames().contains(owner)) {
            panel.getCanvasModel().claim(owner, element);
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
            panel.getChoosenElements().remove(element);
            panel.getCanvasModel().release(element);

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

package ua.stetsenkoinna.graphpresentation.undoable_edits;

import ua.stetsenkoinna.graphpresentation.PetriNetsPanel;
import ua.stetsenkoinna.graphnet.GraphObjectFrame;
import ua.stetsenkoinna.graphnet.GraphPetriPlace;
import ua.stetsenkoinna.graphnet.GraphPetriTransition;
import ua.stetsenkoinna.graphnet.GraphElement;
import javax.swing.undo.AbstractUndoableEdit;

/**
 * Undoable/redoable addition of a single place or transition to the net, to be used together
 * with an {@link javax.swing.undo.UndoManager}.
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

    /**
     * @param owner the Petri-object whose canvas the element was drawn on, or {@code null} for
     *        the net's own canvas
     */
    public AddGraphElementEdit(PetriNetsPanel panel, GraphElement element, GraphObjectFrame owner) {
        this.panel = panel;
        this.element = element;
        this.owner = owner;
    }

    public AddGraphElementEdit(PetriNetsPanel panel, GraphElement element) {
        this(panel, element, null);
    }

    @Override
    public void redo() {
        super.redo(); // checking whether it can be redone and setting hasBeenDone = true
        doFirstTime();
        panel.setCurrent(null);
        panel.repaint();
    }

    /**
     * Puts the element into the net for the first time; also invoked directly the very first
     * time this action is performed (before it is ever undone/redone).
     */
    public void doFirstTime() {
        if (element instanceof GraphPetriPlace place) {
            panel.getGraphNet().getGraphPetriPlaceList().add(place);
        } else if (element instanceof GraphPetriTransition transition) {
            panel.getGraphNet().getGraphPetriTransitionList().add(transition);
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

        if (element instanceof GraphPetriPlace place) {
            panel.getGraphNet().getGraphPetriPlaceList().remove(place);
        } else if (element instanceof GraphPetriTransition transition) {
            panel.getGraphNet().getGraphPetriTransitionList().remove(transition);
        } else {
            throw new RuntimeException("AddGraphElementEdit.undo(): unsupported element");
        }

        panel.revalidate();
        panel.repaint();
    }

}

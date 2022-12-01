package graphpresentation.undoable_edits;

import graphpresentation.PetriNetsPanel;
import graphnet.GraphPetriPlace;
import javax.swing.undo.AbstractUndoableEdit;

/**
 * Represents an action of adding a new place to the net. Contains methods
 * to undo and redo adding the place and is supposed to be used with an UndoManager.
 * @author Leonid
 */
public class AddPlaceEdit extends AbstractUndoableEdit  {
    
    private final PetriNetsPanel panel;
    private final GraphPetriPlace place;

    public AddPlaceEdit(PetriNetsPanel panel, GraphPetriPlace place) {
        this.panel = panel;
        this.place = place;
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
        panel.getGraphNet().getGraphPetriPlaceList().add(place);
        panel.setCurrent(place);
    }
    
    @Override
    public void undo() {
        super.undo(); // checking whether it can be undone and setting hasBeenDone = false
       // panel.getGraphNet().getGraphPetriPlaceList().remove(place);
        //panel.setCurrent(null);
        //panel.redraw();
       // try {
        if (place == panel.getCurrent()) {
                panel.setCurrent(null);
            }
            if (place == panel.getChoosen()) {
               panel.setChoosen(null);
            }
            if (panel.getChoosenElements().contains(place)) {
                panel.getChoosenElements().remove(place);
            }
            panel.getGraphNet().getGraphPetriPlaceList().remove(place);
            panel.revalidate();
            panel.repaint();
          
//            panel.remove(place); // maybe replace with simple removal from list?
            //panel.redraw(); // this is second time it's called so it's bad
       // } catch (ExceptionInvalidNetStructure e) {
        //    e.printStackTrace();
            
        //}
    }
    
}

package ua.stetsenkoinna.graphpresentation.undoable_edits;

import java.util.Map;
import javax.swing.undo.AbstractUndoableEdit;

import ua.stetsenkoinna.graphnet.CanvasNames;
import ua.stetsenkoinna.graphnet.GraphPetriPlace;
import ua.stetsenkoinna.graphnet.GraphPlaceFusion;
import ua.stetsenkoinna.graphpresentation.PetriNetsPanel;

/**
 * Joining two places into a shared place, undone and redone.
 *
 * <p>Making a fusion used to post no edit at all, so the Ctrl+Z pressed right after it left
 * the fusion in place and instead undid whatever older action was next on the stack, up to
 * and including deleting a whole Petri-object the user had no intention of touching.
 *
 * <p>A fusion made by hand also gives the joined place the master's name, since a shared place
 * is one place with one name; the names it replaced come back with the undo.
 */
public class JoinPlacesEdit extends AbstractUndoableEdit {

    private final PetriNetsPanel panel;
    private final GraphPlaceFusion fusion;
    private final Map<GraphPetriPlace, String> namesBefore;

    public JoinPlacesEdit(PetriNetsPanel panel, GraphPlaceFusion fusion) {
        this(panel, fusion, Map.of());
    }

    /**
     * @param namesBefore the places the join renamed, each with the name it had before; empty
     *        when the join renamed nothing
     */
    public JoinPlacesEdit(PetriNetsPanel panel, GraphPlaceFusion fusion, Map<GraphPetriPlace, String> namesBefore) {
        this.panel = panel;
        this.fusion = fusion;
        this.namesBefore = new java.util.IdentityHashMap<>(namesBefore);
    }

    @Override
    public void undo() {
        super.undo();
        panel.getCanvasModel().removeFusion(fusion);
        for (Map.Entry<GraphPetriPlace, String> entry : namesBefore.entrySet()) {
            CanvasNames.rename(panel.getCanvasModel(), entry.getKey(), entry.getValue());
        }
        panel.repaint();
    }

    @Override
    public void redo() {
        super.redo();
        panel.getCanvasModel().restoreFusion(fusion);
        if (!namesBefore.isEmpty()) {
            CanvasNames.shareName(panel.getCanvasModel(), fusion.getMaster());
        }
        panel.repaint();
    }
}

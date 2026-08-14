package ua.stetsenkoinna.graphpresentation.undoable_edits;

import javax.swing.undo.AbstractUndoableEdit;

import ua.stetsenkoinna.graphnet.GraphPlaceFusion;
import ua.stetsenkoinna.graphpresentation.PetriNetsPanel;

/**
 * Splitting a shared place back into two ordinary places, undone and redone. The split moves
 * the joined half aside so the two circles stop coinciding; the undo moves it back by the
 * same offset before restoring the fusion, so the shared place reappears exactly as it was.
 */
public class SplitSharedPlaceEdit extends AbstractUndoableEdit {

    private final PetriNetsPanel panel;
    private final GraphPlaceFusion fusion;
    private final int movedByX;
    private final int movedByY;

    public SplitSharedPlaceEdit(PetriNetsPanel panel, GraphPlaceFusion fusion,
            int movedByX, int movedByY) {
        this.panel = panel;
        this.fusion = fusion;
        this.movedByX = movedByX;
        this.movedByY = movedByY;
    }

    @Override
    public void undo() {
        super.undo();
        fusion.getJoined().moveBy(-movedByX, -movedByY);
        panel.getCanvasModel().restoreFusion(fusion);
        panel.updateArcCoordinates();
        panel.repaint();
    }

    @Override
    public void redo() {
        super.redo();
        panel.getCanvasModel().removeFusion(fusion);
        fusion.getJoined().moveBy(movedByX, movedByY);
        panel.updateArcCoordinates();
        panel.repaint();
    }
}

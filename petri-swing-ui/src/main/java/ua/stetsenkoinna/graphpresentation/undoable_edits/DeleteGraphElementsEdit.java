package ua.stetsenkoinna.graphpresentation.undoable_edits;

import ua.stetsenkoinna.petriobj.ExceptionInvalidNetStructure;
import ua.stetsenkoinna.graphnet.GraphArcIn;
import ua.stetsenkoinna.graphnet.GraphArcOut;
import ua.stetsenkoinna.graphnet.GraphPetriPlace;
import ua.stetsenkoinna.graphnet.GraphPetriTransition;
import ua.stetsenkoinna.graphnet.GraphElement;
import ua.stetsenkoinna.graphpresentation.PetriNetsPanel;
import ua.stetsenkoinna.graphnet.GraphObjectFrame;
import java.awt.Color;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import javax.swing.undo.AbstractUndoableEdit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Undoable removal of a batch of graph elements (places and/or transitions), together with
 * whatever arcs were incident to them.
 */
public class DeleteGraphElementsEdit extends AbstractUndoableEdit {

    private static final Logger log = LoggerFactory.getLogger(DeleteGraphElementsEdit.class);

    private final PetriNetsPanel panel;

    /**
     * The places and transitions this edit removed.
     */
    private final List<GraphElement> elements;

    /**
     * Incoming arcs removed together with {@link #elements}.
     */
    private final List<GraphArcIn> inArcs;

    /**
     * Outgoing arcs removed together with {@link #elements}.
     */
    private final List<GraphArcOut> outArcs;

    /**
     * Which Petri-object claimed each element when it was deleted, so undo puts it back into the
     * same object rather than as a loose element inside that object's frame.
     *
     * <p>Deleting an element releases it from whatever claimed it - before, the frame went on
     * claiming an element the canvas no longer drew, so the object's own member set and the
     * canvas disagreed forever. Undo has to be symmetric with that release, which means the
     * owner is recorded here at the moment of deletion and nowhere else: by the time undo runs
     * there is nothing left on the canvas that remembers it.
     */
    private final Map<GraphElement, GraphObjectFrame> ownerAtDeletion = new IdentityHashMap<>();

    public DeleteGraphElementsEdit(PetriNetsPanel panel, List<GraphElement> elements,
            List<GraphArcIn> inArcs, List<GraphArcOut> outArcs) {
        this.panel = panel;
        this.elements = elements;
        this.inArcs = inArcs;
        this.outArcs = outArcs;
    }

    public DeleteGraphElementsEdit(PetriNetsPanel panel, GraphElement element,
            List<GraphArcIn> inArcs, List<GraphArcOut> outArcs) {
        this.panel = panel;
        this.elements = new ArrayList<>();
        this.elements.add(element);
        this.inArcs = inArcs;
        this.outArcs = outArcs;
    }

    /**
     * Records which object claimed one of the deleted elements. Called by the canvas as it
     * releases each element, which is the only moment the answer still exists.
     *
     * @param element one of the elements this edit will restore
     * @param owner the frame that claimed it, or {@code null} if it was free
     */
    public void rememberOwner(GraphElement element, GraphObjectFrame owner) {
        if (owner != null) {
            ownerAtDeletion.put(element, owner);
        }
    }

    /**
     * Shared places the deletion is about to drop, recorded for the same reason as the
     * owners: deleting a fused place removes its fusion as a side effect, and undo used to
     * restore the place but silently forget it had been shared.
     */
    private final List<ua.stetsenkoinna.graphnet.GraphPlaceFusion> fusionsAtDeletion =
            new ArrayList<>();

    /**
     * Records a fusion that will disappear with one of the deleted places, so undo can put
     * it back.
     *
     * @param fusion the shared place one of the deleted elements is half of
     */
    public void rememberFusion(ua.stetsenkoinna.graphnet.GraphPlaceFusion fusion) {
        if (fusion != null && !fusionsAtDeletion.contains(fusion)) {
            fusionsAtDeletion.add(fusion);
        }
    }

    @Override
    public void undo() {
        super.undo();
        if (elements == null || elements.isEmpty()) {
            return;
        }

        clearCurrentSelection();

        for (GraphElement element: elements) {
            panel.getChoosenElements().add(element);
            element.setColor(Color.GREEN);

            if (element instanceof GraphPetriPlace place) {
                panel.getGraphNet().getGraphPetriPlaceList().add(place);
            } else if (element instanceof GraphPetriTransition transition) {
                panel.getGraphNet().getGraphPetriTransitionList().add(transition);
            } else {
                log.warn("Unknown element while redoing delete");
            }

            // Back into the same Petri-object it was deleted out of. Without this the element
            // reappears exactly where it was drawn - inside that object's frame - while belonging
            // to no object at all, so the object it visibly sits in would neither simulate it nor
            // carry it when moved.
            GraphObjectFrame owner = ownerAtDeletion.get(element);
            if (owner != null && panel.getCanvasModel().getFrames().contains(owner)) {
                panel.getCanvasModel().claim(owner, element);
            }
        }

        for (GraphArcIn arcIn : inArcs) {
            panel.getGraphNet().getGraphArcInList().add(arcIn);
        }

        for (GraphArcOut arcOut : outArcs) {
            panel.getGraphNet().getGraphArcOutList().add(arcOut);
        }

        // The shared places the deletion dropped come back with the places they joined.
        for (ua.stetsenkoinna.graphnet.GraphPlaceFusion fusion : fusionsAtDeletion) {
            panel.getCanvasModel().restoreFusion(fusion);
        }

        relinkRestoredArcs();

        panel.repaint();
    }

    /**
     * Un-highlights whatever the panel currently has selected, making room for this edit's own
     * elements to become the new selection below.
     */
    private void clearCurrentSelection() {
        for (GraphElement selected : panel.getChoosenElements()) {
            selected.setColor(Color.BLACK);
        }
        panel.getChoosenElements().clear();
    }

    /**
     * Reconnects each restored in-arc with the out-arc that runs the opposite way between the
     * same place and transition, and refreshes every arc's drawn geometry now that its
     * endpoints are back on the canvas.
     *
     * <p>The net answers this itself, and answers it better: this used to pair arcs up without
     * ever un-pairing one, so an arc restored beside a partner that is no longer there kept an
     * offset it had no business keeping. It also cast both ends to a place and a transition,
     * which is an assumption the shared method does not need to make.
     */
    private void relinkRestoredArcs() {
        panel.getGraphNet().fixOverlappingArcs();
    }

    @Override
    public void redo() {
        super.redo();
        for (GraphElement element : elements) {
            if (element == panel.getCurrent()) {
                panel.setCurrent(null);
            }

            panel.getChoosenElements().remove(element);
            panel.getCanvasModel().release(element);
            try {
               panel.getGraphNet().delGraphElement(element);
            } catch (ExceptionInvalidNetStructure e) {
                log.error("Unexpected error while redoing delete", e);
                // element is guaranteed to still belong to the net at this point, so this
                // branch should be unreachable in practice
            }
        }
        // The same cleanup the original deletion did: a fused place going away takes its
        // fusion with it, else the restored-then-redone delete leaves the fusion dangling.
        for (ua.stetsenkoinna.graphnet.GraphPlaceFusion fusion : fusionsAtDeletion) {
            panel.getCanvasModel().removeFusion(fusion);
        }
        panel.repaint();
    }

}

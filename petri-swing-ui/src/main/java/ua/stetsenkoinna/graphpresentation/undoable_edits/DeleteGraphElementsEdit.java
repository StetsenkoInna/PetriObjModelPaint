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
 * Represents an undoable action of removing a number of graph elements
 * (places and/or transitions)
 * @author Leonid
 */
public class DeleteGraphElementsEdit extends AbstractUndoableEdit {

    private static final Logger log = LoggerFactory.getLogger(DeleteGraphElementsEdit.class);

    private final PetriNetsPanel panel;
    
    /**
     * Petri net elements that were removed during delete operation
     */
    private final List<GraphElement> elements;
    
    /**
     * In arcs that were removed along with GraphElements
     */
    private final List<GraphArcIn> inArcs;
    
    /**
     * Out arcs that were removed along with GraphElements
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

    @Override
    public void undo() {
        super.undo();
        /* the following code is based on ctrl+V implementation in PetriNetsPanel */
        if (elements == null || elements.isEmpty()) {
            return;
        }
        
        //List<GraphElement> elementsToSpawn =
        //        panel.getGraphNet().bulkCopyElements(elements);

        /* de-highlighting currently selected elements */
        for (GraphElement prevElement: panel.getChoosenElements()) {
            prevElement.setColor(Color.BLACK);
        }
        panel.getChoosenElements().clear();

        for (GraphElement element: elements) {
            //Point2D spawnPoint = element.getGraphElementCenter();
            //spawnPoint.setLocation(spawnPoint.getX() + 15, spawnPoint.getY() + 15);

            // element.setNewCoordinates(spawnPoint);
            panel.getChoosenElements().add(element);
            element.setColor(Color.GREEN);
            
            if (element instanceof GraphPetriPlace) {
                panel.getGraphNet().getGraphPetriPlaceList().add((GraphPetriPlace)element);
            } else if (element instanceof GraphPetriTransition) {
                panel.getGraphNet().getGraphPetriTransitionList().add(
                        (GraphPetriTransition)element);
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

        // elements = new ArrayList<>(elementsToSpawn);

        // some kind of update for arcs? idk what this code does and whether it's really
        // needed here
        for (GraphArcOut arcOut : panel.getGraphNet().getGraphArcOutList()) {
            for (GraphArcIn arcIn : panel.getGraphNet().getGraphArcInList()) {
                int inBeginId = ((GraphPetriPlace) arcIn.getBeginElement()).getId();
                int inEndId = ((GraphPetriTransition) arcIn.getEndElement()).getId();
                int outBeginId = ((GraphPetriTransition) arcOut.getBeginElement()).getId();
                int outEndId = ((GraphPetriPlace) arcOut.getEndElement()).getId();
                if (inBeginId == outEndId && inEndId == outBeginId) {
                    arcIn.twoArcs(arcOut);
                }
                arcIn.updateCoordinates();
                arcOut.updateCoordinates();
            }
        }
        
        panel.repaint();
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
                // theoretically this exception should never happen here
            }
        }
        panel.repaint();
    }
    
}

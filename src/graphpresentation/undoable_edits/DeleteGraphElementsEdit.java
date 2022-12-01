package graphpresentation.undoable_edits;

import PetriObj.ExceptionInvalidNetStructure;
import graphnet.GraphArcIn;
import graphnet.GraphArcOut;
import graphnet.GraphPetriPlace;
import graphnet.GraphPetriTransition;
import graphpresentation.GraphElement;
import graphpresentation.PetriNetsPanel;
import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import javax.swing.undo.AbstractUndoableEdit;

/**
 * Represents an undoable action of removing a number of graph elements
 * (places and/or transitions)
 * @author Leonid
 */
public class DeleteGraphElementsEdit extends AbstractUndoableEdit {
    
    private final PetriNetsPanel panel;
    
    /**
     * Petri net elements that were removed during delete operation
     */
    private List<GraphElement> elements;
    
    /**
     * In arcs that were removed along with GraphElements
     */
    private List<GraphArcIn> inArcs;
    
    /**
     * Out arcs that were removed along with GraphElements
     */
    private List<GraphArcOut> outArcs; 
    
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
        this.elements = new ArrayList();
        this.elements.add(element);
        this.inArcs = inArcs;
        this.outArcs = outArcs;
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
                System.out.println("Unknown element while redoing delete"); // todo remove
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
            //if (element == panel.getChoosen()) {
               // panel.setChoosen(null);
            //}
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
    
}

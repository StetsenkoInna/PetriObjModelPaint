package graphpresentation;

import PetriObj.PetriMainElement;
import java.util.ArrayList;

/**
 *
 * @author Katya (added 20.11.2016)
 */
public class VerticalSet {
    private final Boolean forPlaces;              // or for transitions
    private Boolean ready;                        // whether the set is ready (full with potential net elements)
    private ArrayList<PetriMainElement> elements; // places or transitions
    
    public VerticalSet(Boolean forPlacesParam) {
        forPlaces = forPlacesParam;
        elements = new ArrayList<>();
        ready = false;
    }
    
    public Boolean GetReadyStatus() {
        return ready;
    }
    
    public void SetAsReady() {
        ready = true;
    }
    
    public void SetAsNotReady() {
        ready = false;
    }
    
    public void AddElement(PetriMainElement elem) {
        elements.add(elem);
    }
    
    public ArrayList<PetriMainElement> GetElements() {
        return elements;
    }
    
    public Boolean IsForPlaces() {
        return forPlaces;
    }
}

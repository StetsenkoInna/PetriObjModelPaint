package ua.stetsenkoinna.pnml;

import ua.stetsenkoinna.petriobj.PetriNet;
import java.util.List;
import java.util.Map;

/**
 * Result container for PNML import operations containing PetriNet and coordinate data
 *
 * @author Serhii Rybak
 */
public class ImportResult {
    private final PetriNet petriNet;
    private final Map<Integer, java.awt.geom.Point2D.Double> placeCoordinates;
    private final Map<Integer, java.awt.geom.Point2D.Double> transitionCoordinates;
    private final List<String> warnings;

    /**
     * Create ImportResult from parser
     *
     * @param petriNet the parsed PetriNet
     * @param parser   the parser containing coordinate data
     */
    public ImportResult(PetriNet petriNet, PnmlParser parser) {
        this.petriNet = petriNet;
        this.placeCoordinates = parser.getAllPlaceCoordinates();
        this.transitionCoordinates = parser.getAllTransitionCoordinates();
        // A snapshot, not the live list: PnmlParser#getWarnings() is a view over the parser's
        // own field, and a parser instance reused for a later document clears and refills that
        // field, which would silently change what this already-built result reports.
        this.warnings = List.copyOf(parser.getWarnings());
    }

    /**
     * Soft-validation warnings raised while reading the document: an id that was not valid XML
     * and was imported under a different one, a value that did not parse as the number it
     * named, an arc dropped because its endpoints were not both on this page. Import proceeds
     * regardless; this is what lets a caller show them to the user afterwards.
     *
     * @return the warnings, in document order, empty when the document raised none
     */
    public List<String> getWarnings() {
        return warnings;
    }

    /**
     * Get the parsed PetriNet
     *
     * @return PetriNet object
     */
    public PetriNet getPetriNet() {
        return petriNet;
    }

    /**
     * Get coordinates for a place by its number
     *
     * @param placeNumber the place number
     * @return coordinates or null if not found
     */
    public java.awt.geom.Point2D.Double getPlaceCoordinates(int placeNumber) {
        return placeCoordinates.get(placeNumber);
    }

    /**
     * Get coordinates for a transition by its number
     *
     * @param transitionNumber the transition number
     * @return coordinates or null if not found
     */
    public java.awt.geom.Point2D.Double getTransitionCoordinates(int transitionNumber) {
        return transitionCoordinates.get(transitionNumber);
    }

    /**
     * Get all place coordinates
     *
     * @return map of place numbers to coordinates
     */
    public Map<Integer, java.awt.geom.Point2D.Double> getAllPlaceCoordinates() {
        return placeCoordinates;
    }

    /**
     * Get all transition coordinates
     *
     * @return map of transition numbers to coordinates
     */
    public Map<Integer, java.awt.geom.Point2D.Double> getAllTransitionCoordinates() {
        return transitionCoordinates;
    }
}
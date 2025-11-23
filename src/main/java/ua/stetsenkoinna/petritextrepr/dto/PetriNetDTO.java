package ua.stetsenkoinna.petritextrepr.dto;

import ua.stetsenkoinna.petritextrepr.dto.ArcImpl.*;

import java.util.ArrayList;
import java.util.List;

public class PetriNetDTO {
    private String id;
    private String name;

    private final List<Place> places = new ArrayList<>();
    private final List<Transition> transitions = new ArrayList<>();

    private final List<ArcIn> inputArcs = new ArrayList<>();
    private final List<ArcOut> outputArcs = new ArrayList<>();

    public void addPlace(Place place){
        places.add(place);
    }

    public void addTransition(Transition transition){
        transitions.add(transition);
    }

    public void addInputArc(ArcIn arc){
        inputArcs.add(arc);
    }

    public void addOutputArc(ArcOut arc){
        outputArcs.add(arc);
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public List<Place> getPlaces() {
        return places;
    }

    public List<Transition> getTransitions() {
        return transitions;
    }

    public List<ArcIn> getInputArcs() {
        return inputArcs;
    }

    public List<ArcOut> getOutputArcs() {
        return outputArcs;
    }

    public String dtoToLoggableString() {
        StringBuilder sb = new StringBuilder();
        sb.append("PetriNetDTO Content:\n");

        sb.append("  Places: [\n");
        for (Place p : this.getPlaces()) {
            sb.append(String.format("    { name: \"%s\", tokens: %d },\n", p.getName(), p.getTokens()));
        }
        sb.append("  ],\n");

        sb.append("  Transitions: [\n");
        for (Transition t : this.getTransitions()) {
            sb.append(String.format("    { id: %d, name: \"%s\" },\n", t.getId(), t.getName()));
        }
        sb.append("  ],\n");

        sb.append("  Input Arcs (Place -> Transition): [\n");
        for (ArcIn arc : this.getInputArcs()) {
            String fromName = (arc.getFrom() != null) ? arc.getFrom().getName() : "null";
            String toName = (arc.getTo() != null) ? arc.getTo().getName() : "null";
            sb.append(String.format("    { from: \"%s\", to: \"%s\", multiplicity: %d },\n", fromName, toName, arc.getMultiplicity()));
        }
        sb.append("  ],\n");

        sb.append("  Output Arcs (Transition -> Place): [\n");
        for (ArcOut arc : this.getOutputArcs()) {
            String fromName = (arc.getFrom() != null) ? arc.getFrom().getName() : "null";
            String toName = (arc.getTo() != null) ? arc.getTo().getName() : "null";
            sb.append(String.format("    { from: \"%s\", to: \"%s\", multiplicity: %d },\n", fromName, toName, arc.getMultiplicity()));
        }
        sb.append("  ]\n");

        return sb.toString();
    }
}

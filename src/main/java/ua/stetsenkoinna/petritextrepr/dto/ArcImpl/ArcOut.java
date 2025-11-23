package ua.stetsenkoinna.petritextrepr.dto.ArcImpl;

import ua.stetsenkoinna.petritextrepr.dto.Arc;
import ua.stetsenkoinna.petritextrepr.dto.Place;
import ua.stetsenkoinna.petritextrepr.dto.Transition;

public class ArcOut extends Arc {

    private Place to;
    private Transition from;

    public ArcOut(final int id){
        super(id);
    }

    public void setArcOut(final Place to, final Transition from){
        this.to = to;
        this.from = from;

        if (this.from != null) {
            this.from.getOutputArcs().add(this);
        }
    }

    public Place getTo() {
        return to;
    }

    public void setTo(Place to) {
        this.to = to;
    }

    public Transition getFrom() {
        return from;
    }

    public void setFrom(Transition from) {
        this.from = from;
    }
}
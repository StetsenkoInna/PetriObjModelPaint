package ua.stetsenkoinna.petritextrepr.dto.ArcImpl;

import ua.stetsenkoinna.petritextrepr.dto.Arc;
import ua.stetsenkoinna.petritextrepr.dto.Place;
import ua.stetsenkoinna.petritextrepr.dto.Transition;

public class ArcIn extends Arc {

    private Place from;
    private Transition to;
    private boolean isInhibitor = false;

    public ArcIn(final int id){
        super(id);
    }

    public void setArcIn(final Place from, final Transition to, boolean isInhibitor){
        this.from = from;
        this.to = to;
        this.isInhibitor = isInhibitor;

        this.to.getInputArcs().add(this);
    }

    public void setArcIn(final Place from, final Transition to){
        this.setArcIn(from, to, false);
    }

    public Place getFrom() {
        return from;
    }

    public void setFrom(Place from) {
        this.from = from;
    }

    public Transition getTo() {
        return to;
    }

    public void setTo(Transition to) {
        this.to = to;
    }

    public boolean isInhibitor() {
        return isInhibitor;
    }

    public void setInhibitor(boolean inhibitor) {
        isInhibitor = inhibitor;
    }
}
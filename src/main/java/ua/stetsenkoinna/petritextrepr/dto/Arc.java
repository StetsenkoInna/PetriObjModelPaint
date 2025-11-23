package ua.stetsenkoinna.petritextrepr.dto;


public abstract class Arc {
    final int id;
    int multiplicity = 1;

    public Arc(){
        id = 0;
    }

    public Arc(final int id){
        this.id = id;
    }

    public int getId() {
        return id;
    }

    public int getMultiplicity() {
        return multiplicity;
    }

    public void setMultiplicity(int multiplicity) {
        this.multiplicity = multiplicity;
    }
}

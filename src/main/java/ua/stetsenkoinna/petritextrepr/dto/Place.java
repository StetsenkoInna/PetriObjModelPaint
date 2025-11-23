package ua.stetsenkoinna.petritextrepr.dto;

public class Place {

    public Place(){
        id = 0;
    }

    private String name;
    private final int id;
    private int tokens;

    public String getName() {
        return name;
    }

    public int getId() {
        return id;
    }

    public int getTokens() {
        return tokens;
    }


    public void setName(String name) {
        this.name = name;
    }

    public void setTokens(int tokens) {
        this.tokens = tokens;
    }
}

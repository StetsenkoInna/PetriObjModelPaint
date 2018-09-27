/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package PetriObj;

import java.util.ArrayList;

/**
 *
 *  @author Inna V. Stetsenko
 */
public class Protocol {
    private ArrayList<StateModel> states;
    private PetriObjModel model;
    
    public Protocol(PetriObjModel m){
        states = new ArrayList<>();
        model = m;
    }
    
    public void writeState(){
        getList().add(new StateModel(getModel()));
    }
    
    
    
    public void clear(){
        getList().clear();
    }
    
    public void print(){
        for(StateModel s: states)
            s.printState();
    }

    /**
     * @return the states
     */
    public ArrayList<StateModel> getList() {
        return states;
    }

    /**
     * @param list the states to set
     */
    public void setList(ArrayList<StateModel> list) {
        this.states = list;
    }

    /**
     * @return the model
     */
    public PetriObjModel getModel() {
        return model;
    }

    /**
     * @param model the model to set
     */
    public void setModel(PetriObjModel model) {
        this.model = model;
    }
    
}

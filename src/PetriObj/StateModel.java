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
public class StateModel {
    private String id;
    private double timeCurrent;
    private ArrayList<StateSim> state = new ArrayList<>();
    
    public StateModel(PetriObjModel model){
        id = model.getId();
        timeCurrent = model.getCurrentTime();
        for(PetriSim sim: model.getListObj())
            state.add(new StateSim(sim));
    }
   
    public void printState() {
        System.out.println("===============");
        System.out.println(id + ",  time = "+this.getTimeCurrent());
        System.out.println("===============");
         for(StateSim s: state){
             s.printState();
             System.out.println("------------");
         }
    }

    /**
     * @return the timeCurrent
     */
    public Double getTimeCurrent() {
        return timeCurrent;
    }

    /**
     * @param timeCurrent the timeCurrent to set
     */
    public void setTimeCurrent(Double timeCurrent) {
        this.timeCurrent = timeCurrent;
    }

    /**
     * @return the state
     */
    public ArrayList<StateSim> getState() {
        return state;
    }

    /**
     * @param state the state to set
     */
    public void setState(ArrayList<StateSim> state) {
        this.state = state;
    }

    /**
     * @return the id
     */
    public String getId() {
        return id;
    }

    /**
     * @param id the id to set
     */
    public void setId(String id) {
        this.id = id;
    }
}

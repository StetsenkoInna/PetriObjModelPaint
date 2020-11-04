/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package PetriObj;

/**
 *
 * @author Inna V. Stetsenko
 * this class present information of state of stochastic Petri net
 */
public class StateSim {
    private String id;
    private double timeCurrent;
    private StateNet state;
    
    
    
    public StateSim(PetriSim sim){
        id = sim.getId();
        timeCurrent = sim.getCurrentTime();
        state = new StateNet(sim.getNet());
    }
   
    public void printState() {
        System.out.println(id+"   "+this.getTimeCurrent());
        state.printState();
 
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
    public StateNet getState() {
        return state;
    }

    /**
     * @param state the state to set
     */
    public void setState(StateNet state) {
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

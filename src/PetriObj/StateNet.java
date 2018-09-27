/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package PetriObj;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

/**
 *
 *  @author Inna V. Stetsenko
 * this class present information of state of stochastic Petri net
 */
public class StateNet {
    
    private Map<String, Integer> stateP = new HashMap<>();
    private Map<String, ArrayList<Double>> stateT = new HashMap<>();
   
    public StateNet(PetriNet net){
       
        for(PetriP p: net.getListP()){
            stateP.put(p.getId(), p.getMark());
        }
        for(PetriT tr: net.getListT()){
            stateT.put(tr.getId(), tr.getTimeOut());
        }
    }
    
    public void printState() {
               
        Set set = stateP.entrySet();
        Iterator i = set.iterator();
        
        while(i.hasNext()){
            Map.Entry e = (Map.Entry)i.next();
            System.out.println(e.getKey()+"\t "+e.getValue());
        }
       
        set = stateT.entrySet();
        i = set.iterator();
        
        while(i.hasNext()){
            Map.Entry e = (Map.Entry)i.next();
            System.out.println(e.getKey()+"\t  "+e.getValue());
        }
    }

    /**
     * @return the stateP
     */
    public Map<String, Integer> getStateP() {
        return stateP;
    }

    /**
     * @param stateP the stateP to set
     */
    public void setStateP(Map<String, Integer> stateP) {
        this.stateP = stateP;
    }

    /**
     * @return the stateT
     */
    public Map<String, ArrayList<Double>> getStateT() {
        return stateT;
    }

    /**
     * @param stateT the stateT to set
     */
    public void setStateT(Map<String, ArrayList<Double>> stateT) {
        this.stateT = stateT;
    }
    
    
    
}

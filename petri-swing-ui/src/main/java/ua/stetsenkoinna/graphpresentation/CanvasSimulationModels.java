package ua.stetsenkoinna.graphpresentation;

import java.util.ArrayList;
import javax.swing.JTextArea;
import ua.stetsenkoinna.graphnet.*;
import ua.stetsenkoinna.petriobj.*;

/** The editor and review execute exactly the same object topology and animation matching. */
public final class CanvasSimulationModels {
    private CanvasSimulationModels() { }
    public static RunPetriObjModel run(PetriNetsPanel canvas, String name, double start, double horizon, JTextArea protocol)
            throws ExceptionInvalidNetStructure, ExceptionInvalidTimeDelay {
        return run(canvas.getCanvasModel(), name, start, horizon, protocol);
    }
    public static RunPetriObjModel run(GraphCanvasModel canvas, String name, double start, double horizon, JTextArea protocol)
            throws ExceptionInvalidNetStructure, ExceptionInvalidTimeDelay {
        if (canvas.getNet().getGraphPetriTransitionList().isEmpty() || canvas.getNet().getGraphPetriPlaceList().isEmpty()) throw new ExceptionInvalidNetStructure("The saved model has no executable places and transitions.");
        if (canvas.getNet().getGraphPetriTransitionList().isEmpty() || canvas.getNet().getGraphPetriPlaceList().isEmpty()) throw new ExceptionInvalidNetStructure("The saved model has no executable places and transitions.");
        canvas.getNet().createPetriNet(name);
        var source = canvas.toObjModel();
        ArrayList<PetriSim> objects = new ArrayList<>();
        for (var object : source.getObjects()) {
            var sim = GraphPetriObjModel.createPetriSim(object);
            sim.setSimulationTime(horizon); sim.setTimeCurr(start); objects.add(sim);
        }
        var model = new RunPetriObjModel(objects, protocol);
        for (var link : source.getLinks()) model.addLink(link);
        model.validateStructure(); return model;
    }
    public static AnimRunPetriObjModel animated(PetriNetsPanel canvas, String name, double start, double horizon, JTextArea protocol, AnimationSpeedControl speed)
            throws ExceptionInvalidNetStructure, ExceptionInvalidTimeDelay {
        canvas.getGraphNet().createPetriNet(name);
        var source = canvas.getCanvasModel().toObjModel();
        ArrayList<AnimRunPetriSim> objects = new ArrayList<>(); StateTime clock = new StateTime();
        for (var object : source.getObjects()) {
            object.getGraphNet().createPetriNet(object.getName());
            var sim = new AnimRunPetriSim(object.getGraphNet().getPetriNet(), clock, protocol, canvas, speed, null, object.getGraphNet());
            sim.setName(object.getName()); sim.setPriority(object.getPriority());
            sim.setSimulationTime(horizon); sim.setTimeCurr(start); objects.add(sim);
        }
        var model = new AnimRunPetriObjModel(objects, protocol);
        for (var sim : objects) sim.setParentModel(model);
        for (var link : source.getLinks()) model.addLink(link);
        model.validateStructure(); return model;
    }
}

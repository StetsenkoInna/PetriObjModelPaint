package ua.stetsenkoinna.server.adapter;

import ua.stetsenkoinna.petriobj.PetriP;
import ua.stetsenkoinna.petriobj.PetriSim;
import ua.stetsenkoinna.petriobj.PetriT;
import ua.stetsenkoinna.server.dto.ObjectModelResultDto;
import ua.stetsenkoinna.server.dto.SimulationResultDto;

import java.util.ArrayList;
import java.util.List;

/**
 * Turns the finished Petri-objects of a run into the per-object result the v2 API returns.
 */
public final class ObjectModelResults {

    private ObjectModelResults() {}

    /**
     * @param simulationTime the run's configured time span
     * @param finalTime the model time the run actually reached
     * @param totalSteps number of snapshots taken
     * @param objects the simulated Petri-objects
     * @return the result grouped by Petri-object, in model order
     */
    public static ObjectModelResultDto of(double simulationTime, double finalTime, int totalSteps,
                                          Iterable<PetriSim> objects) {
        List<ObjectModelResultDto.ObjectResultDto> results = new ArrayList<>();
        for (PetriSim sim : objects) {
            List<SimulationResultDto.PlaceResultDto> places = new ArrayList<>();
            for (PetriP place : sim.getNet().getListP()) {
                places.add(new SimulationResultDto.PlaceResultDto(
                        place.getId(), place.getName(), place.getMark(),
                        place.getMean(), place.getObservedMin(), place.getObservedMax()
                ));
            }
            List<SimulationResultDto.TransitionResultDto> transitions = new ArrayList<>();
            for (PetriT transition : sim.getNet().getListT()) {
                transitions.add(new SimulationResultDto.TransitionResultDto(
                        transition.getId(), transition.getName(), transition.getBuffer(),
                        transition.getMean(), transition.getObservedMin(), transition.getObservedMax()
                ));
            }
            results.add(new ObjectModelResultDto.ObjectResultDto(
                    sim.getStatisticId(), sim.getName(), sim.getPriority(), places, transitions));
        }
        results.sort(java.util.Comparator.comparingInt(ObjectModelResultDto.ObjectResultDto::index));
        return new ObjectModelResultDto(simulationTime, finalTime, totalSteps, results);
    }
}

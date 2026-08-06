package ua.stetsenkoinna.graphpresentation.objmodel;

import ua.stetsenkoinna.graphnet.GraphPetriObjModel;
import ua.stetsenkoinna.petriobj.PetriObjModel;
import ua.stetsenkoinna.petriobj.PetriP;
import ua.stetsenkoinna.petriobj.PetriSim;
import ua.stetsenkoinna.petriobj.PetriT;

/**
 * Renders the outcome of a Petri-object model run as text, grouped per object.
 *
 * <p>Objects are labelled {@code O0}, {@code O1}, … — the same names statistic formulas use,
 * so a reader can go straight from a line of this report to a formula such as
 * {@code P_AVG(O1.P2)}.
 */
public final class ModelStatistics {

    private ModelStatistics() {}

    /**
     * @param graphModel the model that was run, used for its name
     * @param model the simulated model, may be {@code null} when the run never started
     * @return a human-readable report of mean markings and transition loads
     */
    public static String report(GraphPetriObjModel graphModel, PetriObjModel model) {
        if (model == null) {
            return "No simulation results.";
        }
        StringBuilder report = new StringBuilder();
        report.append("Results of ").append(graphModel.getName())
                .append(" at time ").append(format(model.getCurrentTime())).append('\n');

        for (PetriSim object : model.getListObj()) {
            report.append('\n').append('O').append(object.getStatisticId())
                    .append("  ").append(object.getName()).append('\n');
            report.append("  mean number of tokens in places\n");
            for (PetriP place : object.getNet().getListP()) {
                report.append("    ").append(place.getName())
                        .append("  mean ").append(format(place.getMean()))
                        .append(", final ").append(place.getMark())
                        .append(", max ").append(place.getObservedMax()).append('\n');
            }
            report.append("  mean number of active transition channels\n");
            for (PetriT transition : object.getNet().getListT()) {
                report.append("    ").append(transition.getName())
                        .append("  mean ").append(format(transition.getMean()))
                        .append(", final ").append(transition.getBuffer())
                        .append(", max ").append(transition.getObservedMax()).append('\n');
            }
        }
        return report.toString();
    }

    private static String format(double value) {
        return String.format("%.4f", value);
    }
}

package ua.stetsenkoinna.graphpresentation.objmodel;

import ua.stetsenkoinna.graphnet.GraphPetriNet;
import ua.stetsenkoinna.graphpresentation.statistic.dto.data.StatisticGraphMonitor;

import java.awt.Point;

/**
 * What the structure layer needs from the net editor it sits on top of.
 *
 * <p>Keeping it to this handful of calls means the structure window drives the editor
 * without knowing how the editor is built.
 */
public interface NetEditorBridge {

    /**
     * Opens a net on the editor canvas for editing.
     *
     * <p>The very same instance is handed over, not a copy, so what the user draws goes
     * straight into the Petri-object the net belongs to.
     *
     * @param net the net to show
     * @param name name to display for it
     */
    void openNet(GraphPetriNet net, String name);

    /**
     * @return the net currently on the canvas, or {@code null} when the canvas is empty
     */
    GraphPetriNet getCanvasNet();

    /**
     * @return a point around which a freshly built net should be laid out
     */
    Point getCanvasCentre();

    /**
     * @return the simulation time configured in the editor, used as the default for a model
     *         run
     */
    double getSimulationTime();

    /**
     * Builds the statistics monitor a model run should report to.
     *
     * <p>Which elements are watched, and how the values are charted, is configured in the
     * editor's statistics dialog; a formula addresses an object of the model by its index,
     * as in {@code P_AVG(O1.P2)}.
     *
     * @param blocking true for a run that finishes before the charts are read, so the
     *        monitor has to wait for its worker
     * @return the monitor, or {@code null} when statistics monitoring is switched off
     */
    StatisticGraphMonitor createStatisticMonitor(boolean blocking);
}

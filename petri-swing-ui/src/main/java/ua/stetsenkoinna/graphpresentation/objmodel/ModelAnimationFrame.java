package ua.stetsenkoinna.graphpresentation.objmodel;

import ua.stetsenkoinna.graphnet.GraphPetriObjModel;
import ua.stetsenkoinna.graphnet.GraphPetriObject;
import ua.stetsenkoinna.graphpresentation.AnimRunPetriObjModel;
import ua.stetsenkoinna.graphpresentation.AnimRunPetriSim;
import ua.stetsenkoinna.graphpresentation.PetriNetsPanel;
import ua.stetsenkoinna.graphpresentation.statistic.dto.data.StatisticGraphMonitor;
import ua.stetsenkoinna.petriobj.PetriObjLink;
import ua.stetsenkoinna.petriobj.StateTime;
import ua.stetsenkoinna.utils.MessageHelper;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSlider;
import javax.swing.JSplitPane;
import javax.swing.JTextArea;
import javax.swing.JToolBar;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.awt.Window;
import java.util.ArrayList;
import java.util.List;

/**
 * Animates a whole Petri-object model: every object gets a view of its own and they all run
 * against one simulation clock.
 *
 * <p>Watching a composed model one object at a time hides exactly what composition is
 * about — a token leaving one object and arriving in another. Here the views are side by
 * side, so a shared place changes in both at the same moment.
 */
public class ModelAnimationFrame extends JFrame {

    /** How often the views are repainted while the simulation runs, in milliseconds. */
    private static final int REPAINT_INTERVAL_MS = 120;
    private static final int MIN_DELAY_MS = 0;
    private static final int MAX_DELAY_MS = 400;
    private static final int DEFAULT_DELAY_MS = 60;
    private static final Dimension VIEW_SIZE = new Dimension(520, 360);

    private final GraphPetriObjModel graphModel;
    private final double simulationTime;
    private final StatisticGraphMonitor statisticMonitor;

    private final JTextArea protocolArea = new JTextArea();
    private final JSlider speedSlider = new JSlider(MIN_DELAY_MS, MAX_DELAY_MS, DEFAULT_DELAY_MS);
    private final JButton pauseButton = new JButton("Pause");
    private final JButton stopButton = new JButton("Stop");
    private final JLabel statusLabel = new JLabel(" ");

    private final List<PetriNetsPanel> views = new ArrayList<>();
    private AnimRunPetriObjModel runningModel;
    private Timer repaintTimer;

    /**
     * @param owner window to centre on
     * @param graphModel the model to animate; its nets are rebuilt before the run
     * @param simulationTime how long to simulate, in model time units
     * @param statisticMonitor where to report watched elements, or {@code null} when
     *        statistics monitoring is switched off
     */
    public ModelAnimationFrame(Window owner, GraphPetriObjModel graphModel, double simulationTime,
                               StatisticGraphMonitor statisticMonitor) {
        super("Petri-object model animation — " + graphModel.getName());
        this.graphModel = graphModel;
        this.simulationTime = simulationTime;
        this.statisticMonitor = statisticMonitor;

        buildUi();
        setSize(1100, 720);
        setLocationRelativeTo(owner);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
    }

    private void buildUi() {
        JToolBar toolBar = new JToolBar();
        toolBar.setFloatable(false);
        pauseButton.addActionListener(e -> togglePause());
        stopButton.addActionListener(e -> stop());
        toolBar.add(pauseButton);
        toolBar.add(stopButton);
        toolBar.addSeparator();
        toolBar.add(new JLabel("Step delay, ms "));
        speedSlider.setMaximumSize(new Dimension(220, 40));
        speedSlider.setPaintTicks(true);
        speedSlider.setMajorTickSpacing(100);
        toolBar.add(speedSlider);
        toolBar.addSeparator();
        toolBar.add(statusLabel);

        JPanel viewGrid = new JPanel(new GridLayout(0, columnsFor(graphModel.getObjectCount()), 6, 6));
        viewGrid.setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));
        for (GraphPetriObject object : graphModel.getObjects()) {
            viewGrid.add(createView(object));
        }

        protocolArea.setEditable(false);
        protocolArea.setRows(8);

        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT,
                new JScrollPane(viewGrid), new JScrollPane(protocolArea));
        split.setResizeWeight(0.75);

        setLayout(new BorderLayout());
        add(toolBar, BorderLayout.NORTH);
        add(split, BorderLayout.CENTER);
    }

    private static int columnsFor(int objectCount) {
        return objectCount <= 1 ? 1 : (objectCount <= 4 ? 2 : 3);
    }

    /**
     * Builds the read-only canvas one Petri-object is drawn on.
     */
    private JPanel createView(GraphPetriObject object) {
        PetriNetsPanel panel = new PetriNetsPanel(null, false);
        panel.setGraphNet(object.getGraphNet());
        panel.setPreferredSize(VIEW_SIZE);
        views.add(panel);

        JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.setBorder(BorderFactory.createTitledBorder(
                "O" + graphModel.indexOf(object) + "  " + object.getName()));
        wrapper.add(new JScrollPane(panel), BorderLayout.CENTER);
        return wrapper;
    }

    /**
     * Builds the animated model and starts it on a background thread.
     *
     * @return true if the run started, false if the model could not be built
     */
    public boolean start() {
        ArrayList<AnimRunPetriSim> objects = new ArrayList<>();
        try {
            StateTime clock = new StateTime();
            for (int index = 0; index < graphModel.getObjectCount(); index++) {
                GraphPetriObject object = graphModel.getObject(index);
                object.getGraphNet().createPetriNet(object.getName());
                AnimRunPetriSim simulator = new AnimRunPetriSim(
                        object.getGraphNet().getPetriNet(), clock,
                        protocolArea, views.get(index), speedSlider, null);
                simulator.setName(object.getName());
                simulator.setPriority(object.getPriority());
                objects.add(simulator);
            }
        } catch (Exception failure) {
            MessageHelper.showException(this, "Cannot build the Petri-object model", failure);
            return false;
        }

        runningModel = new AnimRunPetriObjModel(objects, protocolArea);
        for (AnimRunPetriSim simulator : objects) {
            simulator.setParentModel(runningModel);
        }
        try {
            for (PetriObjLink link : graphModel.getLinks()) {
                runningModel.addLink(link);
            }
        } catch (IllegalArgumentException invalid) {
            MessageHelper.showError(this, "The model cannot be linked: " + invalid.getMessage());
            return false;
        }
        runningModel.setIsProtokol(true);
        if (statisticMonitor != null) {
            runningModel.setStatisticMonitor(statisticMonitor);
        }

        statusLabel.setText("running");
        repaintTimer = new Timer(REPAINT_INTERVAL_MS, e -> views.forEach(PetriNetsPanel::repaint));
        repaintTimer.start();

        Thread simulation = new Thread(() -> {
            try {
                runningModel.go(simulationTime);
            } catch (RuntimeException failure) {
                SwingUtilities.invokeLater(() ->
                        MessageHelper.showException(this, "The simulation failed", failure));
            } finally {
                SwingUtilities.invokeLater(this::onFinished);
            }
        }, "petri-object-animation");
        simulation.setDaemon(true);
        simulation.start();
        return true;
    }

    private void onFinished() {
        if (repaintTimer != null) {
            repaintTimer.stop();
        }
        views.forEach(PetriNetsPanel::repaint);
        pauseButton.setEnabled(false);
        statusLabel.setText(runningModel != null && runningModel.isHalted() ? "stopped" : "finished");
        protocolArea.append("\n\n" + ModelStatistics.report(graphModel, runningModel));
        protocolArea.setCaretPosition(protocolArea.getDocument().getLength());
    }

    private void togglePause() {
        if (runningModel == null) {
            return;
        }
        boolean pause = !runningModel.isPaused();
        runningModel.setPaused(pause);
        if (!pause) {
            synchronized (runningModel) {
                runningModel.notifyAll();
            }
        }
        pauseButton.setText(pause ? "Resume" : "Pause");
        statusLabel.setText(pause ? "paused" : "running");
    }

    private void stop() {
        if (runningModel != null) {
            runningModel.halt();
        }
    }

    @Override
    public void dispose() {
        stop();
        if (repaintTimer != null) {
            repaintTimer.stop();
        }
        super.dispose();
    }
}

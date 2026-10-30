package ua.stetsenkoinna.graphpresentation.training;

import java.awt.BorderLayout;
import java.awt.Dialog;
import java.awt.FlowLayout;
import java.awt.Window;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import javax.swing.*;
import ua.stetsenkoinna.graphnet.GraphCanvasModel;
import ua.stetsenkoinna.graphpresentation.PetriNetsPanel;
import ua.stetsenkoinna.pnml.PnmlModelParser;

/** A detached saved revision with ordinary canvas pan/zoom; no edits reach the bundle. */
public final class TrainingRevisionCanvasWindow extends ua.stetsenkoinna.graphpresentation.SimulationFrame {
    private final PetriNetsPanel canvas;
    private final ua.stetsenkoinna.graphpresentation.AnimationControls simulationControls = new ua.stetsenkoinna.graphpresentation.AnimationControls(this);
    private final ua.stetsenkoinna.graphpresentation.AnimationSpeedControl speed = new ua.stetsenkoinna.graphpresentation.AnimationSpeedControl();
    private final JSpinner startTime = new JSpinner(new SpinnerNumberModel(0.0, 0.0, Double.MAX_VALUE, 1.0));
    private final JSpinner horizon = new JSpinner(new SpinnerNumberModel(1000.0, 0.01, Double.MAX_VALUE, 10.0));
    private final JTextArea protocol = new SimulationEvidence.Log(), statistics = new JTextArea();
    private final JProgressBar progress = new JProgressBar(0, 100);
    private final JLabel simulationStatus = new JLabel("Saved revision · simulation changes only this window's working copy");
    private ua.stetsenkoinna.graphpresentation.statistic.StatisticMonitorDialog monitor;
    private final JCheckBox monitorEnabled = new JCheckBox("Collect monitor data");
    private JComboBox<String> objects;
    private volatile boolean simulationBusy;
    private volatile double attemptedStart, attemptedHorizon;
    private boolean disposed;
    private final String sourceModelHash;
    private java.util.function.Consumer<Map<String, Object>> simulationEvidenceListener = record -> { };
    public void setSimulationEvidenceListener(java.util.function.Consumer<Map<String, Object>> listener) { simulationEvidenceListener = listener; }

    private final ButtonGroup navigationModes = new ButtonGroup();
    private final JTabbedPane detailsTabs = new JTabbedPane();
    public record Selection(String elementId, TrainingBundleWorkspace.Region region, String location) { }


    public TrainingRevisionCanvasWindow(Window owner, String title, Map<String, byte[]> files) throws Exception {
        super(title);
        sourceModelHash = TrainingBundleWorkspace.hash(files.get("model.pnml"));
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setModalExclusionType(Dialog.ModalExclusionType.APPLICATION_EXCLUDE);
        PnmlModelParser parser = new PnmlModelParser();
        GraphCanvasModel model = GraphCanvasModel.fromObjModel(parser.parseXml(new String(files.get("model.pnml"), StandardCharsets.UTF_8)));
        if (!parser.getWarnings().isEmpty() || !model.getLoadWarnings().isEmpty()) {
            throw new IllegalArgumentException("Saved revision requires import repairs and cannot be displayed unchanged");
        }
        canvas = new PetriNetsPanel(null, PetriNetsPanel.Mode.REVIEW);
        for (var frame : model.getFrames()) { frame.setCollapsed(false); frame.setContentVisible(true); }
        canvas.setCanvasModel(model);
        timer = new Timer(250, event -> canvas.repaint());
        canvas.setAnimationPace(speed);
        speed.addChangeListener(() -> timer.setDelay(speed.repaintIntervalMillis()));
        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT));
        objects = new JComboBox<>(); objects.addItem("Complete model");
        for (int index = 0; index < model.getFrames().size(); index++) objects.addItem("O" + index + "  " + model.getFrames().get(index).getName());
        objects.getAccessibleContext().setAccessibleName("Revision canvas level");
        objects.addActionListener(event -> { canvas.openObjectCanvas(objects.getSelectedIndex() == 0 ? null : canvas.getCanvasModel().getFrames().get(objects.getSelectedIndex() - 1)); canvas.fitDrawingToViewport(); });
        canvas.getCanvasStack().addChangeListener(() -> {
            var active = canvas.getCanvasStack().getActive(); int index = active == null ? 0 : canvas.getCanvasModel().getFrames().indexOf(active)+1;
            if (objects.getSelectedIndex() != index) objects.setSelectedIndex(index);
        });
        JButton fit = new JButton("Fit model"); fit.addActionListener(event -> canvas.fitDrawingToViewport());
        JPanel navigation = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton maximize = new JButton("Maximize");
        maximize.addActionListener(event -> setExtendedState((getExtendedState() & java.awt.Frame.MAXIMIZED_BOTH) == 0 ? java.awt.Frame.MAXIMIZED_BOTH : java.awt.Frame.NORMAL));
        addWindowStateListener(event -> {
            maximize.setText((getExtendedState() & java.awt.Frame.MAXIMIZED_BOTH) == 0 ? "Maximize" : "Restore window");
            SwingUtilities.invokeLater(canvas::fitDrawingToViewport);
        });
        navigation.add(objects); navigation.add(fit); navigation.add(maximize);
        JToggleButton inspect = new JToggleButton("Inspect / select", true);
        JToggleButton pan = new JToggleButton("Pan"); navigationModes.add(inspect); navigationModes.add(pan);
        pan.addActionListener(event -> { canvas.setTool(ua.stetsenkoinna.graphpresentation.CanvasTool.PAN); canvas.setReviewFindingMode(false); });
        toolbar.add(pan);
        toolbar.add(inspect);
        JButton allParameters = new JButton("All parameters"); toolbar.add(allParameters);
        JTextArea parameters = new JTextArea(8, 27); parameters.setEditable(false); parameters.setLineWrap(true); parameters.setWrapStyleWord(true);
        parameters.setText("Select an element to inspect it. Double-click for its usual read-only parameter form, or double-click an object to open it. Middle-drag pans.");
        allParameters.addActionListener(event -> {
            parameters.setText(ReviewParameters.entries(canvas.getCanvasModel()).stream().map(ReviewParameters.Entry::text).collect(java.util.stream.Collectors.joining("\n")));
            parameters.setCaretPosition(0); detailsTabs.setSelectedIndex(0);
        });
        canvas.setReviewSelectionListener(area -> {
            var selected = selectedParameters(canvas.getCanvasModel(), area);
            parameters.setText(selected.isEmpty() ? "No model elements in this area." : selected.stream().map(ReviewParameters.Entry::text).collect(java.util.stream.Collectors.joining("\n")));
            parameters.setCaretPosition(0); detailsTabs.setSelectedIndex(0);
        });
        canvas.setReviewFindingHandler(area -> {
            if (simulationBusy || findingHandler == null) return;
            try { findingHandler.accept(savedSelection(files, area)); }
            catch (Exception failure) { JOptionPane.showMessageDialog(this, failure.getMessage(), "Cannot attach finding", JOptionPane.ERROR_MESSAGE); }
        });
        inspect.addActionListener(event -> { canvas.setTool(ua.stetsenkoinna.graphpresentation.CanvasTool.SELECT); canvas.setReviewFindingMode(false); });
        this.inspectMode = inspect;
        this.toolbar = toolbar;
        detailsTabs.addTab("Parameters", new JScrollPane(parameters));
        detailsTabs.setPreferredSize(new java.awt.Dimension(300, 300)); add(detailsTabs, BorderLayout.EAST);
        JPanel controls = new JPanel(); controls.setLayout(new BoxLayout(controls, BoxLayout.Y_AXIS));
        controls.add(navigation); controls.add(toolbar);
        for (JPanel row : java.util.List.of(navigation, toolbar)) for (var component : row.getComponents()) {
            if (component instanceof AbstractButton button) styleButton(button);
            else component.setPreferredSize(new java.awt.Dimension(Math.min(360, component.getPreferredSize().width), 40));
        }
        add(controls, BorderLayout.NORTH);
        protocol.setEditable(false); statistics.setEditable(false); protocol.setLineWrap(true); statistics.setLineWrap(true);
        detailsTabs.addTab("Events", new JScrollPane(protocol)); detailsTabs.addTab("Statistics", new JScrollPane(statistics));
        add(new JScrollPane(canvas), BorderLayout.CENTER); add(simulationBar(), BorderLayout.SOUTH);
        addWindowListener(new WindowAdapter() {
            @Override public void windowOpened(WindowEvent event) { canvas.fitDrawingToViewport(); }
            @Override public void windowClosed(WindowEvent event) { canvas.setArcFrame.dispose(); canvas.setPositionFrame.dispose(); canvas.setTransitionFrame.dispose(); }
        });
        setMinimumSize(new java.awt.Dimension(720, 500));
        setSize(1100, 800); setLocationRelativeTo(owner);
        if (owner != null) {
            var lifecycle = new WindowAdapter() { @Override public void windowClosed(WindowEvent event) { dispose(); } };
            owner.addWindowListener(lifecycle);
            addWindowListener(new WindowAdapter() { @Override public void windowClosed(WindowEvent event) { owner.removeWindowListener(lifecycle); } });
        }
    }

    private JPanel toolbar;
    private JToggleButton inspectMode, findingMode;
    private java.util.function.Consumer<Selection> findingHandler;

    public void enableFindings(java.util.function.Consumer<Selection> handler) {
        findingHandler = handler;
        findingMode = new JToggleButton("Add finding"); navigationModes.add(findingMode); findingMode.setSelected(true);
        findingMode.getAccessibleContext().setAccessibleName("Add finding mode");
        canvas.setTool(ua.stetsenkoinna.graphpresentation.CanvasTool.MARQUEE); canvas.setReviewFindingMode(true);
        findingMode.addActionListener(event -> { canvas.setTool(ua.stetsenkoinna.graphpresentation.CanvasTool.MARQUEE); canvas.setReviewFindingMode(true); });
        styleButton(findingMode); findingMode.setFont(findingMode.getFont().deriveFont(java.awt.Font.BOLD)); toolbar.add(findingMode);
    }

    private JPanel simulationBar() {
        JPanel bar = new JPanel(); bar.setLayout(new BoxLayout(bar, BoxLayout.Y_AXIS));
        JPanel transport = new JPanel(new FlowLayout(FlowLayout.LEFT));
        for (JSpinner field : java.util.List.of(startTime, horizon)) {
            var editor = new JSpinner.NumberEditor(field, "0.###"); editor.getTextField().setColumns(7); field.setEditor(editor);
            field.setPreferredSize(new java.awt.Dimension(95, 32));
        }
        startTime.getAccessibleContext().setAccessibleName("Review simulation start"); horizon.getAccessibleContext().setAccessibleName("Review simulation horizon");
        transport.add(new JLabel("Start")); transport.add(startTime); transport.add(new JLabel("Until")); transport.add(horizon);
        javax.swing.Action[] actions = {simulationControls.stepBackAction, simulationControls.playPauseAction, simulationControls.stopSimulationAction, simulationControls.runOneEventAction, simulationControls.runNetAction};
        String[] labels = {"Step back", "Play / pause", "Stop / reset", "One event", "Run"};
        for (int i = 0; i < actions.length; i++) { JButton button = new JButton(actions[i]); button.setText(labels[i]); button.setIcon((Icon) actions[i].getValue(Action.LARGE_ICON_KEY)); transport.add(button); }
        transport.add(speed);
        JPanel monitoring = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton openMonitor = new JButton("Open monitor..."); openMonitor.addActionListener(event -> openMonitor());
        monitoring.add(openMonitor); monitoring.add(monitorEnabled); monitoring.add(progress); monitoring.add(simulationStatus);
        progress.setVisible(false); bar.add(horizontalControls(transport, 62)); bar.add(horizontalControls(monitoring, 42)); return bar;
    }

    private static JScrollPane horizontalControls(JPanel row, int height) {
        var scroll = new JScrollPane(row, ScrollPaneConstants.VERTICAL_SCROLLBAR_NEVER, ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        scroll.setBorder(BorderFactory.createEmptyBorder()); scroll.setPreferredSize(new java.awt.Dimension(900, height));
        scroll.setMinimumSize(new java.awt.Dimension(100, height)); scroll.setMaximumSize(new java.awt.Dimension(Integer.MAX_VALUE, height)); return scroll;
    }

    public void openMonitor() {
        if (monitor == null) {
            monitor = new ua.stetsenkoinna.graphpresentation.statistic.StatisticMonitorDialog(this, false);
            monitor.setModalExclusionType(Dialog.ModalExclusionType.APPLICATION_EXCLUDE);
        }
        monitorEnabled.setSelected(true); monitor.setVisible(true);
    }
    public ua.stetsenkoinna.graphpresentation.AnimationControls simulationControls() { return simulationControls; }
    @Override public PetriNetsPanel getPetriNetsPanel() { return canvas; }
    @Override public Integer getNumberOfRuns() { return monitor != null && monitorEnabled.isSelected() ? monitor.getChartDataCollectionConfig().getNumberOfRuns() : 1; }
    @Override public void disableInput() {
        simulationBusy = true;
        SwingUtilities.invokeLater(() -> {
            if (disposed) return;
            detailsTabs.setSelectedIndex(1);
            startTime.setEnabled(false); horizon.setEnabled(false); monitorEnabled.setEnabled(false);
            if (monitor != null && monitorEnabled.isSelected()) monitor.onSimulationStart(); objects.setSelectedIndex(0); objects.setEnabled(false);
            if (findingMode != null) { inspectMode.doClick(); findingMode.setEnabled(false); }
            simulationStatus.setText("Simulation on a detached copy · Stop / reset restores its initial state");
        });
    }
    @Override public void enableInput() {
        simulationBusy = false;
        SwingUtilities.invokeLater(() -> {
            if (disposed) return;
            startTime.setEnabled(true); horizon.setEnabled(true); monitorEnabled.setEnabled(true);
            if (monitor != null && monitorEnabled.isSelected()) monitor.onSimulationEnd(); objects.setEnabled(true);
            if (findingMode != null) findingMode.setEnabled(true);
        });
    }
    @Override public void showRunProgress() { progress.setValue(0); progress.setVisible(true); }
    @Override public void hideRunProgress() { SwingUtilities.invokeLater(() -> progress.setVisible(false)); }
    private record Settings(double start, double end, boolean collect) { }
    private Settings settings() throws Exception {
        return SwingCapture.call(() -> {
            double start = ((Number) startTime.getValue()).doubleValue(), end = ((Number) horizon.getValue()).doubleValue();
            attemptedStart = start; attemptedHorizon = end;
            if (!Double.isFinite(start) || !Double.isFinite(end) || end <= start) throw new IllegalArgumentException("Simulation end must be finite and greater than its start.");
            canvas.getGraphNet().createPetriNet(getTitle());
            if (canvas.getGraphNet().hasParameters()) throw new IllegalArgumentException("This saved revision has unspecified parameters and cannot be simulated.");
            boolean collect = monitor != null && monitorEnabled.isSelected();
            if (collect && !monitor.getIsFormulaValid()) throw new IllegalArgumentException("Set a valid formula in the monitor before collecting data.");
            protocol.setText("---------Events protocol----------\n"); statistics.setText("---------STATISTICS---------\n");
            return new Settings(start, end, collect);
        });
    }
    public boolean isSimulationBusy() { return simulationBusy || animationModel != null || runModel != null; }
    @Override public void runNet() { executeSimulation(false); }
    @Override public void animateNet() { executeSimulation(true); }
    private void executeSimulation(boolean animated) {
        SimulationEvidence.Capture evidence = null;
        String status = "failed", error = "";
        try {
            var settings = settings();
            if (animated) {
                var model = ua.stetsenkoinna.graphpresentation.CanvasSimulationModels.animated(canvas, getTitle(), settings.start(), settings.end(), protocol, speed);
                animationModel = model;
                if (startAnimationStepping) { startAnimationStepping = false; model.stepOnce(); }
                model.setSimulationTime(settings.end()); model.setCurrentTime(settings.start());
                if (settings.collect()) model.setStatisticMonitor(new ua.stetsenkoinna.graphpresentation.statistic.dto.data.StatisticGraphMonitor(monitor, false));
                evidence = new SimulationEvidence.Capture(model, sourceModelHash, "interactive-animation", settings.end(), 1);
                model.setStateListener(evidence::observe);
                canvas.clearAnimationHighlight(); model.go(settings.end());
                status = model.isHalted() ? "stopped" : "completed";
                canvas.clearAnimationHighlight();
            } else {
                var model = ua.stetsenkoinna.graphpresentation.CanvasSimulationModels.run(canvas, getTitle(), settings.start(), settings.end(), protocol);
                model.setSimulationTime(settings.end()); model.setCurrentTime(settings.start());
                var chart = settings.collect() ? new ua.stetsenkoinna.graphpresentation.statistic.dto.data.StatisticGraphMonitor(monitor, true) : null;
                if (chart != null) model.setStatisticMonitor(chart);
                model.setProgressListener(value -> SwingUtilities.invokeLater(() -> progress.setValue((int) (value * 100))));
                evidence = new SimulationEvidence.Capture(model, sourceModelHash, "interactive-run", settings.end(), 1);
                model.setStateListener(evidence::observe); runModel = model;
                model.go(settings.end()); status = model.isHalted() ? "stopped" : "completed";
                if (chart != null) chart.getWorkerStateLatch().await(3, java.util.concurrent.TimeUnit.SECONDS);
            }
            canvas.getGraphNet().printStatistics(statistics::append); canvas.repaint();
        } catch (Exception failure) { error = String.valueOf(failure.getMessage()); reportSimulationFailure(failure); }
        finally {
            runModel = null;
            var record = evidence == null ? SimulationEvidence.failed(sourceModelHash, animated ? "interactive-animation" : "interactive-run", error)
                    : evidence.finish(status, error, limitedProtocol(), ((SimulationEvidence.Log) protocol).truncated(), !status.equals("failed"));
            if (evidence == null) {
                record.put("start_time", Double.isFinite(attemptedStart) ? attemptedStart : 0);
                record.put("horizon", Double.isFinite(attemptedHorizon) ? attemptedHorizon : 0);
            }
            simulationEvidenceListener.accept(record);
        }
    }
    private String limitedProtocol() { String text = protocol.getText(); return text.substring(0, Math.min(65536, text.length())); }
    private void reportSimulationFailure(Exception failure) {
        SwingUtilities.invokeLater(() -> { if (!disposed) { simulationStatus.setText("Simulation failed: " + failure.getMessage()); JOptionPane.showMessageDialog(this, failure.getMessage(), "Cannot simulate revision", JOptionPane.ERROR_MESSAGE); } });
    }
    @Override public void dispose() {
        disposed = true;
        if (animationModel != null) animationModel.halt(); if (runModel != null) runModel.halt();
        if (timer != null) timer.stop(); if (monitor != null) monitor.dispose();
        super.dispose();
    }

    static java.util.List<ReviewParameters.Entry> selectedParameters(GraphCanvasModel model, java.awt.Rectangle area) {
        var ids = new java.util.HashSet<String>();
        for (var node : model.getNet().getGraphPetriPlaceList()) if (area.intersects(node.getBounds())) ids.add(node.getType() + ":" + node.getId());
        for (var node : model.getNet().getGraphPetriTransitionList()) if (area.intersects(node.getBounds())) ids.add(node.getType() + ":" + node.getId());
        int index = 0;
        java.util.List<ua.stetsenkoinna.graphnet.GraphArc> arcs = new java.util.ArrayList<>(); arcs.addAll(model.getNet().getGraphArcInList()); arcs.addAll(model.getNet().getGraphArcOutList());
        for (var arc : arcs) { if (arc.getGraphElement() != null && new java.awt.BasicStroke(8).createStrokedShape(arc.getGraphElement()).intersects(area)) ids.add("canvas-arc-" + index); index++; }
        return ReviewParameters.entries(model).stream().filter(entry -> ids.contains(entry.id())).toList();
    }

    /** Resolve against saved geometry: PNML import may allocate different live element IDs. */
    static Selection savedSelection(Map<String, byte[]> files, java.awt.Rectangle world) throws Exception {
        var geometry = new com.fasterxml.jackson.databind.ObjectMapper().readTree(files.get("model-geometry.json"));
        var transform = geometry.path("transform"); double scale = transform.path("scale").asDouble();
        if (scale <= 0) throw new IllegalArgumentException("This archive has no canvas coordinate mapping. Export a new training archive first.");
        double x = Math.max(0, (world.x - transform.path("origin_x").asDouble()) * scale);
        double y = Math.max(0, (world.y - transform.path("origin_y").asDouble()) * scale);
        double right = Math.min(geometry.path("image").path("width").asDouble(), (world.getMaxX() - transform.path("origin_x").asDouble()) * scale);
        double bottom = Math.min(geometry.path("image").path("height").asDouble(), (world.getMaxY() - transform.path("origin_y").asDouble()) * scale);
        if (right <= x || bottom <= y) throw new IllegalArgumentException("Select an area inside the saved network.");
        var region = new TrainingBundleWorkspace.Region(x, y, right - x, bottom - y, TrainingBundleWorkspace.hash(files.get("network.png")));
        java.util.List<String> ids = new java.util.ArrayList<>(), nodes = new java.util.ArrayList<>();
        var hit = new java.awt.geom.Rectangle2D.Double(x, y, right - x, bottom - y);
        for (var element : geometry.path("elements")) {
            if (element.path("kind").asText().equals("object")) continue;
            var b = element.path("bounds");
            if (hit.intersects(b.path("x").asDouble(), b.path("y").asDouble(), Math.max(1,b.path("width").asDouble()), Math.max(1,b.path("height").asDouble()))) {
                ids.add(element.path("id").asText());
                if (!element.path("kind").asText().equals("arc")) nodes.add(element.path("id").asText());
            }
        }
        String elementId = nodes.size() == 1 ? nodes.get(0) : ids.size() == 1 ? ids.get(0) : "";
        return new Selection(elementId, region, !elementId.isEmpty() ? "Selected element" : "Selected area (" + ids.size() + " elements)");
    }

    private static void styleButton(AbstractButton button) {
        button.setFont(button.getFont().deriveFont(14f));
        button.setMargin(new java.awt.Insets(9, 14, 9, 14));
    }

    public PetriNetsPanel canvas() { return canvas; }

    public static TrainingRevisionCanvasWindow[] showPair(Window owner, TrainingBundleWorkspace workspace, String leftId, String rightId, String leftLabel, String rightLabel) throws Exception {
        TrainingRevisionCanvasWindow left = new TrainingRevisionCanvasWindow(owner, "Revision A - " + leftLabel, workspace.revisionFiles(leftId));
        TrainingRevisionCanvasWindow right;
        try { right = new TrainingRevisionCanvasWindow(owner, "Revision B - " + rightLabel, workspace.revisionFiles(rightId)); }
        catch (Exception failure) { left.dispose(); throw failure; }
        java.awt.Rectangle screen = owner == null ? java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds() : owner.getGraphicsConfiguration().getBounds();
        int width = Math.max(400, (screen.width - 50) / 2), height = Math.max(400, Math.min(800, screen.height - 100));
        left.setBounds(screen.x + 15, screen.y + 35, width, height);
        right.setBounds(screen.x + width + 30, screen.y + 35, width, height);
        left.setVisible(true); right.setVisible(true);
        return new TrainingRevisionCanvasWindow[]{left, right};
    }
}

package ua.stetsenkoinna.graphpresentation.objmodel;

import ua.stetsenkoinna.graphnet.GraphPetriNet;
import ua.stetsenkoinna.graphnet.GraphPetriObjModel;
import ua.stetsenkoinna.graphnet.GraphPetriObject;
import ua.stetsenkoinna.graphpresentation.RunPetriObjModel;
import ua.stetsenkoinna.graphpresentation.statistic.dto.data.StatisticGraphMonitor;
import ua.stetsenkoinna.petriobj.PetriObjLink;
import ua.stetsenkoinna.petriobj.PetriSim;
import ua.stetsenkoinna.pnml.PnmlModelGenerator;
import ua.stetsenkoinna.pnml.PnmlModelParser;
import ua.stetsenkoinna.utils.MessageHelper;

import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.JToolBar;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Point;
import java.awt.Window;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.util.ArrayList;

/**
 * The window in which a Petri-object model is composed.
 *
 * <p>It shows the model's objects and the links between them, and drives the net editor
 * underneath: opening an object puts its net on the canvas, so drawing there changes that
 * object's behaviour. From here a model is also saved, loaded, run and animated.
 */
public class ModelStructureFrame extends JFrame implements ObjectStructurePanel.Listener {

    private static final String PNML_EXTENSION = "pnml";

    private final NetEditorBridge editor;
    private final ObjectStructurePanel structurePanel = new ObjectStructurePanel();
    private final DefaultListModel<String> linkListModel = new DefaultListModel<>();
    private final JList<String> linkList = new JList<>(linkListModel);
    private final JTextArea outputArea = new JTextArea();
    private final JTextField simulationTimeField = new JTextField(8);
    private final JLabel openObjectLabel = new JLabel(" ");

    private GraphPetriObjModel model;
    private File modelFile;
    private int openObject = ObjectStructurePanel.NONE;

    /**
     * @param owner window to centre on
     * @param editor the net editor this structure layer drives
     * @param model the model to edit
     */
    public ModelStructureFrame(Window owner, NetEditorBridge editor, GraphPetriObjModel model) {
        super("Petri-object model structure");
        this.editor = editor;
        this.model = model;

        buildUi();
        setModel(model);
        setSize(980, 640);
        setLocationRelativeTo(owner);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);

        // Nets are edited in the other window, so what is on screen here can go stale.
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowActivated(WindowEvent e) {
                refresh();
            }
        });
    }

    public GraphPetriObjModel getModel() {
        return model;
    }

    /**
     * Replaces the edited model and shows it.
     */
    public final void setModel(GraphPetriObjModel model) {
        this.model = model;
        this.openObject = ObjectStructurePanel.NONE;
        structurePanel.setModel(model);
        setTitle("Petri-object model structure — " + model.getName());
        refresh();
    }

    private void buildUi() {
        structurePanel.setListener(this);
        simulationTimeField.setText(String.valueOf(editor.getSimulationTime()));

        setJMenuBar(buildMenuBar());

        JToolBar toolBar = new JToolBar();
        toolBar.setFloatable(false);
        toolBar.add(button("Add object", e -> addObject()));
        toolBar.add(button("Remove object", e -> removeObject()));
        toolBar.add(button("Open net", e -> openSelectedObject()));
        toolBar.addSeparator();
        toolBar.add(button("Add link", e -> addLink()));
        toolBar.add(button("Remove link", e -> removeLink()));
        toolBar.addSeparator();
        toolBar.add(new JLabel(" simulation time "));
        simulationTimeField.setMaximumSize(new Dimension(90, 30));
        toolBar.add(simulationTimeField);
        toolBar.add(button("Run model", e -> runModel()));
        toolBar.add(button("Animate model", e -> animateModel()));

        linkList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        JPanel linkPanel = new JPanel(new BorderLayout());
        linkPanel.setBorder(BorderFactory.createTitledBorder("Links"));
        linkPanel.add(new JScrollPane(linkList), BorderLayout.CENTER);
        linkPanel.setPreferredSize(new Dimension(280, 100));

        outputArea.setEditable(false);
        outputArea.setRows(9);

        JSplitPane withLinks = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
                new JScrollPane(structurePanel), linkPanel);
        withLinks.setResizeWeight(0.75);

        JSplitPane withOutput = new JSplitPane(JSplitPane.VERTICAL_SPLIT,
                withLinks, new JScrollPane(outputArea));
        withOutput.setResizeWeight(0.7);

        JPanel status = new JPanel(new BorderLayout());
        status.setBorder(BorderFactory.createEmptyBorder(2, 8, 2, 8));
        status.add(openObjectLabel, BorderLayout.WEST);

        setLayout(new BorderLayout());
        add(toolBar, BorderLayout.NORTH);
        add(withOutput, BorderLayout.CENTER);
        add(status, BorderLayout.SOUTH);
    }

    private JMenuBar buildMenuBar() {
        JMenuBar bar = new JMenuBar();

        JMenu file = new JMenu("Model");
        file.add(menuItem("New model", e -> newModel()));
        file.add(menuItem("Open model…", e -> openModel()));
        file.add(menuItem("Save model", e -> saveModel(false)));
        file.add(menuItem("Save model as…", e -> saveModel(true)));
        bar.add(file);

        JMenu objects = new JMenu("Objects");
        objects.add(menuItem("Add object…", e -> addObject()));
        objects.add(menuItem("Open net of selected object", e -> openSelectedObject()));
        objects.add(menuItem("Rename selected object…", e -> renameObject()));
        objects.add(menuItem("Set priority of selected object…", e -> changePriority()));
        objects.add(menuItem("Remove selected object", e -> removeObject()));
        bar.add(objects);

        JMenu links = new JMenu("Links");
        links.add(menuItem("Add link…", e -> addLink()));
        links.add(menuItem("Remove selected link", e -> removeLink()));
        bar.add(links);

        JMenu run = new JMenu("Run");
        run.add(menuItem("Run model", e -> runModel()));
        run.add(menuItem("Animate model", e -> animateModel()));
        bar.add(run);

        return bar;
    }

    private JButton button(String text, java.awt.event.ActionListener action) {
        JButton button = new JButton(text);
        button.setFocusable(false);
        button.addActionListener(action);
        return button;
    }

    private JMenuItem menuItem(String text, java.awt.event.ActionListener action) {
        JMenuItem item = new JMenuItem(text);
        item.addActionListener(action);
        return item;
    }

    // ------------------------------------------------------------------ structure listener

    @Override
    public void objectOpened(int index) {
        openObject(index);
    }

    @Override
    public void selectionChanged(int index) {
        // Selection alone changes nothing else on screen.
    }

    @Override
    public void modelChanged() {
        refresh();
    }

    // ------------------------------------------------------------------ objects

    private void addObject() {
        AddObjectDialog dialog = new AddObjectDialog(this, editor.getCanvasNet(),
                editor.getCanvasCentre(), "Object " + (model.getObjectCount() + 1));
        dialog.setVisible(true);
        GraphPetriObject created = dialog.getCreated();
        if (created == null) {
            return;
        }
        created.setPosition(structurePanel.nextFreePosition());
        int index = model.addObject(created);
        refresh();
        structurePanel.setSelected(index);
        openObject(index);
    }

    private void removeObject() {
        int index = structurePanel.getSelected();
        if (index == ObjectStructurePanel.NONE) {
            MessageHelper.showError(this, "Select a Petri-object first");
            return;
        }
        int attached = structurePanel.linksOf(index).size();
        String question = attached == 0
                ? "Remove Petri-object '" + model.getObject(index).getName() + "'?"
                : "Remove Petri-object '" + model.getObject(index).getName() + "' and its "
                        + attached + " link(s)?";
        if (!MessageHelper.showConfirmation(this, question)) {
            return;
        }
        model.removeObject(index);
        if (openObject == index) {
            openObject = ObjectStructurePanel.NONE;
        }
        refresh();
    }

    private void renameObject() {
        int index = structurePanel.getSelected();
        if (index == ObjectStructurePanel.NONE) {
            MessageHelper.showError(this, "Select a Petri-object first");
            return;
        }
        String name = JOptionPane.showInputDialog(this, "Name of the Petri-object",
                model.getObject(index).getName());
        if (name != null && !name.isBlank()) {
            model.getObject(index).setName(name.trim());
            refresh();
        }
    }

    private void changePriority() {
        int index = structurePanel.getSelected();
        if (index == ObjectStructurePanel.NONE) {
            MessageHelper.showError(this, "Select a Petri-object first");
            return;
        }
        String value = JOptionPane.showInputDialog(this,
                "Priority of the Petri-object — the higher it is, the earlier this object acts "
                        + "when several want to act at the same moment",
                model.getObject(index).getPriority());
        if (value == null) {
            return;
        }
        try {
            model.getObject(index).setPriority(Integer.parseInt(value.trim()));
            refresh();
        } catch (NumberFormatException malformed) {
            MessageHelper.showError(this, "Priority has to be a whole number");
        }
    }

    private void openSelectedObject() {
        int index = structurePanel.getSelected();
        if (index == ObjectStructurePanel.NONE) {
            MessageHelper.showError(this, "Select a Petri-object first");
            return;
        }
        openObject(index);
    }

    /**
     * Puts the object's net on the editor canvas. The net is handed over as is, so drawing
     * changes the object itself.
     */
    private void openObject(int index) {
        GraphPetriObject object = model.getObject(index);
        editor.openNet(object.getGraphNet(), object.getName());
        openObject = index;
        openObjectLabel.setText("Editing the net of O" + index + "  " + object.getName());
    }

    // ------------------------------------------------------------------ links

    private void addLink() {
        if (model.getObjectCount() < 1) {
            MessageHelper.showError(this, "Add Petri-objects before linking them");
            return;
        }
        LinkDialog dialog = new LinkDialog(this, model);
        dialog.setVisible(true);
        PetriObjLink created = dialog.getCreated();
        if (created == null) {
            return;
        }
        try {
            model.addLink(created);
        } catch (IllegalArgumentException invalid) {
            MessageHelper.showError(this, invalid.getMessage());
            return;
        }
        refresh();
    }

    private void removeLink() {
        int index = linkList.getSelectedIndex();
        if (index < 0) {
            MessageHelper.showError(this, "Select a link in the list first");
            return;
        }
        model.removeLink(index);
        refresh();
    }

    // ------------------------------------------------------------------ files

    private void newModel() {
        if (!MessageHelper.showConfirmation(this, "Discard the current model and start a new one?")) {
            return;
        }
        modelFile = null;
        setModel(new GraphPetriObjModel());
    }

    private void openModel() {
        JFileChooser chooser = modelChooser();
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        try {
            GraphPetriObjModel loaded = new PnmlModelParser().parse(chooser.getSelectedFile());
            modelFile = chooser.getSelectedFile();
            setModel(loaded);
            if (loaded.getObjectCount() > 0) {
                openObject(0);
            }
            output("Loaded " + loaded.getObjectCount() + " Petri-object(s) and "
                    + loaded.getLinks().size() + " link(s) from " + modelFile.getName());
        } catch (Exception failure) {
            MessageHelper.showException(this, "Cannot read the Petri-object model", failure);
        }
    }

    private void saveModel(boolean askForFile) {
        File target = modelFile;
        if (askForFile || target == null) {
            JFileChooser chooser = modelChooser();
            chooser.setSelectedFile(new File(model.getName() + "." + PNML_EXTENSION));
            if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) {
                return;
            }
            target = chooser.getSelectedFile();
            if (!target.getName().toLowerCase().endsWith("." + PNML_EXTENSION)) {
                target = new File(target.getAbsolutePath() + "." + PNML_EXTENSION);
            }
            if (target.exists() && !MessageHelper.showConfirmation(this,
                    "File already exists. Overwrite it?")) {
                return;
            }
        }
        try {
            new PnmlModelGenerator().generate(model, target);
            modelFile = target;
            output("Saved the model to " + target.getAbsolutePath());
        } catch (Exception failure) {
            MessageHelper.showException(this, "Cannot save the Petri-object model", failure);
        }
    }

    private JFileChooser modelChooser() {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileFilter(new FileNameExtensionFilter("Petri-object model (*.pnml)", PNML_EXTENSION));
        chooser.setCurrentDirectory(modelFile != null
                ? modelFile.getParentFile()
                : new File(System.getProperty("user.home")));
        return chooser;
    }

    // ------------------------------------------------------------------ running

    private void runModel() {
        if (!hasObjects()) {
            return;
        }
        double time = readSimulationTime();
        if (time <= 0) {
            return;
        }
        RunPetriObjModel simulation;
        try {
            ArrayList<PetriSim> objects = new ArrayList<>();
            for (GraphPetriObject object : model.getObjects()) {
                objects.add(GraphPetriObjModel.createPetriSim(object));
            }
            simulation = new RunPetriObjModel(objects, outputArea);
            for (PetriObjLink link : model.getLinks()) {
                simulation.addLink(link);
            }
        } catch (Exception failure) {
            MessageHelper.showException(this, "Cannot build the Petri-object model", failure);
            return;
        }
        simulation.setIsProtokol(false);
        StatisticGraphMonitor monitor = editor.createStatisticMonitor(true);
        if (monitor != null) {
            simulation.setStatisticMonitor(monitor);
        }
        output("Running " + model.getName() + " for " + time + " time units...");

        Thread run = new Thread(() -> {
            try {
                simulation.go(time);
                SwingUtilities.invokeLater(() -> output(ModelStatistics.report(model, simulation)));
            } catch (RuntimeException failure) {
                SwingUtilities.invokeLater(() ->
                        MessageHelper.showException(this, "The simulation failed", failure));
            }
        }, "petri-object-run");
        run.setDaemon(true);
        run.start();
    }

    private void animateModel() {
        if (!hasObjects()) {
            return;
        }
        double time = readSimulationTime();
        if (time <= 0) {
            return;
        }
        ModelAnimationFrame animation =
                new ModelAnimationFrame(this, model, time, editor.createStatisticMonitor(false));
        animation.setVisible(true);
        if (!animation.start()) {
            animation.dispose();
        }
    }

    private boolean hasObjects() {
        if (model.getObjectCount() == 0) {
            MessageHelper.showError(this, "The model has no Petri-objects yet");
            return false;
        }
        return true;
    }

    private double readSimulationTime() {
        try {
            double time = Double.parseDouble(simulationTimeField.getText().trim());
            if (time <= 0) {
                MessageHelper.showError(this, "Simulation time has to be positive");
                return 0;
            }
            return time;
        } catch (NumberFormatException malformed) {
            MessageHelper.showError(this, "Simulation time has to be a number");
            return 0;
        }
    }

    // ------------------------------------------------------------------ refreshing

    /**
     * Redraws the structure and the link list, after dropping links whose endpoints no
     * longer exist — a place a link pointed at may have been deleted on the net canvas.
     */
    private void refresh() {
        int dropped = model.dropBrokenLinks();
        if (dropped > 0) {
            output(dropped + " link(s) no longer fit the nets and were removed.");
        }
        structurePanel.refresh();

        linkListModel.clear();
        for (PetriObjLink link : model.getLinks()) {
            linkListModel.addElement(describe(link));
        }
        if (openObject != ObjectStructurePanel.NONE && openObject < model.getObjectCount()) {
            openObjectLabel.setText("Editing the net of O" + openObject + "  "
                    + model.getObject(openObject).getName());
        } else {
            openObjectLabel.setText("Double-click a Petri-object to edit its net");
        }
    }

    /**
     * @return the link as one line of the list, naming both objects and both elements
     */
    private String describe(PetriObjLink link) {
        GraphPetriObject source = model.getObject(link.getSourceObject());
        GraphPetriObject target = model.getObject(link.getTargetObject());
        return switch (link.getType()) {
            case PLACE_FUSION -> "shared place: O" + link.getSourceObject() + "."
                    + source.getPlaceName(link.getSourceElement()) + " = O" + link.getTargetObject()
                    + "." + target.getPlaceName(link.getTargetElement());
            case TRANSITION_TO_PLACE -> "O" + link.getSourceObject() + "."
                    + source.getTransitionName(link.getSourceElement()) + " → O"
                    + link.getTargetObject() + "." + target.getPlaceName(link.getTargetElement())
                    + " ×" + link.getQuantity();
            case PLACE_TO_TRANSITION -> "O" + link.getSourceObject() + "."
                    + source.getPlaceName(link.getSourceElement())
                    + (link.isInformational() ? " ⇢ " : " → ") + "O" + link.getTargetObject()
                    + "." + target.getTransitionName(link.getTargetElement())
                    + " ×" + link.getQuantity();
        };
    }

    private void output(String text) {
        outputArea.append(text + "\n");
        outputArea.setCaretPosition(outputArea.getDocument().getLength());
    }

    /**
     * @return the net editor this window drives, for callers that opened it
     */
    public NetEditorBridge getEditor() {
        return editor;
    }

    /**
     * @return the net currently open for editing, or {@code null} when none is
     */
    public GraphPetriNet getOpenNet() {
        return openObject == ObjectStructurePanel.NONE || openObject >= model.getObjectCount()
                ? null
                : model.getObject(openObject).getGraphNet();
    }
}

package ua.stetsenkoinna.graphpresentation;

import ua.stetsenkoinna.petriobj.ExceptionInvalidNetStructure;
import ua.stetsenkoinna.petriobj.ExceptionInvalidTimeDelay;
import ua.stetsenkoinna.petriobj.PetriP;
import ua.stetsenkoinna.petriobj.PetriSim;
import ua.stetsenkoinna.petriobj.PetriT;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import ua.stetsenkoinna.graphpresentation.statistic.StatisticMonitorDialog;
import ua.stetsenkoinna.graphpresentation.statistic.dto.data.StatisticGraphMonitor;
import ua.stetsenkoinna.graphreuse.GraphNetParametersFrame;
import ua.stetsenkoinna.graphpresentation.undoable_edits.AddGraphElementEdit;
import ua.stetsenkoinna.config.ResourcePathConfig;
import ua.stetsenkoinna.pnml.CoordinateNormalizer;
import ua.stetsenkoinna.pnml.PnmlParser;
import ua.stetsenkoinna.pnml.PnmlGenerator;
import ua.stetsenkoinna.pnml.PnmlModelGenerator;
import ua.stetsenkoinna.pnml.PnmlModelParser;
import ua.stetsenkoinna.graphnet.GraphCanvasModel;
import ua.stetsenkoinna.graphpresentation.objmodel.NetTemplateDialog;
import ua.stetsenkoinna.petriobj.PetriNet;
import ua.stetsenkoinna.petriobj.ArcIn;
import ua.stetsenkoinna.petriobj.ArcOut;
import ua.stetsenkoinna.graphnet.GraphPetriPlace;
import ua.stetsenkoinna.graphnet.GraphPetriTransition;
import ua.stetsenkoinna.graphnet.GraphArcIn;
import ua.stetsenkoinna.graphnet.GraphArcOut;
import ua.stetsenkoinna.libnet.NetLibrary;
import ua.stetsenkoinna.libnet.HiddenFromUI;

import java.awt.*;
import java.awt.event.*;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.lang.reflect.Method;

import javax.swing.*;

import ua.stetsenkoinna.graphnet.GraphPetriNet;
import ua.stetsenkoinna.graphnet.GraphObjectFrame;
import ua.stetsenkoinna.graphnet.GraphPetriObjModel;
import ua.stetsenkoinna.graphnet.GraphPetriObject;
import ua.stetsenkoinna.petriobj.PetriObjLink;
import ua.stetsenkoinna.petriobj.StateTime;
import ua.stetsenkoinna.graphpresentation.actions.AnimateEventAction;
import ua.stetsenkoinna.graphpresentation.actions.PlayPauseAction;
import ua.stetsenkoinna.graphpresentation.actions.RewindAction;
import ua.stetsenkoinna.graphpresentation.actions.RunNetAction;
import ua.stetsenkoinna.graphpresentation.actions.RunOneEventAction;
import ua.stetsenkoinna.graphpresentation.actions.StopSimulationAction;
import ua.stetsenkoinna.utils.MessageHelper;

import java.awt.Dialog.ModalityType;
import java.io.ObjectInputStream;
import javax.swing.undo.UndoManager;
import javax.swing.undo.UndoableEditSupport;

public class PetriNetsFrame extends javax.swing.JFrame {

    private static final Logger LOGGER = LoggerFactory.getLogger(PetriNetsFrame.class);

    public Timer timer; // timer that starts repainting while simulation
    private final MethodNameDialogPanel dialogPanel = new MethodNameDialogPanel();
    private JDialog dialog;

    static class MethodNameDialogPanel extends JPanel {
        private final JComboBox<String> combo;
        private final JButton okButton = new JButton("OK");

        private Boolean secondListenerAdded = false;

        public MethodNameDialogPanel() {
            okButton.addActionListener((ActionEvent e) -> okButtonAction());
            combo = new JComboBox<>();
            add(combo);
            add(okButton);
        }

        public void addOkButtonClickHandler(ActionListener listener) {
            if (!secondListenerAdded) {
                okButton.addActionListener(listener);
                secondListenerAdded = true;
            }
        }

        public void setComboOptions(ArrayList<String> methodNames) {
            combo.setModel(new DefaultComboBoxModel<>(methodNames.toArray(new String[0])));															// 27.11.2016
        }

        public String getFieldText() {
            return Objects.requireNonNull(combo.getSelectedItem()).toString();
        }

        private void okButtonAction() {
            Window win = SwingUtilities.getWindowAncestor(this);
            if (win != null) {
                win.dispose();
            }
        }
    }
    
    /* ACTIONS */
    private final AnimationControls animationControls = new AnimationControls(this);
    private final RunNetAction runNetAction = animationControls.runNetAction;
    public final RewindAction rewindAction = animationControls.rewindAction;
    public final StopSimulationAction stopSimulationAction = animationControls.stopSimulationAction;
    public final PlayPauseAction playPauseAction = animationControls.playPauseAction;
    public final RunOneEventAction runOneEventAction = animationControls.runOneEventAction;
    public final AnimateEventAction animateEventAction = animationControls.animateEventAction;

    private void UpdateNetLibraryMethodsCombobox() {
        ArrayList<String> workingMethods = new ArrayList<>();

        for (Method method : NetLibrary.class.getDeclaredMethods()) {
            if (!Modifier.isPublic(method.getModifiers()) ||
                    !Modifier.isStatic(method.getModifiers()) ||
                    method.getReturnType() != PetriNet.class ||
                    method.isAnnotationPresent(HiddenFromUI.class)) {
                continue;
            }
            StringBuilder sig = new StringBuilder(method.getName()).append("(");
            Parameter[] params = method.getParameters();
            for (int i = 0; i < params.length; i++) {
                if (i > 0) sig.append(", ");
                sig.append(params[i].getType().getSimpleName()).append(" ").append(params[i].getName());
            }
            sig.append(")");
            workingMethods.add(sig.toString());
        }

        workingMethods.sort(String.CASE_INSENSITIVE_ORDER);
        leftMenuListModel.clear();
        for (String name : workingMethods) {
            leftMenuListModel.addElement(name);
        }
        dialogPanel.setComboOptions(workingMethods);
    }

    /**
     * Creates new form PetriNetsFrame
     */
    public PetriNetsFrame() {
        initComponents();
        this.UpdateNetLibraryMethodsCombobox();
        timer = new Timer(250, ae -> getPetriNetsPanel().repaint());

        newPlaceButton.setHorizontalTextPosition(javax.swing.SwingConstants.CENTER);
        newPlaceButton.setVerticalTextPosition(javax.swing.SwingConstants.CENTER);
        newPlaceButton.setText("Place");
        newPlaceButton.setBorder(null);
        newPlaceButton.setMargin(new Insets(0, 0, 0, 0));

        newArcButton.setHorizontalTextPosition(javax.swing.SwingConstants.CENTER);
        newArcButton.setVerticalTextPosition(javax.swing.SwingConstants.CENTER);
        newArcButton.setText("Arc");
        newArcButton.setBorder(null);
        newArcButton.setMargin(new Insets(0, 0, 0, 0));

        newTransitionButton.setHorizontalTextPosition(javax.swing.SwingConstants.CENTER);
        newTransitionButton.setVerticalTextPosition(javax.swing.SwingConstants.CENTER);
        newTransitionButton.setText("Transition");

        newTransitionButton.setBorder(null);
        newTransitionButton.setMargin(new Insets(0, 0, 0, 0));

        petriNetsPanel = new PetriNetsPanel(netNameTextField);
        petriNetPanelScrollPane.setViewportView(petriNetsPanel);

        // Enable drag and drop for both PNML and PNS files
        petriNetsPanel.enableDragAndDrop(this);

        this.setLocation(50, 50);
        this.setTitle("Discrete Event Simulation System ");
        this.setSize(1000, 700);

        // Set fullscreen mode - should be called after setSize
        this.setExtendedState(JFrame.MAXIMIZED_BOTH);

        undoSupport.addUndoableEditListener((event) -> {
            undoManager.addEdit(event.getEdit());
            undoMenuItem.setEnabled(undoManager.canUndo());
            redoMenuItem.setEnabled(undoManager.canRedo());
        });

        addModelMenu();
    }

    /**
     * Adds the menu that opens the structure layer, where several nets are composed into one
     * Petri-object model.
     */
    private void addModelMenu() {
        JMenu modelMenu = new JMenu("Model");

        JMenuItem groupItem = new JMenuItem("Group selection into Petri-object");
        groupItem.setToolTipText("Draw a Petri-object frame around the selected elements");
        groupItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_G, InputEvent.CTRL_DOWN_MASK));
        groupItem.addActionListener(e -> groupSelectionIntoObject());
        modelMenu.add(groupItem);

        JMenuItem newFrameItem = new JMenuItem("New empty Petri-object");
        newFrameItem.setToolTipText("Put an empty Petri-object frame on the canvas and draw its net inside");
        newFrameItem.addActionListener(e -> addEmptyObjectFrame());
        modelMenu.add(newFrameItem);

        JMenuItem fromLibraryItem = new JMenuItem("Petri-object from net library...");
        fromLibraryItem.setToolTipText("Instantiate a net library template with arguments of its own");
        fromLibraryItem.addActionListener(e -> addObjectFromLibrary());
        modelMenu.add(fromLibraryItem);

        JMenuItem duplicateItem = new JMenuItem("Duplicate selected Petri-object");
        duplicateItem.setToolTipText("Copy the object together with its net — the way to get N alike");
        duplicateItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_D, InputEvent.CTRL_DOWN_MASK));
        duplicateItem.addActionListener(e -> duplicateSelectedObject());
        modelMenu.add(duplicateItem);

        modelMenu.addSeparator();

        JMenuItem renameItem = new JMenuItem("Rename selected Petri-object...");
        renameItem.addActionListener(e -> renameSelectedObject());
        modelMenu.add(renameItem);

        JMenuItem priorityItem = new JMenuItem("Priority of selected Petri-object...");
        priorityItem.addActionListener(e -> changeSelectedObjectPriority());
        modelMenu.add(priorityItem);

        JMenuItem removeItem = new JMenuItem("Remove selected Petri-object frame");
        removeItem.setToolTipText("The net inside stays on the canvas");
        removeItem.addActionListener(e -> removeSelectedObjectFrame());
        modelMenu.add(removeItem);

        getJMenuBar().add(modelMenu);
    }

    /**
     * Draws a Petri-object frame around whatever is selected, which is how an existing net is
     * split into objects.
     */
    private void groupSelectionIntoObject() {
        java.util.List<ua.stetsenkoinna.graphnet.GraphElement> selection =
                getPetriNetsPanel().getChoosenElements();
        if (selection.isEmpty()) {
            MessageHelper.showError(this, "Select the elements of the Petri-object first");
            return;
        }
        String name = JOptionPane.showInputDialog(this, "Name of the Petri-object",
                "Object " + (getPetriNetsPanel().getCanvasModel().getFrames().size() + 1));
        if (name == null || name.isBlank()) {
            return;
        }

        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;
        for (ua.stetsenkoinna.graphnet.GraphElement element : selection) {
            java.awt.geom.Point2D centre = element.getGraphElementCenter();
            int border = Math.max(element.getBorder(), 20);
            minX = Math.min(minX, (int) centre.getX() - border);
            minY = Math.min(minY, (int) centre.getY() - border);
            maxX = Math.max(maxX, (int) centre.getX() + border);
            maxY = Math.max(maxY, (int) centre.getY() + border);
        }
        int padding = 24;
        Rectangle bounds = new Rectangle(
                Math.max(0, minX - padding),
                Math.max(0, minY - padding - GraphObjectFrame.HEADER_HEIGHT),
                maxX - minX + padding * 2,
                maxY - minY + padding * 2 + GraphObjectFrame.HEADER_HEIGHT);
        getPetriNetsPanel().addObjectFrame(new GraphObjectFrame(name.trim(), bounds));
    }

    /**
     * Puts an empty frame in the middle of the view, to be drawn into.
     */
    private void addEmptyObjectFrame() {
        String name = JOptionPane.showInputDialog(this, "Name of the Petri-object",
                "Object " + (getPetriNetsPanel().getCanvasModel().getFrames().size() + 1));
        if (name == null || name.isBlank()) {
            return;
        }
        Point centre = getCanvasCentre();
        Rectangle bounds = new Rectangle(Math.max(0, centre.x - 180), Math.max(0, centre.y - 120), 360, 240);
        getPetriNetsPanel().addObjectFrame(new GraphObjectFrame(name.trim(), bounds));
    }

    /**
     * Instantiates a net library template as a new Petri-object: its net is laid out on the
     * canvas and a frame is drawn around it.
     */
    private void addObjectFromLibrary() {
        NetTemplateDialog dialog = new NetTemplateDialog(this,
                "Object " + (getPetriNetsPanel().getCanvasModel().getFrames().size() + 1));
        dialog.setVisible(true);
        if (dialog.getBuilt() == null) {
            return;
        }
        try {
            Point centre = freeSpotForNewObject();
            GraphPetriNet built = SimpleNetGraphBuilder.build(dialog.getBuilt(), centre);
            getPetriNetsPanel().addGraphNet(built);

            Rectangle bounds = boundsAround(new ArrayList<>(built.getGraphPetriPlaceList()),
                    new ArrayList<>(built.getGraphPetriTransitionList()));
            GraphObjectFrame frame = new GraphObjectFrame(dialog.getObjectName(), bounds);
            frame.setTemplate(dialog.getReference());
            getPetriNetsPanel().addObjectFrame(frame);
            getPetriNetsPanel().repaint();
        } catch (Exception failure) {
            LOGGER.error("Failed to add a Petri-object from the net library", failure);
            MessageHelper.showException(this, "Cannot put the net library template on the canvas", failure);
        }
    }

    /**
     * Copies the selected Petri-object with its net, which is the quick way to a model of
     * several alike objects.
     */
    private void duplicateSelectedObject() {
        GraphObjectFrame frame = requireSelectedFrame();
        if (frame == null) {
            return;
        }
        GraphCanvasModel canvas = getPetriNetsPanel().getCanvasModel();
        java.util.List<ua.stetsenkoinna.graphnet.GraphElement> inside = new ArrayList<>();
        for (ua.stetsenkoinna.graphnet.GraphPetriPlace place : canvas.getNet().getGraphPetriPlaceList()) {
            if (canvas.ownerOf(place) == frame) {
                inside.add(place);
            }
        }
        for (ua.stetsenkoinna.graphnet.GraphPetriTransition transition : canvas.getNet().getGraphPetriTransitionList()) {
            if (canvas.ownerOf(transition) == frame) {
                inside.add(transition);
            }
        }
        if (inside.isEmpty()) {
            MessageHelper.showError(this, "The Petri-object has no net to copy yet");
            return;
        }

        GraphPetriNet.GraphNetFragment copy = canvas.getNet().bulkCopyNoPasteElements(inside);
        int dx = frame.getBounds().width + 40;
        for (ua.stetsenkoinna.graphnet.GraphElement element : copy.elements) {
            java.awt.geom.Point2D centre = element.getGraphElementCenter();
            element.setNewCoordinates(new java.awt.geom.Point2D.Double(centre.getX() + dx, centre.getY()));
        }
        getPetriNetsPanel().addNetFragment(copy);

        Rectangle bounds = new Rectangle(frame.getBounds().x + dx, frame.getBounds().y,
                frame.getBounds().width, frame.getBounds().height);
        GraphObjectFrame duplicate = new GraphObjectFrame(
                frame.getName() + " copy", bounds);
        duplicate.setPriority(frame.getPriority());
        duplicate.setTemplate(frame.getTemplate());
        getPetriNetsPanel().addObjectFrame(duplicate);
        getPetriNetsPanel().repaint();
    }

    /**
     * @return a point to lay a new object's net around, to the right of what is already there
     */
    private Point freeSpotForNewObject() {
        int rightmost = 0;
        for (GraphObjectFrame frame : getPetriNetsPanel().getCanvasModel().getFrames()) {
            rightmost = Math.max(rightmost, frame.getBounds().x + frame.getBounds().width);
        }
        return rightmost == 0 ? getCanvasCentre() : new Point(rightmost + 220, 220);
    }

    /**
     * @return a frame that encloses the given elements with room to spare
     */
    private Rectangle boundsAround(java.util.List<? extends ua.stetsenkoinna.graphnet.GraphElement> places,
                                   java.util.List<? extends ua.stetsenkoinna.graphnet.GraphElement> transitions) {
        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;
        java.util.List<ua.stetsenkoinna.graphnet.GraphElement> all = new ArrayList<>();
        all.addAll(places);
        all.addAll(transitions);
        for (ua.stetsenkoinna.graphnet.GraphElement element : all) {
            java.awt.geom.Point2D centre = element.getGraphElementCenter();
            int border = Math.max(element.getBorder(), 20);
            minX = Math.min(minX, (int) centre.getX() - border);
            minY = Math.min(minY, (int) centre.getY() - border);
            maxX = Math.max(maxX, (int) centre.getX() + border);
            maxY = Math.max(maxY, (int) centre.getY() + border);
        }
        int padding = 24;
        return new Rectangle(
                Math.max(0, minX - padding),
                Math.max(0, minY - padding - GraphObjectFrame.HEADER_HEIGHT),
                maxX - minX + padding * 2,
                maxY - minY + padding * 2 + GraphObjectFrame.HEADER_HEIGHT);
    }

    private void renameSelectedObject() {
        GraphObjectFrame frame = requireSelectedFrame();
        if (frame == null) {
            return;
        }
        String name = JOptionPane.showInputDialog(this, "Name of the Petri-object", frame.getName());
        if (name != null && !name.isBlank()) {
            frame.setName(name.trim());
            getPetriNetsPanel().repaint();
        }
    }

    private void changeSelectedObjectPriority() {
        GraphObjectFrame frame = requireSelectedFrame();
        if (frame == null) {
            return;
        }
        String value = JOptionPane.showInputDialog(this,
                "Priority of the Petri-object — the higher it is, the earlier this object acts "
                        + "when several want to act at the same moment",
                frame.getPriority());
        if (value == null) {
            return;
        }
        try {
            frame.setPriority(Integer.parseInt(value.trim()));
            getPetriNetsPanel().repaint();
        } catch (NumberFormatException malformed) {
            MessageHelper.showError(this, "Priority has to be a whole number");
        }
    }

    private void removeSelectedObjectFrame() {
        GraphObjectFrame frame = requireSelectedFrame();
        if (frame == null) {
            return;
        }
        if (MessageHelper.showConfirmation(this,
                "Remove the Petri-object frame '" + frame.getName() + "'? Its net stays on the canvas.")) {
            getPetriNetsPanel().removeObjectFrame(frame);
        }
    }

    private GraphObjectFrame requireSelectedFrame() {
        GraphObjectFrame frame = getPetriNetsPanel().getSelectedFrame();
        if (frame == null) {
            MessageHelper.showError(this, "Click the header of a Petri-object frame first");
        }
        return frame;
    }

    /**
     * Opens the structure window, seeding it with the net on the canvas the first time so the
     * user starts from what they already drew rather than from an empty model.
     */
    /**
     * @return the canvas read as a Petri-object model, or {@code null} when there is nothing
     *         on it; the statistics module uses it to resolve the {@code O<n>.} prefix of a
     *         formula against the object it names
     */
    public GraphPetriObjModel getObjectModel() {
        try {
            return getPetriNetsPanel().getCanvasModel().toObjModel();
        } catch (RuntimeException empty) {
            return null;
        }
    }

    /**
     * @return a point around which a freshly built net should be laid out
     */
    public Point getCanvasCentre() {
        JScrollPane pane = GetPetriNetPanelScrollPane();
        return new Point(pane.getLocation().x + pane.getBounds().width / 2,
                pane.getLocation().y + pane.getBounds().height / 2);
    }

    /**
     * @return the Petri-object model being composed in the structure window, or {@code null}
     *         when that window was never opened; used by the statistics module to resolve
     *         the {@code O<n>.} prefix of a formula
     */
    
    private JButton createPtrnButton(String title, String tooltip) {

        javax.swing.JButton btn = new javax.swing.JButton();
        btn.setFont(new java.awt.Font("Arial", Font.PLAIN, 14)); // NOI18N
        btn.setToolTipText(tooltip);
        btn.setBorder(javax.swing.BorderFactory.createEmptyBorder(1,
                10, 1, 10));
        btn.setFocusable(false);
        btn.setHorizontalTextPosition(javax.swing.SwingConstants.CENTER);
        btn.setVerticalTextPosition(javax.swing.SwingConstants.CENTER);
        btn.setBorder(null);
        btn.setMargin(new Insets(0, 0, 0, 0));
        btn.setContentAreaFilled(false);
        btn.setIcon(new javax.swing.ImageIcon(ResourcePathConfig.getResource(getClass(), ResourcePathConfig.getIconPath(title + ".png"))));

        return btn;
    }

    private void ptrnButtonActionPerformed(java.awt.event.ActionEvent evt, String fileName) {
        ObjectInputStream ois = null;
        try {
            //Load .pns file from resources
            InputStream resourceStream = ResourcePathConfig.getResourceAsStream(getClass(), ResourcePathConfig.getPnsFilePath(fileName));
            if (resourceStream == null) {
                LOGGER.warn("Resource not found: {}", ResourcePathConfig.getPnsFilePath(fileName));
                return;
            }

            ois = new ObjectInputStream(resourceStream);
            GraphPetriNet net = ((GraphPetriNet) ois.readObject()).clone();  //
            getPetriNetsPanel().addGraphNet(net); //
            ois.close();

            getPetriNetsPanel().repaint();

        } catch (FileNotFoundException e) {
            LOGGER.warn("Such file was not found", e);
        } catch (ClassNotFoundException | IOException ex) {
            LOGGER.error("Unexpected error", ex);
        } catch (CloneNotSupportedException ex) {
            LOGGER.error("Unexpected error", ex);
        } finally {
            try {
                if (ois != null) {
                    ois.close();
                }
            } catch (IOException ex) {
                LOGGER.error("Unexpected error", ex);
            }
        }
    }

    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    // <editor-fold defaultstate="collapsed"
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        petriNetDesign = new javax.swing.JPanel();
        modelingParametersPanel = new javax.swing.JPanel();
        netNameLabel = new javax.swing.JLabel();
        netNameTextField = new javax.swing.JTextField();
        timeStartLabel = new javax.swing.JLabel();
        timeStartField = new javax.swing.JTextField();
        timeModelingLabel = new javax.swing.JLabel();
        timeModelingTextField = new javax.swing.JTextField();
        speedLabel = new javax.swing.JLabel();
        speedSlider = new javax.swing.JSlider();
        playPauseAnimationButton = new javax.swing.JButton();
        stopAnimationButton = new javax.swing.JButton();
        skipBackwardAnimationButton = new javax.swing.JButton();
        skipForwardAnimationButton = new javax.swing.JButton();
        runOneEventButton = new javax.swing.JButton();
        petriNetsFrameToolBar = new javax.swing.JToolBar();
        newPlaceButton = new javax.swing.JButton();
        newTransitionButton = new javax.swing.JButton();
        newArcButton = new javax.swing.JButton();
        petriNetsFrameSplitPane = new javax.swing.JSplitPane();
        petriNetPanelScrollPane = new javax.swing.JScrollPane();
        modelingResultsPanel = new javax.swing.JPanel();
        modelingResultsSplitPane = new javax.swing.JSplitPane();
        protokolScrollPane = new javax.swing.JScrollPane();
        protocolTextArea = new javax.swing.JTextArea();
        statisticsScrollPane = new javax.swing.JScrollPane();
        statisticsTextArea = new javax.swing.JTextArea();
        leftNenuPanel = new javax.swing.JPanel();
        scrollPane = new javax.swing.JScrollPane();
        leftMenuList = new javax.swing.JList<>();
        petriNetsFrameMenuBar = new javax.swing.JMenuBar();
        fileMenu = new javax.swing.JMenu();
        openMenuItem = new javax.swing.JMenuItem();
        newMenuItem = new javax.swing.JMenuItem();
        openMethodMenuItem = new javax.swing.JMenuItem();
        editMenu = new javax.swing.JMenu();
        editNetParameters = new javax.swing.JMenuItem();
        centerLocationOfGraphNet = new javax.swing.JMenuItem();
        undoMenuItem = new javax.swing.JMenuItem();
        redoMenuItem = new javax.swing.JMenuItem();
        save = new javax.swing.JMenu();
        SaveGraphNet = new javax.swing.JMenuItem();
        jMenuItem2 = new javax.swing.JMenuItem();
        SavePetriNetAs = new javax.swing.JMenuItem();
        SaveNetAsMethod = new javax.swing.JMenuItem();
        SaveMethodInNetLibrary = new javax.swing.JMenuItem();
        statisticMenu = new javax.swing.JMenu();
        openMonitor = new javax.swing.JMenuItem();
        isStatisticMonitorEnabled = new javax.swing.JCheckBoxMenuItem();
        Animate = new javax.swing.JMenu();
        itemAnimateNet = new javax.swing.JMenuItem();
        itemAnimateEvent = new javax.swing.JMenuItem();
        runMenu = new javax.swing.JMenu();
        javax.swing.JMenuItem itemRunNet = new javax.swing.JMenuItem();
        itemRunEvent = new javax.swing.JMenuItem();

        setDefaultCloseOperation(javax.swing.WindowConstants.EXIT_ON_CLOSE);


        netNameLabel.setFont(new java.awt.Font("Arial", Font.PLAIN, 11)); // NOI18N
        netNameLabel.setText("Net name");
        netNameLabel.setMinimumSize(new java.awt.Dimension(0, 0));

        netNameTextField.setFont(new java.awt.Font("Arial", Font.PLAIN, 14)); // NOI18N
        netNameTextField.setHorizontalAlignment(javax.swing.JTextField.CENTER);
        netNameTextField.setText("Untitled");
        netNameTextField.setCaretPosition(1);
        netNameTextField.setMinimumSize(new java.awt.Dimension(0, 0));
        netNameTextField.addActionListener(this::netNameTextFieldActionPerformed);

        timeStartLabel.setBackground(new java.awt.Color(192, 192, 192));
        timeStartLabel.setFont(new java.awt.Font("Arial", Font.PLAIN, 11)); // NOI18N
        timeStartLabel.setText("Time start");

        timeStartField.setFont(new java.awt.Font("Arial", Font.PLAIN, 14)); // NOI18N
        timeStartField.setHorizontalAlignment(javax.swing.JTextField.LEFT);
        timeStartField.setText("0");
        timeStartField.setMinimumSize(new java.awt.Dimension(0, 0));
        timeStartField.addActionListener(this::timeStartFieldActionPerformed);

        timeModelingLabel.setBackground(new java.awt.Color(247, 247, 247));
        timeModelingLabel.setFont(new java.awt.Font("Arial", Font.PLAIN, 11)); // NOI18N
        timeModelingLabel.setText("Time modeling");

        timeModelingTextField.setFont(new java.awt.Font("Arial", Font.PLAIN, 14)); // NOI18N
        timeModelingTextField.setHorizontalAlignment(javax.swing.JTextField.LEFT);
        timeModelingTextField.setText("1000");
        timeModelingTextField.setCaretPosition(1);
        timeModelingTextField.setMinimumSize(new java.awt.Dimension(0, 0));

        speedLabel.setFont(new java.awt.Font("Arial", Font.PLAIN, 11)); // NOI18N
        speedLabel.setText("Animation speed");

        speedSlider.setMaximum(1000);
        speedSlider.setValue(1000);
        speedSlider.setInverted(true);
        speedSlider.addChangeListener(this::speedSliderStateChanged);

        playPauseAnimationButton.setAction(playPauseAction);
        playPauseAnimationButton.setMargin(new java.awt.Insets(14, 25, 14, 25));
        playPauseAnimationButton.setMaximumSize(new java.awt.Dimension(50, 50));
        playPauseAnimationButton.setMinimumSize(new java.awt.Dimension(50, 50));
        playPauseAnimationButton.setPreferredSize(new java.awt.Dimension(50, 50));

        stopAnimationButton.setAction(stopSimulationAction);
        stopAnimationButton.setText("⏹");

        skipBackwardAnimationButton.setAction(rewindAction);
        skipBackwardAnimationButton.setText("⏮");

        skipForwardAnimationButton.setAction(runNetAction);
        skipForwardAnimationButton.setText("⏭");

        runOneEventButton.setAction(runOneEventAction);
        runOneEventButton.setText("⏩");

        javax.swing.GroupLayout modelingParametersPanelLayout = new javax.swing.GroupLayout(modelingParametersPanel);
        modelingParametersPanel.setLayout(modelingParametersPanelLayout);
        modelingParametersPanelLayout.setHorizontalGroup(
            modelingParametersPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(modelingParametersPanelLayout.createSequentialGroup()
                .addContainerGap()
                .addComponent(netNameLabel, javax.swing.GroupLayout.DEFAULT_SIZE, 148, Short.MAX_VALUE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(netNameTextField, javax.swing.GroupLayout.DEFAULT_SIZE, 148, Short.MAX_VALUE)
                .addGap(10, 10, 10)
                .addComponent(timeStartLabel, javax.swing.GroupLayout.DEFAULT_SIZE, 194, Short.MAX_VALUE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(timeStartField, javax.swing.GroupLayout.DEFAULT_SIZE, 148, Short.MAX_VALUE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(timeModelingLabel, javax.swing.GroupLayout.DEFAULT_SIZE, 217, Short.MAX_VALUE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(timeModelingTextField, javax.swing.GroupLayout.DEFAULT_SIZE, 147, Short.MAX_VALUE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(speedLabel, javax.swing.GroupLayout.PREFERRED_SIZE, 92, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(18, 18, 18)
                .addComponent(speedSlider, javax.swing.GroupLayout.PREFERRED_SIZE, 163, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(skipBackwardAnimationButton, javax.swing.GroupLayout.PREFERRED_SIZE, 50, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(playPauseAnimationButton, javax.swing.GroupLayout.PREFERRED_SIZE, 50, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(stopAnimationButton, javax.swing.GroupLayout.PREFERRED_SIZE, 50, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(runOneEventButton, javax.swing.GroupLayout.PREFERRED_SIZE, 50, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(skipForwardAnimationButton, javax.swing.GroupLayout.PREFERRED_SIZE, 50, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addContainerGap())
        );
        modelingParametersPanelLayout.setVerticalGroup(
            modelingParametersPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(modelingParametersPanelLayout.createSequentialGroup()
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addGroup(modelingParametersPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, modelingParametersPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE, false)
                        .addComponent(netNameLabel, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addComponent(timeStartLabel)
                        .addComponent(timeStartField, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addComponent(timeModelingLabel)
                        .addComponent(timeModelingTextField, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addComponent(netNameTextField, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addComponent(speedLabel))
                    .addComponent(speedSlider, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)))
            .addComponent(playPauseAnimationButton, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
            .addComponent(stopAnimationButton, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
            .addComponent(runOneEventButton, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
            .addComponent(skipForwardAnimationButton, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
            .addComponent(skipBackwardAnimationButton, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
        );

        timeStartLabel.getAccessibleContext().setAccessibleName("Time");

        petriNetsFrameToolBar.setBorder(null);
        petriNetsFrameToolBar.setRollover(true);
        petriNetsFrameToolBar.setFont(new java.awt.Font("Arial", Font.PLAIN, 12)); // NOI18N
        petriNetsFrameToolBar.setMargin(new java.awt.Insets(0, 10, 0, 10));
        petriNetsFrameToolBar.setFloatable(false);

        newPlaceButton.setFont(new java.awt.Font("Arial", Font.PLAIN, 14)); // NOI18N
        newPlaceButton.setText("Place");
        newPlaceButton.setToolTipText("");
        newPlaceButton.setBorder(javax.swing.BorderFactory.createEmptyBorder(1, 10, 1, 10));
        newPlaceButton.setFocusable(false);
        newPlaceButton.setHorizontalTextPosition(javax.swing.SwingConstants.CENTER);
        newPlaceButton.setMaximumSize(new java.awt.Dimension(103, 19));
        newPlaceButton.setMinimumSize(new java.awt.Dimension(103, 19));
        newPlaceButton.setPreferredSize(new java.awt.Dimension(101, 19));
        newPlaceButton.setRequestFocusEnabled(false);
        newPlaceButton.setVerticalTextPosition(javax.swing.SwingConstants.BOTTOM);
        newPlaceButton.addActionListener(this::newPlaceButtonActionPerformed);
        petriNetsFrameToolBar.add(newPlaceButton);

        newTransitionButton.setFont(new java.awt.Font("Arial", Font.PLAIN, 14)); // NOI18N
        newTransitionButton.setText("Transition");
        newTransitionButton.setBorder(javax.swing.BorderFactory.createEmptyBorder(1, 10, 1, 10));
        newTransitionButton.setFocusable(false);
        newTransitionButton.setHorizontalTextPosition(javax.swing.SwingConstants.CENTER);
        newTransitionButton.setMaximumSize(new java.awt.Dimension(103, 19));
        newTransitionButton.setMinimumSize(new java.awt.Dimension(103, 19));
        newTransitionButton.setPreferredSize(new java.awt.Dimension(101, 19));
        newTransitionButton.setVerticalTextPosition(javax.swing.SwingConstants.BOTTOM);
        newTransitionButton.addActionListener(this::newTransitionButtonActionPerformed);
        petriNetsFrameToolBar.add(newTransitionButton);

        newArcButton.setFont(new java.awt.Font("Arial", Font.PLAIN, 14)); // NOI18N
        newArcButton.setText("Arc");
        newArcButton.setBorder(javax.swing.BorderFactory.createEmptyBorder(1, 10, 1, 10));
        newArcButton.setFocusable(false);
        newArcButton.setHorizontalTextPosition(javax.swing.SwingConstants.CENTER);
        newArcButton.setMaximumSize(new java.awt.Dimension(103, 19));
        newArcButton.setMinimumSize(new java.awt.Dimension(103, 19));
        newArcButton.setPreferredSize(new java.awt.Dimension(101, 19));
        newArcButton.setVerticalTextPosition(javax.swing.SwingConstants.BOTTOM);
        newArcButton.addActionListener(this::newArcButtonActionPerformed);
        petriNetsFrameToolBar.add(newArcButton);

        petriNetsFrameSplitPane.setBorder(javax.swing.BorderFactory.createLineBorder(new java.awt.Color(0, 0, 0)));
        petriNetsFrameSplitPane.setDividerLocation(650);
        petriNetsFrameSplitPane.setDividerSize(3);
        petriNetsFrameSplitPane.setToolTipText("Результати обчислення статистики");
        petriNetsFrameSplitPane.setAutoscrolls(true);
        petriNetsFrameSplitPane.setMinimumSize(new java.awt.Dimension(405, 202));

        petriNetPanelScrollPane.setBorder(null);
        petriNetPanelScrollPane.setForeground(new java.awt.Color(255, 255, 255));
        petriNetPanelScrollPane.setAutoscrolls(true);
        petriNetPanelScrollPane.setMaximumSize(new java.awt.Dimension(2147483647, 2147483647));
        petriNetPanelScrollPane.setMinimumSize(new java.awt.Dimension(200, 200));
        petriNetPanelScrollPane.setPreferredSize(new java.awt.Dimension(800, 1));
        petriNetPanelScrollPane.setWheelScrollingEnabled(false);
        petriNetsFrameSplitPane.setLeftComponent(petriNetPanelScrollPane);
        petriNetPanelScrollPane.getAccessibleContext().setAccessibleDescription("");

        modelingResultsPanel.setBackground(new java.awt.Color(229, 229, 229));
        modelingResultsPanel.setForeground(new java.awt.Color(255, 255, 255));
        modelingResultsPanel.setAutoscrolls(true);
        modelingResultsPanel.setFont(new java.awt.Font("Tahoma", Font.BOLD, 11)); // NOI18N
        modelingResultsPanel.setMaximumSize(new java.awt.Dimension(2147483647, 2147483647));
        modelingResultsPanel.setRequestFocusEnabled(false);

        modelingResultsSplitPane.setBorder(javax.swing.BorderFactory.createLineBorder(new java.awt.Color(0, 0, 0)));
        modelingResultsSplitPane.setDividerSize(1);
        modelingResultsSplitPane.setOrientation(javax.swing.JSplitPane.VERTICAL_SPLIT);
        modelingResultsSplitPane.setPreferredSize(new java.awt.Dimension(100, 35));

        protokolScrollPane.setBorder(null);
        protokolScrollPane.setAutoscrolls(true);
        protokolScrollPane.setMinimumSize(new java.awt.Dimension(21, 220));

        protocolTextArea.setFont(new java.awt.Font("Tahoma", Font.PLAIN, 10)); // NOI18N
        protocolTextArea.setText("-------------- Events protokol ---------------");
        protocolTextArea.setBorder(javax.swing.BorderFactory.createLineBorder(new java.awt.Color(255, 255, 255)));
        protocolTextArea.setMinimumSize(new java.awt.Dimension(100, 400));
        protocolTextArea.setName(""); // NOI18N
        protokolScrollPane.setViewportView(protocolTextArea);

        modelingResultsSplitPane.setLeftComponent(protokolScrollPane);

        statisticsScrollPane.setBorder(javax.swing.BorderFactory.createLineBorder(new java.awt.Color(0, 0, 0)));

        statisticsTextArea.setFont(new java.awt.Font("Tahoma", Font.PLAIN, 10)); // NOI18N
        statisticsTextArea.setText("--------------- STATISTICS ----------------");
        statisticsTextArea.setBorder(javax.swing.BorderFactory.createLineBorder(new java.awt.Color(255, 255, 255)));
        statisticsTextArea.setName(""); // NOI18N
        statisticsScrollPane.setViewportView(statisticsTextArea);
        statisticsTextArea.getAccessibleContext().setAccessibleName("");

        modelingResultsSplitPane.setRightComponent(statisticsScrollPane);

        javax.swing.GroupLayout modelingResultsPanelLayout = new javax.swing.GroupLayout(modelingResultsPanel);
        modelingResultsPanel.setLayout(modelingResultsPanelLayout);
        modelingResultsPanelLayout.setHorizontalGroup(
            modelingResultsPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(modelingResultsSplitPane, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.DEFAULT_SIZE, 789, Short.MAX_VALUE)
        );
        modelingResultsPanelLayout.setVerticalGroup(
            modelingResultsPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(modelingResultsSplitPane, javax.swing.GroupLayout.DEFAULT_SIZE, 639, Short.MAX_VALUE)
        );

        petriNetsFrameSplitPane.setRightComponent(modelingResultsPanel);

        leftNenuPanel.setAlignmentX(0.0F);
        leftNenuPanel.setAlignmentY(0.0F);
        leftNenuPanel.setPreferredSize(new java.awt.Dimension(757, 592));

        scrollPane.setAutoscrolls(true);

        leftMenuList.setModel(leftMenuListModel);
        leftMenuList.setSelectionMode(javax.swing.ListSelectionModel.SINGLE_SELECTION);
        leftMenuList.setAlignmentX(0.0F);
        leftMenuList.setAlignmentY(0.0F);
        leftMenuList.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                leftMenuListMouseClicked(evt);
            }
        });
        scrollPane.setViewportView(leftMenuList);

        javax.swing.GroupLayout leftNenuPanelLayout = new javax.swing.GroupLayout(leftNenuPanel);
        leftNenuPanel.setLayout(leftNenuPanelLayout);
        leftNenuPanelLayout.setHorizontalGroup(
            leftNenuPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 170, Short.MAX_VALUE)
            .addGroup(leftNenuPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                .addComponent(scrollPane, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.PREFERRED_SIZE, 170, javax.swing.GroupLayout.PREFERRED_SIZE))
        );
        leftNenuPanelLayout.setVerticalGroup(
            leftNenuPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 477, Short.MAX_VALUE)
            .addGroup(leftNenuPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, leftNenuPanelLayout.createSequentialGroup()
                    .addGap(0, 0, 0)
                    .addComponent(scrollPane, javax.swing.GroupLayout.DEFAULT_SIZE, 477, Short.MAX_VALUE)))
        );

        javax.swing.GroupLayout petriNetDesignLayout = new javax.swing.GroupLayout(petriNetDesign);
        petriNetDesign.setLayout(petriNetDesignLayout);
        petriNetDesignLayout.setHorizontalGroup(
            petriNetDesignLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(petriNetsFrameToolBar, javax.swing.GroupLayout.PREFERRED_SIZE, 827, javax.swing.GroupLayout.PREFERRED_SIZE)
            .addComponent(modelingParametersPanel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
            .addGroup(petriNetDesignLayout.createSequentialGroup()
                .addGap(183, 183, 183)
                .addComponent(petriNetsFrameSplitPane, javax.swing.GroupLayout.DEFAULT_SIZE, 1443, Short.MAX_VALUE)
                .addContainerGap())
            .addGroup(petriNetDesignLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                .addGroup(petriNetDesignLayout.createSequentialGroup()
                    .addContainerGap()
                    .addComponent(leftNenuPanel, javax.swing.GroupLayout.PREFERRED_SIZE, 170, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addContainerGap(1456, Short.MAX_VALUE)))
        );
        petriNetDesignLayout.setVerticalGroup(
            petriNetDesignLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(petriNetDesignLayout.createSequentialGroup()
                .addComponent(petriNetsFrameToolBar, javax.swing.GroupLayout.PREFERRED_SIZE, 33, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(petriNetsFrameSplitPane, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(modelingParametersPanel, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
            .addGroup(petriNetDesignLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                .addGroup(petriNetDesignLayout.createSequentialGroup()
                    .addGap(38, 38, 38)
                    .addComponent(leftNenuPanel, javax.swing.GroupLayout.PREFERRED_SIZE, 477, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addContainerGap(229, Short.MAX_VALUE)))
        );

        petriNetsFrameToolBar.getAccessibleContext().setAccessibleName("");
        petriNetsFrameToolBar.getAccessibleContext().setAccessibleDescription("");


        petriNetsFrameMenuBar.setBackground(new java.awt.Color(186, 213, 241));
        petriNetsFrameMenuBar.setForeground(new java.awt.Color(98, 147, 167));

        fileMenu.setText("File");
        fileMenu.setMargin(new java.awt.Insets(0, 10, 0, 10));

        openMenuItem.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_O, java.awt.event.InputEvent.CTRL_DOWN_MASK));
        openMenuItem.setText("Open");
        openMenuItem.addActionListener(this::openMenuItemActionPerformed);
        fileMenu.add(openMenuItem);

        newMenuItem.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_N, java.awt.event.InputEvent.CTRL_DOWN_MASK));
        newMenuItem.setText("New");
        newMenuItem.addActionListener(this::newMenuItemActionPerformed);
        fileMenu.add(newMenuItem);

        openMethodMenuItem.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_M, java.awt.event.InputEvent.CTRL_DOWN_MASK));
        openMethodMenuItem.setText("Open a method file");
        openMethodMenuItem.addActionListener(this::openMethodMenuItemActionPerformed);
        fileMenu.add(openMethodMenuItem);

        // Add separator
        fileMenu.addSeparator();

        // Import PNML menu item
        importPnmlMenuItem = new javax.swing.JMenuItem();
        importPnmlMenuItem.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_I, java.awt.event.InputEvent.CTRL_DOWN_MASK));
        importPnmlMenuItem.setText("Import PNML");
        importPnmlMenuItem.addActionListener(this::importPnmlMenuItemActionPerformed);
        fileMenu.add(importPnmlMenuItem);

        petriNetsFrameMenuBar.add(fileMenu);

        editMenu.setText("Edit");
        editMenu.setMargin(new java.awt.Insets(0, 10, 0, 10));

        editNetParameters.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_E, java.awt.event.InputEvent.CTRL_DOWN_MASK));
        editNetParameters.setText("Edit net parameters");
        editNetParameters.addActionListener(this::editNetParametersActionPerformed);
        editMenu.add(editNetParameters);

        centerLocationOfGraphNet.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_L, java.awt.event.InputEvent.CTRL_DOWN_MASK));
        centerLocationOfGraphNet.setText("Locate net in center");
        centerLocationOfGraphNet.addActionListener(this::centerLocationOfGraphNetActionPerformed);
        editMenu.add(centerLocationOfGraphNet);

        undoMenuItem.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_Z, java.awt.event.InputEvent.CTRL_DOWN_MASK));
        undoMenuItem.setText("Undo");
        undoMenuItem.setEnabled(undoManager.canUndo());
        undoMenuItem.addActionListener(this::undoMenuItemActionPerformed);
        editMenu.add(undoMenuItem);

        redoMenuItem.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_Z, java.awt.event.InputEvent.SHIFT_DOWN_MASK | java.awt.event.InputEvent.CTRL_DOWN_MASK));
        redoMenuItem.setText("Redo");
        redoMenuItem.setEnabled(undoManager.canRedo());
        redoMenuItem.addActionListener(this::redoMenuItemActionPerformed);
        editMenu.add(redoMenuItem);

        petriNetsFrameMenuBar.add(editMenu);

        save.setText("Save");
        save.setMargin(new java.awt.Insets(0, 10, 0, 10));

        SaveGraphNet.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_S, java.awt.event.InputEvent.CTRL_DOWN_MASK));
        SaveGraphNet.setText("Save Graph net");
        SaveGraphNet.addActionListener(this::SaveGraphNetActionPerformed);
        save.add(SaveGraphNet);

        jMenuItem2.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_S, java.awt.event.InputEvent.SHIFT_DOWN_MASK | java.awt.event.InputEvent.CTRL_DOWN_MASK));
        jMenuItem2.setText("Save Graph net as");
        jMenuItem2.addActionListener(this::jMenuItem2ActionPerformed);
        save.add(jMenuItem2);

        SavePetriNetAs.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_P, java.awt.event.InputEvent.CTRL_DOWN_MASK));
        SavePetriNetAs.setText("Save  Petri net as");
        SavePetriNetAs.addActionListener(this::SavePetriNetAsActionPerformed);
        save.add(SavePetriNetAs);

        SaveNetAsMethod.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_M, java.awt.event.InputEvent.CTRL_DOWN_MASK));
        SaveNetAsMethod.setText("Save net as method");
        SaveNetAsMethod.addActionListener(this::SaveNetAsMethodActionPerformed);
        save.add(SaveNetAsMethod);

        SaveMethodInNetLibrary.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_M, java.awt.event.InputEvent.SHIFT_DOWN_MASK | java.awt.event.InputEvent.CTRL_DOWN_MASK));
        SaveMethodInNetLibrary.setText("Save method in NetLibrary");
        SaveMethodInNetLibrary.addActionListener(this::SaveMethodInNetLibraryActionPerformed);
        save.add(SaveMethodInNetLibrary);

        // Add separator
        save.addSeparator();

        // Export PNML menu item
        exportPnmlMenuItem = new javax.swing.JMenuItem();
        exportPnmlMenuItem.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_X, java.awt.event.InputEvent.CTRL_DOWN_MASK));
        exportPnmlMenuItem.setText("Export PNML");
        exportPnmlMenuItem.addActionListener(this::exportPnmlMenuItemActionPerformed);
        save.add(exportPnmlMenuItem);

        petriNetsFrameMenuBar.add(save);

        statisticMenu.setText("Statistic");

        openMonitor.setText("Open monitor");
        openMonitor.setMnemonic(KeyEvent.VK_M);
        openMonitor.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_M, KeyEvent.CTRL_MASK | KeyEvent.ALT_MASK));
        openMonitor.addActionListener(this::openMonitorActionPerformed);

        isStatisticMonitorEnabled.setText("Monitor enabled");
        isStatisticMonitorEnabled.setSelected(true);

        statisticMenu.add(openMonitor);
        statisticMenu.add(isStatisticMonitorEnabled);

        petriNetsFrameMenuBar.add(statisticMenu);

        Animate.setAction(animateEventAction);
        Animate.setText("Animate");
        Animate.setMargin(new java.awt.Insets(0, 10, 0, 10));

        itemAnimateNet.setAction(playPauseAction);
        itemAnimateNet.setText("Animate Petri net");
        Animate.add(itemAnimateNet);

        itemAnimateEvent.setAction(animateEventAction);
        itemAnimateEvent.setText("Animate event");
        Animate.add(itemAnimateEvent);

        petriNetsFrameMenuBar.add(Animate);

        runMenu.setAction(runNetAction);
        runMenu.setText("Run");

        itemRunNet.setAction(runNetAction);
        itemRunNet.setText("run");
        runMenu.add(itemRunNet);

        itemRunEvent.setAction(runOneEventAction);
        itemRunEvent.setText("runEvent");
        itemRunEvent.setToolTipText("");
        runMenu.add(itemRunEvent);

        petriNetsFrameMenuBar.add(runMenu);

        setJMenuBar(petriNetsFrameMenuBar);

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(getContentPane());
        getContentPane().setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(petriNetDesign, javax.swing.GroupLayout.Alignment.TRAILING)
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(petriNetDesign, javax.swing.GroupLayout.Alignment.TRAILING)
        );

        pack();
    }// </editor-fold>//GEN-END:initComponents

    private void newArcButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_newArcButtonActionPerformed
        getPetriNetsPanel().setIsSettingArc(true);
    }//GEN-LAST:event_newArcButtonActionPerformed

    private void newTransitionButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_newTransitionButtonActionPerformed
        GraphPetriTransition pt = new GraphPetriTransition(new PetriT(
                GraphPetriTransition.setSimpleName(), 0.0),
                PetriNetsPanel.getIdElement());
        AddGraphElementEdit edit = new AddGraphElementEdit(getPetriNetsPanel(), pt);
        edit.doFirstTime();
        undoSupport.postEdit(edit);
    }//GEN-LAST:event_newTransitionButtonActionPerformed

    private void newPlaceButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_newPlaceButtonActionPerformed
        GraphPetriPlace pp = new GraphPetriPlace(new PetriP(
                GraphPetriPlace.setSimpleName(), 0),
                PetriNetsPanel.getIdElement());
        AddGraphElementEdit edit = new AddGraphElementEdit(getPetriNetsPanel(), pp); 
        edit.doFirstTime();
        undoSupport.postEdit(edit);
    }//GEN-LAST:event_newPlaceButtonActionPerformed

    private void leftMenuListMouseClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_leftMenuListMouseClicked
        if (evt.getClickCount() == 2) {
            try {
                timeStartField.setText(String.valueOf(0));
                protocolTextArea.setText("---------Events protocol----------");
                statisticsTextArea.setText("---------STATISTICS---------");
                //Move current content in center
                Point center = new Point(
                        petriNetPanelScrollPane.getLocation().x
                        + petriNetPanelScrollPane.getBounds().width / 2,
                        petriNetPanelScrollPane.getLocation().y
                        + petriNetPanelScrollPane.getBounds().height / 2
                );
                getPetriNetsPanel().getGraphNet().changeLocation(center);

                String methodFullName = leftMenuList.getSelectedValue();
                if (methodFullName == null) {
                    return;
                }
                String pnetName = fileUse.openMethod(getPetriNetsPanel(),
                        methodFullName, PetriNetsFrame.this);
                if (pnetName != null) {
                    netNameTextField.setText(pnetName);
                }
            } catch (ExceptionInvalidNetStructure ex) {
                LOGGER.error("Unexpected error", ex);
            }
        }
    }//GEN-LAST:event_leftMenuListMouseClicked

    private void itemResetNetActionPerformed(java.awt.event.ActionEvent evt) {
        GraphPetriNet graphPetriNetBackup = GraphPetriNetBackupHolder.getInstance().get();
        if (graphPetriNetBackup != null) {
            getPetriNetsPanel().setGraphNet(graphPetriNetBackup);

            GraphPetriNetBackupHolder.getInstance()
                    .save(new GraphPetriNet(getPetriNetsPanel().getGraphNet()));

            getPetriNetsPanel().requestFocusInWindow();
            getPetriNetsPanel().redraw();
        }
    }


    private void undoMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_undoMenuItemActionPerformed
        if (undoManager.canUndo()) {
            undoManager.undo();
        }
        undoMenuItem.setEnabled(undoManager.canUndo());
        redoMenuItem.setEnabled(undoManager.canRedo());
    }//GEN-LAST:event_undoMenuItemActionPerformed

    private void redoMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_redoMenuItemActionPerformed
        if (undoManager.canRedo()) {
            undoManager.redo();
        }
        undoMenuItem.setEnabled(undoManager.canUndo());
        redoMenuItem.setEnabled(undoManager.canRedo());
    }//GEN-LAST:event_redoMenuItemActionPerformed

    private void speedSliderStateChanged(javax.swing.event.ChangeEvent evt) {//GEN-FIRST:event_speedSliderStateChanged
        timer.setDelay(speedSlider.getValue() / 3);
    }//GEN-LAST:event_speedSliderStateChanged

    private void timeStartFieldActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_timeStartFieldActionPerformed
    }//GEN-LAST:event_timeStartFieldActionPerformed

    private void netNameTextFieldActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_netNameTextFieldActionPerformed
    }//GEN-LAST:event_netNameTextFieldActionPerformed

    private void openMonitorActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_openMonitorActionPerformed
        if (statisticMonitorDialog == null) {
            statisticMonitorDialog = new StatisticMonitorDialog(this, false);
        }
        statisticMonitorDialog.setSize(600, 600);
        statisticMonitorDialog.setLocationRelativeTo(this);
        statisticMonitorDialog.setVisible(true);
    }//GEN-LAST:event_openMonitorActionPerformed

    private void openMenuItemActionPerformed(java.awt.event.ActionEvent evt) {// GEN-FIRST:event_openMenuItemActionPerformed
        try {
            fileUse.newWorksheet(getPetriNetsPanel());
            timeStartField.setText(String.valueOf(0));

            netNameTextField.setText("Untitled");
            protocolTextArea.setText("---------Events protocol----------");
            statisticsTextArea.setText("---------STATISTICS---------");
            String pnetName = fileUse.openFile(getPetriNetsPanel(), this);
            if (pnetName != null) {
                netNameTextField.setText(pnetName);
            }
        } catch (ExceptionInvalidNetStructure ex) {
            LOGGER.error("Unexpected error", ex);
        }
    }// GEN-LAST:event_openMenuItemActionPerformed

    private void newMenuItemActionPerformed(java.awt.event.ActionEvent evt) {// GEN-FIRST:event_newMenuItemActionPerformed
        fileUse.newWorksheet(getPetriNetsPanel());
        timeStartField.setText(String.valueOf(0));

        netNameTextField.setText("Untitled");
    }// GEN-LAST:event_newMenuItemActionPerformed

    private void SaveNetAsMethodActionPerformed(java.awt.event.ActionEvent evt) {// GEN-FIRST:event_SaveNetAsMethodActionPerformed
        try {
            getPetriNetsPanel().getGraphNet().createPetriNet(
                    netNameTextField.getText()); // added by Inna
            fileUse.saveNetAsMethod(getPetriNetsPanel().getGraphNet(),
                    statisticsTextArea);
        } catch (ExceptionInvalidNetStructure | ExceptionInvalidTimeDelay ex) {
            LOGGER.error("Unexpected error", ex);
        }

    }// GEN-LAST:event_SaveNetAsMethodActionPerformed

    private void SaveGraphNetActionPerformed(java.awt.event.ActionEvent evt) {// GEN-FIRST:event_SaveGraphNetActionPerformed
        GraphPetriNet net = getPetriNetsPanel().getGraphNet();
        if (net != null) {
            try {
                if (!fileUse.saveGraphNet(net, netNameTextField.getText()
                        .trim())) {
                    LOGGER.warn("Graph net was not saved");
                }
            } catch (ExceptionInvalidNetStructure ex) {
                LOGGER.error("Unexpected error", ex);
            }
        }

    }// GEN-LAST:event_SaveGraphNetActionPerformed

    private void SavePetriNetAsActionPerformed(java.awt.event.ActionEvent evt) {// GEN-FIRST:event_SavePetriNetAsActionPerformed
        try {
            fileUse.savePetriNetAs(getPetriNetsPanel(), this);
        } catch (ExceptionInvalidNetStructure | ExceptionInvalidTimeDelay ex) {
            LOGGER.error("Unexpected error", ex);
        }
    }// GEN-LAST:event_SavePetriNetAsActionPerformed

    private void jMenuItem2ActionPerformed(java.awt.event.ActionEvent evt) {// GEN-FIRST:event_jMenuItem2ActionPerformed
        try {
            fileUse.saveGraphNetAs(getPetriNetsPanel(), this);
        } catch (ExceptionInvalidNetStructure | ExceptionInvalidTimeDelay ex) {
            LOGGER.error("Unexpected error", ex);
        }
    }// GEN-LAST:event_jMenuItem2ActionPerformed

    private void SaveMethodInNetLibraryActionPerformed(
            java.awt.event.ActionEvent evt) {// GEN-FIRST:event_SaveMethodInNetLibraryActionPerformed
        if (statisticsTextArea.getText().contains("{")) {
            fileUse.saveMethodInNetLibrary(statisticsTextArea);
            this.UpdateNetLibraryMethodsCombobox();
        }

    }// GEN-LAST:event_SaveMethodInNetLibraryActionPerformed

    private void editNetParametersActionPerformed(java.awt.event.ActionEvent evt) {// GEN-FIRST:event_editNetParametersActionPerformed
        try {
            if (getPetriNetsPanel().getGraphNet() != null) { // adde by Inna 19.02.16
                GraphNetParametersFrame reUseFrame = new GraphNetParametersFrame(
                        this);
                reUseFrame.setVisible(true);
            } else {
                GraphNetParametersFrame reUseFrame = new GraphNetParametersFrame();
                reUseFrame.setVisible(true);
            }
        } catch (ExceptionInvalidNetStructure ex) {
            LOGGER.error("Unexpected error", ex);
        }
    }// GEN-LAST:event_editNetParametersActionPerformed

    private boolean isCorrectNet() throws ExceptionInvalidNetStructure, ExceptionInvalidTimeDelay {
        if (getPetriNetsPanel().getGraphNet() == null) {
            errorFrame.setErrorMessage(" Graph image of Petri Net does not exist yet. Paint it or read it from file.");
            errorFrame.setVisible(true);
            return false;
        }
        if (!getPetriNetsPanel().getGraphNet().isCorrectInArcs()) {
                errorFrame.setErrorMessage(" Transition has no input places.");
                errorFrame.setVisible(true);
                return false;
        }
        if (!getPetriNetsPanel().getGraphNet().isCorrectOutArcs()) {
                    errorFrame.setErrorMessage(" Transition has no output places.");
                    errorFrame.setVisible(true);
                    return false;
        }
        // creating Petri net
        getPetriNetsPanel().getGraphNet().createPetriNet(netNameTextField.getText());
        if (getPetriNetsPanel().getGraphNet().getPetriNet() == null) {
                        errorFrame.setErrorMessage(" Petri Net does not exist yet. Paint it or read it from file. ");
                        errorFrame.setVisible(true);
                        return false;
        }
        if (getPetriNetsPanel().getGraphNet().hasParameters()) {
            // Get the detailed list of unspecified parameters
            ArrayList<String> unspecifiedParams = getPetriNetsPanel().getGraphNet().getPetriNet().getUnspecifiedParameters();

            StringBuilder errorMessage = new StringBuilder();
            errorMessage.append("The Petri Net contains unspecified parameters that must be configured before simulation can begin.\n\n");
            errorMessage.append("Unspecified parameters:\n");

            for (String unspecifiedParam : unspecifiedParams) {
                errorMessage.append("• ").append(unspecifiedParam).append("\n");
            }

            errorMessage.append("\nPlease open the 'Edit Net Parameters' dialog (Ctrl+E) to provide specific values for all parameters, or ensure all transition delays and place markings are properly defined.");

            errorFrame.setErrorMessage(errorMessage.toString());
            errorFrame.setVisible(true);
            return false;
        }
        return true;
    }

    public void runNet() {
        protocolTextArea.setText("---------Events protocol----------");
        protocolTextArea.setText("---------STATISTICS---------");
        try {
            if(isCorrectNet()){
                getPetriNetsPanel().getGraphNet().createPetriNet(netNameTextField.getText());
                RunPetriObjModel m = getRunPetriObjModel();
                m.setSimulationTime(Double.parseDouble(timeModelingTextField.getText()));
                m.setCurrentTime(Double.parseDouble(timeStartField.getText()));
                if (statisticMonitorDialog != null && isStatisticMonitorEnabled.isSelected()) {
                    statisticGraphMonitor = new StatisticGraphMonitor(statisticMonitorDialog, true);
                    m.setStatisticMonitor(statisticGraphMonitor);
                }
                m.go(Double.parseDouble(timeModelingTextField.getText()));
                getPetriNetsPanel().getGraphNet().printStatistics(statisticsTextArea::append);

                getPetriNetsPanel().repaint();

                if (statisticGraphMonitor != null) {
                    try {
                        statisticGraphMonitor.getWorkerStateLatch().await(3, TimeUnit.SECONDS);
                    } catch (InterruptedException e) {
                        LOGGER.warn(e.getMessage(), e);
                        Thread.currentThread().interrupt();
                    }
                }
            }
        } catch (ExceptionInvalidNetStructure | ExceptionInvalidTimeDelay ex) {
            LOGGER.error(ex.getMessage(), ex);
        }
    }

    /**
     * Builds the model the canvas describes: one Petri-object per frame, everything drawn
     * outside every frame as one more, and the arcs that cross frame borders as links. A
     * canvas without frames therefore still runs, as a model of one object.
     */
    private RunPetriObjModel getRunPetriObjModel()
            throws ExceptionInvalidNetStructure, ExceptionInvalidTimeDelay {
        GraphPetriObjModel objModel = getPetriNetsPanel().getCanvasModel().toObjModel();

        ArrayList<PetriSim> list = new ArrayList<>();
        for (GraphPetriObject object : objModel.getObjects()) {
            PetriSim petriSim = GraphPetriObjModel.createPetriSim(object);
            petriSim.setSimulationTime(Double.parseDouble(timeModelingTextField.getText()));
            petriSim.setTimeCurr(Double.parseDouble(timeStartField.getText()));
            list.add(petriSim);
        }

        RunPetriObjModel model = new RunPetriObjModel(list, protocolTextArea);
        for (PetriObjLink link : objModel.getLinks()) {
            model.addLink(link);
        }
        return model;
    }

    public void animateNet() {
        protocolTextArea.setText("---------Events protocol----------");
        protocolTextArea.setText("---------STATISTICS---------");
        try {
            if(isCorrectNet()){
                getPetriNetsPanel().getGraphNet().createPetriNet(netNameTextField.getText());
                AnimRunPetriObjModel model = getAnimRunPetriObjModel();

                animationModel = model;

                model.setSimulationTime(Double.parseDouble(timeModelingTextField.getText()));
                model.setCurrentTime(Double.parseDouble(timeStartField.getText()));
                if (statisticMonitorDialog != null && isStatisticMonitorEnabled.isSelected()) {
                    StatisticGraphMonitor statisticGraphMonitor = new StatisticGraphMonitor(statisticMonitorDialog, false);
                    model.setStatisticMonitor(statisticGraphMonitor);
                }
                model.go(Double.parseDouble(timeModelingTextField.getText()));
                getPetriNetsPanel().getGraphNet().printStatistics(statisticsTextArea::append);

                getPetriNetsPanel().repaint();
            }
        } catch (ExceptionInvalidNetStructure | ExceptionInvalidTimeDelay ex) {
            LOGGER.error(ex.getMessage(), ex);
        }
    }

    /**
     * Builds the animated model of the whole canvas. Every Petri-object animates on the one
     * canvas it is drawn on, so a token crossing a frame border is seen crossing it.
     */
    private AnimRunPetriObjModel getAnimRunPetriObjModel()
            throws ExceptionInvalidNetStructure, ExceptionInvalidTimeDelay {
        GraphPetriObjModel objModel = getPetriNetsPanel().getCanvasModel().toObjModel();

        ArrayList<AnimRunPetriSim> objects = new ArrayList<>();
        StateTime clock = new StateTime();
        for (GraphPetriObject object : objModel.getObjects()) {
            object.getGraphNet().createPetriNet(object.getName());
            AnimRunPetriSim petriSim = new AnimRunPetriSim(
                    object.getGraphNet().getPetriNet(), clock,
                    protocolTextArea, getPetriNetsPanel(), speedSlider, null);
            petriSim.setName(object.getName());
            petriSim.setPriority(object.getPriority());
            petriSim.setSimulationTime(Double.parseDouble(timeModelingTextField.getText()));
            petriSim.setTimeCurr(Double.parseDouble(timeStartField.getText()));
            objects.add(petriSim);
        }

        AnimRunPetriObjModel model = new AnimRunPetriObjModel(objects, protocolTextArea);
        for (AnimRunPetriSim petriSim : objects) {
            petriSim.setParentModel(model);
        }
        for (PetriObjLink link : objModel.getLinks()) {
            model.addLink(link);
        }
        return model;
    }

    public void runEvent() {
        if (getPetriNetsPanel().getGraphNet() == null) {
            errorFrame.setErrorMessage(" Graph image of Petri Net does not exist yet. Paint it or read it from file.");
            errorFrame.setVisible(true);
            return;
        } else {
            try {
                // створення мережі Петрі та запис її в GraphNet
                getPetriNetsPanel().getGraphNet().createPetriNet(netNameTextField.getText());
                if (getPetriNetsPanel().getGraphNet().getPetriNet() == null) {
                    errorFrame.setErrorMessage(" Petri Net does not exist yet. Paint it or read it from file. ");
                    errorFrame.setVisible(true);
                    return;
                } else {
                    PetriSim petriSim = new PetriSim(getPetriNetsPanel().getGraphNet().getPetriNet());
                    petriSim.setSimulationTime(Double.parseDouble(timeModelingTextField.getText()));
                    petriSim.setTimeCurr(Double.parseDouble(timeStartField.getText()));
                    petriSim.printMark();
                    petriSim.step();
                    petriSim.printMark(protocolTextArea::append);
                    getPetriNetsPanel().repaint();
                }
            } catch (ExceptionInvalidNetStructure | ExceptionInvalidTimeDelay ex) {
                LOGGER.error("Unexpected error", ex);
            }
        }
        getPetriNetsPanel().getGraphNet().printStatistics(statisticsTextArea::append);
    }

    void animateEvent() {
        if (getPetriNetsPanel().getGraphNet() == null) {
            errorFrame.setErrorMessage(" Petri Net does not exist yet. Paint it or read it from file.");
            errorFrame.setVisible(true);
            return;
        } else {
            try {
                // створення мережі Петрі та запис її в GraphNet
                getPetriNetsPanel().getGraphNet().createPetriNet(netNameTextField.getText());
                if (getPetriNetsPanel().getGraphNet().getPetriNet() == null) {
                    errorFrame.setErrorMessage(" Petri Net does not exist yet. Paint it or read it from file. ");
                    errorFrame.setVisible(true);
                    return;
                } else {
                    AnimRunPetriSim object = new AnimRunPetriSim(
                            getPetriNetsPanel().getGraphNet().getPetriNet(),
                            protocolTextArea,
                            getPetriNetsPanel(),
                            speedSlider,
                            null
                    );
                    animationPetriObject = object;
                    object.setSimulationTime(Double.parseDouble(timeModelingTextField.getText()));
                    object.setTimeCurr(Double.parseDouble(timeStartField.getText()));

                    object.printMark();
                    object.step();
                    object.printMark(protocolTextArea::append);

                    getPetriNetsPanel().repaint();
                }
            } catch (ExceptionInvalidNetStructure | ExceptionInvalidTimeDelay ex) {
                LOGGER.error("Unexpected error", ex);
            }
        }
        getPetriNetsPanel().getGraphNet().printStatistics(statisticsTextArea::append);
    }

    private void centerLocationOfGraphNetActionPerformed(
            java.awt.event.ActionEvent evt) {// GEN-FIRST:event_centerLocationOfGraphNetActionPerformed
        // added by Inna 21.02.2016
        JPanel panel = this.getPetriNetsPanel();
        JScrollPane pane = petriNetPanelScrollPane;
        LOGGER.debug("{}  {}", pane.getLocation().x, pane.getBounds().width);
        Point center = new Point(pane.getLocation().x + pane.getBounds().width
                / 2, pane.getLocation().y + pane.getBounds().height / 2);
        this.getPetriNetsPanel().getGraphNet().changeLocation(center);

        panel.repaint();
    }// GEN-LAST:event_centerLocationOfGraphNetActionPerformed

    private void openMethodMenuItemActionPerformed(
            java.awt.event.ActionEvent evt) {// GEN-FIRST:event_openMethodMenuItemActionPerformed
        //!Не! очищаємо поле, тепер мережа додається до попередньої
        //fileUse.newWorksheet(petriNetsPanel);
        timeStartField.setText(String.valueOf(0));

        //netNameTextField.setText("Untitled");
        protocolTextArea.setText("---------Events protocol----------");
        statisticsTextArea.setText("---------STATISTICS---------");

        UpdateNetLibraryMethodsCombobox(); // added by Katya 27.11.2016

        if (dialog == null) {
            dialog = new JDialog(this, "Method to open",
                    ModalityType.APPLICATION_MODAL);
            dialog.getContentPane().add(dialogPanel);
            dialog.pack();
            dialog.setLocationRelativeTo(null);
        }
        JFrame that = this;
        dialogPanel.addOkButtonClickHandler((ActionEvent arg) -> { // modified by Katya 05.12.2016 
            try {
                //Move current content in center
                Point center = new Point(
                        petriNetPanelScrollPane.getLocation().x
                        + petriNetPanelScrollPane.getBounds().width / 2,
                        petriNetPanelScrollPane.getLocation().y
                        + petriNetPanelScrollPane.getBounds().height / 2);
                this.getPetriNetsPanel().getGraphNet().changeLocation(center);

                String methodFullName = dialogPanel.getFieldText();
                String pnetName = fileUse.openMethod(getPetriNetsPanel(),
                        methodFullName, that);
                if (pnetName != null) {
                    netNameTextField.setText(pnetName);
                }
            } catch (ExceptionInvalidNetStructure ex) {
                LOGGER.error("Unexpected error", ex);
            }
        });
        dialog.setVisible(true);
    }// GEN-LAST:event_openMethodMenuItemActionPerformed

    /**
     * Opens a PNML document on the canvas.
     *
     * <p>The document may hold a whole Petri-object model — a page per object with the links
     * between them — or a single net, which is a model of one object. Both come back as
     * frames on the canvas with their nets inside.
     */
    private void importPnmlMenuItemActionPerformed(java.awt.event.ActionEvent evt) {
        try {
            java.awt.FileDialog fdlg = new java.awt.FileDialog(this, "Import PNML File", java.awt.FileDialog.LOAD);
            fdlg.setFile("*.pnml");
            fdlg.setVisible(true);
            if (fdlg.getFile() == null) {
                return;
            }
            java.io.File selectedFile = new java.io.File(fdlg.getDirectory() + fdlg.getFile());

            GraphPetriObjModel objModel = new PnmlModelParser().parse(selectedFile);
            GraphCanvasModel canvas = GraphCanvasModel.fromObjModel(objModel);
            getPetriNetsPanel().setCanvasModel(canvas);
            netNameTextField.setText(objModel.getName());

            MessageHelper.showInfo(this,
                    "Imported " + objModel.getObjectCount() + " Petri-object(s) and "
                            + objModel.getLinks().size() + " link(s) from " + selectedFile.getName());
        } catch (Exception ex) {
            LOGGER.error("Failed to import PNML", ex);
            MessageHelper.showException(this, "Error importing PNML file", ex);
        }
    }

    /**
     * Writes the canvas to a PNML document: a page per Petri-object frame, the links between
     * them, and everything drawn outside every frame as one more object.
     */
    private void exportPnmlMenuItemActionPerformed(java.awt.event.ActionEvent evt) {
        try {
            if (getPetriNetsPanel().getGraphNet() == null) {
                MessageHelper.showError(this,
                    "No Petri net to export. Please create or load a net first.");
                return;
            }

            java.awt.FileDialog fdlg = new java.awt.FileDialog(this, "Export to PNML File", java.awt.FileDialog.SAVE);
            fdlg.setFile(netNameTextField.getText() + ".pnml");
            fdlg.setVisible(true);
            if (fdlg.getFile() == null) {
                return;
            }
            java.io.File selectedFile = new java.io.File(fdlg.getDirectory() + fdlg.getFile());
            if (!selectedFile.getName().toLowerCase().endsWith(".pnml")) {
                selectedFile = new java.io.File(selectedFile.getAbsolutePath() + ".pnml");
            }

            GraphCanvasModel canvas = getPetriNetsPanel().getCanvasModel();
            canvas.setName(netNameTextField.getText());
            GraphPetriObjModel objModel = canvas.toObjModel();
            new PnmlModelGenerator().generate(objModel, selectedFile);

            MessageHelper.showInfo(this,
                    "Exported " + objModel.getObjectCount() + " Petri-object(s) and "
                            + objModel.getLinks().size() + " link(s) to "
                            + selectedFile.getAbsolutePath());
        } catch (Exception ex) {
            LOGGER.error("Failed to export PNML", ex);
            MessageHelper.showException(this, "Error exporting PNML file", ex);
        }
    }

    public String getNameNet() {
        return netNameTextField.getText();
    }

    public PetriNetsPanel getPetriNetsPanel() {
        return petriNetsPanel;
    }

    public JScrollPane GetPetriNetPanelScrollPane() {
        return petriNetPanelScrollPane;
    }

    public Integer getNumberOfRuns() {
        int numberOfRuns = 1;
        if (statisticMonitorDialog != null && statisticMonitorDialog.getIsFormulaValid()) {
            numberOfRuns = statisticMonitorDialog.getChartDataCollectionConfig().getNumberOfRuns();
        }
        return numberOfRuns;
    }

    public void disableInput() {
        save.setEnabled(false);
        editMenu.setEnabled(false);
        fileMenu.setEnabled(false);
        Animate.setEnabled(false);
        newArcButton.setEnabled(false);
       /* consistBtn.setEnabled(false);
        poolBtn.setEnabled(false);
        newThreadBtn.setEnabled(false);;
        lockBtn.setEnabled(false);
        guardBtn.setEnabled(false);*/
        newPlaceButton.setEnabled(false);
        newTransitionButton.setEnabled(false);
        protocolTextArea.setEnabled(false);
        statisticsTextArea.setEnabled(false);
        timeModelingTextField.setEnabled(false);
        timeStartField.setEnabled(false);
        netNameTextField.setEnabled(false);
        leftMenuList.setEnabled(false);
        statisticMenu.setEnabled(false);
        if (statisticMonitorDialog != null && isStatisticMonitorEnabled.isSelected()) {
            statisticMonitorDialog.onSimulationStart();
        }
    }

    public void enableInput() {
        save.setEnabled(true);
        editMenu.setEnabled(true);
        fileMenu.setEnabled(true);
        Animate.setEnabled(true);
        newArcButton.setEnabled(true);
     /*   consistBtn.setEnabled(true);
        poolBtn.setEnabled(true);
        newThreadBtn.setEnabled(true);;
        lockBtn.setEnabled(true);
        guardBtn.setEnabled(true);*/
        newPlaceButton.setEnabled(true);
        newTransitionButton.setEnabled(true);
        protocolTextArea.setEnabled(true);
        statisticsTextArea.setEnabled(true);
        timeModelingTextField.setEnabled(true);
        timeStartField.setEnabled(true);
        netNameTextField.setEnabled(true);
        leftMenuList.setEnabled(true);
        statisticMenu.setEnabled(true);
        if (statisticMonitorDialog != null && isStatisticMonitorEnabled.isSelected()) {
            statisticMonitorDialog.onSimulationEnd();
        }
    }

    /**
     * @param args the command line arguments
     *
     * Direct usage is not recommended - use a separated launcher class instead
     */
    public static void sample_main(String[] args) {

        try {
            for (javax.swing.UIManager.LookAndFeelInfo info : javax.swing.UIManager
                    .getInstalledLookAndFeels()) {
                if ("Nimbus".equals(info.getName())) {
                    javax.swing.UIManager.setLookAndFeel(info.getClassName());
                    break;
                }
            }
        } catch (ClassNotFoundException | InstantiationException | IllegalAccessException | javax.swing.UnsupportedLookAndFeelException ex) {
            LOGGER.error("Failed to apply look and feel", ex);
        }
		/* Create and display the form */
        java.awt.EventQueue.invokeLater(new Runnable() {
            @Override
            public void run() {
                new PetriNetsFrame().setVisible(true);
            }
        });

    }

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JMenu Animate;
    private javax.swing.JMenuItem SaveGraphNet;
    private javax.swing.JMenuItem SaveMethodInNetLibrary;
    private javax.swing.JMenuItem SaveNetAsMethod;
    private javax.swing.JMenuItem SavePetriNetAs;
    private javax.swing.JMenuItem centerLocationOfGraphNet;
    private javax.swing.JMenu editMenu;
    private javax.swing.JMenuItem editNetParameters;
    private javax.swing.JMenu fileMenu;
    private javax.swing.JMenuItem itemAnimateEvent;
    private javax.swing.JMenuItem itemAnimateNet;
    private javax.swing.JMenuItem itemRunEvent;
    private javax.swing.JMenuItem jMenuItem2;
    private javax.swing.JList<String> leftMenuList;
    private javax.swing.JPanel leftNenuPanel;
    private javax.swing.JPanel modelingParametersPanel;
    private javax.swing.JPanel modelingResultsPanel;
    private javax.swing.JSplitPane modelingResultsSplitPane;
    private javax.swing.JLabel netNameLabel;
    private javax.swing.JTextField netNameTextField;
    private javax.swing.JButton newArcButton;
    private javax.swing.JMenuItem newMenuItem;
    private javax.swing.JButton newPlaceButton;
    private javax.swing.JButton newTransitionButton;
    private javax.swing.JMenuItem openMenuItem;
    private javax.swing.JMenuItem openMethodMenuItem;
    private javax.swing.JMenuItem openMonitor;
    private javax.swing.JCheckBoxMenuItem isStatisticMonitorEnabled;
    private javax.swing.JPanel petriNetDesign;
    private javax.swing.JScrollPane petriNetPanelScrollPane;
    private javax.swing.JMenuBar petriNetsFrameMenuBar;
    private javax.swing.JSplitPane petriNetsFrameSplitPane;
    private javax.swing.JToolBar petriNetsFrameToolBar;
    private javax.swing.JButton playPauseAnimationButton;
    private javax.swing.JTextArea protocolTextArea;
    private javax.swing.JScrollPane protokolScrollPane;
    private javax.swing.JMenuItem redoMenuItem;
    private javax.swing.JMenu runMenu;
    private javax.swing.JButton runOneEventButton;
    private javax.swing.JMenu save;
    private javax.swing.JScrollPane scrollPane;
    private javax.swing.JButton skipBackwardAnimationButton;
    private javax.swing.JButton skipForwardAnimationButton;
    private javax.swing.JLabel speedLabel;
    private javax.swing.JSlider speedSlider;
    private javax.swing.JMenu statisticMenu;
    private javax.swing.JScrollPane statisticsScrollPane;
    private javax.swing.JTextArea statisticsTextArea;
    private javax.swing.JButton stopAnimationButton;
    private javax.swing.JLabel timeModelingLabel;
    private javax.swing.JTextField timeModelingTextField;
    private javax.swing.JTextField timeStartField;
    private javax.swing.JLabel timeStartLabel;
    private javax.swing.JMenuItem undoMenuItem;
    private javax.swing.JMenuItem importPnmlMenuItem;
    private javax.swing.JMenuItem exportPnmlMenuItem;
    // End of variables declaration//GEN-END:variables
    private static PetriNetsPanel petriNetsPanel;
    private FileUse fileUse = new FileUse();
    private ErrorFrame errorFrame = new ErrorFrame();
    private DefaultListModel<String> leftMenuListModel = new DefaultListModel<>();
    /*private javax.swing.JButton consistBtn;
    private javax.swing.JButton poolBtn;
    private javax.swing.JButton newThreadBtn;
    private javax.swing.JButton lockBtn;
    private javax.swing.JButton guardBtn;*/
    
    private static final UndoManager undoManager = new UndoManager();
    private static final UndoableEditSupport undoSupport = new UndoableEditSupport();
    
    public static UndoableEditSupport getUndoSupport() {
        return undoSupport;
    }
    
    /**
     * A petri-object model that is used for displaying animation
     * and can be paused an unpaused
     */
    public AnimRunPetriObjModel animationModel;
    
    /**
     *  A petri-object that is used for displaying animation
     * and can be paused an unpaused, if there's no parent model
     */
    public AnimRunPetriSim animationPetriObject;
    
    /**
     * The thread on which animation happens. Is stored here so that it
     * can be interrupted if stop button is pressed
     */
    public Thread animationThread;

    private StatisticMonitorDialog statisticMonitorDialog;
    private StatisticGraphMonitor statisticGraphMonitor;
}

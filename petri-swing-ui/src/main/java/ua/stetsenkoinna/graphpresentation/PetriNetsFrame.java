package ua.stetsenkoinna.graphpresentation;

import ua.stetsenkoinna.petriobj.ExceptionInvalidNetStructure;
import ua.stetsenkoinna.petriobj.ExceptionInvalidTimeDelay;
import ua.stetsenkoinna.petriobj.PetriSim;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import ua.stetsenkoinna.graphpresentation.statistic.StatisticMonitorDialog;
import ua.stetsenkoinna.graphpresentation.statistic.dto.data.StatisticGraphMonitor;
import ua.stetsenkoinna.graphreuse.GraphNetParametersFrame;
import ua.stetsenkoinna.config.ResourcePathConfig;
import ua.stetsenkoinna.pnml.CoordinateNormalizer;
import ua.stetsenkoinna.pnml.PnmlParser;
import ua.stetsenkoinna.pnml.PnmlGenerator;
import ua.stetsenkoinna.pnml.PnmlModelGenerator;
import ua.stetsenkoinna.pnml.PnmlModelParser;
import ua.stetsenkoinna.graphnet.GraphCanvasModel;
import ua.stetsenkoinna.petriobj.PetriNet;
import ua.stetsenkoinna.petriobj.ArcIn;
import ua.stetsenkoinna.petriobj.ArcOut;
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
import ua.stetsenkoinna.graphnet.GraphPetriObjModel;
import ua.stetsenkoinna.graphnet.GraphPetriObject;
import ua.stetsenkoinna.petriobj.PetriObjLink;
import ua.stetsenkoinna.petriobj.StateTime;
import ua.stetsenkoinna.graphpresentation.actions.PlayPauseAction;
import ua.stetsenkoinna.graphpresentation.actions.RunNetAction;
import ua.stetsenkoinna.graphpresentation.actions.RunOneEventAction;
import ua.stetsenkoinna.graphpresentation.actions.StepBackAction;
import ua.stetsenkoinna.graphpresentation.actions.StopSimulationAction;
import ua.stetsenkoinna.graphpresentation.objmodel.CanvasTabsBar;
import ua.stetsenkoinna.graphpresentation.objmodel.PetriObjectManagerDialog;
import ua.stetsenkoinna.graphpresentation.objmodel.PetriObjectPalette;
import ua.stetsenkoinna.graphpresentation.objmodel.PetriObjectTemplate;
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
    /** Temporary stand-in for the removed library sidebar — see {@link #openPObjectsLibraryWindow}. */
    private JDialog libraryListDialog;
    private final DefaultListModel<String> libraryListModel = new DefaultListModel<>();

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
    public final StepBackAction stepBackAction = animationControls.stepBackAction;
    public final StopSimulationAction stopSimulationAction = animationControls.stopSimulationAction;
    public final PlayPauseAction playPauseAction = animationControls.playPauseAction;
    public final RunOneEventAction runOneEventAction = animationControls.runOneEventAction;

    /**
     * @return every net-building method {@code NetLibrary} exposes to the UI, as signature
     *         strings ({@code fileUse.openMethod} takes this exact string back to resolve
     *         the method again), sorted for a picker to show — shared by the "Open a method
     *         file" dialog and the PObjects library window.
     */
    private ArrayList<String> collectLibraryMethodNames() {
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
        return workingMethods;
    }

    private void UpdateNetLibraryMethodsCombobox() {
        dialogPanel.setComboOptions(collectLibraryMethodNames());
    }

    /**
     * Loads a net from the library and hands it to the user to position on the current canvas.
     *
     * <p>Nothing is placed automatically any more. This method used to re-centre whatever was
     * already drawn — which dragged every element out of its Petri-object frame, since frames
     * do not move with it — and then let the merge drop the new net wherever a calculation
     * guessed, routinely on top of existing work.
     *
     * @param methodFullName a signature string from {@link #collectLibraryMethodNames()}
     */
    private void loadLibraryMethod(String methodFullName) {
        GraphPetriNet net = buildLibraryNet(methodFullName);
        if (net == null) {
            return;
        }
        getPetriNetsPanel().placeNetInteractively(net);
        // The canvas has to be the thing being looked at for "click where it goes" to make
        // sense; the Nets window is non-modal and would otherwise still be covering it.
        if (libraryListDialog != null && libraryListDialog.isVisible()) {
            libraryListDialog.setVisible(false);
        }
        toFront();
    }

    /**
     * Opens a library net as a new document: the canvas is emptied first, so the net arrives
     * on a clean sheet rather than on top of whatever was already there.
     *
     * @param methodFullName a signature string from {@link #collectLibraryMethodNames()}
     */
    private void openLibraryMethodAsNewNet(String methodFullName) {
        if (!confirmDiscardingCurrentNet()) {
            return;
        }
        GraphPetriNet net = buildLibraryNet(methodFullName);
        if (net == null) {
            return;
        }
        resetWorkspaceForNewDocument();
        // Nothing else is on the canvas now, so there is no placement decision to make — the
        // middle of the view is the only sensible answer and asking would be busywork.
        net.changeLocation(viewportCentreOnCanvas());
        getPetriNetsPanel().addNet(net);
        netNameTextField.setText(netNameOf(net, methodFullName));
    }

    /**
     * @return true when it is safe to throw the current drawing away — either because there is
     *         nothing on it, or because the user said so. The application tracks no "modified"
     *         flag, so "has anything been drawn" is the closest honest approximation; erring
     *         toward asking is the right side to err on when the alternative is silently
     *         discarding someone's work.
     */
    private boolean confirmDiscardingCurrentNet() {
        GraphPetriNet net = getPetriNetsPanel().getGraphNet();
        boolean hasContent = net != null
                && (!net.getGraphPetriPlaceList().isEmpty()
                        || !net.getGraphPetriTransitionList().isEmpty());
        if (!hasContent) {
            return true;
        }
        return MessageHelper.showConfirmation(this,
                "Open this net as a new one? What is on the canvas now will be discarded.");
    }

    /**
     * Empties the editor for a new document. Every load path used to reset a different subset
     * of this, which is how undo ended up able to operate against a net that had already been
     * thrown away.
     */
    private void resetWorkspaceForNewDocument() {
        fileUse.newWorksheet(getPetriNetsPanel());

        // The recorded edits hold a reference to the panel and resolve its net lazily, so
        // edits kept across a reset would apply themselves to the new document.
        undoManager.discardAllEdits();
        undoMenuItem.setEnabled(false);
        redoMenuItem.setEnabled(false);

        animationControls.resetForNewDocument();

        timeStartField.setText(String.valueOf(0));
        protocolTextArea.setText("---------Events protocol----------");
        statisticsTextArea.setText("---------STATISTICS---------");
    }

    private GraphPetriNet buildLibraryNet(String methodFullName) {
        if (methodFullName == null) {
            return null;
        }
        try {
            return fileUse.buildLibraryNet(methodFullName, this, viewportCentreOnCanvas());
        } catch (ExceptionInvalidNetStructure ex) {
            LOGGER.error("Unexpected error", ex);
            return null;
        }
    }

    private static String netNameOf(GraphPetriNet net, String fallback) {
        if (net.getPetriNet() != null && net.getPetriNet().getName() != null) {
            return net.getPetriNet().getName();
        }
        int parenthesis = fallback.indexOf('(');
        return parenthesis < 0 ? fallback : fallback.substring(0, parenthesis);
    }

    /**
     * @return the middle of what the user can currently see, in canvas coordinates — the
     *         scroll position and the zoom both have to be undone, since the viewport reports
     *         its own pixels and the canvas paints through a scale transform
     */
    private Point viewportCentreOnCanvas() {
        java.awt.Rectangle view = petriNetPanelScrollPane.getViewport().getViewRect();
        double scale = getPetriNetsPanel().getScale();
        return new Point(
                (int) ((view.x + view.width / 2) / scale),
                (int) ((view.y + view.height / 2) / scale));
    }

    /**
     * Browses the nets that can be put on the canvas. Currently that means the sample nets the
     * library ships with — double-click one to load it — which is also where sections for
     * recently opened and saved nets will go.
     */
    private void openNetsWindow() {
        if (libraryListDialog == null) {
            libraryListDialog = new JDialog(this, "Nets", false);
            JList<String> list = new JList<>(libraryListModel);
            list.setSelectionMode(javax.swing.ListSelectionModel.SINGLE_SELECTION);
            list.addMouseListener(new java.awt.event.MouseAdapter() {
                @Override
                public void mouseClicked(java.awt.event.MouseEvent evt) {
                    if (evt.getClickCount() == 2) {
                        loadLibraryMethod(list.getSelectedValue());
                    }
                }
            });
            JLabel heading = new JLabel("Sample nets");
            heading.setFont(heading.getFont().deriveFont(Font.BOLD));
            heading.setBorder(javax.swing.BorderFactory.createEmptyBorder(8, 10, 4, 10));

            JPanel content = new JPanel(new java.awt.BorderLayout());
            content.add(heading, java.awt.BorderLayout.NORTH);
            content.add(new javax.swing.JScrollPane(list), java.awt.BorderLayout.CENTER);
            libraryListDialog.setContentPane(content);
            // Wide enough for a full template signature — the parameter list is what tells two
            // similarly-named entries apart, and at 320px it was being cut off.
            libraryListDialog.setSize(560, 520);
            libraryListDialog.setLocationRelativeTo(this);
        }
        libraryListModel.clear();
        for (String name : collectLibraryMethodNames()) {
            libraryListModel.addElement(name);
        }
        libraryListDialog.setVisible(true);
        libraryListDialog.toFront();
    }

    /**
     * Creates new form PetriNetsFrame
     */
    public PetriNetsFrame() {
        initComponents();
        this.UpdateNetLibraryMethodsCombobox();
        timer = new Timer(250, ae -> getPetriNetsPanel().repaint());

        petriNetsPanel = new PetriNetsPanel(netNameTextField);
        petriNetPanelScrollPane.setViewportView(petriNetsPanel);
        buildCanvasTabsBar();

        // Enable drag and drop for both PNML and PNS files
        petriNetsPanel.enableDragAndDrop(this);

        installCanvasToolShortcuts();

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
    }

    /**
     * Keyboard shortcuts for the three "drop an element" tools — A(rc), P(lace), T(ransition)
     * — so switching tools doesn't always require reaching for the mouse. Registered on the
     * canvas itself via WHEN_ANCESTOR_OF_FOCUSED_COMPONENT rather than the whole window, so
     * typing "a"/"p"/"t" into the net name or time fields still types a letter instead of
     * switching tools out from under whatever's being edited. Each binding just replays the
     * matching toolbar button's own click ({@code doClick()}) instead of duplicating what its
     * ActionListener already does, so the toolbar's highlighted button stays in sync for free.
     */
    private void installCanvasToolShortcuts() {
        JComponent canvas = getPetriNetsPanel();
        InputMap inputMap = canvas.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);
        ActionMap actionMap = canvas.getActionMap();

        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_A, 0), "activateArcTool");
        actionMap.put("activateArcTool", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                newArcButton.doClick();
            }
        });

        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_P, 0), "activatePlaceTool");
        actionMap.put("activatePlaceTool", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                newPlaceButton.doClick();
            }
        });

        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_T, 0), "activateTransitionTool");
        actionMap.put("activateTransitionTool", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                newTransitionButton.doClick();
            }
        });

        // Left/Right step the simulation. doClick() rather than calling the action directly so
        // a disabled button stays genuinely inert — the key does exactly what pressing the
        // button would, including doing nothing when that button is currently unavailable.
        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_LEFT, 0), "stepSimulationBack");
        actionMap.put("stepSimulationBack", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                stepBackButton.doClick();
            }
        });

        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_RIGHT, 0), "stepSimulationForward");
        actionMap.put("stepSimulationForward", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                runOneEventButton.doClick();
            }
        });

        // A JScrollPane binds Left/Right to unit-scrolling its viewport by default, which is
        // the canvas sliding sideways under the cursor. Those arrows drive the simulation now,
        // so that default is cleared rather than left to fight the bindings above whenever the
        // scroll pane rather than the canvas holds focus. Up/Down keep scrolling as before.
        InputMap scrollPaneMap =
                petriNetPanelScrollPane.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);
        scrollPaneMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_LEFT, 0), "none");
        scrollPaneMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_RIGHT, 0), "none");
    }

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

    /** Width of the narrow left icon toolbar, in pixels. */
    private static final int TOOLBAR_WIDTH = 40;
    /** Side length of every icon drawn or scaled for the left toolbar, in pixels. */
    private static final int TOOL_ICON_SIZE = 20;
    /** Width of the right sidebar's collapsed strip — just the toggle arrow, in pixels. */
    private static final int SIDEBAR_COLLAPSED_WIDTH = 28;
    /** Side length of a transport button in the top header bar, in pixels. */
    private static final int HEADER_BUTTON_SIZE = 34;
    /** Width permanently reserved beside the left toolbar for its Petri-object scrollbar. */
    private static final int TOOLBAR_SCROLLBAR_WIDTH = 8;

    /** Which Petri-object templates exist and which of them the user keeps on the toolbar. */
    private final PetriObjectPalette petriObjectPalette = new PetriObjectPalette();
    /** The scrolling band of the left toolbar, rebuilt whenever the pinned set changes. */
    private javax.swing.JPanel petriObjectSectionPanel;
    /** Holds every pinned template's button, so the tool buttons and they are mutually
     *  exclusive — arming a template has to visibly disarm Place/Transition/Arc. */
    private final javax.swing.ButtonGroup canvasToolGroup = new javax.swing.ButtonGroup();

    /** True while the events-protocol/statistics sidebar is collapsed to its thin strip. */
    private boolean resultsSidebarCollapsed = true;
    /** The sidebar's width the last time it was expanded, restored the next time it is. */
    private int expandedSidebarWidth = 340;
    /** Splits the canvas from the sidebar; lets the user drag-resize the sidebar's width. */
    private javax.swing.JSplitPane mainSplitPane;

    /** Height of the canvas strip: one row of pills, and never more than that. */
    private static final int CANVAS_TABS_HEIGHT = 30;
    /** Holds the canvas and the strip of open canvases under it. */
    private javax.swing.JPanel canvasArea;
    /** Scrolls the strip sideways when more canvases are open than fit across the window. */
    private javax.swing.JScrollPane canvasTabsScrollPane;
    /** One pill per open canvas, the active one badged and named. */
    private CanvasTabsBar canvasTabsBar;

    /**
     * Puts the strip of open canvases into its scroll pane. Called from the constructor rather than
     * from {@code initComponents}, because the strip reads the canvas panel's canvas stack and its
     * document, and the panel is only created once {@code initComponents} has finished.
     */
    private void buildCanvasTabsBar() {
        canvasTabsBar = new CanvasTabsBar(
                getPetriNetsPanel().getCanvasStack(),
                getPetriNetsPanel().getCanvasModel(),
                frame -> getPetriNetsPanel().openObjectCanvas(frame),
                frame -> getPetriNetsPanel().closeObjectCanvas(frame));
        canvasTabsScrollPane.setViewportView(canvasTabsBar);
    }
    /** True while {@link #sidebarToggleButtonActionPerformed} is itself moving the divider,
     *  so the drag listener that syncs collapsed state back from it does not re-trigger. */
    private boolean sidebarTogglingProgrammatically;

    /**
     * @param iconFileName one of the {@code ResourcePathConfig} icon file name constants
     * @return that icon, scaled to {@link #TOOL_ICON_SIZE} so every toolbar button lines up
     *         regardless of its source image's native resolution
     */
    private Icon scaledIcon(String iconFileName) {
        java.net.URL url = ResourcePathConfig.getResource(getClass(), ResourcePathConfig.getIconPath(iconFileName));
        if (url == null) {
            return null;
        }
        java.awt.Image image = new javax.swing.ImageIcon(url).getImage()
                .getScaledInstance(TOOL_ICON_SIZE, TOOL_ICON_SIZE, java.awt.Image.SCALE_SMOOTH);
        return new javax.swing.ImageIcon(image);
    }

    /**
     * Gives one toolbar button (a tool toggle or an add-element action) the flat, icon-only,
     * hover-tooltip look the left toolbar uses throughout — Photoshop-style, not text buttons.
     */
    private void styleToolButton(AbstractButton button) {
        button.setAlignmentX(Component.CENTER_ALIGNMENT);
        button.setFocusable(false);
        button.setFocusPainted(false);
        button.setBorderPainted(false);
        // A toggle button (a sticky tool) keeps its content area so the selected one stays
        // visibly highlighted; a plain button (Place/Transition/Arc, one-shot actions) stays
        // flat except on hover, matching the rest of the icon-only toolbar.
        button.setContentAreaFilled(button instanceof JToggleButton);
        button.setMargin(new Insets(8, 8, 8, 8));
        button.setMaximumSize(new java.awt.Dimension(TOOLBAR_WIDTH, TOOLBAR_WIDTH));
        button.setPreferredSize(new java.awt.Dimension(TOOLBAR_WIDTH, TOOLBAR_WIDTH));
    }

    /**
     * Gives one simulation transport button (rewind/play/stop/step/run) a compact, uniform
     * size. Unlike {@link #styleToolButton}'s flat look, this keeps the L&amp;F's real button
     * chrome (background + border) turned on: these buttons' enabled state changes constantly
     * while a run is in progress, and a disabled button that looks pixel-identical to an
     * enabled one just reads as unresponsive — hence people mashing the button several times
     * waiting for a click to "take". Also hides any Action's text label: these are strictly
     * icon buttons (the label still surfaces as the tooltip via SHORT_DESCRIPTION).
     */
    private void styleTransportButton(AbstractButton button) {
        button.setFocusable(false);
        button.setFocusPainted(false);
        button.setBorderPainted(true);
        button.setContentAreaFilled(true);
        button.setHideActionText(true);
        button.setAlignmentY(Component.CENTER_ALIGNMENT);
        button.setMargin(new Insets(4, 4, 4, 4));
        button.setMaximumSize(new java.awt.Dimension(HEADER_BUTTON_SIZE, HEADER_BUTTON_SIZE));
        button.setPreferredSize(new java.awt.Dimension(HEADER_BUTTON_SIZE, HEADER_BUTTON_SIZE));
    }

    /**
     * Creates a transport-row button whose tooltip always renders in a fixed spot just below
     * the whole header row, instead of Swing's cursor-relative default. At {@link
     * #HEADER_BUTTON_SIZE} with only 8px between neighbors, that default placement (offset
     * down-and-right from the mouse) can land the tooltip's popup window on top of the very
     * button — or an adjacent one — the user is about to click, and the popup then eats the
     * click instead of it reaching the button underneath.
     */
    private static JButton transportButton() {
        return new JButton() {
            @Override
            public Point getToolTipLocation(MouseEvent event) {
                return new Point(0, getHeight() + 4);
            }
        };
    }

    /**
     * A horizontal rule between two groups of buttons in the left toolbar. Inset from the
     * bar's own width so it reads as a divider rather than a full-width border.
     */
    private static JComponent toolSectionSeparator() {
        JSeparator separator = new JSeparator();
        separator.setMaximumSize(new java.awt.Dimension(TOOLBAR_WIDTH - 10, 4));
        return separator;
    }

    /**
     * Refills the toolbar's Petri-object band from the palette's pinned set. Called both at
     * startup and after the management window changes that set — the buttons are rebuilt from
     * scratch rather than patched, since a template can be added, removed or reordered.
     */
    private void rebuildPetriObjectSection() {
        for (java.awt.Component existing : petriObjectSectionPanel.getComponents()) {
            if (existing instanceof AbstractButton button) {
                // A ButtonGroup holds hard references, so buttons that are no longer on screen
                // have to be taken out of it or they keep the group (and the old template)
                // alive and can still be "selected" from the group's point of view.
                canvasToolGroup.remove(button);
            }
        }
        petriObjectSectionPanel.removeAll();

        // Sits under the pinned band's divider, so without this the first Petri-object button
        // crowds the rule above it while every other section has room to breathe.
        petriObjectSectionPanel.add(javax.swing.Box.createVerticalStrut(6));

        for (PetriObjectTemplate template : petriObjectPalette.pinned()) {
            javax.swing.JToggleButton button = new javax.swing.JToggleButton(
                    CanvasToolIcons.letter(template.glyph(), TOOL_ICON_SIZE));
            button.setToolTipText(template.displayName()
                    + " — click the canvas to drop this Petri-object; stays active for the next one");
            button.addActionListener(evt ->
                    getPetriNetsPanel().setTool(CanvasTool.ADD_PETRI_OBJECT, template));
            styleToolButton(button);
            canvasToolGroup.add(button);
            petriObjectSectionPanel.add(button);
        }

        petriObjectSectionPanel.revalidate();
        petriObjectSectionPanel.repaint();
    }

    /**
     * The Petri-object section's context menu — the only way in to managing the list now that
     * it has no button of its own.
     */
    private void showPetriObjectMenu(java.awt.event.MouseEvent evt) {
        if (!evt.isPopupTrigger()) {
            return;
        }
        javax.swing.JPopupMenu menu = new javax.swing.JPopupMenu();
        javax.swing.JMenuItem manage = new javax.swing.JMenuItem("Petri-objects...");
        manage.setToolTipText("Choose which Petri-objects live on this toolbar");
        manage.addActionListener(e -> openPetriObjectManager());
        menu.add(manage);
        menu.show(evt.getComponent(), evt.getX(), evt.getY());
    }

    /**
     * Opens the window for choosing which Petri-object templates the left toolbar shows.
     */
    private void openPetriObjectManager() {
        PetriObjectManagerDialog manager =
                new PetriObjectManagerDialog(this, petriObjectPalette);
        manager.setVisible(true);
        if (manager.isChanged()) {
            rebuildPetriObjectSection();
        }
    }

    /**
     * A short vertical rule between two sections of the top header bar — floats clear of the
     * row's top/bottom edge rather than spanning its full height, so it reads as a divider
     * between groups of controls instead of a structural border.
     */
    private JComponent headerSeparator() {
        // A fresh vertical JSeparator reports a preferred size of (2, 0) — zero along its own
        // length, since it normally expects a layout that stretches it (e.g. BorderLayout.
        // CENTER) to supply that dimension. BoxLayout does no such stretching on its own, so
        // the height has to be given explicitly or the rule renders as nothing at all.
        JSeparator separator = new JSeparator(SwingConstants.VERTICAL);
        java.awt.Dimension size = new java.awt.Dimension(2, 22);
        separator.setPreferredSize(size);
        separator.setMinimumSize(size);
        separator.setMaximumSize(size);
        separator.setAlignmentY(Component.CENTER_ALIGNMENT);
        return separator;
    }

    /**
     * Adds a small "Open full log" bar above a protocol/statistics scroll pane, which dumps
     * the whole text area's content to a temp file and opens it externally — the panel
     * itself only ever shows however much fits scrolled, and a long run's full log is
     * easier to search and read in a real editor than by scrolling a few hundred pixels of
     * sidebar.
     *
     * <p>This is a wrapping panel rather than {@link JScrollPane#setCorner}: a scroll pane's
     * corner components only ever get screen space when the scrollbar next to them is
     * actually showing, so with the placeholder text this area starts with — short enough
     * that no scrollbar appears — a corner button would never be visible at all.
     *
     * @param scrollPane the protocol or statistics scroll pane to wrap
     * @param textArea that scroll pane's own text area, whose content gets exported
     * @param fileNamePrefix distinguishes the two temp files from each other
     * @return a panel combining the button bar and the scroll pane, ready to hand to the
     *         split pane in the scroll pane's place
     */
    private JComponent withOpenLogButton(JScrollPane scrollPane, JTextArea textArea, String fileNamePrefix) {
        JButton button = new JButton("Open full log");
        button.setFont(new java.awt.Font("Arial", Font.PLAIN, 9));
        button.setFocusable(false);
        button.setMargin(new Insets(1, 4, 1, 4));
        button.setToolTipText("Open the complete log in your text editor");
        button.addActionListener(evt -> openInTextEditor(textArea.getText(), fileNamePrefix));

        JPanel buttonBar = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.RIGHT, 4, 2));
        buttonBar.add(button);

        JPanel wrapper = new JPanel(new java.awt.BorderLayout());
        wrapper.add(buttonBar, java.awt.BorderLayout.NORTH);
        wrapper.add(scrollPane, java.awt.BorderLayout.CENTER);
        return wrapper;
    }

    /**
     * Writes {@code content} to a fresh temp file and asks the desktop to open it — Notepad
     * or whatever else is registered for {@code .txt}, not something this app has to bundle
     * or know how to render itself.
     */
    private void openInTextEditor(String content, String fileNamePrefix) {
        try {
            java.io.File file = java.io.File.createTempFile(fileNamePrefix + "-", ".txt");
            file.deleteOnExit();
            try (java.io.Writer writer = new java.io.OutputStreamWriter(
                    new java.io.FileOutputStream(file), java.nio.charset.StandardCharsets.UTF_8)) {
                writer.write(content);
            }
            java.awt.Desktop.getDesktop().open(file);
        } catch (java.io.IOException | UnsupportedOperationException ex) {
            MessageHelper.showException(this, "Could not open the log in an external editor", ex);
        }
    }

    /**
     * Toggles the right sidebar between its default thin collapsed strip and showing the
     * events protocol / statistics panels in full — the arrow always points the way the
     * sidebar will move when clicked. Expanding restores whatever width the user last left
     * it at, since {@link #mainSplitPane} lets it be dragged to any width while shown.
     */
    private void sidebarToggleButtonActionPerformed(java.awt.event.ActionEvent evt) {
        setSidebarCollapsed(!resultsSidebarCollapsed);
    }

    /** Forces the sidebar open or shut — used by the toggle button, and by Run Net to reveal
     *  the live protocol log for the run it's about to start. */
    public void setSidebarCollapsed(boolean collapsed) {
        resultsSidebarCollapsed = collapsed;
        modelingResultsSplitPane.setVisible(!collapsed);
        sidebarToggleButton.setIcon(CanvasToolIcons.chevron(TOOL_ICON_SIZE, collapsed));
        sidebarToggleButton.setToolTipText(collapsed
                ? "Show events protocol & statistics"
                : "Hide events protocol & statistics");

        sidebarTogglingProgrammatically = true;
        int totalWidth = mainSplitPane.getWidth();
        if (totalWidth > 0) {
            int dividerLocation = collapsed
                    ? totalWidth - SIDEBAR_COLLAPSED_WIDTH - mainSplitPane.getDividerSize()
                    : totalWidth - expandedSidebarWidth - mainSplitPane.getDividerSize();
            mainSplitPane.setDividerLocation(Math.max(0, dividerLocation));
        }
        sidebarTogglingProgrammatically = false;

        modelingResultsPanel.revalidate();
        modelingResultsPanel.repaint();
    }

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
        netNameTextField = new javax.swing.JTextField();
        timeStartLabel = new javax.swing.JLabel();
        timeStartField = new javax.swing.JTextField();
        timeModelingLabel = new javax.swing.JLabel();
        timeModelingTextField = new javax.swing.JTextField();
        speedLabel = new javax.swing.JLabel();
        speedSlider = new javax.swing.JSlider();
        runProgressBar = new javax.swing.JProgressBar();
        playPauseAnimationButton = transportButton();
        stopAnimationButton = transportButton();
        stepBackButton = transportButton();
        skipForwardAnimationButton = transportButton();
        runOneEventButton = transportButton();
        leftIconToolBar = new javax.swing.JPanel();
        selectToolButton = new javax.swing.JToggleButton();
        newPlaceButton = new javax.swing.JToggleButton();
        newTransitionButton = new javax.swing.JToggleButton();
        newArcButton = new javax.swing.JToggleButton();
        petriNetPanelScrollPane = new javax.swing.JScrollPane();
        modelingResultsPanel = new javax.swing.JPanel();
        sidebarToggleButton = new javax.swing.JButton();
        modelingResultsSplitPane = new javax.swing.JSplitPane();
        protokolScrollPane = new javax.swing.JScrollPane();
        protocolTextArea = new javax.swing.JTextArea();
        statisticsScrollPane = new javax.swing.JScrollPane();
        statisticsTextArea = new javax.swing.JTextArea();
        petriNetsFrameMenuBar = new javax.swing.JMenuBar();
        fileMenu = new javax.swing.JMenu();
        openMenuItem = new javax.swing.JMenuItem();
        newMenuItem = new javax.swing.JMenuItem();
        openMethodMenuItem = new javax.swing.JMenuItem();
        pObjectsMenuItem = new javax.swing.JMenuItem();
        netsMenuItem = new javax.swing.JMenuItem();
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

        setDefaultCloseOperation(javax.swing.WindowConstants.EXIT_ON_CLOSE);


        netNameTextField.setFont(new java.awt.Font("Arial", Font.PLAIN, 14)); // NOI18N
        netNameTextField.setHorizontalAlignment(javax.swing.JTextField.CENTER);
        netNameTextField.setText("New PetriNet");
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

        styleTransportButton(playPauseAnimationButton);
        playPauseAnimationButton.setAction(playPauseAction);
        // A custom-painted Icon (not backed by an actual image) has no automatic grayscale
        // "disabled" variant for Swing to fall back on — under Nimbus that renders as nothing
        // at all rather than the plain icon, so every transport button needs its disabled
        // state pointed at an explicit (dimmed) icon.
        playPauseAnimationButton.setDisabledIcon(CanvasToolIcons.dimmed(playPauseAnimationButton.getIcon()));

        stopAnimationButton.setAction(stopSimulationAction);
        stopAnimationButton.setIcon(CanvasToolIcons.stop(TOOL_ICON_SIZE));
        stopAnimationButton.setDisabledIcon(CanvasToolIcons.dimmed(stopAnimationButton.getIcon()));
        styleTransportButton(stopAnimationButton);

        stepBackButton.setAction(stepBackAction);
        stepBackButton.setIcon(CanvasToolIcons.arrowLeft(TOOL_ICON_SIZE));
        stepBackButton.setDisabledIcon(CanvasToolIcons.dimmed(stepBackButton.getIcon()));
        styleTransportButton(stepBackButton);

        skipForwardAnimationButton.setAction(runNetAction);
        skipForwardAnimationButton.setIcon(CanvasToolIcons.fastForward(TOOL_ICON_SIZE));
        skipForwardAnimationButton.setDisabledIcon(CanvasToolIcons.dimmed(skipForwardAnimationButton.getIcon()));
        styleTransportButton(skipForwardAnimationButton);

        runOneEventButton.setAction(runOneEventAction);
        runOneEventButton.setIcon(CanvasToolIcons.arrowRight(TOOL_ICON_SIZE));
        runOneEventButton.setDisabledIcon(CanvasToolIcons.dimmed(runOneEventButton.getIcon()));
        styleTransportButton(runOneEventButton);

        netNameTextField.setPreferredSize(new java.awt.Dimension(150, netNameTextField.getPreferredSize().height));
        netNameTextField.setMaximumSize(new java.awt.Dimension(220, netNameTextField.getPreferredSize().height));

        timeStartLabel.setMaximumSize(new java.awt.Dimension(timeStartLabel.getPreferredSize().width, Short.MAX_VALUE));
        timeStartField.setPreferredSize(new java.awt.Dimension(48, timeStartField.getPreferredSize().height));
        timeStartField.setMaximumSize(new java.awt.Dimension(64, timeStartField.getPreferredSize().height));

        timeModelingLabel.setMaximumSize(new java.awt.Dimension(timeModelingLabel.getPreferredSize().width, Short.MAX_VALUE));
        timeModelingTextField.setPreferredSize(new java.awt.Dimension(60, timeModelingTextField.getPreferredSize().height));
        timeModelingTextField.setMaximumSize(new java.awt.Dimension(80, timeModelingTextField.getPreferredSize().height));

        speedLabel.setMaximumSize(new java.awt.Dimension(speedLabel.getPreferredSize().width, Short.MAX_VALUE));
        speedSlider.setPreferredSize(new java.awt.Dimension(130, speedSlider.getPreferredSize().height));
        speedSlider.setMaximumSize(new java.awt.Dimension(170, speedSlider.getPreferredSize().height));

        for (java.awt.Component field : new java.awt.Component[]{netNameTextField,
                timeStartLabel, timeStartField, timeModelingLabel, timeModelingTextField,
                speedLabel, speedSlider}) {
            ((javax.swing.JComponent) field).setAlignmentY(java.awt.Component.CENTER_ALIGNMENT);
        }

        // Right cluster: every simulation control together — time parameters, playback
        // speed, then the transport buttons media-player style — pinned to the header's far
        // edge regardless of how wide the window is. Net name is document identity, not a
        // simulation parameter, so it stays alone on the opposite side.
        //
        // FlowLayout rather than BoxLayout: each child keeps its own preferred size and the
        // layout just places them left-to-right with a fixed gap, so there is no min/
        // preferred/max resolution or alignment math for a component to get wrong.
        javax.swing.JPanel headerSimulationGroup = new javax.swing.JPanel(
                new java.awt.FlowLayout(java.awt.FlowLayout.RIGHT, 8, 0));
        headerSimulationGroup.setOpaque(false);
        headerSimulationGroup.add(timeStartLabel);
        headerSimulationGroup.add(timeStartField);
        headerSimulationGroup.add(timeModelingLabel);
        headerSimulationGroup.add(timeModelingTextField);
        headerSimulationGroup.add(headerSeparator());
        headerSimulationGroup.add(speedLabel);
        headerSimulationGroup.add(speedSlider);

        // Only Run Net (no animation) shows this — it has no per-event visual feedback of
        // its own the way animation does, so this is the one indication of how far along a
        // run is. Hidden the rest of the time, which under FlowLayout just closes the gap
        // rather than leaving a visible blank slot.
        runProgressBar.setStringPainted(true);
        runProgressBar.setPreferredSize(new java.awt.Dimension(130, HEADER_BUTTON_SIZE));
        runProgressBar.setVisible(false);
        headerSimulationGroup.add(runProgressBar);

        headerSimulationGroup.add(headerSeparator());
        headerSimulationGroup.add(playPauseAnimationButton);
        headerSimulationGroup.add(stopAnimationButton);
        headerSimulationGroup.add(stepBackButton);
        headerSimulationGroup.add(runOneEventButton);
        headerSimulationGroup.add(skipForwardAnimationButton);

        modelingParametersPanel.setLayout(new java.awt.BorderLayout());
        modelingParametersPanel.setBackground(new java.awt.Color(238, 238, 238));
        modelingParametersPanel.setBorder(javax.swing.BorderFactory.createCompoundBorder(
                javax.swing.BorderFactory.createMatteBorder(1, 0, 0, 0, new java.awt.Color(200, 200, 200)),
                javax.swing.BorderFactory.createEmptyBorder(6, 12, 6, 12)));
        modelingParametersPanel.add(netNameTextField, java.awt.BorderLayout.WEST);
        modelingParametersPanel.add(headerSimulationGroup, java.awt.BorderLayout.EAST);

        timeStartLabel.getAccessibleContext().setAccessibleName("Time");

        // Three bands: the drawing tools stay pinned at the top, the Petri-object templates
        // scroll in the middle however many the user pins, and the button that manages them
        // stays pinned at the bottom where it is always reachable. A single BoxLayout column
        // could not do that — everything in it scrolls together or not at all.
        leftIconToolBar.setLayout(new java.awt.BorderLayout());
        leftIconToolBar.setBackground(new java.awt.Color(238, 238, 238));
        leftIconToolBar.setBorder(javax.swing.BorderFactory.createMatteBorder(0, 0, 0, 1, new java.awt.Color(200, 200, 200)));
        leftIconToolBar.setAlignmentX(0.0F);
        // Room for the templates' scrollbar is reserved permanently: letting the toolbar widen
        // and narrow as templates come and go would shove the whole canvas sideways.
        leftIconToolBar.setPreferredSize(
                new java.awt.Dimension(TOOLBAR_WIDTH + TOOLBAR_SCROLLBAR_WIDTH, 0));

        javax.swing.JPanel pinnedToolsPanel = new javax.swing.JPanel();
        pinnedToolsPanel.setLayout(new javax.swing.BoxLayout(pinnedToolsPanel, javax.swing.BoxLayout.Y_AXIS));
        pinnedToolsPanel.setOpaque(false);
        pinnedToolsPanel.add(javax.swing.Box.createVerticalStrut(6));

        selectToolButton.setIcon(CanvasToolIcons.pointer(TOOL_ICON_SIZE));
        selectToolButton.setToolTipText("Select — click to select an element, drag to move it");
        selectToolButton.setSelected(true);
        selectToolButton.addActionListener(evt -> getPetriNetsPanel().setTool(CanvasTool.SELECT));
        styleToolButton(selectToolButton);
        canvasToolGroup.add(selectToolButton);
        pinnedToolsPanel.add(selectToolButton);

        javax.swing.JToggleButton marqueeToolButton = new javax.swing.JToggleButton(CanvasToolIcons.marquee(TOOL_ICON_SIZE));
        marqueeToolButton.setToolTipText("Marquee select — drag a rectangle to select without moving anything");
        marqueeToolButton.addActionListener(evt -> getPetriNetsPanel().setTool(CanvasTool.MARQUEE));
        styleToolButton(marqueeToolButton);
        canvasToolGroup.add(marqueeToolButton);
        pinnedToolsPanel.add(marqueeToolButton);

        javax.swing.JToggleButton panToolButton = new javax.swing.JToggleButton(scaledIcon(ResourcePathConfig.HAND_ICON));
        panToolButton.setToolTipText("Pan — drag to move the canvas view");
        panToolButton.addActionListener(evt -> getPetriNetsPanel().setTool(CanvasTool.PAN));
        styleToolButton(panToolButton);
        canvasToolGroup.add(panToolButton);
        pinnedToolsPanel.add(panToolButton);

        javax.swing.JToggleButton deleteToolButton = new javax.swing.JToggleButton(scaledIcon(ResourcePathConfig.ERASER_ICON));
        deleteToolButton.setToolTipText("Delete — click an element or arc to remove it");
        deleteToolButton.addActionListener(evt -> getPetriNetsPanel().setTool(CanvasTool.DELETE));
        styleToolButton(deleteToolButton);
        canvasToolGroup.add(deleteToolButton);
        pinnedToolsPanel.add(deleteToolButton);

        pinnedToolsPanel.add(javax.swing.Box.createVerticalStrut(8));
        pinnedToolsPanel.add(toolSectionSeparator());
        pinnedToolsPanel.add(javax.swing.Box.createVerticalStrut(8));

        newPlaceButton.setIcon(scaledIcon(ResourcePathConfig.PLACE_ICON));
        newPlaceButton.setToolTipText("Place — click the canvas to drop a place; stays active for the next one");
        newPlaceButton.addActionListener(evt -> getPetriNetsPanel().setTool(CanvasTool.ADD_PLACE));
        styleToolButton(newPlaceButton);
        canvasToolGroup.add(newPlaceButton);
        pinnedToolsPanel.add(newPlaceButton);

        newTransitionButton.setIcon(scaledIcon(ResourcePathConfig.TRANSITION_ICON));
        newTransitionButton.setToolTipText("Transition — click the canvas to drop a transition; stays active for the next one");
        newTransitionButton.addActionListener(evt -> getPetriNetsPanel().setTool(CanvasTool.ADD_TRANSITION));
        styleToolButton(newTransitionButton);
        canvasToolGroup.add(newTransitionButton);
        pinnedToolsPanel.add(newTransitionButton);

        newArcButton.setIcon(scaledIcon(ResourcePathConfig.ARC_ICON));
        newArcButton.setToolTipText("Arc — click a place then a transition (or the reverse) to connect them");
        newArcButton.addActionListener(this::newArcButtonActionPerformed);
        styleToolButton(newArcButton);
        canvasToolGroup.add(newArcButton);
        pinnedToolsPanel.add(newArcButton);

        // The divider belongs to the pinned band, not the scrolling one, so it cannot scroll
        // away and leave the Petri-objects looking like part of the drawing tools.
        pinnedToolsPanel.add(javax.swing.Box.createVerticalStrut(8));
        pinnedToolsPanel.add(toolSectionSeparator());
        pinnedToolsPanel.add(javax.swing.Box.createVerticalStrut(8));

        leftIconToolBar.add(pinnedToolsPanel, java.awt.BorderLayout.NORTH);

        petriObjectSectionPanel = new javax.swing.JPanel();
        petriObjectSectionPanel.setLayout(
                new javax.swing.BoxLayout(petriObjectSectionPanel, javax.swing.BoxLayout.Y_AXIS));
        petriObjectSectionPanel.setOpaque(false);

        javax.swing.JScrollPane petriObjectScrollPane = new javax.swing.JScrollPane(
                petriObjectSectionPanel,
                javax.swing.ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                javax.swing.ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        petriObjectScrollPane.setBorder(null);
        petriObjectScrollPane.setOpaque(false);
        petriObjectScrollPane.getViewport().setOpaque(false);
        petriObjectScrollPane.getVerticalScrollBar().setPreferredSize(
                new java.awt.Dimension(TOOLBAR_SCROLLBAR_WIDTH, 0));
        petriObjectScrollPane.getVerticalScrollBar().setUnitIncrement(TOOLBAR_WIDTH);
        leftIconToolBar.add(petriObjectScrollPane, java.awt.BorderLayout.CENTER);

        // Managing the list is a right-click on the section itself rather than a button of its
        // own: it is a rare action, and a permanent button would cost one of the few slots in
        // a 40px-wide column that the templates themselves want.
        java.awt.event.MouseAdapter managePopup = new java.awt.event.MouseAdapter() {
            @Override
            public void mousePressed(java.awt.event.MouseEvent evt) {
                showPetriObjectMenu(evt);
            }

            @Override
            public void mouseReleased(java.awt.event.MouseEvent evt) {
                // Windows fires the popup trigger on release, X11 on press — both are handled
                // and showPetriObjectMenu ignores whichever one is not the trigger.
                showPetriObjectMenu(evt);
            }
        };
        petriObjectSectionPanel.addMouseListener(managePopup);
        petriObjectScrollPane.addMouseListener(managePopup);
        petriObjectSectionPanel.setToolTipText(
                "Petri-objects — right-click to choose which ones live on this toolbar");

        rebuildPetriObjectSection();

        petriNetPanelScrollPane.setBorder(null);
        petriNetPanelScrollPane.setForeground(new java.awt.Color(255, 255, 255));
        petriNetPanelScrollPane.setAutoscrolls(true);
        petriNetPanelScrollPane.setHorizontalScrollBarPolicy(javax.swing.ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        petriNetPanelScrollPane.setVerticalScrollBarPolicy(javax.swing.ScrollPaneConstants.VERTICAL_SCROLLBAR_NEVER);
        petriNetPanelScrollPane.setMaximumSize(new java.awt.Dimension(2147483647, 2147483647));
        petriNetPanelScrollPane.setMinimumSize(new java.awt.Dimension(200, 200));
        petriNetPanelScrollPane.setWheelScrollingEnabled(false);
        petriNetPanelScrollPane.getAccessibleContext().setAccessibleDescription("");

        sidebarToggleButton.setIcon(CanvasToolIcons.chevron(TOOL_ICON_SIZE, true));
        sidebarToggleButton.setToolTipText("Show events protocol & statistics");
        sidebarToggleButton.setFocusable(false);
        sidebarToggleButton.setFocusPainted(false);
        sidebarToggleButton.setBorderPainted(false);
        sidebarToggleButton.setContentAreaFilled(false);
        sidebarToggleButton.setPreferredSize(new java.awt.Dimension(SIDEBAR_COLLAPSED_WIDTH, 0));
        sidebarToggleButton.addActionListener(this::sidebarToggleButtonActionPerformed);

        modelingResultsSplitPane.setBorder(javax.swing.BorderFactory.createLineBorder(new java.awt.Color(0, 0, 0)));
        modelingResultsSplitPane.setDividerSize(1);
        modelingResultsSplitPane.setOrientation(javax.swing.JSplitPane.VERTICAL_SPLIT);
        modelingResultsSplitPane.setPreferredSize(new java.awt.Dimension(340, 35));
        modelingResultsSplitPane.setVisible(false);

        protokolScrollPane.setBorder(null);
        protokolScrollPane.setAutoscrolls(true);
        protokolScrollPane.setMinimumSize(new java.awt.Dimension(21, 220));

        protocolTextArea.setFont(new java.awt.Font("Tahoma", Font.PLAIN, 10)); // NOI18N
        protocolTextArea.setText("-------------- Events protokol ---------------");
        protocolTextArea.setBorder(javax.swing.BorderFactory.createLineBorder(new java.awt.Color(255, 255, 255)));
        protocolTextArea.setMinimumSize(new java.awt.Dimension(100, 400));
        protocolTextArea.setName(""); // NOI18N
        protokolScrollPane.setViewportView(protocolTextArea);

        modelingResultsSplitPane.setLeftComponent(
                withOpenLogButton(protokolScrollPane, protocolTextArea, "petri-events-protocol"));

        statisticsScrollPane.setBorder(javax.swing.BorderFactory.createLineBorder(new java.awt.Color(0, 0, 0)));

        statisticsTextArea.setFont(new java.awt.Font("Tahoma", Font.PLAIN, 10)); // NOI18N
        statisticsTextArea.setText("--------------- STATISTICS ----------------");
        statisticsTextArea.setBorder(javax.swing.BorderFactory.createLineBorder(new java.awt.Color(255, 255, 255)));
        statisticsTextArea.setName(""); // NOI18N
        statisticsScrollPane.setViewportView(statisticsTextArea);
        statisticsTextArea.getAccessibleContext().setAccessibleName("");

        modelingResultsSplitPane.setRightComponent(
                withOpenLogButton(statisticsScrollPane, statisticsTextArea, "petri-statistics"));

        modelingResultsPanel.setBackground(new java.awt.Color(229, 229, 229));
        modelingResultsPanel.setForeground(new java.awt.Color(255, 255, 255));
        modelingResultsPanel.setLayout(new java.awt.BorderLayout());
        // A hard floor on the sidebar's own width — without it, dragging mainSplitPane's
        // divider could squeeze the sidebar (and its toggle arrow) narrower than the
        // collapsed strip itself, past the point where the arrow is still comfortably visible
        // or clickable.
        modelingResultsPanel.setMinimumSize(new java.awt.Dimension(SIDEBAR_COLLAPSED_WIDTH, 0));
        modelingResultsPanel.add(sidebarToggleButton, java.awt.BorderLayout.WEST);
        modelingResultsPanel.add(modelingResultsSplitPane, java.awt.BorderLayout.CENTER);

        // A real split pane rather than a plain WEST/EAST border region, so the sidebar's
        // width is something the user can drag to change, not just collapsed/expanded.
        mainSplitPane = new javax.swing.JSplitPane(javax.swing.JSplitPane.HORIZONTAL_SPLIT,
                petriNetPanelScrollPane, modelingResultsPanel);
        mainSplitPane.setBorder(null);
        mainSplitPane.setDividerSize(3);
        mainSplitPane.setResizeWeight(1.0);
        mainSplitPane.setContinuousLayout(true);
        // The divider's starting position depends on the split pane's actual width, which is
        // still zero at construction time (the frame is not yet shown) — apply the default
        // collapsed position once real geometry exists, then stop listening.
        mainSplitPane.addComponentListener(new java.awt.event.ComponentAdapter() {
            @Override
            public void componentResized(java.awt.event.ComponentEvent e) {
                if (mainSplitPane.getWidth() > 0) {
                    setSidebarCollapsed(resultsSidebarCollapsed);
                    mainSplitPane.removeComponentListener(this);
                }
            }
        });
        // A manual drag away from the collapsed strip re-expands it, and vice versa — the
        // toggle button's own icon and tooltip stay truthful to what the divider is doing
        // even when the user moved it directly instead of clicking the arrow.
        mainSplitPane.addPropertyChangeListener(javax.swing.JSplitPane.DIVIDER_LOCATION_PROPERTY, evt2 -> {
            if (sidebarTogglingProgrammatically || mainSplitPane.getWidth() <= 0) {
                return;
            }
            int sidebarWidth = mainSplitPane.getWidth() - mainSplitPane.getDividerLocation() - mainSplitPane.getDividerSize();
            boolean nowCollapsed = sidebarWidth <= SIDEBAR_COLLAPSED_WIDTH + 4;
            if (!nowCollapsed) {
                expandedSidebarWidth = sidebarWidth;
            }
            if (nowCollapsed != resultsSidebarCollapsed) {
                resultsSidebarCollapsed = nowCollapsed;
                modelingResultsSplitPane.setVisible(!nowCollapsed);
                sidebarToggleButton.setIcon(CanvasToolIcons.chevron(TOOL_ICON_SIZE, nowCollapsed));
                sidebarToggleButton.setToolTipText(nowCollapsed
                        ? "Show events protocol & statistics"
                        : "Hide events protocol & statistics");
            }
        });

        // The strip of open canvases goes at the bottom of the canvas area, directly under the
        // canvas and its sidebar and above the modeling parameters row. SOUTH of petriNetDesign is
        // already the parameters row, so the canvas and the strip share a panel of their own.
        // Only the container is built here. The strip itself needs the canvas panel, which the
        // constructor does not create until after this method has run to completion, so it is
        // installed by buildCanvasTabsBar() at that point instead.
        canvasTabsScrollPane = new javax.swing.JScrollPane();
        canvasTabsScrollPane.setBorder(null);
        canvasTabsScrollPane.setHorizontalScrollBarPolicy(
                javax.swing.ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        canvasTabsScrollPane.setVerticalScrollBarPolicy(
                javax.swing.ScrollPaneConstants.VERTICAL_SCROLLBAR_NEVER);
        // Bound so eight open canvases scroll the strip horizontally rather than growing it until
        // the window itself has to scroll.
        canvasTabsScrollPane.setPreferredSize(new java.awt.Dimension(0, CANVAS_TABS_HEIGHT));

        canvasArea = new javax.swing.JPanel(new java.awt.BorderLayout());
        canvasArea.add(mainSplitPane, java.awt.BorderLayout.CENTER);
        canvasArea.add(canvasTabsScrollPane, java.awt.BorderLayout.SOUTH);

        petriNetDesign.setLayout(new java.awt.BorderLayout());
        petriNetDesign.add(modelingParametersPanel, java.awt.BorderLayout.SOUTH);
        petriNetDesign.add(leftIconToolBar, java.awt.BorderLayout.WEST);
        petriNetDesign.add(canvasArea, java.awt.BorderLayout.CENTER);


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

        // Two bare JMenuItems rather than menus, since each is a single action: "Nets" browses
        // what can be opened onto the canvas, "PObjects" chooses what the left toolbar offers.
        //
        // A bare JMenuItem's default maximumSize has no real bound the way a JMenu's does
        // (it's normally sized by the popup that contains it, not by a menu bar), so left
        // as-is it stretches to fill whatever space BoxLayout hands the menu bar — capping
        // it to its own preferred size keeps it exactly as wide as its label.
        netsMenuItem.setText("Nets");
        netsMenuItem.addActionListener(evt -> openNetsWindow());
        netsMenuItem.setMaximumSize(netsMenuItem.getPreferredSize());
        petriNetsFrameMenuBar.add(netsMenuItem);

        pObjectsMenuItem.setText("PObjects");
        pObjectsMenuItem.addActionListener(evt -> openPetriObjectManager());
        pObjectsMenuItem.setMaximumSize(pObjectsMenuItem.getPreferredSize());
        petriNetsFrameMenuBar.add(pObjectsMenuItem);

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
        // Arc-drawing is a Select-tool sub-mode (isSettingArc), not its own CanvasTool value,
        // but newArcButton is still a JToggleButton in canvasToolGroup like every other tool
        // button — Swing's own click handling already flips its (and the group's) selection
        // before this listener runs, so this used to force selectToolButton highlighted
        // instead, which visibly stole the arrow's "active" look every time Arc was clicked.
        getPetriNetsPanel().setTool(CanvasTool.SELECT);
        getPetriNetsPanel().setIsSettingArc(true);
    }//GEN-LAST:event_newArcButtonActionPerformed

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
        if (!confirmDiscardingCurrentNet()) {
            return;
        }
        try {
            // Opening a file closes what is open and opens that instead — it is not a way to
            // merge one net into another.
            resetWorkspaceForNewDocument();
            netNameTextField.setText("Untitled");
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
        try {
            getPetriNetsPanel().getGraphNet().getPetriNet().validateStructure();
        } catch (ExceptionInvalidTimeDelay ex) {
            errorFrame.setErrorMessage(" " + ex.getMessage());
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

    /**
     * Resets and reveals the Run Net progress bar, and pops the log sidebar open so the
     * protocol being written is actually visible for the run about to start — otherwise
     * Run Net gives no feedback at all while it has the UI locked, only a wall of text
     * afterward.
     */
    public void showRunProgress() {
        runProgressBar.setValue(0);
        runProgressBar.setVisible(true);
        runProgressBar.revalidate();
        setSidebarCollapsed(false);
    }

    /**
     * @param fraction 0.0-1.0 elapsed simulation time. Always call on the EDT — this is a
     *        plain Swing setter, not itself thread-safe.
     */
    public void updateRunProgress(double fraction) {
        runProgressBar.setValue((int) Math.round(Math.max(0, Math.min(1, fraction)) * 100));
    }

    public void hideRunProgress() {
        runProgressBar.setVisible(false);
        runProgressBar.revalidate();
    }

    public void runNet() {
        protocolTextArea.setText("---------Events protocol----------");
        protocolTextArea.setText("---------STATISTICS---------");
        // A run is a run of the whole model, so it is watched where the whole model is drawn.
        // Without this, pressing Run from inside a Petri-object's own canvas would show a fragment
        // of what is actually running.
        getPetriNetsPanel().activateRootCanvas();
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
                // Reachable from the Stop button (AnimationControls) while this runs on its
                // own thread, and polled here for the progress bar — cleared in the finally
                // below the moment this sub-run is done, win or halted.
                runModel = m;
                m.setProgressListener(fraction ->
                        SwingUtilities.invokeLater(() -> updateRunProgress(fraction)));
                try {
                    m.go(Double.parseDouble(timeModelingTextField.getText()));
                } finally {
                    runModel = null;
                }
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
            errorFrame.setErrorMessage(" " + ex.getMessage());
            errorFrame.setVisible(true);
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
        model.validateStructure();
        return model;
    }

    public void animateNet() {
        protocolTextArea.setText("---------Events protocol----------");
        protocolTextArea.setText("---------STATISTICS---------");
        // See runNet: an animation animates the whole model, so it belongs on the net's canvas.
        getPetriNetsPanel().activateRootCanvas();
        try {
            if(isCorrectNet()){
                getPetriNetsPanel().getGraphNet().createPetriNet(netNameTextField.getText());
                AnimRunPetriObjModel model = getAnimRunPetriObjModel();

                animationModel = model;

                if (startAnimationStepping) {
                    startAnimationStepping = false;
                    model.stepOnce();
                }

                model.setSimulationTime(Double.parseDouble(timeModelingTextField.getText()));
                model.setCurrentTime(Double.parseDouble(timeStartField.getText()));
                if (statisticMonitorDialog != null && isStatisticMonitorEnabled.isSelected()) {
                    StatisticGraphMonitor statisticGraphMonitor = new StatisticGraphMonitor(statisticMonitorDialog, false);
                    model.setStatisticMonitor(statisticGraphMonitor);
                }
                getPetriNetsPanel().clearAnimationHighlight();
                model.go(Double.parseDouble(timeModelingTextField.getText()));
                getPetriNetsPanel().getGraphNet().printStatistics(statisticsTextArea::append);
                getPetriNetsPanel().clearAnimationHighlight();

                getPetriNetsPanel().repaint();
            }
        } catch (ExceptionInvalidNetStructure | ExceptionInvalidTimeDelay ex) {
            LOGGER.error(ex.getMessage(), ex);
            errorFrame.setErrorMessage(" " + ex.getMessage());
            errorFrame.setVisible(true);
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
            // object.getGraphNet() is scoped to just this object's own places and transitions,
            // renumbered from zero independently of every other object's — passing it through
            // is what lets animation matching tell apart two objects that landed on the same
            // local number instead of ever finding both of them at once.
            AnimRunPetriSim petriSim = new AnimRunPetriSim(
                    object.getGraphNet().getPetriNet(), clock,
                    protocolTextArea, getPetriNetsPanel(), speedSlider, null, object.getGraphNet());
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
        model.validateStructure();
        return model;
    }

    private void centerLocationOfGraphNetActionPerformed(
            java.awt.event.ActionEvent evt) {// GEN-FIRST:event_centerLocationOfGraphNetActionPerformed
        // added by Inna 21.02.2016
        JPanel panel = this.getPetriNetsPanel();
        JScrollPane pane = petriNetPanelScrollPane;
        LOGGER.debug("{}  {}", pane.getLocation().x, pane.getBounds().width);
        Point center = new Point(pane.getLocation().x + pane.getBounds().width
                / 2, pane.getLocation().y + pane.getBounds().height / 2);
        // Through the canvas rather than straight at the net: the net alone has no notion of a
        // Petri-object frame, so moving it directly slid every object's net out from under its own
        // frame and left every frame where it was.
        this.getPetriNetsPanel().centreCanvasAt(center);

        panel.repaint();
    }// GEN-LAST:event_centerLocationOfGraphNetActionPerformed

    private void openMethodMenuItemActionPerformed(
            java.awt.event.ActionEvent evt) {// GEN-FIRST:event_openMethodMenuItemActionPerformed
        UpdateNetLibraryMethodsCombobox(); // added by Katya 27.11.2016

        if (dialog == null) {
            dialog = new JDialog(this, "Method to open",
                    ModalityType.APPLICATION_MODAL);
            dialog.getContentPane().add(dialogPanel);
            dialog.pack();
            dialog.setLocationRelativeTo(null);
        }
        // Opening a net from the menu starts a new document, the way opening a file does —
        // as opposed to the Nets window, which adds one to the drawing in progress.
        dialogPanel.addOkButtonClickHandler((ActionEvent arg) ->
                openLibraryMethodAsNewNet(dialogPanel.getFieldText()));
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
        if (!confirmDiscardingCurrentNet()) {
            return;
        }
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
            // Opening a document, so everything the old one left behind goes with it — the
            // undo stack in particular, whose edits would otherwise apply to this new net.
            resetWorkspaceForNewDocument();
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
        leftIconToolBar.setEnabled(false);
        statisticMenu.setEnabled(false);
        if (statisticMonitorDialog != null && isStatisticMonitorEnabled.isSelected()) {
            statisticMonitorDialog.onSimulationStart();
        }
    }

    public void enableInput() {
        save.setEnabled(true);
        editMenu.setEnabled(true);
        fileMenu.setEnabled(true);
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
        leftIconToolBar.setEnabled(true);
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
    private javax.swing.JMenuItem SaveGraphNet;
    private javax.swing.JMenuItem SaveMethodInNetLibrary;
    private javax.swing.JMenuItem SaveNetAsMethod;
    private javax.swing.JMenuItem SavePetriNetAs;
    private javax.swing.JMenuItem centerLocationOfGraphNet;
    private javax.swing.JMenu editMenu;
    private javax.swing.JMenuItem editNetParameters;
    private javax.swing.JMenu fileMenu;
    private javax.swing.JMenuItem jMenuItem2;
    private javax.swing.JPanel leftIconToolBar;
    private javax.swing.JToggleButton selectToolButton;
    private javax.swing.JPanel modelingParametersPanel;
    private javax.swing.JPanel modelingResultsPanel;
    private javax.swing.JButton sidebarToggleButton;
    private javax.swing.JSplitPane modelingResultsSplitPane;
    private javax.swing.JTextField netNameTextField;
    private javax.swing.JToggleButton newArcButton;
    private javax.swing.JMenuItem newMenuItem;
    private javax.swing.JToggleButton newPlaceButton;
    private javax.swing.JToggleButton newTransitionButton;
    private javax.swing.JMenuItem openMenuItem;
    private javax.swing.JMenuItem openMethodMenuItem;
    private javax.swing.JMenuItem pObjectsMenuItem;
    private javax.swing.JMenuItem netsMenuItem;
    private javax.swing.JMenuItem openMonitor;
    private javax.swing.JCheckBoxMenuItem isStatisticMonitorEnabled;
    private javax.swing.JPanel petriNetDesign;
    private javax.swing.JScrollPane petriNetPanelScrollPane;
    private javax.swing.JMenuBar petriNetsFrameMenuBar;
    private javax.swing.JButton playPauseAnimationButton;
    private javax.swing.JTextArea protocolTextArea;
    private javax.swing.JScrollPane protokolScrollPane;
    private javax.swing.JMenuItem redoMenuItem;
    private javax.swing.JProgressBar runProgressBar;
    private javax.swing.JButton runOneEventButton;
    private javax.swing.JMenu save;
    private javax.swing.JButton stepBackButton;
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
     * Set by {@link AnimationControls} just before starting an animation that should advance
     * exactly one event and then pause itself — a "step forward" pressed with nothing running
     * yet. Consumed by {@link #animateNet()} the moment the model exists, since a step can
     * only be armed on a model that has been built but has not started stepping through
     * events. Stepping from a standing start goes through the normal animated run this way
     * rather than a separate single-event path, so it looks exactly like Start-then-Pause —
     * same element highlighting, same timing, same statistics.
     */
    public volatile boolean startAnimationStepping;
    
    /**
     * The thread on which animation happens. Is stored here so that it
     * can be interrupted if stop button is pressed
     */
    public Thread animationThread;

    /**
     * The non-animated "Run net" model currently executing, or {@code null} — stored here,
     * the same way {@link #animationModel} is for animation, so the Stop button can reach it
     * to call {@link RunPetriObjModel#halt()} while it's running.
     */
    public RunPetriObjModel runModel;

    private StatisticMonitorDialog statisticMonitorDialog;
    private StatisticGraphMonitor statisticGraphMonitor;
}

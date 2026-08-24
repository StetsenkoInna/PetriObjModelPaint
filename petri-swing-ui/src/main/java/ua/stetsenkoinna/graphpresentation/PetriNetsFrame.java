package ua.stetsenkoinna.graphpresentation;

import java.awt.*;
import java.awt.Dialog.ModalityType;
import java.awt.event.*;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import javax.swing.*;
import javax.swing.undo.UndoManager;
import javax.swing.undo.UndoableEditSupport;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ua.stetsenkoinna.config.AppSettings;
import ua.stetsenkoinna.config.ResourcePathConfig;
import ua.stetsenkoinna.graphnet.GraphCanvasModel;
import ua.stetsenkoinna.graphnet.GraphPetriNet;
import ua.stetsenkoinna.graphnet.GraphPetriObjModel;
import ua.stetsenkoinna.graphnet.GraphPetriObject;
import ua.stetsenkoinna.graphpresentation.actions.PlayPauseAction;
import ua.stetsenkoinna.graphpresentation.actions.RunNetAction;
import ua.stetsenkoinna.graphpresentation.actions.RunOneEventAction;
import ua.stetsenkoinna.graphpresentation.actions.StepBackAction;
import ua.stetsenkoinna.graphpresentation.actions.StopSimulationAction;
import ua.stetsenkoinna.graphpresentation.input.InputShortcuts;
import ua.stetsenkoinna.graphpresentation.objmodel.CanvasTabsBar;
import ua.stetsenkoinna.graphpresentation.objmodel.PetriObjectManagerDialog;
import ua.stetsenkoinna.graphpresentation.objmodel.PetriObjectPalette;
import ua.stetsenkoinna.graphpresentation.objmodel.PetriObjectTemplate;
import ua.stetsenkoinna.graphpresentation.settings.SettingsDialog;
import ua.stetsenkoinna.graphpresentation.statistic.StatisticMonitorDialog;
import ua.stetsenkoinna.graphpresentation.statistic.dto.data.StatisticGraphMonitor;
import ua.stetsenkoinna.graphpresentation.theme.ThemeManager;
import ua.stetsenkoinna.graphpresentation.theme.ThemedMenuBar;
import ua.stetsenkoinna.graphpresentation.theme.UiPalette;
import ua.stetsenkoinna.graphreuse.GraphNetParametersFrame;
import ua.stetsenkoinna.libnet.HiddenFromUI;
import ua.stetsenkoinna.libnet.NetLibrary;
import ua.stetsenkoinna.petriobj.ExceptionInvalidNetStructure;
import ua.stetsenkoinna.petriobj.ExceptionInvalidTimeDelay;
import ua.stetsenkoinna.petriobj.PetriNet;
import ua.stetsenkoinna.petriobj.PetriObjLink;
import ua.stetsenkoinna.petriobj.PetriSim;
import ua.stetsenkoinna.petriobj.StateTime;
import ua.stetsenkoinna.pnml.PnmlModelGenerator;
import ua.stetsenkoinna.pnml.PnmlModelParser;
import ua.stetsenkoinna.theme.ThemeMode;
import ua.stetsenkoinna.theme.ThemeVariant;
import ua.stetsenkoinna.utils.MessageHelper;

public class PetriNetsFrame extends javax.swing.JFrame {

    private static final Logger LOGGER = LoggerFactory.getLogger(PetriNetsFrame.class);

    public Timer timer; // timer that starts repainting while simulation

    /** The View > Theme radio items, kept so the tick can follow a change made anywhere else. */
    private final java.util.EnumMap<ThemeMode, javax.swing.JRadioButtonMenuItem> themeMenuItems =
            new java.util.EnumMap<>(ThemeMode.class);

    /**
     * Held so it can be unregistered in {@link #dispose()}. {@link ThemeManager} keeps a strong
     * reference to every listener, and a frame that never unregisters is a frame that is never
     * collected - which the test suite, which builds a great many of them, would notice first.
     */
    private ThemeManager.ThemeChangeListener themeListener;

    private final MethodChooserPanel methodChooserPanel = new MethodChooserPanel();
    private JDialog methodChooserDialog;
    /** Stands in for the library sidebar that used to live beside the canvas — see
     *  {@link #openNetsWindow()}. */
    private JDialog libraryListDialog;
    private final DefaultListModel<String> libraryListModel = new DefaultListModel<>();

    /** The contents of the "Method to open" dialog: one drop-down of library method
     *  signatures and the button that accepts the selection. */
    static final class MethodChooserPanel extends JPanel {

        private final JComboBox<String> methodCombo = new JComboBox<>();
        private final JButton confirmButton = new JButton("OK");
        private boolean confirmHandlerAttached;

        MethodChooserPanel() {
            // Dismissal is wired up first, so by the time the caller's handler runs the
            // dialog is already out of the way of whatever it is about to change.
            confirmButton.addActionListener(evt -> closeOwningWindow());
            add(methodCombo);
            add(confirmButton);
        }

        /**
         * Wires up what happens when the selection is accepted. Only the first handler is
         * kept: the panel outlives its dialog, so re-registering on every reopen would stack
         * up copies of the same handler and act on one click several times over.
         */
        void onConfirm(ActionListener handler) {
            if (confirmHandlerAttached) {
                return;
            }
            confirmButton.addActionListener(handler);
            confirmHandlerAttached = true;
        }

        void setMethods(ArrayList<String> methodNames) {
            methodCombo.setModel(new DefaultComboBoxModel<>(methodNames.toArray(String[]::new)));
        }

        String selectedMethod() {
            return Objects.requireNonNull(methodCombo.getSelectedItem()).toString();
        }

        private void closeOwningWindow() {
            Window owner = SwingUtilities.getWindowAncestor(this);
            if (owner != null) {
                owner.dispose();
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

    private void refreshNetLibraryMethods() {
        methodChooserPanel.setMethods(collectLibraryMethodNames());
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

        // A fresh document has no file: plain Save asks where to put it rather than
        // silently overwriting whatever happened to be open before.
        currentPnmlFile = null;

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
     * Builds the editor window and everything that lives in it.
     */
    public PetriNetsFrame() {
        initComponents();
        refreshNetLibraryMethods();
        timer = new Timer(250, ae -> getPetriNetsPanel().repaint());

        petriNetsPanel = new PetriNetsPanel(netNameTextField);
        // The canvas scales the pulse that lights up a firing to whatever speed is chosen, and
        // cannot ask the header for that on its own. Here rather than in initComponents, where
        // the header is built: the canvas does not exist yet at that point.
        petriNetsPanel.setAnimationPace(speedControl);
        petriNetPanelScrollPane.setViewportView(petriNetsPanel);
        buildCanvasTabsBar();

        // Accepts both PNML documents and legacy .pns worksheets dropped onto the canvas.
        petriNetsPanel.enableDragAndDrop(this);

        installCanvasToolShortcuts();
        installMenuAccelerators();
        // The canvas no longer takes focus while painting, so it takes it once when the window
        // opens instead: its shortcuts work on a freshly started editor without a click first.
        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowOpened(java.awt.event.WindowEvent e) {
                petriNetsPanel.requestFocusInWindow();
            }
        });
        applyWindowGeometry();
        installUndoTracking();
        petriNetsFrameMenuBar.add(new HelpMenu(this));

        // Last, so everything it colours exists. addListener calls back straight away, which is
        // what paints this frame in the current theme in the first place - there is no separate
        // "apply the theme once at startup" path to keep in step with this one.
        themeListener = this::applyTheme;
        ThemeManager.addListener(themeListener);
    }

    /**
     * Positions and sizes the window, then opens it maximised. The explicit size still
     * matters: it is what the window falls back to the moment it is un-maximised, so it has
     * to be in place before the maximised state is set.
     */
    private void applyWindowGeometry() {
        setTitle("PetriNetSim");
        setLocation(50, 50);
        setSize(1000, 700);
        setExtendedState(JFrame.MAXIMIZED_BOTH);
    }

    /**
     * Collects every edit the canvas posts and keeps Edit &gt; Undo/Redo in step with what
     * the manager can actually replay.
     */
    private void installUndoTracking() {
        undoSupport.addUndoableEditListener(event -> {
            undoManager.addEdit(event.getEdit());
            refreshUndoRedoMenuState();
        });
    }

    private void refreshUndoRedoMenuState() {
        undoMenuItem.setEnabled(undoManager.canUndo());
        redoMenuItem.setEnabled(undoManager.canRedo());
    }

    @Override
    public void dispose() {
        if (themeListener != null) {
            ThemeManager.removeListener(themeListener);
            themeListener = null;
        }
        super.dispose();
    }

    /**
     * The View menu's theme switch: the three modes as radio items, the chosen one ticked.
     *
     * <p>Duplicates what the settings dialog offers, deliberately. Changing the theme is
     * something a user does while looking at the canvas and deciding it is too bright, and
     * making that a trip through a modal dialog would be making a two-click thing into a
     * four-click one. The two stay in step because both write through {@link ThemeManager} and
     * both are refreshed by {@link #applyTheme}.
     */
    private javax.swing.JMenu buildThemeMenu() {
        themeMenu = new javax.swing.JMenu("Theme");
        javax.swing.ButtonGroup group = new javax.swing.ButtonGroup();
        for (ThemeMode mode : ThemeMode.values()) {
            javax.swing.JRadioButtonMenuItem item =
                    new javax.swing.JRadioButtonMenuItem(mode.getDisplayName());
            item.addActionListener(evt -> ThemeManager.selectMode(mode));
            group.add(item);
            themeMenuItems.put(mode, item);
            themeMenu.add(item);
        }
        return themeMenu;
    }

    /**
     * Re-applies everything about this window that the look and feel cannot reach on its own:
     * the backgrounds and borders set by hand in {@link #initComponents}, the canvas, and the
     * tick in the theme menu.
     *
     * <p>Called by {@link ThemeManager} on every change and once when this frame registers, so
     * it is also the only place these colours are ever set - {@code initComponents} no longer
     * spells any of them out.
     */
    private void applyTheme(ThemeVariant variant, UiPalette palette) {
        petriNetsFrameMenuBar.setBackground(palette.getMenuBarBackground());
        petriNetsFrameMenuBar.setForeground(palette.getMenuBarForeground());

        modelingParametersPanel.setBackground(palette.getChrome());
        modelingParametersPanel.setBorder(javax.swing.BorderFactory.createCompoundBorder(
                javax.swing.BorderFactory.createMatteBorder(1, 0, 0, 0, palette.getDivider()),
                javax.swing.BorderFactory.createEmptyBorder(6, 12, 6, 12)));

        leftIconToolBar.setBackground(palette.getChrome());
        leftIconToolBar.setBorder(
                javax.swing.BorderFactory.createMatteBorder(0, 0, 0, 1, palette.getDivider()));

        modelingResultsPanel.setBackground(palette.getChromeAlt());

        if (petriNetsPanel != null) {
            petriNetsPanel.applyTheme();
        }

        if (timeUnitControl != null) {
            timeUnitControl.applyTheme();
            refreshHorizonReading();
        }

        if (speedControl != null) {
            // Its buttons are painted by hand rather than by the look and feel, so a theme
            // change reaches them only by being told about it.
            speedControl.applyTheme();
        }

        javax.swing.JRadioButtonMenuItem selected = themeMenuItems.get(ThemeManager.currentMode());
        if (selected != null) {
            selected.setSelected(true);
        }

        repaint();
    }

    /**
     * Re-states every menu accelerator in terms of the platform's shortcut modifier.
     *
     * <p>Done here, after {@code initComponents}, rather than by editing the accelerators where
     * the form editor writes them. Two reasons. The generated block belongs to
     * {@code PetriNetsFrame.form}: anyone who opens the Design tab and moves a component gets
     * it rewritten, and a fix made inside it would vanish without trace. And the form editor
     * cannot express "Command on macOS, Control elsewhere" in the first place - it only stores a
     * literal modifier - so the choice has to be made in code wherever it lives.
     *
     * <p>Without this the whole menu reads Control on a Mac, and the Command presses a Mac user
     * actually makes fall through to nothing.
     */
    private void installMenuAccelerators() {
        int menu = InputShortcuts.menuMask();
        int menuShift = InputShortcuts.shiftMenuMask();

        newMenuItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_N, menu));
        importPnmlMenuItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_O, menu));
        savePnmlMenuItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_S, menu));
        exportPnmlMenuItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_S, menuShift));
        undoMenuItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_Z, menu));
        redoMenuItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_Z, menuShift));
        editNetParametersMenuItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_E, menu));
        centerOnNetMenuItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_L, menu));
        // Alt is Option on macOS, which makes this Command+Option+M there and Ctrl+Alt+M
        // elsewhere - the same two-modifier shape on both.
        openMonitor.setAccelerator(
                KeyStroke.getKeyStroke(KeyEvent.VK_M, menu | InputEvent.ALT_DOWN_MASK));
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

        // Ctrl+A selects everything, and switches to the Select tool to do it. Everything the
        // selection is for - dragging it, copying it, deleting it - is a Select-tool gesture,
        // so selecting the whole canvas while some other tool was active picked things out that
        // the very next click would then throw away. Switching through the toolbar button
        // rather than calling setTool keeps the toolbar's own highlight in step, the same way
        // every binding here does; the switch comes first, since setTool keeps a selection only
        // when it is switching to Select.
        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_A, InputShortcuts.menuMask()), "selectAll");
        actionMap.put("selectAll", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                selectToolButton.doClick();
                getPetriNetsPanel().selectAll();
                getPetriNetsPanel().repaint();
            }
        });

        // Undo and redo are bound on the canvas as well as being menu accelerators. The
        // accelerator alone left Ctrl+Z doing nothing at all while the canvas held focus -
        // which is exactly where the user is standing after a drag they want to take back -
        // so the one thing every editor promises about a mistake was unreachable from the
        // keyboard. doClick() rather than calling the undo manager, so a key press does
        // precisely what the menu item does, including being inert when there is nothing to
        // undo. Shortcut+Y is here as well on the platforms that use it - see below.
        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_Z, InputShortcuts.menuMask()), "undoEdit");
        actionMap.put("undoEdit", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                undoMenuItem.doClick();
            }
        });

        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_Z, InputShortcuts.shiftMenuMask()), "redoEdit");
        if (InputShortcuts.bindsRedoToY()) {
            // What a Windows user reaches for to redo. Not bound on macOS, where Redo is
            // Command+Shift+Z and Command+Y is spoken for elsewhere.
            inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_Y, InputShortcuts.menuMask()), "redoEdit");
        }
        actionMap.put("redoEdit", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                redoMenuItem.doClick();
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
            return unloadableIcon(iconFileName, "not on the classpath");
        }
        javax.swing.ImageIcon loaded = new javax.swing.ImageIcon(url);
        // ImageIcon pulls the bytes through a MediaTracker, so by this line the image is either
        // fully decoded or permanently broken, and a broken one reports -1 for both dimensions.
        // Checking is not paranoia: a resource can be present and still undecodable. Release 2.2.2
        // shipped every icon as a 128-byte Git LFS pointer file, because the release workflow
        // checked out without lfs:true - the URL was non-null, so the null guard above passed, and
        // the app came up with five blank toolbar buttons and nothing in the log to say why.
        if (loaded.getIconWidth() <= 0 || loaded.getIconHeight() <= 0) {
            return unloadableIcon(iconFileName, "present but not a decodable image");
        }
        java.awt.Image image = loaded.getImage()
                .getScaledInstance(TOOL_ICON_SIZE, TOOL_ICON_SIZE, java.awt.Image.SCALE_SMOOTH);
        return new javax.swing.ImageIcon(image);
    }

    /**
     * What a toolbar button shows when its icon file cannot be loaded. Whatever it returns, the
     * failure is on the record first: a packaging fault that reaches a user is worth a log line.
     *
     * @param iconFileName the icon that could not be loaded
     * @param reason       why it could not be loaded, for the log
     * @return the icon to put on the button in place of the missing one
     */
    private Icon unloadableIcon(String iconFileName, String reason) {
        LOGGER.warn("Toolbar icon {} could not be loaded ({}) - this build is packaged wrong",
                iconFileName, reason);
        // TODO(policy): decide what the user sees here. Returning null leaves a bare button that
        // is still clickable and still has its tooltip, but gives no hint that anything is wrong.
        return null;
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
     * icon buttons, and they carry no tooltip either - a row of five, sitting eight pixels
     * apart and hovered over constantly during a run, was a row of five popups appearing over
     * whichever button was about to be clicked next.
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
     * Keeps a button's greyed-out icon following its normal one.
     *
     * <p>Two of these buttons swap their icon while the window is up: play becomes pause, and
     * stop becomes reset once a run has finished. Swing copies the new icon across from the
     * action but never touches the disabled one, so a button that had swapped and then went
     * disabled showed the greyed-out form of the icon it used to have.
     *
     * @param button a transport button already bound to its action
     */
    private static void keepDisabledIconInStep(AbstractButton button) {
        button.setDisabledIcon(CanvasToolIcons.dimmed(button.getIcon()));
        button.addPropertyChangeListener("icon", evt ->
                button.setDisabledIcon(CanvasToolIcons.dimmed(button.getIcon())));
    }

    /**
     * Creates a transport-row button. Plain now that these carry no tooltip: it used to place
     * one in a fixed spot below the header, because Swing's cursor-relative default landed the
     * popup on top of the very button about to be clicked.
     */
    private static JButton transportButton() {
        return new JButton();
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
            button.setToolTipText(template.displayName());
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

    /**
     * Builds one of the flat pattern buttons: no chrome of its own, the label centred behind
     * the icon, and the icon taken from the bundled resource named after {@code title}.
     */
    private JButton createPatternButton(String title, String tooltip) {
        JButton button = new JButton();
        button.setFont(new Font("Arial", Font.PLAIN, 14)); // NOI18N
        button.setToolTipText(tooltip);
        button.setFocusable(false);
        button.setHorizontalTextPosition(SwingConstants.CENTER);
        button.setVerticalTextPosition(SwingConstants.CENTER);
        button.setBorder(null);
        button.setMargin(new Insets(0, 0, 0, 0));
        button.setContentAreaFilled(false);
        button.setIcon(new ImageIcon(ResourcePathConfig.getResource(
                getClass(), ResourcePathConfig.getIconPath(title + ".png"))));
        return button;
    }

    /**
     * Reads one of the serialized nets that ship with the application and puts a copy of it
     * on the canvas. A copy rather than the deserialized net itself, so the same pattern can
     * be dropped in as many times as wanted without every copy sharing one set of elements.
     */
    private void addPatternNetToCanvas(String fileName) {
        String resourcePath = ResourcePathConfig.getPnsFilePath(fileName);
        InputStream resourceStream = ResourcePathConfig.getResourceAsStream(getClass(), resourcePath);
        if (resourceStream == null) {
            LOGGER.warn("Resource not found: {}", resourcePath);
            return;
        }
        try (ObjectInputStream input = new ObjectInputStream(resourceStream)) {
            GraphPetriNet net = ((GraphPetriNet) input.readObject()).clone();
            getPetriNetsPanel().addGraphNet(net);
            getPetriNetsPanel().repaint();
        } catch (FileNotFoundException ex) {
            LOGGER.warn("Such file was not found", ex);
        } catch (ClassNotFoundException | CloneNotSupportedException | IOException ex) {
            LOGGER.error("Unexpected error", ex);
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
        timeUnitControl = new TimeUnitControl();
        speedLabel = new javax.swing.JLabel();
        speedControl = new AnimationSpeedControl();
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
        protocolScrollPane = new javax.swing.JScrollPane();
        protocolTextArea = new javax.swing.JTextArea();
        statisticsScrollPane = new javax.swing.JScrollPane();
        statisticsTextArea = new javax.swing.JTextArea();
        petriNetsFrameMenuBar = new ThemedMenuBar();
        fileMenu = new javax.swing.JMenu();
        openMenuItem = new javax.swing.JMenuItem();
        newMenuItem = new javax.swing.JMenuItem();
        openMethodMenuItem = new javax.swing.JMenuItem();
        pObjectsMenuItem = new javax.swing.JMenuItem();
        netsMenuItem = new javax.swing.JMenuItem();
        editMenu = new javax.swing.JMenu();
        editNetParametersMenuItem = new javax.swing.JMenuItem();
        centerOnNetMenuItem = new javax.swing.JMenuItem();
        undoMenuItem = new javax.swing.JMenuItem();
        redoMenuItem = new javax.swing.JMenuItem();
        saveGraphNetMenuItem = new javax.swing.JMenuItem();
        saveGraphNetAsMenuItem = new javax.swing.JMenuItem();
        savePetriNetAsMenuItem = new javax.swing.JMenuItem();
        saveNetAsMethodMenuItem = new javax.swing.JMenuItem();
        saveMethodInLibraryMenuItem = new javax.swing.JMenuItem();
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

        // What the horizon beside it, and every delay in the net, are counted in. It changes no
        // number the simulator sees - see TimeUnitScale - so it sits next to the number it
        // explains rather than anywhere a setting would, and its whole visible effect is the
        // reading after it.
        horizonReadingLabel.setFont(new java.awt.Font("Arial", Font.PLAIN, 11)); // NOI18N
        timeUnitControl.addChangeListener(scale -> refreshHorizonReading());
        timeModelingTextField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            @Override
            public void insertUpdate(javax.swing.event.DocumentEvent e) {
                refreshHorizonReading();
            }

            @Override
            public void removeUpdate(javax.swing.event.DocumentEvent e) {
                refreshHorizonReading();
            }

            @Override
            public void changedUpdate(javax.swing.event.DocumentEvent e) {
                refreshHorizonReading();
            }
        });

        speedLabel.setFont(new java.awt.Font("Arial", Font.PLAIN, 11)); // NOI18N
        speedLabel.setText("Animation speed");

        // The canvas repaint interval follows the chosen pace, the way it followed the
        // slider: a fast animation repainting four times a second is a slideshow, and a slow
        // one repainting sixty times a second is that much work for nothing having changed.
        speedControl.addChangeListener(() -> timer.setDelay(speedControl.repaintIntervalMillis()));

        styleTransportButton(playPauseAnimationButton);
        playPauseAnimationButton.setAction(playPauseAction);
        // A custom-painted Icon (not backed by an actual image) has no automatic grayscale
        // "disabled" variant for Swing to fall back on — under Nimbus that renders as nothing
        // at all rather than the plain icon, so every transport button needs its disabled
        // state pointed at an explicit (dimmed) icon.
        keepDisabledIconInStep(playPauseAnimationButton);
        keepDisabledIconInStep(stopAnimationButton);

        stopAnimationButton.setAction(stopSimulationAction);
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

        for (java.awt.Component field : new java.awt.Component[]{netNameTextField,
                timeStartLabel, timeStartField, timeModelingLabel, timeModelingTextField,
                timeUnitControl, horizonReadingLabel, speedLabel, speedControl}) {
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
        headerSimulationGroup.add(timeUnitControl);
        headerSimulationGroup.add(horizonReadingLabel);
        headerSimulationGroup.add(headerSeparator());
        headerSimulationGroup.add(speedLabel);
        headerSimulationGroup.add(speedControl);
        // Last in the row, so it reads as being about the whole row rather than about the one
        // control it happens to stand beside.
        headerSimulationGroup.add(SimulationTimeHelp.button(this));

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
        // Background and border come from applyTheme, which runs before this frame is shown.
        modelingParametersPanel.add(netNameTextField, java.awt.BorderLayout.WEST);
        modelingParametersPanel.add(headerSimulationGroup, java.awt.BorderLayout.EAST);

        timeStartLabel.getAccessibleContext().setAccessibleName("Time");

        // Three bands: the drawing tools stay pinned at the top, the Petri-object templates
        // scroll in the middle however many the user pins, and the button that manages them
        // stays pinned at the bottom where it is always reachable. A single BoxLayout column
        // could not do that — everything in it scrolls together or not at all.
        leftIconToolBar.setLayout(new java.awt.BorderLayout());
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
        selectToolButton.setToolTipText("Select");
        selectToolButton.setSelected(true);
        selectToolButton.addActionListener(evt -> getPetriNetsPanel().setTool(CanvasTool.SELECT));
        styleToolButton(selectToolButton);
        canvasToolGroup.add(selectToolButton);
        pinnedToolsPanel.add(selectToolButton);

        javax.swing.JToggleButton marqueeToolButton = new javax.swing.JToggleButton(CanvasToolIcons.marquee(TOOL_ICON_SIZE));
        marqueeToolButton.setToolTipText("Marquee select");
        marqueeToolButton.addActionListener(evt -> getPetriNetsPanel().setTool(CanvasTool.MARQUEE));
        styleToolButton(marqueeToolButton);
        canvasToolGroup.add(marqueeToolButton);
        pinnedToolsPanel.add(marqueeToolButton);

        javax.swing.JToggleButton panToolButton = new javax.swing.JToggleButton(scaledIcon(ResourcePathConfig.HAND_ICON));
        panToolButton.setToolTipText("Pan");
        panToolButton.addActionListener(evt -> getPetriNetsPanel().setTool(CanvasTool.PAN));
        styleToolButton(panToolButton);
        canvasToolGroup.add(panToolButton);
        pinnedToolsPanel.add(panToolButton);

        javax.swing.JToggleButton deleteToolButton = new javax.swing.JToggleButton(scaledIcon(ResourcePathConfig.ERASER_ICON));
        deleteToolButton.setToolTipText("Delete");
        deleteToolButton.addActionListener(evt -> getPetriNetsPanel().setTool(CanvasTool.DELETE));
        styleToolButton(deleteToolButton);
        canvasToolGroup.add(deleteToolButton);
        pinnedToolsPanel.add(deleteToolButton);

        pinnedToolsPanel.add(javax.swing.Box.createVerticalStrut(8));
        pinnedToolsPanel.add(toolSectionSeparator());
        pinnedToolsPanel.add(javax.swing.Box.createVerticalStrut(8));

        newPlaceButton.setIcon(scaledIcon(ResourcePathConfig.PLACE_ICON));
        newPlaceButton.setToolTipText("Place");
        newPlaceButton.addActionListener(evt -> getPetriNetsPanel().setTool(CanvasTool.ADD_PLACE));
        styleToolButton(newPlaceButton);
        canvasToolGroup.add(newPlaceButton);
        pinnedToolsPanel.add(newPlaceButton);

        newTransitionButton.setIcon(scaledIcon(ResourcePathConfig.TRANSITION_ICON));
        newTransitionButton.setToolTipText("Transition");
        newTransitionButton.addActionListener(evt -> getPetriNetsPanel().setTool(CanvasTool.ADD_TRANSITION));
        styleToolButton(newTransitionButton);
        canvasToolGroup.add(newTransitionButton);
        pinnedToolsPanel.add(newTransitionButton);

        newArcButton.setIcon(scaledIcon(ResourcePathConfig.ARC_ICON));
        newArcButton.setToolTipText("Arc");
        newArcButton.addActionListener(this::newArcButtonActionPerformed);
        styleToolButton(newArcButton);
        canvasToolGroup.add(newArcButton);
        pinnedToolsPanel.add(newArcButton);

        // The divider belongs to the pinned band, not the scrolling one, so it cannot scroll
        // away and leave the Petri-objects looking like part of the drawing tools.
        pinnedToolsPanel.add(javax.swing.Box.createVerticalStrut(8));
        pinnedToolsPanel.add(toolSectionSeparator());
        pinnedToolsPanel.add(javax.swing.Box.createVerticalStrut(8));

        javax.swing.JToggleButton objectBandToolButton =
                new javax.swing.JToggleButton(CanvasToolIcons.objectBand(TOOL_ICON_SIZE));
        objectBandToolButton.setToolTipText(
                "Draw Petri-object: drag a rectangle, and whatever it fully encloses becomes a"
                        + " new Petri-object");
        objectBandToolButton.addActionListener(evt -> getPetriNetsPanel().setTool(CanvasTool.OBJECT_BAND));
        styleToolButton(objectBandToolButton);
        canvasToolGroup.add(objectBandToolButton);
        pinnedToolsPanel.add(objectBandToolButton);

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

        protocolScrollPane.setBorder(null);
        protocolScrollPane.setAutoscrolls(true);
        protocolScrollPane.setMinimumSize(new java.awt.Dimension(21, 220));

        protocolTextArea.setFont(new java.awt.Font("Tahoma", Font.PLAIN, 10)); // NOI18N
        protocolTextArea.setText("-------------- Events protokol ---------------");
        protocolTextArea.setBorder(javax.swing.BorderFactory.createLineBorder(new java.awt.Color(255, 255, 255)));
        protocolTextArea.setMinimumSize(new java.awt.Dimension(100, 400));
        protocolTextArea.setName(""); // NOI18N
        protocolScrollPane.setViewportView(protocolTextArea);

        modelingResultsSplitPane.setLeftComponent(
                withOpenLogButton(protocolScrollPane, protocolTextArea, "petri-events-protocol"));

        statisticsScrollPane.setBorder(javax.swing.BorderFactory.createLineBorder(new java.awt.Color(0, 0, 0)));

        statisticsTextArea.setFont(new java.awt.Font("Tahoma", Font.PLAIN, 10)); // NOI18N
        statisticsTextArea.setText("--------------- STATISTICS ----------------");
        statisticsTextArea.setBorder(javax.swing.BorderFactory.createLineBorder(new java.awt.Color(255, 255, 255)));
        statisticsTextArea.setName(""); // NOI18N
        statisticsScrollPane.setViewportView(statisticsTextArea);
        statisticsTextArea.getAccessibleContext().setAccessibleName("");

        modelingResultsSplitPane.setRightComponent(
                withOpenLogButton(statisticsScrollPane, statisticsTextArea, "petri-statistics"));

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



        // The conventional File / Edit / View bar. PNML is the primary document format:
        // New, Open, Save and Save As all speak it, with the standard shortcuts. Every
        // older format lives on under File > Legacy formats, present but out of the way,
        // and none of them holds a keyboard shortcut hostage any more (the old bar had
        // Ctrl+S on a legacy save, Ctrl+X on export, and Ctrl+M on two different items).
        fileMenu.setText("File");
        fileMenu.setMargin(new java.awt.Insets(0, 10, 0, 10));

        newMenuItem.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_N, java.awt.event.InputEvent.CTRL_DOWN_MASK));
        newMenuItem.setText("New");
        newMenuItem.addActionListener(this::newMenuItemActionPerformed);
        fileMenu.add(newMenuItem);

        importPnmlMenuItem = new javax.swing.JMenuItem();
        importPnmlMenuItem.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_O, java.awt.event.InputEvent.CTRL_DOWN_MASK));
        importPnmlMenuItem.setText("Open...");
        importPnmlMenuItem.addActionListener(this::importPnmlMenuItemActionPerformed);
        fileMenu.add(importPnmlMenuItem);

        openRecentMenu = new javax.swing.JMenu("Open Recent");
        openRecentMenu.addMenuListener(new javax.swing.event.MenuListener() {
            @Override
            public void menuSelected(javax.swing.event.MenuEvent e) {
                rebuildOpenRecentMenu();
            }

            @Override
            public void menuDeselected(javax.swing.event.MenuEvent e) {
            }

            @Override
            public void menuCanceled(javax.swing.event.MenuEvent e) {
            }
        });
        fileMenu.add(openRecentMenu);

        fileMenu.addSeparator();

        savePnmlMenuItem = new javax.swing.JMenuItem();
        savePnmlMenuItem.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_S, java.awt.event.InputEvent.CTRL_DOWN_MASK));
        savePnmlMenuItem.setText("Save");
        savePnmlMenuItem.addActionListener(this::savePnmlMenuItemActionPerformed);
        fileMenu.add(savePnmlMenuItem);

        exportPnmlMenuItem = new javax.swing.JMenuItem();
        exportPnmlMenuItem.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_S, java.awt.event.InputEvent.SHIFT_DOWN_MASK | java.awt.event.InputEvent.CTRL_DOWN_MASK));
        exportPnmlMenuItem.setText("Save As...");
        exportPnmlMenuItem.addActionListener(this::exportPnmlMenuItemActionPerformed);
        fileMenu.add(exportPnmlMenuItem);

        fileMenu.addSeparator();

        legacyMenu = new javax.swing.JMenu("Legacy formats");

        openMenuItem.setText("Open .pns worksheet");
        openMenuItem.addActionListener(this::openMenuItemActionPerformed);
        legacyMenu.add(openMenuItem);

        openMethodMenuItem.setText("Open a method file");
        openMethodMenuItem.addActionListener(this::openMethodMenuItemActionPerformed);
        legacyMenu.add(openMethodMenuItem);

        legacyMenu.addSeparator();

        saveGraphNetMenuItem.setText("Save Graph net");
        saveGraphNetMenuItem.addActionListener(this::saveGraphNetMenuItemActionPerformed);
        legacyMenu.add(saveGraphNetMenuItem);

        saveGraphNetAsMenuItem.setText("Save Graph net as");
        saveGraphNetAsMenuItem.addActionListener(this::saveGraphNetAsMenuItemActionPerformed);
        legacyMenu.add(saveGraphNetAsMenuItem);

        savePetriNetAsMenuItem.setText("Save Petri net as");
        savePetriNetAsMenuItem.addActionListener(this::savePetriNetAsMenuItemActionPerformed);
        legacyMenu.add(savePetriNetAsMenuItem);

        saveNetAsMethodMenuItem.setText("Save net as method");
        saveNetAsMethodMenuItem.addActionListener(this::saveNetAsMethodMenuItemActionPerformed);
        legacyMenu.add(saveNetAsMethodMenuItem);

        saveMethodInLibraryMenuItem.setText("Save method in NetLibrary");
        saveMethodInLibraryMenuItem.addActionListener(this::saveMethodInLibraryMenuItemActionPerformed);
        legacyMenu.add(saveMethodInLibraryMenuItem);

        fileMenu.add(legacyMenu);

        fileMenu.addSeparator();

        welcomeScreenMenuItem = new javax.swing.JMenuItem("Welcome Screen...");
        welcomeScreenMenuItem.addActionListener(evt -> showWelcomeScreenFromMenu());
        fileMenu.add(welcomeScreenMenuItem);

        fileMenu.addSeparator();

        exitMenuItem = new javax.swing.JMenuItem("Exit");
        exitMenuItem.addActionListener(evt -> dispatchEvent(
                new java.awt.event.WindowEvent(this, java.awt.event.WindowEvent.WINDOW_CLOSING)));
        fileMenu.add(exitMenuItem);

        petriNetsFrameMenuBar.add(fileMenu);

        editMenu.setText("Edit");
        editMenu.setMargin(new java.awt.Insets(0, 10, 0, 10));

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

        editMenu.addSeparator();

        editNetParametersMenuItem.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_E, java.awt.event.InputEvent.CTRL_DOWN_MASK));
        editNetParametersMenuItem.setText("Edit net parameters");
        editNetParametersMenuItem.addActionListener(this::editNetParametersMenuItemActionPerformed);
        editMenu.add(editNetParametersMenuItem);

        // Where an application's settings live on Windows and Linux. The theme also has a
        // direct switch under View, since that one is reached often enough to be worth two
        // clicks rather than four - see buildThemeMenu.
        editMenu.addSeparator();
        preferencesMenuItem = new javax.swing.JMenuItem("Preferences...");
        preferencesMenuItem.addActionListener(evt ->
                SettingsDialog.showPreferences(this, AppSettings.shared()));
        editMenu.add(preferencesMenuItem);

        petriNetsFrameMenuBar.add(editMenu);

        viewMenu = new javax.swing.JMenu("View");
        viewMenu.setMargin(new java.awt.Insets(0, 10, 0, 10));

        centerOnNetMenuItem.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_L, java.awt.event.InputEvent.CTRL_DOWN_MASK));
        centerOnNetMenuItem.setText("Center on net");
        centerOnNetMenuItem.addActionListener(this::centerOnNetMenuItemActionPerformed);
        viewMenu.add(centerOnNetMenuItem);

        viewMenu.addSeparator();
        viewMenu.add(buildThemeMenu());

        petriNetsFrameMenuBar.add(viewMenu);

        statisticMenu.setText("Statistics");

        openMonitor.setText("Open monitor");
        openMonitor.setMnemonic(KeyEvent.VK_M);
        openMonitor.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_M, KeyEvent.CTRL_DOWN_MASK | KeyEvent.ALT_DOWN_MASK));
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

        pObjectsMenuItem.setText("Petri-objects");
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
        refreshUndoRedoMenuState();
    }//GEN-LAST:event_undoMenuItemActionPerformed

    private void redoMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_redoMenuItemActionPerformed
        if (undoManager.canRedo()) {
            undoManager.redo();
        }
        refreshUndoRedoMenuState();
    }//GEN-LAST:event_redoMenuItemActionPerformed

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
            netNameTextField.setText(RandomNetNameGenerator.generate());
            String pnetName = fileUse.openFile(getPetriNetsPanel(), this);
            if (pnetName != null) {
                netNameTextField.setText(pnetName);
            }
        } catch (ExceptionInvalidNetStructure ex) {
            LOGGER.error("Unexpected error", ex);
        }
    }// GEN-LAST:event_openMenuItemActionPerformed

    private void newMenuItemActionPerformed(java.awt.event.ActionEvent evt) {// GEN-FIRST:event_newMenuItemActionPerformed
        // File > New discards a whole drawing exactly like the open-as-new paths, so it asks
        // the same question and resets the same workspace. It used to do neither: the canvas
        // was wiped with no confirmation, and the undo history survived into the new
        // document, where Ctrl+Z would replay stale edits against a net they never touched.
        if (!confirmDiscardingCurrentNet()) {
            return;
        }
        resetWorkspaceForNewDocument();
        netNameTextField.setText(RandomNetNameGenerator.generate());
    }// GEN-LAST:event_newMenuItemActionPerformed

    private void saveNetAsMethodMenuItemActionPerformed(java.awt.event.ActionEvent evt) {// GEN-FIRST:event_saveNetAsMethodMenuItemActionPerformed
        try {
            GraphPetriNet net = getPetriNetsPanel().getGraphNet();
            net.createPetriNet(netNameTextField.getText());
            fileUse.saveNetAsMethod(net, statisticsTextArea);
        } catch (ExceptionInvalidNetStructure | ExceptionInvalidTimeDelay ex) {
            LOGGER.error("Unexpected error", ex);
        }
    }// GEN-LAST:event_saveNetAsMethodMenuItemActionPerformed

    private void saveGraphNetMenuItemActionPerformed(java.awt.event.ActionEvent evt) {// GEN-FIRST:event_saveGraphNetMenuItemActionPerformed
        GraphPetriNet net = getPetriNetsPanel().getGraphNet();
        if (net == null) {
            return;
        }
        try {
            if (!fileUse.saveGraphNet(net, netNameTextField.getText().trim())) {
                LOGGER.warn("Graph net was not saved");
            }
        } catch (ExceptionInvalidNetStructure ex) {
            LOGGER.error("Unexpected error", ex);
        }
    }// GEN-LAST:event_saveGraphNetMenuItemActionPerformed

    private void savePetriNetAsMenuItemActionPerformed(java.awt.event.ActionEvent evt) {// GEN-FIRST:event_savePetriNetAsMenuItemActionPerformed
        try {
            fileUse.savePetriNetAs(getPetriNetsPanel(), this);
        } catch (ExceptionInvalidNetStructure | ExceptionInvalidTimeDelay ex) {
            LOGGER.error("Unexpected error", ex);
        }
    }// GEN-LAST:event_savePetriNetAsMenuItemActionPerformed

    private void saveGraphNetAsMenuItemActionPerformed(java.awt.event.ActionEvent evt) {// GEN-FIRST:event_saveGraphNetAsMenuItemActionPerformed
        try {
            fileUse.saveGraphNetAs(getPetriNetsPanel(), this);
        } catch (ExceptionInvalidNetStructure | ExceptionInvalidTimeDelay ex) {
            LOGGER.error("Unexpected error", ex);
        }
    }// GEN-LAST:event_saveGraphNetAsMenuItemActionPerformed

    private void saveMethodInLibraryMenuItemActionPerformed(
            java.awt.event.ActionEvent evt) {// GEN-FIRST:event_saveMethodInLibraryMenuItemActionPerformed
        // The statistics pane doubles as the scratch area a generated method body is written
        // into, so a body without a single brace in it is nothing worth filing away.
        if (!statisticsTextArea.getText().contains("{")) {
            return;
        }
        fileUse.saveMethodInNetLibrary(statisticsTextArea);
        refreshNetLibraryMethods();
    }// GEN-LAST:event_saveMethodInLibraryMenuItemActionPerformed

    private void editNetParametersMenuItemActionPerformed(java.awt.event.ActionEvent evt) {// GEN-FIRST:event_editNetParametersMenuItemActionPerformed
        try {
            // With a net on the canvas the window edits that net's parameters; with an empty
            // canvas it opens standalone, on whatever the user loads into it from there.
            GraphNetParametersFrame parametersFrame = getPetriNetsPanel().getGraphNet() != null
                    ? new GraphNetParametersFrame(this)
                    : new GraphNetParametersFrame();
            parametersFrame.setVisible(true);
        } catch (ExceptionInvalidNetStructure ex) {
            LOGGER.error("Unexpected error", ex);
        }
    }// GEN-LAST:event_editNetParametersMenuItemActionPerformed

    /**
     * Runs every check a net has to pass before it can be simulated, stopping at the first
     * one it fails and putting the reason in front of the user.
     *
     * @return whether the drawing on the canvas is a net that can actually be run
     */
    private boolean isCorrectNet() throws ExceptionInvalidNetStructure, ExceptionInvalidTimeDelay {
        GraphPetriNet net = getPetriNetsPanel().getGraphNet();
        if (net == null) {
            return rejectNet(" Graph image of Petri Net does not exist yet. Paint it or read it from file.");
        }
        if (!net.isCorrectInArcs()) {
            return rejectNet(" Transition has no input places.");
        }
        if (!net.isCorrectOutArcs()) {
            return rejectNet(" Transition has no output places.");
        }

        net.createPetriNet(netNameTextField.getText());
        if (net.getPetriNet() == null) {
            return rejectNet(" Petri Net does not exist yet. Paint it or read it from file. ");
        }
        try {
            net.getPetriNet().validateStructure();
        } catch (ExceptionInvalidTimeDelay ex) {
            return rejectNet(" " + ex.getMessage());
        }
        if (net.hasParameters()) {
            return rejectNet(describeUnspecifiedParameters(
                    net.getPetriNet().getUnspecifiedParameters()));
        }
        return true;
    }

    /** Puts one problem in front of the user in the error window. */
    private void showNetError(String message) {
        errorFrame.setErrorMessage(message);
        errorFrame.setVisible(true);
    }

    /**
     * Reports a failed check and answers {@code false}, so each step of
     * {@link #isCorrectNet()} reads as a single {@code return}.
     */
    private boolean rejectNet(String message) {
        showNetError(message);
        return false;
    }

    /** Spells out which parameters are still waiting for a value, and where to supply them. */
    private static String describeUnspecifiedParameters(ArrayList<String> parameters) {
        StringBuilder message = new StringBuilder(
                "The Petri Net contains unspecified parameters that must be configured before simulation can begin.\n\n")
                .append("Unspecified parameters:\n");
        for (String parameter : parameters) {
            message.append("• ").append(parameter).append("\n");
        }
        return message.append("\nPlease open the 'Edit Net Parameters' dialog (Ctrl+E) to provide specific "
                + "values for all parameters, or ensure all transition delays and place markings are "
                + "properly defined.").toString();
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
        statisticsTextArea.setText("---------STATISTICS---------");
        // A run is a run of the whole model, so it is watched where the whole model is drawn.
        // Without this, pressing Run from inside a Petri-object's own canvas would show a fragment
        // of what is actually running.
        getPetriNetsPanel().activateRootCanvas();
        try {
            if (!isCorrectNet()) {
                return;
            }
            getPetriNetsPanel().getGraphNet().createPetriNet(netNameTextField.getText());

            RunPetriObjModel model = getRunPetriObjModel();
            model.setSimulationTime(modelingTime());
            model.setCurrentTime(startTime());
            if (statisticMonitorDialog != null && isStatisticMonitorEnabled.isSelected()) {
                statisticGraphMonitor = new StatisticGraphMonitor(statisticMonitorDialog, true);
                model.setStatisticMonitor(statisticGraphMonitor);
            }
            model.setProgressListener(fraction ->
                    SwingUtilities.invokeLater(() -> updateRunProgress(fraction)));

            // Published here so the Stop button (AnimationControls) can reach the model while
            // it runs on its own thread, and taken back down again the moment this sub-run is
            // over, whether it finished or was halted.
            runModel = model;
            try {
                model.go(modelingTime());
            } finally {
                runModel = null;
            }

            getPetriNetsPanel().getGraphNet().printStatistics(statisticsTextArea::append);
            getPetriNetsPanel().repaint();
            awaitStatisticMonitor();
        } catch (ExceptionInvalidNetStructure | ExceptionInvalidTimeDelay ex) {
            reportSimulationFailure(ex);
        }
    }

    /**
     * Says what the horizon amounts to under the chosen units, which is the whole visible effect
     * of choosing them. Blank while the units stand for nothing, or while the field holds
     * something that is not a number - a half-typed value is not worth an error beside it.
     */
    private void refreshHorizonReading() {
        String reading;
        try {
            reading = timeUnitControl.getScale().formatDuration(modelingTime());
        } catch (NumberFormatException notANumberYet) {
            reading = "";
        }
        horizonReadingLabel.setText(reading.isEmpty() ? " " : "= " + reading);
    }

    /** The simulation horizon and the clock it starts from, as the header fields spell them. */
    private double modelingTime() {
        return Double.parseDouble(timeModelingTextField.getText());
    }

    private double startTime() {
        return Double.parseDouble(timeStartField.getText());
    }

    /**
     * Gives the monitor's background worker a short grace period to finish charting what the
     * run just produced. Bounded on purpose: a half-drawn chart is worth waiting a moment
     * for, a wedged worker is not worth freezing the editor over.
     */
    private void awaitStatisticMonitor() {
        if (statisticGraphMonitor == null) {
            return;
        }
        try {
            statisticGraphMonitor.getWorkerStateLatch().await(3, TimeUnit.SECONDS);
        } catch (InterruptedException ex) {
            LOGGER.warn(ex.getMessage(), ex);
            Thread.currentThread().interrupt();
        }
    }

    /** Logs a run that could not start or could not finish, and says so on screen. */
    private void reportSimulationFailure(Exception ex) {
        LOGGER.error(ex.getMessage(), ex);
        showNetError(" " + ex.getMessage());
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
            petriSim.setSimulationTime(modelingTime());
            petriSim.setTimeCurr(startTime());
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
        statisticsTextArea.setText("---------STATISTICS---------");
        // See runNet: an animation animates the whole model, so it belongs on the net's canvas.
        getPetriNetsPanel().activateRootCanvas();
        try {
            if (!isCorrectNet()) {
                return;
            }
            getPetriNetsPanel().getGraphNet().createPetriNet(netNameTextField.getText());

            AnimRunPetriObjModel model = getAnimRunPetriObjModel();
            animationModel = model;

            if (startAnimationStepping) {
                startAnimationStepping = false;
                model.stepOnce();
            }

            model.setSimulationTime(modelingTime());
            model.setCurrentTime(startTime());
            if (statisticMonitorDialog != null && isStatisticMonitorEnabled.isSelected()) {
                model.setStatisticMonitor(new StatisticGraphMonitor(statisticMonitorDialog, false));
            }

            getPetriNetsPanel().clearAnimationHighlight();
            model.go(modelingTime());
            getPetriNetsPanel().getGraphNet().printStatistics(statisticsTextArea::append);
            getPetriNetsPanel().clearAnimationHighlight();
            getPetriNetsPanel().repaint();
        } catch (ExceptionInvalidNetStructure | ExceptionInvalidTimeDelay ex) {
            reportSimulationFailure(ex);
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
                    protocolTextArea, getPetriNetsPanel(), speedControl, null, object.getGraphNet());
            petriSim.setName(object.getName());
            petriSim.setPriority(object.getPriority());
            petriSim.setSimulationTime(modelingTime());
            petriSim.setTimeCurr(startTime());
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

    private void centerOnNetMenuItemActionPerformed(
            java.awt.event.ActionEvent evt) {// GEN-FIRST:event_centerOnNetMenuItemActionPerformed
        Rectangle viewBounds = petriNetPanelScrollPane.getBounds();
        LOGGER.debug("{}  {}", viewBounds.x, viewBounds.width);
        Point centre = new Point(
                viewBounds.x + viewBounds.width / 2,
                viewBounds.y + viewBounds.height / 2);
        // Through the canvas rather than straight at the net: the net alone has no notion of a
        // Petri-object frame, so moving it directly slid every object's net out from under its own
        // frame and left every frame where it was.
        getPetriNetsPanel().centreCanvasAt(centre);
        getPetriNetsPanel().repaint();
    }// GEN-LAST:event_centerOnNetMenuItemActionPerformed

    private void openMethodMenuItemActionPerformed(
            java.awt.event.ActionEvent evt) {// GEN-FIRST:event_openMethodMenuItemActionPerformed
        // The library can gain methods while the editor is open, so the list is rebuilt every
        // time the dialog is raised rather than once when it is first created.
        refreshNetLibraryMethods();

        if (methodChooserDialog == null) {
            methodChooserDialog =
                    new JDialog(this, "Method to open", ModalityType.APPLICATION_MODAL);
            methodChooserDialog.getContentPane().add(methodChooserPanel);
            methodChooserDialog.pack();
            methodChooserDialog.setLocationRelativeTo(null);
        }
        // Opening a net from the menu starts a new document, the way opening a file does —
        // as opposed to the Nets window, which adds one to the drawing in progress.
        methodChooserPanel.onConfirm(
                accepted -> openLibraryMethodAsNewNet(methodChooserPanel.selectedMethod()));
        methodChooserDialog.setVisible(true);
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
        java.io.File selectedFile = null;
        try {
            javax.swing.JFileChooser chooser = newDocumentChooser("Open");
            chooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter(
                    "PNML or XML model (*.pnml, *.xml)", "pnml", "xml"));
            if (chooser.showOpenDialog(this) != javax.swing.JFileChooser.APPROVE_OPTION) {
                return;
            }
            selectedFile = chooser.getSelectedFile();
            lastOpenDirectory = selectedFile.getParentFile();

            // Opening a document, so everything the old one left behind goes with it — the
            // undo stack in particular, whose edits would otherwise apply to this new net.
            resetWorkspaceForNewDocument();
            loadPnmlFile(selectedFile);
        } catch (Exception ex) {
            LOGGER.error("Failed to import PNML", ex);
            String fileName = selectedFile != null ? selectedFile.getName() : "the selected file";
            MessageHelper.showException(this, "Error importing PNML file: " + fileName, ex);
        }
    }

    /**
     * Parses {@code file} as a PNML document, applies it to the canvas, and remembers it as
     * the document's file — the part of opening a PNML file that every caller needs, whether
     * it came from the Open dialog, an "Open Recent" entry, the Welcome screen, or startup
     * auto-reopen. Callers are responsible for confirming discard and calling {@link
     * #resetWorkspaceForNewDocument()} first, since not every caller wants those at the same
     * point — a blank canvas at startup has nothing to discard or reset.
     *
     * <p>Any soft-validation warning the parser raised is shown once opening succeeds: an id
     * that was not valid XML and was remapped, a value that did not parse, a link the document
     * declared but the structure disagreed with. Opening proceeds regardless; the dialog is
     * purely informational.
     */
    private void loadPnmlFile(java.io.File file) throws Exception {
        PnmlModelParser parser = new PnmlModelParser();
        GraphPetriObjModel objModel = parser.parse(file);
        GraphCanvasModel canvas = GraphCanvasModel.fromObjModel(objModel);
        getPetriNetsPanel().setCanvasModel(canvas);
        netNameTextField.setText(objModel.getName());
        // The opened file is the document's file from here on: plain Save writes back
        // to it silently, the way every editor treats the file it has open.
        currentPnmlFile = file;

        ua.stetsenkoinna.recentprojects.RecentProjectEntry entry =
                ua.stetsenkoinna.recentprojects.RecentProjectsStore.shared()
                        .recordOpened(file.toPath(), objModel.getName());
        ua.stetsenkoinna.recentprojects.RecentProjectsStore.shared().setActiveProjectId(entry.getId());

        MessageHelper.showImportWarnings(this, parser.getWarnings());
    }

    /**
     * Opens {@code file} onto this already-blank frame — used only by the app launcher for
     * startup auto-reopen, where the canvas is already empty so there is nothing to discard
     * or reset first. Any failure is logged and shown, and the canvas is left blank rather
     * than the exception propagating and taking startup down with it.
     */
    public void openProjectFile(java.io.File file) {
        try {
            loadPnmlFile(file);
        } catch (Exception ex) {
            LOGGER.error("Failed to auto-reopen project", ex);
            MessageHelper.showException(this,
                    "Could not reopen the last project: " + file.getName(), ex);
        }
    }

    /**
     * Opens an entry chosen from the "Open Recent" menu: confirms discarding the current net,
     * checks the file still exists, then loads it exactly as the Open dialog would.
     */
    private void openRecentProject(ua.stetsenkoinna.recentprojects.RecentProjectEntry entry) {
        if (!confirmDiscardingCurrentNet()) {
            return;
        }
        java.io.File file = new java.io.File(entry.getPath());
        if (!file.isFile()) {
            MessageHelper.showError(this,
                    "'" + entry.getName() + "' could not be found at:\n" + entry.getPath());
            return;
        }
        resetWorkspaceForNewDocument();
        try {
            loadPnmlFile(file);
        } catch (Exception ex) {
            LOGGER.error("Failed to open recent project", ex);
            MessageHelper.showException(this, "Error opening PNML file: " + file.getName(), ex);
        }
    }

    /**
     * Rebuilds the "Open Recent" submenu from scratch — called right before it is shown, so it
     * always reflects the registry as of that moment rather than as of when the frame was
     * built.
     */
    private void rebuildOpenRecentMenu() {
        openRecentMenu.removeAll();

        java.util.List<ua.stetsenkoinna.recentprojects.RecentProjectEntry> recents =
                new java.util.ArrayList<>(
                        ua.stetsenkoinna.recentprojects.RecentProjectsStore.shared().all());
        recents.sort(java.util.Comparator.comparingLong(
                ua.stetsenkoinna.recentprojects.RecentProjectEntry::getLastOpenedAt).reversed());

        if (recents.isEmpty()) {
            javax.swing.JMenuItem empty = new javax.swing.JMenuItem("(no recent projects)");
            empty.setEnabled(false);
            openRecentMenu.add(empty);
        } else {
            int limit = Math.min(recents.size(), 10);
            for (int i = 0; i < limit; i++) {
                ua.stetsenkoinna.recentprojects.RecentProjectEntry entry = recents.get(i);
                javax.swing.JMenuItem item = new javax.swing.JMenuItem(entry.getName());
                item.setToolTipText(entry.getPath());
                item.addActionListener(evt -> openRecentProject(entry));
                openRecentMenu.add(item);
            }
        }

        openRecentMenu.addSeparator();
        javax.swing.JMenuItem manage = new javax.swing.JMenuItem("Manage Recent Projects...");
        manage.addActionListener(evt -> manageRecentProjects());
        openRecentMenu.add(manage);
    }

    /**
     * Hides this frame and shows the Welcome screen, reusing this same instance rather than
     * constructing a second {@code PetriNetsFrame} — the undo manager and undo support behind
     * this frame are static, shared across every instance ever built, and a second instance
     * would corrupt that shared state. {@code setVisible(false)} does not fire {@code
     * WINDOW_CLOSING}, so {@code EXIT_ON_CLOSE} does not trigger and the JVM does not exit.
     */
    private void showWelcomeScreenFromMenu() {
        if (!confirmDiscardingCurrentNet()) {
            return;
        }
        setVisible(false);
        ua.stetsenkoinna.graphpresentation.welcome.WelcomeFrame.show(
                ua.stetsenkoinna.config.AppSettings.shared(),
                ua.stetsenkoinna.recentprojects.RecentProjectsStore.shared(),
                file -> {
                    resetWorkspaceForNewDocument();
                    // Shown before loading, not after: a parse failure below puts up an error
                    // dialog, which needs this frame visible and the default parent again to
                    // attach to rather than the WelcomeFrame that is about to be disposed.
                    reshowAfterWelcome();
                    try {
                        loadPnmlFile(file);
                    } catch (Exception ex) {
                        LOGGER.error("Failed to open project from welcome screen", ex);
                        MessageHelper.showException(this, "Error opening PNML file: " + file.getName(), ex);
                    }
                    return true; // discard was already confirmed above, before this frame was hidden
                },
                () -> {
                    resetWorkspaceForNewDocument();
                    netNameTextField.setText(RandomNetNameGenerator.generate());
                    reshowAfterWelcome();
                    return true;
                },
                this::reshowAfterWelcome, // onDismiss - closed with nothing picked: just come back as-is
                false // exitAppIfDismissed - this frame already exists and was only hidden, never exit here
        );
    }

    /**
     * Opens the Welcome screen next to this frame, for browsing or tidying up the recent-
     * projects list (sorting, removing entries, editing description/authors) without touching
     * whatever is currently open. Unlike {@link #showWelcomeScreenFromMenu()}, this frame is
     * never hidden and discarding is never asked about up front — only if the user actually
     * picks something to open or starts a new document from inside the Welcome screen does
     * discarding the current net become relevant at all, so it is asked about at that point,
     * and only then.
     */
    private void manageRecentProjects() {
        ua.stetsenkoinna.graphpresentation.welcome.WelcomeFrame.show(
                ua.stetsenkoinna.config.AppSettings.shared(),
                ua.stetsenkoinna.recentprojects.RecentProjectsStore.shared(),
                file -> {
                    if (!confirmDiscardingCurrentNet()) {
                        return false; // stays open exactly as it was - this frame was never touched
                    }
                    resetWorkspaceForNewDocument();
                    try {
                        loadPnmlFile(file);
                    } catch (Exception ex) {
                        LOGGER.error("Failed to open project from recent-projects manager", ex);
                        MessageHelper.showException(this, "Error opening PNML file: " + file.getName(), ex);
                    }
                    focusMainFrame();
                    return true;
                },
                () -> {
                    if (!confirmDiscardingCurrentNet()) {
                        return false;
                    }
                    resetWorkspaceForNewDocument();
                    netNameTextField.setText(RandomNetNameGenerator.generate());
                    focusMainFrame();
                    return true;
                },
                () -> { }, // onDismiss - this frame was never hidden, so there is nothing to restore
                false // exitAppIfDismissed - never exit from here
        );
    }

    private void reshowAfterWelcome() {
        MessageHelper.setDefaultParent(this);
        setVisible(true);
        toFront();
    }

    /**
     * Restores this frame as {@link MessageHelper}'s default parent and raises it, after the
     * Welcome screen shown by {@link #manageRecentProjects()} disposes itself — that window,
     * not this one, held the default parent and the foreground while it was open, even though
     * this frame stayed visible underneath the whole time.
     */
    private void focusMainFrame() {
        MessageHelper.setDefaultParent(this);
        toFront();
        requestFocus();
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

            java.io.File selectedFile = chooseSaveAsFile();
            if (selectedFile == null) {
                return; // dialog cancelled
            }
            lastSaveAsDirectory = selectedFile.getParentFile();

            // JFileChooser approves silently over an existing file; asking is on us.
            if (selectedFile.exists() && !MessageHelper.showConfirmation(this,
                    "'" + selectedFile.getName() + "' already exists. Overwrite it?")) {
                return;
            }

            writePnml(selectedFile);
        } catch (Exception ex) {
            LOGGER.error("Failed to export PNML", ex);
            MessageHelper.showException(this, "Error exporting PNML file", ex);
        }
    }

    /** One file type {@link #chooseSaveAsFile()} can offer: its extension and its label in the dialog's filter dropdown. */
    private record SaveFormat(String extension, String description) {
    }

    /**
     * File types the Save As dialog offers, in order - the first is the default filter and
     * what a typed name with none of these extensions gets appended. PNML and plain XML
     * (the same PNML content, just a different suffix) today; adding another format later is
     * exactly one more entry here, since both the dialog's filters and its fallback extension
     * in {@link #chooseSaveAsFile()} are driven from this list rather than any extension being
     * hardcoded elsewhere.
     */
    private static final SaveFormat[] SAVE_AS_FORMATS = {
        new SaveFormat("pnml", "PNML model (*.pnml)"),
        new SaveFormat("xml", "XML document (*.xml)"),
    };

    /**
     * The OS's own native Save dialog ({@link java.awt.FileDialog}) rather than
     * {@code JFileChooser}, preceded by a small format pick of its own: {@code FileDialog}'s
     * Windows peer has no way to label more than one file-type filter in its own type box, so
     * asking here first - and baking the chosen extension into the suggested filename before
     * the native dialog ever opens - gets a real PNML/XML choice without needing the native
     * dialog to express it itself.
     *
     * <p>Defaults to {@link #petriNetsFolder()} when the document has never been saved
     * anywhere and no earlier Save As in this session has landed anywhere else either.
     *
     * @return the chosen file, with the picked format's extension appended if the typed name
     *         ended up with none of {@link #SAVE_AS_FORMATS}; {@code null} if either step was
     *         cancelled
     */
    private java.io.File chooseSaveAsFile() {
        SaveFormat format = pickSaveFormat();
        if (format == null) {
            return null;
        }

        java.awt.FileDialog dialog = new java.awt.FileDialog(this, "Save As", java.awt.FileDialog.SAVE);
        java.io.File startDir = lastSaveAsDirectory != null
                ? lastSaveAsDirectory
                : currentPnmlFile != null ? currentPnmlFile.getParentFile() : petriNetsFolder();
        dialog.setDirectory(startDir.getAbsolutePath());
        String baseName = currentPnmlFile != null
                ? stripSaveAsExtension(currentPnmlFile.getName())
                : netNameTextField.getText();
        dialog.setFile(baseName + "." + format.extension());
        // Best-effort: restricts which existing files are browsable, even though the native
        // type box itself will not necessarily reflect it - that is exactly what the pick
        // above is for.
        dialog.setFilenameFilter((dir, name) -> hasSaveAsExtension(name));
        dialog.setVisible(true); // modal - blocks until the user picks a file or cancels

        String fileName = dialog.getFile();
        if (fileName == null) {
            return null;
        }
        java.io.File selected = new java.io.File(dialog.getDirectory(), fileName);
        if (!hasSaveAsExtension(selected.getName())) {
            selected = new java.io.File(selected.getAbsolutePath() + "." + format.extension());
        }
        return selected;
    }

    /**
     * A plain-button vertical list rather than {@code JOptionPane.showOptionDialog}'s default
     * side-by-side row: one format per row reads better than a horizontal strip of buttons
     * once their labels are full descriptions like "PNML model (*.pnml)" rather than "Yes"/
     * "No".
     *
     * @return the format the user picked from {@link #SAVE_AS_FORMATS}, or {@code null} if
     *         they closed the picker (Cancel, Escape, or the window's own close button)
     *         without choosing one
     */
    private SaveFormat pickSaveFormat() {
        javax.swing.JDialog dialog = new javax.swing.JDialog(this, "Save As", true);
        dialog.setDefaultCloseOperation(javax.swing.JDialog.DISPOSE_ON_CLOSE);

        javax.swing.JPanel panel = new javax.swing.JPanel();
        panel.setLayout(new javax.swing.BoxLayout(panel, javax.swing.BoxLayout.Y_AXIS));
        panel.setBorder(javax.swing.BorderFactory.createEmptyBorder(14, 18, 14, 18));

        javax.swing.JLabel prompt = new javax.swing.JLabel("Save as which format?");
        prompt.setAlignmentX(java.awt.Component.CENTER_ALIGNMENT);
        panel.add(prompt);
        panel.add(javax.swing.Box.createVerticalStrut(10));

        SaveFormat[] chosen = new SaveFormat[1];
        for (SaveFormat format : SAVE_AS_FORMATS) {
            javax.swing.JButton button = new javax.swing.JButton(format.description());
            button.setAlignmentX(java.awt.Component.CENTER_ALIGNMENT);
            button.setMaximumSize(new java.awt.Dimension(Integer.MAX_VALUE, button.getPreferredSize().height));
            button.addActionListener(evt -> {
                chosen[0] = format;
                dialog.dispose();
            });
            panel.add(button);
            panel.add(javax.swing.Box.createVerticalStrut(6));
        }

        javax.swing.JButton cancel = new javax.swing.JButton("Cancel");
        cancel.setAlignmentX(java.awt.Component.CENTER_ALIGNMENT);
        cancel.setMaximumSize(new java.awt.Dimension(Integer.MAX_VALUE, cancel.getPreferredSize().height));
        cancel.addActionListener(evt -> dialog.dispose());
        panel.add(cancel);

        dialog.getRootPane().registerKeyboardAction(evt -> dialog.dispose(),
                javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_ESCAPE, 0),
                javax.swing.JComponent.WHEN_IN_FOCUSED_WINDOW);

        dialog.setLayout(new java.awt.BorderLayout());
        dialog.add(panel, java.awt.BorderLayout.CENTER);
        dialog.pack();
        dialog.setLocationRelativeTo(this);
        dialog.setVisible(true); // modal - blocks until a button disposes it

        return chosen[0];
    }

    private static boolean hasSaveAsExtension(String fileName) {
        String lower = fileName.toLowerCase();
        for (SaveFormat format : SAVE_AS_FORMATS) {
            if (lower.endsWith("." + format.extension())) {
                return true;
            }
        }
        return false;
    }

    /**
     * @return {@code fileName} with a known {@link #SAVE_AS_FORMATS} extension removed, so
     *         switching format on an already-saved document does not stack extensions (e.g.
     *         {@code "Net.pnml"} chosen as XML becomes {@code "Net.xml"}, not
     *         {@code "Net.pnml.xml"})
     */
    private static String stripSaveAsExtension(String fileName) {
        String lower = fileName.toLowerCase();
        for (SaveFormat format : SAVE_AS_FORMATS) {
            String suffix = "." + format.extension();
            if (lower.endsWith(suffix)) {
                return fileName.substring(0, fileName.length() - suffix.length());
            }
        }
        return fileName;
    }

    /**
     * @return the user's "PetriNets" folder under their Documents folder, created if it does
     *         not exist yet - offered as the default save location the first time a document
     *         is saved, so a brand-new net has somewhere sensible to land without the user
     *         having to navigate there themselves.
     */
    private static java.io.File petriNetsFolder() {
        java.io.File folder = new java.io.File(
                new java.io.File(System.getProperty("user.home"), "Documents"), "PetriNets");
        if (!folder.exists()) {
            folder.mkdirs();
        }
        return folder;
    }

    /**
     * Plain Save: writes the document back to the file it came from, silently, the way
     * every editor does. A document that has no file yet falls through to Save As.
     */
    private void savePnmlMenuItemActionPerformed(java.awt.event.ActionEvent evt) {
        if (getPetriNetsPanel().getGraphNet() == null) {
            return;
        }
        if (currentPnmlFile == null) {
            exportPnmlMenuItemActionPerformed(evt);
            return;
        }
        try {
            writePnml(currentPnmlFile);
        } catch (Exception ex) {
            LOGGER.error("Failed to save PNML", ex);
            MessageHelper.showException(this, "Error saving PNML file", ex);
        }
    }

    /**
     * A file chooser for the document dialogs, replacing the native AWT FileDialog: on
     * Windows that one cannot list file-type filters at all, so its type box misleadingly
     * said "All Files" for a dialog that only ever meant PNML.
     *
     * @param title the dialog title
     * @return a chooser starting where the previous dialog ended up, or at the document's
     *         own directory
     */
    private javax.swing.JFileChooser newDocumentChooser(String title) {
        java.io.File startAt = lastOpenDirectory != null
                ? lastOpenDirectory
                : currentPnmlFile != null ? currentPnmlFile.getParentFile() : null;
        javax.swing.JFileChooser chooser = new javax.swing.JFileChooser(startAt);
        chooser.setDialogTitle(title);
        return chooser;
    }

    /**
     * Writes the canvas to the given PNML file and remembers it as the document's file, so
     * the next plain Save goes there without asking.
     */
    private void writePnml(java.io.File file) throws Exception {
        GraphCanvasModel canvas = getPetriNetsPanel().getCanvasModel();
        canvas.setName(netNameTextField.getText());
        new PnmlModelGenerator().generate(canvas.toObjModel(), file);
        currentPnmlFile = file;

        ua.stetsenkoinna.recentprojects.RecentProjectEntry entry =
                ua.stetsenkoinna.recentprojects.RecentProjectsStore.shared()
                        .recordSaved(file.toPath(), canvas.getName());
        ua.stetsenkoinna.recentprojects.RecentProjectsStore.shared().setActiveProjectId(entry.getId());
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
        if (statisticMonitorDialog == null || !statisticMonitorDialog.getIsFormulaValid()) {
            return 1;
        }
        return statisticMonitorDialog.getChartDataCollectionConfig().getNumberOfRuns();
    }

    public void disableInput() {
        setEditingEnabled(false);
    }

    public void enableInput() {
        setEditingEnabled(true);
    }

    /**
     * Locks the whole editor down for the duration of a simulation and releases it again
     * afterwards: everything that could redraw the net, rename it, or change the times it is
     * run over goes out of reach while the model is being executed. The statistics monitor,
     * when one is open and switched on, is told which of the two just happened.
     */
    private void setEditingEnabled(boolean enabled) {
        fileMenu.setEnabled(enabled);
        editMenu.setEnabled(enabled);
        statisticMenu.setEnabled(enabled);

        leftIconToolBar.setEnabled(enabled);
        newPlaceButton.setEnabled(enabled);
        newTransitionButton.setEnabled(enabled);
        newArcButton.setEnabled(enabled);

        netNameTextField.setEnabled(enabled);
        timeStartField.setEnabled(enabled);
        timeModelingTextField.setEnabled(enabled);
        timeUnitControl.setEnabled(enabled);

        protocolTextArea.setEnabled(enabled);
        statisticsTextArea.setEnabled(enabled);

        if (statisticMonitorDialog == null || !isStatisticMonitorEnabled.isSelected()) {
            return;
        }
        if (enabled) {
            statisticMonitorDialog.onSimulationEnd();
        } else {
            statisticMonitorDialog.onSimulationStart();
        }
    }

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JMenuItem saveGraphNetMenuItem;
    private javax.swing.JMenuItem saveMethodInLibraryMenuItem;
    private javax.swing.JMenuItem saveNetAsMethodMenuItem;
    private javax.swing.JMenuItem savePetriNetAsMenuItem;
    private javax.swing.JMenuItem centerOnNetMenuItem;
    private javax.swing.JMenu editMenu;
    private javax.swing.JMenuItem editNetParametersMenuItem;
    private javax.swing.JMenuItem preferencesMenuItem;
    private javax.swing.JMenu themeMenu;
    private javax.swing.JMenu fileMenu;
    private javax.swing.JMenuItem saveGraphNetAsMenuItem;
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
    private javax.swing.JScrollPane protocolScrollPane;
    private javax.swing.JMenuItem redoMenuItem;
    private javax.swing.JProgressBar runProgressBar;
    private javax.swing.JButton runOneEventButton;
    private javax.swing.JButton stepBackButton;
    private javax.swing.JButton skipForwardAnimationButton;
    private javax.swing.JLabel speedLabel;
    private AnimationSpeedControl speedControl;
    private javax.swing.JMenu statisticMenu;
    private javax.swing.JScrollPane statisticsScrollPane;
    private javax.swing.JTextArea statisticsTextArea;
    private javax.swing.JButton stopAnimationButton;
    private javax.swing.JLabel timeModelingLabel;
    private javax.swing.JTextField timeModelingTextField;
    private TimeUnitControl timeUnitControl;
    private final javax.swing.JLabel horizonReadingLabel = new javax.swing.JLabel();
    private javax.swing.JTextField timeStartField;
    private javax.swing.JLabel timeStartLabel;
    private javax.swing.JMenuItem undoMenuItem;
    private javax.swing.JMenuItem importPnmlMenuItem;
    private javax.swing.JMenuItem exportPnmlMenuItem;
    private javax.swing.JMenuItem savePnmlMenuItem;
    private javax.swing.JMenu viewMenu;
    private javax.swing.JMenu legacyMenu;
    private javax.swing.JMenu openRecentMenu;
    private javax.swing.JMenuItem welcomeScreenMenuItem;
    private javax.swing.JMenuItem exitMenuItem;
    /**
     * The PNML file the document was last opened from or saved to, so plain Save can write
     * it silently the way every editor does; {@code null} until the document has a file,
     * when Save falls back to Save As.
     */
    private java.io.File currentPnmlFile;

    /**
     * Where the last Open dialog ended up, so the next one starts there. Deliberately not
     * shared with {@link #lastSaveAsDirectory} - opening a file from one folder should not
     * change where Save As defaults to for an unrelated new document.
     */
    private java.io.File lastOpenDirectory;

    /**
     * Where the last Save As dialog ended up, so the next one starts there instead of back at
     * {@link #petriNetsFolder()} every time - but only once the user has actually saved
     * somewhere with it; until then, a document with no file of its own defaults there.
     */
    private java.io.File lastSaveAsDirectory;
    // End of variables declaration//GEN-END:variables
    private static PetriNetsPanel petriNetsPanel;
    private final FileUse fileUse = new FileUse();
    private final ErrorFrame errorFrame = new ErrorFrame();

    private static final UndoManager undoManager = new UndoManager();
    private static final UndoableEditSupport undoSupport = new UndoableEditSupport();

    public static UndoableEditSupport getUndoSupport() {
        return undoSupport;
    }

    /**
     * The Petri-object model currently being animated. Exposed so the transport controls can
     * pause and resume it while it runs.
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
     * The thread an animation runs on, kept here so that pressing Stop has something to
     * interrupt.
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

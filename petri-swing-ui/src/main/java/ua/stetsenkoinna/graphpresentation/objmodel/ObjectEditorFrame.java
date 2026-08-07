package ua.stetsenkoinna.graphpresentation.objmodel;

import ua.stetsenkoinna.graphnet.GraphElementIdGenerator;
import ua.stetsenkoinna.graphnet.GraphPetriPlace;
import ua.stetsenkoinna.graphnet.GraphPetriTransition;
import ua.stetsenkoinna.graphpresentation.PetriNetsPanel;
import ua.stetsenkoinna.petriobj.PetriP;
import ua.stetsenkoinna.petriobj.PetriT;

import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JToolBar;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Window;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

/**
 * The window a Petri-object's own net is edited in.
 *
 * <p>Once an object exists, its elements are locked on the shared canvas — this is the only
 * place they can be moved, added, deleted or rewired. It hosts an ordinary
 * {@link PetriNetsPanel}, editable exactly like the canvas was before Petri-object composition
 * existed, showing just this object's own places, transitions and internal arcs; links to
 * other objects live outside it, on the frame's ports.
 *
 * <p>The panel it wraps operates on the very same element instances the main canvas holds for
 * this object, so while this window is open, a moved element is already the same move the main
 * canvas would show if it were visible underneath. What only takes effect on {@link #wasSaved}
 * is what the caller — {@code PetriNetsPanel.openObjectEditor} — does with that once this
 * window closes: on Save it reconciles this net's own separate list of elements into the main
 * canvas's (which is what makes an addition or removal here take effect at all), and on Cancel
 * it instead restores every element to the position it had when this window opened.
 */
public class ObjectEditorFrame extends JDialog {

    private final PetriNetsPanel panel;
    private boolean saved;

    /**
     * @param owner window to centre on and block while this dialog is open
     * @param objectName the Petri-object's name, shown in the title
     * @param panel the panel to edit in — already carrying the object's own net
     */
    public ObjectEditorFrame(Window owner, String objectName, PetriNetsPanel panel) {
        super(owner, "Edit Petri-object — " + objectName, ModalityType.APPLICATION_MODAL);
        this.panel = panel;
        buildUi();
        pack();
        setLocationRelativeTo(owner);

        // Without this, keyboard focus on first showing the dialog defaults to the Save
        // button (the root pane's default button) rather than the canvas, so none of the
        // panel's own key bindings — Delete, Ctrl+A, Ctrl+D and the rest, the very same
        // KeyListener the main canvas uses — see a keystroke until the user happens to click
        // on the canvas first.
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowOpened(WindowEvent e) {
                panel.requestFocusInWindow();
            }
        });
    }

    /**
     * @return true if Save was pressed; false if the window was closed any other way (Cancel,
     *         the window's own close button), meaning the caller should discard what changed
     *         here rather than apply it
     */
    public boolean wasSaved() {
        return saved;
    }

    private void buildUi() {
        JToolBar toolBar = new JToolBar();
        toolBar.setFloatable(false);

        JButton placeButton = new JButton("Place");
        placeButton.setFocusable(false);
        placeButton.addActionListener(e -> addPlace());
        toolBar.add(placeButton);

        JButton transitionButton = new JButton("Transition");
        transitionButton.setFocusable(false);
        transitionButton.addActionListener(e -> addTransition());
        toolBar.add(transitionButton);

        JButton arcButton = new JButton("Arc");
        arcButton.setFocusable(false);
        arcButton.setToolTipText("Click a place or transition, then the element it connects to");
        arcButton.addActionListener(e -> panel.setIsSettingArc(true));
        toolBar.add(arcButton);

        JButton cancelButton = new JButton("Cancel");
        cancelButton.setToolTipText("Close without keeping anything changed here");
        cancelButton.addActionListener(e -> dispose());

        JButton saveButton = new JButton("Save");
        saveButton.setToolTipText("Apply what changed here to the Petri-object");
        saveButton.addActionListener(e -> {
            saved = true;
            dispose();
        });

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttons.add(cancelButton);
        buttons.add(saveButton);

        panel.setPreferredSize(new Dimension(
                Math.max(500, panel.getPreferredSize().width),
                Math.max(400, panel.getPreferredSize().height)));

        setLayout(new BorderLayout());
        add(toolBar, BorderLayout.NORTH);
        add(new JScrollPane(panel), BorderLayout.CENTER);
        add(buttons, BorderLayout.SOUTH);
        getRootPane().setDefaultButton(saveButton);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
    }

    /**
     * Adds a place the same way the main canvas's own toolbar does — {@code AddGraphElementEdit}
     * there, minus the undo/redo wiring this window has no use for, since Save/Cancel is
     * already its own coarser commit/discard. Setting it {@code current} is what matters: the
     * main canvas's own {@code mouseMoved}/{@code mousePressed} handling, shared by this panel
     * since it is the very same {@link PetriNetsPanel} class, is what then has a newly added
     * element follow the pointer until clicked into place — without it, the place would just
     * sit wherever a freshly constructed one starts out, unmoved and invisible under the header.
     */
    private void addPlace() {
        GraphPetriPlace place = new GraphPetriPlace(
                new PetriP(GraphPetriPlace.setSimpleName(), 0), GraphElementIdGenerator.next());
        panel.getGraphNet().getGraphPetriPlaceList().add(place);
        panel.setCurrent(place);
        panel.repaint();
    }

    /** @see #addPlace() */
    private void addTransition() {
        GraphPetriTransition transition = new GraphPetriTransition(
                new PetriT(GraphPetriTransition.setSimpleName(), 0.0), GraphElementIdGenerator.next());
        panel.getGraphNet().getGraphPetriTransitionList().add(transition);
        panel.setCurrent(transition);
        panel.repaint();
    }
}

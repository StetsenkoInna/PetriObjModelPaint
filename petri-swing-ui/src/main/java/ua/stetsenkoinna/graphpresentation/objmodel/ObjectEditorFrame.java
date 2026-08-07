package ua.stetsenkoinna.graphpresentation.objmodel;

import ua.stetsenkoinna.graphnet.GraphElementIdGenerator;
import ua.stetsenkoinna.graphnet.GraphPetriPlace;
import ua.stetsenkoinna.graphnet.GraphPetriTransition;
import ua.stetsenkoinna.graphpresentation.PetriNetsPanel;
import ua.stetsenkoinna.petriobj.PetriP;
import ua.stetsenkoinna.petriobj.PetriT;

import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JScrollPane;
import javax.swing.JToolBar;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Window;

/**
 * The window a Petri-object's own net is edited in.
 *
 * <p>Once an object exists, its elements are locked on the shared canvas — this is the only
 * place they can be moved, added, deleted or rewired. It hosts an ordinary
 * {@link PetriNetsPanel}, editable exactly like the canvas was before Petri-object composition
 * existed, showing just this object's own places, transitions and internal arcs; links to
 * other objects live outside it, on the frame's ports.
 *
 * <p>The panel it wraps operates on the very same element instances the main canvas holds
 * for this object, so a position or property change is already live the moment it happens —
 * nothing here needs an explicit save. What does need the caller's attention on close is
 * structural change (elements added or removed), since this dialog's net is a separate list
 * of those same instances: see {@code PetriNetsPanel.openObjectEditor}, which builds that list
 * and reconciles it against the main canvas once this window closes.
 */
public class ObjectEditorFrame extends JDialog {

    private final PetriNetsPanel panel;

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

        toolBar.addSeparator();
        toolBar.add(new JLabel(" Delete removes the selected element or arc "));

        JButton doneButton = new JButton("Done");
        doneButton.addActionListener(e -> dispose());

        panel.setPreferredSize(new Dimension(
                Math.max(500, panel.getPreferredSize().width),
                Math.max(400, panel.getPreferredSize().height)));

        setLayout(new BorderLayout());
        add(toolBar, BorderLayout.NORTH);
        add(new JScrollPane(panel), BorderLayout.CENTER);
        add(doneButton, BorderLayout.SOUTH);
        getRootPane().setDefaultButton(doneButton);
    }

    private void addPlace() {
        GraphPetriPlace place = new GraphPetriPlace(
                new PetriP(GraphPetriPlace.setSimpleName(), 0), GraphElementIdGenerator.next());
        panel.getGraphNet().getGraphPetriPlaceList().add(place);
        panel.repaint();
    }

    private void addTransition() {
        GraphPetriTransition transition = new GraphPetriTransition(
                new PetriT(GraphPetriTransition.setSimpleName(), 0.0), GraphElementIdGenerator.next());
        panel.getGraphNet().getGraphPetriTransitionList().add(transition);
        panel.repaint();
    }
}

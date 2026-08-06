package ua.stetsenkoinna.graphpresentation.objmodel;

import ua.stetsenkoinna.graphnet.GraphPetriObjModel;
import ua.stetsenkoinna.graphnet.GraphPetriObject;
import ua.stetsenkoinna.petriobj.PetriObjLink;
import ua.stetsenkoinna.petriobj.PetriObjLinkType;
import ua.stetsenkoinna.utils.MessageHelper;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.Window;

/**
 * Builds one link between two Petri-objects.
 *
 * <p>The type decides what the endpoints are, so the element lists follow the selected type:
 * a fusion joins two places, a transition-to-place link starts at a transition and ends at a
 * place, and a place-to-transition link is the other way round.
 */
public class LinkDialog extends JDialog {

    /** How the three link types are presented, in the order they appear in the list. */
    private static final LinkKind[] KINDS = {
            new LinkKind(PetriObjLinkType.PLACE_FUSION,
                    "Shared place — the two places become one"),
            new LinkKind(PetriObjLinkType.TRANSITION_TO_PLACE,
                    "Transition → place — the firing transition delivers tokens"),
            new LinkKind(PetriObjLinkType.PLACE_TO_TRANSITION,
                    "Place → transition — the place is an extra input of the transition")
    };

    private record LinkKind(PetriObjLinkType type, String label) {
        @Override
        public String toString() {
            return label;
        }
    }

    private final GraphPetriObjModel model;

    private final JComboBox<LinkKind> typeCombo = new JComboBox<>(KINDS);
    private final JComboBox<String> sourceObjectCombo = new JComboBox<>();
    private final JComboBox<String> sourceElementCombo = new JComboBox<>();
    private final JComboBox<String> targetObjectCombo = new JComboBox<>();
    private final JComboBox<String> targetElementCombo = new JComboBox<>();
    private final JSpinner quantitySpinner = new JSpinner(new SpinnerNumberModel(1, 1, 999, 1));
    private final JCheckBox informationalBox = new JCheckBox("Informational — test the marking without consuming it");

    private PetriObjLink created;

    public LinkDialog(Window owner, GraphPetriObjModel model) {
        super(owner, "Link Petri-objects", ModalityType.APPLICATION_MODAL);
        this.model = model;
        buildUi();
        pack();
        setLocationRelativeTo(owner);
    }

    /**
     * @return the link the user described, or {@code null} if the dialog was cancelled
     */
    public PetriObjLink getCreated() {
        return created;
    }

    private void buildUi() {
        for (int index = 0; index < model.getObjectCount(); index++) {
            String label = "O" + index + "  " + model.getObject(index).getName();
            sourceObjectCombo.addItem(label);
            targetObjectCombo.addItem(label);
        }
        if (model.getObjectCount() > 1) {
            targetObjectCombo.setSelectedIndex(1);
        }

        typeCombo.addActionListener(e -> refreshElements());
        sourceObjectCombo.addActionListener(e -> refreshElements());
        targetObjectCombo.addActionListener(e -> refreshElements());
        refreshElements();

        JPanel form = new JPanel(new GridLayout(0, 2, 6, 6));
        form.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        form.add(new JLabel("Kind of link"));
        form.add(typeCombo);
        form.add(new JLabel("From object"));
        form.add(sourceObjectCombo);
        form.add(new JLabel("From element"));
        form.add(sourceElementCombo);
        form.add(new JLabel("To object"));
        form.add(targetObjectCombo);
        form.add(new JLabel("To element"));
        form.add(targetElementCombo);
        form.add(new JLabel("Tokens per firing"));
        form.add(quantitySpinner);
        form.add(new JLabel());
        form.add(informationalBox);

        JButton ok = new JButton("Add link");
        ok.addActionListener(e -> onAdd());
        JButton cancel = new JButton("Cancel");
        cancel.addActionListener(e -> dispose());
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttons.add(ok);
        buttons.add(cancel);

        setLayout(new BorderLayout());
        add(form, BorderLayout.CENTER);
        add(buttons, BorderLayout.SOUTH);
        getRootPane().setDefaultButton(ok);
    }

    private PetriObjLinkType selectedType() {
        LinkKind kind = (LinkKind) typeCombo.getSelectedItem();
        return kind == null ? PetriObjLinkType.PLACE_FUSION : kind.type();
    }

    /**
     * Refills the element lists for the currently selected type and objects, and enables
     * only the settings that type actually has.
     */
    private void refreshElements() {
        PetriObjLinkType type = selectedType();
        boolean sourceIsPlace = type != PetriObjLinkType.TRANSITION_TO_PLACE;
        boolean targetIsPlace = type != PetriObjLinkType.PLACE_TO_TRANSITION;

        fill(sourceElementCombo, sourceObjectCombo.getSelectedIndex(), sourceIsPlace);
        fill(targetElementCombo, targetObjectCombo.getSelectedIndex(), targetIsPlace);

        boolean weighted = type != PetriObjLinkType.PLACE_FUSION;
        quantitySpinner.setEnabled(weighted);
        informationalBox.setEnabled(type == PetriObjLinkType.PLACE_TO_TRANSITION);
        if (!informationalBox.isEnabled()) {
            informationalBox.setSelected(false);
        }
    }

    private void fill(JComboBox<String> combo, int objectIndex, boolean places) {
        combo.removeAllItems();
        if (objectIndex < 0 || objectIndex >= model.getObjectCount()) {
            return;
        }
        GraphPetriObject object = model.getObject(objectIndex);
        int count = places ? object.getPlaceCount() : object.getTransitionCount();
        for (int index = 0; index < count; index++) {
            combo.addItem(index + ": " + (places ? object.getPlaceName(index) : object.getTransitionName(index)));
        }
    }

    private void onAdd() {
        int sourceObject = sourceObjectCombo.getSelectedIndex();
        int targetObject = targetObjectCombo.getSelectedIndex();
        int sourceElement = sourceElementCombo.getSelectedIndex();
        int targetElement = targetElementCombo.getSelectedIndex();
        if (sourceObject < 0 || targetObject < 0 || sourceElement < 0 || targetElement < 0) {
            MessageHelper.showError(this, "Both ends of the link have to be chosen");
            return;
        }
        int quantity = (Integer) quantitySpinner.getValue();
        PetriObjLink link = switch (selectedType()) {
            case PLACE_FUSION -> PetriObjLink.placeFusion(sourceObject, sourceElement, targetObject, targetElement);
            case TRANSITION_TO_PLACE -> PetriObjLink.transitionToPlace(
                    sourceObject, sourceElement, targetObject, targetElement, quantity);
            case PLACE_TO_TRANSITION -> PetriObjLink.placeToTransition(
                    sourceObject, sourceElement, targetObject, targetElement,
                    quantity, informationalBox.isSelected());
        };
        try {
            model.validate(link);
        } catch (IllegalArgumentException invalid) {
            MessageHelper.showError(this, invalid.getMessage());
            return;
        }
        created = link;
        dispose();
    }
}

package ua.stetsenkoinna.graphpresentation.objmodel;

import ua.stetsenkoinna.graphnet.GraphPetriNet;
import ua.stetsenkoinna.graphnet.GraphPetriObject;
import ua.stetsenkoinna.graphnet.NetTemplateRef;
import ua.stetsenkoinna.graphpresentation.SimpleNetGraphBuilder;
import ua.stetsenkoinna.libnet.NetTemplateCatalog;
import ua.stetsenkoinna.petriobj.PetriNet;
import ua.stetsenkoinna.utils.MessageHelper;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.Point;
import java.awt.Window;
import java.util.ArrayList;
import java.util.List;

/**
 * Asks where the net of a new Petri-object should come from.
 *
 * <p>Three sources, matching the ways a model is actually built: an instance of a net
 * library template with arguments of its own, a copy of the net currently on the canvas, or
 * an empty net to draw from scratch.
 */
public class AddObjectDialog extends JDialog {

    private static final int TEMPLATE_ARGUMENT_COLUMNS = 12;

    private final JTextField nameField = new JTextField(18);
    private final JSpinner prioritySpinner = new JSpinner(new SpinnerNumberModel(0, -99, 99, 1));

    private final JRadioButton fromTemplate = new JRadioButton("Instance of a net library template", true);
    private final JRadioButton fromCanvas = new JRadioButton("Copy of the net on the canvas");
    private final JRadioButton empty = new JRadioButton("Empty net");

    private final JComboBox<NetTemplateCatalog.Template> templateCombo = new JComboBox<>();
    private final JPanel argumentsPanel = new JPanel();
    private final List<JTextField> argumentFields = new ArrayList<>();

    private final GraphPetriNet canvasNet;
    private final Point layoutCentre;

    private GraphPetriObject created;

    /**
     * @param owner window the dialog belongs to
     * @param canvasNet the net currently open on the editor canvas, may be {@code null}
     * @param layoutCentre where to centre a net that has to be laid out from scratch
     * @param suggestedName the name to propose for the new object
     */
    public AddObjectDialog(Window owner, GraphPetriNet canvasNet, Point layoutCentre, String suggestedName) {
        super(owner, "Add Petri-object", ModalityType.APPLICATION_MODAL);
        this.canvasNet = canvasNet;
        this.layoutCentre = layoutCentre;
        nameField.setText(suggestedName);

        buildUi();
        pack();
        setLocationRelativeTo(owner);
    }

    /**
     * @return the object the user described, or {@code null} if the dialog was cancelled
     */
    public GraphPetriObject getCreated() {
        return created;
    }

    private void buildUi() {
        for (NetTemplateCatalog.Template template : NetTemplateCatalog.templates()) {
            templateCombo.addItem(template);
        }
        fromCanvas.setEnabled(canvasNet != null);
        if (templateCombo.getItemCount() == 0) {
            fromTemplate.setEnabled(false);
            if (canvasNet != null) {
                fromCanvas.setSelected(true);
            } else {
                empty.setSelected(true);
            }
        }

        ButtonGroup group = new ButtonGroup();
        group.add(fromTemplate);
        group.add(fromCanvas);
        group.add(empty);

        JPanel head = new JPanel(new GridLayout(2, 2, 6, 6));
        head.setBorder(BorderFactory.createEmptyBorder(10, 10, 4, 10));
        head.add(new JLabel("Object name"));
        head.add(nameField);
        head.add(new JLabel("Priority"));
        head.add(prioritySpinner);

        argumentsPanel.setLayout(new BoxLayout(argumentsPanel, BoxLayout.Y_AXIS));
        argumentsPanel.setBorder(BorderFactory.createEmptyBorder(4, 24, 4, 10));

        JPanel source = new JPanel();
        source.setLayout(new BoxLayout(source, BoxLayout.Y_AXIS));
        source.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createTitledBorder("Net of the object"),
                BorderFactory.createEmptyBorder(4, 8, 8, 8)));
        source.add(fromTemplate);
        JPanel templateRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));
        templateRow.add(Box.createHorizontalStrut(14));
        templateRow.add(templateCombo);
        source.add(templateRow);
        source.add(argumentsPanel);
        source.add(fromCanvas);
        source.add(empty);

        templateCombo.addActionListener(e -> rebuildArgumentFields());
        fromTemplate.addActionListener(e -> updateEnabledState());
        fromCanvas.addActionListener(e -> updateEnabledState());
        empty.addActionListener(e -> updateEnabledState());
        rebuildArgumentFields();
        updateEnabledState();

        JButton ok = new JButton("Add");
        ok.addActionListener(e -> onAdd());
        JButton cancel = new JButton("Cancel");
        cancel.addActionListener(e -> dispose());
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttons.add(ok);
        buttons.add(cancel);

        JPanel body = new JPanel(new BorderLayout());
        body.add(head, BorderLayout.NORTH);
        body.add(source, BorderLayout.CENTER);

        setLayout(new BorderLayout());
        JScrollPane scroll = new JScrollPane(body);
        scroll.setBorder(null);
        scroll.setPreferredSize(new Dimension(460, 340));
        add(scroll, BorderLayout.CENTER);
        add(buttons, BorderLayout.SOUTH);
        getRootPane().setDefaultButton(ok);
    }

    /**
     * Rebuilds one input field per parameter of the selected template.
     */
    private void rebuildArgumentFields() {
        argumentsPanel.removeAll();
        argumentFields.clear();
        NetTemplateCatalog.Template template = (NetTemplateCatalog.Template) templateCombo.getSelectedItem();
        if (template != null) {
            for (NetTemplateCatalog.TemplateParameter parameter : template.parameters()) {
                JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));
                row.add(new JLabel(parameter.name() + " (" + parameter.type().getSimpleName() + ")"));
                JTextField field = new JTextField(defaultValueFor(parameter.type()), TEMPLATE_ARGUMENT_COLUMNS);
                argumentFields.add(field);
                row.add(field);
                argumentsPanel.add(row);
            }
        }
        argumentsPanel.revalidate();
        argumentsPanel.repaint();
        pack();
    }

    /**
     * @return a value that is valid for the type, so the dialog is usable without editing
     *         every field
     */
    private String defaultValueFor(Class<?> type) {
        if (type == int.class || type == long.class) {
            return "1";
        }
        if (type == double.class || type == float.class) {
            return "1.0";
        }
        if (type == boolean.class) {
            return "false";
        }
        if (type == double[].class || type == int[].class) {
            return "1, 1";
        }
        return nameField.getText();
    }

    private void updateEnabledState() {
        boolean template = fromTemplate.isSelected();
        templateCombo.setEnabled(template);
        for (JTextField field : argumentFields) {
            field.setEnabled(template);
        }
    }

    private void onAdd() {
        String name = nameField.getText().trim();
        if (name.isEmpty()) {
            MessageHelper.showError(this, "The Petri-object needs a name");
            return;
        }
        try {
            GraphPetriNet net;
            NetTemplateRef template = null;
            if (fromTemplate.isSelected()) {
                NetTemplateCatalog.Template selected =
                        (NetTemplateCatalog.Template) templateCombo.getSelectedItem();
                if (selected == null) {
                    MessageHelper.showError(this, "No net library template is selected");
                    return;
                }
                List<String> arguments = new ArrayList<>();
                for (JTextField field : argumentFields) {
                    arguments.add(field.getText().trim());
                }
                PetriNet built = NetTemplateCatalog.instantiate(selected.name(), arguments);
                net = SimpleNetGraphBuilder.build(built, layoutCentre);
                template = new NetTemplateRef(selected.name(), arguments);
            } else if (fromCanvas.isSelected()) {
                net = new GraphPetriNet(canvasNet);
            } else {
                net = new GraphPetriNet();
            }
            created = new GraphPetriObject(name, net);
            created.setPriority((Integer) prioritySpinner.getValue());
            created.setTemplate(template);
            dispose();
        } catch (Exception failure) {
            MessageHelper.showException(this, "Cannot build the net of the Petri-object", failure);
        }
    }
}

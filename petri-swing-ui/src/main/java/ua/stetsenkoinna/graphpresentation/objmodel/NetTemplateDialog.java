package ua.stetsenkoinna.graphpresentation.objmodel;

import ua.stetsenkoinna.graphnet.NetTemplateRef;
import ua.stetsenkoinna.libnet.NetTemplateCatalog;
import ua.stetsenkoinna.petriobj.PetriNet;
import ua.stetsenkoinna.utils.MessageHelper;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.Window;
import java.util.ArrayList;
import java.util.List;

/**
 * Picks a net library template and the arguments to build it with.
 *
 * <p>Instantiating one template several times with different arguments is how a model gets
 * many similar Petri-objects — four service points that differ only in their service rate.
 */
public class NetTemplateDialog extends JDialog {

    private static final int ARGUMENT_COLUMNS = 12;

    private final JTextField nameField = new JTextField(18);
    private final JComboBox<NetTemplateCatalog.Template> templateCombo = new JComboBox<>();
    private final JPanel argumentsPanel = new JPanel();
    private final List<JTextField> argumentFields = new ArrayList<>();

    private PetriNet built;
    private NetTemplateRef reference;

    /**
     * @param owner window the dialog belongs to
     * @param suggestedName name to propose for the Petri-object
     */
    public NetTemplateDialog(Window owner, String suggestedName) {
        super(owner, "Petri-object from the net library", ModalityType.APPLICATION_MODAL);
        nameField.setText(suggestedName);
        buildUi();
        pack();
        setLocationRelativeTo(owner);
    }

    /**
     * @return the net the chosen template built, or {@code null} if the dialog was cancelled
     */
    public PetriNet getBuilt() {
        return built;
    }

    /**
     * @return which template and arguments the net came from, for the object to remember
     */
    public NetTemplateRef getReference() {
        return reference;
    }

    /**
     * @return the name the user gave the Petri-object
     */
    public String getObjectName() {
        return nameField.getText().trim();
    }

    private void buildUi() {
        for (NetTemplateCatalog.Template template : NetTemplateCatalog.templates()) {
            templateCombo.addItem(template);
        }
        templateCombo.addActionListener(e -> rebuildArgumentFields());

        JPanel head = new JPanel(new GridLayout(2, 2, 6, 6));
        head.setBorder(BorderFactory.createEmptyBorder(10, 10, 4, 10));
        head.add(new JLabel("Petri-object name"));
        head.add(nameField);
        head.add(new JLabel("Template"));
        head.add(templateCombo);

        argumentsPanel.setLayout(new BoxLayout(argumentsPanel, BoxLayout.Y_AXIS));
        argumentsPanel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createTitledBorder("Arguments"),
                BorderFactory.createEmptyBorder(4, 8, 8, 8)));
        rebuildArgumentFields();

        JButton ok = new JButton("Add");
        ok.addActionListener(e -> onAdd());
        JButton cancel = new JButton("Cancel");
        cancel.addActionListener(e -> dispose());
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttons.add(ok);
        buttons.add(cancel);

        JPanel body = new JPanel(new BorderLayout());
        body.add(head, BorderLayout.NORTH);
        body.add(argumentsPanel, BorderLayout.CENTER);

        setLayout(new BorderLayout());
        JScrollPane scroll = new JScrollPane(body);
        scroll.setBorder(null);
        scroll.setPreferredSize(new Dimension(440, 300));
        add(scroll, BorderLayout.CENTER);
        add(buttons, BorderLayout.SOUTH);
        getRootPane().setDefaultButton(ok);
    }

    private void rebuildArgumentFields() {
        argumentsPanel.removeAll();
        argumentFields.clear();
        NetTemplateCatalog.Template template = (NetTemplateCatalog.Template) templateCombo.getSelectedItem();
        if (template != null) {
            for (NetTemplateCatalog.TemplateParameter parameter : template.parameters()) {
                JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));
                row.add(new JLabel(parameter.name() + " (" + parameter.type().getSimpleName() + ")"));
                JTextField field = new JTextField(defaultValueFor(parameter.type()), ARGUMENT_COLUMNS);
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
     * @return a value valid for the type, so the dialog works without editing every field
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

    private void onAdd() {
        if (getObjectName().isEmpty()) {
            MessageHelper.showError(this, "The Petri-object needs a name");
            return;
        }
        NetTemplateCatalog.Template template = (NetTemplateCatalog.Template) templateCombo.getSelectedItem();
        if (template == null) {
            MessageHelper.showError(this, "The net library has no templates");
            return;
        }
        List<String> arguments = new ArrayList<>();
        for (JTextField field : argumentFields) {
            arguments.add(field.getText().trim());
        }
        try {
            built = NetTemplateCatalog.instantiate(template.name(), arguments);
            reference = new NetTemplateRef(template.name(), arguments);
            dispose();
        } catch (Exception failure) {
            MessageHelper.showException(this, "Cannot build the net from the template", failure);
        }
    }
}

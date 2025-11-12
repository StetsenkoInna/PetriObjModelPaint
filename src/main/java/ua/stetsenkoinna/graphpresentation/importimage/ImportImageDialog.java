package ua.stetsenkoinna.graphpresentation.importimage;

import ua.stetsenkoinna.utils.MessageHelper;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.io.File;

/**
 * Dialog for selecting an image (PNG/JPG), a YAML configuration file,
 * and the desired output format (pnml or petriobj) for recognition.
 *
 * @author Bohdan Hrontkovskyi
 * @since 12.11.2025
 */
public class ImportImageDialog extends javax.swing.JDialog {

    private final JTextField imageFileField;
    private final JTextField configFileField;
    private final JComboBox<String> requestedFileTypeCombo;

    private boolean confirmed = false;

    private File imageFile;
    private File configFile;
    private String requestedFileType;

    public ImportImageDialog(java.awt.Frame parent) {
        super(parent, "Import Image for Recognition", true);
        setLayout(new java.awt.BorderLayout(10, 10));
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);

        setSize(500, 210);
        setLocationRelativeTo(parent);

        imageFileField = new JTextField();
        configFileField = new JTextField();
        requestedFileTypeCombo = new JComboBox<>(new String[]{"pnml", "petriobj"});

        add(createFormPanel(), BorderLayout.CENTER);
        add(createButtonPanel(), BorderLayout.SOUTH);
    }

    private JPanel createFormPanel() {
        JPanel formPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(8, 8, 8, 8);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;

        // Browse buttons
        JButton browseImageButton = new JButton("Browse");
        browseImageButton.addActionListener(this::onBrowseImage);

        JButton browseConfigButton = new JButton("Browse");
        browseConfigButton.addActionListener(this::onBrowseConfig);

        // Create labels
        gbc.gridx = 0;
        gbc.gridy = 0;
        formPanel.add(new JLabel("Image File (.png, .jpg):"), gbc);
        gbc.gridx = 1;
        formPanel.add(imageFileField, gbc);
        gbc.gridx = 2;
        formPanel.add(browseImageButton, gbc);

        gbc.gridx = 0;
        gbc.gridy = 1;
        formPanel.add(new JLabel("YAML Config File (.yml, .yaml):"), gbc);
        gbc.gridx = 1;
        formPanel.add(configFileField, gbc);
        gbc.gridx = 2;
        formPanel.add(browseConfigButton, gbc);

        gbc.gridx = 0;
        gbc.gridy = 2;
        formPanel.add(new JLabel("Requested File Type:"), gbc);
        gbc.gridx = 1;
        gbc.gridwidth = 2;
        formPanel.add(requestedFileTypeCombo, gbc);

        return formPanel;
    }

    private JPanel createButtonPanel() {
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton okButton = new JButton("Import");
        JButton cancelButton = new JButton("Cancel");

        okButton.addActionListener(this::onConfirm);
        cancelButton.addActionListener(this::onCancel);

        buttonPanel.add(okButton);
        buttonPanel.add(cancelButton);
        return buttonPanel;
    }

    /**
     * Called when the user confirms import.
     */
    private void onConfirm(ActionEvent actionEvent) {
        if (imageFile == null || configFile == null) {
            MessageHelper.showWarning(this, "Please select both an image and YAML configuration file.");
            return;
        }
        requestedFileType = (String) requestedFileTypeCombo.getSelectedItem();
        confirmed = true;
        dispose();
    }

    /**
     * Called when the user cancels the dialog.
     */
    private void onCancel(ActionEvent actionEvent) {
        confirmed = false;
        dispose();
    }

    /**
     * Handles YAML file selection.
     */
    private void onBrowseConfig(ActionEvent actionEvent) {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Select YAML Configuration File");
        chooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("YAML Files (.yml, .yaml)", "yml", "yaml"));
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            configFile = chooser.getSelectedFile();
            configFileField.setText(configFile.getAbsolutePath());
        }
    }

    /**
     * Handles image file selection.
     */
    private void onBrowseImage(ActionEvent actionEvent) {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Select Image File");
        chooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("Image Files (.png, jpg)", "png", "jpg"));
        if(chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            imageFile = chooser.getSelectedFile();
            imageFileField.setText(imageFile.getAbsolutePath());
        }
    }

    public boolean isConfirmed() {
        return confirmed;
    }

    public File getImageFile() {
        return imageFile;
    }

    public File getConfigFile() {
        return configFile;
    }

    public String getRequestedFileType() {
        return requestedFileType;
    }
}
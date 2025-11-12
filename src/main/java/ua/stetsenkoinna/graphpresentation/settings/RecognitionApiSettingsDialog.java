package ua.stetsenkoinna.graphpresentation.settings;

import ua.stetsenkoinna.utils.MessageHelper;

import java.awt.event.ActionEvent;

public class RecognitionApiSettingsDialog extends javax.swing.JDialog {

    private final javax.swing.JTextField urlField;
    private final javax.swing.JPasswordField keyField;

    private boolean confirmed = false;

    public RecognitionApiSettingsDialog(javax.swing.JFrame parent, String initialApiUrl, String initialApiKey) {
        super(parent, "Recognition API Settings", true);

        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setLayout(new java.awt.BorderLayout(10, 10));
        setSize(420, 220);
        setLocationRelativeTo(parent);

        urlField = new javax.swing.JTextField(initialApiUrl != null ? initialApiUrl: "");
        keyField = new javax.swing.JPasswordField(initialApiKey != null ? initialApiKey: "");

        add(createFormPanel(), java.awt.BorderLayout.CENTER);
        add(createButtonPanel(), java.awt.BorderLayout.SOUTH);
    }

    private javax.swing.JPanel createFormPanel() {
        javax.swing.JPanel form = new javax.swing.JPanel(new java.awt.GridLayout(2, 2, 10, 10));
        form.setBorder(javax.swing.BorderFactory.createEmptyBorder(10, 10, 10, 10));

        form.add(new javax.swing.JLabel("API URI:"));
        form.add(urlField);

        form.add(new javax.swing.JLabel("ROBOFLOW API KEY:"));
        form.add(keyField);

        return form;
    }

    private javax.swing.JPanel createButtonPanel() {
        javax.swing.JPanel buttonPanel = new javax.swing.JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.RIGHT));

        // TODO: Add test connection button
        javax.swing.JButton saveButton = new javax.swing.JButton("Save");
        javax.swing.JButton cancelButton = new javax.swing.JButton("Cancel");

        saveButton.addActionListener(this::onSave);
        cancelButton.addActionListener(this::onCancel);

        buttonPanel.add(saveButton);
        buttonPanel.add(cancelButton);

        return buttonPanel;
    }

    private void onSave(ActionEvent actionEvent) {
       if (urlField.getText().trim().isEmpty() || keyField.getPassword().length == 0) {
           MessageHelper.showWarning(this, "Please fill in both the API URL and API Key fields.");
           return;
       }

       confirmed = true;
       dispose();
    }

    private void onCancel(ActionEvent actionEvent) {
        confirmed = false;
        dispose();
    }

    private void onTestConnection(ActionEvent actionEvent) {
        // TODO: implement test connection feature
    }

    public boolean isConfirmed() {
        return confirmed;
    }

    public String getApiUrl() {
        return urlField.getText().trim();
    }

    public String getApiKey() {
        return new String(keyField.getPassword()).trim();
    }
}
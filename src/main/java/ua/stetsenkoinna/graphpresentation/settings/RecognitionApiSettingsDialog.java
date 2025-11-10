package ua.stetsenkoinna.graphpresentation.settings;

public class RecognitionApiSettingsDialog extends javax.swing.JDialog {
    private final javax.swing.JTextField urlField;
    private final javax.swing.JPasswordField keyField;
    private final RecognitionApiSettingsManager settingsManager;

    public RecognitionApiSettingsDialog(javax.swing.JFrame parent, RecognitionApiSettingsManager settingsManager) {
        super(parent, "Recognition API Settings", true);
        this.settingsManager = settingsManager;

        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setLayout(new java.awt.BorderLayout(10, 10));
        setSize(420, 220);
        setLocationRelativeTo(parent);

        urlField = new javax.swing.JTextField(settingsManager.getApiUrl());
        keyField = new javax.swing.JPasswordField(settingsManager.getApiKey());

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

        saveButton.addActionListener(e -> onSave());
        cancelButton.addActionListener(e -> dispose());

        buttonPanel.add(saveButton);
        buttonPanel.add(cancelButton);

        return buttonPanel;
    }

    private void onSave() {
        settingsManager.setApiUrl(urlField.getText().trim());
        settingsManager.setApiKey(new String(keyField.getPassword()).trim());
        settingsManager.save();

        javax.swing.JOptionPane.showMessageDialog(
                this,
                "API settings saved successfully.",
                "Settings Updated",
                javax.swing.JOptionPane.INFORMATION_MESSAGE
        );
        dispose();
    }
}

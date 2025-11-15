package ua.stetsenkoinna.graphpresentation.settings;

import ua.stetsenkoinna.recognition.RecognitionApiClient;
import ua.stetsenkoinna.utils.MessageHelper;

import javax.swing.*;
import java.awt.event.ActionEvent;

/**
 * A modal dialog that allows the user to configure settings required for connecting
 * to an external Recognition API. The dialog
 * collects two pieces of information:
 *
 * <ul>
 *     <li><b>API URI</b> – the base URL of the recognition service</li>
 *     <li><b>API Key</b> – the authentication token required for access to Roboflow API</li>
 * </ul>
 *
 * <p>The dialog provides "Save" and "Cancel" buttons. If the user clicks “Save”
 * and both fields are filled, the dialog closes and {@link #isConfirmed()} returns
 * {@code true}. Otherwise, if cancelled or validation fails, no changes are confirmed.
 *
 * <p>Example usage:
 * <pre>
 * RecognitionApiSettingsDialog dialog =
 *     new RecognitionApiSettingsDialog(parentFrame, currentUrl, currentKey);
 * dialog.setVisible(true);
 * if (dialog.isConfirmed()) {
 *     String url = dialog.getApiUrl();
 *     String key = dialog.getApiKey();
 *     // Save settings...
 * }
 * </pre>
 *
 * @author  Bohdan Hrontkovskyi
 * @since   14.11.2025
 */
public class RecognitionApiSettingsDialog extends javax.swing.JDialog {

    private final javax.swing.JTextField urlField;
    private final javax.swing.JPasswordField keyField;

    private boolean confirmed = false;

    /**
     * Creates a new settings dialog with pre-filled API URL and API Key.
     *
     * @param parent         the parent frame for positioning this dialog
     * @param initialApiUrl  a previously saved API endpoint, or {@code null}
     * @param initialApiKey  a previously saved API key, or {@code null}
     */
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

    /**
     * Builds the main form containing labels and input fields for API URL and Roboflow Key.
     *
     * @return a JPanel configured for the dialog form
     */
    private javax.swing.JPanel createFormPanel() {
        javax.swing.JPanel form = new javax.swing.JPanel(new java.awt.GridLayout(2, 2, 10, 10));
        form.setBorder(javax.swing.BorderFactory.createEmptyBorder(10, 10, 10, 10));

        form.add(new javax.swing.JLabel("API URI:"));
        form.add(urlField);

        form.add(new javax.swing.JLabel("ROBOFLOW API KEY:"));
        form.add(keyField);

        return form;
    }

    /**
     * Builds the button panel containing "Save" and "Cancel" buttons.
     *
     * @return a JPanel containing action buttons
     */
    private javax.swing.JPanel createButtonPanel() {
        javax.swing.JPanel buttonPanel = new javax.swing.JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.RIGHT));

        javax.swing.JButton saveButton = new javax.swing.JButton("Save");
        javax.swing.JButton cancelButton = new javax.swing.JButton("Cancel");
        javax.swing.JButton testConnectionButton = new javax.swing.JButton("Test Connection");

        saveButton.addActionListener(this::onSave);
        cancelButton.addActionListener(this::onCancel);
        testConnectionButton.addActionListener(this::onTestConnection);

        buttonPanel.add(saveButton);
        buttonPanel.add(cancelButton);
        buttonPanel.add(testConnectionButton);

        return buttonPanel;
    }

    /**
     * Validates input fields and closes the dialog if valid.
     *
     * @param actionEvent the triggered event
     */
    private void onSave(ActionEvent actionEvent) {
       if (urlField.getText().trim().isEmpty() || keyField.getPassword().length == 0) {
           MessageHelper.showWarning(this, "Please fill in both the API URL and API Key fields.");
           return;
       }

       confirmed = true;
       dispose();
    }

    /**
     * Cancels the dialog, discarding any values entered.
     *
     * @param actionEvent the triggered event
     */
    private void onCancel(ActionEvent actionEvent) {
        confirmed = false;
        dispose();
    }

    /**
     * Handles the "Test Connection" button click in the Recognition API settings dialog.
     *
     * <p>This method reads the API URL and Roboflow API key from the text fields,
     * validates that both are provided, and then asynchronously tests the connection
     * to the Recognition API using a {@link SwingWorker}. The UI remains responsive
     * during the check.
     *
     * <p>If the connection succeeds, an informational message is displayed. If it fails,
     * an error message is shown. Any unexpected exceptions are also caught and displayed.
     *
     * @param actionEvent the triggered event
     */
    private void onTestConnection(ActionEvent actionEvent) {
        String apiUrl = urlField.getText().trim();
        String apiKey = new String(keyField.getPassword()).trim();

        if (apiUrl.isEmpty() || apiKey.isEmpty()) {
            MessageHelper.showWarning(this, "Please fill in both the API URL and API Key fields.");
            return;
        }

        new SwingWorker<Boolean, Void>() {
            @Override
            protected Boolean doInBackground() {
                try {
                    RecognitionApiClient client = new RecognitionApiClient(apiUrl, apiKey);
                    client.ping();
                    return true;
                } catch (Exception ex) {
                    return false;
                }
            }

            @Override
            protected void done() {
                try {
                    boolean success = get();
                    if (success) {
                        MessageHelper.showInfo(RecognitionApiSettingsDialog.this, "Connection successful!");
                    } else {
                        MessageHelper.showError(RecognitionApiSettingsDialog.this, "Cannot connect to Recognition API. Check URL and Roboflow API Key.");
                    }
                } catch (Exception ex) {
                    MessageHelper.showException(RecognitionApiSettingsDialog.this, "Error testing connection", ex);
                }
            }
        }.execute();
    }

    /**
     * Returns whether the dialog was confirmed by the user.
     *
     * @return {@code true} if the user clicked "Save"; {@code false} if cancelled
     */
    public boolean isConfirmed() {
        return confirmed;
    }

    /**
     * Returns the API URL entered in the dialog.
     *
     * @return a trimmed string containing the API endpoint
     */
    public String getApiUrl() {
        return urlField.getText().trim();
    }

    /**
     * Returns the API Key entered in the dialog.
     *
     * @return a trimmed string containing the API authentication key
     */
    public String getApiKey() {
        return new String(keyField.getPassword()).trim();
    }
}
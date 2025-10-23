package ua.stetsenkoinna.utils;

import javax.swing.*;
import java.awt.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Utility for displaying messages to users via dialog windows.
 * Replaces System.out.println() and ensures error and notification visibility.
 *
 * @author Serhii Rybak
 */
public class MessageHelper {

    private static final Logger LOGGER = Logger.getLogger(MessageHelper.class.getName());

    // Default parent component (can be set to the application's main window)
    private static Component defaultParent = null;

    /**
     * Sets the default parent component for dialogs.
     * It's recommended to set the application's main window.
     *
     * @param parent the parent component
     */
    public static void setDefaultParent(Component parent) {
        defaultParent = parent;
    }

    /**
     * Displays an informational message to the user.
     *
     * @param message the message text
     */
    public static void showInfo(String message) {
        showInfo(null, message);
    }

    /**
     * Displays an informational message to the user.
     *
     * @param parent the parent component (or null to use defaultParent)
     * @param message the message text
     */
    public static void showInfo(Component parent, String message) {
        LOGGER.log(Level.INFO, message);
        Component parentComponent = parent != null ? parent : defaultParent;
        JOptionPane.showMessageDialog(parentComponent, message, "Information", JOptionPane.INFORMATION_MESSAGE);
    }

    /**
     * Displays a warning to the user.
     *
     * @param message the warning text
     */
    public static void showWarning(String message) {
        showWarning(null, message);
    }

    /**
     * Displays a warning to the user.
     *
     * @param parent the parent component (or null to use defaultParent)
     * @param message the warning text
     */
    public static void showWarning(Component parent, String message) {
        LOGGER.log(Level.WARNING, message);
        Component parentComponent = parent != null ? parent : defaultParent;
        JOptionPane.showMessageDialog(parentComponent, message, "Warning", JOptionPane.WARNING_MESSAGE);
    }

    /**
     * Displays an error message to the user.
     *
     * @param message the error text
     */
    public static void showError(String message) {
        showError(null, message);
    }

    /**
     * Displays an error message to the user.
     *
     * @param parent the parent component (or null to use defaultParent)
     * @param message the error text
     */
    public static void showError(Component parent, String message) {
        LOGGER.log(Level.SEVERE, message);
        Component parentComponent = parent != null ? parent : defaultParent;
        JOptionPane.showMessageDialog(parentComponent, message, "Error", JOptionPane.ERROR_MESSAGE);
    }

    /**
     * Displays detailed exception information to the user.
     *
     * @param message the error description
     * @param ex the exception
     */
    public static void showException(String message, Exception ex) {
        showException(null, message, ex);
    }

    /**
     * Displays detailed exception information to the user.
     *
     * @param parent the parent component (or null to use defaultParent)
     * @param message the error description
     * @param ex the exception
     */
    public static void showException(Component parent, String message, Exception ex) {
        LOGGER.log(Level.SEVERE, message, ex);
        Component parentComponent = parent != null ? parent : defaultParent;

        String fullMessage = message + "\n\nDetails: " + ex.getClass().getSimpleName() +
                           (ex.getMessage() != null ? "\n" + ex.getMessage() : "");

        JOptionPane.showMessageDialog(parentComponent, fullMessage, "Error", JOptionPane.ERROR_MESSAGE);
    }

    /**
     * Displays detailed exception information with the ability to show stack trace.
     *
     * @param message the error description
     * @param ex the exception
     */
    public static void showDetailedException(String message, Exception ex) {
        showDetailedException(null, message, ex);
    }

    /**
     * Displays detailed exception information with the ability to show stack trace.
     *
     * @param parent the parent component (or null to use defaultParent)
     * @param message the error description
     * @param ex the exception
     */
    public static void showDetailedException(Component parent, String message, Exception ex) {
        LOGGER.log(Level.SEVERE, message, ex);
        Component parentComponent = parent != null ? parent : defaultParent;

        // Create a panel with the main message and "Show Details" button
        JPanel panel = new JPanel(new BorderLayout(5, 5));

        String shortMessage = message + "\n\n" + ex.getClass().getSimpleName() +
                            (ex.getMessage() != null ? ": " + ex.getMessage() : "");
        JTextArea messageArea = new JTextArea(shortMessage);
        messageArea.setEditable(false);
        messageArea.setBackground(panel.getBackground());
        messageArea.setLineWrap(true);
        messageArea.setWrapStyleWord(true);
        panel.add(messageArea, BorderLayout.CENTER);

        // Button to show stack trace
        JButton detailsButton = new JButton("Show Stack Trace");
        JTextArea stackTraceArea = new JTextArea(10, 50);
        stackTraceArea.setEditable(false);
        StringBuilder stackTrace = new StringBuilder();
        for (StackTraceElement element : ex.getStackTrace()) {
            stackTrace.append(element.toString()).append("\n");
        }
        stackTraceArea.setText(stackTrace.toString());
        stackTraceArea.setCaretPosition(0);
        JScrollPane scrollPane = new JScrollPane(stackTraceArea);
        scrollPane.setVisible(false);

        detailsButton.addActionListener(e -> {
            scrollPane.setVisible(!scrollPane.isVisible());
            detailsButton.setText(scrollPane.isVisible() ? "Hide Stack Trace" : "Show Stack Trace");
            Window window = SwingUtilities.getWindowAncestor(panel);
            if (window != null) {
                window.pack();
            }
        });

        JPanel buttonPanel = new JPanel();
        buttonPanel.add(detailsButton);
        panel.add(buttonPanel, BorderLayout.SOUTH);
        panel.add(scrollPane, BorderLayout.EAST);

        JOptionPane.showMessageDialog(parentComponent, panel, "Error", JOptionPane.ERROR_MESSAGE);
    }

    /**
     * Displays a confirmation dialog.
     *
     * @param message the question text
     * @return true if the user selected "Yes", false otherwise
     */
    public static boolean showConfirmation(String message) {
        return showConfirmation(null, message);
    }

    /**
     * Displays a confirmation dialog.
     *
     * @param parent the parent component (or null to use defaultParent)
     * @param message the question text
     * @return true if the user selected "Yes", false otherwise
     */
    public static boolean showConfirmation(Component parent, String message) {
        Component parentComponent = parent != null ? parent : defaultParent;
        int result = JOptionPane.showConfirmDialog(parentComponent, message, "Confirmation",
                                                   JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);
        return result == JOptionPane.YES_OPTION;
    }

    /**
     * Displays a dialog with three answer options (Yes/No/Cancel).
     *
     * @param message the question text
     * @return JOptionPane.YES_OPTION, JOptionPane.NO_OPTION, or JOptionPane.CANCEL_OPTION
     */
    public static int showYesNoCancelDialog(String message) {
        return showYesNoCancelDialog(null, message);
    }

    /**
     * Displays a dialog with three answer options (Yes/No/Cancel).
     *
     * @param parent the parent component (or null to use defaultParent)
     * @param message the question text
     * @return JOptionPane.YES_OPTION, JOptionPane.NO_OPTION, or JOptionPane.CANCEL_OPTION
     */
    public static int showYesNoCancelDialog(Component parent, String message) {
        Component parentComponent = parent != null ? parent : defaultParent;
        return JOptionPane.showConfirmDialog(parentComponent, message, "Confirmation",
                                            JOptionPane.YES_NO_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE);
    }

    /**
     * Displays a text input dialog.
     *
     * @param message the prompt text
     * @return the entered text or null if the user cancelled
     */
    public static String showInputDialog(String message) {
        return showInputDialog(null, message, "");
    }

    /**
     * Displays a text input dialog with an initial value.
     *
     * @param parent the parent component (or null to use defaultParent)
     * @param message the prompt text
     * @param initialValue the initial value
     * @return the entered text or null if the user cancelled
     */
    public static String showInputDialog(Component parent, String message, String initialValue) {
        Component parentComponent = parent != null ? parent : defaultParent;
        return (String) JOptionPane.showInputDialog(parentComponent, message, "Input",
                                                    JOptionPane.QUESTION_MESSAGE, null, null, initialValue);
    }
}

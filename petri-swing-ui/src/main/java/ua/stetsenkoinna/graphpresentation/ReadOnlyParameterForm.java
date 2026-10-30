package ua.stetsenkoinna.graphpresentation;

import java.awt.*;
import javax.swing.*;
import javax.swing.text.JTextComponent;

/** Reuse the ordinary parameter forms without offering writes to a saved revision. */
final class ReadOnlyParameterForm {
    private ReadOnlyParameterForm() { }
    static void apply(Container container, JButton save, JButton close) {
        freeze(container); save.setVisible(false); close.setText("Close");
    }
    private static void freeze(Container parent) {
        for (Component component : parent.getComponents()) {
            if (component instanceof JTextComponent text) text.setEditable(false);
            if (component instanceof JComboBox<?> || component instanceof JSpinner || component instanceof JToggleButton) component.setEnabled(false);
            if (component instanceof Container child) freeze(child);
        }
    }
}

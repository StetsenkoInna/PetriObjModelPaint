package ua.stetsenkoinna.graphpresentation.objmodel;

import ua.stetsenkoinna.utils.MessageHelper;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dialog;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Frame;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ScrollPaneConstants;

/**
 * Chooses which Petri-object templates appear on the left toolbar.
 *
 * <p>Everything the net library offers is listed with a tick box; ticking one puts it on the
 * toolbar, unticking takes it off. Nothing is applied until OK, so a half-made selection never
 * reaches the toolbar.
 */
public class PetriObjectManagerDialog extends JDialog {

    private final PetriObjectPalette palette;
    /** Keyed by template id rather than positional, so the two sections cannot fall out of
     *  step with the tick boxes the way parallel lists would. */
    private final Map<String, JCheckBox> boxes = new LinkedHashMap<>();
    private final List<PetriObjectTemplate> templates;
    private boolean changed;

    public PetriObjectManagerDialog(Frame owner, PetriObjectPalette palette) {
        super(owner, "Petri-objects", Dialog.ModalityType.APPLICATION_MODAL);
        this.palette = palette;
        this.templates = palette.available();

        JPanel list = new JPanel();
        list.setLayout(new BoxLayout(list, BoxLayout.Y_AXIS));
        list.setBorder(BorderFactory.createEmptyBorder(8, 12, 8, 12));

        list.add(sectionLabel("Built-in Petri-objects"));
        for (PetriObjectTemplate template : templates) {
            if (template.kind() != PetriObjectTemplate.Kind.LIBRARY) {
                continue;
            }
            JCheckBox box = new JCheckBox(template.displayName(), palette.isPinned(template.id()));
            box.setAlignmentX(Component.LEFT_ALIGNMENT);
            // The signature is what tells two similarly-named templates apart, but it is noise
            // next to the name, so it lives in the tooltip rather than the label.
            box.setToolTipText(template.methodName() + "(" + String.join(", ", template.arguments()) + ")");
            boxes.put(template.id(), box);
            list.add(box);
        }

        list.add(sectionLabel("Saved Petri-objects"));
        boolean anyCustom = false;
        for (PetriObjectTemplate template : templates) {
            if (template.kind() != PetriObjectTemplate.Kind.PROTOTYPE) {
                continue;
            }
            anyCustom = true;
            list.add(customRow(template));
        }
        if (!anyCustom) {
            JLabel empty = new JLabel("Right-click a Petri-object on the canvas to save one here.");
            empty.setAlignmentX(Component.LEFT_ALIGNMENT);
            empty.setFont(empty.getFont().deriveFont(Font.ITALIC, 11f));
            list.add(empty);
        }

        JScrollPane scroll = new JScrollPane(list,
                ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setBorder(null);

        JLabel hint = new JLabel("Ticked Petri-objects appear on the left toolbar.");
        hint.setBorder(BorderFactory.createEmptyBorder(8, 12, 0, 12));
        hint.setFont(hint.getFont().deriveFont(Font.PLAIN, 11f));

        JButton ok = new JButton("OK");
        ok.addActionListener(e -> {
            apply();
            dispose();
        });
        JButton cancel = new JButton("Cancel");
        cancel.addActionListener(e -> dispose());

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 8));
        buttons.add(cancel);
        buttons.add(ok);

        JPanel content = new JPanel(new BorderLayout());
        content.add(hint, BorderLayout.NORTH);
        content.add(scroll, BorderLayout.CENTER);
        content.add(buttons, BorderLayout.SOUTH);
        setContentPane(content);

        getRootPane().setDefaultButton(ok);
        setSize(new Dimension(360, 420));
        setLocationRelativeTo(owner);
    }

    private static Component sectionLabel(String text) {
        JLabel label = new JLabel(text);
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        label.setFont(label.getFont().deriveFont(Font.BOLD));
        label.setBorder(BorderFactory.createEmptyBorder(4, 0, 4, 0));
        return label;
    }

    /**
     * One saved object: its tick box, plus the only way to get rid of it again. Deleting is
     * offered here rather than on the canvas because this window is the only place a saved
     * object is visible at all once it has been stamped.
     */
    private Component customRow(PetriObjectTemplate template) {
        JCheckBox box = new JCheckBox(template.displayName(), palette.isPinned(template.id()));
        box.setToolTipText(String.valueOf(template.prototypeFile()));
        boxes.put(template.id(), box);

        JButton delete = new JButton("Delete");
        delete.setFont(delete.getFont().deriveFont(Font.PLAIN, 11f));
        delete.addActionListener(e -> deleteCustom(template));

        JPanel row = new JPanel(new BorderLayout());
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setOpaque(false);
        row.add(box, BorderLayout.CENTER);
        row.add(delete, BorderLayout.EAST);
        // Without this a BoxLayout row stretches to the tallest thing it could ever be.
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, delete.getPreferredSize().height + 4));
        return row;
    }

    private void deleteCustom(PetriObjectTemplate template) {
        if (!MessageHelper.showConfirmation(this,
                "Delete the saved Petri-object '" + template.displayName() + "'?")) {
            return;
        }
        try {
            palette.deleteCustom(template);
            // The toolbar may have just lost a button, so the caller has to rebuild it whatever
            // else happens — even if the window is then cancelled.
            changed = true;
            rebuild();
        } catch (IOException failure) {
            MessageHelper.showException(this, "Cannot delete this Petri-object", failure);
        }
    }

    /** Reopens this window's content against the palette as it now stands. */
    private void rebuild() {
        dispose();
        PetriObjectManagerDialog replacement =
                new PetriObjectManagerDialog((Frame) getOwner(), palette);
        replacement.changed = true;
        replacement.setVisible(true);
    }

    private void apply() {
        List<String> pinned = new ArrayList<>();
        for (PetriObjectTemplate template : templates) {
            JCheckBox box = boxes.get(template.id());
            if (box != null && box.isSelected()) {
                pinned.add(template.id());
            }
        }
        palette.setPinned(pinned);
        changed = true;
    }

    /**
     * @return true when the selection was confirmed, i.e. the toolbar needs rebuilding
     */
    public boolean isChanged() {
        return changed;
    }
}

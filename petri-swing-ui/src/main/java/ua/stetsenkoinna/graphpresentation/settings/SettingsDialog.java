package ua.stetsenkoinna.graphpresentation.settings;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Window;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import ua.stetsenkoinna.config.AppSettings;
import ua.stetsenkoinna.graphpresentation.theme.ThemeManager;
import ua.stetsenkoinna.theme.ThemeMode;

/**
 * The application's settings, and - on a first start - the dialog that introduces them.
 *
 * <p>One dialog serves both jobs, because they are the same list of choices seen at two moments;
 * only the framing differs, which is what {@link Mode} selects. Splitting them would mean two
 * layouts to keep in step for every setting added from here on.
 *
 * <h3>Live preview</h3>
 * Picking a theme applies it at once, before anything is confirmed, so the choice is judged by
 * looking at the application rather than by reading the word "Dark". {@code Cancel} puts back
 * whatever was in force when the dialog opened - which is why the entry mode is captured up
 * front rather than re-read on the way out.
 *
 * <h3>Adding the second setting</h3>
 * Sections are stacked in {@link #buildSections()}, each built by its own method returning a
 * component. A new setting is a new method and one more line there; nothing about the surrounding
 * dialog needs to know how many there are.
 */
public class SettingsDialog extends JDialog {

    /** Which of the dialog's two jobs this instance is doing. */
    public enum Mode {
        /** Shown once, before the main window, on a first start. */
        FIRST_RUN,
        /** Opened from the menu at any time afterwards. */
        PREFERENCES
    }

    private final AppSettings settings;
    private final Mode mode;

    /** The theme in force when the dialog opened, restored if the user cancels. */
    private final ThemeMode modeOnEntry;

    private final Map<ThemeMode, JRadioButton> themeButtons = new EnumMap<>(ThemeMode.class);

    /**
     * @param owner the window to centre on, or null on a first start, when there is not one yet
     * @param settings the settings to read and write
     * @param mode which of the two jobs this dialog is doing
     */
    public SettingsDialog(Window owner, AppSettings settings, Mode mode) {
        super(owner, mode == Mode.FIRST_RUN ? "Welcome to petri-net-sim" : "Preferences",
                ModalityType.APPLICATION_MODAL);
        this.settings = settings;
        this.mode = mode;
        this.modeOnEntry = ThemeManager.currentMode();

        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        addWindowListener(new java.awt.event.WindowAdapter() {
            /**
             * Closing from the window's own button means Cancel in the settings, and Continue on
             * a first start - there is nothing to cancel back to the first time, and a user who
             * has just watched the theme change and then closed the window has chosen it.
             */
            @Override
            public void windowClosing(java.awt.event.WindowEvent event) {
                close(SettingsDialog.this.mode == Mode.FIRST_RUN);
            }
        });
        setLayout(new BorderLayout());
        add(buildHeader(), BorderLayout.NORTH);
        add(buildSections(), BorderLayout.CENTER);
        add(buildButtons(), BorderLayout.SOUTH);

        selectButtonFor(ThemeManager.currentMode());
        pack();
        setLocationRelativeTo(owner);
    }

    /**
     * Shows the first-start dialog if it has never been answered, and records that it has.
     *
     * <p>Called before the main window exists, so that the very first frame the user sees is
     * already in the theme they chose rather than repainting itself a moment later.
     *
     * @param settings the settings to consult and update
     */
    public static void showIfFirstRun(AppSettings settings) {
        if (settings.isInitialSetupCompleted()) {
            return;
        }
        SettingsDialog dialog = new SettingsDialog(null, settings, Mode.FIRST_RUN);
        dialog.setVisible(true);
        // Marked after the dialog closes rather than on the chosen action, so that a user who
        // dismisses it from the window's own close button is still not asked again - they were
        // asked, and the default stands.
        settings.markInitialSetupCompleted();
    }

    /**
     * Opens the settings from the menu.
     *
     * @param owner the main window, so the dialog is centred on it and blocks it
     */
    public static void showPreferences(Window owner, AppSettings settings) {
        new SettingsDialog(owner, settings, Mode.PREFERENCES).setVisible(true);
    }

    /**
     * The introduction: one plain label per line, and no HTML anywhere.
     *
     * <p>HTML in a {@code JLabel} was tried three ways here and misbehaved every time. A CSS
     * width on {@code <body>} is ignored when the label is measured, so the sentence decides how
     * wide the window is instead of the other way round. A single-cell table is measured
     * correctly, but once the label is allocated slightly more width than the cell it paints an
     * overflowing run on top of the one before it rather than moving it down a line. And even
     * with the breaks written out by hand, a {@code <b>} run lands on top of the plain text
     * before it - the same measured-narrower-than-painted mismatch that clips a radio button's
     * last glyph, see {@link #fullWidthRow}, except that here there is no outer component whose
     * width could absorb it.
     *
     * <p>Plain labels have none of that: each line is one run, measured and painted by itself.
     * The bold emphasis is not worth a sentence that can render on top of itself.
     */
    private JComponent buildHeader() {
        JPanel header = new JPanel();
        header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));
        header.setBorder(BorderFactory.createEmptyBorder(14, 16, 6, 16));
        List<String> lines = mode == Mode.FIRST_RUN
                ? List.of("Let's set the editor up.",
                          "You can change any of this later from Edit > Preferences.")
                : List.of("These settings apply to the whole application",
                          "and are remembered between sessions.");
        for (String line : lines) {
            header.add(fullWidthRow(new JLabel(line)));
        }
        return header;
    }

    /**
     * The dialog's settings, stacked. One section per topic, each built by its own method - so
     * the second setting is a new method and one more line here, and nothing else changes.
     */
    private JComponent buildSections() {
        JPanel sections = new JPanel();
        sections.setLayout(new BoxLayout(sections, BoxLayout.Y_AXIS));
        sections.setBorder(BorderFactory.createEmptyBorder(0, 16, 8, 16));
        sections.add(buildThemeSection());
        return sections;
    }

    private JComponent buildThemeSection() {
        JPanel section = new JPanel();
        section.setLayout(new BoxLayout(section, BoxLayout.Y_AXIS));
        section.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createTitledBorder("Appearance"),
                BorderFactory.createEmptyBorder(4, 8, 8, 8)));
        section.setAlignmentX(Component.LEFT_ALIGNMENT);

        ButtonGroup group = new ButtonGroup();
        for (ThemeMode themeMode : ThemeMode.values()) {
            JRadioButton button = new JRadioButton(themeMode.getDisplayName());
            // Applied, not just recorded: the point of choosing here is to see the result.
            button.addActionListener(event -> ThemeManager.applyMode(themeMode));
            group.add(button);
            themeButtons.put(themeMode, button);
            section.add(fullWidthRow(button));
            section.add(fullWidthRow(describe(hintFor(themeMode))));
        }
        return section;
    }

    /**
     * Wraps a control so it is laid out at the section's full width instead of at its own
     * preferred width.
     *
     * <p>Not cosmetic. Left to itself, {@code BoxLayout} sizes each child to its preferred width,
     * and Nimbus measures a button or label a hair narrower than it then paints it - enough to
     * lose the last glyph of a label, with no ellipsis to show that anything was dropped, so
     * "Light" reads as "Ligh". Handing the control a whole row's worth of width means the
     * measurement never has to be exactly right.
     */
    private static JComponent fullWidthRow(JComponent content) {
        JPanel row = new JPanel(new BorderLayout());
        row.add(content, BorderLayout.CENTER);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, row.getPreferredSize().height));
        return row;
    }

    private static String hintFor(ThemeMode themeMode) {
        return switch (themeMode) {
            case SYSTEM -> "Follow whatever your desktop is currently set to.";
            case LIGHT -> "The editor's original light appearance.";
            case DARK -> "A dark window and canvas, for low-light work.";
        };
    }

    /**
     * @return the small grey line of explanation that sits under a radio button, indented to
     *         line up with its label rather than with its box
     */
    private static JComponent describe(String text) {
        JLabel label = new JLabel(text);
        label.setFont(label.getFont().deriveFont(Font.PLAIN, label.getFont().getSize2D() - 1f));
        label.setEnabled(false);
        label.setBorder(BorderFactory.createEmptyBorder(0, 22, 6, 0));
        return label;
    }

    private JComponent buildButtons() {
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 8));

        if (mode == Mode.PREFERENCES) {
            JButton cancel = new JButton("Cancel");
            cancel.addActionListener(event -> close(false));
            buttons.add(cancel);
        }

        // "Continue" on a first start, where there is nothing yet to be OK about, and the
        // window it belongs to is an introduction rather than an editor of existing settings.
        JButton confirm = new JButton(mode == Mode.FIRST_RUN ? "Continue" : "OK");
        confirm.addActionListener(event -> close(true));
        buttons.add(confirm);
        getRootPane().setDefaultButton(confirm);
        return buttons;
    }

    /**
     * The one way out, so that confirming, cancelling and closing the window cannot drift apart.
     *
     * @param keepSelection true to persist what is selected, false to put back the theme that
     *        was in force when the dialog opened - which the live preview has since replaced
     */
    private void close(boolean keepSelection) {
        if (keepSelection) {
            settings.setThemeMode(selectedThemeMode());
        } else {
            ThemeManager.applyMode(modeOnEntry);
        }
        dispose();
    }

    private ThemeMode selectedThemeMode() {
        for (Map.Entry<ThemeMode, JRadioButton> entry : themeButtons.entrySet()) {
            if (entry.getValue().isSelected()) {
                return entry.getKey();
            }
        }
        return ThemeMode.DEFAULT;
    }

    private void selectButtonFor(ThemeMode themeMode) {
        JRadioButton button = themeButtons.get(themeMode);
        if (button != null) {
            button.setSelected(true);
        }
    }

}

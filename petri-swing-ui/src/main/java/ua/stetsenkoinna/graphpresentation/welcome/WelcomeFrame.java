package ua.stetsenkoinna.graphpresentation.welcome;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;
import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.filechooser.FileNameExtensionFilter;
import ua.stetsenkoinna.config.AppSettings;
import ua.stetsenkoinna.graphpresentation.theme.ThemeManager;
import ua.stetsenkoinna.graphpresentation.theme.UiPalette;
import ua.stetsenkoinna.recentprojects.RecentProjectEntry;
import ua.stetsenkoinna.recentprojects.RecentProjectsStore;
import ua.stetsenkoinna.theme.ThemeVariant;
import ua.stetsenkoinna.utils.MessageHelper;

/**
 * The IntelliJ-style "Welcome" window: two pinned action tiles (new project, open project)
 * followed by a sortable grid of cards for previously opened projects.
 *
 * <p>Built once per call to {@link #show}; there is no shared instance, so a caller that wants
 * the window shown again after being hidden (see {@code exitAppIfDismissed}) simply calls {@link
 * #show} again.
 */
public class WelcomeFrame extends JFrame {

    private final AppSettings settings;
    private final RecentProjectsStore store;
    private final Predicate<File> onOpenProject;
    private final BooleanSupplier onCreateNew;
    private final Runnable onDismiss;

    private final JComboBox<SortOption> sortCombo = new JComboBox<>(SortOption.values());
    private final JList<WelcomeItem> list = new JList<>();
    private final JScrollPane scrollPane = new JScrollPane(list);

    /** Held so it can be unregistered in {@link #dispose()}, mirroring {@code PetriNetsFrame}. */
    private ThemeManager.ThemeChangeListener themeListener;

    private WelcomeFrame(AppSettings settings, RecentProjectsStore store,
            Predicate<File> onOpenProject, BooleanSupplier onCreateNew, Runnable onDismiss,
            boolean exitAppIfDismissed) {
        super("PetriObjModelPaint - Welcome");
        this.settings = settings;
        this.store = store;
        this.onOpenProject = onOpenProject;
        this.onCreateNew = onCreateNew;
        this.onDismiss = onDismiss;

        setDefaultCloseOperation(exitAppIfDismissed ? DO_NOTHING_ON_CLOSE : DISPOSE_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent event) {
                if (exitAppIfDismissed) {
                    System.exit(0);
                } else {
                    // Dismissed without picking anything (plain close via the window's own [X]):
                    // hand control back to the caller instead of leaving no window visible and
                    // no process exit, which is what DISPOSE_ON_CLOSE alone would do here.
                    onDismiss.run();
                }
            }
        });

        setLayout(new BorderLayout());
        add(buildHeader(), BorderLayout.NORTH);
        add(scrollPane, BorderLayout.CENTER);

        configureList();
        refreshList();

        setSize(900, 600);
        setLocationRelativeTo(null);

        // Last, so everything it colours already exists - addListener calls back immediately,
        // which paints this frame in the current theme rather than needing a separate initial
        // apply that could drift out of step with it.
        themeListener = this::applyTheme;
        ThemeManager.addListener(themeListener);
    }

    /**
     * Builds and shows the welcome window.
     *
     * @param settings application settings (currently read for future preferences; theming comes
     *        from {@link ThemeManager} directly)
     * @param store the recent-projects registry to render cards from and edit in place
     * @param onOpenProject called with the chosen file once a project is picked to open; returns
     *        true if it actually proceeded (this window then closes), or false if the caller
     *        declined - e.g. the user backed out of a discard-unsaved-changes prompt - in which
     *        case this window stays open exactly as it was
     * @param onCreateNew called when the user activates the "New Project" tile; same true/false
     *        contract as {@code onOpenProject}
     * @param onDismiss called when the window is closed via its own close button without picking
     *        anything; ignored when {@code exitAppIfDismissed} is true, since that case exits the
     *        process directly instead. Typically reshows whatever the caller hid before calling
     *        {@link #show}, or is a no-op if the caller never hid anything.
     * @param exitAppIfDismissed true if closing this window from its own close button should end
     *        the application; false if the caller will take over instead
     */
    public static void show(AppSettings settings, RecentProjectsStore store,
            Predicate<File> onOpenProject, BooleanSupplier onCreateNew, Runnable onDismiss,
            boolean exitAppIfDismissed) {
        WelcomeFrame frame = new WelcomeFrame(
                settings, store, onOpenProject, onCreateNew, onDismiss, exitAppIfDismissed);
        MessageHelper.setDefaultParent(frame);
        frame.setVisible(true);
    }

    private JComponent buildHeader() {
        JPanel header = new JPanel(new BorderLayout());
        header.setBorder(BorderFactory.createEmptyBorder(10, 14, 10, 14));

        JLabel title = new JLabel("PetriObjModelPaint");
        title.setFont(title.getFont().deriveFont(java.awt.Font.BOLD, 18f));
        header.add(title, BorderLayout.WEST);

        JPanel sortPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        sortPanel.add(fullWidthRow(new JLabel("Sort by")));
        sortCombo.addActionListener(event -> refreshList());
        sortPanel.add(sortCombo);
        header.add(sortPanel, BorderLayout.EAST);

        return header;
    }

    private void configureList() {
        list.setLayoutOrientation(JList.HORIZONTAL_WRAP);
        list.setVisibleRowCount(0);
        list.setFixedCellWidth(220);
        list.setFixedCellHeight(170);
        list.setCellRenderer(new WelcomeCardRenderer());
        list.setSelectionMode(javax.swing.ListSelectionModel.SINGLE_SELECTION);
        // The renderer sets a per-cell tooltip on itself, but a JList's own tooltip lookup
        // (which delegates to the renderer under the mouse) only fires for a component the
        // ToolTipManager actually knows about - without this, every tooltip is dead code.
        javax.swing.ToolTipManager.sharedInstance().registerComponent(list);

        // A single left click activates a card - these read as buttons, not as rows in a list
        // a user is expected to double-click into. Requiring a genuine double-click (the
        // original behaviour) needed two clicks inside the OS's double-click time/distance
        // window to register at all; a click a little too slow or a little too far apart
        // counted as two separate single clicks and did nothing, which is what made it feel
        // like it "took many clicks" to work.
        MouseAdapter mouseHandler = new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent event) {
                if (!SwingUtilities.isLeftMouseButton(event) || event.getClickCount() != 1) {
                    return;
                }
                int index = indexAt(event.getPoint());
                if (index >= 0) {
                    activate(list.getModel().getElementAt(index));
                }
            }

            @Override
            public void mousePressed(MouseEvent event) {
                maybeShowPopup(event);
            }

            @Override
            public void mouseReleased(MouseEvent event) {
                maybeShowPopup(event);
            }

            @Override
            public void mouseMoved(MouseEvent event) {
                boolean overCard = indexAt(event.getPoint()) >= 0;
                list.setCursor(java.awt.Cursor.getPredefinedCursor(
                        overCard ? java.awt.Cursor.HAND_CURSOR : java.awt.Cursor.DEFAULT_CURSOR));
            }
        };
        list.addMouseListener(mouseHandler);
        list.addMouseMotionListener(mouseHandler);

        JComponent component = list;
        component.getInputMap(JComponent.WHEN_FOCUSED)
                .put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "welcome.activate");
        component.getActionMap().put("welcome.activate", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent event) {
                WelcomeItem selected = list.getSelectedValue();
                if (selected != null) {
                    activate(selected);
                }
            }
        });

        component.getInputMap(JComponent.WHEN_FOCUSED)
                .put(KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, 0), "welcome.remove");
        component.getActionMap().put("welcome.remove", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent event) {
                WelcomeItem selected = list.getSelectedValue();
                if (selected instanceof RecentProjectItem recentItem) {
                    store.remove(recentItem.entry().getId());
                    refreshList();
                }
            }
        });

        component.getInputMap(JComponent.WHEN_FOCUSED)
                .put(KeyStroke.getKeyStroke(KeyEvent.VK_CONTEXT_MENU, 0), "welcome.contextMenu");
        component.getInputMap(JComponent.WHEN_FOCUSED).put(
                KeyStroke.getKeyStroke(KeyEvent.VK_F10, InputEvent.SHIFT_DOWN_MASK),
                "welcome.contextMenu");
        component.getActionMap().put("welcome.contextMenu", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent event) {
                int index = list.getSelectedIndex();
                if (index < 0) {
                    return;
                }
                WelcomeItem selected = list.getModel().getElementAt(index);
                if (!(selected instanceof RecentProjectItem recentItem)) {
                    return;
                }
                Rectangle bounds = list.getCellBounds(index, index);
                showContextMenu(recentItem.entry(), bounds.x + bounds.width / 2,
                        bounds.y + bounds.height / 2);
            }
        });
    }

    private int indexAt(java.awt.Point point) {
        int index = list.locationToIndex(point);
        if (index < 0) {
            return -1;
        }
        Rectangle bounds = list.getCellBounds(index, index);
        return (bounds != null && bounds.contains(point)) ? index : -1;
    }

    private void maybeShowPopup(MouseEvent event) {
        if (!event.isPopupTrigger()) {
            return;
        }
        int index = indexAt(event.getPoint());
        if (index < 0) {
            return;
        }
        WelcomeItem item = list.getModel().getElementAt(index);
        if (!(item instanceof RecentProjectItem recentItem)) {
            return;
        }
        list.setSelectedIndex(index);
        showContextMenu(recentItem.entry(), event.getX(), event.getY());
    }

    private void showContextMenu(RecentProjectEntry entry, int x, int y) {
        JPopupMenu menu = new JPopupMenu();

        JMenuItem open = new JMenuItem("Open");
        open.addActionListener(event -> openRecent(entry));
        menu.add(open);

        JMenuItem remove = new JMenuItem("Remove from list");
        remove.addActionListener(event -> {
            store.remove(entry.getId());
            refreshList();
        });
        menu.add(remove);

        JMenuItem edit = new JMenuItem("Edit details...");
        edit.addActionListener(event -> editDetails(entry));
        menu.add(edit);

        menu.show(list, x, y);
    }

    private void activate(WelcomeItem item) {
        if (item instanceof NewProjectItem) {
            if (onCreateNew.getAsBoolean()) {
                dispose();
            }
        } else if (item instanceof OpenProjectItem) {
            openViaChooser();
        } else if (item instanceof RecentProjectItem recentItem) {
            openRecent(recentItem.entry());
        }
    }

    private void openViaChooser() {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileFilter(new FileNameExtensionFilter("PNML or XML model (*.pnml, *.xml)",
                "pnml", "xml"));
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            File selected = chooser.getSelectedFile();
            if (onOpenProject.test(selected)) {
                dispose();
            }
        }
    }

    private void openRecent(RecentProjectEntry entry) {
        File file = new File(entry.getPath());
        if (file.isFile()) {
            if (onOpenProject.test(file)) {
                dispose();
            }
        } else {
            JOptionPane.showMessageDialog(this,
                    "'" + entry.getName() + "' could not be found at:\n" + entry.getPath(),
                    "Project not found", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void editDetails(RecentProjectEntry entry) {
        JTextField descriptionField = new JTextField(entry.getDescription(), 24);
        JTextField authorsField = new JTextField(entry.getAuthors(), 24);

        JDialog dialog = new JDialog(this, "Edit project details", true);
        dialog.setLayout(new BorderLayout());

        JPanel form = new JPanel();
        form.setLayout(new BoxLayout(form, BoxLayout.Y_AXIS));
        form.setBorder(BorderFactory.createEmptyBorder(12, 14, 4, 14));
        form.add(fullWidthRow(new JLabel("Description")));
        form.add(fullWidthRow(descriptionField));
        form.add(javax.swing.Box.createVerticalStrut(8));
        form.add(fullWidthRow(new JLabel("Authors")));
        form.add(fullWidthRow(authorsField));
        dialog.add(form, BorderLayout.CENTER);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 8));
        JButton cancel = new JButton("Cancel");
        cancel.addActionListener(event -> dialog.dispose());
        buttons.add(cancel);

        JButton ok = new JButton("OK");
        ok.addActionListener(event -> {
            store.updateMetadata(entry.getId(), descriptionField.getText(),
                    authorsField.getText());
            refreshList();
            dialog.dispose();
        });
        buttons.add(ok);
        dialog.getRootPane().setDefaultButton(ok);
        dialog.add(buttons, BorderLayout.SOUTH);

        dialog.pack();
        dialog.setLocationRelativeTo(this);
        dialog.setVisible(true);
    }

    private void refreshList() {
        List<RecentProjectEntry> entries = new ArrayList<>(store.all());
        SortOption sort = (SortOption) sortCombo.getSelectedItem();
        if (sort != null) {
            entries.sort(sort.comparator());
        }

        DefaultListModel<WelcomeItem> model = new DefaultListModel<>();
        model.addElement(new NewProjectItem());
        model.addElement(new OpenProjectItem());
        for (RecentProjectEntry entry : entries) {
            model.addElement(new RecentProjectItem(entry));
        }

        int previouslySelected = list.getSelectedIndex();
        list.setModel(model);
        if (previouslySelected >= 0 && previouslySelected < model.size()) {
            list.setSelectedIndex(previouslySelected);
        }
    }

    @Override
    public void dispose() {
        if (themeListener != null) {
            ThemeManager.removeListener(themeListener);
            themeListener = null;
        }
        super.dispose();
    }

    private void applyTheme(ThemeVariant variant, UiPalette palette) {
        getContentPane().setBackground(palette.getChrome());
        list.setBackground(palette.getSurface());
        scrollPane.getViewport().setBackground(palette.getSurface());
        revalidate();
        repaint();
    }

    /**
     * Wraps a control so it lays out at its row's full width instead of at its own preferred
     * width - the same fix {@code SettingsDialog.fullWidthRow} applies, and for the same reason:
     * left to {@code BoxLayout}'s own preferred-width measurement, Nimbus can clip the last glyph
     * of a label.
     */
    private static JComponent fullWidthRow(JComponent content) {
        JPanel row = new JPanel(new BorderLayout());
        row.add(content, BorderLayout.CENTER);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, row.getPreferredSize().height));
        return row;
    }

    /** How the recent-project cards can be ordered; the two pinned action tiles are unaffected. */
    private enum SortOption {
        RECENTLY_OPENED("Recently opened",
                Comparator.comparingLong(RecentProjectEntry::getLastOpenedAt).reversed()),
        NAME("Name", Comparator.comparing(RecentProjectEntry::getName,
                String.CASE_INSENSITIVE_ORDER)),
        RECENTLY_EDITED("Recently edited",
                Comparator.comparingLong(RecentProjectEntry::getLastEditedAt).reversed()),
        DATE_CREATED("Date created",
                Comparator.comparingLong(RecentProjectEntry::getCreatedAt).reversed());

        private final String label;
        private final Comparator<RecentProjectEntry> comparator;

        SortOption(String label, Comparator<RecentProjectEntry> comparator) {
            this.label = label;
            this.comparator = comparator;
        }

        Comparator<RecentProjectEntry> comparator() {
            return comparator;
        }

        @Override
        public String toString() {
            return label;
        }
    }
}

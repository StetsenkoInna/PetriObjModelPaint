package ua.stetsenkoinna.graphpresentation.io;

import ua.stetsenkoinna.graphpresentation.input.InputShortcuts;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.Component;
import java.awt.Dialog;
import java.awt.Dimension;
import java.awt.FileDialog;
import java.awt.Frame;
import java.awt.Window;
import java.io.File;
import java.util.List;
import java.util.Locale;

/**
 * Every file the user picks, asked for in one place and through the platform's own dialog.
 *
 * <p>The editor used to ask in six, split between {@link FileDialog} — which <em>is</em> the
 * platform's dialog, the Explorer window on Windows and the Finder sheet on macOS — and
 * {@link JFileChooser}, which is drawn by Swing and looks like neither. Which one a user got
 * depended on which command they had reached for, so opening a model and exporting a chart
 * looked like two different applications.
 *
 * <h2>What "native" can and cannot mean</h2>
 *
 * <p>Files, always: {@code FileDialog} is the real thing on both platforms.
 *
 * <p>Directories, not quite. {@code FileDialog} cannot select a folder on Windows at all — the
 * native open dialog picks files, and there is no flag that changes it — while macOS can be
 * asked to with {@code apple.awt.fileDialogForDirectories}. So {@link #directory} is native on
 * macOS and falls back to {@code JFileChooser} elsewhere. That asymmetry is the honest state of
 * the toolkit; what this class fixes is that the choice is made once, here, instead of six times
 * by whoever was writing that screen.
 *
 * <h2>Filters</h2>
 *
 * <p>Given twice on purpose. macOS honours a {@link java.io.FilenameFilter}; the Windows native
 * dialog ignores it and honours a wildcard in the file name box instead. Setting only one of the
 * two silently does nothing on the other platform.
 */
public final class FileDialogs {

    private FileDialogs() {
    }

    /**
     * A kind of file the user can be asked for.
     *
     * @param description what the dialog calls this kind, for the platforms that show it
     * @param extensions the extensions it covers, without dots, lower case
     */
    public record FileKind(String description, List<String> extensions) {

        /**
         * @param description shown as the filter's name
         * @param extensions the extensions it covers
         */
        public FileKind(String description, String... extensions) {
            this(description, List.of(extensions));
        }

        /** @return the wildcard the Windows dialog understands, e.g. {@code *.pnml;*.xml} */
        String wildcard() {
            return extensions.stream().map(e -> "*." + e).reduce((a, b) -> a + ";" + b).orElse("*.*");
        }

        /** @return whether this name is one of the kind */
        boolean matches(String name) {
            String lower = name.toLowerCase(Locale.ROOT);
            return extensions.stream().anyMatch(e -> lower.endsWith("." + e));
        }

        /** @return the extension to append when the user typed none */
        String defaultExtension() {
            return extensions.isEmpty() ? "" : extensions.getFirst();
        }
    }

    /** A model document, in either of the two spellings the editor reads. */
    public static final FileKind MODEL = new FileKind("PNML or XML model", "pnml", "xml");

    /** A PNML document, for the commands that only ever write that. */
    public static final FileKind PNML = new FileKind("PNML document", "pnml");

    /** An XML document, the other spelling a model may be saved under. */
    public static final FileKind XML = new FileKind("XML document", "xml");

    /** Anything at all - offered so that "all files" is something the user chooses, not a
     *  default they are left to puzzle over. */
    public static final FileKind ANY = new FileKind("All files");

    /**
     * Asks for a file to read.
     *
     * @param parent  the component the dialog belongs to
     * @param title   the dialog's title
     * @param kind    what to offer
     * @param startIn the directory to open in, or {@code null} for the platform's own choice
     * @return the chosen file, or {@code null} if the user cancelled
     */
    public static File open(Component parent, String title, FileKind kind, File startIn) {
        FileDialog dialog = dialogFor(parent, title, FileDialog.LOAD);
        applyFilter(dialog, kind);
        applyDirectory(dialog, startIn);
        dialog.setVisible(true);
        return chosenFile(dialog);
    }

    /**
     * Asks for a file to write.
     *
     * <p>The extension is appended when the user typed none, so a document saved as "model"
     * lands as "model.pnml". Doing that here rather than at each call site is half the reason
     * this class exists - it was done in some of the six places and not in others.
     *
     * @param parent        the component the dialog belongs to
     * @param title         the dialog's title
     * @param kind          what is being written
     * @param startIn       the directory to open in, or {@code null}
     * @param suggestedName the name to put in the box, with or without extension
     * @return the chosen file, or {@code null} if the user cancelled
     */
    public static File save(Component parent, String title, FileKind kind, File startIn,
                            String suggestedName) {
        FileDialog dialog = dialogFor(parent, title, FileDialog.SAVE);
        applyFilter(dialog, kind);
        applyDirectory(dialog, startIn);
        if (suggestedName != null && !suggestedName.isBlank()) {
            dialog.setFile(suggestedName);
        }
        dialog.setVisible(true);
        File chosen = chosenFile(dialog);
        if (chosen == null || kind.matches(chosen.getName()) || kind.defaultExtension().isEmpty()) {
            return chosen;
        }
        return new File(chosen.getParentFile(),
                chosen.getName() + "." + kind.defaultExtension());
    }

    /**
     * Asks which kind of file, then asks for one to read.
     *
     * @param parent  the component the dialog belongs to
     * @param title   the dialog's title
     * @param kinds   the kinds to offer; a single kind is used without asking
     * @param startIn the directory to open in, or {@code null}
     * @return the chosen file, or {@code null} if either step was cancelled
     */
    public static File openAmong(Component parent, String title, List<FileKind> kinds,
                                 File startIn) {
        FileKind kind = pickKind(parent, title, "Open which kind of file?", kinds);
        return kind == null ? null : open(parent, title, kind, startIn);
    }

    /**
     * Asks which kind of file, then asks where to write one.
     *
     * <p>The picked kind's extension is put into the suggested name before the native dialog
     * opens, so the name in the box already reads the way the file will be saved.
     *
     * @param parent   the component the dialog belongs to
     * @param title    the dialog's title
     * @param kinds    the kinds to offer; a single kind is used without asking
     * @param startIn  the directory to open in, or {@code null}
     * @param baseName the name to suggest, without an extension
     * @return the chosen file, or {@code null} if either step was cancelled
     */
    public static File saveAmong(Component parent, String title, List<FileKind> kinds,
                                 File startIn, String baseName) {
        FileKind kind = pickKind(parent, title, "Save as which format?", kinds);
        if (kind == null) {
            return null;
        }
        String suggested = baseName == null || baseName.isBlank()
                ? null
                : baseName + (kind.defaultExtension().isEmpty()
                        ? "" : "." + kind.defaultExtension());
        return save(parent, title, kind, startIn, suggested);
    }

    /**
     * The small step that makes a native dialog able to express a file type at all.
     *
     * <p>Neither platform's own dialog can be given a list of labelled type filters through
     * {@link FileDialog} — on Windows the type box has no way to say more than one thing, which
     * is why it used to read "All Files" for a dialog that only ever meant PNML. Asking here, one
     * button per kind, gets the choice made and named before the native dialog opens; the dialog
     * itself then has only one filter to apply, which is the one thing it can do.
     *
     * <p>Skipped entirely when there is only one kind: a question with a single answer is not a
     * question.
     *
     * @return the chosen kind, or {@code null} if the user closed the dialog
     */
    private static FileKind pickKind(Component parent, String title, String prompt,
                                     List<FileKind> kinds) {
        if (kinds.size() == 1) {
            return kinds.getFirst();
        }
        JDialog dialog = new JDialog(SwingUtilities.getWindowAncestor(parent), title,
                Dialog.ModalityType.APPLICATION_MODAL);
        dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);

        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(BorderFactory.createEmptyBorder(14, 18, 14, 18));

        JLabel label = new JLabel(prompt);
        label.setAlignmentX(Component.CENTER_ALIGNMENT);
        panel.add(label);
        panel.add(Box.createVerticalStrut(10));

        FileKind[] chosen = new FileKind[1];
        for (FileKind kind : kinds) {
            JButton button = new JButton(kind.description() + "  (" + kind.wildcard() + ")");
            button.setAlignmentX(Component.CENTER_ALIGNMENT);
            button.setMaximumSize(
                    new Dimension(Integer.MAX_VALUE, button.getPreferredSize().height));
            button.addActionListener(event -> {
                chosen[0] = kind;
                dialog.dispose();
            });
            panel.add(button);
            panel.add(Box.createVerticalStrut(6));
        }

        dialog.setContentPane(panel);
        dialog.pack();
        dialog.setLocationRelativeTo(parent);
        dialog.setVisible(true);
        return chosen[0];
    }

    /**
     * Asks for a directory.
     *
     * <p>Native on macOS, a {@code JFileChooser} elsewhere — see this class's own notes. The
     * caller does not have to know which it got.
     *
     * @param parent  the component the dialog belongs to
     * @param title   the dialog's title
     * @param startIn the directory to open in, or {@code null}
     * @return the chosen directory, or {@code null} if the user cancelled
     */
    public static File directory(Component parent, String title, File startIn) {
        if (!InputShortcuts.isMac()) {
            JFileChooser chooser = new JFileChooser();
            chooser.setDialogTitle(title);
            chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            if (startIn != null) {
                chooser.setCurrentDirectory(startIn);
            }
            return chooser.showOpenDialog(parent) == JFileChooser.APPROVE_OPTION
                    ? chooser.getSelectedFile()
                    : null;
        }
        // The one documented way to make the macOS dialog select a folder. Set around the call
        // and put back afterwards, so an ordinary file dialog opened later is not affected.
        String previous = System.getProperty(MAC_DIRECTORY_PROPERTY);
        System.setProperty(MAC_DIRECTORY_PROPERTY, "true");
        try {
            FileDialog dialog = dialogFor(parent, title, FileDialog.LOAD);
            applyDirectory(dialog, startIn);
            dialog.setVisible(true);
            return chosenFile(dialog);
        } finally {
            if (previous == null) {
                System.clearProperty(MAC_DIRECTORY_PROPERTY);
            } else {
                System.setProperty(MAC_DIRECTORY_PROPERTY, previous);
            }
        }
    }

    private static final String MAC_DIRECTORY_PROPERTY = "apple.awt.fileDialogForDirectories";

    /**
     * A dialog owned by whatever window the caller sits in.
     *
     * <p>Ownership is not cosmetic: an unowned dialog can open behind the editor, and on macOS
     * a sheet has to have a window to hang from.
     */
    private static FileDialog dialogFor(Component parent, String title, int mode) {
        Window window = parent == null ? null : SwingUtilities.getWindowAncestor(parent);
        if (window instanceof Frame frame) {
            return new FileDialog(frame, title, mode);
        }
        if (window instanceof Dialog owner) {
            return new FileDialog(owner, title, mode);
        }
        return new FileDialog(JOptionPane.getFrameForComponent(parent), title, mode);
    }

    private static void applyFilter(FileDialog dialog, FileKind kind) {
        if (kind == null || kind.extensions().isEmpty()) {
            return;
        }
        // Both spellings - see the note on this class. Neither platform reads the other's.
        dialog.setFilenameFilter((directory, name) -> kind.matches(name));
        dialog.setFile(kind.wildcard());
    }

    private static void applyDirectory(FileDialog dialog, File startIn) {
        if (startIn != null && startIn.isDirectory()) {
            dialog.setDirectory(startIn.getAbsolutePath());
        }
    }

    private static File chosenFile(FileDialog dialog) {
        String name = dialog.getFile();
        if (name == null) {
            return null;
        }
        String directory = dialog.getDirectory();
        return directory == null ? new File(name) : new File(directory, name);
    }

    /**
     * The Swing filter matching a kind, for the one place that still shows a
     * {@link JFileChooser} - kept here so a kind is described once.
     *
     * @param kind the kind to describe
     * @return a filter for {@code JFileChooser}
     */
    public static FileNameExtensionFilter swingFilter(FileKind kind) {
        return new FileNameExtensionFilter(
                kind.description() + " (" + kind.wildcard().replace(";", ", ") + ")",
                kind.extensions().toArray(new String[0]));
    }
}

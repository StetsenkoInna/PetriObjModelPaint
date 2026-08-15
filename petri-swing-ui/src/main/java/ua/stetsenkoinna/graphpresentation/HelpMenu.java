package ua.stetsenkoinna.graphpresentation;

import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import java.awt.Desktop;
import java.awt.Font;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;

/**
 * The Help menu: shows the license this build ships under and links to the project
 * repository.
 *
 * <p>The license text is never duplicated by hand. The Maven build copies the module's
 * LICENSE file into the JAR (see the resources section of the POM), and the dialog renders
 * that packaged copy — so the menu always shows exactly the license of the build it is
 * part of, and editing the LICENSE file is all it takes to update the dialog.
 */
public class HelpMenu extends JMenu {

    private static final String LICENSE_RESOURCE = "/ua/stetsenkoinna/license/LICENSE";
    private static final String REPOSITORY_URL = "https://github.com/StetsenkoInna/PetriObjModelPaint";
    private static final String WEB_APP_URL = "https://github.com/sergiorbk/petri-net-sim";

    private final JFrame owner;

    public HelpMenu(JFrame owner) {
        super("Help");
        this.owner = owner;

        JMenuItem license = new JMenuItem("License...");
        license.addActionListener(e -> {
            JDialog dialog = buildLicenseDialog();
            dialog.setVisible(true);
        });
        add(license);

        JMenuItem repository = new JMenuItem("Project on GitHub...");
        repository.addActionListener(e -> openInBrowser(REPOSITORY_URL));
        add(repository);

        JMenuItem webApp = new JMenuItem("Web app on GitHub...");
        webApp.addActionListener(e -> openInBrowser(WEB_APP_URL));
        add(webApp);
    }

    /** Builds the dialog without showing it, so it can also be rendered off-screen in tests. */
    JDialog buildLicenseDialog() {
        JTextArea text = new JTextArea(loadLicenseText(), 30, 84);
        text.setEditable(false);
        text.setLineWrap(true);
        text.setWrapStyleWord(true);
        text.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        text.setCaretPosition(0);

        JDialog dialog = new JDialog(owner, "License", true);
        dialog.add(new JScrollPane(text));
        dialog.pack();
        dialog.setLocationRelativeTo(owner);
        return dialog;
    }

    private String loadLicenseText() {
        try (InputStream in = HelpMenu.class.getResourceAsStream(LICENSE_RESOURCE)) {
            if (in != null) {
                return new String(in.readAllBytes(), StandardCharsets.UTF_8);
            }
        } catch (IOException ignored) {
            // No recovery: fall through to the pointer below.
        }
        return "The license text was not packaged with this build.\n"
                + "See " + REPOSITORY_URL + "/blob/master/petri-swing-ui/LICENSE";
    }

    private void openInBrowser(String url) {
        try {
            Desktop.getDesktop().browse(URI.create(url));
        } catch (IOException | UnsupportedOperationException | IllegalArgumentException ex) {
            // Headless setups and desktops without browse support end up here; the address
            // is still worth having, so show it for copying instead.
            JOptionPane.showMessageDialog(owner, url, "Repository",
                    JOptionPane.INFORMATION_MESSAGE);
        }
    }
}

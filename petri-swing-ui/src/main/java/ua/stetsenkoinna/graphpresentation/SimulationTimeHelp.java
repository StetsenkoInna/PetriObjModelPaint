package ua.stetsenkoinna.graphpresentation;

import java.awt.Component;
import java.awt.Font;
import java.awt.Insets;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JOptionPane;

/**
 * The question mark beside the simulation parameters, and what it says.
 *
 * <p>Four controls sit in that row and every one of them is about time, which is exactly why
 * none of them explains itself: "Time start", "Time modeling", "1 unit = 1 s" and a row of
 * speeds all look like the same kind of setting, while two of them decide what the simulator
 * computes, one decides only what the labels say, and one decides only how fast the result is
 * played back. That distinction is the whole of what a user needs and none of it is visible.
 *
 * <p>Short on purpose. A paragraph nobody reads helps nobody, so each control gets one line for
 * what it is and, where it earns one, a second for the mistake it invites.
 */
final class SimulationTimeHelp {

    private static final String TITLE = "Simulation time";

    private static final String BODY = """
            <html><body style='width: 380px; font-family: sans-serif; font-size: 11px;'>
            <p style='margin: 0 0 10px 0;'>The model has a clock of its own. It starts at
            <b>Time start</b>, moves forward as transitions fire, and the run ends when it
            reaches <b>Time modeling</b>. A run also ends early if nothing can fire any more.</p>

            <p style='margin: 0 0 4px 0;'><b>Time start</b><br>
            The clock value a run begins at. Normally 0. It must be below Time modeling, or the
            run ends the moment it starts.</p>

            <p style='margin: 0 0 4px 0;'><b>Time modeling</b><br>
            The clock value a run stops at. This is how much of the model's life you simulate,
            not how long you wait.</p>

            <p style='margin: 0 0 4px 0;'><b>s, min, h, abstract</b><br>
            What one tick of that clock stands for. Purely a reading: it says what the horizon
            beside it amounts to and changes nothing the simulation computes. Pick abstract if
            the units stand for nothing in particular.</p>

            <p style='margin: 0 0 0 0;'><b>Animation speed</b><br>
            How fast a run is played back, as a multiple of the normal pace. It changes nothing
            about the result, only how long you watch it. 1x is the pace the editor has always
            animated at; 0.5x is worth using to follow a firing closely, Max plays with no
            pauses at all.</p>
            </body></html>
            """;

    private SimulationTimeHelp() {
    }

    /**
     * @param owner what the dialog is centred on
     * @return the button that opens the explanation, sized to sit in the parameters row
     */
    static JButton button(Component owner) {
        JButton button = new JButton("?");
        button.setToolTipText("What these time settings mean");
        button.setFont(new Font("Arial", Font.BOLD, 11));
        button.setFocusable(false);
        button.setMargin(new Insets(2, 6, 2, 6));
        button.setBorder(BorderFactory.createEmptyBorder(2, 6, 2, 6));
        button.setAlignmentY(Component.CENTER_ALIGNMENT);
        button.addActionListener(e -> show(owner));
        return button;
    }

    /**
     * Shows the explanation. Rendered from HTML in a plain label rather than a text area: it is
     * six short paragraphs to read, not text to select, scroll or edit.
     *
     * @param owner what the dialog is centred on
     */
    static void show(Component owner) {
        JOptionPane.showMessageDialog(owner, new JLabel(BODY), TITLE, JOptionPane.PLAIN_MESSAGE);
    }
}

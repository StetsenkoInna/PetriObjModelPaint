package ua.stetsenkoinna.updates;

import java.awt.*;
import java.awt.event.*;
import java.time.Instant;
import javax.swing.*;

/** A modeless startup notice. Download and installation start only after an explicit click. */
public final class UpdateDialog extends JDialog {
    public UpdateDialog(Window owner, UpdateRelease release, UpdatePreferences preferences, Runnable install) {
        super(owner, "Application update", ModalityType.MODELESS);
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        JPanel content = new JPanel(new BorderLayout(12,12));
        content.setBorder(BorderFactory.createEmptyBorder(18,18,18,18));
        JPanel lines = new JPanel(); lines.setLayout(new BoxLayout(lines,BoxLayout.Y_AXIS));
        addLine(lines,"PetriObjModelPaint " + release.version() + " is available.");
        lines.add(Box.createVerticalStrut(8));
        addLine(lines,"Install the update and restart, or continue using this version.");
        addLine(lines,"Your preferences and saved models will be kept.");
        content.add(lines,BorderLayout.CENTER);
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT,8,0));
        JButton skip = new JButton("Skip this build");
        skip.addActionListener(e -> { preferences.skip(release.identity()); dispose(); });
        JButton later = new JButton("Remind me tomorrow");
        later.addActionListener(e -> postpone(release,preferences));
        JButton update = new JButton("Install update");
        update.addActionListener(e -> { dispose(); install.run(); });
        buttons.add(skip); buttons.add(later); buttons.add(update);
        content.add(buttons,BorderLayout.SOUTH); setContentPane(content);
        addWindowListener(new WindowAdapter() { @Override public void windowClosing(WindowEvent e) { postpone(release,preferences); } });
        getRootPane().registerKeyboardAction(e -> postpone(release,preferences),KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE,0),JComponent.WHEN_IN_FOCUSED_WINDOW);
        pack(); setSize(Math.max(620,getWidth()),getHeight()); setLocationRelativeTo(owner);
    }
    private static void addLine(JPanel lines,String text) {
        JPanel row=new JPanel(new BorderLayout()); row.add(new JLabel(text),BorderLayout.CENTER);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE,row.getPreferredSize().height+4));
        lines.add(row);
    }
    private void postpone(UpdateRelease release, UpdatePreferences preferences) {
        preferences.remindTomorrow(release.identity(),Instant.now()); dispose();
    }
}

package ua.stetsenkoinna.graphpresentation.settings;

import javax.swing.*;
import ua.stetsenkoinna.config.AppSettings;
import ua.stetsenkoinna.config.ContributorProfile;

public final class ContributorProfileDialog {
    private ContributorProfileDialog() { }
    public static void show(java.awt.Component owner, AppSettings settings) {
        ContributorProfile current = settings.getContributorProfile();
        JTextField nickname = new JTextField(current.nickname(), 32);
        JTextField organization = new JTextField(current.organization(), 32);
        JTextField group = new JTextField(current.group(), 32);
        JTextArea contacts = new JTextArea(current.contacts(), 3, 32);
        nickname.getAccessibleContext().setAccessibleName("Contributor nickname");
        organization.getAccessibleContext().setAccessibleName("Contributor organization");
        group.getAccessibleContext().setAccessibleName("Contributor group");
        contacts.getAccessibleContext().setAccessibleName("Contributor contacts");
        JPanel panel = new JPanel(); panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.add(new JLabel("Nickname (blank generates a persistent random nickname)")); panel.add(nickname);
        panel.add(new JLabel("Organization / educational institution (optional)")); panel.add(organization);
        panel.add(new JLabel("Study group (optional)")); panel.add(group);
        panel.add(new JLabel("Contacts: email, messenger or URL (optional)")); panel.add(new JScrollPane(contacts));
        panel.add(new JLabel("Saved once locally and included in new training ZIPs. Organization, group and contacts are optional."));
        panel.add(new JLabel("Importing someone else's ZIP does not change your profile. This is not identity verification."));
        while (JOptionPane.showConfirmDialog(owner, panel, "Contributor profile", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE) == JOptionPane.OK_OPTION) {
            try { settings.setContributorProfile(new ContributorProfile(nickname.getText(), organization.getText(), group.getText(), contacts.getText())); return; }
            catch (IllegalArgumentException failure) { JOptionPane.showMessageDialog(owner, failure.getMessage(), "Profile not saved", JOptionPane.ERROR_MESSAGE); }
        }
    }
}

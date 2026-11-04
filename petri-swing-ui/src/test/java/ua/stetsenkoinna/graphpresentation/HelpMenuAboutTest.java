package ua.stetsenkoinna.graphpresentation;

import static org.junit.Assert.assertTrue;

import org.junit.Test;
import ua.stetsenkoinna.AppVersion;

public class HelpMenuAboutTest {

    @Test
    public void aboutNamesTheRunningReleaseAndItsRuntime() {
        String about = HelpMenu.aboutText();
        assertTrue(about, about.startsWith(AppVersion.PRODUCT + "\n"));
        assertTrue(about, about.contains("Version: " + AppVersion.current()));
        assertTrue(about, about.contains("Java: " + System.getProperty("java.version")));
        assertTrue(about, about.contains("System: " + System.getProperty("os.name")));
    }
}

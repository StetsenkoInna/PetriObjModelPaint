package ua.stetsenkoinna;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.Properties;
import org.junit.Test;

public class AppVersionTest {

    private static Properties build(String version) {
        Properties properties = new Properties();
        if (version != null) {
            properties.setProperty("version", version);
        }
        return properties;
    }

    @Test
    public void theBuildOnTheClasspathStatesARealVersion() {
        // Maven filters the resource for tests as it does for the JAR, so the placeholder
        // never survives into a build that someone runs.
        assertTrue(AppVersion.current(), AppVersion.current().matches("\\d+\\.\\d+\\.\\d+.*"));
    }

    @Test
    public void anUnfilteredPlaceholderReadsAsNoVersion() {
        assertEquals("", AppVersion.parse(build("${project.version}")));
        assertEquals("", AppVersion.parse(build(null)));
        assertEquals("", AppVersion.parse(build("  ")));
    }

    @Test
    public void releaseAndPrereleaseNumbersAreKept() {
        assertEquals("2.8.1", AppVersion.parse(build(" 2.8.1 ")));
        assertEquals("2.9.0-SNAPSHOT", AppVersion.parse(build("2.9.0-SNAPSHOT")));
    }

    @Test
    public void labelsFallBackToTheBareNameWithoutAVersion() {
        assertEquals("PetriNetSim 2.8.1", AppVersion.label("PetriNetSim", "2.8.1"));
        assertEquals("PetriNetSim", AppVersion.label("PetriNetSim", ""));
        assertEquals("PetriNetSim " + AppVersion.current(), AppVersion.label("PetriNetSim"));
    }
}

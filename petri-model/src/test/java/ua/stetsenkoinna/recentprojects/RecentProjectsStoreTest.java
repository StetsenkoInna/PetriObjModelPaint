package ua.stetsenkoinna.recentprojects;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * How the recent-projects registry behaves, including reload-after-restart and the cap on how
 * many entries it keeps - mirrors {@code AppSettingsTest}'s conventions.
 */
public class RecentProjectsStoreTest {

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    private Path registryFile() {
        return folder.getRoot().toPath().resolve("data").resolve("recent-projects.properties");
    }

    private Path projectFile(String name) {
        return folder.getRoot().toPath().resolve(name);
    }

    @Test
    public void openingANewProjectCreatesAnEntryWithTheSystemUserAsAuthor() {
        RecentProjectsStore store = new RecentProjectsStore(registryFile());

        RecentProjectEntry entry = store.recordOpened(projectFile("first.pnml"), "First Net");

        assertEquals("First Net", entry.getName());
        assertEquals(System.getProperty("user.name"), entry.getAuthors());
        assertEquals("", entry.getDescription());
        assertEquals(1, store.all().size());
    }

    @Test
    public void openingTheSamePathTwiceUpdatesTheSameEntryInPlace() throws InterruptedException {
        RecentProjectsStore store = new RecentProjectsStore(registryFile());
        Path file = projectFile("same.pnml");

        RecentProjectEntry first = store.recordOpened(file, "Same Net");
        Thread.sleep(5);
        RecentProjectEntry second = store.recordOpened(file, "Same Net");

        assertEquals(1, store.all().size());
        assertEquals(first.getId(), second.getId());
        assertEquals(first.getCreatedAt(), second.getCreatedAt());
        assertTrue(second.getLastOpenedAt() >= first.getLastOpenedAt());
    }

    @Test
    public void recordingASaveBumpsLastEditedAt() throws InterruptedException {
        RecentProjectsStore store = new RecentProjectsStore(registryFile());
        Path file = projectFile("edited.pnml");

        RecentProjectEntry opened = store.recordOpened(file, "Edited Net");
        long editedAtAfterOpen = opened.getLastEditedAt();
        Thread.sleep(5);
        RecentProjectEntry saved = store.recordSaved(file, "Edited Net");

        assertEquals(opened.getId(), saved.getId());
        assertTrue(saved.getLastEditedAt() >= editedAtAfterOpen);
        assertTrue(saved.getLastEditedAt() >= saved.getCreatedAt());
    }

    @Test
    public void removeActuallyRemovesTheEntry() {
        RecentProjectsStore store = new RecentProjectsStore(registryFile());
        RecentProjectEntry entry = store.recordOpened(projectFile("gone.pnml"), "Gone Net");

        store.remove(entry.getId());

        assertTrue(store.all().isEmpty());
        assertFalse(store.findById(entry.getId()).isPresent());
    }

    @Test
    public void updateMetadataChangesOnlyDescriptionAndAuthors() {
        RecentProjectsStore store = new RecentProjectsStore(registryFile());
        RecentProjectEntry entry = store.recordOpened(projectFile("meta.pnml"), "Meta Net");

        store.updateMetadata(entry.getId(), "A short description", "Ada, Grace");

        RecentProjectEntry updated = store.findById(entry.getId()).orElseThrow();
        assertEquals("A short description", updated.getDescription());
        assertEquals("Ada, Grace", updated.getAuthors());
        assertEquals(entry.getId(), updated.getId());
        assertEquals(entry.getPath(), updated.getPath());
        assertEquals(entry.getName(), updated.getName());
        assertEquals(entry.getCreatedAt(), updated.getCreatedAt());
        assertEquals(entry.getLastOpenedAt(), updated.getLastOpenedAt());
        assertEquals(entry.getLastEditedAt(), updated.getLastEditedAt());
    }

    @Test
    public void aFreshStoreBuiltAgainstTheSameFileSeesThePersistedData() {
        RecentProjectsStore first = new RecentProjectsStore(registryFile());
        RecentProjectEntry entry = first.recordOpened(projectFile("restart.pnml"), "Restart Net");
        first.updateMetadata(entry.getId(), "Survives a restart", "Restart Author");

        RecentProjectsStore reloaded = new RecentProjectsStore(registryFile());
        List<RecentProjectEntry> all = reloaded.all();

        assertEquals(1, all.size());
        RecentProjectEntry reloadedEntry = all.get(0);
        assertEquals(entry.getId(), reloadedEntry.getId());
        assertEquals(entry.getPath(), reloadedEntry.getPath());
        assertEquals("Survives a restart", reloadedEntry.getDescription());
        assertEquals("Restart Author", reloadedEntry.getAuthors());
        assertEquals(entry.getCreatedAt(), reloadedEntry.getCreatedAt());
    }

    @Test
    public void activeProjectIdRoundTripsAcrossRestarts() {
        RecentProjectsStore first = new RecentProjectsStore(registryFile());
        RecentProjectEntry entry = first.recordOpened(projectFile("active.pnml"), "Active Net");
        first.setActiveProjectId(entry.getId());

        RecentProjectsStore reloaded = new RecentProjectsStore(registryFile());
        assertEquals(entry.getId(), reloaded.getActiveProjectId());
    }

    @Test
    public void activeProjectIdIsNullUntilSet() {
        RecentProjectsStore store = new RecentProjectsStore(registryFile());
        assertNull(store.getActiveProjectId());
    }

    @Test
    public void exceedingTheCapEvictsTheLeastRecentlyOpenedEntries() {
        RecentProjectsStore store = new RecentProjectsStore(registryFile());

        int extra = 5;
        String[] ids = new String[RecentProjectsStore.MAX_ENTRIES + extra];
        for (int i = 0; i < ids.length; i++) {
            RecentProjectEntry entry = store.recordOpened(projectFile("p" + i + ".pnml"), "Net " + i);
            ids[i] = entry.getId();
        }

        List<RecentProjectEntry> all = store.all();
        assertEquals(RecentProjectsStore.MAX_ENTRIES, all.size());

        // The earliest-opened entries (the first `extra` of them) should have been evicted...
        for (int i = 0; i < extra; i++) {
            assertFalse("entry " + i + " should have been evicted",
                    store.findById(ids[i]).isPresent());
        }
        // ...while the most recently opened ones survive.
        for (int i = extra; i < ids.length; i++) {
            assertTrue("entry " + i + " should have survived", store.findById(ids[i]).isPresent());
        }
    }

    @Test
    public void settingsWithNowhereToLiveStillWorkForThisSession() {
        RecentProjectsStore sessionOnly = new RecentProjectsStore(null);

        RecentProjectEntry entry = sessionOnly.recordOpened(projectFile("session.pnml"), "Session Net");
        sessionOnly.setActiveProjectId(entry.getId());

        assertEquals(1, sessionOnly.all().size());
        assertEquals(entry.getId(), sessionOnly.getActiveProjectId());
    }
}

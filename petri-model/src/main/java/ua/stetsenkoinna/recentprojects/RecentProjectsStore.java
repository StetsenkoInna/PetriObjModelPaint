package ua.stetsenkoinna.recentprojects;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ua.stetsenkoinna.config.AppDirectoryType;
import ua.stetsenkoinna.config.UserDirectoryManager;

/**
 * The "recent projects" registry: {@code ~/.PetriObjModelPaint/data/recent-projects.properties},
 * next to the other per-user data the app keeps outside its config file.
 *
 * <p>Entries are keyed by {@link RecentProjectEntry#getId() id}, upserted by absolute path -
 * opening or saving the same file again updates that one entry in place rather than adding a
 * duplicate. The list is capped at {@link #MAX_ENTRIES}; once exceeded, the least-recently-opened
 * entries are dropped first.
 *
 * <p>Every failure to read or write is logged and swallowed, the same philosophy as {@code
 * AppSettings}: a corrupt or unwritable registry is not a reason to refuse to open the editor. An
 * unreadable file means an empty list, an unwritable one means this session's changes do not
 * outlive it.
 */
public class RecentProjectsStore {

    private static final Logger LOGGER = LoggerFactory.getLogger(RecentProjectsStore.class);

    private static final String FILE_NAME = "recent-projects.properties";

    /** Entries beyond this many are evicted, oldest-{@code lastOpenedAt}-first, on every upsert. */
    public static final int MAX_ENTRIES = 30;

    private static final String KEY_ACTIVE_PROJECT = "activeProject";
    private static final String KEY_PROJECTS = "projects";
    private static final String ENTRY_KEY_PREFIX = "project.";

    private static volatile RecentProjectsStore shared;

    /** Where the registry lives, or null when there is nowhere to keep it - see {@link #shared()}. */
    private final Path file;

    /** Keyed by id; iteration order is insertion order but callers must not rely on it. */
    private Map<String, RecentProjectEntry> entries = new LinkedHashMap<>();

    private String activeProjectId;

    /**
     * @param file the properties file to read now and write back on every change; it does not
     *        have to exist, and its directory is created on the first save. Null for a registry
     *        that lives for this session only.
     */
    public RecentProjectsStore(Path file) {
        this.file = file;
        load();
    }

    /**
     * @return the registry of the running application, loaded on first use from the standard
     *         location under the user's home directory - or a session-only registry if that
     *         directory cannot be used at all
     */
    public static RecentProjectsStore shared() {
        RecentProjectsStore local = shared;
        if (local == null) {
            synchronized (RecentProjectsStore.class) {
                local = shared;
                if (local == null) {
                    Path file = null;
                    try {
                        UserDirectoryManager directories = new UserDirectoryManager();
                        file = directories.getFilePath(FILE_NAME, AppDirectoryType.DATA);
                    } catch (RuntimeException unavailable) {
                        LOGGER.warn(
                                "User directory unavailable; recent projects will not persist",
                                unavailable);
                    }
                    local = new RecentProjectsStore(file);
                    shared = local;
                }
            }
        }
        return local;
    }

    /** @return every remembered project; order is unspecified, sort as needed */
    public synchronized List<RecentProjectEntry> all() {
        return new ArrayList<>(entries.values());
    }

    /**
     * Records that {@code file} was opened: upserts by absolute path (reusing the id and {@code
     * createdAt} of any existing entry for that path), bumps {@code lastOpenedAt} to now, updates
     * the display name, and persists immediately. Evicts the oldest entries if this pushes the
     * registry over {@link #MAX_ENTRIES}.
     */
    public synchronized RecentProjectEntry recordOpened(Path file, String name) {
        return upsert(file, name, false);
    }

    /**
     * Records that {@code file} was saved: same upsert as {@link #recordOpened}, but bumps both
     * {@code lastEditedAt} and {@code lastOpenedAt} to now - a save implies the document is open.
     */
    public synchronized RecentProjectEntry recordSaved(Path file, String name) {
        return upsert(file, name, true);
    }

    private RecentProjectEntry upsert(Path projectFile, String name, boolean bumpEdited) {
        String absolutePath = projectFile.toAbsolutePath().toString();
        long now = System.currentTimeMillis();
        RecentProjectEntry existing = findByPath(absolutePath);
        RecentProjectEntry updated;
        if (existing == null) {
            updated = RecentProjectEntry.create(absolutePath, name);
        } else {
            long lastEditedAt = bumpEdited ? now : existing.getLastEditedAt();
            updated = existing.touched(absolutePath, name, now, lastEditedAt);
        }
        entries.put(updated.getId(), updated);
        evictOldestBeyondCap(updated.getId());
        save();
        return updated;
    }

    private RecentProjectEntry findByPath(String absolutePath) {
        for (RecentProjectEntry entry : entries.values()) {
            if (entry.getPath().equals(absolutePath)) {
                return entry;
            }
        }
        return null;
    }

    /**
     * Drops entries with the oldest {@code lastOpenedAt} until back at {@link #MAX_ENTRIES},
     * never evicting {@code justTouchedId} even if it happens to be the oldest by that measure
     * (a brand-new entry just created it).
     */
    private void evictOldestBeyondCap(String justTouchedId) {
        while (entries.size() > MAX_ENTRIES) {
            String oldestId = null;
            long oldestOpenedAt = Long.MAX_VALUE;
            for (RecentProjectEntry entry : entries.values()) {
                if (entry.getId().equals(justTouchedId)) {
                    continue;
                }
                if (entry.getLastOpenedAt() < oldestOpenedAt) {
                    oldestOpenedAt = entry.getLastOpenedAt();
                    oldestId = entry.getId();
                }
            }
            if (oldestId == null) {
                break;
            }
            entries.remove(oldestId);
        }
    }

    /** Removes the entry with this id, if any, and persists. A no-op if there is no such entry. */
    public synchronized void remove(String id) {
        if (entries.remove(id) == null) {
            return;
        }
        if (id.equals(activeProjectId)) {
            activeProjectId = null;
        }
        save();
    }

    /**
     * Replaces the description and authors of the entry with this id, leaving everything else -
     * including both activity timestamps - untouched. A no-op if there is no such entry.
     */
    public synchronized void updateMetadata(String id, String description, String authors) {
        RecentProjectEntry existing = entries.get(id);
        if (existing == null) {
            return;
        }
        entries.put(id, existing.withMetadata(description, authors));
        save();
    }

    public synchronized Optional<RecentProjectEntry> findById(String id) {
        return Optional.ofNullable(entries.get(id));
    }

    /** @return the id of the project to auto-reopen on next startup, or null if none is set */
    public synchronized String getActiveProjectId() {
        return activeProjectId;
    }

    /** Sets which project to auto-reopen on next startup, and persists it. */
    public synchronized void setActiveProjectId(String id) {
        activeProjectId = id;
        save();
    }

    private void load() {
        Properties properties = readFile();
        Map<String, RecentProjectEntry> loaded = new LinkedHashMap<>();
        String idList = properties.getProperty(KEY_PROJECTS, "");
        for (String rawId : idList.split(",")) {
            String id = rawId.trim();
            if (id.isEmpty()) {
                continue;
            }
            RecentProjectEntry entry = readEntry(properties, id);
            if (entry != null) {
                loaded.put(id, entry);
            }
        }
        entries = loaded;
        String active = properties.getProperty(KEY_ACTIVE_PROJECT);
        activeProjectId = (active == null || active.isBlank()) ? null : active;
    }

    private RecentProjectEntry readEntry(Properties properties, String id) {
        String path = properties.getProperty(ENTRY_KEY_PREFIX + id + ".path");
        if (path == null) {
            // A projects-list entry with no matching data is a half-written or hand-edited file;
            // skip it rather than fabricate a path.
            return null;
        }
        String name = properties.getProperty(ENTRY_KEY_PREFIX + id + ".name", "");
        String description = properties.getProperty(ENTRY_KEY_PREFIX + id + ".description", "");
        String authors = properties.getProperty(ENTRY_KEY_PREFIX + id + ".authors", "");
        long createdAt = parseLong(properties.getProperty(ENTRY_KEY_PREFIX + id + ".createdAt"));
        long lastOpenedAt =
                parseLong(properties.getProperty(ENTRY_KEY_PREFIX + id + ".lastOpenedAt"));
        long lastEditedAt =
                parseLong(properties.getProperty(ENTRY_KEY_PREFIX + id + ".lastEditedAt"));
        return RecentProjectEntry.of(id, path, name, description, authors, createdAt,
                lastOpenedAt, lastEditedAt);
    }

    private long parseLong(String value) {
        if (value == null) {
            return 0L;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException ex) {
            return 0L;
        }
    }

    private Properties readFile() {
        Properties loaded = new Properties();
        if (file == null || !Files.exists(file)) {
            LOGGER.debug("No recent-projects file at {} yet; using defaults", file);
            return loaded;
        }
        try (InputStream in = Files.newInputStream(file)) {
            loaded.load(in);
        } catch (IOException ex) {
            LOGGER.warn("Could not read recent projects from {}; using defaults", file, ex);
        }
        return loaded;
    }

    /**
     * Writes the registry back out, after re-reading what is on disk so an unrelated key left by
     * a future version of this file is not lost, then replacing every {@code project.*} key with
     * the current in-memory entries - a plain {@code putAll} of this instance's keys, as {@code
     * AppSettings} does, would add and update keys but never remove one, so a removed or evicted
     * entry would keep reappearing after every save.
     */
    private void save() {
        if (file == null) {
            return;
        }
        Properties merged = readFile();
        removeProjectKeys(merged);
        if (activeProjectId != null) {
            merged.setProperty(KEY_ACTIVE_PROJECT, activeProjectId);
        } else {
            merged.remove(KEY_ACTIVE_PROJECT);
        }
        merged.setProperty(KEY_PROJECTS, String.join(",", entries.keySet()));
        for (RecentProjectEntry entry : entries.values()) {
            String prefix = ENTRY_KEY_PREFIX + entry.getId();
            merged.setProperty(prefix + ".path", entry.getPath());
            merged.setProperty(prefix + ".name", entry.getName());
            merged.setProperty(prefix + ".description", entry.getDescription());
            merged.setProperty(prefix + ".authors", entry.getAuthors());
            merged.setProperty(prefix + ".createdAt", Long.toString(entry.getCreatedAt()));
            merged.setProperty(prefix + ".lastOpenedAt", Long.toString(entry.getLastOpenedAt()));
            merged.setProperty(prefix + ".lastEditedAt", Long.toString(entry.getLastEditedAt()));
        }
        try {
            Path parent = file.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            // Written to a sibling temp file and moved into place rather than truncated
            // in-place: a crash or a full disk mid-write must never leave a half-written
            // registry behind, since a dropped project.<id>.* key on the next load means a
            // silently lost project - including hand-entered description/authors metadata
            // that exists nowhere else.
            Path tmp = file.resolveSibling(file.getFileName().toString() + ".tmp");
            try (OutputStream out = Files.newOutputStream(tmp)) {
                merged.store(out, "PetriObjModelPaint recent projects");
            }
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException ex) {
            LOGGER.warn(
                    "Could not write recent projects to {}; this session's changes will not persist",
                    file, ex);
        }
    }

    private void removeProjectKeys(Properties properties) {
        List<String> toRemove = new ArrayList<>();
        for (String key : properties.stringPropertyNames()) {
            if (key.startsWith(ENTRY_KEY_PREFIX)) {
                toRemove.add(key);
            }
        }
        for (String key : toRemove) {
            properties.remove(key);
        }
    }
}

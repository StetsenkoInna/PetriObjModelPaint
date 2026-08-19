package ua.stetsenkoinna.recentprojects;

import java.util.Objects;
import java.util.UUID;

/**
 * One project the "recent projects" list remembers: where it lives on disk, what it is called,
 * who wrote it, and when it was created, opened and edited.
 *
 * <p>Identity is the {@link #getId() id}, a UUID handed out once when the project is first seen -
 * never derived from the path, because the path is exactly the thing that can change under a
 * user's feet (a rename, a move to another folder) while the entry itself must stay the same row
 * in the recent list.
 *
 * <p>Immutable: every change - new metadata, a later open, a later save - produces a new instance
 * rather than mutating this one, so a {@link RecentProjectsStore} can hand a snapshot out without
 * a caller being able to corrupt what the store thinks is true.
 */
public final class RecentProjectEntry {

    private final String id;
    private final String path;
    private final String name;
    private final String description;
    private final String authors;
    private final long createdAt;
    private final long lastOpenedAt;
    private final long lastEditedAt;

    private RecentProjectEntry(String id, String path, String name, String description,
            String authors, long createdAt, long lastOpenedAt, long lastEditedAt) {
        this.id = id;
        this.path = path;
        this.name = name;
        this.description = description;
        this.authors = authors;
        this.createdAt = createdAt;
        this.lastOpenedAt = lastOpenedAt;
        this.lastEditedAt = lastEditedAt;
    }

    /**
     * Creates a brand-new entry, as seen the first time a project is opened or saved: a fresh id,
     * every timestamp set to now, authors defaulted to the current system user, and no
     * description yet.
     *
     * @param path absolute file path of the project
     * @param name display name (the net/model name)
     */
    static RecentProjectEntry create(String path, String name) {
        long now = System.currentTimeMillis();
        return new RecentProjectEntry(UUID.randomUUID().toString(), path, name, "",
                System.getProperty("user.name"), now, now, now);
    }

    /**
     * Reconstructs an entry with every field already known - from the persisted properties file,
     * or from a caller that already has the full record.
     */
    static RecentProjectEntry of(String id, String path, String name, String description,
            String authors, long createdAt, long lastOpenedAt, long lastEditedAt) {
        return new RecentProjectEntry(id, path, name, description, authors, createdAt,
                lastOpenedAt, lastEditedAt);
    }

    /**
     * @return the same entry, with the path, name and activity timestamps updated - as recorded
     *         by {@link RecentProjectsStore#recordOpened} or {@link
     *         RecentProjectsStore#recordSaved}. The id and {@link #getCreatedAt() createdAt}
     *         never change once assigned.
     */
    RecentProjectEntry touched(String path, String name, long lastOpenedAt, long lastEditedAt) {
        return new RecentProjectEntry(id, path, name, description, authors, createdAt,
                lastOpenedAt, lastEditedAt);
    }

    /**
     * @return the same entry with new description/authors metadata; everything else, including
     *         both activity timestamps, is unchanged
     */
    public RecentProjectEntry withMetadata(String description, String authors) {
        return new RecentProjectEntry(id, path, name, description, authors, createdAt,
                lastOpenedAt, lastEditedAt);
    }

    /** @return the stable identifier assigned once, at first registration */
    public String getId() {
        return id;
    }

    /** @return the absolute file path of the project */
    public String getPath() {
        return path;
    }

    /** @return the display name (the net/model name) */
    public String getName() {
        return name;
    }

    /** @return the user-entered description, or {@code ""} if never set */
    public String getDescription() {
        return description;
    }

    /** @return the authors field, defaulted to the system user at creation and editable after */
    public String getAuthors() {
        return authors;
    }

    /** @return epoch millis this entry was first registered; never changes after that */
    public long getCreatedAt() {
        return createdAt;
    }

    /** @return epoch millis this project was last opened */
    public long getLastOpenedAt() {
        return lastOpenedAt;
    }

    /** @return epoch millis this project was last saved */
    public long getLastEditedAt() {
        return lastEditedAt;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof RecentProjectEntry)) {
            return false;
        }
        return id.equals(((RecentProjectEntry) other).id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }

    @Override
    public String toString() {
        return "RecentProjectEntry{id=" + id + ", path=" + path + ", name=" + name + "}";
    }
}

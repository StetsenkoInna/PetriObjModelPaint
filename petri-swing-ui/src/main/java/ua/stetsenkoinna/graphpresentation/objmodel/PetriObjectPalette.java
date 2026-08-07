package ua.stetsenkoinna.graphpresentation.objmodel;

import ua.stetsenkoinna.config.AppDirectoryType;
import ua.stetsenkoinna.config.UserDirectoryManager;
import ua.stetsenkoinna.graphnet.GraphPetriNet;
import ua.stetsenkoinna.libnet.NetTemplateCatalog;
import ua.stetsenkoinna.pnml.PnmlGenerator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Which Petri-object templates exist, and which of them the user keeps on the left toolbar.
 *
 * <p>The available set is derived from the net library rather than hardcoded, so a template
 * added there shows up here without any further wiring. The pinned subset is the user's own
 * choice and is remembered between runs in the application's user directory.
 */
public final class PetriObjectPalette {

    private static final Logger LOGGER = LoggerFactory.getLogger(PetriObjectPalette.class);

    private static final String SETTINGS_FILE = "ui.properties";
    private static final String PINNED_KEY = "toolbar.petriObjects";
    private static final String ID_SEPARATOR = ",";
    /** Under the app's data directory — user content that has to survive, unlike a cache. */
    private static final String CUSTOM_DIRECTORY = "petriobjects";
    /**
     * Saved objects are PNML, not serialized Java. The graph classes define no
     * serialVersionUID anywhere in this repo, so the first field added to any of them would
     * make every object a user had accumulated unreadable — acceptable for a document they
     * re-export, not for a library they build up over months.
     */
    private static final String PROTOTYPE_SUFFIX = ".pnml";

    /**
     * What the toolbar shows before the user has chosen anything. The generator is the one
     * template that is useful in almost every model — every queueing net needs something
     * producing arrivals — so it is the sensible default rather than an arbitrary first entry.
     */
    private static final List<String> DEFAULT_PINNED_IDS =
            List.of(PetriObjectTemplate.BUILTIN_PREFIX + "CreateNetGenerator");

    /**
     * The built-in building blocks, named and lettered by hand.
     *
     * <p>Deliberately a short hand-picked list rather than everything the net library returns:
     * a Petri-object here is meant to be a small, self-contained part a model is assembled out
     * of — an arrival stream, a service node, a branch — and stamped repeatedly. The library
     * also holds whole worked example models (Friend, Thread3, the Test* nets); those are
     * documents to open, not parts to repeat, and belong in the Nets window instead.
     *
     * <p>Names and glyphs are given rather than derived from the method name, because derived
     * ones collide ({@code Simple} and {@code SMOwithoutQueue} would both letter as "S") and
     * read like code rather than like the thing they build.
     */
    private record BuiltIn(String methodName, String displayName, String glyph) {}

    private static final List<BuiltIn> BUILT_INS = List.of(
            new BuiltIn("CreateNetGenerator", "Generator", "G"),
            new BuiltIn("CreateNetGeneratorInf", "Unlimited generator", "U"),
            new BuiltIn("CreateNetSMOwithoutQueue", "Service", "S"),
            new BuiltIn("CreateNetFork", "Fork", "F"));

    private final UserDirectoryManager directories;
    private final List<String> pinnedIds = new ArrayList<>();

    public PetriObjectPalette() {
        UserDirectoryManager manager = null;
        try {
            manager = new UserDirectoryManager();
        } catch (RuntimeException unavailable) {
            // A read-only or otherwise unusable home directory must not stop the editor from
            // opening — the palette just falls back to its defaults and forgets changes.
            LOGGER.warn("User directory unavailable; Petri-object pinning will not persist", unavailable);
        }
        this.directories = manager;
        load();
    }

    /**
     * @return every template that can be put on the toolbar — the built-in building blocks
     *         first, then the Petri-objects the user has saved. A built-in whose net library
     *         method has gone is skipped rather than offered as a button that could only fail
     *         when clicked.
     */
    public List<PetriObjectTemplate> available() {
        List<PetriObjectTemplate> templates = new ArrayList<>(builtIns());
        templates.addAll(custom());
        return templates;
    }

    /** @return the hand-picked building blocks that ship with the application */
    public List<PetriObjectTemplate> builtIns() {
        List<PetriObjectTemplate> templates = new ArrayList<>();
        for (BuiltIn builtIn : BUILT_INS) {
            NetTemplateCatalog.Template template = NetTemplateCatalog.find(builtIn.methodName());
            if (template == null) {
                LOGGER.warn("Net library has no template {}; skipping it", builtIn.methodName());
                continue;
            }
            List<String> arguments = new ArrayList<>();
            for (NetTemplateCatalog.TemplateParameter parameter : template.parameters()) {
                arguments.add(defaultArgument(parameter, builtIn.displayName()));
            }
            templates.add(PetriObjectTemplate.library(
                    builtIn.methodName(), builtIn.displayName(), builtIn.glyph(), arguments));
        }
        return templates;
    }

    /**
     * @return the Petri-objects the user saved from their own canvas, read fresh from disk so
     *         one saved in this session shows up without restarting; empty when the user
     *         directory is unavailable
     */
    public List<PetriObjectTemplate> custom() {
        List<PetriObjectTemplate> templates = new ArrayList<>();
        Path directory = customDirectory();
        if (directory == null || !Files.isDirectory(directory)) {
            return templates;
        }
        try (Stream<Path> files = Files.list(directory)) {
            files.filter(file -> file.getFileName().toString().endsWith(PROTOTYPE_SUFFIX))
                    .sorted(Comparator.comparing(file -> file.getFileName().toString()))
                    .forEach(file -> {
                        String slug = file.getFileName().toString()
                                .substring(0, file.getFileName().toString().length() - PROTOTYPE_SUFFIX.length());
                        String displayName = slug.replace('-', ' ');
                        templates.add(PetriObjectTemplate.prototype(
                                slug, displayName, glyphFor(displayName), file));
                    });
        } catch (IOException failure) {
            LOGGER.warn("Could not list the saved Petri-objects", failure);
        }
        return templates;
    }

    /**
     * Saves a net as a reusable Petri-object under a name of the user's choosing.
     *
     * @param prototype the net to store; already detached from the canvas by the caller
     * @param displayName what to call it
     * @return the file it was written to
     * @throws IOException if the user directory is unavailable or the file cannot be written
     */
    public Path saveCustom(GraphPetriNet prototype, String displayName) throws Exception {
        Path directory = customDirectory();
        if (directory == null) {
            throw new IOException("No user directory is available to save Petri-objects in");
        }
        Files.createDirectories(directory);

        String slug = uniqueSlug(slugify(displayName), directory);
        Path file = directory.resolve(slug + PROTOTYPE_SUFFIX);
        prototype.createPetriNet(displayName);
        new PnmlGenerator().generate(prototype.getPetriNet(), file.toFile(), prototype);
        return file;
    }

    /**
     * Forgets a saved Petri-object: the file goes, and so does its place on the toolbar — an
     * id left pinned to a template that no longer exists would silently do nothing.
     */
    public void deleteCustom(PetriObjectTemplate template) throws IOException {
        if (template.prototypeFile() != null) {
            Files.deleteIfExists(template.prototypeFile());
        }
        if (pinnedIds.remove(template.id())) {
            save();
        }
    }

    /**
     * @return the templates currently on the toolbar, in the order the user pinned them;
     *         ids that no longer resolve to a template are skipped rather than reported, so a
     *         net library method disappearing cannot break the toolbar
     */
    public List<PetriObjectTemplate> pinned() {
        List<PetriObjectTemplate> all = available();
        List<PetriObjectTemplate> result = new ArrayList<>();
        for (String id : pinnedIds) {
            all.stream()
                    .filter(template -> template.id().equals(id))
                    .findFirst()
                    .ifPresent(result::add);
        }
        return result;
    }

    public boolean isPinned(String templateId) {
        return pinnedIds.contains(templateId);
    }

    /**
     * Replaces the pinned set and remembers it for next time.
     *
     * @param ids template ids to show on the toolbar, in the order they should appear
     */
    public void setPinned(List<String> ids) {
        Set<String> unique = new LinkedHashSet<>(ids);
        pinnedIds.clear();
        pinnedIds.addAll(unique);
        save();
    }

    private void load() {
        pinnedIds.clear();
        Properties properties = readSettings();
        String stored = properties.getProperty(PINNED_KEY);
        if (stored == null) {
            pinnedIds.addAll(DEFAULT_PINNED_IDS);
            return;
        }
        // A stored-but-empty value is a real choice — the user unpinned everything — and must
        // not be mistaken for "never set" and silently repopulated with the defaults.
        for (String id : stored.split(ID_SEPARATOR)) {
            String trimmed = id.trim();
            if (!trimmed.isEmpty()) {
                pinnedIds.add(trimmed);
            }
        }
    }

    private void save() {
        if (directories == null) {
            return;
        }
        Properties properties = readSettings();
        properties.setProperty(PINNED_KEY, String.join(ID_SEPARATOR, pinnedIds));
        try (OutputStream out = Files.newOutputStream(settingsPath())) {
            properties.store(out, "PetriObjModelPaint user interface settings");
        } catch (IOException failure) {
            LOGGER.warn("Could not save the Petri-object toolbar selection", failure);
        }
    }

    private Properties readSettings() {
        Properties properties = new Properties();
        if (directories == null) {
            return properties;
        }
        Path path = settingsPath();
        if (!Files.exists(path)) {
            return properties;
        }
        try (InputStream in = Files.newInputStream(path)) {
            properties.load(in);
        } catch (IOException failure) {
            LOGGER.warn("Could not read the user interface settings", failure);
        }
        return properties;
    }

    private Path settingsPath() {
        return directories.getFilePath(SETTINGS_FILE, AppDirectoryType.CONFIGS);
    }

    /**
     * A value the template can actually be built with before the user has said anything —
     * the toolbar stamps without asking, so every parameter needs a workable starting point.
     */
    private static String defaultArgument(NetTemplateCatalog.TemplateParameter parameter,
                                          String displayName) {
        Class<?> type = parameter.type();
        if (type == String.class) {
            return displayName;
        }
        if (type == double.class || type == Double.class
                || type == float.class || type == Float.class) {
            return "1.0";
        }
        if (type == boolean.class || type == Boolean.class) {
            return "false";
        }
        if (type == double[].class || type == int[].class) {
            return "1";
        }
        return "1";
    }

    private Path customDirectory() {
        return directories == null
                ? null
                : directories.getDirectoryPath(AppDirectoryType.DATA).resolve(CUSTOM_DIRECTORY);
    }

    /**
     * Turns a name into something usable as a file name and as an id. Commas in particular
     * have to go: {@link #setPinned} joins pinned ids with one, so a comma in an id would
     * split it in two the next time the toolbar selection was read back.
     */
    private static String slugify(String displayName) {
        String slug = displayName.trim().toLowerCase()
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-+)|(-+$)", "");
        return slug.isEmpty() ? "petri-object" : slug;
    }

    private static String uniqueSlug(String base, Path directory) {
        if (!Files.exists(directory.resolve(base + PROTOTYPE_SUFFIX))) {
            return base;
        }
        for (int suffix = 2; ; suffix++) {
            String candidate = base + "-" + suffix;
            if (!Files.exists(directory.resolve(candidate + PROTOTYPE_SUFFIX))) {
                return candidate;
            }
        }
    }

    /**
     * The letter(s) drawn on a saved object's button. Built-ins are lettered by hand, but a
     * user's own name has to be reduced to something automatically: initials when the name has
     * several words, otherwise its first letter.
     */
    private static String glyphFor(String displayName) {
        String[] words = displayName.trim().split("[ _-]+");
        if (words.length > 1) {
            StringBuilder initials = new StringBuilder();
            for (String word : words) {
                if (!word.isEmpty() && initials.length() < 2) {
                    initials.append(Character.toUpperCase(word.charAt(0)));
                }
            }
            return initials.toString();
        }
        return displayName.isEmpty() ? "?" : displayName.substring(0, 1).toUpperCase();
    }
}

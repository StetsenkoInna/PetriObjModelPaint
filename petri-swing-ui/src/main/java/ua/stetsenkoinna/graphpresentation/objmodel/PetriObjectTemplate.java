package ua.stetsenkoinna.graphpresentation.objmodel;

import ua.stetsenkoinna.graphnet.NetTemplateRef;

import java.nio.file.Path;
import java.util.List;

/**
 * A ready-made Petri-object the user can stamp onto the canvas from the left toolbar.
 *
 * <p>Templates come in two kinds, and both are deliberately <em>recipes</em> rather than live
 * nets held in memory — each stamp produces its own fresh places, transitions and arcs, so two
 * Petri-objects made from one template can never end up sharing state.
 *
 * <ul>
 *   <li>{@link Kind#LIBRARY} — a net library method plus the arguments to call it with. The
 *       method builds a new net on every call, so nothing needs copying.
 *   <li>{@link Kind#PROTOTYPE} — an object the user saved, stored as a PNML file. Re-reading
 *       the file per stamp is what keeps stamps independent, and it avoids the deep-copy
 *       caveats that copying one in-memory prototype would carry.
 * </ul>
 *
 * @param id stable identity used to remember which templates the user pinned to the toolbar;
 *        namespaced ({@code builtin:} / {@code custom:}) so ids of different origins cannot
 *        collide
 * @param displayName what the management window and the button's tooltip call it
 * @param glyph the one or two letters drawn on its toolbar button
 * @param kind which of the two recipes below applies
 * @param methodName the {@code NetLibrary} method that builds the net; {@code null} for a
 *        prototype
 * @param arguments the arguments to call that method with, as text; empty for a prototype
 * @param prototypeFile the saved PNML the object is rebuilt from; {@code null} for a library
 *        template
 */
public record PetriObjectTemplate(String id, String displayName, String glyph, Kind kind,
                                  String methodName, List<String> arguments, Path prototypeFile) {

    public enum Kind {
        /** Built by calling a net library method. */
        LIBRARY,
        /** Rebuilt by re-reading a Petri-object the user saved. */
        PROTOTYPE
    }

    /** Namespace prefix for the templates that ship with the application. */
    public static final String BUILTIN_PREFIX = "builtin:";
    /** Namespace prefix for Petri-objects the user saved from their own canvas. */
    public static final String CUSTOM_PREFIX = "custom:";

    public static PetriObjectTemplate library(String methodName, String displayName, String glyph,
                                              List<String> arguments) {
        return new PetriObjectTemplate(BUILTIN_PREFIX + methodName, displayName, glyph,
                Kind.LIBRARY, methodName, List.copyOf(arguments), null);
    }

    public static PetriObjectTemplate prototype(String slug, String displayName, String glyph,
                                                Path prototypeFile) {
        return new PetriObjectTemplate(CUSTOM_PREFIX + slug, displayName, glyph,
                Kind.PROTOTYPE, null, List.of(), prototypeFile);
    }

    /**
     * @return the provenance stamped onto every Petri-object this template creates, so a saved
     *         model still records which library template each of its objects came from, or
     *         {@code null} for a saved prototype — that has no library recipe to point at, and
     *         claiming one would make a model reload as something it never was
     */
    public NetTemplateRef toReference() {
        return kind == Kind.LIBRARY ? new NetTemplateRef(methodName, arguments) : null;
    }
}

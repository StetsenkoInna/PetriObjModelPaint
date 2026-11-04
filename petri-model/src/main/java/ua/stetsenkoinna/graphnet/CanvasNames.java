package ua.stetsenkoinna.graphnet;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Keeps names unique on one canvas when something new lands on it.
 *
 * <p>Places, transitions and Petri-object frames share one namespace: whatever is pasted,
 * duplicated, replicated or stamped from a saved or library net gets a name nothing else on
 * the canvas uses. The one exception is a fusion set: a master place and every alias joined
 * to it are one place in several objects, so they carry one name, and that name is theirs
 * alone. Names already on the canvas are never touched; only the newcomers move, so opening
 * or importing a file keeps exactly the names it was saved with.
 *
 * <p>Element names continue an {@code _N} count ({@code Queue}, {@code Queue_2},
 * {@code Queue_3}, and {@code Queue_2} becomes {@code Queue_3} rather than
 * {@code Queue_2_2}). Object names keep the editor's existing style with a space
 * ({@code Server}, {@code Server 2}).
 */
public final class CanvasNames {
    private static final Pattern ELEMENT_COUNT = Pattern.compile("^(.*)_(\\d+)$");
    private static final Pattern OBJECT_COUNT = Pattern.compile("^(.*) (\\d+)$");

    private CanvasNames() {
    }

    /** {@code base} if it is free, otherwise the first free {@code base_N}. */
    public static String nextFree(String base, Set<String> taken) {
        return next(base, taken, ELEMENT_COUNT, "_");
    }

    /** {@code base} if it is free, otherwise the first free {@code base N}. */
    public static String nextFreeObjectName(String base, Set<String> taken) {
        return next(base, taken, OBJECT_COUNT, " ");
    }

    private static String next(String base, Set<String> taken, Pattern counted, String separator) {
        if (!taken.contains(base)) {
            return base;
        }
        Matcher match = counted.matcher(base);
        String stem = match.matches() ? match.group(1) : base;
        long n = match.matches() ? parse(match.group(2)) + 1 : 2;
        while (taken.contains(stem + separator + n)) {
            n++;
        }
        return stem + separator + n;
    }

    private static long parse(String digits) {
        try {
            return Long.parseLong(digits);
        } catch (NumberFormatException tooLong) {
            return 1;
        }
    }

    /**
     * The default name for a new element drawn with a tool: {@code prefix} followed by the
     * smallest number nothing on the canvas uses ({@code P1}, {@code P2}, ...). Counting from
     * the canvas rather than from a session counter is what keeps a net opened from a file,
     * which already has its own {@code P1}, from getting a second one.
     */
    public static String nextDefault(GraphCanvasModel canvas, String prefix) {
        Set<String> taken = namesOn(canvas);
        int n = 1;
        while (taken.contains(prefix + n)) {
            n++;
        }
        return prefix + n;
    }

    /** Every place, transition and object name on the canvas. */
    public static Set<String> namesOn(GraphCanvasModel canvas) {
        Set<String> names = new HashSet<>();
        for (GraphPetriPlace place : places(canvas)) {
            add(names, place.getName());
        }
        for (GraphPetriTransition transition : transitions(canvas)) {
            add(names, transition.getName());
        }
        for (GraphObjectFrame frame : canvas.getFrames()) {
            add(names, frame.getName());
        }
        return names;
    }

    /**
     * A free object name for a frame about to be created, or for {@code exclude} being renamed
     * (its own current name does not count against it).
     */
    public static String freeObjectName(GraphCanvasModel canvas, String base, GraphObjectFrame exclude) {
        Set<String> taken = namesOn(canvas);
        if (exclude != null && exclude.getName() != null && !otherFrameNamed(canvas, exclude)) {
            taken.remove(exclude.getName());
        }
        return nextFreeObjectName(base == null || base.isBlank() ? "Object" : base, taken);
    }

    private static boolean otherFrameNamed(GraphCanvasModel canvas, GraphObjectFrame exclude) {
        for (GraphPetriPlace place : places(canvas)) {
            if (exclude.getName().equals(place.getName())) return true;
        }
        for (GraphPetriTransition transition : transitions(canvas)) {
            if (exclude.getName().equals(transition.getName())) return true;
        }
        for (GraphObjectFrame frame : canvas.getFrames()) {
            if (frame != exclude && exclude.getName().equals(frame.getName())) return true;
        }
        return false;
    }

    /**
     * Rename what was just added to {@code canvas} so that no name repeats.
     *
     * <p>Everything in {@code added} and {@code addedFrames} must already be on the canvas,
     * with its fusion links in place: a newcomer fused to a place that was already there takes
     * that set's name, and newcomers fused only among themselves share one new name. Anything
     * not listed keeps its name.
     *
     * @return how many names changed
     */
    public static int settle(GraphCanvasModel canvas, Collection<? extends GraphElement> added,
            Collection<GraphObjectFrame> addedFrames) {
        Set<GraphElement> fresh = newIdentitySet(added);
        Set<GraphObjectFrame> freshFrames = newIdentitySet(addedFrames);
        if (fresh.isEmpty() && freshFrames.isEmpty()) {
            return 0;
        }
        List<GraphPetriPlace> places = places(canvas);
        List<GraphPetriTransition> transitions = transitions(canvas);

        Set<String> taken = new HashSet<>();
        for (GraphPetriPlace place : places) {
            if (!fresh.contains(place)) add(taken, place.getName());
        }
        for (GraphPetriTransition transition : transitions) {
            if (!fresh.contains(transition)) add(taken, transition.getName());
        }
        for (GraphObjectFrame frame : canvas.getFrames()) {
            if (!freshFrames.contains(frame)) add(taken, frame.getName());
        }

        Map<GraphPetriPlace, GraphPetriPlace> root = fusionRoots(canvas);
        // The name each fusion set already goes by, from a member that was already there:
        // the set's master when it was there, otherwise any older member.
        Map<GraphPetriPlace, String> setName = new IdentityHashMap<>();
        for (GraphPetriPlace place : places) {
            GraphPetriPlace top = root.get(place);
            if (top == null || fresh.contains(place) || place.getName() == null) continue;
            if (place == top || !setName.containsKey(top)) setName.put(top, place.getName());
        }

        int changed = 0;
        Map<GraphElement, String> renamed = new LinkedHashMap<>();
        for (GraphPetriPlace place : places) {
            if (!fresh.contains(place)) continue;
            GraphPetriPlace top = root.get(place);
            String name;
            if (top != null && setName.containsKey(top)) {
                name = setName.get(top);
            } else {
                String wanted = top != null && fresh.contains(top) ? top.getName() : place.getName();
                name = nextFree(baseOf(wanted, "P"), taken);
                taken.add(name);
                if (top != null) setName.put(top, name);
            }
            if (!name.equals(place.getName())) {
                renamed.put(place, name);
            }
        }
        for (GraphPetriTransition transition : transitions) {
            if (!fresh.contains(transition)) continue;
            String name = nextFree(baseOf(transition.getName(), "T"), taken);
            taken.add(name);
            if (!name.equals(transition.getName())) renamed.put(transition, name);
        }
        for (Map.Entry<GraphElement, String> entry : renamed.entrySet()) {
            if (entry.getKey() instanceof GraphPetriPlace place) {
                place.getPetriPlace().setName(entry.getValue());
            } else if (entry.getKey() instanceof GraphPetriTransition transition) {
                transition.getPetriTransition().setName(entry.getValue());
            }
            changed++;
        }
        for (GraphObjectFrame frame : canvas.getFrames()) {
            if (!freshFrames.contains(frame)) continue;
            String name = nextFreeObjectName(baseOf(frame.getName(), "Object"), taken);
            taken.add(name);
            if (!name.equals(frame.getName())) {
                frame.setName(name);
                changed++;
            }
        }
        if (!renamed.isEmpty() && canvas.getNet() != null) {
            refreshArcNames(canvas.getNet(), renamed.keySet());
        }
        return changed;
    }

    /**
     * Give every place fused with {@code place}, at any distance, the name {@code place} has
     * now. A fusion set is one place drawn in several objects, so renaming any part of it
     * renames all of it.
     *
     * @return each other place that was renamed, mapped to the name it had before, so the
     *         change can be undone
     */
    public static Map<GraphPetriPlace, String> shareName(GraphCanvasModel canvas, GraphPetriPlace place) {
        Map<GraphPetriPlace, String> before = new IdentityHashMap<>();
        String name = place.getName();
        if (name == null) return before;
        Map<GraphPetriPlace, GraphPetriPlace> root = fusionRoots(canvas);
        GraphPetriPlace top = root.get(place);
        if (top == null) return before;
        for (Map.Entry<GraphPetriPlace, GraphPetriPlace> entry : root.entrySet()) {
            GraphPetriPlace member = entry.getKey();
            if (member != place && entry.getValue() == top && !name.equals(member.getName())) {
                before.put(member, member.getName());
                member.getPetriPlace().setName(name);
            }
        }
        if (!before.isEmpty() && canvas.getNet() != null) {
            refreshArcNames(canvas.getNet(), before.keySet());
        }
        return before;
    }

    /** Renames one place or transition and keeps its arcs' copy of the name current. */
    public static void rename(GraphCanvasModel canvas, GraphElement element, String name) {
        if (element instanceof GraphPetriPlace place) {
            place.getPetriPlace().setName(name);
        } else if (element instanceof GraphPetriTransition transition) {
            transition.getPetriTransition().setName(name);
        } else {
            return;
        }
        if (canvas.getNet() != null) {
            refreshArcNames(canvas.getNet(), Set.of(element));
        }
    }

    /**
     * Each fused place mapped to one representative of its fusion set. The representative is
     * the set's top master when the links form a proper forest, which they always do on a
     * canvas built through {@link GraphCanvasModel#joinPlaces}. Unfused places are absent.
     */
    static Map<GraphPetriPlace, GraphPetriPlace> fusionRoots(GraphCanvasModel canvas) {
        Map<GraphPetriPlace, GraphPetriPlace> parent = new IdentityHashMap<>();
        for (GraphPlaceFusion fusion : canvas.getFusions()) {
            GraphPetriPlace master = fusion.getMaster();
            GraphPetriPlace joined = fusion.getJoined();
            if (master == null || joined == null) continue;
            parent.putIfAbsent(master, master);
            parent.putIfAbsent(joined, joined);
            GraphPetriPlace a = find(parent, joined);
            GraphPetriPlace b = find(parent, master);
            // The master side wins, so a chain resolves to its top master.
            if (a != b) parent.put(a, b);
        }
        Map<GraphPetriPlace, GraphPetriPlace> roots = new IdentityHashMap<>();
        for (GraphPetriPlace place : parent.keySet()) {
            roots.put(place, find(parent, place));
        }
        return roots;
    }

    private static GraphPetriPlace find(Map<GraphPetriPlace, GraphPetriPlace> parent, GraphPetriPlace place) {
        GraphPetriPlace walker = place;
        for (int guard = 0; guard <= parent.size() && parent.get(walker) != walker; guard++) {
            walker = parent.get(walker);
        }
        return walker;
    }

    /** Arcs keep a copy of their endpoints' names for the simulation model; keep it current. */
    private static void refreshArcNames(GraphPetriNet net, Set<? extends GraphElement> renamed) {
        Set<GraphElement> lookup = newIdentitySet(renamed);
        for (GraphArcIn arc : net.getGraphArcInList()) {
            if (lookup.contains(arc.getBeginElement())) arc.getArcIn().setNameP(arc.getBeginElement().getName());
            if (lookup.contains(arc.getEndElement())) arc.getArcIn().setNameT(arc.getEndElement().getName());
        }
        for (GraphArcOut arc : net.getGraphArcOutList()) {
            if (lookup.contains(arc.getBeginElement())) arc.getArcOut().setNameT(arc.getBeginElement().getName());
            if (lookup.contains(arc.getEndElement())) arc.getArcOut().setNameP(arc.getEndElement().getName());
        }
    }

    private static List<GraphPetriPlace> places(GraphCanvasModel canvas) {
        return canvas.getNet() == null ? List.of() : canvas.getNet().getGraphPetriPlaceList();
    }

    private static List<GraphPetriTransition> transitions(GraphCanvasModel canvas) {
        return canvas.getNet() == null ? List.of() : canvas.getNet().getGraphPetriTransitionList();
    }

    private static String baseOf(String name, String fallback) {
        return name == null || name.isBlank() ? fallback : name;
    }

    private static void add(Set<String> names, String name) {
        if (name != null) names.add(name);
    }

    private static <T> Set<T> newIdentitySet(Collection<? extends T> items) {
        Set<T> set = java.util.Collections.newSetFromMap(new IdentityHashMap<>());
        if (items != null) set.addAll(items);
        return set;
    }
}

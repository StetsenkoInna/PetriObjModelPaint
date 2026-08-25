package ua.stetsenkoinna.graphnet;

import java.awt.Point;
import java.awt.Rectangle;
import java.awt.geom.Point2D;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * What the editor canvas holds: one drawing, plus the frames that mark out the Petri-objects
 * in it and the places that were joined across those frames.
 *
 * <p>The drawing stays a single net, the way it always was — elements, arcs, selection, undo
 * and copy all work on it unchanged. Which Petri-object an element belongs to is what its
 * frame explicitly claims ({@link #claim}), not where it happens to sit - so dragging a frame
 * across the canvas can never quietly pick up something it passes over. An arc that crosses
 * between two objects' elements is therefore not an arc of any net but a link between them,
 * and {@link #toObjModel()} is where that reading happens.
 *
 * <p>Ownership is single-valued and this class is its only writer. {@link #claim} releases
 * whatever held an element before claiming it for a new frame, so {@link #ownerOf} can never
 * find two claimants and has no tie to break. That used to be a convention spread over six
 * callers of {@code GraphObjectFrame.addMember}, and the frame a user had just created could
 * therefore hold nothing at all: {@code ownerOf} answered with whichever frame came first in
 * canvas order, so the new one was invisible to every reader.
 *
 * <p>A frame can sit inside another frame - nesting one Petri-object in another. That is a
 * frame-to-frame relation ({@link #nest}, {@link #enclosingOf}), laid on top of the flat
 * {@link #getFrames()} list rather than replacing it, because that list's order is what indexes
 * objects in {@link #toObjModel()}, in the PNML document and in the statistics formulas. A
 * nested object is consequently an ordinary sibling object of the model it exports to, which is
 * exactly what the web editor does with the same relation.
 *
 * <p>Anything drawn outside every frame still simulates: it is gathered into one last,
 * unnamed Petri-object, so a plain net drawn without any frame is a model of one object.
 */
public class GraphCanvasModel implements Serializable {
    /**
     * Pinned before this class ever reached a saved file, which it does from now on.
     * Left to the compiler it would be recomputed from the class shape, and the next
     * field added here would make every file written before that unreadable.
     */
    private static final long serialVersionUID = 1L;


    /** Name given to the object that collects everything drawn outside every frame. */
    public static final String FREE_OBJECT_NAME = "Free elements";

    /** Gap left between a frame's border and the net laid out inside it. */
    private static final int FRAME_PADDING = 26;

    private String name;
    private GraphPetriNet net;
    private final List<GraphObjectFrame> frames = new ArrayList<>();

    /**
     * Petri-objects stamped together from one net - see {@link GraphObjectGroup}.
     *
     * <p>Held beside the frames rather than instead of them. A group's members are ordinary
     * objects on this canvas and stay in {@link #frames}; this list only records which of them
     * were made together, which is all a group is.
     */
    private final List<GraphObjectGroup> groups = new ArrayList<>();
    private final List<GraphPlaceFusion> fusions = new ArrayList<>();

    /**
     * What had to be dropped from the document this canvas was read from, if anything.
     *
     * <p>A file can carry links the editor would never have allowed to be drawn - a pair linked
     * both ways round, a place copying two sources, a loop - because a document is built by a
     * writer, by hand, or by an older version of this tool, none of which consulted these rules.
     * Such links are left out and named here rather than silently drawn, which is how one of them
     * came to be noticed on screen in the first place.
     */
    private final List<String> loadWarnings = new ArrayList<>();

    /**
     * @return what was dropped while reading this canvas, in document order; empty if nothing was
     */
    public List<String> getLoadWarnings() {
        return java.util.Collections.unmodifiableList(loadWarnings);
    }

    public GraphCanvasModel() {
        this(GraphPetriObjModel.DEFAULT_NAME, new GraphPetriNet());
    }

    public GraphCanvasModel(String name, GraphPetriNet net) {
        this.name = name == null || name.isBlank() ? GraphPetriObjModel.DEFAULT_NAME : name;
        this.net = Objects.requireNonNull(net, "net");
    }

    /**
     * Deep-copies another canvas: a fully independent net, frames whose membership is
     * translated onto the new net's own element instances, and fusions rebuilt the same way —
     * nothing here is shared with {@code other}, so mutating the copy (running a simulation on
     * it, editing it) can never reach back into {@code other} or vice versa.
     *
     * <p>This is the piece a plain net-only snapshot ({@code new GraphPetriNet(canvas.getNet())})
     * cannot give you: the net alone has no notion of Petri-object frames, so restoring from
     * one always came back as loose elements with every frame gone — this is the fix for that.
     *
     * @param other the canvas to copy
     */
    public GraphCanvasModel(GraphCanvasModel other) {
        this.name = other.name;
        Map<GraphElement, GraphElement> oldToNew = new IdentityHashMap<>();
        this.net = new GraphPetriNet(other.net, oldToNew);

        Map<GraphObjectFrame, GraphObjectFrame> frameMap = new IdentityHashMap<>();
        for (GraphObjectFrame oldFrame : other.frames) {
            GraphObjectFrame newFrame = new GraphObjectFrame(oldFrame, oldToNew);
            frameMap.put(oldFrame, newFrame);
            this.frames.add(newFrame);
        }
        // Nesting is wired in a second pass: a child can appear in the list before its parent,
        // so the parent's copy may not exist yet while the child's is being made. Each copy is
        // pointed at the COPY of its parent - pointing it at the original would tie the two
        // canvases back together through exactly the relation a deep copy exists to sever.
        for (GraphObjectFrame oldFrame : other.frames) {
            GraphObjectFrame oldParent = oldFrame.getEnclosing();
            if (oldParent != null) {
                frameMap.get(oldFrame).setEnclosing(frameMap.get(oldParent));
            }
        }

        for (GraphPlaceFusion oldFusion : other.fusions) {
            GraphPetriPlace newMaster = (GraphPetriPlace) oldToNew.get(oldFusion.getMaster());
            GraphPetriPlace newJoined = (GraphPetriPlace) oldToNew.get(oldFusion.getJoined());
            if (newMaster == null || newJoined == null) {
                // Both halves of a fusion are always in the net being copied — this would mean
                // the source model was already inconsistent, not something a copy should mask.
                continue;
            }
            this.fusions.add(new GraphPlaceFusion(newMaster, newJoined,
                    frameMap.get(oldFusion.getMasterOwner()), frameMap.get(oldFusion.getJoinedOwner())));
        }
        copyGroups(other, frameMap);

    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name == null || name.isBlank() ? GraphPetriObjModel.DEFAULT_NAME : name;
    }

    /**
     * @return the single drawing the canvas shows, holding the elements of every object
     */
    public GraphPetriNet getNet() {
        return net;
    }

    public void setNet(GraphPetriNet net) {
        this.net = Objects.requireNonNull(net, "net");
    }

    /**
     * @return every frame on the canvas at every depth, in creation order - nesting does not
     *         change this list or its order. That order is load-bearing: {@link #toObjModel()}
     *         indexes objects by position in it, the PNML writer keys its pages by that index
     *         and the statistics formulas resolve an object the same way, which is also why
     *         nesting needs no change to any of them.
     */
    public List<GraphObjectFrame> getFrames() {
        return frames;
    }

    /**
     * @return the object groups on this canvas, live - the same treatment {@link #getFrames}
     *         gets, so a caller that has to add one can
     */
    public List<GraphObjectGroup> getGroups() {
        return groups;
    }

    /**
     * The places belonging to one Petri-object, in the order the model addresses them by.
     *
     * <p>The same order links are written and read in - "the n-th place of the object's page" -
     * so an index into this list means the same thing here, in a saved document, and across two
     * objects stamped from one net. That is what lets a link be replicated across a group: the
     * place at index i of one member is the place at index i of every other.
     *
     * @param frame the object, which may be null for the places belonging to no object
     * @return its places, in canvas order
     */
    public List<GraphPetriPlace> placesOf(GraphObjectFrame frame) {
        List<GraphPetriPlace> owned = new ArrayList<>();
        for (GraphPetriPlace place : net.getGraphPetriPlaceList()) {
            if (ownerOf(place) == frame) {
                owned.add(place);
            }
        }
        return owned;
    }

    /**
     * @param frame any Petri-object
     * @return the group it was stamped as part of, or {@code null} if it stands alone
     */
    public GraphObjectGroup groupOf(GraphObjectFrame frame) {
        for (GraphObjectGroup group : groups) {
            if (group.contains(frame)) {
                return group;
            }
        }
        return null;
    }

    /**
     * Drops members that are no longer on the canvas, and groups that no longer hold enough
     * objects to be one.
     *
     * <p>Called wherever {@link #removeDanglingFusions} is, and for the same reason: deleting an
     * object cannot be expected to know every record that mentioned it. A group that has fallen
     * to a single member is dissolved rather than kept - one object stamped from a template is
     * just an object, and leaving a group of one would draw a stack around it and offer to
     * replicate a connector across a group of one.
     */
    public void removeDanglingGroupMembers() {
        for (GraphObjectGroup group : groups) {
            for (GraphObjectFrame member : new ArrayList<>(group.getMembers())) {
                if (!frames.contains(member)) {
                    group.remove(member);
                }
            }
        }
        groups.removeIf(group -> group.size() < 2);
    }

    public List<GraphPlaceFusion> getFusions() {
        return fusions;
    }

    /**
     * @return true when the canvas carries no Petri-object frames, so it is an ordinary net
     */
    public boolean isPlainNet() {
        return frames.isEmpty();
    }

    /**
     * Takes on another canvas's structure - its Petri-objects, the nest between them and the
     * places they share - after that canvas's drawing has already been merged into this one's.
     *
     * <p>Merging a net copies every element rather than adopting the instances, so that two
     * documents opened one after the other cannot collide over an id. Everything that pointed
     * at the originals therefore has to be rebuilt against the copies, and this is where that
     * happens: a frame's membership, a fusion's two halves and the frames it is anchored to.
     * Appending {@code other}'s own frames and fusions instead - which is what this used to be
     * - left them referring to instances that were never put on the canvas. The visible result
     * was a document whose every element came out unowned: the objects were drawn as empty
     * rooms around a net that belonged to nobody, Ctrl+A picked up all of it as free elements,
     * dragging that selection asked to move every element into "a different Petri-object", and
     * the shared-place links stayed behind at their old coordinates because both of their ends
     * were off-canvas ghosts.
     *
     * @param other the canvas whose structure to take on
     * @param oldToNew that canvas's elements mapped to the copies this canvas's net now holds,
     *        as {@code GraphPetriNet.mergeGraphNet} reports it; an element missing from the map
     *        is one the merge did not copy, and whatever referred to it is dropped rather than
     *        left dangling
     */
    public void absorbStructureOf(GraphCanvasModel other, Map<GraphElement, GraphElement> oldToNew) {
        if (other == null || other == this) {
            return;
        }
        Map<GraphObjectFrame, GraphObjectFrame> frameMap = new IdentityHashMap<>();
        for (GraphObjectFrame oldFrame : other.frames) {
            GraphObjectFrame copy = new GraphObjectFrame(oldFrame, oldToNew);
            frameMap.put(oldFrame, copy);
            frames.add(copy);
        }
        // Nesting in a second pass, for the same reason the deep-copy constructor does it that
        // way: a child can precede its parent in the list, so the parent's copy need not exist
        // yet while the child's is being made.
        for (GraphObjectFrame oldFrame : other.frames) {
            GraphObjectFrame oldParent = oldFrame.getEnclosing();
            if (oldParent != null && frameMap.containsKey(oldParent)) {
                frameMap.get(oldFrame).setEnclosing(frameMap.get(oldParent));
            }
        }
        for (GraphPlaceFusion oldFusion : other.fusions) {
            GraphElement master = oldToNew.get(oldFusion.getMaster());
            GraphElement joined = oldToNew.get(oldFusion.getJoined());
            if (!(master instanceof GraphPetriPlace) || !(joined instanceof GraphPetriPlace)) {
                continue;
            }
            GraphPlaceFusion copy = new GraphPlaceFusion((GraphPetriPlace) master, (GraphPetriPlace) joined,
                    frameMap.get(oldFusion.getMasterOwner()), frameMap.get(oldFusion.getJoinedOwner()));
            copy.setBoundaryStubOffset(oldFusion.getBoundaryStubOffset());
            fusions.add(copy);
        }
        copyGroups(other, frameMap);
        syncFusions();
    }

    /**
     * @param point a point on the canvas
     * @return the innermost frame whose rectangle contains the point, or {@code null}. The
     *         deepest nesting level wins, and among frames at the same level the later one in
     *         canvas order does. A hit test for clicks and menus; it says nothing about which
     *         object an element belongs to, see {@link #ownerOf}.
     *         <p>Deepest-first matters because it is the same rule {@link #ownerOf} follows: a
     *         nested object's own element belongs to the nested object, so a click inside the
     *         nested object's rectangle has to resolve to it too. The two used to break an
     *         overlap tie in opposite directions, which is what made a frame drawn inside
     *         another frame inconsistent to reason about at all.
     */
    public GraphObjectFrame frameAt(Point2D point) {
        GraphObjectFrame best = null;
        int bestLevel = -1;
        for (GraphObjectFrame frame : frames) {
            if (!frame.contains(point)) {
                continue;
            }
            int level = levelOf(frame);
            if (level >= bestLevel) {
                best = frame;
                bestLevel = level;
            }
        }
        return best;
    }

    /**
     * @param element an element of the drawing
     * @return the frame that claims it ({@link GraphObjectFrame#hasMember}), or {@code null}
     *         when no frame does, which makes it one of the free elements. Always the DIRECT
     *         owner: an element inside a nested object belongs to that nested object, never to
     *         its parent. Ask {@link #membersOfSubtree} for the parent's outward-facing content.
     *         <p>A shared place used to short-circuit this and answer with whichever frame owned
     *         it when the fusion was made. That was stale twice over: the two halves are no
     *         longer moved on top of each other, so there is nothing for the fusion to
     *         disambiguate, and a removed frame kept answering forever.
     */
    public GraphObjectFrame ownerOf(GraphElement element) {
        for (GraphObjectFrame frame : frames) {
            if (frame.hasMember(element)) {
                return frame;
            }
        }
        return null;
    }

    /**
     * Claims an element for a frame, releasing whatever claimed it before - the only way an
     * element is ever claimed, which is what makes ownership single-valued by construction
     * rather than by every caller remembering to check.
     *
     * @param frame the object that should hold the element, or {@code null} to free it
     * @param element the place or transition to claim
     */
    public void claim(GraphObjectFrame frame, GraphElement element) {
        Objects.requireNonNull(element, "element");
        release(element);
        if (frame != null) {
            frame.addMember(element);
        }
        refreshFusionOwners();
    }

    /**
     * Frees an element from whatever claims it. Called when an element is deleted, and by
     * {@link #claim} before it hands the element to someone else.
     *
     * @param element the place or transition to free
     * @return the frame that held it, or {@code null} if it was already free
     */
    public GraphObjectFrame release(GraphElement element) {
        for (GraphObjectFrame frame : frames) {
            if (frame.hasMember(element)) {
                frame.removeMember(element);
                refreshFusionOwners();
                return frame;
            }
        }
        return null;
    }

    /**
     * Re-reads which frame owns each half of every shared place. A fusion used to freeze its
     * two owners at join time, so ungrouping the owning object, dragging a half elsewhere or
     * removing a frame left it anchored to a frame no longer on the canvas: it kept drawing a
     * line to a ghost and could never fall back to the coincident-ring form.
     */
    public void refreshFusionOwners() {
        for (GraphPlaceFusion fusion : fusions) {
            fusion.refreshOwners(ownerOf(fusion.getMaster()), ownerOf(fusion.getJoined()));
        }
    }

    // ------------------------------------------------------------------ nesting

    /**
     * Puts one object inside another, or lifts it back to the top level.
     *
     * @param child the object being nested
     * @param parent the object it goes inside, or {@code null} for the top level
     * @throws IllegalArgumentException if the child would end up inside itself
     */
    public void nest(GraphObjectFrame child, GraphObjectFrame parent) {
        Objects.requireNonNull(child, "child");
        if (child == parent) {
            throw new IllegalArgumentException("A Petri-object cannot be nested in itself");
        }
        for (GraphObjectFrame above = parent; above != null; above = above.getEnclosing()) {
            if (above == child) {
                throw new IllegalArgumentException(
                        "'" + parent.getName() + "' is already inside '" + child.getName() + "'");
            }
        }
        child.setEnclosing(parent);
    }

    /**
     * @param frame an object on the canvas
     * @return the object that encloses it, or {@code null} when it sits directly on the net.
     *         Deliberately not an {@code ownerOf} overload: a frame is not a
     *         {@link GraphElement}, and one word answering two different questions is how the
     *         previous version of this class got hard to reason about.
     */
    public GraphObjectFrame enclosingOf(GraphObjectFrame frame) {
        return frame == null ? null : frame.getEnclosing();
    }

    /**
     * @param frame an object on the canvas
     * @return its abstraction level: 1 for an object sitting directly on the net, 2 for one
     *         nested in that, and so on. The net itself is level 0, which is what the canvas
     *         strip badges the root with.
     */
    public int levelOf(GraphObjectFrame frame) {
        int level = 0;
        // Bounded by the frame count rather than by reaching null, so a cycle that somehow got
        // in cannot hang the paint loop that asks this for every frame.
        for (GraphObjectFrame above = frame; above != null && level <= frames.size(); above = above.getEnclosing()) {
            level++;
        }
        return level;
    }

    /**
     * @param frame an object on the canvas, or {@code null} for the top level
     * @return the objects directly inside it, in canvas order
     */
    public List<GraphObjectFrame> childrenOf(GraphObjectFrame frame) {
        List<GraphObjectFrame> children = new ArrayList<>();
        for (GraphObjectFrame candidate : frames) {
            if (candidate != frame && candidate.getEnclosing() == frame) {
                children.add(candidate);
            }
        }
        return children;
    }

    /**
     * @param frame an object on the canvas
     * @return the frame itself followed by every object nested anywhere inside it, parents
     *         before children - what "this one object, seen from outside" covers
     */
    public List<GraphObjectFrame> subtreeOf(GraphObjectFrame frame) {
        List<GraphObjectFrame> subtree = new ArrayList<>();
        if (frame == null) {
            return subtree;
        }
        subtree.add(frame);
        for (int index = 0; index < subtree.size(); index++) {
            subtree.addAll(childrenOf(subtree.get(index)));
        }
        return subtree;
    }

    /**
     * @param frame an object on the canvas
     * @return every place and transition claimed by it or by anything nested inside it. This is
     *         what an object holds as far as the rest of the canvas is concerned: its ports, its
     *         collapsed element count and the net "Save as Petri-object" writes out all read it.
     */
    public List<GraphElement> membersOfSubtree(GraphObjectFrame frame) {
        List<GraphObjectFrame> subtree = subtreeOf(frame);
        List<GraphElement> members = new ArrayList<>();
        for (GraphPetriPlace place : net.getGraphPetriPlaceList()) {
            if (subtree.contains(ownerOf(place))) {
                members.add(place);
            }
        }
        for (GraphPetriTransition transition : net.getGraphPetriTransitionList()) {
            if (subtree.contains(ownerOf(transition))) {
                members.add(transition);
            }
        }
        return members;
    }

    /**
     * @return every frame ordered so a parent always comes before its children - the order to
     *         paint a nest in, since a child has to be drawn over the parent it sits inside. A
     *         frame whose parent is missing from the canvas, or which is caught in a cycle,
     *         surfaces at the top level rather than being dropped: a frame the user can see has
     *         to be painted whatever state its parent pointer got into.
     */
    public List<GraphObjectFrame> framesParentFirst() {
        List<GraphObjectFrame> ordered = new ArrayList<>(frames.size());
        List<GraphObjectFrame> pending = new ArrayList<>(frames);
        boolean progressed = true;
        while (!pending.isEmpty() && progressed) {
            progressed = false;
            for (int index = 0; index < pending.size(); ) {
                GraphObjectFrame frame = pending.get(index);
                GraphObjectFrame parent = frame.getEnclosing();
                if (parent == null || ordered.contains(parent) || !frames.contains(parent)) {
                    ordered.add(frame);
                    pending.remove(index);
                    progressed = true;
                } else {
                    index++;
                }
            }
        }
        // Whatever is left is in a cycle; it still has to be drawn.
        ordered.addAll(pending);
        return ordered;
    }

    /**
     * Called when a frame is removed: what it held does not vanish along with it, it moves one
     * level out. Its elements go to the object that enclosed it, becoming free when nothing
     * did, and the objects nested inside it are re-nested onto that same enclosing object.
     *
     * <p>This used to be decided by geometry - the released elements fell to whichever other
     * frame's rectangle happened to cover them. With nesting that is actively wrong: removing an
     * outer frame handed its whole net to the frame drawn inside it, which is the opposite of
     * what removing the outer object means.
     *
     * @param removed the frame that was just taken off the canvas
     */
    public void releaseMembers(GraphObjectFrame removed) {
        GraphObjectFrame outer = enclosingOf(removed);
        for (GraphElement element : new ArrayList<>(removed.getMembers())) {
            removed.removeMember(element);
            if (outer != null) {
                claim(outer, element);
            }
        }
        for (GraphObjectFrame child : childrenOf(removed)) {
            child.setEnclosing(outer);
        }
        removed.setEnclosing(null);
        // The direct removeMember above bypasses claim/release, so the shared places that
        // pointed at this frame refresh here.
        refreshFusionOwners();
    }

    /**
     * Every reference link this place takes part in, in either role.
     *
     * <p>Returns a list rather than one link, and replaced a {@code fusionOf} that returned the
     * first match, because a place may now be the source of any number of links. Answering with
     * whichever one happened to be found first was correct only while a place could be in at
     * most one, and would have silently ignored the rest the moment that stopped being true.
     *
     * @param place a place of the drawing
     * @return its links, newest last; empty if it takes part in none
     */
    public List<GraphPlaceFusion> fusionsOf(GraphPetriPlace place) {
        List<GraphPlaceFusion> found = new ArrayList<>();
        for (GraphPlaceFusion fusion : fusions) {
            if (fusion.involves(place)) {
                found.add(fusion);
            }
        }
        return found;
    }

    /**
     * The link this place copies from, if any.
     *
     * <p>At most one, and that is enforced rather than assumed: a place that copied two sources
     * would have no answer to what its marking is. See {@link #joinPlaces}.
     *
     * @param place a place of the drawing
     * @return the link in which it is the target, or {@code null} if it copies nothing
     */
    public GraphPlaceFusion sourceFusionOf(GraphPetriPlace place) {
        for (GraphPlaceFusion fusion : fusions) {
            if (fusion.getJoined() == place) {
                return fusion;
            }
        }
        return null;
    }

    /**
     * Brings another canvas's groups across, rebuilt around the frames that stand for its own.
     *
     * <p>Called from every path that copies a canvas rather than shares it. A group is a list of
     * frames, so copying the frames without it leaves the copy's objects unrelated - which is
     * exactly what happened to a group on the way through a file: the model carried it, the
     * canvas built it, and then the panel copied the frames and the links into its own canvas
     * and left the group behind.
     *
     * @param other    the canvas being copied
     * @param frameMap old frame to the frame that replaces it
     */
    private void copyGroups(GraphCanvasModel other,
                            Map<GraphObjectFrame, GraphObjectFrame> frameMap) {
        for (GraphObjectGroup oldGroup : other.groups) {
            GraphObjectGroup copy = new GraphObjectGroup(oldGroup.getName(), oldGroup.getTemplate());
            for (GraphObjectFrame member : oldGroup.getMembers()) {
                GraphObjectFrame replacement = frameMap.get(member);
                if (replacement != null) {
                    copy.add(replacement);
                }
            }
            if (copy.size() >= 2) {
                groups.add(copy);
            }
        }
    }

    /**
     * The connector a link belongs to: every link joining the same two Petri-objects.
     *
     * <p>A connector is the technique's own name for the whole set of shared places between one
     * pair of objects, written {@code connector(o_u, o_v) = {(o_u.net.p_b, o_v.net.p_a)}}. Two
     * objects sharing three places are joined by one connector of three place identifications,
     * not by three unrelated links, and treating it as one thing is what lets the pair be
     * reasoned about - and detached - as a unit.
     *
     * <p>Derived, never stored. A connector is entirely determined by which objects the existing
     * links run between, so keeping a second record of it could only ever be a record that
     * disagrees; this is the same reason the two-way arc pairing and the linked-place mark are
     * both re-derived rather than maintained.
     *
     * <p>A link with an end belonging to no object is a connector of its own. The concept joins
     * two Petri-objects, and loose places are not one - bundling every loose link into a single
     * enormous "no-object" connector would be an artefact of saying null equals null.
     *
     * @param link one of this canvas's links
     * @return the links of its connector, in declaration order, including {@code link} itself;
     *         a single-element list when nothing else joins the same pair
     */
    public List<GraphPlaceFusion> connectorOf(GraphPlaceFusion link) {
        GraphObjectFrame one = ownerOf(link.getMaster());
        GraphObjectFrame other = ownerOf(link.getJoined());
        if (one == null || other == null) {
            return List.of(link);
        }
        List<GraphPlaceFusion> connector = new ArrayList<>();
        for (GraphPlaceFusion candidate : fusions) {
            if (joinsTheSamePair(candidate, one, other)) {
                connector.add(candidate);
            }
        }
        return connector;
    }

    /**
     * @return true if this link runs between these two objects, whichever way round it was drawn
     */
    private boolean joinsTheSamePair(GraphPlaceFusion link,
                                     GraphObjectFrame one, GraphObjectFrame other) {
        GraphObjectFrame master = ownerOf(link.getMaster());
        GraphObjectFrame joined = ownerOf(link.getJoined());
        return (master == one && joined == other) || (master == other && joined == one);
    }

    /**
     * Every connector on this canvas, each as the list of links that make it up.
     *
     * @return one entry per pair of objects that share at least one place, plus one entry per
     *         link that has an end outside any object; declaration order throughout
     */
    public List<List<GraphPlaceFusion>> connectors() {
        List<List<GraphPlaceFusion>> found = new ArrayList<>();
        List<GraphPlaceFusion> accountedFor = new ArrayList<>();
        for (GraphPlaceFusion link : fusions) {
            if (accountedFor.contains(link)) {
                continue;
            }
            List<GraphPlaceFusion> connector = connectorOf(link);
            accountedFor.addAll(connector);
            found.add(connector);
        }
        return found;
    }

    /**
     * @param place a place of the drawing
     * @return the links in which it is the source - the fan-out, empty if it has none
     */
    public List<GraphPlaceFusion> fusionsFrom(GraphPetriPlace place) {
        List<GraphPlaceFusion> found = new ArrayList<>();
        for (GraphPlaceFusion fusion : fusions) {
            if (fusion.getMaster() == place) {
                found.add(fusion);
            }
        }
        return found;
    }

    // ------------------------------------------------------------------ ports

    /** Smallest gap kept between two ports on the same edge, so their circles never touch. */
    private static final double MIN_PORT_GAP = FramePort.RADIUS * 3.0;

    /**
     * Computes the port markers a frame shows on its border: one per place and one per
     * transition it owns, each sitting on whichever side of the frame — left, right or bottom,
     * never the header side on top — is closest to where the element itself actually is, so a
     * port reads as "this element, right about here" rather than an arbitrary slot.
     *
     * <p>Ports are what a locked object is connected to other objects through, since its own
     * elements can no longer be dragged directly on the shared canvas. Nothing about a port
     * is stored — it is derived fresh from the frame's current bounds and its current
     * elements every time, so moving or resizing a frame, or editing its net, keeps the ports
     * in step without any bookkeeping of its own.
     *
     * <p>One port per element of {@link #membersOfSubtree}, not just per direct member: from
     * outside, a nest is one object, so a collapsed parent still shows where everything inside
     * it - including everything its nested objects hold - can be connected. For an object with
     * nothing nested in it that is exactly its own members, so nothing changes.
     *
     * @param frame the object to compute ports for
     * @return the frame's ports, places first then transitions, both in net order
     */
    public List<FramePort> portsOf(GraphObjectFrame frame) {
        List<GraphElement> owned = membersOfSubtree(frame);
        List<PositionedPort> positions = perimeterPositionsNear(frame.getBounds(), owned);
        List<FramePort> ports = new ArrayList<>(owned.size());
        for (int i = 0; i < owned.size(); i++) {
            ports.add(new FramePort(owned.get(i), positions.get(i).point(), positions.get(i).edge()));
        }
        return ports;
    }

    /**
     * @param point a point on the canvas
     * @return the port under that point, across every frame, or {@code null}. While an
     *         object's content is actually shown ({@link GraphObjectFrame#isContentShown()}),
     *         a point on the real place or transition itself also resolves to that element's
     *         port — its circle is not drawn there in that case, but the port it stands for is
     *         still exactly what a link from it should be, so there is no reason a locked
     *         element's own drawing should not be draggable the same way its port would be
     */
    public FramePort portAt(Point2D point) {
        for (GraphObjectFrame frame : frames) {
            boolean contentShown = frame.isContentShown();
            for (FramePort port : portsOf(frame)) {
                if (port.isNear(point) || (contentShown && port.getElement().isGraphElement(point))) {
                    return port;
                }
            }
        }
        return null;
    }

    /** One element's port, still being placed: which edge, and how far along it. */
    private static final class PendingPort {
        final int index;
        final FramePort.Edge edge;
        double along;

        PendingPort(int index, FramePort.Edge edge, double along) {
            this.index = index;
            this.edge = edge;
            this.along = along;
        }
    }

    private record PositionedPort(Point point, FramePort.Edge edge) {}

    /**
     * Places one port per element of {@code owned}, on whichever of the frame's left, right or
     * bottom side sits closest to that element's own position — the top side, under the
     * header, is never used. Elements that land close together on the same side are then
     * nudged apart just enough that their circles do not overlap.
     *
     * @param bounds the frame's current rectangle
     * @param owned the elements to place a port for, in the order ports are returned
     * @return one position per element of {@code owned}, same order
     */
    private static List<PositionedPort> perimeterPositionsNear(Rectangle bounds, List<GraphElement> owned) {
        List<PositionedPort> result = new ArrayList<>(Collections.nCopies(owned.size(), null));
        if (owned.isEmpty()) {
            return result;
        }
        int left = bounds.x;
        int right = bounds.x + bounds.width;
        int top = bounds.y + GraphObjectFrame.HEADER_HEIGHT;
        int bottom = bounds.y + bounds.height;

        List<PendingPort> onLeft = new ArrayList<>();
        List<PendingPort> onRight = new ArrayList<>();
        List<PendingPort> onBottom = new ArrayList<>();

        for (int i = 0; i < owned.size(); i++) {
            Point2D centre = owned.get(i).getGraphElementCenter();
            double ex = centre == null ? (left + right) / 2.0 : clamp(centre.getX(), left, right);
            double ey = centre == null ? bottom : clamp(centre.getY(), top, bottom);
            double distLeft = ex - left;
            double distRight = right - ex;
            double distBottom = bottom - ey;
            double nearest = Math.min(distLeft, Math.min(distRight, distBottom));
            if (nearest == distLeft) {
                onLeft.add(new PendingPort(i, FramePort.Edge.LEFT, clamp(ey, top, bottom)));
            } else if (nearest == distRight) {
                onRight.add(new PendingPort(i, FramePort.Edge.RIGHT, clamp(ey, top, bottom)));
            } else {
                onBottom.add(new PendingPort(i, FramePort.Edge.BOTTOM, clamp(ex, left, right)));
            }
        }

        spaceOut(onLeft, top, bottom);
        spaceOut(onRight, top, bottom);
        spaceOut(onBottom, left, right);

        for (PendingPort p : onLeft) {
            result.set(p.index, new PositionedPort(new Point(left, (int) Math.round(p.along)), p.edge));
        }
        for (PendingPort p : onRight) {
            result.set(p.index, new PositionedPort(new Point(right, (int) Math.round(p.along)), p.edge));
        }
        for (PendingPort p : onBottom) {
            result.set(p.index, new PositionedPort(new Point((int) Math.round(p.along), bottom), p.edge));
        }
        return result;
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    /**
     * Enforces a minimum gap between ports sharing one edge, moving each only as far as it
     * takes to stop overlapping its neighbour — a port that already has room to itself does
     * not move at all.
     */
    private static void spaceOut(List<PendingPort> group, double min, double max) {
        if (group.size() < 2) {
            return;
        }
        group.sort((a, b) -> Double.compare(a.along, b.along));
        for (int i = 1; i < group.size(); i++) {
            double wanted = group.get(i - 1).along + MIN_PORT_GAP;
            if (group.get(i).along < wanted) {
                group.get(i).along = wanted;
            }
        }
        // The forward pass alone can push the last ports past the edge's end; pull the whole
        // run back from that end, which keeps every gap intact.
        int last = group.size() - 1;
        if (group.get(last).along > max) {
            group.get(last).along = max;
            for (int i = last - 1; i >= 0; i--) {
                double limit = group.get(i + 1).along - MIN_PORT_GAP;
                if (group.get(i).along > limit) {
                    group.get(i).along = limit;
                }
            }
        }
        for (PendingPort p : group) {
            p.along = clamp(p.along, min, max);
        }
    }

    /**
     * Joins two places into one shared place.
     *
     * @param master the place the shared marking lives in
     * @param joined the place that becomes the same place
     * @return the new fusion
     * @throws IllegalArgumentException if either place is already shared, or both belong to
     *         the same Petri-object, where a shared place would mean nothing
     */
    public GraphPlaceFusion joinPlaces(GraphPetriPlace master, GraphPetriPlace joined) {
        String rejection = rejectionFor(master, joined);
        if (rejection != null) {
            throw new IllegalArgumentException(rejection);
        }
        GraphObjectFrame masterOwner = ownerOf(master);
        GraphObjectFrame joinedOwner = ownerOf(joined);
        // Places keep whatever position they already have — the two owners are always
        // different by this point, so moving one onto the other would displace it out of its
        // own object's layout (or, for a free place, away from wherever it was drawn) for no
        // benefit: a framed fusion is drawn as a port-to-port line, which does not depend on
        // where either place actually sits.
        GraphPlaceFusion fusion = new GraphPlaceFusion(master, joined, masterOwner, joinedOwner);
        fusions.add(fusion);
        // One place, one marking, from the moment the two become one: the join is dragged
        // from the master, so the master's count is the one that wins - the same count the
        // built simulation would keep anyway.
        fusion.syncMarking();
        return fusion;
    }

    /**
     * Why these two places may not be linked, if they may not be.
     *
     * <p>Stated once and consulted from both directions a link can arrive from: the editor,
     * which turns it into a refusal the user reads, and a document being opened, which drops the
     * link and says so afterwards. Two copies of these rules would be two chances to disagree,
     * and the one that mattered would be the one nobody was looking at - a file can carry a link
     * the editor would never have let anyone draw.
     *
     * @param master the place to be repeated
     * @param joined the place that would repeat it
     * @return a sentence saying what is wrong, or {@code null} if nothing is
     */
    private String rejectionFor(GraphPetriPlace master, GraphPetriPlace joined) {
        if (master == joined) {
            return "A place cannot be linked to itself";
        }
        // One source, any number of targets - but each of the four ways that could go wrong is
        // refused on its own terms, rather than by the blanket "this place is already shared"
        // that used to stand here and was what limited the whole feature to one link per place.
        if (linkBetween(master, joined) != null) {
            return "These two places are already linked; a link in the opposite direction is not"
                    + " allowed either";
        }
        if (sourceFusionOf(joined) != null) {
            return "That place already copies another one. A place can repeat a single source,"
                    + " otherwise there is no saying which marking it holds";
        }
        if (copiesFrom(master, joined)) {
            return "That would make a loop of links, where each place copies the next";
        }
        GraphObjectFrame masterOwner = ownerOf(master);
        // Two places of one object still make no sense to link - the object would be repeating
        // itself. Two places that belong to no object at all are a different matter and are
        // allowed: they used to land here as well, since both owners read null, and got refused
        // for a reason that did not apply to them.
        if (masterOwner != null && masterOwner == ownerOf(joined)) {
            return "Both places belong to the same Petri-object, so linking them would have it"
                    + " repeat itself";
        }
        return null;
    }

    /**
     * @return the link between these two places whichever way round it runs, or {@code null}
     */
    private GraphPlaceFusion linkBetween(GraphPetriPlace one, GraphPetriPlace other) {
        for (GraphPlaceFusion fusion : fusions) {
            if ((fusion.getMaster() == one && fusion.getJoined() == other)
                    || (fusion.getMaster() == other && fusion.getJoined() == one)) {
                return fusion;
            }
        }
        return null;
    }

    /**
     * Whether {@code place} already copies {@code candidateSource}, directly or through a chain
     * of links.
     *
     * <p>Links may be chained - a place that copies another may itself be copied - so the check
     * that a new link does not close a loop has to walk the whole chain, not just look one step
     * up. PNML requires this too: a reference place must not refer to a cycle of reference
     * places, which is exactly what a loop here would be written as.
     */
    private boolean copiesFrom(GraphPetriPlace place, GraphPetriPlace candidateSource) {
        GraphPetriPlace walker = place;
        // Bounded by the number of links: every step moves strictly up a chain that has no loop
        // in it yet, so it cannot run longer than that even if the model is somehow corrupt.
        for (int step = 0; step <= fusions.size() && walker != null; step++) {
            if (walker == candidateSource) {
                return true;
            }
            GraphPlaceFusion source = sourceFusionOf(walker);
            walker = source == null ? null : source.getMaster();
        }
        return false;
    }

    /**
     * Drops fusions and frames that no longer refer to anything on the canvas — called after
     * elements were deleted.
     */
    public void removeDanglingFusions() {
        List<GraphPetriPlace> places = net.getGraphPetriPlaceList();
        fusions.removeIf(fusion -> !places.contains(fusion.getMaster()) || !places.contains(fusion.getJoined()));
    }

    /**
     * Keeps every linked place holding its source's marking after elements were moved.
     *
     * <p>No longer moves anything. It used to pull a free target onto its source so the two
     * could be drawn as one circle; a link now leaves both places exactly where they are, which
     * is the only thing that makes sense once one source can be repeated by several places.
     */
    public void syncFusions() {
        refreshFusionOwners();
        // Groups too, and here rather than beside each command that can remove an object. A
        // member can go by the eraser, by Delete, by an undone paste, by its parent being
        // removed; expecting every one of those paths to remember a collection added later is
        // the mistake that made a group vanish on open. Run on every pass, it cannot be missed.
        removeDanglingGroupMembers();
        // Stated afresh, not added to: a place that has just lost its last link has to stop
        // being drawn as linked, and only clearing first can say that.
        for (GraphPetriPlace place : net.getGraphPetriPlaceList()) {
            place.setLinkedToAnotherPlace(false);
        }
        for (GraphPlaceFusion fusion : fusions) {
            // Both ends: the source's marking is no more its own than the copy's once they are
            // one instance, so marking only the copies would say something untrue about it.
            fusion.getMaster().setLinkedToAnotherPlace(true);
            fusion.getJoined().setLinkedToAnotherPlace(true);
            // Self-healing for the one-marking rule: any path that changed a marking
            // without going through the properties dialog converges back to the master's.
            fusion.syncMarking();
        }
    }

    /**
     * Takes a shared place apart, leaving the two places ordinary again. The inverse of
     * {@link #joinPlaces}; {@link #restoreFusion} puts the same fusion back, which is what
     * the undo of a join and the redo of a split both do.
     *
     * @param fusion the shared place to remove
     */
    public void removeFusion(GraphPlaceFusion fusion) {
        fusions.remove(fusion);
    }

    /**
     * Puts a previously removed fusion back, with its owners re-read from the claims as they
     * are now.
     *
     * @param fusion the shared place to restore
     */
    public void restoreFusion(GraphPlaceFusion fusion) {
        if (!fusions.contains(fusion)) {
            fusions.add(fusion);
        }
        refreshFusionOwners();
    }

    // ------------------------------------------------------------------ splitting

    /**
     * Reads the canvas as a Petri-object model.
     *
     * <p>Objects come out in frame order, with everything drawn outside every frame gathered
     * into a last unnamed object. An output arc whose ends sit in different objects becomes a
     * transition-to-place link, and a shared place becomes a place fusion.
     *
     * <p>An input arc may not cross an object boundary: a transition takes its input places
     * from its own Petri-object only, so such an arc is refused rather than turned into a
     * link. The canonical drawing is a shared place plus an ordinary arc inside the object
     * that owns the transition, which means exactly the same thing.
     *
     * @return the model the canvas describes
     * @throws IllegalStateException if the canvas has nothing on it
     * @throws IllegalArgumentException if an input arc runs from a place of one object to a
     *         transition of another
     */
    public GraphPetriObjModel toObjModel() {
        List<GraphElement> elements = new ArrayList<>();
        elements.addAll(net.getGraphPetriPlaceList());
        elements.addAll(net.getGraphPetriTransitionList());
        if (elements.isEmpty()) {
            throw new IllegalStateException("There is nothing on the canvas to simulate");
        }

        // Frames become objects in the order they appear; index -1 collects the free elements.
        Map<GraphElement, Integer> owners = new IdentityHashMap<>();
        boolean hasFree = false;
        for (GraphElement element : elements) {
            GraphObjectFrame frame = ownerOf(element);
            int owner = frame == null ? -1 : frames.indexOf(frame);
            owners.put(element, owner);
            hasFree |= owner < 0;
        }
        int freeIndex = hasFree ? frames.size() : -1;
        if (hasFree) {
            for (Map.Entry<GraphElement, Integer> entry : owners.entrySet()) {
                if (entry.getValue() < 0) {
                    entry.setValue(freeIndex);
                }
            }
        }

        int objectCount = frames.size() + (hasFree ? 1 : 0);
        List<List<GraphPetriPlace>> places = new ArrayList<>();
        List<List<GraphPetriTransition>> transitions = new ArrayList<>();
        for (int index = 0; index < objectCount; index++) {
            places.add(new ArrayList<>());
            transitions.add(new ArrayList<>());
        }
        for (GraphPetriPlace place : net.getGraphPetriPlaceList()) {
            places.get(owners.get(place)).add(place);
        }
        for (GraphPetriTransition transition : net.getGraphPetriTransitionList()) {
            transitions.get(owners.get(transition)).add(transition);
        }

        List<List<GraphArcIn>> arcsIn = new ArrayList<>();
        List<List<GraphArcOut>> arcsOut = new ArrayList<>();
        for (int index = 0; index < objectCount; index++) {
            arcsIn.add(new ArrayList<>());
            arcsOut.add(new ArrayList<>());
        }

        GraphPetriObjModel model = new GraphPetriObjModel(name);
        List<ua.stetsenkoinna.petriobj.PetriObjLink> links = new ArrayList<>();

        for (GraphArcIn arc : net.getGraphArcInList()) {
            GraphPetriPlace place = placeOf(arc.getBeginElement());
            GraphPetriTransition transition = transitionOf(arc.getEndElement());
            if (place == null || transition == null) {
                continue;
            }
            int placeOwner = owners.get(place);
            int transitionOwner = owners.get(transition);
            if (placeOwner == transitionOwner) {
                arcsIn.get(placeOwner).add(arc);
            } else {
                throw new IllegalArgumentException(crossObjectInputArcMessage(
                        place, placeOwner, transition, transitionOwner, freeIndex));
            }
        }

        for (GraphArcOut arc : net.getGraphArcOutList()) {
            GraphPetriTransition transition = transitionOf(arc.getBeginElement());
            GraphPetriPlace place = placeOf(arc.getEndElement());
            if (place == null || transition == null) {
                continue;
            }
            int placeOwner = owners.get(place);
            int transitionOwner = owners.get(transition);
            if (placeOwner == transitionOwner) {
                arcsOut.get(placeOwner).add(arc);
            } else {
                links.add(ua.stetsenkoinna.petriobj.PetriObjLink.transitionToPlace(
                        transitionOwner, transitions.get(transitionOwner).indexOf(transition),
                        placeOwner, places.get(placeOwner).indexOf(place),
                        Math.max(1, arc.getArcOut().getQuantity())));
            }
        }

        for (GraphPlaceFusion fusion : fusions) {
            Integer masterOwner = owners.get(fusion.getMaster());
            Integer joinedOwner = owners.get(fusion.getJoined());
            if (masterOwner == null || joinedOwner == null || masterOwner.equals(joinedOwner)) {
                continue;
            }
            links.add(ua.stetsenkoinna.petriobj.PetriObjLink.placeFusion(
                    joinedOwner, places.get(joinedOwner).indexOf(fusion.getJoined()),
                    masterOwner, places.get(masterOwner).indexOf(fusion.getMaster())));
        }

        for (int index = 0; index < objectCount; index++) {
            GraphPetriNet objectNet = new GraphPetriNet(null,
                    new ArrayList<>(places.get(index)),
                    new ArrayList<>(transitions.get(index)),
                    new ArrayList<>(arcsIn.get(index)),
                    new ArrayList<>(arcsOut.get(index)));
            boolean free = index == freeIndex;
            GraphObjectFrame frame = free ? null : frames.get(index);
            GraphPetriObject object = new GraphPetriObject(
                    free ? FREE_OBJECT_NAME : frame.getName(), objectNet);
            if (!free) {
                object.setPriority(frame.getPriority());
                object.setTemplate(frame.getTemplate());
                // The expanded rectangle, never the collapsed summary box: a collapsed
                // frame saved with its 170x56 box came back as an object whose whole net
                // was piled into that box, and expanding it restored the box size.
                Rectangle exported = frame.getExpandedBounds();
                object.setPosition(new Point(exported.x, exported.y));
                object.setSize(exported.width, exported.height);
                object.setCollapsed(frame.isCollapsed());
                // The nest travels with the object: frames map to objects 1:1 in order,
                // so the enclosing frame's index is the enclosing object's index.
                GraphObjectFrame enclosing = enclosingOf(frame);
                object.setParentIndex(enclosing == null ? -1 : frames.indexOf(enclosing));
            }
            model.addObject(object);
        }

        for (ua.stetsenkoinna.petriobj.PetriObjLink link : links) {
            model.addLink(link);
        }
        // Which objects were stamped together, addressed by index like everything else a
        // document says about an object. A group whose members no longer all exist is left out
        // rather than written half-complete.
        for (GraphObjectGroup group : groups) {
            List<Integer> memberIndices = new ArrayList<>();
            for (GraphObjectFrame member : group.getMembers()) {
                int at = frames.indexOf(member);
                if (at >= 0) {
                    memberIndices.add(at);
                }
            }
            if (memberIndices.size() >= 2) {
                model.getGroups().add(new PetriObjectGroupRef(group.getName(), memberIndices,
                        group.getTemplate() == null ? null : group.getTemplate().getMethodName()));
            }
        }

        return model;
    }

    /**
     * Explains why an input arc that leaves its Petri-object cannot be exported, and what to
     * draw instead.
     *
     * @param place the place the arc starts at
     * @param placeOwner index of the object that owns the place
     * @param transition the transition the arc ends at
     * @param transitionOwner index of the object that owns the transition
     * @param freeIndex index given to the elements drawn outside every frame, or -1
     */
    private String crossObjectInputArcMessage(GraphPetriPlace place, int placeOwner,
                                              GraphPetriTransition transition, int transitionOwner,
                                              int freeIndex) {
        String placeObject = objectNameAt(placeOwner, freeIndex);
        String transitionObject = objectNameAt(transitionOwner, freeIndex);
        return "Place '" + place.getName() + "' of Petri-object '" + placeObject
                + "' feeds transition '" + transition.getName() + "' of Petri-object '"
                + transitionObject + "'. A transition takes its input places from its own "
                + "Petri-object only, so this arc is not a link between the two objects. "
                + "Share place '" + place.getName() + "' between '" + placeObject + "' and '"
                + transitionObject + "', then draw an ordinary arc from the shared place to '"
                + transition.getName() + "' inside '" + transitionObject + "'.";
    }

    /**
     * @param index index of an object being built by {@link #toObjModel()}
     * @param freeIndex index given to the elements drawn outside every frame, or -1
     * @return the name that object will carry
     */
    private String objectNameAt(int index, int freeIndex) {
        return index == freeIndex || index < 0 || index >= frames.size()
                ? FREE_OBJECT_NAME : frames.get(index).getName();
    }

    private static GraphPetriPlace placeOf(GraphElement element) {
        return element instanceof GraphPetriPlace place ? place : null;
    }

    private static GraphPetriTransition transitionOf(GraphElement element) {
        return element instanceof GraphPetriTransition transition ? transition : null;
    }

    // ------------------------------------------------------------------ assembling

    /**
     * Lays a Petri-object model out on one canvas: a frame per object, its net inside, and
     * the links restored as arcs crossing frame borders or as shared places.
     *
     * <p>The nest between objects is restored too, in a second pass once every frame exists.
     * A model carries it as each object's parent index, which a document states by writing a
     * child object's {@code <page>} inside its parent's, the way ISO/IEC 15909-2 states a
     * hierarchy of pages.
     *
     * @param model the model to show
     * @return the canvas that draws it
     */
    public static GraphCanvasModel fromObjModel(GraphPetriObjModel model) {
        GraphCanvasModel canvas = new GraphCanvasModel(model.getName(), new GraphPetriNet());

        Map<Integer, GraphObjectFrame> frameByObjectIndex = new HashMap<>();
        for (int index = 0; index < model.getObjectCount(); index++) {
            GraphPetriObject object = model.getObject(index);
            // An object that recorded no geometry at all is not a Petri-object: it is either
            // the loose elements of a canvas this application exported, or a plain net from a
            // document that never described objects in the first place — a PNML with no
            // <page> elements parses as exactly one such object. Drawing a frame around it
            // would invent a Petri-object the document does not contain. A real frame always
            // has a width, so its geometry is never all-zero.
            boolean free = object.getPosition().x == 0 && object.getPosition().y == 0
                    && object.getWidth() == 0 && object.getHeight() == 0;

            Rectangle bounds = new Rectangle(
                    object.getPosition().x, object.getPosition().y,
                    object.getWidth() > 0 ? object.getWidth() : defaultWidth(object),
                    object.getHeight() > 0 ? object.getHeight() : defaultHeight(object));
            if (!free) {
                GraphObjectFrame frame = new GraphObjectFrame(object.getName(), bounds);
                frame.setPriority(object.getPriority());
                frame.setTemplate(object.getTemplate());
                canvas.frames.add(frame);
                frameByObjectIndex.put(index, frame);
                // The object's own places and transitions are exactly what this frame claims —
                // known outright here, from the document, rather than left to be re-derived
                // from wherever changeLocation below happens to put them.
                for (GraphPetriPlace place : object.getGraphNet().getGraphPetriPlaceList()) {
                    canvas.claim(frame, place);
                }
                for (GraphPetriTransition transition : object.getGraphNet().getGraphPetriTransitionList()) {
                    canvas.claim(frame, transition);
                }
                // An object whose net still carries the exact coordinates it was exported
                // with stays exactly where the user drew it. Re-centring is only for
                // documents whose layout was normalized or never described.
                if (!object.isAbsoluteLayout()) {
                    // Put the object's drawing in the middle of its own frame; the net keeps
                    // its shape, only where it sits on the shared canvas is decided here.
                    object.getGraphNet().changeLocation(new Point(
                            bounds.x + bounds.width / 2,
                            bounds.y + (bounds.height + GraphObjectFrame.HEADER_HEIGHT) / 2));
                }
                if (object.isCollapsed()) {
                    frame.setCollapsed(true);
                }
            }
            canvas.absorb(object.getGraphNet());
        }

        // Second pass, once every frame exists: the nest the document stated by writing one
        // page inside another. Reimporting used to lose it, so the inner object came back
        // sitting geometrically inside the outer frame while structurally belonging to
        // nobody, and dragging the outer object left it behind.
        for (int index = 0; index < model.getObjectCount(); index++) {
            GraphObjectFrame frame = frameByObjectIndex.get(index);
            GraphObjectFrame parent = frameByObjectIndex.get(model.getObject(index).getParentIndex());
            if (frame != null && parent != null) {
                canvas.nest(frame, parent);
            }
        }

        canvas.restoreLinks(model);
        canvas.restoreGroups(model);
        canvas.syncFusions();
        return canvas;
    }

    private static int defaultWidth(GraphPetriObject object) {
        return Math.max(GraphObjectFrame.MIN_WIDTH * 2,
                80 * Math.max(2, object.getTransitionCount() + object.getPlaceCount() / 2));
    }

    private static int defaultHeight(GraphPetriObject object) {
        return Math.max(GraphObjectFrame.MIN_HEIGHT * 2, 60 * Math.max(2, object.getPlaceCount()));
    }

    /**
     * Adds every element and arc of a net to the canvas drawing.
     */
    private void absorb(GraphPetriNet other) {
        net.getGraphPetriPlaceList().addAll(other.getGraphPetriPlaceList());
        net.getGraphPetriTransitionList().addAll(other.getGraphPetriTransitionList());
        net.getGraphArcInList().addAll(other.getGraphArcInList());
        net.getGraphArcOutList().addAll(other.getGraphArcOutList());
    }

    /**
     * Turns the model's link declarations back into things drawn on the canvas: an output arc
     * from a transition of one object into a place of another, or a shared place.
     */
    /**
     * Puts back the record of which objects were stamped together.
     *
     * <p>After the frames exist, since a group is nothing but a list of them. A member index the
     * document names but this canvas has no frame for is skipped, and a group left with fewer
     * than two members is not restored at all - the same rule that dissolves a group on the
     * canvas when it shrinks to one.
     */
    private void restoreGroups(GraphPetriObjModel model) {
        for (PetriObjectGroupRef declared : model.getGroups()) {
            GraphObjectGroup group = new GraphObjectGroup(declared.name(),
                    declared.templateMethod() == null
                            ? null
                            : new NetTemplateRef(declared.templateMethod(), List.of()));
            for (Integer index : declared.memberObjects()) {
                GraphObjectFrame member = frameOfObject(index);
                if (member != null) {
                    group.add(member);
                }
            }
            if (group.size() >= 2) {
                groups.add(group);
            }
        }
    }

    private void restoreLinks(GraphPetriObjModel model) {
        for (ua.stetsenkoinna.petriobj.PetriObjLink link : model.getLinks()) {
            GraphPetriObject source = model.getObject(link.getSourceObject());
            GraphPetriObject target = model.getObject(link.getTargetObject());
            switch (link.getType()) {
                case PLACE_FUSION -> {
                    GraphPetriPlace joined = placeAt(source, link.getSourceElement());
                    GraphPetriPlace master = placeAt(target, link.getTargetElement());
                    if (joined != null && master != null) {
                        // The same rules the editor applies. Links are restored in document
                        // order, so of a contradictory group the first one stated is the one
                        // that stands - which is also the one that already took effect by the
                        // time the later ones were declared.
                        String rejection = rejectionFor(master, joined);
                        if (rejection != null) {
                            loadWarnings.add("Dropped the link " + master.getName() + " = "
                                    + joined.getName() + ": " + rejection);
                        } else {
                            fusions.add(new GraphPlaceFusion(master, joined,
                                    frameOfObject(link.getTargetObject()),
                                    frameOfObject(link.getSourceObject())));
                        }
                    }
                }
                case TRANSITION_TO_PLACE -> {
                    GraphPetriTransition transition = transitionAt(source, link.getSourceElement());
                    GraphPetriPlace place = placeAt(target, link.getTargetElement());
                    if (transition != null && place != null) {
                        net.getGraphArcOutList().add(
                                GraphArcFactory.outArc(transition, place, link.getQuantity()));
                    }
                }
            }
        }
    }

    /**
     * @param objectIndex position of an object in the model being laid out
     * @return the frame created for it, or null when it is the free-elements object
     */
    private GraphObjectFrame frameOfObject(int objectIndex) {
        return objectIndex >= 0 && objectIndex < frames.size() ? frames.get(objectIndex) : null;
    }

    private static GraphPetriPlace placeAt(GraphPetriObject object, int index) {
        List<GraphPetriPlace> places = object.getGraphNet().getGraphPetriPlaceList();
        return index >= 0 && index < places.size() ? places.get(index) : null;
    }

    private static GraphPetriTransition transitionAt(GraphPetriObject object, int index) {
        List<GraphPetriTransition> transitions = object.getGraphNet().getGraphPetriTransitionList();
        return index >= 0 && index < transitions.size() ? transitions.get(index) : null;
    }

    /**
     * @return the frames, in the order that indexes the objects they mark out
     */
    public List<GraphObjectFrame> framesInOrder() {
        return Collections.unmodifiableList(frames);
    }
}

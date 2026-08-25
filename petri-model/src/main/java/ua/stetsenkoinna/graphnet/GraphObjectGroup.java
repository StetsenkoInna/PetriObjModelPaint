package ua.stetsenkoinna.graphnet;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Several Petri-objects made together from one net, and treated as one thing while the model is
 * being built.
 *
 * <p>This is the technique's {@code multiply} operation:
 * {@code g = multiply(net, lists, k) ⟺ ∀i: o_i = (net, list_i)}. A system of a hundred like
 * nodes is described once and stamped a hundred times, each instance free to carry its own
 * parameters — which is the whole reason the Petri-object approach exists, and the half of it
 * this editor did not have.
 *
 * <p><b>A group is an editing-time construct, not a runtime one.</b> The definition above says
 * so outright: a group <em>is</em> its k objects. So nothing downstream has to learn about
 * groups — the simulation builds the same model it always did, and a saved document is the same
 * conformant PNML with k pages in it. What a group adds is a record that those k objects were
 * made together, so the editor can go on treating them as one: draw them as a stack, replicate a
 * connector across all of them, remove them together.
 *
 * <p>The members are ordinary {@link GraphObjectFrame}s, on the canvas in their own right. That
 * is deliberate rather than convenient: a member has to be selectable, movable and editable like
 * any other object, and would have to be re-invented as such if a group owned its contents
 * privately.
 */
public final class GraphObjectGroup implements Serializable {

    /**
     * Pinned before this class ever reached a saved file, which it does from now on. Left to the
     * compiler it would be recomputed from the class shape, and the next field added here would
     * make every file written before that unreadable.
     */
    private static final long serialVersionUID = 1L;

    private String name;

    /**
     * The members, in the order they were stamped.
     *
     * <p>Order is part of what a group is: {@code list_i} is the i-th instance's parameters, and
     * "the third server" has to keep meaning the same object across a save and a reload.
     */
    private final List<GraphObjectFrame> members = new ArrayList<>();

    /**
     * Where the shared net came from, when it came from the net library.
     *
     * <p>Provenance only, and {@code null} for a group stamped from an object the user drew by
     * hand. The nets themselves are stored in full on each member, exactly as they are for a
     * lone object, so a group opens even when the library method behind it is gone.
     */
    private final NetTemplateRef template;

    /**
     * @param name what the group is called on the canvas and in the structure view
     * @param template the library method the shared net came from, or {@code null}
     */
    public GraphObjectGroup(String name, NetTemplateRef template) {
        this.name = Objects.requireNonNull(name, "name");
        this.template = template;
    }

    /**
     * @return what the group is called
     */
    public String getName() {
        return name;
    }

    /**
     * @param name the new name
     */
    public void setName(String name) {
        this.name = Objects.requireNonNull(name, "name");
    }

    /**
     * @return the library method the shared net came from, or {@code null} if it was drawn
     */
    public NetTemplateRef getTemplate() {
        return template;
    }

    /**
     * @return the members in stamping order, read-only
     */
    public List<GraphObjectFrame> getMembers() {
        return Collections.unmodifiableList(members);
    }

    /**
     * @return how many objects the group holds — the {@code k} of {@code multiply}
     */
    public int size() {
        return members.size();
    }

    /**
     * @param frame any Petri-object
     * @return whether it is one of this group's members
     */
    public boolean contains(GraphObjectFrame frame) {
        return members.contains(frame);
    }

    /**
     * Adds a member at the end of the stamping order.
     *
     * @param frame the object to add; ignored if it is already a member
     */
    public void add(GraphObjectFrame frame) {
        if (frame != null && !members.contains(frame)) {
            members.add(frame);
        }
    }

    /**
     * Drops a member — used when the object itself is removed from the canvas.
     *
     * @param frame the object that has gone
     * @return true if it had been a member
     */
    public boolean remove(GraphObjectFrame frame) {
        return members.remove(frame);
    }

    @Override
    public String toString() {
        return name + " ×" + members.size();
    }
}

package ua.stetsenkoinna.graphnet;

import java.awt.Rectangle;
import java.awt.geom.Point2D;

/**
 * Something on the canvas an operation can act on without caring which kind it turned out to be:
 * a place or transition ({@link GraphElement}), or the frame that marks out a Petri-object
 * ({@link GraphObjectFrame}).
 *
 * <p>An interface, not a shared base class, on purpose. {@link GraphElement} already has its own
 * superclass chain and state a frame does not want, and it sits inside every saved {@code .pns}
 * — which is why it declared no {@code serialVersionUID} of its own until this interface made one
 * necessary: left to the compiler, adding {@code implements CanvasItem} recomputes the id and
 * every file written before that stops loading. {@link GraphObjectFrame} is not a place or a
 * transition either, so folding the two into one class would buy nothing beyond what they already
 * share, which is exactly what this interface names instead: where one is, whether a point lands
 * on it, and how to move it — the three things an operation over "whatever is on the canvas"
 * actually needs, regardless of which kind it turned out to be.
 *
 * <p>Deliberately small. {@link #moveBy} moves only this item, never a frame's nested subtree or
 * the elements it claims — that cascade needs {@link GraphCanvasModel} to walk, since a frame
 * does not hold pointers to its own children or members, so it stays orchestrated by the caller,
 * one {@code moveBy} per item in the subtree, rather than hidden inside the interface.
 */
public interface CanvasItem {

    /**
     * @param point a point in canvas coordinates
     * @return true if the point lands on this item — what a click, or a link being drawn to it,
     *         hit-tests against
     */
    boolean containsPoint(Point2D point);

    /**
     * @return the rectangle this item currently occupies, in canvas coordinates
     */
    Rectangle getBounds();

    /**
     * Moves this item by a delta, keeping its own anchor: an element keeps its centre where it
     * is, a frame keeps its top-left corner — each already had its own notion of "where it is"
     * before this interface existed, and this does not change that. It only gives every caller
     * one name for "move it" regardless of which anchor it happens to use underneath.
     *
     * @param dx horizontal shift, in canvas coordinates
     * @param dy vertical shift, in canvas coordinates
     */
    void moveBy(int dx, int dy);
}

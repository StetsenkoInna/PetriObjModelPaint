package ua.stetsenkoinna.graphpresentation.objmodel;

import org.junit.Test;
import ua.stetsenkoinna.graphnet.GraphCanvasModel;
import ua.stetsenkoinna.graphnet.GraphObjectFrame;
import ua.stetsenkoinna.graphnet.GraphPetriNet;

import java.awt.Rectangle;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/**
 * The strip's model: which canvases are open, which is active, and what happens to a canvas whose
 * frame goes away.
 */
public class CanvasStackTest {

    private static GraphObjectFrame frame(String name) {
        return new GraphObjectFrame(name, new Rectangle(0, 0, 300, 200));
    }

    @Test
    public void aFreshStackHoldsTheNetsOwnCanvasAlone() {
        CanvasStack stack = new CanvasStack(new GraphCanvasModel("M", new GraphPetriNet()));

        assertEquals(List.of(), stack.getOpen().stream().filter(java.util.Objects::nonNull).toList());
        assertEquals(1, stack.getOpen().size());
        assertNull("index 0 is always the net itself", stack.getActive());
        assertTrue(stack.isRootOnly());
    }

    @Test
    public void openingANestedCanvasOpensItsWholeChainOutermostFirst() {
        GraphCanvasModel model = new GraphCanvasModel("M", new GraphPetriNet());
        GraphObjectFrame outer = frame("Outer");
        GraphObjectFrame middle = frame("Middle");
        GraphObjectFrame inner = frame("Inner");
        model.getFrames().addAll(List.of(outer, middle, inner));
        model.nest(middle, outer);
        model.nest(inner, middle);
        CanvasStack stack = new CanvasStack(model);

        stack.open(inner);

        assertEquals("the pill order is the breadcrumb",
                java.util.Arrays.asList(null, outer, middle, inner),
                new java.util.ArrayList<>(stack.getOpen()));
        assertSame(inner, stack.getActive());
    }

    @Test
    public void openingAnAlreadyOpenCanvasActivatesItInsteadOfDuplicatingIt() {
        GraphCanvasModel model = new GraphCanvasModel("M", new GraphPetriNet());
        GraphObjectFrame first = frame("First");
        GraphObjectFrame second = frame("Second");
        model.getFrames().addAll(List.of(first, second));
        CanvasStack stack = new CanvasStack(model);
        stack.open(first);
        stack.open(second);

        stack.open(first);

        assertEquals(3, stack.getOpen().size());
        assertSame(first, stack.getActive());
    }

    @Test
    public void closingACanvasCascadesToItsDescendantsAndFallsBackToTheNearestAncestor() {
        GraphCanvasModel model = new GraphCanvasModel("M", new GraphPetriNet());
        GraphObjectFrame outer = frame("Outer");
        GraphObjectFrame middle = frame("Middle");
        GraphObjectFrame inner = frame("Inner");
        model.getFrames().addAll(List.of(outer, middle, inner));
        model.nest(middle, outer);
        model.nest(inner, middle);
        CanvasStack stack = new CanvasStack(model);
        stack.open(inner);

        stack.close(middle);

        assertEquals("a canvas whose enclosing canvas is gone has no chain left to read",
                java.util.Arrays.asList(null, outer), new java.util.ArrayList<>(stack.getOpen()));
        assertSame("the nearest surviving ancestor becomes active", outer, stack.getActive());
    }

    @Test
    public void theNetsOwnCanvasCanNeverBeClosed() {
        GraphCanvasModel model = new GraphCanvasModel("M", new GraphPetriNet());
        GraphObjectFrame only = frame("Only");
        model.getFrames().add(only);
        CanvasStack stack = new CanvasStack(model);
        stack.open(only);

        stack.close(null);
        assertEquals(2, stack.getOpen().size());

        stack.close(only);
        assertEquals(1, stack.getOpen().size());
        assertNull(stack.getActive());
    }

    @Test
    public void pruningARemovedFrameClosesItsCanvasAndItsDescendants() {
        // Eager where the web editor is lazy: a pill here holds the live frame, so a canvas whose
        // frame is off the document cannot be painted at all rather than merely being stale.
        GraphCanvasModel model = new GraphCanvasModel("M", new GraphPetriNet());
        GraphObjectFrame parent = frame("Parent");
        GraphObjectFrame child = frame("Child");
        model.getFrames().addAll(List.of(parent, child));
        model.nest(child, parent);
        CanvasStack stack = new CanvasStack(model);
        stack.open(child);

        stack.pruneRemoved(parent);

        assertEquals(List.of(), stack.getOpen().stream().filter(java.util.Objects::nonNull).toList());
        assertNull(stack.getActive());
    }

    @Test
    public void resetDropsEveryCanvasButTheNetsAndAnnouncesIt() {
        GraphCanvasModel model = new GraphCanvasModel("M", new GraphPetriNet());
        GraphObjectFrame open = frame("Open");
        model.getFrames().add(open);
        CanvasStack stack = new CanvasStack(model);
        stack.open(open);
        int[] announcements = {0};
        stack.addChangeListener(() -> announcements[0]++);

        stack.reset();

        assertTrue(stack.isRootOnly());
        assertNull(stack.getActive());
        assertEquals(1, announcements[0]);
    }
}

package ua.stetsenkoinna.graphpresentation.objmodel;

import org.junit.Test;
import ua.stetsenkoinna.graphnet.GraphCanvasModel;
import ua.stetsenkoinna.graphnet.GraphObjectFrame;
import ua.stetsenkoinna.graphnet.GraphPetriNet;

import javax.swing.AbstractButton;
import javax.swing.JButton;
import javax.swing.JToggleButton;
import java.awt.Component;
import java.awt.Container;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/**
 * The strip itself: one pill per open canvas, badged with its level, and hidden while there is
 * nothing to navigate. Constructible without a display, so this runs anywhere.
 */
public class CanvasTabsBarTest {

    private static GraphObjectFrame frame(String name) {
        return new GraphObjectFrame(name, new Rectangle(0, 0, 300, 200));
    }

    private static <T extends Component> List<T> descendants(Container root, Class<T> type) {
        List<T> found = new ArrayList<>();
        for (Component child : root.getComponents()) {
            if (type.isInstance(child)) {
                found.add(type.cast(child));
            }
            if (child instanceof Container container) {
                found.addAll(descendants(container, type));
            }
        }
        return found;
    }

    private static CanvasTabsBar barFor(CanvasStack stack, GraphCanvasModel model,
            List<GraphObjectFrame> activated, List<GraphObjectFrame> closed) {
        return new CanvasTabsBar(stack, model, activated::add, closed::add);
    }

    @Test
    public void aPlainNetWithNoObjectsShowsNoStripAtAll() {
        GraphCanvasModel model = new GraphCanvasModel("M", new GraphPetriNet());
        CanvasStack stack = new CanvasStack(model);

        CanvasTabsBar bar = barFor(stack, model, new ArrayList<>(), new ArrayList<>());

        assertFalse("a strip would only take room away from the drawing", bar.isVisible());
    }

    @Test
    public void theStripAppearsAsSoonAsTheDocumentHasAnObject() {
        GraphCanvasModel model = new GraphCanvasModel("M", new GraphPetriNet());
        CanvasStack stack = new CanvasStack(model);
        CanvasTabsBar bar = barFor(stack, model, new ArrayList<>(), new ArrayList<>());

        model.getFrames().add(frame("Machine"));
        stack.notifyChanged();

        assertTrue(bar.isVisible());
        assertEquals("0  Net", descendants(bar, JToggleButton.class).getFirst().getText());
    }

    @Test
    public void everyOpenCanvasGetsOnePillBadgedWithItsLevel() {
        GraphCanvasModel model = new GraphCanvasModel("M", new GraphPetriNet());
        GraphObjectFrame machine = frame("Machine");
        GraphObjectFrame buffer = frame("Buffer");
        model.getFrames().addAll(List.of(machine, buffer));
        model.nest(buffer, machine);
        CanvasStack stack = new CanvasStack(model);
        CanvasTabsBar bar = barFor(stack, model, new ArrayList<>(), new ArrayList<>());

        stack.open(buffer);

        List<JToggleButton> pills = descendants(bar, JToggleButton.class);
        assertEquals(List.of("0  Net", "1  Machine", "2  Buffer"),
                pills.stream().map(AbstractButton::getText).toList());
    }

    @Test
    public void onlyTheNonRootPillsCarryACloseControl() {
        GraphCanvasModel model = new GraphCanvasModel("M", new GraphPetriNet());
        GraphObjectFrame machine = frame("Machine");
        model.getFrames().add(machine);
        CanvasStack stack = new CanvasStack(model);
        List<GraphObjectFrame> closed = new ArrayList<>();
        CanvasTabsBar bar = barFor(stack, model, new ArrayList<>(), closed);
        stack.open(machine);

        List<JButton> closers = descendants(bar, JButton.class);
        assertEquals("the net's own canvas can never be closed", 1, closers.size());

        closers.getFirst().doClick();
        assertEquals(List.of(machine), closed);
    }

    @Test
    public void theActivePillFollowsTheStackAndClickingOnePillAsksToActivateIt() {
        GraphCanvasModel model = new GraphCanvasModel("M", new GraphPetriNet());
        GraphObjectFrame machine = frame("Machine");
        model.getFrames().add(machine);
        CanvasStack stack = new CanvasStack(model);
        List<GraphObjectFrame> activated = new ArrayList<>();
        CanvasTabsBar bar = barFor(stack, model, activated, new ArrayList<>());
        stack.open(machine);

        List<JToggleButton> pills = descendants(bar, JToggleButton.class);
        assertFalse("the net's pill is not the active one any more", pills.get(0).isSelected());
        assertTrue(pills.get(1).isSelected());

        pills.get(0).doClick();
        assertEquals(1, activated.size());
        assertSame("clicking the net's pill asks for the net's own canvas", null, activated.getFirst());
    }

    @Test
    public void aPillFollowsTheObjectsCurrentName() {
        GraphCanvasModel model = new GraphCanvasModel("M", new GraphPetriNet());
        GraphObjectFrame machine = frame("Machine");
        model.getFrames().add(machine);
        CanvasStack stack = new CanvasStack(model);
        CanvasTabsBar bar = barFor(stack, model, new ArrayList<>(), new ArrayList<>());
        stack.open(machine);

        machine.setName("Lathe");
        stack.notifyChanged();

        assertEquals("1  Lathe", descendants(bar, JToggleButton.class).get(1).getText());
    }
}

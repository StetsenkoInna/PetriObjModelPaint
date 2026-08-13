package ua.stetsenkoinna.graphpresentation;

import org.junit.Test;

import ua.stetsenkoinna.graphnet.GraphArcOut;
import ua.stetsenkoinna.graphnet.GraphElement;
import ua.stetsenkoinna.graphnet.GraphObjectFrame;
import ua.stetsenkoinna.graphnet.GraphPetriPlace;
import ua.stetsenkoinna.graphnet.GraphPetriTransition;
import ua.stetsenkoinna.petriobj.PetriP;
import ua.stetsenkoinna.petriobj.PetriT;

import java.awt.Rectangle;
import java.awt.geom.Point2D;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

/**
 * What the Arc tool's release point resolves to when it lands on another Petri-object's
 * territory, not just a free element on the active canvas.
 *
 * <p>The tool used to reject any element belonging to another object outright, even one plainly
 * visible because that object is fully expanded - the same silent "nothing happens" a click on
 * empty canvas gives, which made it look like the tool could never reach into an object at all.
 * It now resolves to the real element while the object showing it is expanded, and to the real
 * element a port stands in for while the object is collapsed - the same two cases
 * {@code crossingAnchor} already draws an existing crossing arc reaching for.
 */
public class ArcToolCrossObjectTest {

    private static Object invoke(PetriNetsPanel panel, String name, Class<?>[] types, Object... args) {
        try {
            Method method = PetriNetsPanel.class.getDeclaredMethod(name, types);
            method.setAccessible(true);
            return method.invoke(panel, args);
        } catch (InvocationTargetException failure) {
            throw new AssertionError(failure.getCause());
        } catch (ReflectiveOperationException broken) {
            throw new AssertionError(broken);
        }
    }

    private static GraphElement arcToolTargetAt(PetriNetsPanel panel, Point2D point) {
        return (GraphElement) invoke(panel, "arcToolTargetAt", new Class<?>[]{Point2D.class}, point);
    }

    @Test
    public void resolvesDirectlyToAMemberOfAnotherExpandedObject() {
        PetriNetsPanel panel = new PetriNetsPanel(null, false);

        GraphPetriPlace framedPlace = new GraphPetriPlace(new PetriP("Pin", 1), 1);
        framedPlace.setNewCoordinates(new Point2D.Double(120, 140));
        panel.getGraphNet().getGraphPetriPlaceList().add(framedPlace);

        GraphObjectFrame frame = new GraphObjectFrame("Other", new Rectangle(40, 40, 160, 160));
        panel.getCanvasModel().claim(frame, framedPlace);
        panel.addObjectFrame(frame);
        // Left expanded (the default): the place is drawn for real, so the tool should reach it
        // directly - no port needed, the same as any free element.

        assertSame("a visibly-drawn member of another object is a valid arc target now",
                framedPlace, arcToolTargetAt(panel, new Point2D.Double(120, 140)));
    }

    @Test
    public void resolvesThroughThePortWhenTheObjectIsCollapsed() {
        PetriNetsPanel panel = new PetriNetsPanel(null, false);

        GraphPetriPlace framedPlace = new GraphPetriPlace(new PetriP("Pin", 1), 1);
        framedPlace.setNewCoordinates(new Point2D.Double(120, 140));
        panel.getGraphNet().getGraphPetriPlaceList().add(framedPlace);

        GraphObjectFrame frame = new GraphObjectFrame("Hidden", new Rectangle(40, 40, 160, 160));
        panel.getCanvasModel().claim(frame, framedPlace);
        panel.addObjectFrame(frame);
        frame.setCollapsed(true);

        // The place itself is nowhere on screen now, so only its port's position resolves it.
        assertNull("the real, hidden place is not a target at its own (invisible) coordinates",
                arcToolTargetAt(panel, new Point2D.Double(120, 140)));

        var port = panel.getCanvasModel().portsOf(frame).stream()
                .filter(p -> p.getElement() == framedPlace)
                .findFirst()
                .orElseThrow();

        assertSame("clicking the port reaches the real place it stands for",
                framedPlace, arcToolTargetAt(panel, new Point2D.Double(port.getPosition().x, port.getPosition().y)));
    }

    @Test
    public void arcToolCompletesAConnectionIntoAnExpandedObject() {
        // End to end: the resolved target is not just found, it can actually finish the arc -
        // the same call the mouse handler makes once arcToolTargetAt has picked a target.
        PetriNetsPanel panel = new PetriNetsPanel(null, false);

        GraphPetriTransition freeTransition = new GraphPetriTransition(new PetriT("Free", 1.0), 1);
        freeTransition.setNewCoordinates(new Point2D.Double(400, 140));
        panel.getGraphNet().getGraphPetriTransitionList().add(freeTransition);

        GraphPetriPlace framedPlace = new GraphPetriPlace(new PetriP("Pin", 1), 2);
        framedPlace.setNewCoordinates(new Point2D.Double(120, 140));
        panel.getGraphNet().getGraphPetriPlaceList().add(framedPlace);
        GraphObjectFrame frame = new GraphObjectFrame("Other", new Rectangle(40, 40, 160, 160));
        panel.getCanvasModel().claim(frame, framedPlace);
        panel.addObjectFrame(frame);

        // Mirrors what mousePressed/mouseReleased do: a GraphArcOut armed from the free
        // transition, finished onto whatever arcToolTargetAt resolves the release point to.
        GraphArcOut arc = new GraphArcOut();
        arc.settingNewArc(freeTransition);
        boolean finished = arc.finishSettingNewArc(arcToolTargetAt(panel, new Point2D.Double(120, 140)));

        org.junit.Assert.assertTrue("a place inside another expanded object completes a valid arc",
                finished);
        org.junit.Assert.assertSame(framedPlace, arc.getEndElement());
    }
}

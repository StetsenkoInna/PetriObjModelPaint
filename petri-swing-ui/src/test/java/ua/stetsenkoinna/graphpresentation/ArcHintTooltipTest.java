package ua.stetsenkoinna.graphpresentation;

import org.junit.Test;

import ua.stetsenkoinna.graphnet.GraphArcIn;
import ua.stetsenkoinna.graphnet.GraphArcOut;
import ua.stetsenkoinna.graphnet.GraphPetriPlace;
import ua.stetsenkoinna.graphnet.GraphPetriTransition;
import ua.stetsenkoinna.graphnet.GraphPlaceFusion;
import ua.stetsenkoinna.petriobj.PetriP;
import ua.stetsenkoinna.petriobj.PetriT;

import java.awt.event.MouseEvent;
import java.awt.geom.Point2D;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

/**
 * Hovering a connection names its kind and says what it does, and hovering an element says
 * nothing - its own drawing already does.
 */
public class ArcHintTooltipTest {

    private static MouseEvent hoverAt(PetriNetsPanel panel, double x, double y) {
        return new MouseEvent(panel, MouseEvent.MOUSE_MOVED, 0, 0, (int) x, (int) y, 0, false);
    }

    @Test
    public void anArcAndASharedPlaceLinkEachNameTheirKind() {
        PetriP.initNext();
        PetriNetsPanel panel = new PetriNetsPanel(null, true);
        GraphPetriPlace in = new GraphPetriPlace(new PetriP("In", 1), 1);
        in.setNewCoordinates(new Point2D.Double(100, 100));
        GraphPetriTransition t = new GraphPetriTransition(new PetriT("T", 1.0), 2);
        t.setNewCoordinates(new Point2D.Double(300, 100));
        GraphPetriPlace out = new GraphPetriPlace(new PetriP("Out", 0), 3);
        out.setNewCoordinates(new Point2D.Double(500, 100));
        GraphPetriPlace copy = new GraphPetriPlace(new PetriP("Copy", 0), 4);
        copy.setNewCoordinates(new Point2D.Double(500, 400));
        panel.getGraphNet().getGraphPetriPlaceList().add(in);
        panel.getGraphNet().getGraphPetriPlaceList().add(out);
        panel.getGraphNet().getGraphPetriPlaceList().add(copy);
        panel.getGraphNet().getGraphPetriTransitionList().add(t);

        GraphArcIn input = new GraphArcIn();
        input.settingNewArc(in);
        input.finishSettingNewArc(t);
        input.updateCoordinates();
        panel.getGraphNet().getGraphArcInList().add(input);
        GraphArcOut output = new GraphArcOut();
        output.settingNewArc(t);
        output.finishSettingNewArc(out);
        output.updateCoordinates();
        panel.getGraphNet().getGraphArcOutList().add(output);
        GraphPlaceFusion link = panel.getCanvasModel().joinPlaces(out, copy);
        panel.getCanvasModel().syncFusions();

        assertEquals(ArcHints.INPUT, panel.getToolTipText(hoverAt(panel, 200, 100)));
        assertEquals(ArcHints.OUTPUT, panel.getToolTipText(hoverAt(panel, 400, 100)));
        assertEquals(ArcHints.SHARED_PLACE, panel.getToolTipText(hoverAt(panel, 500, 250)));
        assertNull("nothing over a place itself", panel.getToolTipText(hoverAt(panel, 100, 100)));

        input.setInf(true);
        assertEquals(ArcHints.INFORMATIONAL, panel.getToolTipText(hoverAt(panel, 200, 100)));
        assertEquals(link, panel.findSharedPlace(new Point2D.Double(500, 250)));
    }
}

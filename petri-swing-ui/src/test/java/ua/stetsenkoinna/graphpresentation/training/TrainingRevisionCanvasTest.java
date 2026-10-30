package ua.stetsenkoinna.graphpresentation.training;

import java.awt.Point;
import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;
import org.junit.Test;
import ua.stetsenkoinna.graphpresentation.PetriNetsPanel;
import static org.junit.Assert.*;

public class TrainingRevisionCanvasTest {
    @Test public void readOnlyCanvasUsesDragPanningAndPointerZoomWithoutMovingNodes() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var canvas = new PetriNetsPanel(null, false);
            try {
                canvas.enablePreviewNavigation();
                var place = new ua.stetsenkoinna.graphnet.GraphPetriPlace(new ua.stetsenkoinna.petriobj.PetriP("Original", 2), 1);
                place.setNewCoordinates(new Point(700, 600)); canvas.getGraphNet().getGraphPetriPlaceList().add(place);
                var source = place.getGraphElementCenter();
                var scroll = new JScrollPane(canvas); scroll.setSize(600, 400); scroll.doLayout();
                canvas.setSize(canvas.getPreferredSize()); scroll.getViewport().setViewPosition(new Point(400, 300));
                canvas.dispatchEvent(new MouseEvent(canvas, MouseEvent.MOUSE_PRESSED, 0, InputEvent.BUTTON1_DOWN_MASK, 500, 400, 1, false, MouseEvent.BUTTON1));
                canvas.dispatchEvent(new MouseEvent(canvas, MouseEvent.MOUSE_DRAGGED, 1, InputEvent.BUTTON1_DOWN_MASK, 450, 350, 0, false, MouseEvent.NOBUTTON));
                canvas.dispatchEvent(new MouseEvent(canvas, MouseEvent.MOUSE_RELEASED, 2, 0, 450, 350, 1, false, MouseEvent.BUTTON1));
                assertEquals(new Point(450, 350), scroll.getViewport().getViewPosition());
                canvas.dispatchEvent(new MouseWheelEvent(canvas, MouseEvent.MOUSE_WHEEL, 3, InputEvent.CTRL_DOWN_MASK, 600, 450, 0, false, MouseWheelEvent.WHEEL_UNIT_SCROLL, 1, -1));
                assertTrue(canvas.getScale() > 1); assertEquals(source, place.getGraphElementCenter());
                assertTrue(canvas.getSelection().isEmpty());
            } finally { canvas.setArcFrame.dispose(); canvas.setPositionFrame.dispose(); canvas.setTransitionFrame.dispose(); }
        });
    }
}

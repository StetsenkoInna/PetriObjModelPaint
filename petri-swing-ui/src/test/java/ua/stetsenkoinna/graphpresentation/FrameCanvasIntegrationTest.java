package ua.stetsenkoinna.graphpresentation;

import java.awt.Component;
import java.awt.Container;
import java.awt.Rectangle;
import java.awt.Window;
import java.awt.geom.Point2D;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import javax.swing.AbstractButton;
import javax.swing.JButton;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import ua.stetsenkoinna.graphnet.*;
import ua.stetsenkoinna.graphpresentation.training.SwingCapture;
import ua.stetsenkoinna.petriobj.*;
import ua.stetsenkoinna.pnml.PnmlModelGenerator;
import static org.junit.Assert.*;

public class FrameCanvasIntegrationTest {
    @Rule public TemporaryFolder folder = new TemporaryFolder();
    private PetriNetsFrame frame;
    private Set<Window> existingWindows;

    @Before public void setup() throws Exception {
        SwingCapture.call(() -> {
            existingWindows = new HashSet<>(Arrays.asList(Window.getWindows()));
            frame = new PetriNetsFrame();
            frame.setSize(1100, 750);
            layout(frame);
            assertTrue(frame.getPetriNetsPanel().getVisibleRect().width > 0);
            return null;
        });
    }

    @After public void cleanup() throws Exception {
        if (frame != null) frame.getMcpServer().close();
        SwingCapture.call(() -> {
            for (Window window : Window.getWindows()) if (!existingWindows.contains(window)) window.dispose();
            return null;
        });
    }

    private static void layout(Component component) {
        if (component instanceof Container container) {
            container.doLayout();
            for (Component child : container.getComponents()) layout(child);
        }
    }

    private JMenuItem viewItem(String title) {
        for (int i = 0; i < frame.getJMenuBar().getMenuCount(); i++) {
            JMenu menu = frame.getJMenuBar().getMenu(i);
            if (menu != null && "View".equals(menu.getText())) {
                for (int j = 0; j < menu.getItemCount(); j++) {
                    JMenuItem item = menu.getItem(j);
                    if (item != null && title.equals(item.getText())) return item;
                }
            }
        }
        throw new AssertionError("Missing View menu item: " + title);
    }

    private static AbstractButton accessibleButton(Container parent, String name) {
        for (Component child : parent.getComponents()) {
            if (child instanceof JButton button
                    && name.equals(button.getAccessibleContext().getAccessibleName())) return button;
            if (child instanceof Container container) {
                AbstractButton found = accessibleButton(container, name);
                if (found != null) return found;
            }
        }
        return null;
    }

    private static GraphCanvasModel negativeModel() {
        var net = new GraphPetriNet();
        var input = new GraphPetriPlace(new PetriP("p_input", "Input", 1), 101);
        var output = new GraphPetriPlace(new PetriP("p_output", "Output", 0), 102);
        var transition = new GraphPetriTransition(new PetriT("t_work", "Work", 1), 103);
        input.getPetriPlace().setNumber(0); output.getPetriPlace().setNumber(1);
        transition.getPetriTransition().setNumber(0);
        input.setNewCoordinates(new Point2D.Double(-1100, -900));
        output.setNewCoordinates(new Point2D.Double(-700, -900));
        transition.setNewCoordinates(new Point2D.Double(-900, -900));
        net.getGraphPetriPlaceList().add(input); net.getGraphPetriPlaceList().add(output);
        net.getGraphPetriTransitionList().add(transition);
        var consuming = new ArcIn("a_input", 0, 0, 1);
        consuming.setNameP("Input"); consuming.setNameT("Work");
        var incoming = new GraphArcIn(consuming);
        incoming.settingNewArc(input); incoming.finishSettingNewArc(transition); incoming.updateCoordinates();
        net.getGraphArcInList().add(incoming);
        var producing = new ArcOut("a_output", 0, 1, 1);
        producing.setNameP("Output"); producing.setNameT("Work");
        var outgoing = new GraphArcOut(producing);
        outgoing.settingNewArc(transition); outgoing.finishSettingNewArc(output); outgoing.updateCoordinates();
        net.getGraphArcOutList().add(outgoing);
        var model = new GraphCanvasModel("Negative coordinates", net);
        var object = new GraphObjectFrame("Worker", new Rectangle(-1200, -1000, 600, 220));
        object.setModelId("o_worker"); model.getFrames().add(object);
        model.claim(object, input); model.claim(object, output); model.claim(object, transition);
        return model;
    }

    private static void assertCamera(PetriNetsPanel panel, PetriNetsPanel.CameraState expected) {
        var actual = panel.getCamera();
        assertEquals(expected.center_x(), actual.center_x(), 0.001);
        assertEquals(expected.center_y(), actual.center_y(), 0.001);
        assertEquals(expected.scale(), actual.scale(), 0.000001);
    }

    @Test public void spacingToolbarButtonIsAccessibleAndSharesTheViewMenuAction() throws Exception {
        SwingCapture.call(() -> {
            AbstractButton button = accessibleButton(frame.getContentPane(), "Improve spacing");
            assertNotNull("The spacing command is available in the toolbar", button);
            assertEquals("Improve spacing", button.getToolTipText());
            assertNotNull("Both entry points use one command", button.getAction());
            assertSame(button.getAction(), viewItem("Improve spacing").getAction());
            button.getAction().setEnabled(false);
            assertFalse(button.isEnabled());
            assertFalse(viewItem("Improve spacing").isEnabled());
            button.getAction().setEnabled(true);
            assertTrue(button.isEnabled());
            assertTrue(viewItem("Improve spacing").isEnabled());
            return null;
        });
    }

    @Test public void centerMenuMovesOnlyTheCameraAndShowsNegativeCoordinates() throws Exception {
        SwingCapture.call(() -> {
            frame.applyMcpCanvas(negativeModel(), true);
            var panel = frame.getPetriNetsPanel();
            var place = panel.getGraphNet().getGraphPetriPlaceList().getFirst();
            Point2D point = new Point2D.Double(place.getGraphElementCenter().getX(), place.getGraphElementCenter().getY());
            Rectangle objectBounds = new Rectangle(panel.getCanvasModel().getFrames().getFirst().getExpandedBounds());
            panel.setCamera(80000, -90000, 0.3);
            viewItem("Center on net").doClick(0);
            assertEquals(point, place.getGraphElementCenter());
            assertEquals(objectBounds, panel.getCanvasModel().getFrames().getFirst().getExpandedBounds());
            assertTrue(panel.getVisibleWorldBounds().contains(objectBounds));
            assertTrue(panel.getCamera().center_x() < 0);
            assertTrue(panel.getCamera().center_y() < 0);
            return null;
        });
    }

    @Test public void applyingAndOpeningDocumentsFitTheirNegativeGeometry() throws Exception {
        var file = folder.newFile("negative.pnml").toPath();
        SwingCapture.call(() -> {
            var model = negativeModel();
            frame.applyMcpCanvas(model, true);
            var panel = frame.getPetriNetsPanel();
            assertTrue(panel.getVisibleWorldBounds().contains(model.getFrames().getFirst().getExpandedBounds()));
            Files.writeString(file, new PnmlModelGenerator().generateXml(model.toObjModel()));
            panel.setCamera(70000, 60000, 0.2);
            frame.openProjectFile(file.toFile());
            assertEquals(-1100, panel.getGraphNet().getGraphPetriPlaceList().getFirst().getGraphElementCenter().getX(), 0.001);
            assertTrue(panel.getVisibleWorldBounds().contains(panel.getCanvasModel().getFrames().getFirst().getExpandedBounds()));
            assertTrue(panel.getCamera().center_x() < 0);
            assertTrue(panel.getCamera().center_y() < 0);
            return null;
        });
    }

    @Test public void ordinaryEditsUndoAndRedoPreserveCameraAndFocusedObject() throws Exception {
        SwingCapture.call(() -> {
            frame.applyMcpCanvas(negativeModel(), true);
            var panel = frame.getPetriNetsPanel();
            panel.openObjectCanvas(panel.getCanvasModel().getFrames().getFirst());
            panel.setCamera(-12345, 67890, 0.65);
            var camera = panel.getCamera();
            var edited = new GraphCanvasModel(panel.getCanvasModel());
            edited.setName("Edited model");
            frame.applyMcpCanvas(edited, false);
            assertCamera(panel, camera);
            assertEquals("o_worker", panel.getFocusedFrame().getModelId());
            panel.setCamera(54321, -98765, 1.35);
            var navigatedCamera = panel.getCamera();
            frame.undoMcpModel(false);
            assertCamera(panel, navigatedCamera);
            assertEquals("o_worker", panel.getFocusedFrame().getModelId());
            frame.undoMcpModel(true);
            assertCamera(panel, navigatedCamera);
            assertEquals("o_worker", panel.getFocusedFrame().getModelId());
            return null;
        });
    }
}

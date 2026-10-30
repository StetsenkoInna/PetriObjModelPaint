package ua.stetsenkoinna.graphpresentation.training;

import java.awt.*;
import java.awt.event.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import javax.swing.*;
import javax.swing.text.JTextComponent;
import org.junit.After;
import org.junit.Test;
import ua.stetsenkoinna.graphnet.*;
import ua.stetsenkoinna.graphpresentation.*;
import ua.stetsenkoinna.pnml.PnmlModelParser;
import static org.junit.Assert.*;

public class ReviewSimulationIntegrationTest {
    @After public void close() throws Exception { SwingUtilities.invokeAndWait(() -> { for (Window window : Window.getWindows()) window.dispose(); }); }
    @Test public void reviewSelectionCannotEditAndUsesTheUsualReadOnlyParameterForm() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var canvas = new PetriNetsPanel(null, PetriNetsPanel.Mode.REVIEW);
            try {
                var place = new GraphPetriPlace(new ua.stetsenkoinna.petriobj.PetriP("P0",2),10); place.setNewCoordinates(new Point(100,100));
                canvas.getGraphNet().getGraphPetriPlaceList().add(place);
                canvas.dispatchEvent(new MouseEvent(canvas,MouseEvent.MOUSE_PRESSED,0,InputEvent.BUTTON1_DOWN_MASK,100,100,1,false,MouseEvent.BUTTON1));
                canvas.dispatchEvent(new MouseEvent(canvas,MouseEvent.MOUSE_DRAGGED,1,InputEvent.BUTTON1_DOWN_MASK,150,150,0,false,MouseEvent.NOBUTTON));
                canvas.dispatchEvent(new MouseEvent(canvas,MouseEvent.MOUSE_RELEASED,2,0,150,150,1,false,MouseEvent.BUTTON1));
                assertEquals(new Point(100,100),place.getGraphElementCenter());
                canvas.deleteSelection(); canvas.duplicateSelection(); canvas.pasteAction(); canvas.setTool(CanvasTool.ADD_PLACE);
                assertEquals(1,canvas.getGraphNet().getGraphPetriPlaceList().size()); assertEquals(CanvasTool.SELECT,canvas.getTool());
                canvas.dispatchEvent(new MouseEvent(canvas,MouseEvent.MOUSE_CLICKED,3,0,100,100,2,false,MouseEvent.BUTTON1));
                assertTrue(canvas.setPositionFrame.isVisible());
                for (Component c : descendants(canvas.setPositionFrame)) if (c instanceof JTextComponent text) { assertFalse(text.isEditable()); text.setText("999"); }
                for (Component c : descendants(canvas.setPositionFrame)) if (c instanceof JButton button && "OK".equals(button.getText())) { assertFalse(button.isVisible()); button.doClick(); }
                assertEquals("P0",place.getPetriPlace().getName()); assertEquals(2,place.getPetriPlace().getMark());
            } finally { canvas.setArcFrame.dispose(); canvas.setPositionFrame.dispose(); canvas.setTransitionFrame.dispose(); }
        });
    }
    @Test public void reviewRunsAndResetUseIndependentBackupsAndLeaveTheEditorUntouched() throws Exception {
        byte[] pnml = SimulationEvidenceTest.cyclicModel(2,3);
        TrainingRevisionCanvasWindow[] windows = new TrainingRevisionCanvasWindow[2]; PetriNetsFrame[] editor={null};
        List<Map<String,Object>> records = new CopyOnWriteArrayList<>();
        SwingUtilities.invokeAndWait(() -> {
            try {
                editor[0]=new PetriNetsFrame(); editor[0].getPetriNetsPanel().setCanvasModel(GraphCanvasModel.fromObjModel(new PnmlModelParser().parseXml(new String(pnml,java.nio.charset.StandardCharsets.UTF_8))));
                for (int i=0;i<2;i++) {
                    windows[i]=new TrainingRevisionCanvasWindow(editor[0],"Saved revision",Map.of("model.pnml",pnml));
                    windows[i].setSimulationEvidenceListener(records::add);
                    for(Component c:descendants(windows[i])) if(c instanceof JSpinner spinner && "Review simulation horizon".equals(spinner.getAccessibleContext().getAccessibleName())) spinner.setValue(6.0);
                }
            } catch(Exception failure){throw new RuntimeException(failure);}
        });
        var ids=windows[0].canvas().getGraphNet().getGraphPetriPlaceList().stream().map(GraphPetriPlace::getId).toList();
        for(var window:windows){ SwingUtilities.invokeAndWait(()->window.simulationControls().runNetButtonPressed()); await(()->window.simulationControls().stepBackAction.isEnabled()&&!window.isSimulationBusy()); }
        assertEquals(2,records.size()); assertEquals("completed",records.getFirst().get("status"));
        assertFalse(windows[0].getSimulationBackup().isEmpty()); assertFalse(windows[1].getSimulationBackup().isEmpty());
        assertNotSame(windows[0].getSimulationBackup(),windows[1].getSimulationBackup());
        SwingUtilities.invokeAndWait(()->windows[0].simulationControls().stopSimulationButtonPressed());
        assertFalse(windows[1].getSimulationBackup().isEmpty());
        SwingUtilities.invokeAndWait(()->windows[1].simulationControls().stopSimulationButtonPressed());
        assertEquals(ids,windows[0].canvas().getGraphNet().getGraphPetriPlaceList().stream().map(GraphPetriPlace::getId).toList());
        assertEquals(1,editor[0].getPetriNetsPanel().getGraphNet().getGraphPetriPlaceList().getFirst().getPetriPlace().getMark());
        assertTrue(editor[0].getSimulationBackup().isEmpty());
    }
    @Test public void animatedStepAndStopReuseTheSharedLifecycleAndKeepPartialTimeSeries() throws Exception {
        byte[] pnml=SimulationEvidenceTest.cyclicModel(2,3); TrainingRevisionCanvasWindow[] window={null}; List<Map<String,Object>> records=new CopyOnWriteArrayList<>();
        SwingUtilities.invokeAndWait(()->{try{
            window[0]=new TrainingRevisionCanvasWindow(null,"Saved revision",Map.of("model.pnml",pnml));window[0].setSimulationEvidenceListener(records::add);
            for(Component c:descendants(window[0]))if(c instanceof AnimationSpeedControl pace)pace.selectFastestSpeed();
            window[0].simulationControls().runOneEventButtonPressed();
        }catch(Exception error){throw new RuntimeException(error);}});
        await(()->window[0].animationModel!=null&&window[0].animationModel.isPaused()&&window[0].animationModel.getCurrentTime()>=2);
        assertEquals(2,window[0].animationModel.getCurrentTime(),0.00001);
        SwingUtilities.invokeAndWait(()->window[0].simulationControls().stopSimulationButtonPressed());
        await(()->!window[0].isSimulationBusy()&&!records.isEmpty()&&window[0].animationModel==null);
        assertEquals("stopped",records.getFirst().get("status"));
        var node=new com.fasterxml.jackson.databind.ObjectMapper().valueToTree(records.getFirst());
        assertEquals(3,node.path("trajectory").path("samples").size());
        assertTrue(window[0].getSimulationBackup().isEmpty());
    }
    @Test public void ordinaryMonitorCanCollectFromTheDetachedReviewModel() throws Exception {
        byte[] pnml=SimulationEvidenceTest.cyclicModel(2,3); TrainingRevisionCanvasWindow[] window={null}; List<Map<String,Object>> records=new CopyOnWriteArrayList<>();
        SwingUtilities.invokeAndWait(()->{try{
            window[0]=new TrainingRevisionCanvasWindow(null,"Saved revision",Map.of("model.pnml",pnml));window[0].setSimulationEvidenceListener(records::add);
            for(Component c:descendants(window[0]))if(c instanceof JSpinner spinner&&"Review simulation horizon".equals(spinner.getAccessibleContext().getAccessibleName()))spinner.setValue(6.0);
            window[0].openMonitor();
            for(Window candidate:Window.getWindows())if(candidate instanceof ua.stetsenkoinna.graphpresentation.statistic.StatisticMonitorDialog monitor){
                for(Component c:descendants(monitor))if(c instanceof JTextArea formula&&"Monitor formula".equals(formula.getAccessibleContext().getAccessibleName())){
                    formula.setText("P_AVG(O0.P0)");for(var listener:formula.getKeyListeners())listener.keyReleased(new KeyEvent(formula,KeyEvent.KEY_RELEASED,0,0,KeyEvent.VK_0,'0'));
                }
                assertTrue(monitor.getIsFormulaValid());
            }
            window[0].simulationControls().runNetButtonPressed();
        }catch(Exception error){throw new RuntimeException(error);}});
        await(()->!records.isEmpty()&&!window[0].isSimulationBusy());
        assertEquals(records.toString(),"completed",records.getFirst().get("status"));
        SwingUtilities.invokeAndWait(()->{for(Window candidate:Window.getWindows())if(candidate instanceof ua.stetsenkoinna.graphpresentation.statistic.StatisticMonitorDialog monitor)assertFalse(monitor.getElementsWatchMap().isEmpty());});
    }
    @Test public void exactBackupsPreserveSymbolicParametersAndIdentifiers() {
        var model=new GraphCanvasModel(); var p=new GraphPetriPlace(new ua.stetsenkoinna.petriobj.PetriP("Resources",2),15);
        p.getPetriPlace().setMarkParam("resources"); model.getNet().getGraphPetriPlaceList().add(p);
        var copy=GraphCanvasSnapshots.copy(model);var detached=copy.getNet().getGraphPetriPlaceList().getFirst();
        assertNotSame(p,detached);assertEquals(p.getId(),detached.getId());assertTrue(detached.getPetriPlace().markIsParam());assertEquals("resources",detached.getPetriPlace().getMarkParamName());
    }
    private static List<Component> descendants(Container root) { List<Component> out=new ArrayList<>();for(Component c:root.getComponents()){out.add(c);if(c instanceof Container child)out.addAll(descendants(child));}return out; }
    private static void await(java.util.function.BooleanSupplier condition)throws Exception{long deadline=System.nanoTime()+java.util.concurrent.TimeUnit.SECONDS.toNanos(10);while(System.nanoTime()<deadline){if(condition.getAsBoolean())return;Thread.sleep(20);}fail("Review simulation did not finish");}
}

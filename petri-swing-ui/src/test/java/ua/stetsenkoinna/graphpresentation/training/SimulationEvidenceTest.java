package ua.stetsenkoinna.graphpresentation.training;

import com.fasterxml.jackson.databind.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.junit.Test;
import ua.stetsenkoinna.graphnet.*;
import ua.stetsenkoinna.petriobj.*;
import ua.stetsenkoinna.pnml.PnmlModelGenerator;
import static org.junit.Assert.*;

public class SimulationEvidenceTest {
    private static final ObjectMapper JSON = new ObjectMapper();
    static byte[] cyclicModel(double firstDelay, double secondDelay) throws Exception {
        PetriP.initNext(); PetriT.initNext(); ArcIn.initNext(); ArcOut.initNext();
        var p0 = new PetriP("P0", 1); var p1 = new PetriP("P1", 0);
        var t0 = new PetriT("T0", firstDelay); var t1 = new PetriT("T1", secondDelay);
        var net = new PetriNet("Cycle", new ArrayList<>(List.of(p0,p1)), new ArrayList<>(List.of(t0,t1)),
                new ArrayList<>(List.of(new ArcIn(p0,t0,1),new ArcIn(p1,t1,1))), new ArrayList<>(List.of(new ArcOut(t0,p1,1),new ArcOut(t1,p0,1))));
        var graph = ua.stetsenkoinna.graphpresentation.SimpleNetGraphBuilder.build(net, new java.awt.Point(300,200)); var canvas = new GraphCanvasModel(); canvas.setNet(graph);
        return new PnmlModelGenerator().generateXml(canvas.toObjModel()).getBytes(StandardCharsets.UTF_8);
    }
    @Test public void samplesAreRightContinuousAtUnitTimesAndIncludeIntermediateStates() throws Exception {
        byte[] source = cyclicModel(2,3); byte[] unchanged = source.clone();
        var result = JSON.valueToTree(SimulationEvidence.automatic(source, new SimulationPlan(List.of(6.0),1,"",List.of())));
        assertArrayEquals(unchanged, source);
        var run = result.path("automatic_runs").get(0); assertEquals(run.toString(), "completed", run.path("status").asText());
        assertEquals(TrainingBundleWorkspace.hash(source), run.path("source_model_sha256").asText());
        var trajectory = run.path("trajectory"); var samples = trajectory.path("samples"); assertEquals(7,samples.size());
        int column = 1;
        for (var entry : trajectory.path("columns")) { if (entry.path("name").asText().equals("T0")) break; column++; }
        assertEquals(1,samples.get(0).get(column).asInt());
        assertEquals(1,samples.get(1).get(column).asInt());
        assertEquals(0,samples.get(2).get(column).asInt());
        assertEquals(0,samples.get(4).get(column).asInt());
        assertEquals(1,samples.get(5).get(column).asInt());
        assertEquals(6,samples.get(6).get(0).asInt());
        assertFalse(run.path("initial_state").isEmpty()); assertFalse(run.path("final_state").isEmpty());
    }
    @Test public void aNonzeroStartIsHonoredByTheEngineAndTrajectory() throws Exception {
        byte[] source=cyclicModel(2,3);
        var canvas=GraphCanvasModel.fromObjModel(new ua.stetsenkoinna.pnml.PnmlModelParser().parseXml(new String(source,StandardCharsets.UTF_8)));
        var log=new SimulationEvidence.Log();
        var engine=ua.stetsenkoinna.graphpresentation.CanvasSimulationModels.run(canvas,"Review",2,6,log);engine.setCurrentTime(2);
        var capture=new SimulationEvidence.Capture(engine,TrainingBundleWorkspace.hash(source),"interactive-run",6,1);engine.setStateListener(capture::observe);engine.go(6);
        var run=JSON.valueToTree(capture.finish("completed","",log.getText(),log.truncated(),true));
        assertEquals(2,run.path("start_time").asInt());assertEquals(5,run.path("trajectory").path("samples").size());assertEquals(2,run.path("trajectory").path("samples").get(0).get(0).asInt());
    }
    @Test public void skippingRequiresAReasonAndPreservesThatReason() throws Exception {
        try { new SimulationPlan(List.of(),1," ",List.of()); fail(); } catch (IllegalArgumentException expected) { }
        var result = JSON.valueToTree(SimulationEvidence.automatic(cyclicModel(2,3), new SimulationPlan(List.of(),1,"Мережа потребує уточнення параметрів",List.of())));
        assertTrue(result.path("automatic_runs").isEmpty()); assertFalse(result.path("skip_reason").asText().isBlank());
    }
    @Test public void aBrokenModelProducesAnErrorAttemptAndZeroTimeCycleStopsAtEventLimit() throws Exception {
        var invalid = JSON.valueToTree(SimulationEvidence.automatic("<pnml/>".getBytes(StandardCharsets.UTF_8),new SimulationPlan(List.of(10.0),1,"",List.of())));
        assertEquals("failed",invalid.path("automatic_runs").get(0).path("status").asText());
        assertFalse(invalid.path("automatic_runs").get(0).path("error").asText().isBlank());
        var limited = JSON.valueToTree(SimulationEvidence.automatic(cyclicModel(0,0),new SimulationPlan(List.of(10.0),1,"",List.of())));
        assertEquals("limit_reached",limited.path("automatic_runs").get(0).path("status").asText());
        assertFalse(limited.path("automatic_runs").get(0).path("trajectory").path("complete").asBoolean());
    }
}

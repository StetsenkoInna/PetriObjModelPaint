package ua.stetsenkoinna.graphpresentation.ai;

import com.fasterxml.jackson.databind.JsonNode;
import java.awt.Window;
import java.nio.file.*;
import java.util.Map;
import javax.swing.SwingUtilities;
import org.junit.*;
import org.junit.rules.TemporaryFolder;
import ua.stetsenkoinna.graphpresentation.PetriNetsFrame;
import ua.stetsenkoinna.graphpresentation.training.SwingCapture;
import static org.junit.Assert.*;

public class ModelToolsTest {
    @Rule public TemporaryFolder folder = new TemporaryFolder();
    private PetriNetsFrame frame;
    private McpToolRegistry registry;
    static final String NET="""
      {"name":"Direct MCP test","places":[
        {"id":"p1","tokens":1,"x":160,"y":220},
        {"id":"p2","tokens":0,"x":520,"y":220}],
       "transitions":[{"id":"t1","delay":2,"x":340,"y":220}],
       "arcs":[{"id":"a1","source":"p1","target":"t1"},{"id":"a2","source":"t1","target":"p2"}]}
      """;
    @Before public void setup() throws Exception {
        frame=SwingCapture.call(() -> new PetriNetsFrame());
        registry=new McpToolRegistry(new ModelTools(frame));
    }
    @After public void cleanup() throws Exception {
        frame.getMcpServer().close();
        SwingUtilities.invokeAndWait(() -> { for (Window w:Window.getWindows()) w.dispose(); });
    }
    private JsonNode call(String name,Object args) throws Exception {
        var result=DesktopMcpServer.JSON.valueToTree(registry.call(name,DesktopMcpServer.JSON.valueToTree(args)));
        return DesktopMcpServer.JSON.readTree(result.at("/content/0/text").asText());
    }
    private String revision() throws Exception { return call("get_model",Map.of()).path("revision").asText(); }
    private JsonNode build() throws Exception {
        return call("apply_model",Map.of("expected_revision",revision(),"model",DesktopMcpServer.JSON.readTree(NET)));
    }
    @Test public void buildsSimulatesAndSavesWithoutUiGestures() throws Exception {
        String empty=revision(); assertEquals(empty,revision());
        var applied=build(); String built=revision(); assertNotEquals(empty,built);
        assertEquals(2,applied.at("/model/places").size());
        assertEquals("p1",applied.at("/model/places/0/id").asText());
        assertTrue(call("validate_model",Map.of()).path("valid").asBoolean());
        assertEquals(built,revision());
        var simulation=call("simulate_model",Map.of("horizon",10,"expected_revision",built));
        var run=simulation.at("/automatic_runs/0");
        assertEquals(run.toPrettyString(),"completed",run.path("status").asText());
        var markings=new java.util.HashMap<String,Integer>();
        for (var p:run.at("/final_state/0/places")) markings.put(p.path("name").asText(),p.path("tokens").asInt());
        assertEquals(run.toPrettyString(),Integer.valueOf(0),markings.get("p1"));
        assertEquals(run.toPrettyString(),Integer.valueOf(1),markings.get("p2"));
        assertEquals(built,revision());
        Path target=folder.getRoot().toPath().resolve("test.pnml");
        call("save_model",Map.of("path",target.toString(),"expected_revision",built));
        String saved=Files.readString(target); assertTrue(saved.contains("pnml"));
        assertThrows(Exception.class,() -> call("save_model",Map.of("path",target.toString())));
        assertEquals(saved,Files.readString(target));
        var loaded=call("open_model",Map.of("path",target.toString(),"expected_revision",revision(),"replace_current",true));
        assertEquals(2,loaded.at("/model/places").size());
        assertTrue(call("get_model",Map.of("include_pnml",true)).path("pnml").asText().contains("p1"));
    }
    @Test public void rejectsBadBatchesAndStaleRevisionsWithoutChangingCanvas() throws Exception {
        build(); String rev=revision();
        var net=DesktopMcpServer.JSON.readTree(NET);
        assertThrows(Exception.class,() -> call("apply_model",Map.of("expected_revision",rev,"model",net)));
        assertThrows(Exception.class,() -> call("apply_model",Map.of("expected_revision","stale","replace_current",true,"model",net)));
        ((com.fasterxml.jackson.databind.node.ObjectNode)net.at("/arcs/0")).put("target","missing");
        assertThrows(Exception.class,() -> call("apply_model",Map.of("expected_revision",rev,"replace_current",true,"model",net)));
        assertEquals(rev,revision());
        ((com.fasterxml.jackson.databind.node.ObjectNode)net.at("/places/0")).put("tokens",-1);
        assertThrows(IllegalArgumentException.class,() -> call("apply_model",Map.of("expected_revision",rev,"replace_current",true,"model",net)));
        assertEquals(rev,revision());
    }
    @Test public void undoRestoresOriginalAndRedoRestoresBatch() throws Exception {
        build();
        SwingCapture.call(() -> { frame.getPetriNetsPanel().getActionMap().get("undoEdit").actionPerformed(null); return null; });
        assertEquals(0,call("get_model",Map.of()).at("/model/places").size());
    }
    @Test public void rejectsUnknownFieldsNonfiniteAndOversizedPayloads() throws Exception {
        var net=DesktopMcpServer.JSON.readTree(NET);
        ((com.fasterxml.jackson.databind.node.ObjectNode)net.at("/transitions/0")).put("delay",Double.NaN);
        assertThrows(IllegalArgumentException.class,() -> call("apply_model",Map.of("expected_revision",revision(),"model",net)));
        assertThrows(IllegalArgumentException.class,() -> call("open_model",Map.of("expected_revision",revision(),"pnml","x".repeat(1048577))));
    }
    @Test public void editsByIdAndRollsBackWholeInvalidBatch() throws Exception {
        build();
        call("edit_model",Map.of("expected_revision",revision(),"operations",java.util.List.of(
                Map.of("op","put_place","id","p1","tokens",3), Map.of("op","put_transition","id","t1","delay",5))));
        var current=call("get_model",Map.of());
        assertEquals(3,current.at("/model/places/0/tokens").asInt());
        assertEquals(5,current.at("/model/transitions/0/delay").asInt());
        String rev=revision();
        assertThrows(Exception.class,() -> call("edit_model",Map.of("expected_revision",rev,"operations",java.util.List.of(
                Map.of("op","put_place","id","p1","tokens",7), Map.of("op","put_arc","id","bad","source","p1","target","missing")))));
        assertEquals(rev,revision());
        call("edit_model",Map.of("expected_revision",rev,"operations",java.util.List.of(Map.of("op","remove","id","t1"))));
        assertEquals(0,call("get_model",Map.of()).at("/model/arcs").size());
    }
    @Test public void transportDiscoversDirectToolsAndCallsThem() throws Exception {
        build();
        assertTrue(frame.getMcpServer().toolNames().contains("edit_model"));
        try (var server=new DesktopMcpServer(new ModelTools(frame)); var client=java.net.http.HttpClient.newHttpClient()) {
            server.start(0);
            for (String method:java.util.List.of("tools/list","tools/call")) {
                var request=Map.of("jsonrpc","2.0","id",1,"method",method,"params",Map.of("name","get_model","arguments",Map.of()));
                var response=client.send(java.net.http.HttpRequest.newBuilder(java.net.URI.create(server.endpoint()))
                        .header("Content-Type","application/json").POST(java.net.http.HttpRequest.BodyPublishers.ofString(DesktopMcpServer.JSON.writeValueAsString(request))).build(),java.net.http.HttpResponse.BodyHandlers.ofString());
                assertEquals(200,response.statusCode());
                assertTrue(response.body(),response.body().contains(method.equals("tools/list") ? "edit_model" : "p1"));
            }
        }
    }
    @Test public void composedPnmlRetainsObjectsAndRejectsLossyStructuredEditing() throws Exception {
        var canvas=ModelTools.build(DesktopMcpServer.JSON.readTree(NET));
        var object=new ua.stetsenkoinna.graphnet.GraphObjectFrame("Machine",new java.awt.Rectangle(80,100,520,240));
        canvas.getFrames().add(object);
        for(var p:canvas.getNet().getGraphPetriPlaceList()) canvas.claim(object,p);
        for(var t:canvas.getNet().getGraphPetriTransitionList()) canvas.claim(object,t);
        String pnml=ModelTools.xml(canvas);
        var opened=call("open_model",Map.of("expected_revision",revision(),"pnml",pnml));
        assertEquals(1,opened.path("object_count").asInt());
        assertEquals("use_pnml",opened.path("structured_editing").asText());
        String rev=revision();
        assertThrows(Exception.class,() -> call("edit_model",Map.of("expected_revision",rev,"operations",java.util.List.of(Map.of("op","put_place","id","p1","tokens",7)))));
        assertEquals(rev,revision());
        Path target=folder.getRoot().toPath().resolve("composed.pnml");
        call("save_model",Map.of("path",target.toString()));
        var reloaded=call("open_model",Map.of("expected_revision",revision(),"path",target.toString(),"replace_current",true));
        assertEquals(1,reloaded.path("object_count").asInt());
    }
}

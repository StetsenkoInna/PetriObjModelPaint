package ua.stetsenkoinna.graphpresentation.ai;

import com.fasterxml.jackson.databind.JsonNode;
import java.awt.Window;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import javax.swing.SwingUtilities;
import org.junit.*;
import org.junit.rules.TemporaryFolder;
import ua.stetsenkoinna.graphpresentation.PetriNetsFrame;
import ua.stetsenkoinna.graphpresentation.training.SwingCapture;
import static org.junit.Assert.*;

/** Exercises the registered direct tools against the live document without UI gestures. */
public class ComposedModelToolsTest {
    @Rule public TemporaryFolder folder = new TemporaryFolder();
    private PetriNetsFrame frame;
    private McpToolRegistry registry;
    private static final String DOCUMENT = """
      {"name":"Composed direct model","places":[
        {"id":"job","tokens":1,"x":-300,"y":-100},
        {"id":"input","tokens":0,"x":100,"y":100},
        {"id":"alias","tokens":0,"x":100,"y":220},
        {"id":"done","tokens":0,"x":400,"y":100},
        {"id":"resource","tokens":1,"x":650,"y":100},
        {"id":"loose","tokens":0,"x":-600,"y":-300}],
       "transitions":[
        {"id":"send","delay":1,"x":-150,"y":-100},
        {"id":"work","delay":2,"x":250,"y":100}],
       "arcs":[
        {"id":"a_job","source":"job","target":"send"},
        {"id":"a_input","source":"input","target":"work"},
        {"id":"a_resource","source":"alias","target":"work","read":true},
        {"id":"a_done","source":"work","target":"done"}],
       "objects":[
        {"id":"assembly","name":"Assembly","x":-400,"y":-200,"width":950,"height":600,
         "members":["job","send"],"template":{"method":"assemblyTemplate","arguments":["4","two words"]}},
        {"id":"worker","name":"Worker","x":0,"y":0,"width":500,"height":300,
         "members":["input","alias","done","work"],"parent":"assembly","content_visible":false},
        {"id":"pool","name":"Resource pool","x":600,"y":0,"width":200,"height":200,
         "members":["resource"]}],
       "links":[
        {"type":"transitionToPlace","source":"assembly:send","target":"worker:input","weight":1,
         "boundary_stub":{"x":17,"y":-23}},
        {"type":"placeFusion","source":"pool:resource","target":"worker:alias",
         "boundary_stub":{"x":-29,"y":31}}],
       "groups":[{"id":"system","name":"System","objects":["assembly","pool"],
         "template":{"method":"systemTemplate","arguments":["3","resource count"]}}]}
      """;

    @Before public void setup() throws Exception {
        frame=SwingCapture.call(() -> new PetriNetsFrame());
        registry=new McpToolRegistry(new ModelTools(frame));
    }
    @After public void cleanup() throws Exception {
        if(frame!=null)frame.getMcpServer().close();
        SwingUtilities.invokeAndWait(() -> { for(Window window:Window.getWindows())window.dispose(); });
    }
    private JsonNode call(String tool,Object arguments) throws Exception {
        JsonNode result=DesktopMcpServer.JSON.valueToTree(registry.call(tool,DesktopMcpServer.JSON.valueToTree(arguments)));
        return DesktopMcpServer.JSON.readTree(result.at("/content/0/text").asText());
    }
    private JsonNode get() throws Exception { return call("get_model",Map.of()); }
    private String revision() throws Exception { return get().path("revision").asText(); }
    private JsonNode build() throws Exception {
        return call("apply_model",Map.of("expected_revision",revision(),"model",DesktopMcpServer.JSON.readTree(DOCUMENT)));
    }
    private JsonNode edit(Object... operations) throws Exception {
        return call("edit_model",Map.of("expected_revision",revision(),"operations",Arrays.asList(operations)));
    }
    private static JsonNode byId(JsonNode values,String id) {
        for(JsonNode value:values)if(id.equals(value.path("id").asText()))return value;
        fail("Missing ID "+id+" in "+values);return null;
    }
    private static JsonNode element(JsonNode response,String kind,String id) { return byId(response.path("model").path(kind),id); }
    private static JsonNode link(JsonNode response,String type,String source,String target) {
        for(JsonNode value:response.at("/model/links"))if(type.equals(value.path("type").asText())
                &&source.equals(value.path("source").asText())&&target.equals(value.path("target").asText()))return value;
        fail("Missing "+type+" connection "+source+" -> "+target+" in "+response);return null;
    }
    private static Set<String> ids(JsonNode array) {
        Set<String> result=new TreeSet<>();for(JsonNode value:array)assertTrue("Duplicate ID "+value,result.add(value.path("id").asText()));return result;
    }
    private static Map<String,JsonNode> indexed(JsonNode array) {
        Map<String,JsonNode> result=new TreeMap<>();for(JsonNode value:array)result.put(value.path("id").asText(),value);return result;
    }
    private JsonNode simulate(double horizon) throws Exception {
        String before=revision();JsonNode evidence=call("simulate_model",Map.of("horizon",horizon,"expected_revision",before));
        assertEquals(before,revision());assertFalse(evidence.path("canvas_changed").asBoolean());
        JsonNode run=evidence.at("/automatic_runs/0");assertEquals(run.toPrettyString(),"completed",run.path("status").asText());return run;
    }
    private static int tokens(JsonNode run,String placeId) {
        for(JsonNode object:run.path("final_state"))for(JsonNode place:object.path("places"))if(placeId.equals(place.path("id").asText()))return place.path("tokens").asInt();
        fail("Missing final place "+placeId+" in "+run);return -1;
    }

    @Test public void buildsNestedObjectsAndConnectsDerivedPortsDirectly() throws Exception {
        JsonNode built=build();assertEquals("complete",built.path("structured_editing").asText());
        assertEquals(3,built.path("object_count").asInt());assertEquals(11,built.path("ports").size());
        assertEquals("assembly",element(built,"objects","worker").path("parent").asText());
        assertEquals(1,element(built,"places","alias").path("tokens").asInt());
        assertEquals("place",byId(built.path("ports"),"worker:alias").path("kind").asText());
        assertEquals("transition",byId(built.path("ports"),"assembly:send").path("kind").asText());
        assertEquals("place",byId(built.path("ports"),"assembly:alias").path("kind").asText());
        link(built,"placeFusion","pool:resource","worker:alias");
        link(built,"transitionToPlace","assembly:send","worker:input");
        JsonNode validation=call("validate_model",Map.of());assertTrue(validation.toPrettyString(),validation.path("valid").asBoolean());
        JsonNode run=simulate(20);assertEquals(1,tokens(run,"done"));assertEquals(1,tokens(run,"resource"));
    }

    @Test public void editsAndResolvesAllSymbolicFieldsInComposedDocument() throws Exception {
        build();JsonNode symbolic=edit(
                Map.of("op","put_place","id","alias","tokens_parameter","capacity"),
                Map.of("op","put_transition","id","work","delay_parameter","serviceTime","distribution_parameter","law",
                        "priority_parameter","rank","probability_parameter","chance"),
                Map.of("op","put_arc","id","a_resource","weight_parameter","quantity","read_parameter","readOnly"));
        assertEquals("capacity",element(symbolic,"places","resource").path("tokens_parameter").asText());
        assertEquals("serviceTime",element(symbolic,"transitions","work").path("delay_parameter").asText());
        assertEquals("quantity",element(symbolic,"arcs","a_resource").path("weight_parameter").asText());
        JsonNode resolved=edit(
                Map.of("op","put_place","id","alias","tokens",1),
                Map.of("op","put_transition","id","work","delay",3,"distribution","deterministic","priority",2,"probability",1),
                Map.of("op","put_arc","id","a_resource","weight",1,"read",true));
        for(String id:List.of("resource","alias"))assertFalse(element(resolved,"places",id).has("tokens_parameter"));
        for(String field:List.of("delay_parameter","distribution_parameter","priority_parameter","probability_parameter"))
            assertFalse(field,element(resolved,"transitions","work").has(field));
        for(String field:List.of("weight_parameter","read_parameter"))assertFalse(element(resolved,"arcs","a_resource").has(field));
        assertEquals(3,element(resolved,"transitions","work").path("delay").asInt());
        assertEquals(1,tokens(simulate(20),"done"));
    }

    @Test public void rejectsCrossObjectOrdinaryAndReadArcsWithoutPartialEdits() throws Exception {
        build();String before=revision();JsonNode unchanged=get().path("model");
        for(boolean read:List.of(false,true)) {
            assertThrows(Exception.class,() -> edit(Map.of("op","put_place","id","job","tokens",9),
                    Map.of("op","put_arc","id","bad","source","resource","target","work","read",read)));
            assertEquals(before,revision());assertEquals(unchanged,get().path("model"));
        }
        assertThrows(Exception.class,() -> edit(Map.of("op","put_arc","id","badOutput","source","send","target","input")));
        assertThrows(Exception.class,() -> edit(Map.of("op","connect_ports","type","transitionToPlace","source","worker:send","target","worker:input")));
        assertEquals(before,revision());
    }

    @Test public void retainsSeparateLocalReturnArrowsAcrossEditsWithoutPairingDeliveryLinks() throws Exception {
        var document=(com.fasterxml.jackson.databind.node.ObjectNode)DesktopMcpServer.JSON.readTree(DOCUMENT);
        document.withArray("arcs").addObject().put("id","a_return").put("source","send").put("target","job");
        call("apply_model",Map.of("expected_revision",revision(),"model",document));
        assertReturnArrowsSeparated();
        edit(Map.of("op","put_place","id","job","tokens",3));
        assertReturnArrowsSeparated();
    }

    private void assertReturnArrowsSeparated() throws Exception {
        SwingCapture.call(() -> {
            var net=frame.getPetriNetsPanel().getCanvasModel().getNet();
            var input=net.getGraphArcInList().stream().filter(a -> a.getArcIn().getId().equals("a_job")).findFirst().orElseThrow();
            var returned=net.getGraphArcOutList().stream().filter(a -> a.getArcOut().getId().equals("a_return")).findFirst().orElseThrow();
            assertTrue("Local input must keep its paired offset",input.isSecondArc());
            assertTrue("Local return must keep the opposite offset",returned.isFirstArc());
            var a=input.getGraphElement();var b=returned.getGraphElement();
            double midpointDistance=Math.hypot((a.getX1()+a.getX2()-b.getX1()-b.getX2())/2,
                    (a.getY1()+a.getY2()-b.getY1()-b.getY2())/2);
            assertTrue("Return arrows must remain visibly separated",midpointDistance>=10);
            var delivery=net.getGraphArcOutList().stream().filter(arc -> ModelDocument.id(arc.getEndElement()).equals("input")).findFirst().orElseThrow();
            assertFalse("A cross-object delivery has no opposite local arc",delivery.isFirstArc());
            assertFalse("A cross-object delivery must remain unpaired",delivery.isSecondArc());
            return null;
        });
    }

    @Test public void rejectsUnresolvedPortTransferAndResolvesItsQuantityExplicitly() throws Exception {
        build();JsonNode symbolic=edit(Map.of("op","connect_ports","type","transitionToPlace","source","assembly:send",
                "target","worker:input","weight_parameter","batchSize"));
        assertEquals("batchSize",link(symbolic,"transitionToPlace","assembly:send","worker:input").path("weight_parameter").asText());
        String before=revision();JsonNode validation=call("validate_model",Map.of());
        assertFalse("An unresolved transfer must not silently use a numeric placeholder",validation.path("valid").asBoolean());
        assertThrows(Exception.class,() -> call("simulate_model",Map.of("horizon",20,"expected_revision",before)));
        Path path=folder.getRoot().toPath().resolve("unresolved.pnml");
        assertThrows(Exception.class,() -> call("save_model",Map.of("path",path.toString(),"expected_revision",before)));
        assertFalse(Files.exists(path));assertEquals(before,revision());
        JsonNode resolved=edit(Map.of("op","connect_ports","type","transitionToPlace","source","assembly:send",
                "target","worker:input","weight",2));
        assertFalse(link(resolved,"transitionToPlace","assembly:send","worker:input").has("weight_parameter"));
        assertEquals(2,tokens(simulate(20),"done"));
    }

    @Test public void aliasMarkingEditsReachMasterAndUndoRestoresTheSharedValue() throws Exception {
        build();JsonNode changed=edit(Map.of("op","put_place","id","alias","tokens",7));
        for(String id:List.of("resource","alias"))assertEquals(7,element(changed,"places",id).path("tokens").asInt());
        SwingCapture.call(() -> {frame.getPetriNetsPanel().getActionMap().get("undoEdit").actionPerformed(null);return null;});
        JsonNode restored=get();for(String id:List.of("resource","alias"))assertEquals(1,element(restored,"places",id).path("tokens").asInt());
        assertEquals(2,restored.at("/model/links").size());
    }

    @Test public void replicatesNestedInternalLinksAndOptInExternalSharedResource() throws Exception {
        build();JsonNode isolated=edit(Map.of("op","replicate_object","id","assembly","prefix","isolated","count",1,"dx",1100));
        assertEquals("isolated_1_assembly",element(isolated,"objects","isolated_1_worker").path("parent").asText());
        link(isolated,"transitionToPlace","isolated_1_assembly:isolated_1_send","isolated_1_worker:isolated_1_input");
        assertEquals(3,isolated.at("/model/links").size());
        JsonNode shared=edit(Map.of("op","replicate_object","id","assembly","prefix","shared","count",1,"dx",2200,"include_external_links",true));
        link(shared,"transitionToPlace","shared_1_assembly:shared_1_send","shared_1_worker:shared_1_input");
        link(shared,"placeFusion","pool:resource","shared_1_worker:shared_1_alias");
        assertEquals(5,shared.at("/model/links").size());
        assertEquals(element(shared,"objects","assembly").path("template"),element(shared,"objects","shared_1_assembly").path("template"));
        JsonNode marking=edit(Map.of("op","put_place","id","shared_1_alias","tokens",4));
        for(String id:List.of("resource","alias","shared_1_alias"))assertEquals(4,element(marking,"places",id).path("tokens").asInt());
        assertEquals(1,element(marking,"places","isolated_1_alias").path("tokens").asInt());
        ids(marking.at("/model/places"));ids(marking.at("/model/objects"));ids(marking.at("/model/links"));
    }

    @Test public void savesAndOpensCompleteIdentityTemplatesVisibilityAndBoundaryStubs() throws Exception {
        JsonNode before=build();Path path=folder.getRoot().toPath().resolve("composed.pnml");
        call("save_model",Map.of("path",path.toString(),"expected_revision",revision()));
        JsonNode loaded=call("open_model",Map.of("path",path.toString(),"expected_revision",revision(),"replace_current",true));
        for(String collection:List.of("places","transitions","arcs","objects","links","groups"))
            assertEquals(collection,indexed(before.path("model").path(collection)),indexed(loaded.path("model").path(collection)));
        assertFalse(element(loaded,"objects","worker").path("content_visible").asBoolean());
        assertEquals(-400,element(loaded,"objects","assembly").path("x").asInt());
        assertEquals("resource count",element(loaded,"groups","system").at("/template/arguments/1").asText());
        assertEquals(-23,link(loaded,"transitionToPlace","assembly:send","worker:input").at("/boundary_stub/y").asInt());
        assertEquals(-29,link(loaded,"placeFusion","pool:resource","worker:alias").at("/boundary_stub/x").asInt());
        assertEquals(1,tokens(simulate(20),"done"));
    }

    @Test public void ungroupsLinkedChildAndChangesOwnershipWithoutLosingConnections() throws Exception {
        build();JsonNode owned=edit(Map.of("op","set_owner","id","worker","members",List.of("loose")));
        assertTrue(element(owned,"objects","worker").path("members").toString().contains("loose"));
        JsonNode promoted=edit(Map.of("op","remove","id","worker"));
        assertEquals(2,promoted.path("object_count").asInt());
        Set<String> members=new HashSet<>();element(promoted,"objects","assembly").path("members").forEach(n -> members.add(n.asText()));
        assertTrue(members.containsAll(List.of("input","alias","done","work","loose")));
        link(promoted,"placeFusion","pool:resource","assembly:alias");
        assertEquals(1,promoted.at("/model/links").size());
        boolean converted=false;for(JsonNode arc:promoted.at("/model/arcs"))if("send".equals(arc.path("source").asText())&&"input".equals(arc.path("target").asText())) {
            converted=true;assertEquals(-23,arc.at("/boundary_stub/y").asInt());
        }
        assertTrue("Internalized delivery must become an ordinary arc",converted);
        assertEquals(1,tokens(simulate(20),"done"));
        String before=revision();assertThrows(Exception.class,() -> edit(Map.of("op","set_owner","id","assembly","members",List.of("resource"))));
        assertEquals("An unsupported internal shared-place fusion must fail atomically",before,revision());
    }

    @Test public void reimportsFullUserFixtureThroughStructuredModelAndStillDeliversFourJobs() throws Exception {
        Path fixture=Path.of("../petri-model/src/test/resources/pnml/modular-port-connections.pnml").toAbsolutePath().normalize();
        assertTrue(fixture.toString(),Files.isRegularFile(fixture));
        JsonNode opened=call("open_model",Map.of("pnml",Files.readString(fixture),"expected_revision",revision()));
        assertEquals(4,opened.path("object_count").asInt());assertEquals(5,opened.at("/model/links").size());
        long reads=0;for(JsonNode arc:opened.at("/model/arcs"))if(arc.path("read").asBoolean())reads++;
        assertEquals(6,reads);
        JsonNode full=get().path("model");
        JsonNode rebuilt=call("apply_model",Map.of("model",full,"expected_revision",revision(),"replace_current",true));
        for(String collection:List.of("places","transitions","arcs","objects","links","groups"))
            assertEquals(collection,indexed(full.path(collection)),indexed(rebuilt.path("model").path(collection)));
        JsonNode transition=full.path("transitions").get(0);
        edit(Map.of("op","put_transition","id",transition.path("id").asText(),"delay",transition.path("delay").asDouble()+1));
        JsonNode run=simulate(100);assertEquals(4,tokens(run,"p-17cd38d8"));assertEquals(1,tokens(run,"p-1-af58633a"));
        for(String id:List.of("p-23780345","p-2-c16b011f","p-3-a505cf0e","p-4-08693823"))assertEquals(id,1,tokens(run,id));
    }
}

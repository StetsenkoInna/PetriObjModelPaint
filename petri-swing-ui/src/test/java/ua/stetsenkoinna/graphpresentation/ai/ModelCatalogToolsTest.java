package ua.stetsenkoinna.graphpresentation.ai;

import com.fasterxml.jackson.databind.JsonNode;
import java.nio.file.*;
import java.util.*;
import org.junit.*;
import org.junit.rules.TemporaryFolder;
import ua.stetsenkoinna.graphpresentation.PetriNetsFrame;
import ua.stetsenkoinna.graphpresentation.training.SwingCapture;
import static org.junit.Assert.*;

public class ModelCatalogToolsTest {
    @Rule public TemporaryFolder folder=new TemporaryFolder();
    private PetriNetsFrame frame;
    private McpToolRegistry tools;
    @Before public void setup() throws Exception {frame=SwingCapture.call(PetriNetsFrame::new);tools=new McpToolRegistry(new ModelTools(frame),new ModelCatalogTools(frame));}
    @After public void close() throws Exception {frame.getMcpServer().close();SwingCapture.call(()->{frame.dispose();return null;});}
    private JsonNode call(String name,Object args) throws Exception {var raw=DesktopMcpServer.JSON.valueToTree(tools.call(name,DesktopMcpServer.JSON.valueToTree(args)));return DesktopMcpServer.JSON.readTree(raw.at("/content/0/text").asText());}
    private String revision() throws Exception{return call("get_model",Map.of()).path("revision").asText();}
    @Test public void libraryInstancesAndComposedModulesNeedNoUiPlacement() throws Exception {
        var listed=call("list_model_templates",Map.of());assertTrue(listed.path("library").size()>0);
        var first=call("insert_model_template",Map.of("expected_revision",revision(),"method","CreateNetSMOwithoutQueue","arguments",List.of("1","2","Machine"),"prefix","first","name","First machine","x",-700,"y",-400));
        assertEquals(1,first.path("object_count").asInt());assertTrue(first.path("ports").size()>0);
        String objectId=first.path("inserted_objects").get(0).asText();
        Path path=folder.getRoot().toPath().resolve("module.pnml");
        call("export_model_fragment",Map.of("expected_revision",revision(),"object_ids",List.of(objectId),"path",path.toString()));assertTrue(Files.isRegularFile(path));
        var imported=call("insert_model_fragment",Map.of("expected_revision",revision(),"path",path.toString(),"prefix","second","x",700,"y",400));
        assertEquals(2,imported.path("object_count").asInt());
        var ids=new HashSet<String>();for(String key:List.of("places","transitions","arcs","objects"))for(var n:imported.path("model").path(key))assertTrue(ids.add(n.path("id").asText()));
        var before=imported.path("model");String rev=revision();
        assertThrows(Exception.class,()->call("insert_model_fragment",Map.of("expected_revision",rev,"path",path.toString(),"prefix","second","x",0,"y",0)));
        assertEquals(before,call("get_model",Map.of()).path("model"));
    }
    @Test public void capabilitiesAndNavigationDescribeDirectObjectWorkflow() throws Exception {
        var capabilities=call("get_model_capabilities",Map.of());assertFalse(capabilities.path("ui_required_for_modeling").asBoolean(true));
        assertTrue(capabilities.path("connections").path("placeFusion").asText().contains("marking"));
        var placed=call("insert_model_template",Map.of("expected_revision",revision(),"method","CreateNetGenerator","arguments",List.of("2"),"prefix","g","x",-200,"y",-100));
        String rev=revision();var view=call("set_model_view",Map.of("center_x",-10000,"center_y",-20000,"scale",0.5));
        assertEquals(-10000,view.path("center_x").asDouble(),1);assertEquals(rev,revision());
        call("set_model_view",Map.of("object_id",placed.path("inserted_objects").get(0).asText(),"fit",true));assertEquals(rev,revision());
        var undo=call("undo_model",Map.of("expected_revision",revision()));assertEquals(0,undo.path("object_count").asInt());
        var redo=call("undo_model",Map.of("expected_revision",revision(),"redo",true));assertEquals(1,redo.path("object_count").asInt());
    }
    @Test public void layoutIsOptionalAtomicAndCannotChangeSemantics() throws Exception {
        var placed=call("insert_model_template",Map.of("expected_revision",revision(),"method","CreateNetSMOwithoutQueue","arguments",List.of("1","2","Machine"),"prefix","m","x",-200,"y",-100));
        String rev=revision();var preview=call("optimize_model_layout",Map.of("expected_revision",rev,"apply",false));
        assertFalse(preview.path("applied").asBoolean());assertEquals(rev,revision());assertTrue(preview.at("/report/semantic_preserved").asBoolean());
        String fingerprint=SwingCapture.call(()->ua.stetsenkoinna.graphpresentation.layout.LayoutOptimizer.semanticFingerprint(frame.getPetriNetsPanel().getCanvasModel()));
        var applied=call("optimize_model_layout",Map.of("expected_revision",rev,"apply",true));
        assertTrue(applied.path("applied").asBoolean());
        assertEquals(fingerprint,SwingCapture.call(()->ua.stetsenkoinna.graphpresentation.layout.LayoutOptimizer.semanticFingerprint(frame.getPetriNetsPanel().getCanvasModel())));
        assertEquals(placed.path("object_count"),call("get_model",Map.of()).path("object_count"));
    }
    @Test public void modelingPromptAndDiscoveryLeadWithFullDirectApi() {
        var skills=new ModelingSkills();var resource=skills.read("petri://desktop/model-api");assertTrue(resource.toString().contains("connect_ports"));
        var prompt=skills.prompt("build_petri_model",DesktopMcpServer.JSON.valueToTree(Map.of("description","Build two machines sharing one resource"))).toString();
        assertTrue(prompt.indexOf("Direct desktop model API")<prompt.indexOf("Model the system before drawing"));
        assertTrue(prompt.contains("placeFusion"));assertFalse(prompt.contains("Structured edits are for flat concrete nets"));
    }
}

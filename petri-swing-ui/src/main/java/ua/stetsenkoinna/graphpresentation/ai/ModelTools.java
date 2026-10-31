package ua.stetsenkoinna.graphpresentation.ai;

import com.fasterxml.jackson.databind.JsonNode;
import java.awt.geom.Point2D;
import java.io.ByteArrayOutputStream;
import java.io.ObjectOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import ua.stetsenkoinna.graphnet.*;
import ua.stetsenkoinna.graphpresentation.PetriNetsFrame;
import ua.stetsenkoinna.graphpresentation.training.*;
import ua.stetsenkoinna.petriobj.*;
import ua.stetsenkoinna.pnml.*;

/** Direct model operations. No mouse gestures, modal dialogs or synthetic UI events. */
public final class ModelTools {
    private final PetriNetsFrame frame;
    public ModelTools(PetriNetsFrame frame) { this.frame = frame; }

    @McpTool(name="get_model_capabilities", description="Read the direct modeling workflow and connection semantics before building a net. All valid modeling operations, including objects, nesting, groups, ports, symbols, templates, copy, layout, camera, simulation and PNML, use direct tools. Do not inspect or click UI controls to group, place or connect elements.")
    public Object capabilities(JsonNode args) throws Exception {
        return text(Map.of(
                "workflow",List.of("get_model_capabilities","get_model","apply_model or edit_model","validate_model","simulate_model","optional optimize_model_layout and inspect its report","save_model"),
                "model_fields",List.of("name","places","transitions","arcs","objects","links","groups"),
                "objects","Create with objects[] or put_object; members are element IDs. parent defines nesting. No UI grouping is needed. IDs survive save/open and Undo.",
                "ports","Ports are derived from object members, not drawn manually. get_model returns objectId:elementId and perimeter positions. Both a node ID and its valid object port address can be used in a link.",
                "connections",Map.of("placeFusion","source place is the canonical marking; target is its local alias in another object. This shares one buffer/resource and does not transfer or duplicate tokens.","transitionToPlace","source transition delivers weight tokens to a place in another object. It is not a shared queue.","local_arcs","Ordinary and read arcs stay inside an object. To consume from an external shared queue, create a local place, fuse it to the queue, then draw the local input arc. A read arc tests availability, not zero."),
                "editing_operations",List.of("put_place","put_transition","put_arc","put_object","put_group","connect_ports","disconnect_ports","move_object","replicate_object","set_owner","remove","rename_model"),
                "parameters","Use *_parameter fields for symbolic values. Put a numeric/literal value to resolve that field. Shared place edits propagate to aliases. Resolve symbolic cross-object transfer weights before export/simulation.",
                "layout","Coordinates are signed world coordinates. put_object changes its frame; move_object translates the subtree. optimize_model_layout changes geometry only, supports Undo and reports unresolved labels/arrows instead of claiming success.",
                "ui_required_for_modeling",false,
                "guide","petri://desktop/model-api"));
    }

    @McpTool(name="set_model_view", description="Navigate the canvas directly without UI gestures or changing the net: focus an object or root, set signed world camera coordinates and zoom, or fit the drawing. Object focus does not change its saved collapsed state.", inputSchema="""
      {"type":"object","properties":{"object_id":{"type":"string"},"center_x":{"type":"number","minimum":-1000000000,"maximum":1000000000},"center_y":{"type":"number","minimum":-1000000000,"maximum":1000000000},"scale":{"type":"number","minimum":0.1,"maximum":5},"fit":{"type":"boolean"}},"additionalProperties":false}
      """)
    public Object view(JsonNode args) throws Exception {
        return text(SwingCapture.call(() -> {
            var panel=frame.getPetriNetsPanel();
            if(args.has("object_id")) {
                String id=args.get("object_id").asText();GraphObjectFrame focus=null;
                if(!id.isBlank()&&!id.equals("root"))focus=current().getFrames().stream().filter(o -> o.getModelId().equals(id)).findFirst().orElseThrow(() -> new IllegalArgumentException("Unknown object: "+id));
                boolean collapsed=focus!=null&&focus.isCollapsed();panel.openObjectCanvas(focus);if(focus!=null)focus.setCollapsed(collapsed);
            }
            if(args.path("fit").asBoolean())panel.fitDrawingToViewport();
            else if(args.has("center_x")||args.has("center_y")||args.has("scale")) {
                var camera=panel.getCamera();panel.setCamera(args.path("center_x").asDouble(camera.center_x()),args.path("center_y").asDouble(camera.center_y()),args.path("scale").asDouble(camera.scale()));
            }
            var camera=panel.getCamera();
            return Map.of("center_x",camera.center_x(),"center_y",camera.center_y(),"scale",camera.scale(),"revision",revision(current()));
        }));
    }

    @McpTool(name="undo_model", description="Undo or redo one direct model edit, layout change or existing canvas edit without clicking UI controls.", inputSchema="""
      {"type":"object","properties":{"redo":{"type":"boolean"},"expected_revision":{"type":"string"}},"required":["expected_revision"],"additionalProperties":false}
      """)
    public Object undo(JsonNode args) throws Exception {
        return text(SwingCapture.call(() -> {checkRevision(args);frame.undoMcpModel(args.path("redo").asBoolean());return describe(current());}));
    }

    @McpTool(name="optimize_model_layout", description="Improve spacing without rebuilding the net. Preserves node/arc IDs, topology, markings, parameters, objects, groups and port semantics. Uses rendered label extents, expands frames and moves colliding elements while retaining the existing arrangement where possible. Straight arrows are preserved; unresolved crossings/overlaps are reported with offending IDs. Only report.verified=true means all checks in verification_scope passed. apply=false previews diagnostics without editing. Apply is revision-guarded and undoable.", inputSchema="""
      {"type":"object","properties":{"expected_revision":{"type":"string"},"minimum_gap":{"type":"integer","minimum":8,"maximum":300},"apply":{"type":"boolean"},"fit":{"type":"boolean"}},"required":["expected_revision"],"additionalProperties":false}
      """)
    public Object optimize(JsonNode args) throws Exception {
        var source=SwingCapture.call(() -> {checkRevision(args);return new GraphCanvasModel(current());});
        var font=SwingCapture.call(() -> frame.getPetriNetsPanel().getFont());
        var optimized=ua.stetsenkoinna.graphpresentation.layout.LayoutOptimizer.optimize(source,font,new ua.stetsenkoinna.graphpresentation.layout.LayoutOptimizer.Options(args.path("minimum_gap").asInt(24)));
        return text(SwingCapture.call(() -> {
            checkRevision(args);boolean apply=args.path("apply").asBoolean(true);
            if(apply){frame.applyMcpCanvas(optimized.canvas(),false);if(args.path("fit").asBoolean(true))frame.getPetriNetsPanel().fitDrawingToViewport();}
            return Map.of("revision",revision(current()),"applied",apply,"report",optimized.report());
        }));
    }

    @McpTool(name="get_model", description="Read the whole canvas directly: stable IDs, markings, timing, symbolic parameters, complete objects/groups/links, derived object ports and a revision token. include_pnml returns the full composed document including objects and links. Read before editing; UI inspection is unnecessary.", inputSchema="""
      {"type":"object","properties":{"include_pnml":{"type":"boolean"}},"additionalProperties":false}
      """)
    public Object get(JsonNode args) throws Exception {
        return text(SwingCapture.call(() -> {
            var canvas = current();
            var result = describe(canvas);
            if (args.path("include_pnml").asBoolean()) result.put("pnml", xml(canvas));
            return result;
        }));
    }

    @McpTool(name="apply_model", description="Atomically build or replace the complete structured document: places, transitions, local arcs, objects, nesting, groups, symbolic parameters and explicit port links. Coordinates may be negative. Links are placeFusion (shared marking) or transitionToPlace (delivery). Ordinary/read arcs cannot cross objects. Read get_model first; expected_revision and explicit replacement protect existing work. No UI inspection or gestures are needed. Undo restores the prior document.", inputSchema=ModelSchemas.APPLY)
    public Object apply(JsonNode args) throws Exception {
        return text(SwingCapture.call(() -> {
            checkRevision(args);
            requireReplacement(args);
            var candidate = build(args.get("model"));
            frame.applyMcpCanvas(candidate, true);
            return describe(current());
        }));
    }

    @McpTool(name="edit_model", description="Atomically edit the full document by ID, including composed objects and symbolic parameters. Supports put_place, put_transition, put_arc, put_object, put_group, connect_ports, disconnect_ports, move_object, replicate_object, remove and rename_model. A placeFusion source is the master marking; target is a local alias. Use a shared local input place plus an internal arc instead of cross-object consumption. Numeric values clear corresponding symbolic parameters. Object removal ungroups by default; cascade deletes its subtree. Replication copies internal links, with external links only when requested. No UI actions are required. IDs and Undo are preserved.", inputSchema=ModelSchemas.EDIT)
    public Object edit(JsonNode args) throws Exception {
        return text(SwingCapture.call(() -> {
            checkRevision(args);
            var document=ModelEdits.apply(ModelDocument.read(current()),args.get("operations"));
            frame.applyMcpCanvas(ModelDocument.build(document),false);
            return describe(current());
        }));
    }

    @McpTool(name="validate_model", description="Check the current model using the application's exporter, parser and simulation model builder on an independent copy. Returns engine validation, not a proof of task correctness or RELAX NG conformance. Reports transitions without consuming inputs; it does not execute them.", inputSchema="""
      {"type":"object","properties":{},"additionalProperties":false}
      """)
    public Object validate(JsonNode args) throws Exception {
        var snapshot = SwingCapture.call(() -> new GraphCanvasModel(current()));
        var result = new LinkedHashMap<String,Object>();
        try {
            String pnml = xml(snapshot);
            var parser = new PnmlModelParser();
            var copy = GraphCanvasModel.fromObjModel(parser.parseXml(pnml));
            if (!parser.getWarnings().isEmpty() || !copy.getLoadWarnings().isEmpty()) throw new IllegalArgumentException("Import repairs: " + parser.getWarnings() + copy.getLoadWarnings());
            ua.stetsenkoinna.graphpresentation.CanvasSimulationModels.run(copy, copy.getName(), 0, 1, new javax.swing.JTextArea());
            result.put("valid", true);
            result.put("warnings", sourceTransitions(snapshot));
        } catch (Exception e) { result.put("valid", false); result.put("error", String.valueOf(e.getMessage())); }
        return text(result);
    }

    @McpTool(name="simulate_model", description="Run the exact current document on a fresh independent copy without changing canvas markings. Returns execution status, initial/final state, statistics, sampled trajectory and bounded protocol. Uses the existing simulation engine, a 10000-event and four-second limit. No successful completion is inferred on a limit or error. Time is in model-clock units; random runs are unseeded.", inputSchema="""
      {"type":"object","properties":{"horizon":{"type":"number","minimum":0.000001,"maximum":1000000},"sampling_interval":{"type":"number","minimum":0.000001},"expected_revision":{"type":"string"}},"required":["horizon"],"additionalProperties":false}
      """)
    public Object simulate(JsonNode args) throws Exception {
        var snapshot = SwingCapture.call(() -> { checkRevision(args); return Map.of("revision", revision(current()), "pnml", xml(new GraphCanvasModel(current()))); });
        var result = SimulationEvidence.automatic(snapshot.get("pnml").getBytes(StandardCharsets.UTF_8),
                new SimulationPlan(List.of(args.get("horizon").asDouble()), args.path("sampling_interval").asDouble(1), "", List.of()));
        result.put("revision", snapshot.get("revision"));
        result.put("canvas_changed", false);
        return text(result);
    }

    @McpTool(name="save_model", description="Save the current complete document directly as PNML, without a file dialog. Absolute path required. Existing files are preserved unless overwrite=true. Uses a temporary file and atomic move when supported. Remembers the path for the editor's next Save; expected_revision optionally protects against concurrent edits.", inputSchema="""
      {"type":"object","properties":{"path":{"type":"string"},"overwrite":{"type":"boolean"},"expected_revision":{"type":"string"}},"required":["path"],"additionalProperties":false}
      """)
    public Object save(JsonNode args) throws Exception {
        Path path = absolute(args);
        return text(SwingCapture.call(() -> {
            checkRevision(args);
            String revision = revision(current());
            String pnml = xml(new GraphCanvasModel(current()));
            write(path, pnml, args.path("overwrite").asBoolean());
            frame.rememberMcpFile(path);
            return Map.of("path", path.toString(), "revision", revision, "bytes", Files.size(path));
        }));
    }

    @McpTool(name="open_model", description="Load PNML directly from an absolute path or pnml text (exactly one), including composed objects and links. Parses before replacing the canvas and rejects import repairs. expected_revision is required; replace_current=true is required for a nonempty document. No file dialog.", inputSchema="""
      {"type":"object","properties":{"path":{"type":"string"},"pnml":{"type":"string","maxLength":1048576},"expected_revision":{"type":"string"},"replace_current":{"type":"boolean"}},"required":["expected_revision"],"additionalProperties":false}
      """)
    public Object open(JsonNode args) throws Exception {
        if (args.has("path") == args.has("pnml")) throw new IllegalArgumentException("Supply exactly one of path or pnml");
        String pnml;
        if (args.has("path")) {
            Path path = absolute(args);
            if (Files.size(path) > 1_048_576) throw new IllegalArgumentException("PNML exceeds 1 MiB");
            pnml = Files.readString(path);
        } else pnml = args.get("pnml").asText();
        var parser = new PnmlModelParser();
        var model = GraphCanvasModel.fromObjModel(parser.parseXml(pnml));
        if (!parser.getWarnings().isEmpty() || !model.getLoadWarnings().isEmpty()) throw new IllegalArgumentException("Import repairs required: " + parser.getWarnings() + model.getLoadWarnings());
        return text(SwingCapture.call(() -> {
            checkRevision(args); requireReplacement(args);
            frame.applyMcpCanvas(model, true);
            if (args.has("path")) frame.rememberMcpFile(absolute(args));
            return describe(current());
        }));
    }

    GraphCanvasModel current() { var canvas = frame.getPetriNetsPanel().getCanvasModel(); canvas.setName(frame.getNameNet()); return canvas; }
    void checkRevision(JsonNode args) throws Exception {
        if (args.has("expected_revision") && !revision(current()).equals(args.get("expected_revision").asText())) throw new IllegalArgumentException("Revision conflict; call get_model and retry with its revision");
    }
    private void requireReplacement(JsonNode args) {
        var c = current();
        if ((!c.getNet().getGraphPetriPlaceList().isEmpty() || !c.getNet().getGraphPetriTransitionList().isEmpty() || !c.getFrames().isEmpty()) && !args.path("replace_current").asBoolean()) throw new IllegalArgumentException("Nonempty document; set replace_current=true explicitly");
    }
    public static String revision(GraphCanvasModel canvas) throws Exception {
        var bytes = new ByteArrayOutputStream();
        try (var out = new ObjectOutputStream(bytes)) { out.writeObject(canvas); }
        return TrainingBundleWorkspace.hash(bytes.toByteArray());
    }
    static String xml(GraphCanvasModel canvas) throws Exception { return new PnmlModelGenerator().generateXml(canvas.toObjModel()); }
    static Map<String,Object> describe(GraphCanvasModel canvas) throws Exception {
        var result=new LinkedHashMap<String,Object>();
        result.put("model",ModelDocument.read(canvas));result.put("revision",revision(canvas));
        result.put("ports",ModelDocument.ports(canvas));result.put("object_count",canvas.getFrames().size());
        result.put("structured_editing","complete");
        return result;
    }
    static GraphCanvasModel build(JsonNode document) throws Exception { return ModelDocument.build(document); }
    private static String id(GraphElement node) { return ModelDocument.id(node); }
    private static List<String> sourceTransitions(GraphCanvasModel c) {
        return c.getNet().getGraphPetriTransitionList().stream().filter(t -> c.getNet().getGraphArcInList().stream().noneMatch(a -> a.getEndElement()==t && !a.getArcIn().getIsInf())).map(t -> "No consuming input: " + id(t)).toList();
    }
    private static Path absolute(JsonNode args) { Path p=Path.of(args.get("path").asText()); if (!p.isAbsolute()) throw new IllegalArgumentException("Absolute path required"); return p.normalize(); }
    static void write(Path path,String value,boolean overwrite) throws Exception {
        if (!overwrite && Files.exists(path)) throw new FileAlreadyExistsException(path.toString());
        Path temp=Files.createTempFile(path.getParent(),".petri-mcp-",".pnml");
        try {
            Files.writeString(temp,value);
            // Non-replacing move has CREATE_NEW semantics even if another process wins the race.
            if (!overwrite) Files.move(temp,path);
            else try { Files.move(temp,path,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING); }
            catch (AtomicMoveNotSupportedException e) { Files.move(temp,path,StandardCopyOption.REPLACE_EXISTING); }
        } finally { Files.deleteIfExists(temp); }
    }
    static Object text(Object value) throws Exception { return Map.of("content",List.of(Map.of("type","text","text",DesktopMcpServer.JSON.writeValueAsString(value)))); }
}

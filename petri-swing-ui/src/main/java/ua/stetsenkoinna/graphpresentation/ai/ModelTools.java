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

    private static final String DOCUMENT = """
      {"type":"object","properties":{
        "name":{"type":"string","minLength":1},
        "places":{"type":"array","maxItems":2000,"items":{"type":"object","properties":{
          "id":{"type":"string"},"name":{"type":"string"},"tokens":{"type":"integer","minimum":0},
          "x":{"type":"number","minimum":0,"maximum":19000},"y":{"type":"number","minimum":0,"maximum":19000}
        },"required":["id","tokens","x","y"],"additionalProperties":false}},
        "transitions":{"type":"array","maxItems":2000,"items":{"type":"object","properties":{
          "id":{"type":"string"},"name":{"type":"string"},"delay":{"type":"number","minimum":0},
          "distribution":{"type":"string","enum":["deterministic","exp","unif","norm"]},
          "deviation":{"type":"number","minimum":0},"priority":{"type":"integer","minimum":0},
          "probability":{"type":"number","minimum":0,"maximum":1},
          "x":{"type":"number","minimum":0,"maximum":19000},"y":{"type":"number","minimum":0,"maximum":19000}
        },"required":["id","delay","x","y"],"additionalProperties":false}},
        "arcs":{"type":"array","maxItems":8000,"items":{"type":"object","properties":{
          "id":{"type":"string"},"source":{"type":"string"},"target":{"type":"string"},
          "weight":{"type":"integer","minimum":1},"read":{"type":"boolean"}
        },"required":["id","source","target"],"additionalProperties":false}}
      },"required":["name","places","transitions","arcs"],"additionalProperties":false}
      """;

    @McpTool(name="get_model", description="Read the whole canvas directly: stable model IDs, markings, timing, layout, object count and a revision token. include_pnml returns the full composed document including objects and links. Read before editing; UI inspection is unnecessary.", inputSchema="""
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

    @McpTool(name="apply_model", description="Atomically build or replace a flat net from structured places, transitions and arcs, then repaint the canvas. expected_revision must match get_model; explicit replace_current=true is required for a nonempty document. Invalid batches leave the document unchanged. IDs must be unique XML NCNames. Coordinates are node centers; time values use the model clock. Use open_model with PNML for composed models. Undo restores the previous drawing.", inputSchema="{\"type\":\"object\",\"properties\":{\"expected_revision\":{\"type\":\"string\"},\"replace_current\":{\"type\":\"boolean\"},\"model\":" + DOCUMENT + "},\"required\":[\"expected_revision\",\"model\"],\"additionalProperties\":false}")
    public Object apply(JsonNode args) throws Exception {
        return text(SwingCapture.call(() -> {
            checkRevision(args);
            requireReplacement(args);
            var candidate = build(args.get("model"));
            frame.applyMcpCanvas(candidate, true);
            return describe(current());
        }));
    }

    @McpTool(name="edit_model", description="Atomically edit a flat concrete net by stable ID. Operations put_place, put_transition and put_arc create or partially update an element; remove deletes an element and its incident arcs. Reads, checks and commits on the EDT; the whole batch rolls back on error. expected_revision from get_model is required. Preserves the document's Save path and supports Undo. For objects or symbolic parameters use get_model(include_pnml=true) and open_model.", inputSchema="""
      {"type":"object","properties":{"expected_revision":{"type":"string"},"operations":{"type":"array","minItems":1,"maxItems":8000,"items":{"type":"object","properties":{
        "op":{"type":"string","enum":["put_place","put_transition","put_arc","remove"]},"id":{"type":"string"},
        "name":{"type":"string"},"tokens":{"type":"integer","minimum":0},"delay":{"type":"number","minimum":0},
        "distribution":{"type":"string","enum":["deterministic","exp","unif","norm"]},"deviation":{"type":"number","minimum":0},
        "priority":{"type":"integer","minimum":0},"probability":{"type":"number","minimum":0,"maximum":1},
        "x":{"type":"number","minimum":0,"maximum":19000},"y":{"type":"number","minimum":0,"maximum":19000},
        "source":{"type":"string"},"target":{"type":"string"},"weight":{"type":"integer","minimum":1},"read":{"type":"boolean"}
      },"required":["op","id"],"additionalProperties":false}}},"required":["expected_revision","operations"],"additionalProperties":false}
      """)
    public Object edit(JsonNode args) throws Exception {
        return text(SwingCapture.call(() -> {
            checkRevision(args);
            var description=describe(current());
            if (!"flat".equals(description.get("structured_editing"))) throw new IllegalArgumentException("Use PNML for composed or parameterized models; structured editing would lose metadata");
            com.fasterxml.jackson.databind.node.ObjectNode document=DesktopMcpServer.JSON.valueToTree(description.get("model"));
            for (var operation:args.get("operations")) {
                String id=operation.get("id").asText(), op=operation.get("op").asText();
                if (op.equals("remove")) {
                    if (operation.size()!=2) throw new IllegalArgumentException("remove accepts only op and id");
                    boolean found=false;
                    for (String key:List.of("places","transitions","arcs")) {
                        var array=(com.fasterxml.jackson.databind.node.ArrayNode)document.get(key);
                        for (int i=array.size()-1;i>=0;i--) if (id.equals(array.get(i).path("id").asText())) { array.remove(i); found=true; }
                    }
                    if (!found) throw new IllegalArgumentException("Unknown element: " + id);
                    var arcs=(com.fasterxml.jackson.databind.node.ArrayNode)document.get("arcs");
                    for (int i=arcs.size()-1;i>=0;i--) if (id.equals(arcs.get(i).path("source").asText()) || id.equals(arcs.get(i).path("target").asText())) arcs.remove(i);
                } else {
                    String key=switch(op) { case "put_place" -> "places"; case "put_transition" -> "transitions"; case "put_arc" -> "arcs"; default -> throw new IllegalArgumentException("Unknown operation"); };
                    var array=(com.fasterxml.jackson.databind.node.ArrayNode)document.get(key);
                    com.fasterxml.jackson.databind.node.ObjectNode node=null;
                    for (var existing:array) if (id.equals(existing.path("id").asText())) node=(com.fasterxml.jackson.databind.node.ObjectNode)existing;
                    if (node==null) { node=DesktopMcpServer.JSON.createObjectNode(); array.add(node); }
                    var fields=operation.fields();
                    while(fields.hasNext()) { var field=fields.next(); if (!field.getKey().equals("op")) node.set(field.getKey(),field.getValue()); }
                }
            }
            McpToolRegistry.validate(document,DesktopMcpServer.JSON.readTree(DOCUMENT));
            var candidate=build(document);
            frame.applyMcpCanvas(candidate,false);
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
    private void checkRevision(JsonNode args) throws Exception {
        if (args.has("expected_revision") && !revision(current()).equals(args.get("expected_revision").asText())) throw new IllegalArgumentException("Revision conflict; call get_model and retry with its revision");
    }
    private void requireReplacement(JsonNode args) {
        var c = current();
        if ((!c.getNet().getGraphPetriPlaceList().isEmpty() || !c.getNet().getGraphPetriTransitionList().isEmpty() || !c.getFrames().isEmpty()) && !args.path("replace_current").asBoolean()) throw new IllegalArgumentException("Nonempty document; set replace_current=true explicitly");
    }
    static String revision(GraphCanvasModel canvas) throws Exception {
        var bytes = new ByteArrayOutputStream();
        try (var out = new ObjectOutputStream(bytes)) { out.writeObject(canvas); }
        return TrainingBundleWorkspace.hash(bytes.toByteArray());
    }
    static String xml(GraphCanvasModel canvas) throws Exception { return new PnmlModelGenerator().generateXml(canvas.toObjModel()); }
    static Map<String,Object> describe(GraphCanvasModel canvas) throws Exception {
        var document = new LinkedHashMap<String,Object>();
        document.put("name", canvas.getName());
        document.put("places", canvas.getNet().getGraphPetriPlaceList().stream().map(p -> Map.of("id", p.getPetriPlace().getId(), "name", p.getName(), "tokens", p.getPetriPlace().getMark(), "x", p.getGraphElementCenter().getX(), "y", p.getGraphElementCenter().getY())).toList());
        document.put("transitions", canvas.getNet().getGraphPetriTransitionList().stream().map(t -> {
            var p=t.getPetriTransition();
            return Map.of("id", p.getId(), "name", p.getName(), "delay", p.getParameter(), "distribution", Objects.requireNonNullElse(p.getDistribution(), "deterministic"), "deviation", p.getParamDeviation(), "priority", p.getPriority(), "probability", p.getProbability(), "x", t.getGraphElementCenter().getX(), "y", t.getGraphElementCenter().getY());
        }).toList());
        var arcs = new ArrayList<Map<String,Object>>();
        for (var a:canvas.getNet().getGraphArcInList()) arcs.add(Map.of("id",a.getArcIn().getId(),"source",id(a.getBeginElement()),"target",id(a.getEndElement()),"weight",a.getQuantity(),"read",a.getArcIn().getIsInf()));
        for (var a:canvas.getNet().getGraphArcOutList()) arcs.add(Map.of("id",a.getArcOut().getId(),"source",id(a.getBeginElement()),"target",id(a.getEndElement()),"weight",a.getQuantity(),"read",false));
        document.put("arcs",arcs);
        boolean parameters = canvas.getNet().getGraphPetriPlaceList().stream().anyMatch(p -> p.getPetriPlace().markIsParam())
                || canvas.getNet().getGraphPetriTransitionList().stream().anyMatch(t -> t.getPetriTransition().parametrIsParam() || t.getPetriTransition().distributionIsParam() || t.getPetriTransition().priorityIsParam() || t.getPetriTransition().probabilityIsParam())
                || canvas.getNet().getGraphArcInList().stream().anyMatch(a -> a.getArcIn().kIsParam() || a.getArcIn().infIsParam())
                || canvas.getNet().getGraphArcOutList().stream().anyMatch(a -> a.getArcOut().kIsParam());
        return new LinkedHashMap<>(Map.of("revision",revision(canvas),"model",document,"object_count",canvas.getFrames().size(),"structured_editing",canvas.getFrames().isEmpty() && !parameters ? "flat" : "use_pnml"));
    }
    private static String id(GraphElement n) { return n instanceof GraphPetriPlace p ? p.getPetriPlace().getId() : ((GraphPetriTransition)n).getPetriTransition().getId(); }
    static GraphCanvasModel build(JsonNode document) throws Exception {
        var net=new GraphPetriNet(); var nodes=new LinkedHashMap<String,GraphElement>(); var ids=new HashSet<String>(); int ui=0;
        for (var n:document.get("places")) {
            String id=unique(n,ids); var p=new PetriP(id,n.path("name").asText(id),n.get("tokens").asInt()); p.setNumber(net.getGraphPetriPlaceList().size());
            var graph=new GraphPetriPlace(p,ui++); position(graph,n); net.getGraphPetriPlaceList().add(graph); nodes.put(id,graph);
        }
        for (var n:document.get("transitions")) {
            String id=unique(n,ids); var p=new PetriT(id,n.path("name").asText(id),n.get("delay").asDouble()); p.setNumber(net.getGraphPetriTransitionList().size());
            String distribution=n.path("distribution").asText("deterministic");
            p.setDistribution(distribution.equals("deterministic") ? null : distribution,p.getParameter()); p.setParamDeviation(n.path("deviation").asDouble(0));
            p.setPriority(n.path("priority").asInt(0)); p.setProbability(n.path("probability").asDouble(1));
            var graph=new GraphPetriTransition(p,ui++); position(graph,n); net.getGraphPetriTransitionList().add(graph); nodes.put(id,graph);
        }
        for (var n:document.get("arcs")) {
            String id=unique(n,ids); var source=nodes.get(n.get("source").asText()); var target=nodes.get(n.get("target").asText()); int weight=n.path("weight").asInt(1);
            if (source instanceof GraphPetriPlace p && target instanceof GraphPetriTransition t) {
                var a=new ArcIn(id,p.getNumber(),t.getNumber(),weight); a.setInf(n.path("read").asBoolean()); a.setNameP(p.getName()); a.setNameT(t.getName());
                var g=new GraphArcIn(a); wire(g,p,t); net.getGraphArcInList().add(g);
            } else if (source instanceof GraphPetriTransition t && target instanceof GraphPetriPlace p && !n.path("read").asBoolean()) {
                var a=new ArcOut(id,t.getNumber(),p.getNumber(),weight); a.setNameP(p.getName()); a.setNameT(t.getName());
                var g=new GraphArcOut(a); wire(g,t,p); net.getGraphArcOutList().add(g);
            } else throw new IllegalArgumentException("Invalid endpoints or read-arc direction: " + id);
        }
        var canvas=new GraphCanvasModel(document.get("name").asText(),net);
        // Export/parse through the actual application contract before publishing the batch.
        if (!net.getGraphPetriTransitionList().isEmpty()) xml(new GraphCanvasModel(canvas));
        return canvas;
    }
    private static String unique(JsonNode n,Set<String> ids) {
        String id=n.get("id").asText();
        if (!id.matches("[A-Za-z_][A-Za-z0-9_.-]*") || !ids.add(id)) throw new IllegalArgumentException("Invalid or duplicate XML ID: " + id);
        return id;
    }
    private static void position(GraphElement n,JsonNode v) { n.setNewCoordinates(new Point2D.Double(v.get("x").asDouble(),v.get("y").asDouble())); }
    private static void wire(GraphArc a,GraphElement s,GraphElement t) { a.settingNewArc(s); a.finishSettingNewArc(t); a.updateCoordinates(); }
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

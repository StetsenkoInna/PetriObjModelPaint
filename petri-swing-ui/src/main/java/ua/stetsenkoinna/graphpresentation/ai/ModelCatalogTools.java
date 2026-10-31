package ua.stetsenkoinna.graphpresentation.ai;

import com.fasterxml.jackson.databind.JsonNode;
import java.awt.*;
import java.nio.file.*;
import java.util.*;
import java.util.List;
import ua.stetsenkoinna.graphnet.*;
import ua.stetsenkoinna.graphpresentation.PetriNetsFrame;
import ua.stetsenkoinna.graphpresentation.training.SwingCapture;
import ua.stetsenkoinna.graphpresentation.objmodel.*;
import ua.stetsenkoinna.libnet.NetTemplateCatalog;
import ua.stetsenkoinna.pnml.*;

/** Direct library instantiation and reusable composed modules, without placement dialogs. */
public final class ModelCatalogTools {
    private final PetriNetsFrame frame;
    private final ModelTools model;
    public ModelCatalogTools(PetriNetsFrame frame){this.frame=frame;model=new ModelTools(frame);}
    @McpTool(name="list_model_templates",description="List available library methods with typed parameter names and saved palette templates. Use insert_model_template to create an instance directly at world coordinates, without opening Nets or dragging a palette button.")
    public Object list(JsonNode args) throws Exception {
        return ModelTools.text(Map.of("library",NetTemplateCatalog.templates().stream().map(t->Map.of("method",t.name(),"parameters",t.parameters().stream().map(p->Map.of("name",p.name(),"type",p.type().getSimpleName())).toList())).toList(),
                "palette",new PetriObjectPalette().available().stream().map(t->Map.of("id",t.id(),"name",t.displayName(),"kind",t.kind().name(),"arguments",t.arguments())).toList()));
    }
    @McpTool(name="insert_model_template",description="Instantiate a known library method or saved palette template directly as a Petri-object. Exactly one of method/template_id; arguments are literal text converted by the catalog to declared types. prefix makes every new ID unique. Returns inserted object IDs and their derived ports. No UI placement or grouping. Use replicate_object for more instances and connect_ports for shared resources/delivery.",inputSchema="""
      {"type":"object","properties":{"expected_revision":{"type":"string"},"method":{"type":"string"},"template_id":{"type":"string"},"arguments":{"type":"array","items":{"type":"string"},"maxItems":100},"prefix":{"type":"string"},"name":{"type":"string"},"x":{"type":"number","minimum":-1000000000,"maximum":1000000000},"y":{"type":"number","minimum":-1000000000,"maximum":1000000000}},"required":["expected_revision","prefix","x","y"],"additionalProperties":false}
      """)
    public Object insert(JsonNode args) throws Exception {
        if(args.has("method")==args.has("template_id"))throw new IllegalArgumentException("Supply exactly one of method or template_id");
        return ModelTools.text(SwingCapture.call(() -> {
            model.checkRevision(args);List<String> arguments=new ArrayList<>();for(var a:args.path("arguments"))arguments.add(a.asText());
            String method=args.path("method").asText();GraphPetriNet graph;NetTemplateRef reference=null;
            if(args.has("template_id")) {
                var template=new PetriObjectPalette().available().stream().filter(t->t.id().equals(args.get("template_id").asText())).findFirst().orElseThrow(()->new IllegalArgumentException("Unknown template"));
                if(!args.has("arguments"))arguments=new ArrayList<>(template.arguments());
                if(template.kind()==PetriObjectTemplate.Kind.PROTOTYPE) {
                    if(args.has("arguments"))throw new IllegalArgumentException("Saved prototypes take no library arguments");
                    var parser=new PnmlParser();var net=parser.parse(template.prototypeFile().toFile());
                    if(!parser.getWarnings().isEmpty())throw new IllegalArgumentException("Prototype needs repairs: "+parser.getWarnings());
                    graph=GraphNetBuilder.build(net,parser.getAllPlaceCoordinates(),parser.getAllTransitionCoordinates(),null);
                } else {method=template.methodName();reference=new NetTemplateRef(method,arguments);graph=GraphNetBuilder.build(NetTemplateCatalog.instantiate(method,arguments),Map.of(),Map.of(),null);}
            } else {reference=new NetTemplateRef(method,arguments);graph=GraphNetBuilder.build(NetTemplateCatalog.instantiate(method,arguments),Map.of(),Map.of(),null);}
            var fragment=new GraphCanvasModel("Template",graph);Rectangle bounds=null;
            for(var node:graph.getGraphPetriPlaceList())bounds=bounds==null?new Rectangle(node.getBounds()):bounds.union(node.getBounds());
            for(var node:graph.getGraphPetriTransitionList())bounds=bounds==null?new Rectangle(node.getBounds()):bounds.union(node.getBounds());
            if(bounds==null)throw new IllegalArgumentException("Template has no nodes");bounds.grow(100,100);
            var object=new GraphObjectFrame(args.path("name").asText(method.isBlank()?"Module":method),bounds);object.setModelId("module");object.setTemplate(reference);fragment.getFrames().add(object);
            for(var node:graph.getGraphPetriPlaceList())fragment.claim(object,node);for(var node:graph.getGraphPetriTransitionList())fragment.claim(object,node);
            var document=ModelDocument.read(model.current());var ids=ModelFragments.merge(document,ModelDocument.read(fragment),ModelEdits.required(args,"prefix"),args.get("x").asDouble(),args.get("y").asDouble());
            frame.applyMcpCanvas(ModelDocument.build(document),false);var result=ModelTools.describe(model.current());result.put("inserted_objects",ids);return result;
        }));
    }
    @McpTool(name="export_model_fragment",description="Save selected object subtrees as a reusable composed PNML module, retaining internal fusions, special connections, parameters, nested objects and complete groups. Connections to objects outside the selection are omitted and counted in the response. The current Save path is unchanged.",inputSchema="""
      {"type":"object","properties":{"expected_revision":{"type":"string"},"object_ids":{"type":"array","items":{"type":"string"},"minItems":1,"maxItems":1000},"path":{"type":"string"},"overwrite":{"type":"boolean"}},"required":["expected_revision","object_ids","path"],"additionalProperties":false}
      """)
    public Object export(JsonNode args) throws Exception {
        return ModelTools.text(SwingCapture.call(() -> {
            model.checkRevision(args);var roots=new LinkedHashSet<String>();for(var id:args.get("object_ids"))roots.add(id.asText());
            var whole=ModelDocument.read(model.current());var part=ModelFragments.extract(whole,roots);
            Set<String> selectedNodes=new HashSet<>();for(String key:List.of("places","transitions"))for(var node:part.path(key))selectedNodes.add(node.path("id").asText());
            long omitted=0;for(var link:whole.path("links"))if(selectedNodes.contains(ModelDocument.nodeId(link.path("source").asText()))!=selectedNodes.contains(ModelDocument.nodeId(link.path("target").asText())))omitted++;
            Path path=absolute(args);ModelTools.write(path,ModelTools.xml(ModelDocument.build(part)),args.path("overwrite").asBoolean());
            return Map.of("path",path.toString(),"objects",part.path("objects").size(),"omitted_external_links",omitted,"revision",ModelTools.revision(model.current()));
        }));
    }
    @McpTool(name="insert_model_fragment",description="Insert a composed PNML module directly into the current document. Prefixes all object/group/node/arc IDs and preserves internal port links and relative arrangement. x/y specify the fragment center. The source file and current Save path are unchanged; the operation supports Undo.",inputSchema="""
      {"type":"object","properties":{"expected_revision":{"type":"string"},"path":{"type":"string"},"prefix":{"type":"string"},"x":{"type":"number","minimum":-1000000000,"maximum":1000000000},"y":{"type":"number","minimum":-1000000000,"maximum":1000000000}},"required":["expected_revision","path","prefix","x","y"],"additionalProperties":false}
      """)
    public Object importFragment(JsonNode args) throws Exception {
        Path path=absolute(args);if(Files.size(path)>1_048_576)throw new IllegalArgumentException("PNML exceeds 1 MiB");
        var parser=new PnmlModelParser();var imported=GraphCanvasModel.fromObjModel(parser.parseXml(Files.readString(path)));
        if(!parser.getWarnings().isEmpty()||!imported.getLoadWarnings().isEmpty())throw new IllegalArgumentException("Module needs import repairs");
        return ModelTools.text(SwingCapture.call(() -> {
            model.checkRevision(args);var document=ModelDocument.read(model.current());var ids=ModelFragments.merge(document,ModelDocument.read(imported),ModelEdits.required(args,"prefix"),args.get("x").asDouble(),args.get("y").asDouble());
            frame.applyMcpCanvas(ModelDocument.build(document),false);var result=ModelTools.describe(model.current());result.put("inserted_objects",ids);return result;
        }));
    }
    private static Path absolute(JsonNode args){Path path=Path.of(args.get("path").asText());if(!path.isAbsolute())throw new IllegalArgumentException("Absolute path required");return path.normalize();}
}

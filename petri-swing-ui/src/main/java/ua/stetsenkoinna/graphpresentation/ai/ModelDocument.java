package ua.stetsenkoinna.graphpresentation.ai;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import java.awt.Rectangle;
import java.awt.geom.Point2D;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import ua.stetsenkoinna.graphnet.*;
import ua.stetsenkoinna.petriobj.*;

/** Lossless structured adapter for the editable document, including object ownership and ports. */
final class ModelDocument {
    static final ObjectMapper JSON=DesktopMcpServer.JSON;
    private ModelDocument() { }
    static ObjectNode read(GraphCanvasModel canvas) {
        ObjectNode doc=JSON.createObjectNode();doc.put("name",canvas.getName());
        ArrayNode places=doc.putArray("places"),transitions=doc.putArray("transitions"),arcs=doc.putArray("arcs");
        for(var graph:canvas.getNet().getGraphPetriPlaceList()) {
            var p=graph.getPetriPlace();ObjectNode n=places.addObject();n.put("id",p.getId());n.put("name",p.getName());n.put("tokens",p.getMark());position(n,graph);
            if(p.markIsParam())n.put("tokens_parameter",p.getMarkParamName());
        }
        for(var graph:canvas.getNet().getGraphPetriTransitionList()) {
            var t=graph.getPetriTransition();ObjectNode n=transitions.addObject();n.put("id",t.getId());n.put("name",t.getName());n.put("delay",t.getParameter());
            n.put("distribution",Objects.requireNonNullElse(t.getDistribution(),"deterministic"));n.put("deviation",t.getParamDeviation());n.put("priority",t.getPriority());n.put("probability",t.getProbability());position(n,graph);
            if(t.parametrIsParam())n.put("delay_parameter",t.getParameterParamName());
            if(t.distributionIsParam())n.put("distribution_parameter",t.getDistributionParamName());
            if(t.priorityIsParam())n.put("priority_parameter",t.getPriorityParamName());
            if(t.probabilityIsParam())n.put("probability_parameter",t.getProbabilityParamName());
        }
        for(var graph:canvas.getNet().getGraphArcInList()) {
            var a=graph.getArcIn();ObjectNode n=arcs.addObject();n.put("id",a.getId());n.put("source",id(graph.getBeginElement()));n.put("target",id(graph.getEndElement()));n.put("weight",a.getQuantity());n.put("read",a.getIsInf());
            if(a.kIsParam())n.put("weight_parameter",a.getKParamName());if(a.infIsParam())n.put("read_parameter",a.getInfParamName());
            stub(n,graph.getBoundaryStubOffset());
        }
        ArrayNode links=doc.putArray("links");
        for(var graph:canvas.getNet().getGraphArcOutList()) {
            var a=graph.getArcOut();
            if(canvas.ownerOf(graph.getBeginElement())!=canvas.ownerOf(graph.getEndElement())) {
                ObjectNode n=links.addObject();n.put("type","transitionToPlace");n.put("source",port(canvas,graph.getBeginElement()));n.put("target",port(canvas,graph.getEndElement()));n.put("weight",a.getQuantity());n.put("id",linkId(n));
                if(a.kIsParam())n.put("weight_parameter",a.getKParamName());
                stub(n,graph.getBoundaryStubOffset());
            } else {
                ObjectNode n=arcs.addObject();n.put("id",a.getId());n.put("source",id(graph.getBeginElement()));n.put("target",id(graph.getEndElement()));n.put("weight",a.getQuantity());n.put("read",false);
                if(a.kIsParam())n.put("weight_parameter",a.getKParamName());
                stub(n,graph.getBoundaryStubOffset());
            }
        }
        for(var fusion:canvas.getFusions()) {
            ObjectNode n=links.addObject();n.put("type","placeFusion");n.put("source",port(canvas,fusion.getMaster()));n.put("target",port(canvas,fusion.getJoined()));n.put("id",linkId(n));
            stub(n,fusion.getBoundaryStubOffset());
        }
        ArrayNode objects=doc.putArray("objects");
        for(var frame:canvas.getFrames()) {
            ObjectNode n=objects.addObject();n.put("id",frame.getModelId());n.put("name",frame.getName());var b=frame.getExpandedBounds();n.put("x",b.x);n.put("y",b.y);n.put("width",b.width);n.put("height",b.height);
            n.put("priority",frame.getPriority());n.put("collapsed",frame.isCollapsed());n.put("content_visible",frame.isContentVisible());
            if(canvas.enclosingOf(frame)!=null)n.put("parent",canvas.enclosingOf(frame).getModelId());
            var members=n.putArray("members");for(var p:canvas.getNet().getGraphPetriPlaceList())if(canvas.ownerOf(p)==frame)members.add(id(p));for(var t:canvas.getNet().getGraphPetriTransitionList())if(canvas.ownerOf(t)==frame)members.add(id(t));
            template(n,frame.getTemplate());
        }
        ArrayNode groups=doc.putArray("groups");
        for(var group:canvas.getGroups()) {
            var n=groups.addObject();n.put("id",group.getModelId());n.put("name",group.getName());var members=n.putArray("objects");for(var member:group.getMembers())members.add(member.getModelId());template(n,group.getTemplate());
        }
        return doc;
    }
    static ArrayNode ports(GraphCanvasModel canvas) {
        var result=JSON.createArrayNode();
        for(var frame:canvas.getFrames())for(var port:canvas.portsOf(frame)) {
            var n=result.addObject();n.put("id",frame.getModelId()+":"+id(port.getElement()));n.put("object",frame.getModelId());n.put("element",id(port.getElement()));
            n.put("kind",port.isPlace()?"place":"transition");n.put("x",port.getPosition().x);n.put("y",port.getPosition().y);n.put("edge",port.getEdge().name());
        }
        return result;
    }
    static GraphCanvasModel build(JsonNode doc) throws Exception {
        McpToolRegistry.validate(doc,JSON.readTree(ModelSchemas.DOCUMENT));
        var net=new GraphPetriNet();var canvas=new GraphCanvasModel(doc.get("name").asText(),net);
        Map<String,GraphElement> nodes=new LinkedHashMap<>();Map<String,GraphObjectFrame> objects=new LinkedHashMap<>();Set<String> ids=new HashSet<>();int index=0;
        for(var n:doc.get("places")) {
            String id=unique(n,ids);var p=new PetriP(id,n.path("name").asText(id),n.get("tokens").asInt());p.setNumber(net.getGraphPetriPlaceList().size());p.setMarkParam(parameter(n,"tokens_parameter"));
            var graph=new GraphPetriPlace(p,index++);position(graph,n);nodes.put(id,graph);net.getGraphPetriPlaceList().add(graph);
        }
        for(var n:doc.get("transitions")) {
            String id=unique(n,ids);var t=new PetriT(id,n.path("name").asText(id),n.get("delay").asDouble());t.setNumber(net.getGraphPetriTransitionList().size());
            String distribution=n.path("distribution").asText("deterministic");t.setDistribution(distribution.equals("deterministic")?null:distribution,t.getParameter());t.setParamDeviation(n.path("deviation").asDouble(0));
            t.setPriority(n.path("priority").asInt(0));t.setProbability(n.path("probability").asDouble(1));
            t.setParameterParam(parameter(n,"delay_parameter"));t.setDistributionParam(parameter(n,"distribution_parameter"));t.setPriorityParam(parameter(n,"priority_parameter"));t.setProbabilityParam(parameter(n,"probability_parameter"));
            var graph=new GraphPetriTransition(t,index++);position(graph,n);nodes.put(id,graph);net.getGraphPetriTransitionList().add(graph);
        }
        Set<GraphElement> claimed=Collections.newSetFromMap(new IdentityHashMap<>());
        for(var n:doc.path("objects")) {
            String id=unique(n,ids);var frame=new GraphObjectFrame(n.get("name").asText(),new Rectangle(n.get("x").asInt(),n.get("y").asInt(),n.get("width").asInt(),n.get("height").asInt()));
            if(id.equals("free"))throw new IllegalArgumentException("Object ID free is reserved for unowned elements");
            frame.setModelId(id);frame.setPriority(n.path("priority").asInt(0));frame.setTemplate(template(n));frame.setContentVisible(n.path("content_visible").asBoolean(true));canvas.getFrames().add(frame);objects.put(id,frame);
            for(var member:n.get("members")) {
                GraphElement node=nodes.get(member.asText());if(node==null)throw new IllegalArgumentException("Unknown object member: "+member.asText());
                if(!claimed.add(node))throw new IllegalArgumentException("An element can belong to only one object: "+member.asText());canvas.claim(frame,node);
            }
        }
        for(var n:doc.path("objects"))if(!n.path("parent").asText().isBlank()) {
            var parent=objects.get(n.get("parent").asText());if(parent==null)throw new IllegalArgumentException("Unknown parent object: "+n.get("parent"));canvas.nest(objects.get(n.get("id").asText()),parent);
        }
        for(var n:doc.get("arcs")) {
            String id=unique(n,ids);var from=nodes.get(n.get("source").asText());var to=nodes.get(n.get("target").asText());
            if(from==null||to==null)throw new IllegalArgumentException("Unknown arc endpoint: "+id);
            if(canvas.ownerOf(from)!=canvas.ownerOf(to))throw new IllegalArgumentException("Ordinary/read arcs stay inside one object. Use links.transitionToPlace for an output, or share a local input place with links.placeFusion and draw its consuming/read arc inside the target object.");
            int weight=n.path("weight").asInt(1);
            if(from instanceof GraphPetriPlace p && to instanceof GraphPetriTransition t) {
                var a=new ArcIn(id,p.getNumber(),t.getNumber(),weight);a.setNameP(p.getName());a.setNameT(t.getName());a.setInf(n.path("read").asBoolean());a.setKParam(parameter(n,"weight_parameter"));a.setInfParam(parameter(n,"read_parameter"));
                var graph=new GraphArcIn(a);wire(graph,p,t);graph.setBoundaryStubOffset(stub(n));net.getGraphArcInList().add(graph);
            } else if(from instanceof GraphPetriTransition t && to instanceof GraphPetriPlace p && !n.path("read").asBoolean() && parameter(n,"read_parameter")==null) {
                var a=new ArcOut(id,t.getNumber(),p.getNumber(),weight);a.setNameP(p.getName());a.setNameT(t.getName());a.setKParam(parameter(n,"weight_parameter"));var graph=new GraphArcOut(a);wire(graph,t,p);graph.setBoundaryStubOffset(stub(n));net.getGraphArcOutList().add(graph);
            } else throw new IllegalArgumentException("Invalid arc type/direction: "+id);
        }
        Set<String> linkIds=new HashSet<>();
        for(var n:doc.path("links")) {
            var from=resolve(canvas,n.get("source").asText(),nodes,objects);var to=resolve(canvas,n.get("target").asText(),nodes,objects);
            ObjectNode canonical=n.deepCopy();canonical.put("source",port(canvas,from));canonical.put("target",port(canvas,to));String id=linkId(canonical);
            if(!linkIds.add(id))throw new IllegalArgumentException("Duplicate port connection: "+id);
            if(n.has("id")&&!n.get("id").asText().equals(id))throw new IllegalArgumentException("Link IDs are derived from type and ports; omit id when creating a new connection");
            if(n.get("type").asText().equals("placeFusion") && from instanceof GraphPetriPlace master && to instanceof GraphPetriPlace joined) {
                if(canvas.ownerOf(master)==canvas.ownerOf(joined))throw new IllegalArgumentException("A port fusion connects different objects; two aliases in one object would not define cross-object composition");
                if(parameter(n,"weight_parameter")!=null)throw new IllegalArgumentException("Shared places have no transfer weight parameter");
                if(n.has("weight")&&n.get("weight").asInt()!=1)throw new IllegalArgumentException("Shared places have one marking, not a transfer weight");canvas.joinPlaces(master,joined).setBoundaryStubOffset(stub(n));
            } else if(n.get("type").asText().equals("transitionToPlace") && from instanceof GraphPetriTransition t && to instanceof GraphPetriPlace p) {
                if(canvas.ownerOf(t)==canvas.ownerOf(p))throw new IllegalArgumentException("transitionToPlace is a connection between different objects; use an ordinary output arc inside one object");
                var a=new ArcOut("a_"+id,t.getNumber(),p.getNumber(),n.path("weight").asInt(1));a.setNameP(p.getName());a.setNameT(t.getName());a.setKParam(parameter(n,"weight_parameter"));var graph=new GraphArcOut(a);wire(graph,t,p);graph.setBoundaryStubOffset(stub(n));net.getGraphArcOutList().add(graph);
            } else throw new IllegalArgumentException("Ports allow placeFusion (place/place) or transitionToPlace (transition/place). Cross-object place-to-transition is expressed by a shared local place plus an internal arc.");
        }
        for(var n:doc.path("groups")) {
            String id=unique(n,ids);var group=new GraphObjectGroup(n.get("name").asText(),template(n));group.setModelId(id);
            for(var member:n.get("objects")) {var frame=objects.get(member.asText());if(frame==null)throw new IllegalArgumentException("Unknown group object: "+member.asText());if(canvas.groupOf(frame)!=null)throw new IllegalArgumentException("An object can belong to only one replication group");group.add(frame);}
            if(group.size()<2)throw new IllegalArgumentException("A replication group needs at least two distinct objects");canvas.getGroups().add(group);
        }
        for(var n:doc.path("objects"))objects.get(n.get("id").asText()).setCollapsed(n.path("collapsed").asBoolean());
        canvas.syncFusions();net.fixOverlappingArcs();return canvas;
    }
    static String id(GraphElement n) { return n instanceof GraphPetriPlace p?p.getPetriPlace().getId():((GraphPetriTransition)n).getPetriTransition().getId(); }
    static String nodeId(String port) { int colon=port.indexOf(':');return colon<0?port:port.substring(colon+1); }
    static String port(GraphCanvasModel c,GraphElement n) { var owner=c.ownerOf(n);return owner==null?id(n):owner.getModelId()+":"+id(n); }
    static String linkId(JsonNode n) {
        try { String value=n.path("type").asText()+"|"+nodeId(n.path("source").asText())+"|"+nodeId(n.path("target").asText());return "link_"+HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))).substring(0,32); }
        catch(Exception e){throw new IllegalStateException(e);}
    }
    private static GraphElement resolve(GraphCanvasModel canvas,String port,Map<String,GraphElement> nodes,Map<String,GraphObjectFrame> objects) {
        var node=nodes.get(nodeId(port));if(node==null)throw new IllegalArgumentException("Unknown port element: "+port);
        int colon=port.indexOf(':');if(colon>=0){var frame=objects.get(port.substring(0,colon));if(frame==null||!canvas.membersOfSubtree(frame).contains(node))throw new IllegalArgumentException("The port does not belong to that object: "+port);}
        return node;
    }
    static NetTemplateRef template(JsonNode n) { var t=n.path("template");if(t.isMissingNode())return null;var arguments=new ArrayList<String>();for(var a:t.path("arguments"))arguments.add(a.asText());return new NetTemplateRef(t.get("method").asText(),arguments); }
    private static void template(ObjectNode n,NetTemplateRef t) { if(t==null)return;var node=n.putObject("template");node.put("method",t.getMethodName());var args=node.putArray("arguments");t.getArguments().forEach(args::add); }
    private static String parameter(JsonNode n,String key) { String value=n.path(key).asText("");return value.isBlank()?null:value; }
    static String unique(JsonNode n,Set<String> ids) { String id=n.get("id").asText();if(!id.matches("[A-Za-z_][A-Za-z0-9_.-]*")||!ids.add(id))throw new IllegalArgumentException("Invalid or duplicate model ID: "+id);return id; }
    private static void stub(ObjectNode n,Point2D point){if(point!=null){var stub=n.putObject("boundary_stub");stub.put("x",point.getX());stub.put("y",point.getY());}}
    private static Point2D.Double stub(JsonNode n){var v=n.path("boundary_stub");return v.isMissingNode()?null:new Point2D.Double(v.path("x").asDouble(),v.path("y").asDouble());}
    private static void position(ObjectNode n,GraphElement graph){n.put("x",graph.getGraphElementCenter().getX());n.put("y",graph.getGraphElementCenter().getY());}
    private static void position(GraphElement graph,JsonNode n){graph.setNewCoordinates(new Point2D.Double(n.get("x").asDouble(),n.get("y").asDouble()));}
    private static void wire(GraphArc arc,GraphElement from,GraphElement to){arc.settingNewArc(from);arc.finishSettingNewArc(to);arc.updateCoordinates();}
}

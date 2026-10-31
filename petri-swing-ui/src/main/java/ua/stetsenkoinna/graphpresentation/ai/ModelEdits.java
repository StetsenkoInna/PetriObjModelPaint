package ua.stetsenkoinna.graphpresentation.ai;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import java.util.*;

/** Atomic edits are prepared against a document copy before the live canvas is replaced. */
final class ModelEdits {
    private static final Map<String,String> PUTS=Map.of("put_place","places","put_transition","transitions","put_arc","arcs","put_object","objects","put_group","groups");
    private static final Set<String> ARRAYS=Set.of("places","transitions","arcs","objects","links","groups");
    static ObjectNode apply(ObjectNode document,JsonNode operations) throws Exception {
        ObjectNode doc=document.deepCopy();
        Set<String> touchedLinks=new HashSet<>();
        for(JsonNode operation:operations) {
            String op=operation.path("op").asText();
            if(PUTS.containsKey(op))put(doc,operation,PUTS.get(op));
            else switch(op) {
                case "connect_ports" -> {connect(doc,operation);touchedLinks.add(ModelDocument.linkId(operation));}
                case "disconnect_ports" -> disconnect(doc,operation);
                case "remove" -> remove(doc,required(operation,"id"),operation.path("cascade").asBoolean());
                case "move_object" -> move(doc,required(operation,"id"),operation.path("dx").asDouble(),operation.path("dy").asDouble());
                case "replicate_object" -> replicate(doc,operation);
                case "rename_model" -> doc.put("name",required(operation,"name"));
                case "set_owner" -> {
                    String owner=required(operation,"id");ObjectNode object=find(array(doc,"objects"),owner);
                    if(object==null&&!owner.equals("free"))throw new IllegalArgumentException("Unknown object: "+owner);
                    Set<String> members=new LinkedHashSet<>();for(var n:operation.path("members"))members.add(n.asText());
                    for(String member:members)if(find(array(doc,"places"),member)==null&&find(array(doc,"transitions"),member)==null)throw new IllegalArgumentException("Unknown element: "+member);
                    for(var o:array(doc,"objects"))removeValues((ArrayNode)o.get("members"),members);
                    if(object!=null){var list=(ArrayNode)object.get("members");members.forEach(list::add);}
                }
                default -> throw new IllegalArgumentException("Unknown model operation: "+op);
            }
        }
        normalizeExistingLinks(doc,touchedLinks);
        McpToolRegistry.validate(doc,ModelDocument.JSON.readTree(ModelSchemas.DOCUMENT));return doc;
    }
    private static void put(ObjectNode doc,JsonNode operation,String key) {
        String id=required(operation,"id");ObjectNode node=find(array(doc,key),id);
        if(node==null){node=ModelDocument.JSON.createObjectNode();array(doc,key).add(node);}
        ObjectNode changes=(ObjectNode)operation.deepCopy();changes.remove("op");
        for(String numeric:List.of("tokens","delay","distribution","priority","probability","weight","read")) {
            if(changes.has(numeric)&&!changes.has(numeric+"_parameter"))node.remove(numeric+"_parameter");
            if(changes.has(numeric+"_parameter")&&changes.path(numeric+"_parameter").asText().isBlank()){changes.remove(numeric+"_parameter");node.remove(numeric+"_parameter");}
        }
        if(key.equals("places")&&(operation.has("tokens")||operation.has("tokens_parameter"))) {
            Set<String> shared=shared(doc,id);
            for(var place:array(doc,"places"))if(shared.contains(place.path("id").asText())) {
                var target=(ObjectNode)place;
                if(operation.has("tokens")){target.put("tokens",operation.get("tokens").asInt());target.remove("tokens_parameter");}
                if(operation.has("tokens_parameter")) {
                    if(operation.get("tokens_parameter").asText().isBlank())target.remove("tokens_parameter");else target.set("tokens_parameter",operation.get("tokens_parameter"));
                }
            }
        }
        node.setAll(changes);
    }
    private static Set<String> shared(ObjectNode doc,String id) {
        Set<String> connected=new HashSet<>(Set.of(id));boolean added;
        do {added=false;for(var link:array(doc,"links"))if(link.path("type").asText().equals("placeFusion")) {
            String a=ModelDocument.nodeId(link.path("source").asText()),b=ModelDocument.nodeId(link.path("target").asText());
            if(connected.contains(a)||connected.contains(b)){added|=connected.add(a);added|=connected.add(b);}
        }}while(added);return connected;
    }
    private static void connect(ObjectNode doc,JsonNode operation) {
        ObjectNode link=operation.deepCopy();link.remove("op");required(link,"type");required(link,"source");required(link,"target");
        String id=ModelDocument.linkId(link);if(link.has("id")&&!id.equals(link.get("id").asText()))throw new IllegalArgumentException("Omit id for a new connection; IDs are derived from ports");link.put("id",id);
        ObjectNode previous=find(array(doc,"links"),id);
        if(previous!=null){if(operation.has("weight")&&!operation.has("weight_parameter"))previous.remove("weight_parameter");previous.setAll(link);}else array(doc,"links").add(link);
    }
    private static void disconnect(ObjectNode doc,JsonNode operation) {
        String id=operation.has("id")?operation.get("id").asText():ModelDocument.linkId(operation);
        if(!removeId(array(doc,"links"),id))throw new IllegalArgumentException("No such port connection: "+id);
    }
    private static void remove(ObjectNode doc,String id,boolean cascade) {
        ObjectNode object=find(array(doc,"objects"),id);
        if(object!=null) {
            Set<String> removed=cascade?subtree(doc,id):Set.of(id);Set<String> nodes=new HashSet<>();
            if(!cascade&&object.has("parent")) {
                var parent=find(array(doc,"objects"),object.get("parent").asText());
                if(parent!=null)for(var member:object.get("members"))((ArrayNode)parent.get("members")).add(member.asText());
            }
            if(cascade)for(var o:array(doc,"objects"))if(removed.contains(o.path("id").asText()))for(var n:o.path("members"))nodes.add(n.asText());
            for(var o:array(doc,"objects"))if(removed.contains(o.path("parent").asText())) {
                if(!cascade&&object.has("parent"))((ObjectNode)o).set("parent",object.get("parent"));else ((ObjectNode)o).remove("parent");
            }
            for(String gone:removed)removeId(array(doc,"objects"),gone);
            for(String node:nodes)remove(doc,node,false);
            for(var group:array(doc,"groups"))removeValues((ArrayNode)group.get("objects"),removed);
            pruneGroups(doc);return;
        }
        for(String key:List.of("places","transitions"))if(removeId(array(doc,key),id)) {
            for(String a:List.of("arcs","links")) {
                var values=array(doc,a);for(int i=values.size()-1;i>=0;i--)if(id.equals(ModelDocument.nodeId(values.get(i).path("source").asText()))||id.equals(ModelDocument.nodeId(values.get(i).path("target").asText())))values.remove(i);
            }
            for(var o:array(doc,"objects"))removeValues((ArrayNode)o.get("members"),Set.of(id));return;
        }
        for(String key:List.of("arcs","links","groups"))if(removeId(array(doc,key),id))return;
        throw new IllegalArgumentException("No model element with id: "+id);
    }
    static void move(ObjectNode doc,String id,double dx,double dy) {
        Set<String> objects=subtree(doc,id),nodes=new HashSet<>();
        for(var o:array(doc,"objects"))if(objects.contains(o.path("id").asText())) {offset((ObjectNode)o,dx,dy);for(var n:o.get("members"))nodes.add(n.asText());}
        for(String key:List.of("places","transitions"))for(var n:array(doc,key))if(nodes.contains(n.path("id").asText()))offset((ObjectNode)n,dx,dy);
    }
    private static void replicate(ObjectNode doc,JsonNode operation) {
        String root=required(operation,"id"),prefix=required(operation,"prefix");int count=operation.path("count").asInt(1);
        Set<String> selected=subtree(doc,root),nodes=new LinkedHashSet<>();
        ObjectNode original=doc.deepCopy();for(var o:array(original,"objects"))if(selected.contains(o.path("id").asText()))for(var n:o.get("members"))nodes.add(n.asText());
        List<String> newRoots=new ArrayList<>();if(operation.path("include_original").asBoolean())newRoots.add(root);
        for(int i=1;i<=count;i++) {
            String p=prefix+"_"+i+"_";Map<String,String> replacements=new HashMap<>();selected.forEach(id->replacements.put(id,p+id));nodes.forEach(id->replacements.put(id,p+id));newRoots.add(replacements.get(root));
            double dx=operation.path("dx").asDouble(280)*i,dy=operation.path("dy").asDouble(0)*i;
            for(String key:List.of("places","transitions"))for(var n:array(original,key))if(nodes.contains(n.path("id").asText())) {ObjectNode copy=n.deepCopy();copy.put("id",replacements.get(n.get("id").asText()));offset(copy,dx,dy);array(doc,key).add(copy);}
            for(var o:array(original,"objects"))if(selected.contains(o.path("id").asText())) {
                ObjectNode copy=o.deepCopy();copy.put("id",replacements.get(o.get("id").asText()));if(o.get("id").asText().equals(root))copy.put("name",o.get("name").asText()+" "+i);
                var members=copy.putArray("members");for(var n:o.get("members"))members.add(replacements.get(n.asText()));
                if(selected.contains(o.path("parent").asText()))copy.put("parent",replacements.get(o.get("parent").asText()));offset(copy,dx,dy);array(doc,"objects").add(copy);
            }
            for(var arc:array(original,"arcs"))if(nodes.contains(arc.path("source").asText())&&nodes.contains(arc.path("target").asText())) {
                ObjectNode copy=arc.deepCopy();copy.put("id",p+arc.get("id").asText());copy.put("source",replacements.get(arc.get("source").asText()));copy.put("target",replacements.get(arc.get("target").asText()));array(doc,"arcs").add(copy);
            }
            for(var link:array(original,"links")) {
                boolean from=nodes.contains(ModelDocument.nodeId(link.path("source").asText())),to=nodes.contains(ModelDocument.nodeId(link.path("target").asText()));
                if((from&&to)||(operation.path("include_external_links").asBoolean()&&(from||to))) {
                    ObjectNode copy=link.deepCopy();copy.remove("id");copy.put("source",replacePort(copy.get("source").asText(),replacements));copy.put("target",replacePort(copy.get("target").asText(),replacements));array(doc,"links").add(copy);
                }
            }
            for(var group:array(original,"groups")) {
                var members=new ArrayList<String>();for(var member:group.get("objects"))if(selected.contains(member.asText()))members.add(replacements.get(member.asText()));
                if(members.size()==group.get("objects").size()) {ObjectNode copy=group.deepCopy();copy.put("id",p+group.get("id").asText());copy.put("name",group.get("name").asText()+" "+i);var list=copy.putArray("objects");members.forEach(list::add);array(doc,"groups").add(copy);}
            }
        }
        if(operation.has("group_id")) {
            var group=array(doc,"groups").addObject();group.put("id",operation.get("group_id").asText());group.put("name",operation.path("name").asText(prefix));var list=group.putArray("objects");newRoots.forEach(list::add);
        }
    }
    static String replacePort(String port,Map<String,String> ids) {int colon=port.indexOf(':');if(colon<0)return ids.getOrDefault(port,port);return ids.getOrDefault(port.substring(0,colon),port.substring(0,colon))+":"+ids.getOrDefault(port.substring(colon+1),port.substring(colon+1));}
    private static void normalizeExistingLinks(ObjectNode doc,Set<String> touched) {
        Map<String,String> owners=new HashMap<>();
        for(var object:array(doc,"objects"))for(var member:object.path("members")) {
            String old=owners.put(member.asText(),object.path("id").asText());
            if(old!=null)throw new IllegalArgumentException("An element can belong to only one object: "+member.asText());
        }
        var links=array(doc,"links");
        for(int i=links.size()-1;i>=0;i--) {
            var link=(ObjectNode)links.get(i);if(touched.contains(ModelDocument.linkId(link)))continue;
            String source=ModelDocument.nodeId(link.path("source").asText()),target=ModelDocument.nodeId(link.path("target").asText());
            link.put("source",owners.containsKey(source)?owners.get(source)+":"+source:source);
            link.put("target",owners.containsKey(target)?owners.get(target)+":"+target:target);
            if(link.path("type").asText().equals("transitionToPlace")&&Objects.equals(owners.get(source),owners.get(target))) {
                ObjectNode arc=link.deepCopy();arc.remove("type");arc.put("id","a_"+ModelDocument.linkId(link));arc.put("source",source);arc.put("target",target);arc.put("read",false);array(doc,"arcs").add(arc);links.remove(i);
            }
        }
    }
    static Set<String> subtree(ObjectNode doc,String id) {
        if(find(array(doc,"objects"),id)==null)throw new IllegalArgumentException("Unknown object: "+id);
        Set<String> result=new LinkedHashSet<>(Set.of(id));boolean changed;do{changed=false;for(var o:array(doc,"objects"))if(result.contains(o.path("parent").asText()))changed|=result.add(o.path("id").asText());}while(changed);return result;
    }
    private static void offset(ObjectNode node,double dx,double dy){node.put("x",node.get("x").asDouble()+dx);node.put("y",node.get("y").asDouble()+dy);}
    private static void pruneGroups(ObjectNode doc){var groups=array(doc,"groups");for(int i=groups.size()-1;i>=0;i--)if(groups.get(i).path("objects").size()<2)groups.remove(i);}
    private static void removeValues(ArrayNode array,Set<String> values){for(int i=array.size()-1;i>=0;i--)if(values.contains(array.get(i).asText()))array.remove(i);}
    static ObjectNode find(ArrayNode array,String id){for(var n:array)if(id.equals(n.path("id").asText()))return(ObjectNode)n;return null;}
    static ArrayNode array(ObjectNode doc,String key){if(!ARRAYS.contains(key))throw new IllegalArgumentException("Unknown collection");if(!doc.has(key))doc.putArray(key);return(ArrayNode)doc.get(key);}
    private static boolean removeId(ArrayNode array,String id){for(int i=0;i<array.size();i++)if(id.equals(array.get(i).path("id").asText())){array.remove(i);return true;}return false;}
    static String required(JsonNode node,String key){String value=node.path(key).asText();if(value.isBlank())throw new IllegalArgumentException("Missing "+key);return value;}
}

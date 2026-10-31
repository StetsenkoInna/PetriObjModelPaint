package ua.stetsenkoinna.graphpresentation.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.*;
import java.util.*;

/** Subdocuments and instances retain their original relative geometry and composition. */
final class ModelFragments {
    static ObjectNode extract(ObjectNode document,Set<String> roots) {
        Set<String> objects=new LinkedHashSet<>(),nodes=new LinkedHashSet<>();
        for(String root:roots)objects.addAll(ModelEdits.subtree(document,root));
        ObjectNode result=document.deepCopy();
        for(var o:result.withArray("objects"))if(objects.contains(o.path("id").asText()))for(var n:o.path("members"))nodes.add(n.asText());
        filter(result,"objects",n->objects.contains(n.path("id").asText()));
        for(var o:result.withArray("objects"))if(!objects.contains(o.path("parent").asText()))((ObjectNode)o).remove("parent");
        for(String key:List.of("places","transitions"))filter(result,key,n->nodes.contains(n.path("id").asText()));
        for(String key:List.of("arcs","links"))filter(result,key,n->nodes.contains(ModelDocument.nodeId(n.path("source").asText()))&&nodes.contains(ModelDocument.nodeId(n.path("target").asText())));
        filter(result,"groups",n->{for(var o:n.path("objects"))if(!objects.contains(o.asText()))return false;return true;});
        return result;
    }
    static List<String> merge(ObjectNode destination,ObjectNode fragment,String prefix,double centerX,double centerY) {
        Map<String,String> ids=new HashMap<>();
        for(String key:List.of("places","transitions","arcs","objects","groups"))for(var n:fragment.withArray(key))ids.put(n.path("id").asText(),prefix+"_"+n.path("id").asText());
        double minX=Double.POSITIVE_INFINITY,minY=minX,maxX=Double.NEGATIVE_INFINITY,maxY=maxX;
        for(String key:List.of("places","transitions","objects"))for(var n:fragment.withArray(key)) {
            double x=n.path("x").asDouble(),y=n.path("y").asDouble();minX=Math.min(minX,x);minY=Math.min(minY,y);maxX=Math.max(maxX,x+n.path("width").asDouble());maxY=Math.max(maxY,y+n.path("height").asDouble());
        }
        double dx=Double.isFinite(minX)?centerX-(minX+maxX)/2:0,dy=Double.isFinite(minY)?centerY-(minY+maxY)/2:0;
        List<String> inserted=new ArrayList<>();
        for(String key:List.of("places","transitions","arcs","objects","links","groups"))for(var n:fragment.withArray(key)) {
            ObjectNode copy=n.deepCopy();
            if(key.equals("links")) {copy.remove("id");copy.put("source",ModelEdits.replacePort(n.path("source").asText(),ids));copy.put("target",ModelEdits.replacePort(n.path("target").asText(),ids));}
            else copy.put("id",ids.get(n.path("id").asText()));
            if(key.equals("arcs")){copy.put("source",ids.get(n.path("source").asText()));copy.put("target",ids.get(n.path("target").asText()));}
            if(copy.has("x")){copy.put("x",copy.get("x").asDouble()+dx);copy.put("y",copy.get("y").asDouble()+dy);}
            if(key.equals("objects")) {
                inserted.add(copy.path("id").asText());var members=copy.putArray("members");for(var member:n.path("members"))members.add(ids.get(member.asText()));
                if(n.has("parent"))copy.put("parent",ids.get(n.get("parent").asText()));
            }
            if(key.equals("groups")){var members=copy.putArray("objects");for(var member:n.path("objects"))members.add(ids.get(member.asText()));}
            ModelEdits.array(destination,key).add(copy);
        }
        return inserted;
    }
    private static void filter(ObjectNode doc,String key,java.util.function.Predicate<JsonNode> keep){var array=doc.withArray(key);for(int i=array.size()-1;i>=0;i--)if(!keep.test(array.get(i)))array.remove(i);}
}

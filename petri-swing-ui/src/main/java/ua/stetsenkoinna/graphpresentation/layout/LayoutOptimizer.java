package ua.stetsenkoinna.graphpresentation.layout;

import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.geom.Line2D;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import ua.stetsenkoinna.graphnet.*;

/** Improves spacing around the existing drawing without replacing its graph or choosing a new topology. */
public final class LayoutOptimizer {
    private static final int DEFAULT_GAP = 24;
    private static final int FRAME_PADDING = 32;
    private static final int MAX_DIAGNOSTICS = 1000;
    private static final Font FRAME_NAME_FONT = new Font(Font.SANS_SERIF, Font.BOLD, 12);
    private static final Font FRAME_DETAIL_FONT = new Font(Font.SANS_SERIF, Font.PLAIN, 11);
    private static final Font PORT_FONT = new Font(Font.SANS_SERIF, Font.PLAIN, 10);

    public record Result(GraphCanvasModel canvas, Map<String,Object> report) { }
    public record Options(int minimumGap) {
        public Options { if (minimumGap < 8 || minimumGap > 300) throw new IllegalArgumentException("Minimum spacing must be between 8 and 300 canvas units"); }
        public static Options defaults() { return new Options(DEFAULT_GAP); }
    }
    private LayoutOptimizer() { }

    public static Result optimize(GraphCanvasModel source, Font font) {
        return optimize(source, font, Options.defaults());
    }

    public static Result optimize(GraphCanvasModel source, Font font, Options options) {
        Objects.requireNonNull(source); Objects.requireNonNull(options);
        String semantics = semanticFingerprint(source);
        GraphCanvasModel canvas = new GraphCanvasModel(source);
        Font actualFont = font == null ? new Font(Font.SANS_SERIF, Font.PLAIN, 12) : font;
        var engine = new Engine(canvas, actualFont, options.minimumGap());
        Map<String,Point2D> oldPositions = positions(source);
        Map<String,Rectangle> oldFrames = frames(source);
        var collapsed = new IdentityHashMap<GraphObjectFrame,Boolean>();
        for (var frame : canvas.getFrames()) { collapsed.put(frame, frame.isCollapsed()); frame.setCollapsed(false); }
        engine.updateArcs();
        Diagnostics before = engine.inspectExpanded();
        engine.arrangeScope(null, Collections.newSetFromMap(new IdentityHashMap<>()));
        for (int pass=0;pass<4;pass++) {
            if (!engine.clearStrayArcs()) break;
            engine.arrangeScope(null, Collections.newSetFromMap(new IdentityHashMap<>()));
        }
        engine.updateArcs();
        Diagnostics after = engine.inspectExpanded();
        for (var frame : canvas.getFrames()) frame.setCollapsed(Boolean.TRUE.equals(collapsed.get(frame)));
        after.merge(engine.inspectHiddenPorts());
        if (!semantics.equals(semanticFingerprint(canvas))) throw new IllegalStateException("Spacing optimization changed model semantics");

        int movedNodes = 0, movedFrames = 0;
        for (var entry : positions(canvas).entrySet()) {
            Point2D old = oldPositions.get(entry.getKey());
            if (old == null || old.distance(entry.getValue()) > 0.001) movedNodes++;
        }
        for (var entry : frames(canvas).entrySet()) if (!entry.getValue().equals(oldFrames.get(entry.getKey()))) movedFrames++;
        long errors = after.errors;
        Map<String,Object> report = new LinkedHashMap<>();
        report.put("semantic_preserved", true);
        report.put("semantic_sha256", semantics);
        report.put("verified", after.total == 0);
        report.put("readability_verified", errors == 0);
        report.put("moved_nodes", movedNodes);
        report.put("moved_objects", movedFrames);
        report.put("minimum_gap", options.minimumGap());
        report.put("before_issue_count", before.total);
        report.put("after_issue_count", after.total);
        report.put("unreadable_count", errors);
        report.put("diagnostics", List.copyOf(after));
        report.put("diagnostics_truncated", after.total > after.size());
        report.put("routing", "Existing straight arcs and paired-arc offsets retained; unresolved crossings are reported.");
        report.put("verification_scope", "Expanded node labels, parameters and arc weights; object headers; replication-group titles; collapsed/hidden port labels, all at the supplied canvas font. Straight-line crossings are reported, not rerouted.");
        return new Result(canvas, Collections.unmodifiableMap(report));
    }

    /** Inspects the current geometry without moving anything. */
    public static Map<String,Object> inspect(GraphCanvasModel source, Font font) {
        GraphCanvasModel canvas = new GraphCanvasModel(source);
        var engine = new Engine(canvas, font == null ? new Font(Font.SANS_SERIF, Font.PLAIN, 12) : font, DEFAULT_GAP);
        for(var frame:canvas.getFrames())frame.setCollapsed(false);
        engine.updateArcs();
        var issues = engine.inspectExpanded();
        for(var frame:canvas.getFrames())frame.setCollapsed(Boolean.TRUE.equals(engine.originallyCollapsed.get(frame)));
        issues.merge(engine.inspectHiddenPorts());
        return Map.of("verified", issues.total == 0, "issue_count", issues.total, "diagnostics", List.copyOf(issues), "diagnostics_truncated", issues.total > issues.size());
    }

    /** A geometry-independent contract check used before and after optimization. */
    public static String semanticFingerprint(GraphCanvasModel canvas) {
        try {
            var values = new ArrayList<String>(); values.add("name:" + text(canvas.getName()));
            for (var graph : canvas.getNet().getGraphPetriPlaceList()) {
                var p = graph.getPetriPlace();
                values.add("place:" + text(p.getId(), p.getName(), Integer.toString(p.getMark()), p.getMarkParamName(), ownerId(canvas, graph)));
            }
            for (var graph : canvas.getNet().getGraphPetriTransitionList()) {
                var t = graph.getPetriTransition();
                values.add("transition:" + text(t.getId(), t.getName(), Double.toString(t.getParameter()), t.getDistribution(), Double.toString(t.getParamDeviation()), Integer.toString(t.getPriority()), Double.toString(t.getProbability()), t.getParameterParamName(), t.getDistributionParamName(), t.getPriorityParamName(), t.getProbabilityParamName(), ownerId(canvas, graph)));
            }
            for (var graph : canvas.getNet().getGraphArcInList()) {
                var a = graph.getArcIn();
                values.add("in:" + text(a.getId(), id(graph.getBeginElement()), id(graph.getEndElement()), Integer.toString(a.getQuantity()), Boolean.toString(a.getIsInf()), a.getKParamName(), a.getInfParamName()));
            }
            for (var graph : canvas.getNet().getGraphArcOutList()) {
                var a = graph.getArcOut();
                values.add("out:" + text(a.getId(), id(graph.getBeginElement()), id(graph.getEndElement()), Integer.toString(a.getQuantity()), a.getKParamName()));
            }
            for (var f : canvas.getFusions()) values.add("fusion:" + text(id(f.getMaster()), id(f.getJoined())));
            for (var f : canvas.getFrames()) values.add("object:" + text(f.getModelId(), f.getName(), Integer.toString(f.getPriority()), f.getEnclosing() == null ? null : f.getEnclosing().getModelId(), Boolean.toString(f.isCollapsed()), Boolean.toString(f.isContentVisible()), template(f.getTemplate())));
            for (var g : canvas.getGroups()) values.add("group:" + text(g.getModelId(), g.getName(), template(g.getTemplate()), g.getMembers().stream().map(GraphObjectFrame::getModelId).reduce("", (a,b) -> a + text(b))));
            Collections.sort(values);
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(String.join("\n", values).getBytes(StandardCharsets.UTF_8)));
        } catch (Exception failure) { throw new IllegalStateException("Cannot compare model semantics", failure); }
    }

    private static String template(NetTemplateRef template) { return template == null ? null : text(template.getMethodName(), String.join("\u001f", template.getArguments())); }
    private static String text(String... values) { StringBuilder out = new StringBuilder(); for (String value : values) out.append(value == null ? "-1:" : value.length() + ":" + value); return out.toString(); }
    private static String ownerId(GraphCanvasModel canvas, GraphElement node) { var owner = canvas.ownerOf(node); return owner == null ? null : owner.getModelId(); }
    private static String id(GraphElement element) { return element instanceof GraphPetriPlace p ? p.getPetriPlace().getId() : ((GraphPetriTransition) element).getPetriTransition().getId(); }
    private static Map<String,Point2D> positions(GraphCanvasModel canvas) { Map<String,Point2D> result = new HashMap<>(); for (var n : nodes(canvas)) result.put(id(n), n.getGraphElementCenter()); return result; }
    private static Map<String,Rectangle> frames(GraphCanvasModel canvas) { Map<String,Rectangle> result = new HashMap<>(); for (var f : canvas.getFrames()) result.put(f.getModelId(), f.getExpandedBounds()); return result; }
    private static List<GraphElement> nodes(GraphCanvasModel canvas) { List<GraphElement> result = new ArrayList<>(canvas.getNet().getGraphPetriPlaceList()); result.addAll(canvas.getNet().getGraphPetriTransitionList()); return result; }

    private static final class Engine {
        final GraphCanvasModel canvas;
        final Font font;
        final FontMetrics metrics, titleMetrics, detailMetrics, portMetrics, groupMetrics;
        final int gap;
        final List<GraphElement> nodes;
        final List<GraphArc> arcs;
        final List<int[]> movementOffsets=offsets();
        int remainingTrials=40_000;
        final Map<GraphObjectFrame,Boolean> originallyCollapsed = new IdentityHashMap<>();
        Engine(GraphCanvasModel canvas, Font font, int gap) {
            this.canvas = canvas; this.font = font; this.gap = gap;
            Graphics2D graphics = new BufferedImage(1,1,BufferedImage.TYPE_INT_ARGB).createGraphics();
            try { metrics = graphics.getFontMetrics(font); titleMetrics = graphics.getFontMetrics(FRAME_NAME_FONT); detailMetrics = graphics.getFontMetrics(FRAME_DETAIL_FONT); portMetrics = graphics.getFontMetrics(PORT_FONT); groupMetrics = graphics.getFontMetrics(font.deriveFont(Font.BOLD)); }
            finally { graphics.dispose(); }
            nodes = nodes(canvas); arcs = new ArrayList<>(canvas.getNet().getGraphArcInList()); arcs.addAll(canvas.getNet().getGraphArcOutList());
            for (var frame : canvas.getFrames()) originallyCollapsed.put(frame,frame.isCollapsed());
        }
        void arrangeScope(GraphObjectFrame owner, Set<GraphObjectFrame> seen) {
            var children = new ArrayList<>(canvas.childrenOf(owner));
            children.sort(Comparator.comparing(GraphObjectFrame::getModelId));
            for (var child : children) if (seen.add(child)) arrangeScope(child, seen);
            List<Item> items = items(owner);
            items.sort(Comparator.comparingDouble((Item i) -> i.anchor.getX()).thenComparingDouble(i -> i.anchor.getY()).thenComparing(Item::id));
            for (int index=1; index<items.size(); index++) {
                Item item = items.get(index);
                for (int attempt=0; attempt<Math.max(8, items.size()*2); attempt++) {
                    Item blocker = null;
                    for (int before=0; before<index; before++) if (grown(items.get(before).bounds(), gap).intersects(item.bounds())) { blocker=items.get(before); break; }
                    if (blocker==null) break;
                    Rectangle2D a=blocker.bounds(), b=item.bounds();
                    double right=a.getMaxX()+gap-b.getMinX();
                    double down=a.getMaxY()+gap-b.getMinY();
                    double up=a.getMinY()-gap-b.getMaxY();
                    double dy=b.getCenterY()>=a.getCenterY()?down:up;
                    boolean horizontal=Math.abs(item.anchor.getX()-blocker.anchor.getX())>=Math.abs(item.anchor.getY()-blocker.anchor.getY());
                    if (horizontal) item.move((int)Math.ceil(right),0);
                    else item.move(0,dy>=0?(int)Math.ceil(dy):(int)Math.floor(dy));
                }
            }
            if (owner != null) expand(owner);
        }
        List<Item> items(GraphObjectFrame owner) {
            List<Item> items=new ArrayList<>();
            for (var n:nodes) if(canvas.ownerOf(n)==owner) items.add(new Item(n,null));
            for (var f:canvas.childrenOf(owner)) items.add(new Item(null,f));
            return items;
        }
        void expand(GraphObjectFrame frame) {
            Rectangle2D contents=null;
            for (var item:items(frame)) contents=union(contents,item.bounds());
            for(var group:canvas.getGroups())if(!group.getMembers().isEmpty()&&group.getMembers().stream().allMatch(member -> canvas.enclosingOf(member)==frame)) {
                Rectangle2D label=groupTitle(group);if(label!=null)contents=union(contents,label);
            }
            Rectangle old=frame.getBounds();
            double x=old.x,y=old.y,right=old.getMaxX(),bottom=old.getMaxY();
            if(contents!=null) {
                x=Math.min(x,contents.getMinX()-FRAME_PADDING); y=Math.min(y,contents.getMinY()-FRAME_PADDING-GraphObjectFrame.HEADER_HEIGHT);
                right=Math.max(right,contents.getMaxX()+FRAME_PADDING); bottom=Math.max(bottom,contents.getMaxY()+FRAME_PADDING);
            }
            int index=canvas.getFrames().indexOf(frame);
            String detail="priority "+frame.getPriority()+(frame.getTemplate()==null?"":"  ·  "+frame.getTemplate().getMethodName());
            double needed=titleMetrics.stringWidth("O"+index+"  "+frame.getName())+detailMetrics.stringWidth(detail)+84;
            right=Math.max(right,x+needed);
            frame.setBounds(new Rectangle((int)Math.floor(x),(int)Math.floor(y),(int)Math.ceil(right-x),(int)Math.ceil(bottom-y)));
        }
        void move(GraphElement node,int dx,int dy) { node.setNewCoordinates(new Point2D.Double(node.getGraphElementCenter().getX()+dx,node.getGraphElementCenter().getY()+dy)); }
        void move(GraphObjectFrame frame,int dx,int dy) {
            for(var child:canvas.subtreeOf(frame)) child.moveBy(dx,dy);
            for(var node:canvas.membersOfSubtree(frame)) move(node,dx,dy);
        }
        void updateArcs() {
            for(var arc:arcs) { arc.setFirstArc(false);arc.setSecondArc(false); }
            for(var in:canvas.getNet().getGraphArcInList()) for(var out:canvas.getNet().getGraphArcOutList()) if(in.getBeginElement()==out.getEndElement() && in.getEndElement()==out.getBeginElement()) in.twoArcs(out);
            for(var arc:arcs) if(arc.getBeginElement()!=null && arc.getEndElement()!=null) arc.updateCoordinates();
        }
        /** Locally adjusts existing nodes, including endpoints whose arrows cover their own text. */
        boolean clearStrayArcs() {
            if(nodes.size()>150 || arcs.size()>300) return false;
            boolean changed=false;
            for(int pass=0;pass<80 && remainingTrials>0;pass++) {
                updateArcs();
                int baseline=collisionScore(); if(baseline==0)break;
                GraphElement bestNode=null; Point2D bestPosition=null;double bestQuality=0,bestDistance=Double.POSITIVE_INFINITY;
                for(var node:nodes) {
                    Point2D origin=node.getGraphElementCenter();
                    for(int[] offset:movementOffsets) {
                        if(remainingTrials--<=0)break;
                        move(node,offset[0],offset[1]);updateArcs();
                        int score=collisionScore();double distance=Math.hypot(offset[0],offset[1]),quality=(baseline-score)/(gap+distance);
                        if(quality>bestQuality || (quality==bestQuality && quality>0 && distance<bestDistance)) {
                            bestNode=node;bestPosition=node.getGraphElementCenter();bestQuality=quality;bestDistance=distance;
                        }
                        node.setNewCoordinates(origin);
                    }
                }
                if(bestNode==null) {
                    if (clearCollisionPair(baseline)) { changed=true;continue; }
                    break;
                }
                bestNode.setNewCoordinates(bestPosition);changed=true;
            }
            updateArcs();
            return changed;
        }
        /** A neutral first adjustment may need one further adjustment to clear both labels. */
        boolean clearCollisionPair(int baseline) {
            if(baseline>400)return false;
            updateArcs();var initial=labelCollisions();
            if(initial.isEmpty())return false;
            GraphElement bestFirst=null,bestSecond=null;Point2D bestFirstPosition=null,bestSecondPosition=null;
            double bestDistance=Double.POSITIVE_INFINITY;
            for(var first:collisionNodes()) {
                Point2D firstOrigin=first.getGraphElementCenter();
                for(int[] firstOffset:movementOffsets) {
                    if(remainingTrials--<=0)break;
                    double distance1=Math.hypot(firstOffset[0],firstOffset[1]);if(distance1>=bestDistance)continue;
                    move(first,firstOffset[0],firstOffset[1]);updateArcs();
                    if(collisionScore()==baseline && !labelCollisions().containsAll(initial)) {
                        for(var second:collisionNodes()) {
                            if(second==first)continue;Point2D secondOrigin=second.getGraphElementCenter();
                            for(int[] secondOffset:movementOffsets) {
                                if(remainingTrials--<=0)break;
                                double distance=distance1+Math.hypot(secondOffset[0],secondOffset[1]);if(distance>=bestDistance)continue;
                                move(second,secondOffset[0],secondOffset[1]);updateArcs();
                                if(collisionScore()<baseline) {
                                    bestFirst=first;bestSecond=second;bestFirstPosition=first.getGraphElementCenter();bestSecondPosition=second.getGraphElementCenter();bestDistance=distance;
                                }
                                second.setNewCoordinates(secondOrigin);
                            }
                        }
                    }
                    first.setNewCoordinates(firstOrigin);
                }
            }
            if(bestFirst==null){updateArcs();return false;}
            bestFirst.setNewCoordinates(bestFirstPosition);bestSecond.setNewCoordinates(bestSecondPosition);updateArcs();return true;
        }
        Set<String> labelCollisions() {
            Set<String> collisions=new HashSet<>();
            for(var arc:arcs)if(arc.getGraphElement()!=null)for(var node:nodes)for(var label:labels(node))if(label.intersectsLine(arc.getGraphElement())){collisions.add(arcId(arc)+":"+id(node));break;}
            return collisions;
        }
        Set<GraphElement> collisionNodes() {
            Set<GraphElement> involved=new LinkedHashSet<>();
            for(var arc:arcs)if(arc.getGraphElement()!=null)for(var node:nodes) {
                boolean hit=node!=arc.getBeginElement()&&node!=arc.getEndElement()&&node.getBounds().intersectsLine(arc.getGraphElement());
                for(var label:labels(node))if(label.intersectsLine(arc.getGraphElement())){hit=true;break;}
                if(hit){involved.add(node);involved.add(arc.getBeginElement());involved.add(arc.getEndElement());}
            }
            return involved;
        }
        List<int[]> offsets() {
            var offsets=new ArrayList<int[]>();
            for(int step:new int[]{12,24,48,96,192,384,768}) {
                offsets.add(new int[]{0,-step});offsets.add(new int[]{0,step});offsets.add(new int[]{-step,0});offsets.add(new int[]{step,0});
                if(step>=24 && step<=384)for(int dx:new int[]{-step,step})for(int dy:new int[]{-step,step})offsets.add(new int[]{dx,dy});
            }
            return offsets;
        }
        boolean insideOwner(GraphElement node) {
            var owner=canvas.ownerOf(node);if(owner==null)return true;
            Rectangle body=new Rectangle(owner.getBounds());body.y+=GraphObjectFrame.HEADER_HEIGHT;body.height-=GraphObjectFrame.HEADER_HEIGHT;
            return body.contains(footprint(node));
        }
        int collisionScore() {
            int score=0;
            for(int i=0;i<nodes.size();i++)for(int j=i+1;j<nodes.size();j++)if(grown(footprint(nodes.get(i)),gap).intersects(footprint(nodes.get(j))))score+=1000;
            for(var arc:arcs) {
                var line=arc.getGraphElement();if(line==null)continue;
                if(line.getP1().distance(line.getP2())<12)score+=100;
                Rectangle2D quantity=quantityLabel(arc);
                for(var node:nodes) {
                    if(node!=arc.getBeginElement()&&node!=arc.getEndElement()&&node.getBounds().intersectsLine(line))score+=100;
                    for(var label:labels(node))if(label.intersectsLine(line)){score+=100;break;}
                    if(quantity!=null&&quantity.intersects(footprint(node)))score+=100;
                }
            }
            for(var fusion:canvas.getFusions()) {
                var line=fusionLine(fusion);
                for(var node:nodes) {
                    if(node!=fusion.getMaster()&&node!=fusion.getJoined()&&node.getBounds().intersectsLine(line))score+=100;
                    for(var label:labels(node))if(label.intersectsLine(line)){score+=100;break;}
                }
            }
            return score;
        }
        Rectangle2D footprint(GraphElement node) {
            Rectangle2D result=new Rectangle2D.Double(node.getBounds().x,node.getBounds().y,node.getBounds().width,node.getBounds().height);
            for(var label:labels(node))result=union(result,label);
            return result;
        }
        List<Rectangle2D> labels(GraphElement node) {
            var labels=new ArrayList<Rectangle2D>();Point2D c=node.getGraphElementCenter();double x=c.getX(),y=c.getY();
            if(node instanceof GraphPetriPlace graph) {
                var p=graph.getPetriPlace();String mark=p.markIsParam()?p.getMarkParamName():Integer.toString(p.getMark());
                labels.add(textRect(p.getName(),x-p.getName().length()*5.0,y-GraphPetriPlace.getDiameter()+GraphPetriPlace.getDiameter()/3.0,metrics));
                if(metrics.stringWidth(mark)>GraphPetriPlace.getDiameter()-6)labels.add(textRect(mark,x-Integer.toString(p.getMark()).length()*5.0,y+5,metrics));
            } else {
                var graph=(GraphPetriTransition)node;var t=graph.getPetriTransition();
                labels.add(textRect(t.getName(),x-t.getName().length()*5.0,y-GraphPetriTransition.getHeight()/2.0-GraphPetriTransition.getHeight()/5.0,metrics));
                String delay=t.parametrIsParam()?t.getParameterParamName():Double.toString(t.getParameter());
                String distribution=t.distributionIsParam()?t.getDistributionParamName():t.getDistribution();
                labels.add(textRect("d="+delay+(distribution==null?"":"("+distribution+")"),x-Double.toString(t.getParameter()).length()*5.0,y+GraphPetriTransition.getHeight()/2.0+20,metrics));
                labels.add(textRect("r="+t.getProbability(),x-Double.toString(t.getBuffer()).length()*5.0,y+GraphPetriTransition.getHeight()/2.0+40,metrics));
            }
            return labels;
        }
        Diagnostics inspectExpanded() {
            Diagnostics issues=new Diagnostics();
            List<GraphObjectFrame> levels=new ArrayList<>();levels.add(null);levels.addAll(canvas.getFrames());
            for(var level:levels) {
                var items=items(level);
                for(int i=0;i<items.size();i++)for(int j=i+1;j<items.size();j++)if(items.get(i).bounds().intersects(items.get(j).bounds()))issues.add(issue("element_overlap","error",List.of(items.get(i).id(),items.get(j).id()),"Element, label or object rectangles overlap."));
            }
            for(var node:nodes) {
                var owner=canvas.ownerOf(node);
                if(owner!=null&&!insideOwner(node))issues.add(issue("object_label_clipping","error",List.of(id(node),owner.getModelId()),"The node or its labels extend beyond the object's content area."));
            }
            for(var arc:arcs) {
                Line2D line=arc.getGraphElement();if(line==null)continue;
                if(!finite(line.getX1(),line.getY1(),line.getX2(),line.getY2()) || line.getP1().distance(line.getP2())<12)issues.add(issue("short_arrow","error",List.of(arcId(arc)),"The arrow has insufficient visible length."));
                for(var node:nodes) {
                    if(node!=arc.getBeginElement()&&node!=arc.getEndElement()&&new Rectangle2D.Double(node.getBounds().x,node.getBounds().y,node.getBounds().width,node.getBounds().height).intersectsLine(line))issues.add(issue("arrow_through_node","error",List.of(arcId(arc),id(node)),"An arrow passes through another element."));
                    for(var label:labels(node))if(label.intersectsLine(line)) {issues.add(issue("arrow_label_overlap","error",List.of(arcId(arc),id(node)),"An arrow overlaps a node label."));break;}
                }
                Rectangle2D quantity=quantityLabel(arc);
                if(quantity!=null)for(var node:nodes)if(quantity.intersects(footprint(node)))issues.add(issue("arc_quantity_overlap","error",List.of(arcId(arc),id(node)),"An arc weight label overlaps an element or its labels."));
            }
            for(int i=0;i<arcs.size();i++)for(int j=i+1;j<arcs.size();j++) {
                var a=arcs.get(i);var b=arcs.get(j);
                Rectangle2D qa=quantityLabel(a),qb=quantityLabel(b);
                if(qa!=null&&qb!=null&&qa.intersects(qb))issues.add(issue("arc_quantity_overlap","error",List.of(arcId(a),arcId(b)),"Arc weight labels overlap."));
                if(a.getBeginElement()==b.getBeginElement()||a.getBeginElement()==b.getEndElement()||a.getEndElement()==b.getBeginElement()||a.getEndElement()==b.getEndElement())continue;
                if(a.getGraphElement()!=null&&b.getGraphElement()!=null&&a.getGraphElement().intersectsLine(b.getGraphElement()))issues.add(issue("arrow_crossing","warning",List.of(arcId(a),arcId(b)),"Straight arrows cross; no unsupported bend geometry was introduced."));
            }
            for(var fusion:canvas.getFusions()) {
                var from=fusion.getMaster();var to=fusion.getJoined();
                Line2D line=fusionLine(fusion);String identifier="placeFusion:"+id(from)+":"+id(to);
                for(var node:nodes) {
                    boolean hit=node!=from&&node!=to&&node.getBounds().intersectsLine(line);
                    for(var label:labels(node))if(label.intersectsLine(line)){hit=true;break;}
                    if(hit)issues.add(issue("connection_element_overlap","error",List.of(identifier,id(node)),"A shared-place connection overlaps an element or its labels."));
                }
                for(var arc:arcs) {
                    if(arc.getBeginElement()==from||arc.getEndElement()==from||arc.getBeginElement()==to||arc.getEndElement()==to)continue;
                    if(arc.getGraphElement()!=null&&line.intersectsLine(arc.getGraphElement()))issues.add(issue("connection_crossing","warning",List.of(identifier,arcId(arc)),"A shared-place connection crosses a straight arrow."));
                }
            }
            issues.merge(inspectGroupTitles());
            return issues;
        }
        Rectangle groupBand(GraphObjectGroup group) {
            Rectangle band=null;
            for(var member:group.getMembers())if(canvas.getFrames().contains(member))band=band==null?new Rectangle(member.getExpandedBounds()):band.union(member.getExpandedBounds());
            if(band!=null)band.grow(16,16);
            return band;
        }
        Rectangle2D groupTitle(GraphObjectGroup group) {
            Rectangle band=groupBand(group);
            return band==null?null:textRect(group.getName()+" 1..."+group.size(),band.x+6,band.y-4,groupMetrics);
        }
        boolean containsGroup(GraphObjectFrame ancestor,GraphObjectGroup group) {
            return !group.getMembers().isEmpty()&&group.getMembers().stream().allMatch(member -> canvas.subtreeOf(ancestor).contains(member));
        }
        Diagnostics inspectGroupTitles() {
            var issues=new Diagnostics();
            for(var group:canvas.getGroups()) {
                Rectangle2D title=groupTitle(group);if(title==null)continue;
                for(var node:nodes)if(title.intersects(footprint(node)))issues.add(issue("group_title_overlap","error",List.of(group.getModelId(),id(node)),"A replication-group title overlaps an element or its labels."));
                for(var frame:canvas.getFrames()) {
                    Rectangle bounds=frame.getExpandedBounds();
                    Rectangle header=new Rectangle(bounds.x,bounds.y,bounds.width,GraphObjectFrame.HEADER_HEIGHT);
                    if(title.intersects(header)||(!containsGroup(frame,group)&&title.intersects(bounds)))issues.add(issue("group_title_overlap","error",List.of(group.getModelId(),frame.getModelId()),"A replication-group title overlaps an object header or an unrelated object."));
                }
                for(var arc:arcs)if(arc.getGraphElement()!=null&&title.intersectsLine(arc.getGraphElement()))issues.add(issue("group_title_arrow_overlap","error",List.of(group.getModelId(),arcId(arc)),"A straight arrow overlaps a replication-group title."));
            }
            for(int i=0;i<canvas.getGroups().size();i++)for(int j=i+1;j<canvas.getGroups().size();j++) {
                var a=canvas.getGroups().get(i);var b=canvas.getGroups().get(j);var first=groupTitle(a);var second=groupTitle(b);
                if(first!=null&&second!=null&&first.intersects(second))issues.add(issue("group_title_overlap","error",List.of(a.getModelId(),b.getModelId()),"Replication-group titles overlap."));
            }
            return issues;
        }
        Line2D fusionLine(GraphPlaceFusion fusion) {
            var line=new GraphArc();line.settingNewArc(fusion.getMaster());line.setEndElement(fusion.getJoined());line.changeBorder();
            return line.getGraphElement();
        }
        Diagnostics inspectHiddenPorts() {
            var issues=new Diagnostics();
            record PortLabel(String object,String element,Rectangle2D bounds) { }
            var allPorts=new ArrayList<PortLabel>();
            for(var frame:canvas.getFrames()) {
                Rectangle b=frame.getBounds();int index=canvas.getFrames().indexOf(frame);
                String title="O"+index+"  "+frame.getName();String detail="priority "+frame.getPriority()+(frame.getTemplate()==null?"":"  ·  "+frame.getTemplate().getMethodName());
                if(titleMetrics.stringWidth(title)+detailMetrics.stringWidth(detail)+84>b.width)issues.add(issue("object_header_overlap","error",List.of(frame.getModelId()),"The object title and detail need more header width."));
                if(frame.isContentShown())continue;
                var ports=canvas.portsOf(frame);var boxes=new ArrayList<Rectangle2D>();
                for(var port:ports)boxes.add(portLabel(port));
                for(int i=0;i<boxes.size();i++)for(int j=i+1;j<boxes.size();j++)if(boxes.get(i).intersects(boxes.get(j)))issues.add(issue("port_label_overlap","error",List.of(frame.getModelId()+":"+id(ports.get(i).getElement()),frame.getModelId()+":"+id(ports.get(j).getElement())),"Collapsed or hidden port labels overlap."));
                if (visibleAtRoot(frame)) for(int i=0;i<ports.size();i++) allPorts.add(new PortLabel(frame.getModelId(),id(ports.get(i).getElement()),boxes.get(i)));
            }
            for(int i=0;i<allPorts.size();i++)for(int j=i+1;j<allPorts.size();j++) {
                var a=allPorts.get(i);var b=allPorts.get(j);
                if(!a.object().equals(b.object()) && a.bounds().intersects(b.bounds()))issues.add(issue("port_label_overlap","error",List.of(a.object()+":"+a.element(),b.object()+":"+b.element()),"Port labels of different objects overlap."));
            }
            for(var port:allPorts)for(var node:nodes) {
                var owner=canvas.ownerOf(node);
                if(owner!=null && (!owner.isContentShown() || !visibleAtRoot(owner)))continue;
                if(port.bounds().intersects(footprint(node)))issues.add(issue("port_element_overlap","error",List.of(port.object()+":"+port.element(),id(node)),"A port label overlaps another visible element or its labels."));
            }
            return issues;
        }
        boolean visibleAtRoot(GraphObjectFrame frame) {
            for(var parent=canvas.enclosingOf(frame);parent!=null;parent=canvas.enclosingOf(parent))if(!parent.isContentShown())return false;
            return true;
        }
        Rectangle2D objectFootprint(GraphObjectFrame frame) {
            Rectangle2D result=frame.getExpandedBounds();
            if(Boolean.TRUE.equals(originallyCollapsed.get(frame)) || !frame.isContentVisible()) {
                boolean collapse=Boolean.TRUE.equals(originallyCollapsed.get(frame)) && !frame.isCollapsed();
                if(collapse)frame.setCollapsed(true);
                for(var port:canvas.portsOf(frame))result=union(result,portLabel(port));
                if(collapse)frame.setCollapsed(false);
            }
            return result;
        }
        Rectangle2D quantityLabel(GraphArc arc) {
            String value=null;
            if(arc instanceof GraphArcIn in && (in.getQuantity()!=1 || in.getArcIn().kIsParam()))value=in.getArcIn().kIsParam()?in.getArcIn().getKParamName():Integer.toString(in.getQuantity());
            if(arc instanceof GraphArcOut out && (out.getQuantity()!=1 || out.getArcOut().kIsParam()))value=out.getArcOut().kIsParam()?out.getArcOut().getKParamName():Integer.toString(out.getQuantity());
            if(value==null)return null;
            var line=arc.getGraphElement();double x=(line.getX1()+line.getX2())/2,y=(line.getY1()+line.getY2())/2-7;
            if(arc.isFirstArc()){x-=10;y-=5;}else if(arc.isSecondArc()){x+=10;y+=5;}
            return textRect(value,x,y,metrics);
        }
        Rectangle2D portLabel(FramePort port) {
            int width=portMetrics.stringWidth(port.getLabel()),x=port.getPosition().x,y=port.getPosition().y;
            x=switch(port.getEdge()){case LEFT -> x-FramePort.RADIUS-4-width;case RIGHT -> x+FramePort.RADIUS+4;case TOP,BOTTOM -> x-width/2;};
            y=switch(port.getEdge()){case TOP -> y-FramePort.RADIUS-4;case BOTTOM -> y+FramePort.RADIUS+12;case LEFT,RIGHT -> y+4;};
            return new Rectangle2D.Double(x-2,y-10,width+4,13);
        }
        final class Item {
            final GraphElement node;final GraphObjectFrame frame;final Point2D anchor;
            Item(GraphElement node,GraphObjectFrame frame){this.node=node;this.frame=frame;this.anchor=node!=null?node.getGraphElementCenter():new Point2D.Double(frame.getExpandedBounds().getCenterX(),frame.getExpandedBounds().getCenterY());}
            String id(){return node!=null?LayoutOptimizer.id(node):frame.getModelId();}
            Rectangle2D bounds(){return node!=null?footprint(node):objectFootprint(frame);}
            void move(int dx,int dy){if(node!=null)Engine.this.move(node,dx,dy);else Engine.this.move(frame,dx,dy);}
        }
    }
    private static Rectangle2D textRect(String text,double x,double baseline,FontMetrics metrics) { return new Rectangle2D.Double(x-2,baseline-metrics.getAscent()-2,Math.max(1,metrics.stringWidth(Objects.requireNonNullElse(text,"")))+4,metrics.getHeight()+4); }
    private static Rectangle2D union(Rectangle2D one,Rectangle2D two){if(one==null)return (Rectangle2D)two.clone();return one.createUnion(two);}
    private static Rectangle2D grown(Rectangle2D rectangle,int gap){return new Rectangle2D.Double(rectangle.getX()-gap,rectangle.getY()-gap,rectangle.getWidth()+2.0*gap,rectangle.getHeight()+2.0*gap);}
    private static boolean finite(double... values){for(double value:values)if(!Double.isFinite(value))return false;return true;}
    private static String arcId(GraphArc arc){return arc instanceof GraphArcIn in?in.getArcIn().getId():((GraphArcOut)arc).getArcOut().getId();}
    private static Map<String,Object> issue(String kind,String severity,List<String> ids,String message){return Map.of("kind",kind,"severity",severity,"element_ids",ids,"message",message);}
    private static final class Diagnostics extends ArrayList<Map<String,Object>> {
        int total, errors;
        @Override public boolean add(Map<String,Object> value) {
            total++; if("error".equals(value.get("severity")))errors++;
            return size()<MAX_DIAGNOSTICS && super.add(value);
        }
        void merge(Diagnostics other) {
            total+=other.total; errors+=other.errors;
            for(var value:other) { if(size()>=MAX_DIAGNOSTICS)break;super.add(value); }
        }
    }
}

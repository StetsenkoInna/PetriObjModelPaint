package ua.stetsenkoinna.graphpresentation.layout;

import java.awt.Font;
import java.awt.Rectangle;
import java.awt.geom.Point2D;
import java.util.*;
import org.junit.Test;
import ua.stetsenkoinna.graphnet.*;
import ua.stetsenkoinna.petriobj.*;
import static org.junit.Assert.*;

public class LayoutOptimizerTest {
    private static final Font FONT = new Font(Font.SANS_SERIF,Font.PLAIN,12);
    private int ui;
    private GraphPetriPlace place(GraphCanvasModel c,String id,String name,int tokens,double x,double y) {
        var p=new PetriP(id,name,tokens);p.setNumber(c.getNet().getGraphPetriPlaceList().size());
        var graph=new GraphPetriPlace(p,ui++);graph.setNewCoordinates(new Point2D.Double(x,y));c.getNet().getGraphPetriPlaceList().add(graph);return graph;
    }
    private GraphPetriTransition transition(GraphCanvasModel c,String id,String name,double x,double y) {
        var t=new PetriT(id,name,2);t.setNumber(c.getNet().getGraphPetriTransitionList().size());
        var graph=new GraphPetriTransition(t,ui++);graph.setNewCoordinates(new Point2D.Double(x,y));c.getNet().getGraphPetriTransitionList().add(graph);return graph;
    }
    private void in(GraphCanvasModel c,GraphPetriPlace p,GraphPetriTransition t) { c.getNet().getGraphArcInList().add(GraphArcFactory.inArc(p,t,1,false)); }
    private void out(GraphCanvasModel c,GraphPetriTransition t,GraphPetriPlace p) { c.getNet().getGraphArcOutList().add(GraphArcFactory.outArc(t,p,1)); }
    private GraphCanvasModel net() { return new GraphCanvasModel("Spacing test",new GraphPetriNet()); }
    private Map<String,Point2D> positions(GraphCanvasModel c) {
        var result=new LinkedHashMap<String,Point2D>();
        for(var p:c.getNet().getGraphPetriPlaceList())result.put(p.getPetriPlace().getId(),p.getGraphElementCenter());
        for(var t:c.getNet().getGraphPetriTransitionList())result.put(t.getPetriTransition().getId(),t.getGraphElementCenter());
        return result;
    }
    @Test public void separatesLongLabelsWhilePreservingHorizontalFlowAndSemantics() {
        var c=net();var p=place(c,"input","Черга заявок з довгою назвою",3,-20,50);var t=transition(c,"service","Детермінована обробка",0,50);var q=place(c,"done","Кількість завершених заявок",0,20,50);in(c,p,t);out(c,t,q);
        String semantics=LayoutOptimizer.semanticFingerprint(c);var before=positions(c);
        var result=LayoutOptimizer.optimize(c,FONT);
        assertEquals(semantics,LayoutOptimizer.semanticFingerprint(result.canvas()));
        assertEquals(before,positions(c));
        var after=positions(result.canvas());
        assertTrue(after.get("input").getX()<after.get("service").getX());
        assertTrue(after.get("service").getX()<after.get("done").getX());
        assertEquals(after.get("input").getY(),after.get("service").getY(),0);
        assertEquals(Boolean.TRUE,result.report().get("verified"));
        assertEquals(0,((Number)result.report().get("after_issue_count")).intValue());
    }
    @Test public void alreadyReadableDrawingIsUnchangedAndRepeatedOptimizationIsStable() {
        var c=net();var p=place(c,"p","Input",1,-400,-160);var t=transition(c,"t","Service",0,-160);var q=place(c,"q","Done",0,400,-160);in(c,p,t);out(c,t,q);
        var first=LayoutOptimizer.optimize(c,FONT);var second=LayoutOptimizer.optimize(first.canvas(),FONT);
        assertEquals(positions(c),positions(first.canvas()));assertEquals(positions(first.canvas()),positions(second.canvas()));
        assertEquals(0,((Number)second.report().get("moved_nodes")).intValue());
        assertEquals(Boolean.TRUE,first.report().get("verified"));
    }
    @Test public void expandsNestedFramesWithoutLosingOwnershipOrNegativeCoordinates() {
        var c=net();var parent=new GraphObjectFrame("Складна зовнішня підсистема",new Rectangle(-500,-400,180,140));var child=new GraphObjectFrame("Внутрішній ресурс",new Rectangle(-450,-350,130,100));
        c.getFrames().add(parent);c.getFrames().add(child);c.nest(child,parent);
        var p=place(c,"p","Довга назва спільного буфера",2,-420,-310);var t=transition(c,"t","Обслуговування",-405,-310);var q=place(c,"q","Готові заявки",0,-390,-310);c.claim(child,p);c.claim(child,t);c.claim(child,q);in(c,p,t);out(c,t,q);
        var external=place(c,"external","Інша частина батьківської мережі",0,-410,-300);c.claim(parent,external);
        String semantics=LayoutOptimizer.semanticFingerprint(c);
        var result=LayoutOptimizer.optimize(c,FONT);
        assertEquals(semantics,LayoutOptimizer.semanticFingerprint(result.canvas()));
        var copyParent=result.canvas().getFrames().get(0);var copyChild=result.canvas().getFrames().get(1);
        assertSame(copyParent,result.canvas().enclosingOf(copyChild));assertTrue(copyParent.getBounds().contains(copyChild.getBounds()));
        assertTrue(copyParent.getBounds().x<0);assertTrue(copyParent.getBounds().y<0);
        var repeated=LayoutOptimizer.optimize(result.canvas(),FONT);
        assertEquals(positions(result.canvas()),positions(repeated.canvas()));
        assertEquals(copyParent.getBounds(),repeated.canvas().getFrames().get(0).getBounds());
    }
    @Test public void unavoidableStraightArrowCrossingsAreReportedInsteadOfClaimingSuccess() {
        var c=net();var p=place(c,"p","P",1,-300,-200);var q=place(c,"q","Q",1,-300,200);var t=transition(c,"t","T",300,200);var u=transition(c,"u","U",300,-200);in(c,p,t);in(c,q,u);
        var result=LayoutOptimizer.optimize(c,FONT);
        assertEquals(Boolean.FALSE,result.report().get("verified"));
        var diagnostics=(List<?>)result.report().get("diagnostics");
        assertTrue(diagnostics.stream().map(item -> (Map<?,?>)item).anyMatch(item -> item.get("kind").equals("arrow_crossing")));
        assertEquals(LayoutOptimizer.semanticFingerprint(c),LayoutOptimizer.semanticFingerprint(result.canvas()));
    }
    @Test public void preservesSharedResourcesGroupsAndCollapsedStateAndReportsHiddenTitleClipping() {
        var c=net();var a=new GraphObjectFrame("A very long resource pool object title which cannot fit a collapsed header",new Rectangle(-600,0,260,220));var b=new GraphObjectFrame("Worker",new Rectangle(100,0,260,220));c.getFrames().add(a);c.getFrames().add(b);
        var master=place(c,"master","Resource",1,-480,100);var alias=place(c,"alias","Resource",1,220,100);c.claim(a,master);c.claim(b,alias);c.joinPlaces(master,alias);
        var group=new GraphObjectGroup("Workers",null);group.add(a);group.add(b);c.getGroups().add(group);a.setCollapsed(true);
        String semantics=LayoutOptimizer.semanticFingerprint(c);var result=LayoutOptimizer.optimize(c,FONT);
        assertEquals(semantics,LayoutOptimizer.semanticFingerprint(result.canvas()));assertTrue(result.canvas().getFrames().getFirst().isCollapsed());assertEquals(1,result.canvas().getFusions().size());assertEquals(2,result.canvas().getGroups().getFirst().size());
        assertEquals(Boolean.FALSE,result.report().get("verified"));
        assertTrue(((List<?>)result.report().get("diagnostics")).stream().map(item -> (Map<?,?>)item).anyMatch(item -> item.get("kind").equals("object_header_overlap")));
    }
    @Test public void groupTitlesAreIncludedInDiagnosticsInsteadOfBeingAssumedReadable() {
        var c=net();var a=new GraphObjectFrame("A",new Rectangle(0,100,200,180));var b=new GraphObjectFrame("B",new Rectangle(300,100,200,180));c.getFrames().add(a);c.getFrames().add(b);
        var group=new GraphObjectGroup("Назва групи реплікованих підсистем",null);group.add(a);group.add(b);c.getGroups().add(group);
        place(c,"obstacle","Obstacle",0,70,70);
        var report=LayoutOptimizer.inspect(c,FONT);
        assertEquals(Boolean.FALSE,report.get("verified"));
        assertTrue(((List<?>)report.get("diagnostics")).stream().map(item -> (Map<?,?>)item).anyMatch(item -> item.get("kind").equals("group_title_overlap")));
    }
    @Test public void clearsAnInformationalInputArrowFromItsOwnTransitionName() {
        var c=net();var permission=place(c,"permission","Дозвіл виконання підмережі",1,200,20);var work=transition(c,"work","Тривале обслуговування",200,260);
        var input=GraphArcFactory.inArc(permission,work,1,true);c.getNet().getGraphArcInList().add(input);
        var before=LayoutOptimizer.inspect(c,FONT);
        assertTrue(((List<?>)before.get("diagnostics")).stream().map(item -> (Map<?,?>)item).anyMatch(item -> item.get("kind").equals("arrow_label_overlap")));
        String semantics=LayoutOptimizer.semanticFingerprint(c);var result=LayoutOptimizer.optimize(c,FONT);
        assertEquals(semantics,LayoutOptimizer.semanticFingerprint(result.canvas()));
        assertTrue(result.canvas().getNet().getGraphArcInList().getFirst().getArcIn().getIsInf());
        assertEquals(0,((Number)result.report().get("unreadable_count")).intValue());
        assertEquals(Boolean.TRUE,result.report().get("readability_verified"));
    }
    @Test public void expandedObjectsDoNotInventUndrawnPortLabelCollisions() {
        var c=net();var frame=new GraphObjectFrame("Object",new Rectangle(0,0,400,400));c.getFrames().add(frame);
        c.claim(frame,place(c,"one","Довга назва першого порту",0,100,390));c.claim(frame,place(c,"two","Довга назва другого порту",0,140,390));
        assertTrue(frame.isContentShown());
        var report=LayoutOptimizer.inspect(c,FONT);
        assertFalse(((List<?>)report.get("diagnostics")).stream().map(item -> (Map<?,?>)item).anyMatch(item -> item.get("kind").toString().startsWith("port_")));
        frame.setContentVisible(false);
        var hidden=LayoutOptimizer.inspect(c,FONT);
        assertTrue(((List<?>)hidden.get("diagnostics")).stream().map(item -> (Map<?,?>)item).anyMatch(item -> item.get("kind").equals("port_label_overlap")));
    }
    @Test public void realModularPortFixtureKeepsLinksParametersAndExactModelSemantics() throws Exception {
        var fixture=java.nio.file.Path.of("../petri-model/src/test/resources/pnml/modular-port-connections.pnml");
        assertTrue("Missing modular-port regression fixture",java.nio.file.Files.isRegularFile(fixture));
        var parser=new ua.stetsenkoinna.pnml.PnmlModelParser();
        var original=GraphCanvasModel.fromObjModel(parser.parse(fixture.toFile()));
        assertTrue(parser.getWarnings().toString(),parser.getWarnings().isEmpty());
        int links=original.toObjModel().getLinks().size();
        String semantics=LayoutOptimizer.semanticFingerprint(original);
        var result=LayoutOptimizer.optimize(original,FONT);
        assertEquals(4,result.canvas().getFrames().size());
        assertEquals(5,links);assertEquals(links,result.canvas().toObjModel().getLinks().size());
        assertEquals(semantics,LayoutOptimizer.semanticFingerprint(result.canvas()));
        assertEquals(semantics,LayoutOptimizer.semanticFingerprint(original));
        var serialized=new ua.stetsenkoinna.pnml.PnmlModelGenerator().generateXml(result.canvas().toObjModel());
        var reopened=GraphCanvasModel.fromObjModel(new ua.stetsenkoinna.pnml.PnmlModelParser().parseXml(serialized));
        assertEquals(links,reopened.toObjModel().getLinks().size());
        assertEquals(result.canvas().getNet().getGraphPetriTransitionList().stream().map(t -> t.getPetriTransition().getParameter()).sorted().toList(),reopened.getNet().getGraphPetriTransitionList().stream().map(t -> t.getPetriTransition().getParameter()).sorted().toList());
        assertTrue(((List<?>)result.report().get("diagnostics")).size()<=1000);
        assertEquals(0,((Number)result.report().get("unreadable_count")).intValue());
        assertEquals(Boolean.TRUE,result.report().get("readability_verified"));
        assertEquals(Boolean.FALSE,result.report().get("verified"));
        var diagnostics=(List<?>)result.report().get("diagnostics");
        assertFalse(diagnostics.isEmpty());
        assertTrue(diagnostics.stream().map(item -> (Map<?,?>)item).allMatch(item -> item.get("kind").equals("arrow_crossing")&&item.get("severity").equals("warning")));
        var frames=result.canvas().getFrames();
        assertTrue(frames.get(0).getBounds().getCenterX()<frames.get(1).getBounds().getCenterX());
        assertTrue(frames.get(1).getBounds().getCenterX()<frames.get(2).getBounds().getCenterX());
        assertTrue(frames.get(3).getBounds().getCenterY()>frames.get(0).getBounds().getMaxY());
        var repeated=LayoutOptimizer.optimize(result.canvas(),FONT);
        assertEquals(positions(result.canvas()),positions(repeated.canvas()));
        for(int i=0;i<frames.size();i++)assertEquals(frames.get(i).getExpandedBounds(),repeated.canvas().getFrames().get(i).getExpandedBounds());
        assertEquals(0,((Number)repeated.report().get("moved_nodes")).intValue());
        System.out.println("Modular-port spacing report: "+result.report());
    }
}

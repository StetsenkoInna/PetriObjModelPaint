package pnml;

import org.junit.Test;
import ua.stetsenkoinna.graphnet.*;
import ua.stetsenkoinna.petriobj.*;

import java.awt.Rectangle;
import java.awt.geom.Point2D;
import java.util.List;

import static org.junit.Assert.*;

public class ModelSnapshotContractTest {
    private static GraphPetriPlace place(GraphCanvasModel canvas, String id, int marking, int index) {
        var p = new GraphPetriPlace(new PetriP(id, id, marking), index);
        p.setNewCoordinates(new Point2D.Double(index*100-250, -150));
        canvas.getNet().getGraphPetriPlaceList().add(p);
        return p;
    }

    @Test public void transactionSnapshotPreservesIdsParametersOrderAndIndependentGeometry() throws Exception {
        var canvas = new GraphCanvasModel();
        var p = place(canvas,"input",3,0); var done=place(canvas,"done",0,1);
        var t = new GraphPetriTransition(new PetriT("service","Service",2),2);
        t.setNewCoordinates(new Point2D.Double(-50,-150)); canvas.getNet().getGraphPetriTransitionList().add(t);
        var in=GraphArcFactory.inArc(p,t,2,false);var out=GraphArcFactory.outArc(t,done,3);
        canvas.getNet().getGraphArcInList().add(in); canvas.getNet().getGraphArcOutList().add(out);
        p.getPetriPlace().setMarkParam("jobs");
        t.getPetriTransition().setParameterParam("delay");t.getPetriTransition().setDistributionParam("distribution");
        t.getPetriTransition().setPriorityParam("priority");t.getPetriTransition().setProbabilityParam("probability");
        in.getArcIn().setKParam("inputWeight");in.getArcIn().setInfParam("testInput");out.getArcOut().setKParam("outputWeight");
        in.setBoundaryStubOffset(new Point2D.Double(-50,40));
        var copy=new GraphCanvasModel(canvas);
        assertEquals(List.of("input","done"),copy.getNet().getGraphPetriPlaceList().stream().map(n -> n.getPetriPlace().getId()).toList());
        assertEquals("service",copy.getNet().getGraphPetriTransitionList().get(0).getPetriTransition().getId());
        assertEquals(in.getArcIn().getId(),copy.getNet().getGraphArcInList().get(0).getArcIn().getId());
        assertEquals(out.getArcOut().getId(),copy.getNet().getGraphArcOutList().get(0).getArcOut().getId());
        assertEquals("jobs",copy.getNet().getGraphPetriPlaceList().get(0).getPetriPlace().getMarkParamName());
        var copiedT=copy.getNet().getGraphPetriTransitionList().get(0).getPetriTransition();
        assertEquals("delay",copiedT.getParameterParamName());assertEquals("distribution",copiedT.getDistributionParamName());
        assertEquals("priority",copiedT.getPriorityParamName());assertEquals("probability",copiedT.getProbabilityParamName());
        assertEquals("inputWeight",copy.getNet().getGraphArcInList().get(0).getArcIn().getKParamName());
        assertEquals("testInput",copy.getNet().getGraphArcInList().get(0).getArcIn().getInfParamName());
        assertEquals("outputWeight",copy.getNet().getGraphArcOutList().get(0).getArcOut().getKParamName());
        assertNotSame(p,copy.getNet().getGraphPetriPlaceList().get(0));
        assertNotSame(in.getBoundaryStubOffset(),copy.getNet().getGraphArcInList().get(0).getBoundaryStubOffset());
        copy.getNet().getGraphPetriPlaceList().get(0).moveBy(500,500);
        assertEquals(new Point2D.Double(-250,-150),p.getGraphElementCenter());
        var duplicate=canvas.getNet().bulkCopyNoPasteElements(List.of(p,t,done));
        var duplicateP=(GraphPetriPlace)duplicate.oldToNew.get(p);
        assertNotEquals(p.getPetriPlace().getId(),duplicateP.getPetriPlace().getId());
        assertEquals("jobs",duplicateP.getPetriPlace().getMarkParamName());
        assertEquals("delay",((GraphPetriTransition)duplicate.oldToNew.get(t)).getPetriTransition().getParameterParamName());
        assertEquals("inputWeight",duplicate.inArcs.get(0).getArcIn().getKParamName());
    }

    @Test public void reverseOrderedFusionChainsUseOneCanonicalMarkingAndParameter() {
        var canvas=new GraphCanvasModel();
        var a=place(canvas,"a",7,0);var b=place(canvas,"b",0,1);var c=place(canvas,"c",0,2);var d=place(canvas,"d",0,3);var e=place(canvas,"e",0,4);
        canvas.joinPlaces(d,e);canvas.joinPlaces(c,d);canvas.joinPlaces(b,c);canvas.joinPlaces(a,b);canvas.syncFusions();
        for(var p:canvas.getNet().getGraphPetriPlaceList())assertEquals(7,p.getPetriPlace().getMark());
        a.getPetriPlace().setMarkParam("capacity");canvas.syncFusions();
        for(var p:canvas.getNet().getGraphPetriPlaceList())assertEquals("capacity",p.getPetriPlace().getMarkParamName());
    }

    @Test public void sharedFreePlacesCannotSilentlyLoseTheirIdentityDuringExport() {
        var canvas=new GraphCanvasModel();var a=place(canvas,"a",1,0);var b=place(canvas,"b",0,1);canvas.joinPlaces(a,b);
        var error=assertThrows(IllegalArgumentException.class,canvas::toObjModel);
        assertTrue(error.getMessage().contains("different Petri-objects"));
    }

    @Test public void unresolvedCrossObjectQuantityIsNotSilentlyConcretized() {
        var canvas=new GraphCanvasModel();var source=place(canvas,"source",1,0);var target=place(canvas,"target",0,1);
        var t=new GraphPetriTransition(new PetriT("t","Transfer",1),2);canvas.getNet().getGraphPetriTransitionList().add(t);
        var first=new GraphObjectFrame("First",new Rectangle(-400,-300,300,300));var second=new GraphObjectFrame("Second",new Rectangle(100,-300,300,300));
        canvas.getFrames().addAll(List.of(first,second));canvas.claim(first,source);canvas.claim(first,t);canvas.claim(second,target);
        canvas.getNet().getGraphArcInList().add(GraphArcFactory.inArc(source,t,1,false));
        var external=GraphArcFactory.outArc(t,target,1);external.getArcOut().setKParam("batch");canvas.getNet().getGraphArcOutList().add(external);
        var error=assertThrows(IllegalArgumentException.class,canvas::toObjModel);
        assertTrue(error.getMessage().contains("batch"));
    }
}

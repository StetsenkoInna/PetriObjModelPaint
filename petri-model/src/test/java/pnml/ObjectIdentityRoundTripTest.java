package pnml;

import org.junit.Test;
import ua.stetsenkoinna.graphnet.*;
import ua.stetsenkoinna.petriobj.PetriObjLinkType;
import ua.stetsenkoinna.pnml.PnmlModelGenerator;
import ua.stetsenkoinna.pnml.PnmlModelParser;

import java.awt.Rectangle;
import java.awt.geom.Point2D;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.*;

public class ObjectIdentityRoundTripTest {
    private static String fixture() throws Exception {
        try (var input = ObjectIdentityRoundTripTest.class.getResourceAsStream("/pnml/modular-port-connections.pnml")) {
            assertNotNull(input);
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static GraphCanvasModel canvas() throws Exception {
        var parser = new PnmlModelParser();
        var canvas = GraphCanvasModel.fromObjModel(parser.parseXml(fixture()));
        assertTrue(parser.getWarnings().toString(), parser.getWarnings().isEmpty());
        assertTrue(canvas.getLoadWarnings().toString(), canvas.getLoadWarnings().isEmpty());
        return canvas;
    }

    private static int mark(ua.stetsenkoinna.petriobj.PetriObjModel model, String id) {
        for (var object : model.getListObj()) for (var place : object.getNet().getListP()) {
            if (id.equals(place.getId())) return place.getMark();
        }
        throw new AssertionError("Missing place " + id);
    }

    @Test public void modularPortFixtureKeepsLinksReadArcsAndExecutionAfterSchemaValidRoundTrip() throws Exception {
        var original = canvas();
        assertEquals(4, original.getFrames().size());
        var model = original.toObjModel();
        assertEquals(5, model.getLinks().size());
        assertTrue(model.getLinks().stream().allMatch(link -> link.getType() == PetriObjLinkType.TRANSITION_TO_PLACE));
        assertEquals(6, original.getNet().getGraphArcInList().stream().filter(a -> a.getArcIn().getIsInf()).count());
        for (var frame : original.getFrames()) {
            assertEquals(original.membersOfSubtree(frame).size(), original.portsOf(frame).size());
        }
        String xml = new PnmlModelGenerator().generateXml(model);
        PnmlConformanceAssertions.assertSchemaValid(xml);
        PnmlConformanceAssertions.assertConformant(PnmlConformanceAssertions.parse(xml));
        var parser = new PnmlModelParser();
        var restored = GraphCanvasModel.fromObjModel(parser.parseXml(xml));
        assertTrue(parser.getWarnings().toString(), parser.getWarnings().isEmpty());
        assertTrue(restored.getLoadWarnings().toString(), restored.getLoadWarnings().isEmpty());
        assertEquals(5, restored.toObjModel().getLinks().size());
        assertEquals(6, restored.getNet().getGraphArcInList().stream().filter(a -> a.getArcIn().getIsInf()).count());
        for (var net : List.of(original.toObjModel(), restored.toObjModel())) {
            var simulation = net.createPetriObjModel("Port regression");
            simulation.setIsProtokol(false);
            simulation.go(100);
            assertEquals(4, mark(simulation, "p-17cd38d8"));
            assertEquals(1, mark(simulation, "p-1-af58633a"));
            for (String permission : List.of("p-23780345", "p-2-c16b011f", "p-3-a505cf0e", "p-4-08693823")) {
                assertEquals("Read arcs do not consume permission tokens", 1, mark(simulation, permission));
            }
        }
    }

    @Test public void objectAndGroupIdentityVisibilityNestingTemplatesAndOffsetsSurviveSaveAndCopy() throws Exception {
        var original = canvas();
        var first = original.getFrames().get(0);
        var second = original.getFrames().get(1);
        first.setModelId("preparation"); second.setModelId("routing");
        first.moveBy(-700, -500);
        for (var member : first.getMembers()) member.moveBy(-700, -500);
        original.nest(second, first);
        first.setCollapsed(true); second.setContentVisible(false);
        first.setTemplate(new NetTemplateRef("CreateNetGenerator", List.of("1", "exp")));
        var group = new GraphObjectGroup("Processing group", new NetTemplateRef("CreateNetSMOwithoutQueue", List.of("1", "2", "Stage")));
        group.setModelId("processing_group"); group.add(original.getFrames().get(2)); group.add(original.getFrames().get(3)); original.getGroups().add(group);
        var master = original.getNet().getGraphPetriPlaceList().stream().filter(p -> p.getPetriPlace().getId().equals("p-23780345")).findFirst().orElseThrow();
        var alias = original.getNet().getGraphPetriPlaceList().stream().filter(p -> p.getPetriPlace().getId().equals("p-2-c16b011f")).findFirst().orElseThrow();
        var fusion = original.joinPlaces(master, alias); fusion.setBoundaryStubOffset(new Point2D.Double(-24, 61));
        var external = original.getNet().getGraphArcOutList().stream().filter(a -> original.ownerOf(a.getBeginElement()) != original.ownerOf(a.getEndElement())).findFirst().orElseThrow();
        external.setBoundaryStubOffset(new Point2D.Double(85, -37));
        var copy = new GraphCanvasModel(original);
        assertEquals("preparation", copy.getFrames().get(0).getModelId());
        assertEquals("processing_group", copy.getGroups().get(0).getModelId());
        assertEquals(fusion.getBoundaryStubOffset(), copy.getFusions().get(0).getBoundaryStubOffset());
        assertNotSame(fusion.getBoundaryStubOffset(), copy.getFusions().get(0).getBoundaryStubOffset());
        assertEquals(external.getBoundaryStubOffset(), copy.getNet().getGraphArcOutList().stream().filter(a -> copy.ownerOf(a.getBeginElement()) != copy.ownerOf(a.getEndElement())).findFirst().orElseThrow().getBoundaryStubOffset());
        String xml = new PnmlModelGenerator().generateXml(original.toObjModel());
        PnmlConformanceAssertions.assertSchemaValid(xml);
        PnmlConformanceAssertions.assertConformant(PnmlConformanceAssertions.parse(xml));
        var parser = new PnmlModelParser();
        var restored = GraphCanvasModel.fromObjModel(parser.parseXml(xml));
        assertTrue(parser.getWarnings().toString(), parser.getWarnings().isEmpty());
        var restoredFirst = restored.getFrames().stream().filter(f -> f.getModelId().equals("preparation")).findFirst().orElseThrow();
        var restoredSecond = restored.getFrames().stream().filter(f -> f.getModelId().equals("routing")).findFirst().orElseThrow();
        assertTrue(restoredFirst.isCollapsed()); assertFalse(restoredSecond.isContentVisible());
        assertSame(restoredFirst, restored.enclosingOf(restoredSecond));
        assertEquals(first.getExpandedBounds(), restoredFirst.getExpandedBounds());
        assertTrue(restoredFirst.getExpandedBounds().x < 0 && restoredFirst.getExpandedBounds().y < 0);
        assertEquals(first.getTemplate(), restoredFirst.getTemplate());
        assertEquals(group.getModelId(), restored.getGroups().get(0).getModelId());
        assertEquals(group.getTemplate(), restored.getGroups().get(0).getTemplate());
        assertEquals(fusion.getBoundaryStubOffset(), restored.getFusions().get(0).getBoundaryStubOffset());
        assertEquals(1, restored.getNet().getGraphArcOutList().stream().filter(a -> new Point2D.Double(85,-37).equals(a.getBoundaryStubOffset())).count());
    }

    @Test public void absorbingCopiesCreatesNewObjectAndGroupIdsButSnapshotCopyPreservesThem() throws Exception {
        var source = canvas();
        var group = new GraphObjectGroup("Group", null); group.add(source.getFrames().get(0)); group.add(source.getFrames().get(1)); source.getGroups().add(group);
        var target = new GraphCanvasModel(source);
        Set<String> prior = new HashSet<>(); target.getFrames().forEach(f -> prior.add(f.getModelId())); prior.add(target.getGroups().get(0).getModelId());
        var mapping = target.getNet().mergeGraphNet(source.getNet());
        target.absorbStructureOf(source, mapping);
        assertEquals(8, target.getFrames().size()); assertEquals(2, target.getGroups().size());
        Set<String> all = new HashSet<>();
        for (var frame : target.getFrames()) assertTrue(all.add(frame.getModelId()));
        for (var band : target.getGroups()) assertTrue(all.add(band.getModelId()));
        for (int i=0; i<4; i++) assertTrue(prior.contains(target.getFrames().get(i).getModelId()));
        for (int i=4; i<8; i++) assertFalse(prior.contains(target.getFrames().get(i).getModelId()));
        assertFalse(prior.contains(target.getGroups().get(1).getModelId()));
    }

    @Test public void malformedAndDuplicateObjectMetadataIdsAreRejectedAndFrameMovementAllowsNegativeCoordinates() throws Exception {
        var original = canvas();
        original.getFrames().get(0).setModelId("stable"); original.getFrames().get(1).setModelId("other");
        String xml = new PnmlModelGenerator().generateXml(original.toObjModel());
        assertThrows(IllegalArgumentException.class, () -> new PnmlModelParser().parseXml(xml.replace("modelId=\"other\"", "modelId=\"stable\"")));
        assertThrows(IllegalArgumentException.class, () -> original.getFrames().get(0).setModelId("invalid id"));
        var frame = new GraphObjectFrame("Negative", new Rectangle(30,40,300,250));
        frame.setCollapsed(true); frame.moveTo(-400,-300); frame.setCollapsed(false);
        assertEquals(new Rectangle(-400,-300,300,250), frame.getBounds());
    }
}

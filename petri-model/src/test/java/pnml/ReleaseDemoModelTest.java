package pnml;

import org.junit.Test;
import ua.stetsenkoinna.graphnet.GraphNetBuilder;
import ua.stetsenkoinna.graphnet.GraphPetriNet;
import ua.stetsenkoinna.graphnet.GraphPetriObjModel;
import ua.stetsenkoinna.graphnet.GraphPetriObject;
import ua.stetsenkoinna.graphnet.PetriObjectGroupRef;
import ua.stetsenkoinna.petriobj.ArcIn;
import ua.stetsenkoinna.petriobj.ArcOut;
import ua.stetsenkoinna.petriobj.PetriNet;
import ua.stetsenkoinna.petriobj.PetriObjLink;
import ua.stetsenkoinna.petriobj.PetriP;
import ua.stetsenkoinna.petriobj.PetriT;
import ua.stetsenkoinna.pnml.PnmlModelGenerator;

import java.awt.Point;
import java.awt.geom.Point2D;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static pnml.PnmlConformanceAssertions.assertConformant;
import static pnml.PnmlConformanceAssertions.assertSchemaValid;
import static pnml.PnmlConformanceAssertions.parse;

/**
 * A realistic composed model that exercises everything this release added to Petri-object
 * models, checked against the standard's own grammar and written out for opening by hand.
 *
 * <p>Two things at once. It is a test — a model using every new construct together has to be a
 * conformant document, and asserting that on a toy example proves less than asserting it on one
 * shaped like real work. And the document it validates is left in {@code target/demo}, so the
 * features can be looked at in the editor without clicking a model together first.
 *
 * <p>What the model contains:
 * <ul>
 *   <li>a <b>group</b> of four Petri-objects stamped from one net — {@code multiply(net, lists, k)};
 *   <li>a place of the dispatcher <b>repeated by all four</b> of them, which is one-to-many;
 *   <li>a second place shared with each, so every dispatcher-server pair is a <b>connector</b> of
 *       two place identifications rather than two unrelated links;
 *   <li>tokens in the right places for the model to actually run once opened.
 * </ul>
 */
public class ReleaseDemoModelTest {

    private static final Path DEMO =
            Paths.get("target", "demo", "release-2.3.0-demo.pnml");

    /** How many servers the group holds. */
    private static final int SERVERS = 4;

    @Test
    public void theReleaseDemoModelIsAConformantDocument() throws Exception {
        GraphPetriObjModel model = demoModel();

        String xml = new PnmlModelGenerator().generateXml(model);

        assertSchemaValid(xml);
        assertConformant(parse(xml));

        assertTrue("the group is recorded", xml.contains("petriObjectGroups"));
        assertEquals("one shared place per link, two per server",
                SERVERS * 2, model.getLinks().size());

        Files.createDirectories(DEMO.getParent());
        Files.writeString(DEMO, xml, StandardCharsets.UTF_8);
    }

    /**
     * The group has to survive the file, not just the model.
     *
     * <p>The round trip already covered canvas to model and back; this covers the half that
     * actually reaches a user - written to XML and read again by the parser. They are different
     * code paths, and only one of them was being exercised.
     */
    @Test
    public void theGroupSurvivesBeingWrittenAndParsed() throws Exception {
        String xml = new PnmlModelGenerator().generateXml(demoModel());
        java.io.File file = DEMO.getParent().resolve("round-trip.pnml").toFile();
        Files.createDirectories(DEMO.getParent());
        Files.writeString(file.toPath(), xml, StandardCharsets.UTF_8);

        GraphPetriObjModel reparsed = new ua.stetsenkoinna.pnml.PnmlModelParser().parse(file);

        assertEquals("the group came back", 1, reparsed.getGroups().size());
        assertEquals("Server", reparsed.getGroups().getFirst().name());
        assertEquals("with all four members",
                List.of(1, 2, 3, 4), reparsed.getGroups().getFirst().memberObjects());
    }

    /**
     * And the whole way to the canvas the editor actually draws — the step after the parser.
     */
    @Test
    public void theGroupReachesTheCanvasAfterOpeningTheFile() throws Exception {
        String xml = new PnmlModelGenerator().generateXml(demoModel());
        java.io.File file = DEMO.getParent().resolve("to-canvas.pnml").toFile();
        Files.createDirectories(DEMO.getParent());
        Files.writeString(file.toPath(), xml, StandardCharsets.UTF_8);

        GraphPetriObjModel reparsed = new ua.stetsenkoinna.pnml.PnmlModelParser().parse(file);
        ua.stetsenkoinna.graphnet.GraphCanvasModel canvas =
                ua.stetsenkoinna.graphnet.GraphCanvasModel.fromObjModel(reparsed);

        assertEquals("the canvas has the group", 1, canvas.getGroups().size());
        assertEquals("with four members", 4, canvas.getGroups().getFirst().size());
    }

    /**
     * A dispatcher handing work to a group of four servers.
     *
     * <p>Object 0 is the dispatcher; objects 1..4 are the group. Each server repeats two of the
     * dispatcher's places: the one work arrives in, and the one acknowledgements go back
     * through. Two shared places between the same pair is what makes each of them a connector.
     */
    private static GraphPetriObjModel demoModel() throws Exception {
        GraphPetriObjModel model = new GraphPetriObjModel("Dispatcher and a group of servers");

        GraphPetriObject dispatcher =
                new GraphPetriObject("Dispatcher", dispatcherNet());
        dispatcher.setPosition(new Point(DISPATCHER_X, DISPATCHER_Y));
        dispatcher.setSize(560, 200);
        dispatcher.setPriority(2);
        model.addObject(dispatcher);

        for (int index = 1; index <= SERVERS; index++) {
            GraphPetriObject server = new GraphPetriObject("Server " + index, serverNet(index));
            // Laid out in a row under the dispatcher, which is where the group's band will be.
            server.setPosition(new Point(serverX(index), SERVER_Y));
            server.setSize(400, 220);
            model.addObject(server);
        }

        model.getGroups().add(new PetriObjectGroupRef(
                "Server", List.of(1, 2, 3, 4), null));

        for (int index = 1; index <= SERVERS; index++) {
            // Server.Task repeats Dispatcher.Sent - the same place, repeated by all four.
            model.addLink(PetriObjLink.placeFusion(index, 0, 0, 1));
            // Server.Ack repeats Dispatcher.Ack - the second strand of the same connector.
            model.addLink(PetriObjLink.placeFusion(index, 2, 0, 2));
        }
        return model;
    }

    private static final int DISPATCHER_X = 80;
    private static final int DISPATCHER_Y = 60;
    private static final int SERVER_Y = 360;

    /** Where the {@code index}-th server's frame starts, counting from one. */
    private static int serverX(int index) {
        return DISPATCHER_X + (index - 1) * 440;
    }

    /** Queue -> Dispatch -> Sent, plus the acknowledgement place the servers share. */
    private static GraphPetriNet dispatcherNet() throws Exception {
        PetriP queue = new PetriP("d_queue", "Queue", 12);
        PetriP sent = new PetriP("d_sent", "Sent", 0);
        PetriP ack = new PetriP("d_ack", "Ack", 0);
        PetriT dispatch = new PetriT("d_dispatch", "Dispatch", 0.5);
        int row = DISPATCHER_Y + 110;
        Map<Integer, Point2D.Double> places = Map.of(
                queue.getNumber(), at(DISPATCHER_X + 80, row),
                sent.getNumber(), at(DISPATCHER_X + 320, row),
                ack.getNumber(), at(DISPATCHER_X + 460, row));
        Map<Integer, Point2D.Double> transitions = Map.of(
                dispatch.getNumber(), at(DISPATCHER_X + 200, row));
        return net("Dispatcher", places, transitions,
                List.of(queue, sent, ack), List.of(dispatch),
                List.of(new ArcIn(queue, dispatch, 1)),
                List.of(new ArcOut(dispatch, sent, 1)));
    }

    /** Task -> Serve -> Ack, the net every member of the group is stamped from. */
    private static GraphPetriNet serverNet(int index) throws Exception {
        PetriP task = new PetriP("s" + index + "_task", "Task", 0);
        PetriP busy = new PetriP("s" + index + "_busy", "Busy", 1);
        PetriP ack = new PetriP("s" + index + "_ack", "Ack", 0);
        PetriT serve = new PetriT("s" + index + "_serve", "Serve", 3.0);
        int left = serverX(index);
        int row = SERVER_Y + 110;
        Map<Integer, Point2D.Double> places = Map.of(
                task.getNumber(), at(left + 70, row),
                // Under Task rather than under Serve: the transition's own delay and
                // probability labels are drawn below it, and a place there collides with them.
                busy.getNumber(), at(left + 70, row + 90),
                ack.getNumber(), at(left + 310, row));
        Map<Integer, Point2D.Double> transitions = Map.of(
                serve.getNumber(), at(left + 190, row));
        return net("Server " + index, places, transitions,
                List.of(task, busy, ack), List.of(serve),
                List.of(new ArcIn(task, serve, 1), new ArcIn(busy, serve, 1)),
                List.of(new ArcOut(serve, ack, 1), new ArcOut(serve, busy, 1)));
    }

    private static Point2D.Double at(int x, int y) {
        return new Point2D.Double(x, y);
    }

    /**
     * Builds one object's net at the coordinates given.
     *
     * <p>Absolute coordinates, and {@code normalize = false} so they are trusted as canvas
     * positions rather than shifted to a corner. Leaving them out is what made the first
     * attempt at this document useless: with no coordinates every object fell back to the same
     * generated layout, so on import the four servers landed on top of one another while their
     * frames sat elsewhere, empty. A document this application wrote is expected to carry the
     * user's own positions, and the reader trusts them - so a document written by hand has to
     * carry real ones too.
     */
    private static GraphPetriNet net(String name,
                                     Map<Integer, Point2D.Double> placeCoordinates,
                                     Map<Integer, Point2D.Double> transitionCoordinates,
                                     List<PetriP> places, List<PetriT> transitions,
                                     List<ArcIn> arcsIn, List<ArcOut> arcsOut) throws Exception {
        PetriNet built = new PetriNet(name,
                new ArrayList<>(places), new ArrayList<>(transitions),
                new ArrayList<>(arcsIn), new ArrayList<>(arcsOut));
        return GraphNetBuilder.build(built, placeCoordinates, transitionCoordinates, null, false);
    }
}

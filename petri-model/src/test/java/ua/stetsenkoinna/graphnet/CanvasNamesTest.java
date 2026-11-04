package ua.stetsenkoinna.graphnet;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.awt.Rectangle;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.Before;
import org.junit.Test;
import ua.stetsenkoinna.petriobj.PetriP;
import ua.stetsenkoinna.petriobj.PetriT;

public class CanvasNamesTest {

    private GraphPetriNet net;
    private GraphCanvasModel canvas;
    private int ids;

    @Before
    public void emptyCanvas() {
        net = new GraphPetriNet();
        canvas = new GraphCanvasModel("Names", net);
    }

    private GraphPetriPlace place(String name) {
        GraphPetriPlace place = new GraphPetriPlace(new PetriP(name, 0), ++ids);
        net.getGraphPetriPlaceList().add(place);
        return place;
    }

    private GraphPetriTransition transition(String name) {
        GraphPetriTransition transition = new GraphPetriTransition(new PetriT(name, 1.0), ++ids);
        net.getGraphPetriTransitionList().add(transition);
        return transition;
    }

    private GraphObjectFrame object(String name) {
        GraphObjectFrame frame = new GraphObjectFrame(name, new Rectangle(0, 0, 200, 120));
        canvas.getFrames().add(frame);
        return frame;
    }

    @Test
    public void aTakenNameContinuesItsCount() {
        assertEquals("Queue", CanvasNames.nextFree("Queue", Set.of()));
        assertEquals("Queue_2", CanvasNames.nextFree("Queue", Set.of("Queue")));
        assertEquals("Queue_3", CanvasNames.nextFree("Queue", Set.of("Queue", "Queue_2")));
        assertEquals("a copy of a copy continues the count instead of stacking suffixes",
                "Queue_3", CanvasNames.nextFree("Queue_2", Set.of("Queue", "Queue_2")));
        assertEquals("Server 2", CanvasNames.nextFreeObjectName("Server", Set.of("Server")));
        assertEquals("Server 3", CanvasNames.nextFreeObjectName("Server 2", Set.of("Server", "Server 2")));
    }

    @Test
    public void newcomersMoveAndWhatWasThereKeepsItsName() {
        GraphPetriPlace old = place("P1");
        GraphPetriTransition oldT = transition("T1");
        GraphPetriPlace copy = place("P1");
        GraphPetriTransition copyT = transition("T1");

        assertEquals(2, CanvasNames.settle(canvas, List.of(copy, copyT), List.of()));

        assertEquals("P1", old.getName());
        assertEquals("T1", oldT.getName());
        assertEquals("P1_2", copy.getName());
        assertEquals("T1_2", copyT.getName());
    }

    @Test
    public void twoCopiesOfOneNetGetDifferentNamesFromEachOther() {
        place("P1");
        GraphPetriPlace first = place("P1");
        GraphPetriPlace second = place("P1");

        CanvasNames.settle(canvas, List.of(first, second), List.of());

        assertEquals("P1_2", first.getName());
        assertEquals("P1_3", second.getName());
    }

    @Test
    public void placesTransitionsAndObjectsShareOneNamespace() {
        place("Queue");
        GraphPetriTransition transition = transition("Queue");
        GraphObjectFrame frame = object("Queue");

        CanvasNames.settle(canvas, List.of(transition), List.of(frame));

        assertEquals("Queue_2", transition.getName());
        assertEquals("objects keep the editor's spaced style", "Queue 2", frame.getName());
    }

    @Test
    public void fusedNewcomersShareOneNewName() {
        place("Resource");
        GraphPetriPlace master = place("Resource");
        GraphPetriPlace alias = place("Something else");
        canvas.joinPlaces(master, alias);

        CanvasNames.settle(canvas, List.of(master, alias), List.of());

        assertEquals("Resource_2", master.getName());
        assertEquals("one shared place, one name", "Resource_2", alias.getName());
    }

    @Test
    public void aNewAliasOfAnExistingSharedPlaceTakesItsNameAndNoOneElseDoes() {
        GraphPetriPlace master = place("Resource");
        GraphPetriPlace alias = place("Resource");
        canvas.joinPlaces(master, alias);
        GraphPetriPlace newAlias = place("Pool");
        canvas.joinPlaces(master, newAlias);
        GraphPetriPlace stranger = place("Resource");

        CanvasNames.settle(canvas, List.of(newAlias, stranger), List.of());

        assertEquals("the pair's name is allowed inside the pair", "Resource", newAlias.getName());
        assertEquals("but no one outside the pair may use it", "Resource_2", stranger.getName());
        assertEquals("Resource", master.getName());
        assertEquals("Resource", alias.getName());
    }

    @Test
    public void defaultNamesStartAtTheFirstFreeNumberOnTheCanvas() {
        assertEquals("P1", CanvasNames.nextDefault(canvas, "P"));
        place("P1");
        place("P2");
        place("P4");
        transition("T1");
        assertEquals("P3", CanvasNames.nextDefault(canvas, "P"));
        assertEquals("T2", CanvasNames.nextDefault(canvas, "T"));
    }

    @Test
    public void defaultNamesWorkOnACanvasWithNoNetYet() {
        assertEquals("P1", CanvasNames.nextDefault(new GraphCanvasModel(), "P"));
    }

    @Test
    public void renamingAnyHalfOfASharedPlaceRenamesTheWholeSet() {
        GraphPetriPlace master = place("Buffer");
        GraphPetriPlace alias = place("Buffer copy");
        GraphPetriPlace aliasOfAlias = place("Other");
        canvas.joinPlaces(master, alias);
        canvas.joinPlaces(alias, aliasOfAlias);
        GraphPetriPlace unrelated = place("Elsewhere");

        alias.getPetriPlace().setName("Store");
        Map<GraphPetriPlace, String> before = CanvasNames.shareName(canvas, alias);

        assertEquals("Store", master.getName());
        assertEquals("Store", aliasOfAlias.getName());
        assertEquals("Elsewhere", unrelated.getName());
        assertEquals(Map.of(master, "Buffer", aliasOfAlias, "Other"), before);
    }

    @Test
    public void arcsCarryTheNewNameOfARenamedEndpoint() {
        GraphPetriPlace input = place("In");
        GraphPetriTransition work = transition("Work");
        GraphPetriPlace output = place("Out");
        GraphArcIn in = GraphArcFactory.inArc(input, work, 1, false);
        GraphArcOut out = GraphArcFactory.outArc(work, output, 1);
        net.getGraphArcInList().add(in);
        net.getGraphArcOutList().add(out);
        place("In");
        place("Out");
        transition("Work");

        CanvasNames.settle(canvas, List.of(input, work, output), List.of());

        assertEquals(input.getName(), in.getArcIn().getNameP());
        assertEquals(work.getName(), in.getArcIn().getNameT());
        assertEquals(work.getName(), out.getArcOut().getNameT());
        assertEquals(output.getName(), out.getArcOut().getNameP());
        assertTrue(input.getName().startsWith("In_"));
    }
}

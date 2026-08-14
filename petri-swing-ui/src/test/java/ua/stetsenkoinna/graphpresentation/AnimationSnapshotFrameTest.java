package ua.stetsenkoinna.graphpresentation;

import org.junit.Test;

import ua.stetsenkoinna.graphnet.GraphCanvasModel;
import ua.stetsenkoinna.graphnet.GraphElement;
import ua.stetsenkoinna.graphnet.GraphObjectFrame;
import ua.stetsenkoinna.graphnet.GraphPetriNet;
import ua.stetsenkoinna.graphnet.GraphPetriObjModel;
import ua.stetsenkoinna.graphnet.GraphPetriPlace;
import ua.stetsenkoinna.graphnet.GraphPetriTransition;
import ua.stetsenkoinna.libnet.NetLibrary;

import java.awt.Point;
import java.awt.Rectangle;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/**
 * The bug: build a net with one Petri-object, press Start then Stop, and the object's frame
 * vanishes — the places and transitions come back, but as loose elements, because the pre-run
 * snapshot ({@code AnimationControls.saveCurrentNetState}) only ever captured a bare
 * {@link GraphPetriNet}, which has no concept of frames, and restoring went through {@code
 * PetriNetsPanel.deletePetriNet()}, which explicitly clears every frame.
 *
 * <p>These tests exercise the fixed primitive directly — a {@link GraphCanvasModel} deep copy,
 * restored via {@code PetriNetsPanel.setCanvasModel} — the same one {@code AnimationControls}
 * now uses for the pre-run snapshot, the step-back history, and Run Net's between-repetition
 * reset alike, since all three shared the one broken mechanism.
 */
public class AnimationSnapshotFrameTest {

    private static PetriNetsPanel panelWithFramedNet() throws Exception {
        PetriNetsPanel panel = new PetriNetsPanel(null, false);
        GraphPetriNet net = SimpleNetGraphBuilder.build(NetLibrary.CreateNetGenerator(2.0), new Point(300, 200));
        panel.setGraphNet(net);
        GraphObjectFrame frame = new GraphObjectFrame("Generator", new Rectangle(0, 0, 900, 600));
        for (GraphPetriPlace place : net.getGraphPetriPlaceList()) {
            panel.getCanvasModel().claim(frame, place);
        }
        for (GraphPetriTransition transition : net.getGraphPetriTransitionList()) {
            panel.getCanvasModel().claim(frame, transition);
        }
        panel.addObjectFrame(frame);
        return panel;
    }

    /**
     * Reproduces the exact bug report: snapshot before a run, the canvas gets wiped the way a
     * restore always starts by doing ({@code deletePetriNet()}, which clears frames outright),
     * then restore from the snapshot — the Petri-object must come back, not just its net.
     */
    @Test
    public void aFramedPetriObjectSurvivesASnapshotAndRestore() throws Exception {
        PetriNetsPanel panel = panelWithFramedNet();
        int elementCount = panel.getGraphNet().getGraphPetriPlaceList().size()
                + panel.getGraphNet().getGraphPetriTransitionList().size();

        GraphCanvasModel snapshot = new GraphCanvasModel(panel.getCanvasModel());

        // What every restore path does first, live or not — this is the step that used to make
        // the frame unrecoverable, since nothing had captured it to bring back.
        panel.deletePetriNet();
        assertTrue("sanity check: the frame is really gone before restoring",
                panel.getCanvasModel().getFrames().isEmpty());

        panel.setCanvasModel(snapshot);

        assertEquals("the Petri-object's frame must come back", 1, panel.getCanvasModel().getFrames().size());
        GraphObjectFrame restored = panel.getCanvasModel().getFrames().getFirst();
        assertEquals("Generator", restored.getName());
        assertEquals("every element the frame originally owned must still be inside it",
                elementCount, panel.countElementsIn(restored));

        // The read a running simulation actually relies on: one object, not the framed object
        // (now empty) plus its elements dumped into a second "Free elements" bucket.
        GraphPetriObjModel asModel = panel.getCanvasModel().toObjModel();
        assertEquals(1, asModel.getObjectCount());
        assertEquals(elementCount, asModel.getObject(0).getPlaceCount() + asModel.getObject(0).getTransitionCount());
    }

    /**
     * A snapshot has to be a genuine copy, not a second reference to the same frame and
     * elements — otherwise a run that mutates the live canvas after the snapshot was taken
     * (which is the entire point of running one) would silently corrupt the snapshot too, and
     * "restoring" would just hand back the already-mutated state.
     */
    @Test
    public void theSnapshotSharesNothingWithTheLiveCanvas() throws Exception {
        PetriNetsPanel panel = panelWithFramedNet();
        GraphObjectFrame liveFrame = panel.getCanvasModel().getFrames().getFirst();

        GraphCanvasModel snapshot = new GraphCanvasModel(panel.getCanvasModel());
        GraphObjectFrame snapshotFrame = snapshot.getFrames().getFirst();

        assertNotSame("the frame itself must be a new instance", liveFrame, snapshotFrame);
        for (GraphElement liveMember : liveFrame.getMembers()) {
            assertTrue("the snapshot's frame must not claim any of the live canvas's own elements",
                    snapshotFrame.getMembers().stream().noneMatch(copy -> copy == liveMember));
        }

        // Mutate the live canvas the way running the net for a while would (moving a marker,
        // moving the frame) — the snapshot must not see it.
        Rectangle originalSnapshotBounds = new Rectangle(snapshotFrame.getBounds());
        liveFrame.setBounds(new Rectangle(500, 500, 200, 200));
        assertEquals("moving the live frame must not move the snapshot's copy",
                originalSnapshotBounds, snapshotFrame.getBounds());
    }

    /**
     * Nesting survives the copy, pointing at the copy's own parent rather than the original's.
     * Pointing it at the original would tie the two canvases back together through exactly the
     * relation a deep copy exists to sever, so a run would drag the snapshot's hierarchy around
     * with it.
     */
    @Test
    public void aNestedPetriObjectSurvivesTheSnapshotPointingAtItsOwnParent() throws Exception {
        PetriNetsPanel panel = panelWithFramedNet();
        GraphObjectFrame parent = panel.getCanvasModel().getFrames().getFirst();
        GraphPetriPlace nested = panel.getGraphNet().getGraphPetriPlaceList().getFirst();
        GraphObjectFrame child = new GraphObjectFrame("Inner", new Rectangle(100, 100, 200, 160));
        panel.getCanvasModel().nest(child, parent);
        panel.addObjectFrame(child);
        panel.getCanvasModel().claim(child, nested);

        GraphCanvasModel snapshot = new GraphCanvasModel(panel.getCanvasModel());

        GraphObjectFrame copiedParent = snapshot.getFrames().get(0);
        GraphObjectFrame copiedChild = snapshot.getFrames().get(1);
        assertEquals("Inner", copiedChild.getName());
        assertSame("the copy's child names the copy's own parent",
                copiedParent, snapshot.enclosingOf(copiedChild));
        assertNotSame(parent, copiedParent);
        assertEquals(2, snapshot.levelOf(copiedChild));
        assertEquals("and it still claims the copy of what it claimed",
                1, snapshot.membersOfSubtree(copiedChild).size());
    }
}

package ua.stetsenkoinna.graphpresentation;

import org.junit.Test;
import ua.stetsenkoinna.graphnet.GraphArcIn;
import ua.stetsenkoinna.graphnet.GraphArcOut;
import ua.stetsenkoinna.graphnet.GraphPetriPlace;
import ua.stetsenkoinna.graphnet.GraphPetriTransition;
import ua.stetsenkoinna.graphpresentation.undoable_edits.DeleteArcEdit;
import ua.stetsenkoinna.petriobj.PetriP;
import ua.stetsenkoinna.petriobj.PetriT;

import javax.swing.undo.UndoManager;
import java.awt.geom.Point2D;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Two arcs running opposite ways between the same place and transition are drawn to either side
 * of the centre line, so both can be seen. Which arcs get that treatment is worked out by
 * {@code fixOverlappingArcs}, and this pins that it is re-worked-out whenever the set of arcs
 * changes rather than only when a net is first built.
 *
 * <p>It used to be set-only, never cleared, which broke the pair in both directions. Erase one
 * of the two and the survivor kept an offset it had only been given because its opposite number
 * existed, drawing a lone arc off centre. Put the erased one back — undo drops it straight into
 * the list — and nothing paired them up again, so both drew down the middle with one hidden
 * underneath the other. The second is what showed up in practice while using the eraser.
 */
public class BidirectionalArcPairingTest {

    private static int idCounter = 1;

    private PetriNetsPanel panel;
    private GraphArcIn arcIn;
    private GraphArcOut arcOut;

    /** A place and a transition with an arc running each way between them. */
    private void netWithATwoWayPair() {
        PetriP.initNext();
        PetriT.initNext();
        idCounter = 1;
        panel = new PetriNetsPanel(null, true);

        GraphPetriPlace place = new GraphPetriPlace(new PetriP("P1", 0), idCounter++);
        place.setNewCoordinates(new Point2D.Double(200, 300));
        panel.getGraphNet().getGraphPetriPlaceList().add(place);

        GraphPetriTransition transition =
                new GraphPetriTransition(new PetriT("T1", 1.0), idCounter++);
        transition.setNewCoordinates(new Point2D.Double(500, 300));
        panel.getGraphNet().getGraphPetriTransitionList().add(transition);

        arcIn = new GraphArcIn();
        arcIn.settingNewArc(place);
        arcIn.finishSettingNewArc(transition);
        arcIn.updateCoordinates();
        panel.getGraphNet().getGraphArcInList().add(arcIn);

        arcOut = new GraphArcOut();
        arcOut.settingNewArc(transition);
        arcOut.finishSettingNewArc(place);
        arcOut.updateCoordinates();
        panel.getGraphNet().getGraphArcOutList().add(arcOut);

        panel.getGraphNet().fixOverlappingArcs();
    }

    private boolean paired() {
        return arcOut.isFirstArc() && arcIn.isSecondArc();
    }

    @Test
    public void twoArcsRunningOppositeWaysAreDrawnApart() {
        netWithATwoWayPair();

        assertTrue("a two-way pair is offset so both arcs are visible", paired());
    }

    /** Stating the whole answer rather than adding to it: running it again changes nothing. */
    @Test
    public void workingOutThePairsTwiceIsTheSameAsWorkingThemOutOnce() {
        netWithATwoWayPair();

        panel.getGraphNet().fixOverlappingArcs();
        panel.getGraphNet().fixOverlappingArcs();

        assertTrue(paired());
    }

    /**
     * The survivor. Its offset only existed because the other arc did; once that one is erased
     * there is nothing to make room for, and a lone arc belongs on the centre line.
     */
    @Test
    public void erasingOneOfThePairBringsTheOtherBackToTheCentre() {
        netWithATwoWayPair();

        panel.removeArc(arcOut);

        assertFalse("the arc left behind is no longer half of a pair", arcIn.isSecondArc());
    }

    /**
     * The defect as it was reported: after erasing an arc and taking it back, the two drew on
     * top of each other. Undo puts the arc straight into the list, so unless the pairing is
     * re-derived nothing tells the two to move apart again.
     */
    @Test
    public void undoingAnErasedArcSeparatesThePairAgain() {
        netWithATwoWayPair();
        UndoManager undo = new UndoManager();
        PetriNetsFrame.getUndoSupport().addUndoableEditListener(undo);

        panel.removeArc(arcOut);
        PetriNetsFrame.getUndoSupport().postEdit(new DeleteArcEdit(panel, arcOut));
        assertFalse("gone, and the survivor centred", paired());

        undo.undo();

        assertTrue("restored, and drawn apart from its opposite number again", paired());
    }

    /**
     * Erasing the element at one end takes its arcs with it, which can leave the other half of
     * a pair standing alone just as surely as erasing the arc itself does.
     */
    @Test
    public void erasingAnElementAlsoReleasesWhateverItsArcsWerePairedWith() {
        netWithATwoWayPair();

        // Erase only the out-arc's own record of the place, the way removing an element does.
        panel.removeArc(arcOut);
        panel.getGraphNet().fixOverlappingArcs();

        assertFalse(arcIn.isFirstArc());
        assertFalse(arcIn.isSecondArc());
    }
}

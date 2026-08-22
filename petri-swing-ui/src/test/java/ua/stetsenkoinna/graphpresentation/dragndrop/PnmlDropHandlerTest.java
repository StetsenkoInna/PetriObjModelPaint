package ua.stetsenkoinna.graphpresentation.dragndrop;

import org.junit.Test;
import ua.stetsenkoinna.graphnet.GraphCanvasModel;
import ua.stetsenkoinna.graphnet.GraphObjectFrame;
import ua.stetsenkoinna.graphnet.GraphPetriNet;
import ua.stetsenkoinna.graphnet.GraphPetriPlace;
import ua.stetsenkoinna.graphpresentation.PetriNetsPanel;
import ua.stetsenkoinna.petriobj.PetriP;
import ua.stetsenkoinna.pnml.PnmlConstants;
import ua.stetsenkoinna.pnml.PnmlModelGenerator;

import java.awt.Rectangle;
import java.awt.geom.Point2D;
import java.io.File;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Dropping a PNML file that holds a whole Petri-object model, not one net.
 *
 * <p>{@link PnmlDropHandler#importPnmlFile} and {@link PnmlDropHandler#importComposedPnmlFile}
 * both end in a success dialog, which - like every other modal {@code JOptionPane} in this
 * codebase - cannot run in a test JVM. What is pinned here instead is the two dialog-free parts
 * that decide and perform the routing: recognising the failure {@link
 * ua.stetsenkoinna.pnml.PnmlParser} raises for a multi-page document, and {@link
 * PnmlDropHandler#mergeComposedDocument}, which does the actual read-and-merge with no dialog
 * of its own.
 */
public class PnmlDropHandlerTest {

    private static int idCounter = 9000;

    @Test
    public void recognisesTheMultiPageFailureByItsMessage() {
        Exception composed = new Exception(
                String.format(PnmlConstants.ERROR_OBJECT_MODEL_NOT_SUPPORTED, 3));
        assertTrue(PnmlDropHandler.isComposedDocumentFailure(composed));
    }

    @Test
    public void doesNotMistakeAnUnrelatedFailureForTheMultiPageCase() {
        assertFalse(PnmlDropHandler.isComposedDocumentFailure(
                new Exception("No net element found in PNML file")));
        assertFalse(PnmlDropHandler.isComposedDocumentFailure(new Exception()));
    }

    @Test
    public void mergesEveryObjectOfAComposedDocumentIntoWhatIsAlreadyOnTheCanvas() throws Exception {
        PetriP.initNext();

        PetriNetsPanel panel = new PetriNetsPanel(null, true);
        panel.setCanvasModel(oneFrameCanvas("Already there"));
        assertEquals(1, panel.getCanvasModel().getFrames().size());

        File file = twoObjectComposedDocument();
        PnmlDropHandler handler = new PnmlDropHandler(panel, null);

        PnmlDropHandler.ComposedImportResult result = handler.mergeComposedDocument(file);

        assertEquals(2, result.model().getObjects().size());
        // The frame that was already there survived the merge, and the two objects the file
        // carried arrived alongside it - addCanvasModel merges, it does not replace.
        assertEquals(3, panel.getCanvasModel().getFrames().size());
    }

    /** A canvas with one object: a single place inside a single frame. */
    private static GraphCanvasModel oneFrameCanvas(String frameName) {
        GraphCanvasModel canvas = new GraphCanvasModel();
        GraphPetriNet net = new GraphPetriNet();
        canvas.setNet(net);
        canvas.setName(frameName);

        GraphPetriPlace place = placeAt(net, 20, 20);
        GraphObjectFrame frame = new GraphObjectFrame(frameName, new Rectangle(0, 0, 80, 80));
        canvas.getFrames().add(frame);
        canvas.claim(frame, place);
        return canvas;
    }

    /** A PNML file for a two-object model: one place each, no links between them. */
    private static File twoObjectComposedDocument() throws Exception {
        GraphCanvasModel canvas = new GraphCanvasModel();
        GraphPetriNet net = new GraphPetriNet();
        canvas.setNet(net);
        canvas.setName("Two objects");

        GraphObjectFrame frameA = new GraphObjectFrame("Object A", new Rectangle(0, 0, 80, 80));
        canvas.getFrames().add(frameA);
        canvas.claim(frameA, placeAt(net, 10, 10));

        GraphObjectFrame frameB = new GraphObjectFrame("Object B", new Rectangle(150, 0, 80, 80));
        canvas.getFrames().add(frameB);
        canvas.claim(frameB, placeAt(net, 200, 10));

        File file = File.createTempFile("pnml-drop-handler-test", ".pnml");
        file.deleteOnExit();
        new PnmlModelGenerator().generate(canvas.toObjModel(), file);
        return file;
    }

    private static GraphPetriPlace placeAt(GraphPetriNet net, int x, int y) {
        GraphPetriPlace place = new GraphPetriPlace(new PetriP("P" + idCounter, 1), idCounter++);
        place.setNewCoordinates(new Point2D.Double(x, y));
        net.getGraphPetriPlaceList().add(place);
        return place;
    }
}

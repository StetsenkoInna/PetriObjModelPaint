package ua.stetsenkoinna.graphpresentation;

import ua.stetsenkoinna.graphnet.GraphCanvasModel;

/**
 * The single pre-run snapshot Stop and Step-back roll the canvas back to.
 *
 * <p>Holds a whole {@link GraphCanvasModel} — net, Petri-object frames and shared places
 * alike — rather than a bare net: a snapshot of the net alone has no notion of frames, so
 * restoring one used to bring every place and transition back as loose elements with whatever
 * Petri-object had held them gone.
 */
public class GraphPetriNetBackupHolder {
    private static GraphPetriNetBackupHolder instance;
    private GraphCanvasModel canvasModel = null;

    private GraphPetriNetBackupHolder() {}

    public static GraphPetriNetBackupHolder getInstance() {
        if (instance == null) {
            instance = new GraphPetriNetBackupHolder();
        }
        return instance;
    }

    public GraphCanvasModel get() {
        return canvasModel;
    }

    public void save(GraphCanvasModel canvasModel) {
        this.canvasModel = canvasModel;
    }

    public boolean isEmpty() {
        return canvasModel == null;
    }

    public void clear() {
        canvasModel = null;
    }

}

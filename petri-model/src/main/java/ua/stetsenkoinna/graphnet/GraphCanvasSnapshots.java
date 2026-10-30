package ua.stetsenkoinna.graphnet;

/** Exact detached copies of trusted in-memory canvases, preserving IDs and symbolic parameters. */
public final class GraphCanvasSnapshots {
    private GraphCanvasSnapshots() { }
    public static GraphCanvasModel copy(GraphCanvasModel source) {
        try {
            var bytes = new java.io.ByteArrayOutputStream();
            try (var output = new java.io.ObjectOutputStream(bytes)) { output.writeObject(source); }
            try (var input = new java.io.ObjectInputStream(new java.io.ByteArrayInputStream(bytes.toByteArray()))) { return (GraphCanvasModel) input.readObject(); }
        } catch (java.io.IOException | ClassNotFoundException failure) { throw new IllegalStateException("Cannot copy canvas state", failure); }
    }
}

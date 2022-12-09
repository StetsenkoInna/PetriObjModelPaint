package graphpresentation;

import graphnet.GraphPetriNet;

public class GraphPetriNetBackupHolder {
    private static GraphPetriNetBackupHolder instance;
    private GraphPetriNet graphPetriNet = null;

    private GraphPetriNetBackupHolder() {}

    public static GraphPetriNetBackupHolder getInstance() {
        if (instance == null) {
            instance = new GraphPetriNetBackupHolder();
        }

        return instance;
    }

    public GraphPetriNet get() {
        return graphPetriNet;
    }

    public void save(GraphPetriNet graphPetriNet) {
        this.graphPetriNet = graphPetriNet;
    }
    
    public boolean isEmpty() {
        return graphPetriNet == null;
    }
    
    public void clear() {
        graphPetriNet = null;
    }
    
}

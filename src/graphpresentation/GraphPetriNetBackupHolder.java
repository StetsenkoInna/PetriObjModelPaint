package graphpresentation;

import graphnet.GraphPetriNet;

public class GraphPetriNetBackupHolder {
    private static GraphPetriNetBackupHolder instance;
    private GraphPetriNet graphPetriNet;

    private GraphPetriNetBackupHolder() {}

    public static GraphPetriNetBackupHolder getInstance() {
        if (instance == null) {
            instance = new GraphPetriNetBackupHolder();
        }

        return instance;
    }

    public GraphPetriNet getGraphPetriNet() {
        return graphPetriNet;
    }

    public void setGraphPetriNet(GraphPetriNet graphPetriNet) {
        this.graphPetriNet = graphPetriNet;
    }
}

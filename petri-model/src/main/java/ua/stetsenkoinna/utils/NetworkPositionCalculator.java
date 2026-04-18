package ua.stetsenkoinna.utils;

import ua.stetsenkoinna.graphnet.GraphPetriNet;

import java.awt.Point;
import java.util.List;

/**
 * Utility class for calculating optimal position for imported or merged Petri nets
 * to avoid overlapping with existing networks on the workspace.
 *
 * @author Serhii Rybak
 */
public class NetworkPositionCalculator {

    private static final double DEFAULT_SPACING = 100.0;

    /**
     * Calculates the target position for a new network to be placed on the workspace.
     * If existing networks are present, places the new network to the right with spacing.
     * Otherwise, centers it around the drop location.
     *
     * @param existingNetworks List of existing networks on the workspace
     * @param newNetwork The network to be placed
     * @param dropLocation The location where the user dropped the file (can be null for automatic placement)
     * @return The calculated center point for the new network
     */
    public static Point calculateTargetPosition(List<GraphPetriNet> existingNetworks,
                                                GraphPetriNet newNetwork,
                                                Point dropLocation) {
        if (existingNetworks == null || existingNetworks.isEmpty()) {
            // No existing networks - use drop location or default position
            return dropLocation != null ? dropLocation : new Point(200, 200);
        }

        // Find the rightmost edge of all existing networks
        double maxRightEdge = 0.0;
        for (GraphPetriNet net : existingNetworks) {
            double rightEdge = net.getRightmostX();
            if (rightEdge > maxRightEdge) {
                maxRightEdge = rightEdge;
            }
        }

        // Calculate offset to position new network to the right of existing ones
        double newNetLeftEdge = newNetwork.getLeftmostX();
        double xOffset = maxRightEdge + DEFAULT_SPACING - newNetLeftEdge;

        // Get current center of the new network
        Point newNetworkCenter = newNetwork.getCurrentLocation();

        // Apply offset to center
        int targetX = (int) (newNetworkCenter.getX() + xOffset);

        // For Y coordinate, use drop location if available, otherwise use network's current Y
        int targetY = dropLocation != null ?
                      (int) dropLocation.getY() :
                      (int) newNetworkCenter.getY();

        return new Point(targetX, targetY);
    }

    /**
     * Calculates the target position using a custom spacing between networks
     *
     * @param existingNetworks List of existing networks on the workspace
     * @param newNetwork The network to be placed
     * @param dropLocation The location where the user dropped the file (can be null)
     * @param spacing Custom spacing between networks
     * @return The calculated center point for the new network
     */
    public static Point calculateTargetPosition(List<GraphPetriNet> existingNetworks,
                                                GraphPetriNet newNetwork,
                                                Point dropLocation,
                                                double spacing) {
        if (existingNetworks == null || existingNetworks.isEmpty()) {
            return dropLocation != null ? dropLocation : new Point(200, 200);
        }

        double maxRightEdge = 0.0;
        for (GraphPetriNet net : existingNetworks) {
            double rightEdge = net.getRightmostX();
            if (rightEdge > maxRightEdge) {
                maxRightEdge = rightEdge;
            }
        }

        double newNetLeftEdge = newNetwork.getLeftmostX();
        double xOffset = maxRightEdge + spacing - newNetLeftEdge;

        Point newNetworkCenter = newNetwork.getCurrentLocation();
        int targetX = (int) (newNetworkCenter.getX() + xOffset);
        int targetY = dropLocation != null ?
                      (int) dropLocation.getY() :
                      (int) newNetworkCenter.getY();

        return new Point(targetX, targetY);
    }

    /**
     * Checks if a network has any elements
     *
     * @param network The network to check
     * @return true if network has places or transitions, false otherwise
     */
    public static boolean isNetworkEmpty(GraphPetriNet network) {
        return network == null ||
               (network.getGraphPetriPlaceList().isEmpty() &&
                network.getGraphPetriTransitionList().isEmpty());
    }
}
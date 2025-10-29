package ua.stetsenkoinna.utils;

import ua.stetsenkoinna.graphnet.GraphPetriNet;
import ua.stetsenkoinna.graphnet.GraphPetriPlace;
import ua.stetsenkoinna.graphnet.GraphPetriTransition;

import java.awt.Point;
import java.awt.geom.Point2D;

/**
 * Utility for finding free space on workspace for imported networks
 *
 * @author Serhii Rybak
 */
public class NetworkLocationUtil {

    private static final int DEFAULT_SPACING = 150;

    /**
     * Find free space near drop point avoiding overlap with existing network
     *
     * @param existingNet existing network on workspace (can be null)
     * @param dropLocation desired drop location
     * @return optimal location for new network
     */
    public static Point findFreeSpaceNear(GraphPetriNet existingNet, Point dropLocation) {
        return findFreeSpaceNear(existingNet, dropLocation, DEFAULT_SPACING);
    }

    /**
     * Find free space near drop point with custom spacing
     *
     * @param existingNet existing network on workspace (can be null)
     * @param dropLocation desired drop location
     * @param spacing minimum spacing between networks
     * @return optimal location for new network
     */
    public static Point findFreeSpaceNear(GraphPetriNet existingNet, Point dropLocation, int spacing) {
        if (existingNet == null || isNetworkEmpty(existingNet)) {
            return dropLocation;
        }

        NetworkBounds bounds = calculateNetworkBounds(existingNet);

        if (bounds.isEmpty()) {
            return dropLocation;
        }

        // Check if drop location overlaps with existing network
        if (isLocationOverlapping(dropLocation, bounds, spacing)) {
            // Place to the right of existing network with spacing
            return new Point(bounds.maxX + spacing, bounds.minY);
        }

        return dropLocation;
    }

    /**
     * Check if network is empty
     */
    private static boolean isNetworkEmpty(GraphPetriNet net) {
        return net.getGraphPetriPlaceList().isEmpty() &&
               net.getGraphPetriTransitionList().isEmpty();
    }

    /**
     * Check if location overlaps with existing network bounds
     */
    private static boolean isLocationOverlapping(Point location, NetworkBounds bounds, int spacing) {
        return location.x >= bounds.minX - spacing &&
               location.x <= bounds.maxX + spacing &&
               location.y >= bounds.minY - spacing &&
               location.y <= bounds.maxY + spacing;
    }

    /**
     * Calculate bounding box of network elements
     */
    private static NetworkBounds calculateNetworkBounds(GraphPetriNet net) {
        NetworkBounds bounds = new NetworkBounds();

        // Process places
        for (GraphPetriPlace place : net.getGraphPetriPlaceList()) {
            Point2D center = place.getGraphElementCenter();
            if (center != null) {
                bounds.update((int) center.getX(), (int) center.getY());
            }
        }

        // Process transitions
        for (GraphPetriTransition transition : net.getGraphPetriTransitionList()) {
            Point2D center = transition.getGraphElementCenter();
            if (center != null) {
                bounds.update((int) center.getX(), (int) center.getY());
            }
        }

        return bounds;
    }

    /**
     * Helper class for network bounding box
     */
    private static class NetworkBounds {
        int minX = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int minY = Integer.MAX_VALUE;
        int maxY = Integer.MIN_VALUE;

        void update(int x, int y) {
            minX = Math.min(minX, x);
            maxX = Math.max(maxX, x);
            minY = Math.min(minY, y);
            maxY = Math.max(maxY, y);
        }

        boolean isEmpty() {
            return minX == Integer.MAX_VALUE;
        }
    }
}

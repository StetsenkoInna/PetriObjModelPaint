package ua.stetsenkoinna.pnml;

import java.awt.geom.Point2D;
import java.util.Map;

/**
 * Normalizes coordinates from PNML files exported by different tools.
 * Preserves network structure by maintaining relative positions and proportions.
 */
public class CoordinateNormalizer {

    public static class BoundingBox {
        public double minX, minY, maxX, maxY, width, height;

        public BoundingBox(double minX, double minY, double maxX, double maxY) {
            this.minX = minX;
            this.minY = minY;
            this.maxX = maxX;
            this.maxY = maxY;
            this.width = maxX - minX;
            this.height = maxY - minY;
        }

        public boolean isEmpty() {
            return width == 0 && height == 0;
        }
    }

    public static class NormalizationResult {
        public BoundingBox boundingBox;
        public Map<Integer, Point2D.Double> normalizedPlaceCoordinates;
        public Map<Integer, Point2D.Double> normalizedTransitionCoordinates;

        public NormalizationResult(BoundingBox boundingBox,
                                   Map<Integer, Point2D.Double> normalizedPlaceCoordinates,
                                   Map<Integer, Point2D.Double> normalizedTransitionCoordinates) {
            this.boundingBox = boundingBox;
            this.normalizedPlaceCoordinates = normalizedPlaceCoordinates;
            this.normalizedTransitionCoordinates = normalizedTransitionCoordinates;
        }
    }

    /**
     * Calculates bounding box of all elements
     */
    public static BoundingBox calculateBoundingBox(Map<Integer, Point2D.Double> placeCoordinates,
                                                    Map<Integer, Point2D.Double> transitionCoordinates) {
        if (placeCoordinates.isEmpty() && transitionCoordinates.isEmpty()) {
            return new BoundingBox(0, 0, 0, 0);
        }

        double minX = Double.MAX_VALUE, minY = Double.MAX_VALUE;
        double maxX = Double.MIN_VALUE, maxY = Double.MIN_VALUE;

        for (Point2D.Double coord : placeCoordinates.values()) {
            minX = Math.min(minX, coord.x);
            minY = Math.min(minY, coord.y);
            maxX = Math.max(maxX, coord.x);
            maxY = Math.max(maxY, coord.y);
        }

        for (Point2D.Double coord : transitionCoordinates.values()) {
            minX = Math.min(minX, coord.x);
            minY = Math.min(minY, coord.y);
            maxX = Math.max(maxX, coord.x);
            maxY = Math.max(maxY, coord.y);
        }

        return new BoundingBox(minX, minY, maxX, maxY);
    }

    /**
     * Normalizes coordinates preserving network structure
     */
    public static NormalizationResult normalize(Map<Integer, Point2D.Double> placeCoordinates,
                                                 Map<Integer, Point2D.Double> transitionCoordinates,
                                                 double targetMinX,
                                                 double targetMinY) {
        BoundingBox boundingBox = calculateBoundingBox(placeCoordinates, transitionCoordinates);

        if (boundingBox.isEmpty()) {
            return new NormalizationResult(boundingBox, placeCoordinates, transitionCoordinates);
        }

        // Calculate translation to move min corner to target position
        double offsetX = targetMinX - boundingBox.minX;
        double offsetY = targetMinY - boundingBox.minY;

        Map<Integer, Point2D.Double> normalizedPlaces = new java.util.HashMap<>();
        Map<Integer, Point2D.Double> normalizedTransitions = new java.util.HashMap<>();

        for (Map.Entry<Integer, Point2D.Double> entry : placeCoordinates.entrySet()) {
            Point2D.Double oldCoord = entry.getValue();
            normalizedPlaces.put(entry.getKey(),
                new Point2D.Double(oldCoord.x + offsetX, oldCoord.y + offsetY));
        }

        for (Map.Entry<Integer, Point2D.Double> entry : transitionCoordinates.entrySet()) {
            Point2D.Double oldCoord = entry.getValue();
            normalizedTransitions.put(entry.getKey(),
                new Point2D.Double(oldCoord.x + offsetX, oldCoord.y + offsetY));
        }

        BoundingBox normalizedBox = new BoundingBox(targetMinX, targetMinY,
                boundingBox.maxX + offsetX, boundingBox.maxY + offsetY);

        return new NormalizationResult(normalizedBox, normalizedPlaces, normalizedTransitions);
    }

    /**
     * Normalizes with default target (50, 50)
     */
    public static NormalizationResult normalize(Map<Integer, Point2D.Double> placeCoordinates,
                                                 Map<Integer, Point2D.Double> transitionCoordinates) {
        return normalize(placeCoordinates, transitionCoordinates, 50.0, 50.0);
    }

    /**
     * Scales network if too large, maintaining aspect ratio
     */
    public static NormalizationResult scaleIfNeeded(NormalizationResult result,
                                                     double maxWidth, double maxHeight) {
        BoundingBox box = result.boundingBox;

        if (box.width <= maxWidth && box.height <= maxHeight) {
            return result;
        }

        double scale = Math.min(maxWidth / box.width, maxHeight / box.height);
        Map<Integer, Point2D.Double> scaledPlaces = new java.util.HashMap<>();
        Map<Integer, Point2D.Double> scaledTransitions = new java.util.HashMap<>();

        double anchorX = box.minX;
        double anchorY = box.minY;

        for (Map.Entry<Integer, Point2D.Double> entry : result.normalizedPlaceCoordinates.entrySet()) {
            Point2D.Double coord = entry.getValue();
            scaledPlaces.put(entry.getKey(),
                new Point2D.Double(anchorX + (coord.x - anchorX) * scale,
                                   anchorY + (coord.y - anchorY) * scale));
        }

        for (Map.Entry<Integer, Point2D.Double> entry : result.normalizedTransitionCoordinates.entrySet()) {
            Point2D.Double coord = entry.getValue();
            scaledTransitions.put(entry.getKey(),
                new Point2D.Double(anchorX + (coord.x - anchorX) * scale,
                                   anchorY + (coord.y - anchorY) * scale));
        }

        BoundingBox scaledBox = new BoundingBox(box.minX, box.minY,
                box.minX + box.width * scale, box.minY + box.height * scale);

        return new NormalizationResult(scaledBox, scaledPlaces, scaledTransitions);
    }
}
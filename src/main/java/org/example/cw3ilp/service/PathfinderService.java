package org.example.cw3ilp.service;

import org.example.cw3ilp.api.model.LngLat;
import org.example.cw3ilp.api.model.LngLatAlt;
import org.example.cw3ilp.api.model.RestrictedArea;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class PathfinderService {

    private static final Logger logger = LoggerFactory.getLogger(PathfinderService.class);

    private final RegionService regionService;
    private final DistanceService distanceService;

    // Movement constants
    private static final int NUM_DIRECTIONS = 16;
    private static final double STEP_SIZE = 0.00015;


    public PathfinderService(RegionService regionService, DistanceService distanceService) {
        this.regionService = regionService;
        this.distanceService = distanceService;
    }

    /**
     * Calculate Euclidean distance between two points (in degrees)
     */
    public double calculateDistance(LngLatAlt from, LngLatAlt to) {
        if (from == null || to == null ||
                from.getLng() == null || from.getLat() == null ||
                to.getLng() == null || to.getLat() == null) {
            logger.warn("Cannot calculate distance with null coordinates");
            return Double.MAX_VALUE;
        }

        LngLat fromLngLat = new LngLat(from.getLng(), from.getLat());
        LngLat toLngLat = new LngLat(to.getLng(), to.getLat());
        return distanceService.computeDistance(fromLngLat, toLngLat);
    }

    /**
     * Check if two positions are close enough
     */
    public boolean isCloseEnough(LngLatAlt from, LngLatAlt to) {
        if (from == null || to == null ||
                from.getLng() == null || from.getLat() == null ||
                to.getLng() == null || to.getLat() == null) {
            return false;
        }

        LngLat fromLngLat = new LngLat(from.getLng(), from.getLat());
        LngLat toLngLat = new LngLat(to.getLng(), to.getLat());
        return distanceService.computeCloseness(fromLngLat, toLngLat);
    }

    /**
     * Get next position given current position and direction (0-15)
     */
    public LngLatAlt getNextPosition(LngLatAlt current, int direction) {
        if (direction < 0 || direction >= NUM_DIRECTIONS) {
            throw new IllegalArgumentException("Direction must be 0-15, got: " + direction);
        }

        if (current == null || current.getLng() == null || current.getLat() == null) {
            logger.error("Cannot get next position from null current position");
            return null;
        }

        double angle = direction * 22.5;
        LngLat currentLngLat = new LngLat(current.getLng(), current.getLat());
        LngLat nextLngLat = distanceService.computeNextPosition(currentLngLat, angle);

        return new LngLatAlt(nextLngLat.getLng(), nextLngLat.getLat(), null);
    }

    /**
     * Check if a point is inside any restricted area
     * Ignores altitude - treats all restricted areas as no-fly zones
     */
    public boolean isInNoFlyZone(LngLatAlt point, List<RestrictedArea> zones) {
        if (zones == null || zones.isEmpty()) {
            return false;
        }

        if (point == null || point.getLng() == null || point.getLat() == null) {
            logger.warn("Cannot check no-fly zone with null point");
            return true;
        }

        for (RestrictedArea zone : zones) {
            List<LngLat> vertices = convertToLngLat(zone.getVertices());
            if (vertices != null && regionService.isInside(vertices, point.getLng(), point.getLat())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Check if a line segment crosses any restricted area
     * Ignores altitude - treats all restricted areas as no-fly zones
     */
    public boolean crossesNoFlyZone(LngLatAlt from, LngLatAlt to, List<RestrictedArea> zones) {
        if (zones == null || zones.isEmpty()) {
            return false;
        }

        if (from == null || to == null ||
                from.getLng() == null || from.getLat() == null ||
                to.getLng() == null || to.getLat() == null) {
            logger.warn("Cannot check crossing with null coordinates");
            return true;
        }

        for (RestrictedArea zone : zones) {
            List<LngLat> vertices = convertToLngLat(zone.getVertices());
            if (lineIntersectsPolygon(from, to, vertices)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Find optimal path from start to goal using A* algorithm with pruning
     * Returns null if no path exists or inputs are invalid
     */
    public List<LngLatAlt> findPath(LngLatAlt start, LngLatAlt goal, List<RestrictedArea> zones) {
        // Validate inputs & log
        if (start == null || start.getLng() == null || start.getLat() == null) {
            logger.error("Invalid start position: {}", start);
            return null;
        }
        if (goal == null || goal.getLng() == null || goal.getLat() == null) {
            logger.error("Invalid goal position: {}", goal);
            return null;
        }

        logger.info("Finding path from ({}, {}) to ({}, {})",
                start.getLng(), start.getLat(), goal.getLng(), goal.getLat());

        if (isInNoFlyZone(start, zones)) {
            logger.warn("Start position is in restricted area");
            return null;
        }
        if (isInNoFlyZone(goal, zones)) {
            logger.warn("Goal position is in restricted area");
            return null;
        }

        // A* data structures
        PriorityQueue<Node> openSet = new PriorityQueue<>(Comparator.comparingDouble(n -> n.fScore));
        Set<String> closedSet = new HashSet<>();
        Map<String, Double> bestGScore = new HashMap<>();

        LngLatAlt safeStart = new LngLatAlt(start.getLng(), start.getLat(), null);
        String startKey = positionKey(safeStart);

        double initialH = calculateDistance(safeStart, goal);
        Node startNode = new Node(safeStart, null, 0, initialH);
        openSet.add(startNode);
        bestGScore.put(startKey, 0.0);

        int bestDirection = calculateBestDirection(start, goal);

        int iterations = 0;
        // Reasonable safety limit: 100k iterations prevents infinite loops
        // while still being far above typical usage (~1,500 iterations)
        int maxIterations = 100000;

        while (!openSet.isEmpty() && iterations < maxIterations) {
            iterations++;

            Node current = openSet.poll();
            String currentKey = positionKey(current.position);

            if (closedSet.contains(currentKey)) {
                continue;
            }
            closedSet.add(currentKey);

            if (isCloseEnough(current.position, goal)) {
                logger.info("Path found in {} iterations, {} moves", iterations, (int) current.gScore);
                return reconstructPath(current);
            }

            int[] directionOrder = getDirectionOrder(bestDirection);

            for (int direction : directionOrder) {
                LngLatAlt neighbor = getNextPosition(current.position, direction);
                if (neighbor == null) continue;

                String neighborKey = positionKey(neighbor);

                if (closedSet.contains(neighborKey)) continue;
                if (neighborKey.startsWith("invalid")) continue;

                if (isInNoFlyZone(neighbor, zones)) continue;
                if (crossesNoFlyZone(current.position, neighbor, zones)) continue;

                double tentativeG = current.gScore + 1;

                Double existingG = bestGScore.get(neighborKey);
                if (existingG != null && tentativeG >= existingG) {
                    continue;
                }

                bestGScore.put(neighborKey, tentativeG);

                double h = calculateDistance(neighbor, goal);
                if (h == Double.MAX_VALUE) continue;

                Node neighborNode = new Node(neighbor, current, tentativeG, h);
                openSet.add(neighborNode);
            }
        }

        logger.warn("No path found after {} iterations", iterations);
        return null;
    }

    /**
     * Calculate the best direction (0-15) from start toward goal
     */
    private int calculateBestDirection(LngLatAlt from, LngLatAlt to) {
        double dLng = to.getLng() - from.getLng();
        double dLat = to.getLat() - from.getLat();

        double angle = Math.atan2(dLat, dLng);
        if (angle < 0) angle += 2 * Math.PI;

        return (int) Math.round(angle / (Math.PI / 8)) % 16;
    }

    /**
     * Get directions ordered by proximity to best direction
     * This helps A* explore promising directions first
     */
    private int[] getDirectionOrder(int bestDirection) {
        int[] order = new int[NUM_DIRECTIONS];

        order[0] = bestDirection;
        for (int i = 1; i < NUM_DIRECTIONS; i++) {
            int offset = (i + 1) / 2;
            if (i % 2 == 1) {
                order[i] = (bestDirection + offset) % NUM_DIRECTIONS;
            } else {
                order[i] = (bestDirection - offset + NUM_DIRECTIONS) % NUM_DIRECTIONS;
            }
        }

        return order;
    }

    /**
     * Convert LngLatAlt list to LngLat list for RegionService
     */
    private List<LngLat> convertToLngLat(List<LngLatAlt> vertices) {
        if (vertices == null || vertices.isEmpty()) {
            return null;
        }

        List<LngLat> result = new ArrayList<>(vertices.size());
        for (LngLatAlt vertex : vertices) {
            if (vertex != null && vertex.getLng() != null && vertex.getLat() != null) {
                result.add(new LngLat(vertex.getLng(), vertex.getLat()));
            }
        }
        return result;
    }

    /**
     * Reconstruct path from A* result
     */
    private List<LngLatAlt> reconstructPath(Node endNode) {
        List<LngLatAlt> path = new ArrayList<>();
        Node current = endNode;

        while (current != null) {
            path.add(current.position);
            current = current.parent;
        }

        Collections.reverse(path);
        return path;
    }

    /**
     * Create unique key for position (for HashMap/Set)
     */
    private String positionKey(LngLatAlt pos) {
        if (pos == null || pos.getLng() == null || pos.getLat() == null) {
            logger.warn("Cannot create position key for invalid position: {}", pos);
            return "invalid-" + System.nanoTime();
        }

        long lngKey = Math.round(pos.getLng() / STEP_SIZE);
        long latKey = Math.round(pos.getLat() / STEP_SIZE);
        return lngKey + "," + latKey;
    }

    /**
     * Check if line segment intersects polygon
     */
    private boolean lineIntersectsPolygon(LngLatAlt from, LngLatAlt to, List<LngLat> vertices) {
        if (vertices == null || vertices.size() < 3) {
            return false;
        }

        if (regionService.isInside(vertices, from.getLng(), from.getLat()) ||
                regionService.isInside(vertices, to.getLng(), to.getLat())) {
            return true;
        }

        int n = vertices.size();
        for (int i = 0; i < n; i++) {
            LngLat v1 = vertices.get(i);
            LngLat v2 = vertices.get((i + 1) % n);

            if (segmentsIntersect(from, to, v1, v2)) {
                return true;
            }
        }

        return false;
    }

    /**
     * Check if two line segments intersect
     */
    private boolean segmentsIntersect(LngLatAlt p1, LngLatAlt p2, LngLat p3, LngLat p4) {
        double d1 = direction(p3.getLng(), p3.getLat(), p4.getLng(), p4.getLat(),
                p1.getLng(), p1.getLat());
        double d2 = direction(p3.getLng(), p3.getLat(), p4.getLng(), p4.getLat(),
                p2.getLng(), p2.getLat());
        double d3 = direction(p1.getLng(), p1.getLat(), p2.getLng(), p2.getLat(),
                p3.getLng(), p3.getLat());
        double d4 = direction(p1.getLng(), p1.getLat(), p2.getLng(), p2.getLat(),
                p4.getLng(), p4.getLat());

        if (((d1 > 0 && d2 < 0) || (d1 < 0 && d2 > 0)) &&
                ((d3 > 0 && d4 < 0) || (d3 < 0 && d4 > 0))) {
            return true;
        }

        if (d1 == 0 && onSegment(p3.getLng(), p3.getLat(), p4.getLng(), p4.getLat(),
                p1.getLng(), p1.getLat())) return true;
        if (d2 == 0 && onSegment(p3.getLng(), p3.getLat(), p4.getLng(), p4.getLat(),
                p2.getLng(), p2.getLat())) return true;
        if (d3 == 0 && onSegment(p1.getLng(), p1.getLat(), p2.getLng(), p2.getLat(),
                p3.getLng(), p3.getLat())) return true;
        if (d4 == 0 && onSegment(p1.getLng(), p1.getLat(), p2.getLng(), p2.getLat(),
                p4.getLng(), p4.getLat())) return true;

        return false;
    }

    /**
     * Calculate direction (cross product)
     */
    private double direction(double x1, double y1, double x2, double y2, double x3, double y3) {
        return (x3 - x1) * (y2 - y1) - (x2 - x1) * (y3 - y1);
    }

    /**
     * Check if point (px, py) is on line segment (x1,y1)-(x2,y2)
     */
    private boolean onSegment(double x1, double y1, double x2, double y2, double px, double py) {
        return px >= Math.min(x1, x2) && px <= Math.max(x1, x2) &&
                py >= Math.min(y1, y2) && py <= Math.max(y1, y2);
    }

    /**
     * A* Node
     */
    private static class Node {
        LngLatAlt position;
        Node parent;
        double gScore;
        double hScore;
        double fScore;

        Node(LngLatAlt position, Node parent, double gScore, double hScore) {
            this.position = position;
            this.parent = parent;
            this.gScore = gScore;
            this.hScore = hScore;
            this.fScore = gScore + hScore;
        }
    }
}
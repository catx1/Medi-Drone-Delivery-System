package org.example.cw3ilp.service;

import org.example.cw3ilp.api.model.LngLatAlt;
import org.example.cw3ilp.api.dto.DronePositionUpdate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class DroneFlightSimulator {

    private static final Logger logger = LoggerFactory.getLogger(DroneFlightSimulator.class);

    private List<LngLatAlt> flightPath;
    private int currentPathIndex = 0;
    private String droneId;
    private String status = "WAITING"; // WAITING, FLYING, HOVERING, DELIVERED
    private boolean isActive = false;

    // Simulation parameters
    private static final double SPEED_KM_PER_HOUR = 1000.0;
    private static final double SPEED_DEGREES_PER_SECOND = SPEED_KM_PER_HOUR / 111.0 / 3600.0;

    private double currentLng;
    private double currentLat;
    private double targetLng;
    private double targetLat;

    public void startFlight(String droneId, List<LngLatAlt> path) {
        if (path == null || path.isEmpty()) {
            throw new IllegalArgumentException("Flight path cannot be empty");
        }

        this.droneId = droneId;
        this.flightPath = path;
        this.currentPathIndex = 0;
        this.isActive = true;
        this.status = "FLYING";

        // IMPORTANT: Set initial position
        LngLatAlt start = path.getFirst();
        this.currentLng = start.getLng();  // Make sure this is not null!
        this.currentLat = start.getLat();  // Make sure this is not null!

        // Set first target
        if (path.size() > 1) {
            LngLatAlt target = path.get(1);
            this.targetLng = target.getLng();  // Make sure this is not null!
            this.targetLat = target.getLat();  // Make sure this is not null!
        }

        logger.info("Drone {} started flight with {} waypoints", droneId, path.size());
        logger.info("Initial position: lng={}, lat={}", currentLng, currentLat);  // ADD THIS LOG
    }

    public DronePositionUpdate updatePosition() {
        if (!isActive || flightPath == null) {
            return null;
        }

        // Calculate distance to target
        double distanceToTarget = calculateDistance(currentLng, currentLat, targetLng, targetLat);

        // If we're close enough to target, move to next waypoint
        if (distanceToTarget < SPEED_DEGREES_PER_SECOND * 2) {
            currentPathIndex++;

            if (currentPathIndex >= flightPath.size()) {
                // Reached end of path
                status = "DELIVERED";
                isActive = false;
                logger.info("Drone {} completed delivery", droneId);
            } else {
                // Move to next waypoint
                LngLatAlt nextPoint = flightPath.get(currentPathIndex);
                currentLng = nextPoint.getLng();
                currentLat = nextPoint.getLat();

                if (currentPathIndex < flightPath.size() - 1) {
                    LngLatAlt target = flightPath.get(currentPathIndex + 1);
                    targetLng = target.getLng();
                    targetLat = target.getLat();

                    // Check if this is a hover point
                    if (Math.abs(currentLng - targetLng) < 0.0000001 &&
                            Math.abs(currentLat - targetLat) < 0.0000001) {
                        status = "HOVERING";
                    } else {
                        status = "FLYING";
                    }
                }
            }
        } else {
            // Move towards target
            double dx = targetLng - currentLng;
            double dy = targetLat - currentLat;
            double angle = Math.atan2(dy, dx);

            currentLng += Math.cos(angle) * SPEED_DEGREES_PER_SECOND;
            currentLat += Math.sin(angle) * SPEED_DEGREES_PER_SECOND;

            status = "FLYING";
        }

        // Calculate progress percentage
        double progressPercent = 0.0;
        if (flightPath != null && !flightPath.isEmpty()) {
            progressPercent = (currentPathIndex / (double) flightPath.size()) * 100.0;
        }

        // IMPORTANT: Create the update object with explicit values
        DronePositionUpdate update = new DronePositionUpdate();
        update.setDroneId(this.droneId);           // Set droneId
        update.setLng(this.currentLng);            // Set lng
        update.setLat(this.currentLat);            // Set lat
        update.setStatus(this.status);             // Set status
        update.setPercentComplete(progressPercent); // Set progress
        update.setTimestamp(System.currentTimeMillis());

        // Log to verify
        logger.debug("Created update: droneId={}, lng={}, lat={}, status={}, progress={}%",
                update.getDroneId(), update.getLng(), update.getLat(), update.getStatus(),
                String.format("%.1f", progressPercent));

        return update;
    }

    private double calculateDistance(double lng1, double lat1, double lng2, double lat2) {
        double dx = lng2 - lng1;
        double dy = lat2 - lat1;
        return Math.sqrt(dx * dx + dy * dy);
    }

    public void stopFlight() {
        this.isActive = false;
        this.status = "STOPPED";
        logger.info("Drone {} flight stopped", droneId);
    }

    public boolean isActive() {
        return isActive;
    }

}
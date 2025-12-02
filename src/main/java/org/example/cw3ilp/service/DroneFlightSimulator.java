package org.example.cw3ilp.service;

import lombok.Getter;
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
    private String status = "WAITING"; // WAITING, FLYING, HOVERING, ARRIVED, RETURNED
    private boolean isActive = false;
    /**
     * -- GETTER --
     *  Get current order number being delivered
     */
    @Getter
    private String currentOrderNumber;
    private boolean isReturnJourney = false;

    // Simulation parameters
    private static final double SPEED_KM_PER_HOUR = 160.0;
    private static final double SPEED_DEGREES_PER_SECOND = SPEED_KM_PER_HOUR / 111.0 / 3600.0;

    /** Duration (in seconds) that drone hovers at delivery location for drop-off */
    public static final int HOVER_DURATION_SECONDS = 3;

    private double currentLng;
    private double currentLat;
    private double targetLng;
    private double targetLat;

    /**
     * Start flight WITHOUT order tracking (manual mode)
     */
    public void startFlight(String droneId, List<LngLatAlt> path) {
        if (path == null || path.isEmpty()) {
            throw new IllegalArgumentException("Flight path cannot be empty");
        }

        // Check if this is a return journey
        // Has current order number AND status was arrived (waiting at customer)
        String previousOrderNumber = this.currentOrderNumber;
        boolean wasWaitingAtCustomer = (this.status != null && this.status.equals("ARRIVED"));

        // If we have an order number and were waiting at customer, this is a return journey
        this.isReturnJourney = (previousOrderNumber != null && wasWaitingAtCustomer);

        // Keep order number for return journey, clear for new deliveries
        if (!this.isReturnJourney) {
            this.currentOrderNumber = null; // Clear for new deliveries
        }

        this.droneId = droneId;
        this.flightPath = path;
        this.currentPathIndex = 0;
        this.isActive = true;
        this.status = "FLYING";

        // Set initial position
        LngLatAlt start = path.getFirst();
        this.currentLng = start.getLng();
        this.currentLat = start.getLat();

        // Set first target
        if (path.size() > 1) {
            LngLatAlt target = path.get(1);
            this.targetLng = target.getLng();
            this.targetLat = target.getLat();
        }

        if (isReturnJourney) {
            logger.info("Drone {} starting return journey to service point ({} waypoints)", droneId, path.size());
        } else {
            logger.info("Drone {} started delivery flight with {} waypoints", droneId, path.size());
        }
        logger.info("Initial position: lng={}, lat={}", currentLng, currentLat);
    }

    /**
     * Start flight WITH order tracking (for queue processor)
     */
    public void startFlightWithOrder(String droneId, List<LngLatAlt> path, String orderNumber) {
        // Start the flight (this will set isActive = true and calculate isReturnJourney)
        startFlight(droneId, path);

        // Set the order number for this delivery
        // Note: startFlight may have cleared this for new deliveries, so we set it again
        this.currentOrderNumber = orderNumber;

        logger.info("Delivery flight started for order: {}", orderNumber);
    }

    /**
     * Get current drone position (called by WebSocket controller)
     */
    public DronePositionUpdate getCurrentPosition() {
        if (!isActive || flightPath == null) {
            return null;
        }

        // Update position
        return updatePosition();
    }

    /**
     * Update drone position along flight path
     */
    private DronePositionUpdate updatePosition() {
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
                if (isReturnJourney) {
                    status = "RETURNED";
                    isActive = false;
                    logger.info("Drone {} returned to service point", droneId);
                    // Clear return journey flag
                    isReturnJourney = false;
                    currentOrderNumber = null;
                } else {
                    status = "ARRIVED";
                    isActive = false;
                    logger.info("Drone {} arrived at delivery location, waiting for collection", droneId);
                }
            } else {
                // Move to next waypoint
                LngLatAlt nextPoint = flightPath.get(currentPathIndex);
                currentLng = nextPoint.getLng();
                currentLat = nextPoint.getLat();

                if (currentPathIndex < flightPath.size() - 1) {
                    LngLatAlt target = flightPath.get(currentPathIndex + 1);
                    targetLng = target.getLng();
                    targetLat = target.getLat();

                    // Check if this is a hover point (same coordinates)
                    if (Math.abs(currentLng - targetLng) < 0.0000001 &&
                            Math.abs(currentLat - targetLat) < 0.0000001) {
                        status = "HOVERING";
                    } else {
                        status = "FLYING";
                    }
                }
            }
        } else {
            // Move towards target using angle-based interpolation
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

        // Create position update
        DronePositionUpdate update = new DronePositionUpdate();
        update.setDroneId(this.droneId);
        update.setLng(this.currentLng);
        update.setLat(this.currentLat);
        update.setStatus(this.status);
        update.setPercentComplete(progressPercent);
        update.setTimestamp(System.currentTimeMillis());
        update.setOrderNumber(this.currentOrderNumber); // Include order number

        logger.debug("Position: droneId={}, lng={}, lat={}, status={}, order={}, progress={}%",
                update.getDroneId(), update.getLng(), update.getLat(), update.getStatus(),
                update.getOrderNumber(), String.format("%.1f", progressPercent));

        return update;
    }

    /**
     * Calculate distance between two points
     */
    private double calculateDistance(double lng1, double lat1, double lng2, double lat2) {
        double dx = lng2 - lng1;
        double dy = lat2 - lat1;
        return Math.sqrt(dx * dx + dy * dy);
    }

    /**
     * Stop current flight
     */
    public void stopFlight() {
        this.isActive = false;
        this.status = "STOPPED";
        this.currentOrderNumber = null;
        logger.info("Drone {} flight stopped", droneId);
    }

    /**
     * Check if simulator is active (legacy method)
     */
    public boolean isActive() {
        return isActive;
    }
}
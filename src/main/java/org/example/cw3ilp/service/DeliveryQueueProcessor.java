package org.example.cw3ilp.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.cw3ilp.api.model.DronesAvailability;
import org.example.cw3ilp.api.model.LngLatAlt;
import org.example.cw3ilp.api.model.RestrictedArea;
import org.example.cw3ilp.entity.DeliveryOrder;
import org.example.cw3ilp.entity.OrderStatus;
import org.example.cw3ilp.repository.DeliveryOrderRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class DeliveryQueueProcessor {

    private final DeliveryOrderRepository orderRepository;
    private final DroneFlightSimulator droneFlightSimulator;
    private final PathfinderService pathfinderService;
    private final ILPDataService ilpDataService;
    private final DistanceService distanceService;

    /**
     * Process queue every 5 seconds
     * Checks for queued orders and dispatches drone if available
     */
    @Scheduled(fixedRate = 5000)
    public void processQueue() {
        try {
            List<DeliveryOrder> queuedOrders = orderRepository
                    .findByStatusOrderByCreatedAtAsc(OrderStatus.QUEUED);

            if (queuedOrders.isEmpty()) {
                return;
            }

            // Check if drone is available
            if (droneFlightSimulator.isActive()) {
                log.debug("Drone busy, {} orders in queue", queuedOrders.size());
                return;
            }

            // Process first order in queue
            DeliveryOrder order = queuedOrders.get(0);
            log.info("Processing order from queue: {}", order.getOrderNumber());

            try {
                dispatchDrone(order);
            } catch (Exception e) {
                log.error("Failed to dispatch drone for order: {}", order.getOrderNumber(), e);
            }
        } catch (org.springframework.dao.InvalidDataAccessResourceUsageException e) {
            // Database not ready yet - silently skip this iteration
            log.trace("Database not ready, skipping queue processing");
        } catch (Exception e) {
            log.error("Error processing queue", e);
        }
    }

    /**
     * Dispatch drone for a specific order
     */
    private void dispatchDrone(DeliveryOrder order) {
        log.info("Dispatching drone for order: {}", order.getOrderNumber());

        try {

            // Delivery target
            LngLatAlt target = new LngLatAlt(
                    order.getDeliveryLng(),
                    order.getDeliveryLat(),
                    0.0
            );

            // Get all service points
            List<DronesAvailability.ServicePoint> servicePoints =
                    ilpDataService.getAllServicePoints();

            // Find nearest service point to delivery target
            DronesAvailability.ServicePoint nearestServicePoint =
                    distanceService.findNearestServicePoint(servicePoints, target.getLng(), target.getLat());

            if (nearestServicePoint == null) {
                throw new RuntimeException("No service points available");
            }

            LngLatAlt servicePointLocation = new LngLatAlt(
                    nearestServicePoint.getLocation().getLng(),
                    nearestServicePoint.getLocation().getLat(),
                    0.0
            );

            log.info("Using service point: {} at distance: {}",
                    nearestServicePoint.getName(),
                    distanceService.computeDistance(
                            new org.example.cw3ilp.api.model.LngLat(target.getLng(), target.getLat()),
                            new org.example.cw3ilp.api.model.LngLat(servicePointLocation.getLng(), servicePointLocation.getLat())
                    )
            );

            // Get restricted areas for pathfinding
            List<RestrictedArea> restrictedAreas = ilpDataService.getAllRestrictedAreas();

            // Calculate path: Service Point -> Customer (ONE-WAY delivery only)
            List<LngLatAlt> pathToTarget = pathfinderService.findPath(
                    servicePointLocation, target, restrictedAreas);

            if (pathToTarget == null) {
                throw new RuntimeException("Could not find valid path to customer");
            }

            // Build delivery path: Service Point → Customer
            List<LngLatAlt> fullPath = new ArrayList<>(pathToTarget);

            // Add hover at delivery location (landing/drop-off)
            // Each waypoint = 1 second, so add target multiple times to hover
            for (int i = 0; i < DroneFlightSimulator.HOVER_DURATION_SECONDS; i++) {
                fullPath.add(target);
            }

            log.info("Delivery path calculated: {} waypoints", fullPath.size());
            log.info("Route: {} → {} (delivery)",
                    nearestServicePoint.getName(), order.getCustomerAddress());

            // Use the drone that was already assigned during order creation
            String assignedDroneId = order.getAssignedDroneId();
            if (assignedDroneId == null || assignedDroneId.isEmpty()) {
                log.warn("Order {} has no assigned drone, using default", order.getOrderNumber());
                assignedDroneId = "DRONE-001";
            }

            // Update order status to IN_TRANSIT
            order.setStatus(OrderStatus.IN_TRANSIT);
            order.setDispatchedAt(LocalDateTime.now());
            orderRepository.save(order);

            log.info("Using assigned drone: {}", assignedDroneId);

            // Clear any previous flight state before starting new delivery
            if (droneFlightSimulator.isActive()) {
                droneFlightSimulator.stopFlight();
            }

            // Start NEW delivery flight with order context
            droneFlightSimulator.startFlightWithOrder(assignedDroneId, fullPath, order.getOrderNumber());

            log.info("Drone dispatched for order: {}", order.getOrderNumber());

        } catch (Exception e) {
            log.error("Failed to dispatch drone for order: {}", order.getOrderNumber(), e);
            throw e;
        }
    }

}

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
        log.info("🚁 Processing order from queue: {}", order.getOrderNumber());

        try {
            dispatchDrone(order);
        } catch (Exception e) {
            log.error("Failed to dispatch drone for order: {}", order.getOrderNumber(), e);
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
                    findNearestServicePoint(target, servicePoints);

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

            // Add hover at delivery location (landing/drop-off - 3 seconds)
            fullPath.add(target);
            fullPath.add(target);
            fullPath.add(target);

            log.info("Delivery path calculated: {} waypoints", fullPath.size());
            log.info("Route: {} → {} (delivery)",
                    nearestServicePoint.getName(), order.getCustomerAddress());

            // Update order status to IN_TRANSIT
            order.setStatus(OrderStatus.IN_TRANSIT);
            order.setAssignedDroneId("DRONE-001");
            order.setDispatchedAt(LocalDateTime.now());
            orderRepository.save(order);

            // Clear any previous flight state before starting new delivery
            if (droneFlightSimulator.isActive()) {
                droneFlightSimulator.stopFlight();
            }

            // Start NEW delivery flight with order context
            droneFlightSimulator.startFlightWithOrder("DRONE-001", fullPath, order.getOrderNumber());

            log.info("✅ Drone dispatched for order: {}", order.getOrderNumber());

        } catch (Exception e) {
            log.error("Failed to dispatch drone for order: {}", order.getOrderNumber(), e);
            throw e;
        }
    }

    /**
     * Find nearest service point to target location
     */
    private DronesAvailability.ServicePoint findNearestServicePoint(
            LngLatAlt target,
            List<DronesAvailability.ServicePoint> servicePoints) {

        DronesAvailability.ServicePoint nearest = null;
        double minDistance = Double.MAX_VALUE;

        for (DronesAvailability.ServicePoint sp : servicePoints) {
            if (sp.getLocation() == null) continue;

            org.example.cw3ilp.api.model.LngLat spLocation = new org.example.cw3ilp.api.model.LngLat(
                    sp.getLocation().getLng(),
                    sp.getLocation().getLat()
            );

            org.example.cw3ilp.api.model.LngLat targetLocation = new org.example.cw3ilp.api.model.LngLat(
                    target.getLng(),
                    target.getLat()
            );

            double distance = distanceService.computeDistance(spLocation, targetLocation);

            if (distance < minDistance) {
                minDistance = distance;
                nearest = sp;
            }
        }

        return nearest;
    }
}

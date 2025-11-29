package org.example.cw3ilp.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.cw3ilp.entity.DeliveryOrder;
import org.example.cw3ilp.entity.Medication;
import org.example.cw3ilp.entity.OrderStatus;
import org.example.cw3ilp.repository.DeliveryOrderRepository;
import org.example.cw3ilp.repository.MedicationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderService {

    private final DeliveryOrderRepository orderRepository;
    private final MedicationRepository medicationRepository;
    private final GeocodingService geocodingService;
    private final DroneFlightSimulator droneFlightSimulator;
    private final PathfinderService pathfinderService;
    private final ILPDataService ilpDataService;
    private final DistanceService distanceService;

    /**
     * Create a new delivery order
     */
    @Transactional
    public DeliveryOrder createOrder(String address, Long medicationId, int quantity) {
        log.info("Creating order: address={}, medicationId={}, quantity={}",
                address, medicationId, quantity);

        // 1. Validate medication exists and has stock
        Medication medication = medicationRepository.findById(medicationId)
                .orElseThrow(() -> new RuntimeException("Medication not found with id: " + medicationId));

        if (medication.getStockQuantity() == null || medication.getStockQuantity() < quantity) {
            throw new RuntimeException("Insufficient stock. Available: " +
                    (medication.getStockQuantity() != null ? medication.getStockQuantity() : 0) +
                    ", Requested: " + quantity);
        }

        // 2. Geocode the address
        Map<String, Double> coordinates;
        try {
            coordinates = geocodingService.geocodeAddress(address);
        } catch (Exception e) {
            throw new RuntimeException("Failed to geocode address: " + e.getMessage());
        }

        // 3. Generate order number
        String orderNumber = generateOrderNumber();

        // 4. Create the order
        DeliveryOrder order = new DeliveryOrder();
        order.setOrderNumber(orderNumber);
        order.setCustomerAddress(address);
        order.setDeliveryLat(coordinates.get("lat"));
        order.setDeliveryLng(coordinates.get("lng"));
        order.setMedication(medication);
        order.setQuantity(quantity);
        order.setStatus(OrderStatus.QUEUED);
        order.setCreatedAt(LocalDateTime.now());

        // 5. Update medication stock
        medication.setStockQuantity(medication.getStockQuantity() - quantity);
        medicationRepository.save(medication);

        // 6. Save order
        DeliveryOrder savedOrder = orderRepository.save(order);

        log.info("✅ Order created: {}", orderNumber);

        return savedOrder;
    }

    /**
     * Get all queued orders (waiting for drone dispatch)
     */
    public List<DeliveryOrder> getQueuedOrders() {
        return orderRepository.findByStatusOrderByCreatedAtAsc(OrderStatus.QUEUED);
    }

    /**
     * Find order by order number
     */
    public DeliveryOrder getOrderByNumber(String orderNumber) {
        return orderRepository.findByOrderNumber(orderNumber)
                .orElseThrow(() -> new RuntimeException("Order not found: " + orderNumber));
    }

    /**
     * Confirm pickup by order number
     */
    @Transactional
    public DeliveryOrder confirmPickupByOrderNumber(String orderNumber) {
        log.info("Confirming pickup for order: {}", orderNumber);

        DeliveryOrder order = orderRepository.findByOrderNumber(orderNumber)
                .orElseThrow(() -> new RuntimeException("Order not found: " + orderNumber));

        if (order.getStatus() != OrderStatus.ARRIVED) {
            throw new RuntimeException("Drone has not arrived yet. Current status: " + order.getStatus());
        }

        order.setStatus(OrderStatus.COLLECTED);
        order.setCollectedAt(LocalDateTime.now());

        DeliveryOrder savedOrder = orderRepository.save(order);

        log.info("✅ Pickup confirmed for order: {}", order.getOrderNumber());
        log.info("🚁 Drone now returning to service point");

        // Trigger drone to return to base
        triggerDroneReturnToBase(savedOrder);

        return savedOrder;
    }

    /**
     * Trigger drone to return to service point after customer pickup
     */
    private void triggerDroneReturnToBase(DeliveryOrder order) {
        try {
            // Check if drone is currently at the delivery location (not active = waiting)
            if (droneFlightSimulator.isActive()) {
                log.warn("⚠️ Drone is still in flight, cannot start return journey yet");
                return;
            }

            // Current location (customer/delivery location)
            org.example.cw3ilp.api.model.LngLatAlt currentLocation =
                new org.example.cw3ilp.api.model.LngLatAlt(
                    order.getDeliveryLng(),
                    order.getDeliveryLat(),
                    0.0
                );

            // Find nearest service point
            List<org.example.cw3ilp.api.model.DronesAvailability.ServicePoint> servicePoints =
                ilpDataService.getAllServicePoints();

            org.example.cw3ilp.api.model.DronesAvailability.ServicePoint nearestServicePoint =
                findNearestServicePoint(currentLocation, servicePoints);

            if (nearestServicePoint == null) {
                log.warn("⚠️ No service points available for return journey");
                return;
            }

            org.example.cw3ilp.api.model.LngLatAlt servicePointLocation =
                new org.example.cw3ilp.api.model.LngLatAlt(
                    nearestServicePoint.getLocation().getLng(),
                    nearestServicePoint.getLocation().getLat(),
                    0.0
                );

            // Calculate return path
            List<org.example.cw3ilp.api.model.RestrictedArea> restrictedAreas =
                ilpDataService.getAllRestrictedAreas();

            List<org.example.cw3ilp.api.model.LngLatAlt> returnPath =
                pathfinderService.findPath(currentLocation, servicePointLocation, restrictedAreas);

            if (returnPath == null || returnPath.isEmpty()) {
                log.warn("⚠️ Could not calculate return path");
                return;
            }

            log.info("🚁 Drone {} departing from customer location", order.getAssignedDroneId());
            log.info("📍 Returning to service point: {}", nearestServicePoint.getName());
            log.info("🛤️  Return path: {} waypoints", returnPath.size());

            // IMPORTANT: Start return flight - the drone should still have the order number
            // so DroneFlightSimulator will detect this as a return journey
            droneFlightSimulator.startFlight(order.getAssignedDroneId(), returnPath);

        } catch (Exception e) {
            log.error("❌ Failed to trigger drone return: {}", e.getMessage(), e);
        }
    }

    /**
     * Find nearest service point to given location
     */
    private org.example.cw3ilp.api.model.DronesAvailability.ServicePoint findNearestServicePoint(
            org.example.cw3ilp.api.model.LngLatAlt location,
            List<org.example.cw3ilp.api.model.DronesAvailability.ServicePoint> servicePoints) {

        return servicePoints.stream()
                .filter(sp -> sp.getLocation() != null)
                .min((sp1, sp2) -> {
                    double dist1 = distanceService.computeDistance(
                        new org.example.cw3ilp.api.model.LngLat(location.getLng(), location.getLat()),
                        new org.example.cw3ilp.api.model.LngLat(sp1.getLocation().getLng(), sp1.getLocation().getLat())
                    );
                    double dist2 = distanceService.computeDistance(
                        new org.example.cw3ilp.api.model.LngLat(location.getLng(), location.getLat()),
                        new org.example.cw3ilp.api.model.LngLat(sp2.getLocation().getLng(), sp2.getLocation().getLat())
                    );
                    return Double.compare(dist1, dist2);
                })
                .orElse(null);
    }

    /**
     * Update order status
     */
    @Transactional
    public DeliveryOrder updateOrderStatus(String orderNumber, OrderStatus newStatus) {
        DeliveryOrder order = getOrderByNumber(orderNumber);

        OrderStatus oldStatus = order.getStatus();
        order.setStatus(newStatus);

        // Update timestamps based on status
        switch (newStatus) {
            case IN_TRANSIT -> order.setDispatchedAt(LocalDateTime.now());
            case ARRIVED -> order.setArrivedAt(LocalDateTime.now());
            case COLLECTED -> order.setCollectedAt(LocalDateTime.now());
        }

        DeliveryOrder savedOrder = orderRepository.save(order);

        log.info("Order {} status updated: {} -> {}", orderNumber, oldStatus, newStatus);

        return savedOrder;
    }

    /**
     * Generate unique order number
     */
    private String generateOrderNumber() {
        return "ORD-" + System.currentTimeMillis();
    }
}
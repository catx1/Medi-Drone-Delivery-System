package org.example.cw3ilp.api.controller;

import jakarta.validation.Valid;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.example.cw3ilp.api.dto.DronePositionUpdate;
import org.example.cw3ilp.api.model.LngLatAlt;
import org.example.cw3ilp.entity.DeliveryOrder;
import org.example.cw3ilp.entity.OrderStatus;
import org.example.cw3ilp.repository.DeliveryOrderRepository;
import org.example.cw3ilp.service.DroneFlightSimulator;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@EnableScheduling
@RequestMapping("api/v1")
@RequiredArgsConstructor
@Slf4j
public class DroneWebSocketController {

    private final DroneFlightSimulator droneFlightSimulator;
    private final SimpMessagingTemplate messagingTemplate;
    private final DeliveryOrderRepository orderRepository;

    private String lastStatus = null;

    /**
     * Client sends message to /app/drone/start with flight path
     * Response broadcast to /topic/drone/status
     */
    @MessageMapping("/drone/start")
    @SendTo("/topic/drone/status")
    public String startDrone(StartFlightRequest request) {
        log.info("Received start request for drone {}", request.getDroneId());

        try {
            droneFlightSimulator.startFlight(request.getDroneId(), request.getFlightPath());
            return "Drone " + request.getDroneId() + " started";
        } catch (Exception e) {
            log.error("Error starting drone: {}", e.getMessage());
            return "Error: " + e.getMessage();
        }
    }

    /**
     * Client sends message to /app/drone/stop
     */
    @MessageMapping("/drone/stop")
    @SendTo("/topic/drone/status")
    public String stopDrone() {
        log.info("Received stop request");
        droneFlightSimulator.stopFlight();
        return "Drone stopped";
    }

    /**
     * REST endpoint to stop drone (for HTML button)
     */
    @PostMapping("/drone/stop")
    public ResponseEntity<Map<String, String>> stopDroneRest() {
        log.info("Received REST stop request");
        droneFlightSimulator.stopFlight();
        return ResponseEntity.ok(Map.of("status", "Drone stopped"));
    }

    /**
     * Broadcast drone position every 250ms (4 updates/second for smooth animation)
     */
    @Scheduled(fixedRate = 250)
    public void broadcastDronePosition() {
        DronePositionUpdate position = droneFlightSimulator.getCurrentPosition();

        if (position != null) {
            String currentOrderNumber = droneFlightSimulator.getCurrentOrderNumber();

            // Add order number to position update
            if (currentOrderNumber != null) {
                position.setOrderNumber(currentOrderNumber);
            }

            // Check for status changes
            if (!position.getStatus().equals(lastStatus)) {
                handleStatusChange(position, currentOrderNumber);
                lastStatus = position.getStatus();
            }

            // Broadcast position to all subscribers
            messagingTemplate.convertAndSend("/topic/drone/position", position);

            log.debug("Broadcasting: droneId={}, lng={}, lat={}, status={}, order={}",
                    position.getDroneId(), position.getLng(), position.getLat(),
                    position.getStatus(), currentOrderNumber);
        }
    }

    /**
     * Handle drone status changes and update order accordingly
     */
    private void handleStatusChange(DronePositionUpdate position, String orderNumber) {
        log.info("🔄 Drone status changed to: {}", position.getStatus());

        if (orderNumber == null) {
            return;
        }

        Optional<DeliveryOrder> orderOpt = orderRepository.findByOrderNumber(orderNumber);
        if (orderOpt.isEmpty()) {
            log.warn("Order not found: {}", orderNumber);
            return;
        }

        DeliveryOrder order = orderOpt.get();

        // Update order status based on drone status
        switch (position.getStatus()) {
            case "HOVERING":
                // Hovering during delivery (intermediate hover points)
                double distanceToDelivery = calculateDistance(
                        position.getLat(), position.getLng(),
                        order.getDeliveryLat(), order.getDeliveryLng()
                );

                log.debug("Hovering at distance to delivery: {} (threshold: 0.0001)", distanceToDelivery);
                break;

            case "ARRIVED":
                // Drone has arrived at customer location - final destination
                if (order.getStatus() == OrderStatus.IN_TRANSIT || order.getStatus() == OrderStatus.QUEUED) {
                    order.setStatus(OrderStatus.ARRIVED);
                    order.setArrivedAt(LocalDateTime.now());
                    orderRepository.save(order);

                    log.info("✅ Drone arrived at delivery location, waiting for collection");
                    log.info("📦 Order {} status: ARRIVED", orderNumber);

                    // Send notification to customer
                    messagingTemplate.convertAndSend("/topic/order/" + orderNumber,
                            Map.of(
                                    "status", "ARRIVED",
                                    "message", "Your medication has arrived! Please collect it."
                            ));
                }
                break;

            case "RETURNED":
                // Drone has returned to service point after customer pickup
                if (order.getStatus() == OrderStatus.COLLECTED) {
                    order.setStatus(OrderStatus.COMPLETED);
                    orderRepository.save(order);
                    log.info("✅ Order {} COMPLETED - drone returned to service point", orderNumber);
                }
                break;

            case "DELIVERED":
                // Legacy status - should not be used with new one-way delivery
                log.warn("⚠️ DELIVERED status detected - should be ARRIVED for one-way delivery");
                break;

            case "FLYING":
                if (order.getStatus() == OrderStatus.QUEUED) {
                    // Order just started delivery
                    order.setStatus(OrderStatus.IN_TRANSIT);
                    order.setDispatchedAt(LocalDateTime.now());
                    orderRepository.save(order);
                    log.info("🚁 Order {} is now IN_TRANSIT", orderNumber);
                }
                break;
        }
    }

    /**
     * Calculate distance between two coordinates (simple approximation)
     * Returns approximate distance in degrees
     */
    private double calculateDistance(double lat1, double lng1, double lat2, double lng2) {
        return Math.sqrt(Math.pow(lat2 - lat1, 2) + Math.pow(lng2 - lng1, 2));
    }

    /**
     * DTO for start flight request
     */
    @Setter
    @Getter
    public static class StartFlightRequest {
        private String droneId;
        private List<LngLatAlt> flightPath;
    }
}
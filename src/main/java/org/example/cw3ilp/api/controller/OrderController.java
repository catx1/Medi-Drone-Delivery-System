package org.example.cw3ilp.api.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.cw3ilp.entity.DeliveryOrder;
import org.example.cw3ilp.entity.Medication;
import org.example.cw3ilp.entity.OrderStatus;
import org.example.cw3ilp.repository.DeliveryOrderRepository;
import org.example.cw3ilp.repository.MedicationRepository;
import org.example.cw3ilp.service.OrderService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
@Slf4j
public class OrderController {

    private final OrderService orderService;
    private final MedicationRepository medicationRepository;
    private final DeliveryOrderRepository deliveryOrderRepository;

    /**
     * Get all available medications
     */
    @GetMapping("/medications")
    public ResponseEntity<List<Medication>> getAvailableMedications() {
        log.info("Fetching available medications");
        List<Medication> medications = medicationRepository.findByStockQuantityGreaterThan(0);
        return ResponseEntity.ok(medications);
    }

    /**
     * Create a new order
     */
    @PostMapping("/create")
    public ResponseEntity<Map<String, Object>> createOrder(
            @RequestParam String address,
            @RequestParam Long medicationId,
            @RequestParam(defaultValue = "1") int quantity) {

        log.info("Creating order: address={}, medicationId={}, quantity={}",
                address, medicationId, quantity);

        // Validate quantity
        if (quantity < 1 || quantity > 5) {
            return ResponseEntity.badRequest()
                    .body(Map.of(
                            "success", false,
                            "error", "Quantity must be between 1 and 5"
                    ));
        }

        try {
            DeliveryOrder order = orderService.createOrder(address, medicationId, quantity);

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "orderNumber", order.getOrderNumber(),
                    "estimatedWait", "5-10 minutes",
                    "message", "Order placed successfully! Your drone will be dispatched soon.",
                    "order", Map.of(
                            "id", order.getId(),
                            "medication", order.getMedication().getName(),
                            "quantity", order.getQuantity(),
                            "address", order.getCustomerAddress(),
                            "status", order.getStatus().toString()
                    )
            ));

        } catch (Exception e) {
            log.error("Failed to create order", e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of(
                            "success", false,
                            "error", e.getMessage()
                    ));
        }
    }

    /**
     * Track an order by order number
     */
    @GetMapping("/track/{orderNumber}")
    public ResponseEntity<?> trackOrder(@PathVariable String orderNumber) {
        log.info("Tracking order: {}", orderNumber);

        try {
            DeliveryOrder order = orderService.getOrderByNumber(orderNumber);

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "order", Map.of(
                            "orderNumber", order.getOrderNumber(),
                            "medication", order.getMedication().getName(),
                            "quantity", order.getQuantity(),
                            "address", order.getCustomerAddress(),
                            "status", order.getStatus().toString(),
                            "lat", order.getDeliveryLat(),
                            "lng", order.getDeliveryLng(),
                            "createdAt", order.getCreatedAt().toString()
                    )
            ));

        } catch (Exception e) {
            log.error("Failed to track order", e);
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of(
                            "success", false,
                            "error", e.getMessage()
                    ));
        }
    }

    /**
     * Get all queued orders
     */
    @GetMapping("/queue")
    public ResponseEntity<List<DeliveryOrder>> getQueue() {
        log.info("Fetching order queue");
        List<DeliveryOrder> queuedOrders = orderService.getQueuedOrders();
        return ResponseEntity.ok(queuedOrders);
    }

    /**
     * Confirm pickup by order number (no code required)
     */
    @PostMapping("/confirm-pickup-by-order")
    public ResponseEntity<Map<String, Object>> confirmPickupByOrder(
            @RequestParam String orderNumber) {

        log.info("Confirming pickup for order: {}", orderNumber);

        try {
            DeliveryOrder order = orderService.confirmPickupByOrderNumber(orderNumber);

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Pickup confirmed! Drone returning to base.",
                    "orderNumber", order.getOrderNumber(),
                    "status", order.getStatus().toString()
            ));

        } catch (Exception e) {
            log.error("Failed to confirm pickup for order {}", orderNumber, e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of(
                            "success", false,
                            "error", e.getMessage()
                    ));
        }
    }

    /**
     * Create order with both address and adjusted coordinates
     */
    @PostMapping("/create-with-address")
    public ResponseEntity<Map<String, Object>> createOrderWithAddress(
            @RequestBody Map<String, Object> request) {

        String address = (String) request.get("address");
        double lat = parseDouble(request.get("lat"));
        double lng = parseDouble(request.get("lng"));
        Long medicationId = parseLong(request.get("medicationId"));
        int quantity = request.containsKey("quantity") ? parseInt(request.get("quantity")) : 1;
        List<?> calculatedPathList = (List<?>) request.get("calculatedPath");
        String assignedDroneId = (String) request.get("assignedDroneId");

        log.info("Creating order: address={}, lat={}, lng={}, medicationId={}, assignedDrone={}",
                address, lat, lng, medicationId, assignedDroneId);

        // Validate quantity
        if (quantity < 1 || quantity > 5) {
            return ResponseEntity.badRequest()
                    .body(Map.of("success", false, "error", "Quantity must be between 1 and 5"));
        }

        try {
            // Validate medication
            Medication medication = medicationRepository.findById(medicationId)
                    .orElseThrow(() -> new RuntimeException("Medication not found"));

            if (medication.getStockQuantity() == null || medication.getStockQuantity() < quantity) {
                throw new RuntimeException("Insufficient stock");
            }

            // Serialize calculatedPath to JSON string if provided
            String calculatedPathJson = null;
            if (calculatedPathList != null && !calculatedPathList.isEmpty()) {
                ObjectMapper mapper = new ObjectMapper();
                calculatedPathJson = mapper.writeValueAsString(calculatedPathList);
            }

            // Create order with address AND coordinates
            String orderNumber = orderService.generateOrderNumber();

            DeliveryOrder order = new DeliveryOrder();
            order.setOrderNumber(orderNumber);
            order.setCustomerAddress(address);  // Store the address
            order.setDeliveryLat(lat);           // Use adjusted coordinates
            order.setDeliveryLng(lng);
            order.setMedication(medication);
            order.setQuantity(quantity);
            order.setStatus(OrderStatus.QUEUED);
            order.setCreatedAt(java.time.LocalDateTime.now());
            order.setCalculatedPath(calculatedPathJson);  // Store the pre-calculated path
            order.setAssignedDroneId(assignedDroneId);    // Store the assigned drone ID

            // Update stock
            medication.setStockQuantity(medication.getStockQuantity() - quantity);
            medicationRepository.save(medication);

            // Save order
            deliveryOrderRepository.save(order);

            log.info("Order created: {} for {}", orderNumber, address);

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "orderNumber", orderNumber,
                    "message", "Order placed successfully!"
            ));

        } catch (Exception e) {
            log.error("Failed to create order", e);
            return ResponseEntity.badRequest()
                    .body(Map.of("success", false, "error", e.getMessage()));
        }
    }

    /**
     * Proxy endpoint for Nominatim geocoding to avoid CORS issues
     */
    @GetMapping("/geocode")
    public ResponseEntity<List<Map<String, Object>>> geocodeAddress(@RequestParam String query) {
        log.info("Geocoding request for: {}", query);

        try {
            // Build Nominatim URL
            String url = UriComponentsBuilder
                    .fromHttpUrl("https://nominatim.openstreetmap.org/search")
                    .queryParam("format", "json")
                    .queryParam("q", query)
                    .queryParam("countrycodes", "gb")
                    .queryParam("limit", 5)
                    .queryParam("addressdetails", 1)
                    .build()
                    .toUriString();

            // Create RestTemplate with user agent header (required by Nominatim)
            RestTemplate restTemplate = new RestTemplate();
            org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
            headers.set("User-Agent", "MediDrone-App/1.0");

            org.springframework.http.HttpEntity<String> entity = new org.springframework.http.HttpEntity<>(headers);

            // Make request
            ResponseEntity<String> response = restTemplate.exchange(
                    url,
                    org.springframework.http.HttpMethod.GET,
                    entity,
                    String.class
            );

            // Parse JSON response
            ObjectMapper mapper = new ObjectMapper();
            JsonNode results = mapper.readTree(response.getBody());

            // Convert to list of maps
            List<Map<String, Object>> resultList = new ArrayList<>();
            for (JsonNode result : results) {
                Map<String, Object> item = new HashMap<>();
                item.put("lat", result.get("lat").asText());
                item.put("lon", result.get("lon").asText());
                item.put("display_name", result.get("display_name").asText());
                resultList.add(item);
            }

            return ResponseEntity.ok(resultList);

        } catch (Exception e) {
            log.error("Geocoding error: ", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ArrayList<>());
        }
    }

    /**
     * Reverse geocode coordinates to get nearest address
     */
    @GetMapping("/reverse-geocode")
    public ResponseEntity<Map<String, Object>> reverseGeocode(
            @RequestParam double lat,
            @RequestParam double lng) {

        log.info("Reverse geocoding request for coordinates: lat={}, lng={}", lat, lng);

        try {
            // Build Nominatim reverse geocode URL
            String url = UriComponentsBuilder
                    .fromHttpUrl("https://nominatim.openstreetmap.org/reverse")
                    .queryParam("format", "json")
                    .queryParam("lat", lat)
                    .queryParam("lon", lng)
                    .queryParam("addressdetails", 1)
                    .build()
                    .toUriString();

            // Create RestTemplate with user agent header (required by Nominatim)
            RestTemplate restTemplate = new RestTemplate();
            org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
            headers.set("User-Agent", "MediDrone-App/1.0");

            org.springframework.http.HttpEntity<String> entity = new org.springframework.http.HttpEntity<>(headers);

            // Make request
            ResponseEntity<String> response = restTemplate.exchange(
                    url,
                    org.springframework.http.HttpMethod.GET,
                    entity,
                    String.class
            );

            // Parse JSON response
            ObjectMapper mapper = new ObjectMapper();
            JsonNode result = mapper.readTree(response.getBody());

            // Convert to map
            Map<String, Object> resultMap = new HashMap<>();
            if (result.has("display_name")) {
                resultMap.put("display_name", result.get("display_name").asText());
                resultMap.put("lat", result.get("lat").asText());
                resultMap.put("lon", result.get("lon").asText());
            }

            return ResponseEntity.ok(resultMap);

        } catch (Exception e) {
            log.error("Reverse geocoding error: ", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to reverse geocode coordinates"));
        }
    }

    /**
     * Helper method to parse a value as Long (handles both String and Number types)
     */
    private Long parseLong(Object value) {
        if (value instanceof Number) {
            return ((Number) value).longValue();
        } else if (value instanceof String) {
            return Long.parseLong((String) value);
        }
        throw new IllegalArgumentException("Cannot parse value as Long: " + value);
    }

    /**
     * Helper method to parse a value as Integer (handles both String and Number types)
     */
    private int parseInt(Object value) {
        if (value instanceof Number) {
            return ((Number) value).intValue();
        } else if (value instanceof String) {
            return Integer.parseInt((String) value);
        }
        throw new IllegalArgumentException("Cannot parse value as Integer: " + value);
    }

    /**
     * Helper method to parse a value as Double (handles both String and Number types)
     */
    private double parseDouble(Object value) {
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        } else if (value instanceof String) {
            return Double.parseDouble((String) value);
        }
        throw new IllegalArgumentException("Cannot parse value as Double: " + value);
    }
}
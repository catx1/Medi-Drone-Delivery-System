package org.example.cw3ilp.api.controller;

import jakarta.validation.Valid;
import org.example.cw3ilp.api.dto.CalcDeliveryPathResponse;
import org.example.cw3ilp.api.dto.MedDispatchRec;
import org.example.cw3ilp.api.dto.QueryCriteriaRequest;
import org.example.cw3ilp.api.model.*;
import org.example.cw3ilp.service.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("api/v1")
public class DroneController {

    private final DroneService droneService;
    private final DroneFlightSimulator droneFlightSimulator;
    private final PathfinderService pathfinderService;
    private final ILPDataService ilpDataService;
    private final DistanceService distanceService;
    private final GeocodingService geocodingService;

    public DroneController(DroneService droneService,
                           DroneFlightSimulator droneFlightSimulator,
                           PathfinderService pathfinderService,
                           ILPDataService ilpDataService,
                           DistanceService distanceService,
                           GeocodingService geocodingService) {
        this.droneService = droneService;
        this.droneFlightSimulator = droneFlightSimulator;
        this.pathfinderService = pathfinderService;
        this.ilpDataService = ilpDataService;
        this.distanceService = distanceService;
        this.geocodingService = geocodingService;
    }


    private static final Logger logger = LoggerFactory.getLogger(DroneController.class);


    @GetMapping("/dronesWithCooling/{state}")
    public ResponseEntity<List<String>> dronesWithCooling(@PathVariable boolean state) {
        List<String> droneIds = droneService.getDronesWithCooling(state);
        return ResponseEntity.ok(droneIds);
    }

    @GetMapping("/droneDetails/{id}")
    public ResponseEntity<Drone> droneDetails(@PathVariable String id) {
        return droneService.getDroneById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/queryAsPath/{attributeName}/{attributeValue}")
    public ResponseEntity<List<String>> queryAsPath(
            @PathVariable String attributeName,
            @PathVariable String attributeValue
    ) {

        List<String> matchingDroneIds = droneService.queryDronesByAttribute(attributeName, attributeValue);

        return ResponseEntity.ok(matchingDroneIds);
    }

    @PostMapping("/query")
    public ResponseEntity<List<String>> query(
            @Valid @RequestBody List<QueryCriteriaRequest> criteria
    ) {
        List<String> matchingDroneIds = droneService.queryDronesByCriteria(criteria);

        return ResponseEntity.ok(matchingDroneIds);
    }

    @PostMapping("/queryAvailableDrones")
    public ResponseEntity<List<String>> queryAvailableDrones(
            @Valid @RequestBody List<MedDispatchRec> medDispatchRecs
    ) {
        List<String> availableDrones = droneService.findAvailableDrones(medDispatchRecs);
        return ResponseEntity.ok(availableDrones);
    }

    @PostMapping("/calcDeliveryPath")
    public ResponseEntity<CalcDeliveryPathResponse> calcDeliveryPath(
            @Valid @RequestBody List<MedDispatchRec> dispatches
    ) {
        logger.info("Received calcDeliveryPath request with {} dispatches",
                dispatches != null ? dispatches.size() : 0);

        try {
            if (dispatches == null || dispatches.isEmpty()) {
                logger.warn("Empty list received");
                return ResponseEntity.badRequest().build();
            }

            CalcDeliveryPathResponse response = droneService.calculateDeliveryPath(dispatches);

            if (response == null) {
                logger.warn("Could not calculate delivery path");
                return ResponseEntity.badRequest().build();
            }

            logger.info("Successfully calculated delivery path: {} moves, {} cost",
                    response.getTotalMoves(), response.getTotalCost());
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            logger.error("Error calculating delivery path: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }


    @PostMapping("/calcDeliveryPathAsGeoJson")
    public ResponseEntity<Map<String, Object>> calcDeliveryPathAsGeoJson(
            @Valid @RequestBody List<MedDispatchRec> dispatches
    ) {
        logger.info("Received calcDeliveryPathAsGeoJson request with {} dispatches",
                dispatches != null ? dispatches.size() : 0);

        try {
            if (dispatches == null || dispatches.isEmpty()) {
                return ResponseEntity.ok(createEmptyGeoJson());
            }

            // calculate path for a single drone
            Map<String, Object> geoJson = droneService.calculateSingleDronePathAsGeoJson(dispatches);

            return ResponseEntity.ok(geoJson);

        } catch (Exception e) {
            logger.error("Error: {}", e.getMessage(), e);
            return ResponseEntity.ok(createEmptyGeoJson());
        }
    }

    private Map<String, Object> createEmptyGeoJson() {
        Map<String, Object> empty = new LinkedHashMap<>();
        empty.put("type", "FeatureCollection");
        empty.put("features", new ArrayList<>());
        return empty;
    }

    // helper endpoint for debugging calcDeliveryPath
    @PostMapping("/convertToGeoJSon")
    public ResponseEntity<Map<String, Object>> calcDeliveryPathAsGeoJson(
            @Valid @RequestBody CalcDeliveryPathResponse deliveryPathResponse
    ) {
        logger.info("Received calcDeliveryPathAsGeoJson request");

        try {
            if (deliveryPathResponse == null ||
                    deliveryPathResponse.getDronePaths() == null ||
                    deliveryPathResponse.getDronePaths().isEmpty()) {
                logger.warn("Empty or null delivery path response received");
                return ResponseEntity.ok(createEmptyGeoJson());
            }

            Map<String, Object> geoJson = droneService.convertToGeoJson(deliveryPathResponse);

            return ResponseEntity.ok(geoJson);

        } catch (Exception e) {
            logger.error("Error converting to GeoJSON: {}", e.getMessage(), e);
            return ResponseEntity.ok(createEmptyGeoJson());
        }
    }

    @PostMapping("/api/v1/drone/simulate")
    public ResponseEntity<String> startDroneSimulation(
            @RequestParam String droneId,
            @Valid @RequestBody List<MedDispatchRec> dispatches
    ) {
        logger.info("Starting drone simulation for {}", droneId);

        // Get flight path from your existing service
        CalcDeliveryPathResponse pathResponse = droneService.calculateDeliveryPath(dispatches);

        if (pathResponse.getDronePaths().isEmpty()) {
            return ResponseEntity.badRequest().body("No path calculated");
        }

        // Extract flight path
        List<LngLatAlt> flightPath = new ArrayList<>();
        for (Delivery delivery : pathResponse.getDronePaths().getFirst().getDeliveries()) {
            flightPath.addAll(delivery.getFlightPath());
        }

        // This would trigger via WebSocket in real implementation
        return ResponseEntity.ok("Simulation ready. Connect via WebSocket to start.");
    }

    @PostMapping("drone/start-live-tracking")
    public ResponseEntity<Map<String, Object>> startLiveTracking(
            @Valid @RequestBody List<MedDispatchRec> dispatches
    ) {
        logger.info("Starting live tracking for {} dispatches", dispatches.size());

        try {
            // Calculate actual delivery path using your CW2 code
            CalcDeliveryPathResponse pathResponse = droneService.calculateDeliveryPath(dispatches);

            if (pathResponse.getDronePaths().isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "No path calculated"));
            }

        // Extract flight path for first drone
        DronePath dronePath = pathResponse.getDronePaths().getFirst();
        List<LngLatAlt> fullPath = new ArrayList<>();

            for (Delivery delivery : dronePath.getDeliveries()) {
                if (delivery.getFlightPath() != null) {
                    fullPath.addAll(delivery.getFlightPath());
                }
            }

            // Start the simulation with real path
            droneFlightSimulator.startFlight(dronePath.getDroneId(), fullPath);

            Map<String, Object> response = new HashMap<>();
            response.put("droneId", dronePath.getDroneId());
            response.put("totalWaypoints", fullPath.size());
            response.put("totalMoves", pathResponse.getTotalMoves());
            response.put("totalCost", pathResponse.getTotalCost());
            response.put("flightPath", fullPath); // Include for map display

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            logger.error("Error starting live tracking: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/drone/calculate-and-track")
    public ResponseEntity<Map<String, Object>> calculateAndTrack(
            @RequestParam double targetLng,
            @RequestParam double targetLat
    ) {
        logger.info("Calculating path to target: lng={}, lat={}", targetLng, targetLat);

        try {
            LngLatAlt target = new LngLatAlt(targetLng, targetLat, 0.0);

            // Get all service points
            List<DronesAvailability.ServicePoint> servicePoints = ilpDataService.getAllServicePoints();

            // Find nearest service point to target
            DronesAvailability.ServicePoint nearestServicePoint = null;
            double minDistance = Double.MAX_VALUE;

            for (DronesAvailability.ServicePoint sp : servicePoints) {
                if (sp.getLocation() == null) continue;

                LngLat spLocation = new LngLat(
                        sp.getLocation().getLng(),
                        sp.getLocation().getLat()
                );
                LngLat targetLocation = new LngLat(targetLng, targetLat);
                double distance = distanceService.computeDistance(spLocation, targetLocation);

                if (distance < minDistance) {
                    minDistance = distance;
                    nearestServicePoint = sp;
                }
            }

            if (nearestServicePoint == null) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "No service points available"));
            }

            LngLatAlt servicePointLocation = new LngLatAlt(
                    nearestServicePoint.getLocation().getLng(),
                    nearestServicePoint.getLocation().getLat(),
                    0.0
            );

            logger.info("Nearest service point: {} at distance: {}",
                    nearestServicePoint.getName(), minDistance);

            // Get restricted areas for pathfinding
            List<RestrictedArea> restrictedAreas = ilpDataService.getAllRestrictedAreas();

            // Calculate round trip: Service Point -> Target -> Service Point
            List<LngLatAlt> pathToTarget = pathfinderService.findPath(
                    servicePointLocation, target, restrictedAreas);

            List<LngLatAlt> pathBackToServicePoint = pathfinderService.findPath(
                    target, servicePointLocation, restrictedAreas);

            if (pathToTarget == null || pathBackToServicePoint == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("error", "Could not find valid path"));
            }

            // Combine paths for round trip starting and ending at service point
            List<LngLatAlt> fullPath = new ArrayList<>(pathToTarget);

            // Add hover at delivery (drop-off - 3 seconds)
            fullPath.add(target);
            fullPath.add(target);
            fullPath.add(target);

            // Add return path (skip first point to avoid duplicate)
            if (!pathBackToServicePoint.isEmpty()) {
                fullPath.addAll(pathBackToServicePoint.subList(1, pathBackToServicePoint.size()));
            }

            logger.info("Full path calculated: {} waypoints", fullPath.size());
            logger.info("Route: {} (start) -> Target (delivery) -> {} (return)",
                    nearestServicePoint.getName(), nearestServicePoint.getName());

            // Log the start and end points for verification
            if (!fullPath.isEmpty()) {
                LngLatAlt start = fullPath.get(0);
                LngLatAlt end = fullPath.get(fullPath.size() - 1);
                logger.info("Path starts at: lng={}, lat={}", start.getLng(), start.getLat());
                logger.info("Path ends at: lng={}, lat={}", end.getLng(), end.getLat());
            }

            // Start flight simulation
            String droneId = "DRONE-001";
            droneFlightSimulator.startFlight(droneId, fullPath);

            // Prepare response
            Map<String, Object> response = new HashMap<>();
            response.put("droneId", droneId);
            response.put("totalWaypoints", fullPath.size());
            response.put("flightPath", fullPath);
            response.put("servicePoint", Map.of(
                    "name", nearestServicePoint.getName(),
                    "id", nearestServicePoint.getId(),
                    "lng", nearestServicePoint.getLocation().getLng(),
                    "lat", nearestServicePoint.getLocation().getLat(),
                    "distance", minDistance
            ));
            response.put("target", Map.of(
                    "lng", targetLng,
                    "lat", targetLat
            ));

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            logger.error("Error calculating flight path", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to calculate path: " + e.getMessage()));
        }
    }

    @GetMapping("/test-geocoding")
    public ResponseEntity<Map<String, Object>> testGeocoding(
            @RequestParam(defaultValue = "Appleton Tower") String address) {

        try {
            Map<String, Double> coords = geocodingService.geocodeAddress(address);

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "address", address,
                    "coordinates", coords,
                    "message", "Geocoding successful!"
            ));

        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(Map.of(
                            "success", false,
                            "error", e.getMessage()
                    ));
        }
    }

    /**
     * Calculate delivery path with medication requirements - for customer portal
     */
    @PostMapping("/drone/calculate-delivery")
    public ResponseEntity<Map<String, Object>> calculateDeliveryPath(
            @RequestParam Long medicationId,
            @RequestParam double targetLat,
            @RequestParam double targetLng) {

        try {
            logger.info("Calculating delivery path for medication {} to ({}, {})",
                    medicationId, targetLat, targetLng);

            // Get medication details
            boolean requiresRefrigeration = checkMedicationRequiresRefrigeration(medicationId);
            logger.info("Medication requires refrigeration: {}", requiresRefrigeration);

            // Get all service points
            List<DronesAvailability.ServicePoint> servicePoints = ilpDataService.getAllServicePoints();

            // Find nearest service point
            DronesAvailability.ServicePoint nearestServicePoint = findNearestServicePoint(servicePoints, targetLat, targetLng);

            if (nearestServicePoint == null) {
                return ResponseEntity.badRequest()
                        .body(Map.of("success", false, "error", "No service points available"));
            }

            double startLat = nearestServicePoint.getLocation().getLat();
            double startLng = nearestServicePoint.getLocation().getLng();

            LngLat targetLocation = new LngLat(targetLng, targetLat);
            double distance = distanceService.computeDistance(
                    new LngLat(startLng, startLat),
                    targetLocation);

            logger.info("Nearest service point: {} at distance: {}",
                    nearestServicePoint.getName(), distance);

            // Get restricted areas
            List<RestrictedArea> restrictedAreas = ilpDataService.getAllRestrictedAreas();

            // Calculate path - ONLY to target (one-way delivery)
            LngLatAlt servicePointPos = new LngLatAlt(startLng, startLat, 50.0);
            LngLatAlt targetPos = new LngLatAlt(targetLng, targetLat, 50.0);

            List<LngLatAlt> pathToTarget = pathfinderService.findPath(servicePointPos, targetPos, restrictedAreas);

            if (pathToTarget == null) {
                return ResponseEntity.badRequest()
                        .body(Map.of("success", false, "error", "Could not find valid path"));
            }

            // Add hover at delivery location (3 waypoints for ~3 seconds landing/delivery)
            pathToTarget.add(targetPos);
            pathToTarget.add(targetPos);
            pathToTarget.add(targetPos);

            // Calculate distance and ETA (one-way only)
            double totalDistance = calculatePathDistanceAlt(pathToTarget);
            double speedKmPerSec = 0.015; // 15 m/s
            int etaSeconds = (int) (totalDistance / speedKmPerSec);

            logger.info("Delivery path calculated: {} waypoints, ETA: {} seconds",
                    pathToTarget.size(), etaSeconds);

            // Assign drone
            String assignedDrone = assignSuitableDrone(requiresRefrigeration);

            // Convert to map format
            List<Map<String, Double>> pathCoordinates = pathToTarget.stream()
                    .map(pos -> Map.of("lat", pos.getLat(), "lng", pos.getLng()))
                    .toList();

            // Round to 2 decimal places for display
            double distanceKm = Math.round(totalDistance * 100.0) / 100.0;
            int etaMinutes = (int) Math.ceil(etaSeconds / 60.0);

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "servicePoint", Map.of(
                            "name", nearestServicePoint.getName(),
                            "lat", startLat,
                            "lng", startLng
                    ),
                    "assignedDrone", assignedDrone,
                    "requiresRefrigeration", requiresRefrigeration,
                    "path", pathCoordinates,
                    "totalWaypoints", pathToTarget.size(),
                    "etaSeconds", etaSeconds,
                    "etaMinutes", etaMinutes,
                    "distanceKm", distanceKm
            ));

        } catch (Exception e) {
            logger.error("Failed to calculate delivery path", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("success", false, "error", e.getMessage()));
        }
    }

    private DronesAvailability.ServicePoint findNearestServicePoint(
            List<DronesAvailability.ServicePoint> servicePoints,
            double targetLat, double targetLng) {

        LngLat target = new LngLat(targetLng, targetLat);

        return servicePoints.stream()
                .filter(sp -> sp.getLocation() != null)
                .min(Comparator.comparingDouble(sp ->
                        distanceService.computeDistance(
                                new LngLat(sp.getLocation().getLng(), sp.getLocation().getLat()),
                                target)))
                .orElse(null);
    }

    private double calculatePathDistanceAlt(List<LngLatAlt> path) {
        double totalDistance = 0;
        for (int i = 0; i < path.size() - 1; i++) {
            LngLatAlt current = path.get(i);
            LngLatAlt next = path.get(i + 1);
            totalDistance += distanceService.computeDistance(
                    new LngLat(current.getLng(), current.getLat()),
                    new LngLat(next.getLng(), next.getLat()));
        }
        return totalDistance;
    }

    private String assignSuitableDrone(boolean requiresRefrigeration) {
        // Simple assignment logic - in real app, check availability
        if (requiresRefrigeration) {
            return "DRONE-COOL-001"; // Drone with refrigeration
        } else {
            return "DRONE-STD-001"; // Standard drone
        }
    }

    private boolean checkMedicationRequiresRefrigeration(Long medicationId) {
        // TODO: Implement actual database lookup
        // For now, assume medication IDs 1-3 need refrigeration
        return medicationId <= 3;
    }

}

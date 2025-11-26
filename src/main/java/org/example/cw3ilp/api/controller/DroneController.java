package org.example.cw3ilp.api.controller;

import jakarta.validation.Valid;
import org.example.cw3ilp.api.dto.CalcDeliveryPathResponse;
import org.example.cw3ilp.api.dto.MedDispatchRec;
import org.example.cw3ilp.api.dto.QueryCriteriaRequest;
import org.example.cw3ilp.api.model.Drone;
import org.example.cw3ilp.service.DroneService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("api/v1")
public class DroneController {

    private final DroneService droneService;

    private static final Logger logger = LoggerFactory.getLogger(DroneController.class);

    @Autowired
    public DroneController(DroneService droneService) {
        this.droneService = droneService;
    }

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
}

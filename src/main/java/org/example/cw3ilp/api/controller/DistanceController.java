package org.example.cw3ilp.api.controller;

import jakarta.validation.Valid;
import org.example.cw3ilp.api.dto.DistanceRequest;
import org.example.cw3ilp.api.dto.NextPositionRequest;
import org.example.cw3ilp.api.model.LngLat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.example.cw3ilp.service.DistanceService;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller responsible for exposing endpoints related to:
 * distance, proximity and position calculation.
 * <p>
 *     Computational logic is handled by {@link DistanceService}
 * </p>
 */
@RestController
@RequestMapping("/api/v1")
public class DistanceController {

    private final DistanceService distanceService;

    private static final Logger logger = LoggerFactory.getLogger(DistanceController.class);


    public DistanceController(DistanceService distanceService) {
        this.distanceService = distanceService;
    }

    /**
     * Calculates the Euclidean distance between two positions (latitude, longitude).
     * @param request a valid {@link DistanceRequest} containing two {@link LngLat} objects
     * @return the computed distance in degrees
     *
     * <p><b>Response</b></p>
     * <ul>
     *     <li><code>200 OK</code> – successful</li>
     *     <li><code>400 Bad Request</code> – invalid request body</li>
     * </ul>
     */
    @PostMapping("/distanceTo")
    public double distanceTo(@Valid @RequestBody DistanceRequest request) {
        return distanceService.computeDistance(request.getPosition1(), request.getPosition2());
    }

    /**
     * Determines whether two positions are considered "close"
     * based on a predefined distance threshold (0.00015 degrees).
     *
     * @param request a {@link DistanceRequest} containing two {@link LngLat} objects
     * @return a {@link ResponseEntity} with:
     *         <ul>
     *             <li><code>true</code> if the positions are close</li>
     *             <li><code>false</code> otherwise</li>
     *         </ul>
     *
     * <p><b>Response:</b>
     * <ul>
     *     <li><code>200 OK</code> – successful</li>
     *     <li><code>400 Bad Request</code> – invalid request body</li>
     * </ul></p>
     */
    @PostMapping("/isCloseTo")
    public ResponseEntity<Boolean> closeTo(@Valid @RequestBody DistanceRequest request) {

        // ensure valid request
        if (request.getPosition1() == null || request.getPosition2() == null) {
            // bad request - 400
            return ResponseEntity.badRequest().build();
        }

        // run the service
        boolean result = distanceService.computeCloseness(request.getPosition1(), request.getPosition2());

        // return 200 with true/false
        return ResponseEntity.ok(result);

    }

    /**
     * Computes the next position based on given start position and movement angle.
     * <p>
     * The step distance is fixed at <code>0.00015</code> degrees, and the angle
     * is measured clockwise from East, from a selection of 16 allowed angles (North, South, East and West, and the secondary directions
     * between those of NE, NW, SE and SW, and the tertiary directions between those of NNE, ENE,
     * and so forth.)
     * </p>
     *
     * @param request a valid {@link NextPositionRequest} containing a start {@link LngLat} and angle (in degrees)
     * @return a {@link ResponseEntity} containing the next {@link LngLat}
     *
     * <p><b>Response:</b>
     * <ul>
     *     <li><code>200 OK</code> – successful</li>
     *     <li><code>400 Bad Request</code> – invalid request body</li>
     * </ul></p>
     */
    @PostMapping("/nextPosition")
    public ResponseEntity<LngLat> nextPosition(@Valid @RequestBody NextPositionRequest request) {

        // null validation
        if (request == null || request.getStart() == null || request.getAngle() == null) {
            logger.warn("Bad request: One of the inputs is null");
            return ResponseEntity.badRequest().build(); // 400 Bad Request
        }

        try {
            LngLat nextPos = distanceService.computeNextPosition(
                    request.getStart(),
                    request.getAngle()
            );

            logger.info("Computed next position for start {} with angle {}: {}",
                    request.getStart(), request.getAngle(), nextPos);
            return ResponseEntity.ok(nextPos); // 200 OK good request

        } catch (IllegalArgumentException e) {
            logger.warn("Invalid request: {}", e.getMessage());
            return ResponseEntity.badRequest().build();

        } catch (Exception e) {
            // Other errors (server errors etc)
            logger.error("Unexpected error while computing next position", e);
            return ResponseEntity.internalServerError().build(); // 500 Internal Server Error
        }

    }
}

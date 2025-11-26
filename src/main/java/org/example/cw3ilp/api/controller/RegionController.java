package org.example.cw3ilp.api.controller;

import jakarta.validation.Valid;
import org.example.cw3ilp.api.dto.RegionRequest;
import org.example.cw3ilp.service.RegionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.example.cw3ilp.api.model.LngLat;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * REST controller responsible for verifying whether a point
 * lies inside a defined geographical region (polygon).
 * <p>
 * Regions are defined as polygons by a sequence of vertices (longitude/latitude pairs),
 * and must form a closed shape.
 * </p>
 *
 * <p> Geometric calculations handled by {@link RegionService}.</p>
 */
@RestController
@RequestMapping("/api/v1")
public class RegionController {

    private final RegionService regionService;

    @Autowired
    public RegionController(RegionService regionService) {
        this.regionService = regionService;
    }

    /**
     * Calculates whether a given point (longitude, latitude) lies within
     * a named region (polygon).
     * <p>
     * The region is defined by an ordered list of vertices forming a closed polygon.
     * If the region is not closed (i.e., the last vertex does not match the first),
     * the request is considered invalid.
     * </p>
     *
     * @param request a valid {@link RegionRequest} containing the point and region
     * @return a {@link ResponseEntity} containing:
     *         <ul>
     *             <li><code>true</code> – if the point is inside the region (including the border)</li>
     *             <li><code>false</code> – if the point lies outside the region</li>
     *         </ul>
     *
     * <p><b>Response Codes:</b></p>
     * <ul>
     *     <li><code>200 OK</code> – valid input, computation successful</li>
     *     <li><code>400 Bad Request</code> – invalid input (null fields, open region, or missing vertices)</li>
     * </ul>
     */
    @PostMapping("/isInRegion")
    public ResponseEntity<Boolean> isInRegion(@Valid @RequestBody RegionRequest request) {
        // error handling
        if (request.getPosition() == null || request.getRegion() == null
                || request.getRegion().getVertices() == null
                || request.getRegion().getVertices().size() < 4) {
            return ResponseEntity.badRequest().build();
        }

        // TODO Split this into 2 functions

        List<LngLat> vertices = request.getRegion().getVertices();
        LngLat first = vertices.getFirst();
        LngLat last = vertices.getLast();

        // check if polygon is closed
        if (!first.getLat().equals(last.getLat()) || !first.getLng().equals(last.getLng())) {
            return ResponseEntity.badRequest().build();
        }

        // call region service
        boolean inside = regionService.isInside(vertices,
                request.getPosition().getLng(),
                request.getPosition().getLat());

        return ResponseEntity.ok(inside);
    }

}

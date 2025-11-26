package org.example.cw3ilp.service;
import org.example.cw3ilp.api.model.LngLat;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Service class responsible for determining whether a geographic position lies within a
 * polygonal region.
 * <p>
 *     Implements a ray-casting algorithm (even-odd rule) to
 *     determine whether a point is inside or outside a polygon
 * </p>
 */
@Service
public class RegionService {

    /**
     * @param vertices a list of {@link LngLat} objects representing the polygon's vertices.
     *                 The last vertex should close the polygon by connecting with the starting vertex.
     * @param xp the longitude of the point
     * @param yp the latitude of the point
     * @return {@code true} if the point lies inside or on the border of the polygon.
     * otherwise, {@code false}
     * @throws IllegalArgumentException if the vertex is invalid
     */
    public boolean isInside(List<LngLat> vertices, double xp, double yp){
        int counter = 0;
        int n = vertices.size();

        // for each edge (n-1) since last point closes polygon
        for (int i = 0; i < n-1; i++) {
            LngLat p1 = vertices.get(i);
            LngLat p2 = vertices.get(i+1);

            double x1 = p1.getLng();
            double x2 = p2.getLng();
            double y1 = p1.getLat();
            double y2 = p2.getLat();

            // check if point crosses edge
            if ((yp < y1) != (yp < y2)){
                double xIntersection = x1 + ((yp-y1)/(y2-y1) * (x2-x1));
                if (xIntersection > xp){
                    counter++;
                }
            }
        }
        // if counter odd, point is inside
        return counter % 2 == 1;
    }
}

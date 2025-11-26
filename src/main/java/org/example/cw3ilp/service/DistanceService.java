package org.example.cw3ilp.service;
import org.example.cw3ilp.api.model.LngLat;
import org.springframework.stereotype.Service;
import static java.lang.Math.cos;
import static java.lang.Math.sin;

/**
 * Service class responsible for distance and position calculations
 * <p>
 *     Provides methods for computing Euclidean distance, closeness checks,
 *     and finding the next position, given a starting point and an angle.
 * </p>
 */
@Service
public class DistanceService {

    /** Threshold used to determine if 2 points are considered "close" */
    private static final double CLOSE_THRESHOLD = 0.00015;
    /** Step distance of how far the point will move along the specified angle */
    private static final double STEP_DISTANCE = 0.00015;

    /**
     * Computes Euclidean distance between two positions (using longitude and latitude)
     * @param lngLat1 the first position
     * @param lngLat2 the second position
     * @return the Euclidean distance in degrees
     * @throws IllegalArgumentException if either position is null
     */
    public double computeDistance(LngLat lngLat1, LngLat lngLat2) {
        if (lngLat1 == null || lngLat2 == null) {
            throw new IllegalArgumentException("Position cannot be null");
        }

        double dx = lngLat2.getLng() - lngLat1.getLng();
        double dy = lngLat2.getLat() - lngLat1.getLat();
        return Math.sqrt(dx * dx + dy * dy);
    }

    /**
     * Determines if two positions are considered "close",
     * based on the pre-defined {@link #CLOSE_THRESHOLD}
     * @param lngLat1 the first position
     * @param lngLat2 the second position
     * @return {@code true} if the distance is less than {@code CLOSE_THRESHOLD}.
     * otherwise {@code false}
     * @throws IllegalArgumentException if either position is null
     */
    public boolean computeCloseness(LngLat lngLat1, LngLat lngLat2) {
        if (lngLat1 == null || lngLat2 == null) {
            throw new IllegalArgumentException("Position cannot be null");
        }

        double distance = computeDistance(lngLat1, lngLat2);
        return distance < CLOSE_THRESHOLD;
    }

    /**
     * Computes next position based on a starting point and movement angle.
     * The step distance is fixed at {@code 0.00015} degrees, and
     * angles are measured in degrees clockwise from East:
     * <ul>
     *     <li> 0°   →  East  </li>
     *     <li> 90°  →  North </li>
     *     <li> 180° →  West  </li>
     *     <li> 270° →  South </li>
     * </ul>
     *
     * Will not allow angles that are out of the 0 -> 337.5 range, and that are not divisible by 22.5
     * (i.e. are not one of 16 allowed angles)
     *
     * @param startPos the starting position (longitude and latitude)
     * @param angle    the direction of movement in degrees
     * @return a new {@link LngLat} representing the next location
     */
    public LngLat computeNextPosition(LngLat startPos, double angle) {
        // ensure angle is valid
        if (angle < 0 || angle >= 360) {
            throw new IllegalArgumentException(
                    "Invalid angle: " + angle + ". Must be between 0 (inclusive) and 360 (exclusive).");
        }

        double remainder = angle % 22.5;
        if (Math.abs(remainder) > 1e-9 && Math.abs(remainder - 22.5) > 1e-9){
            throw new IllegalArgumentException(
                    "Invalid angle: " + angle + ". Drone can only move in 22.5° increments (0, 22.5, 45, ..., 337.5).");
        }

        // compute next position
        double d = STEP_DISTANCE; // distance tolerance
        double angleRad = Math.toRadians(angle); // convert to radians for sin/cos

        // get dx and dy
        double dx = d * cos(angleRad);
        double dy = d * sin(angleRad);

        // get new lat and lng
        double nextLng = startPos.getLng() + dx;
        double nextLat = startPos.getLat() + dy;

        return new LngLat(nextLng, nextLat);
    }
}
